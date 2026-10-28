import com.bosch.eclipsedriver.protocol.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * M1 integration test: launch a real install through the MCP server jar and read its shells.
 * Usage: java -cp <protocol classes>;<it classes> LaunchIT <java.exe> <server jar> <launcher exe> <workspace dir>
 */
public final class LaunchIT {
    private static int nextId = 1;
    private static PrintStream toServer;
    private static BufferedReader fromServer;

    public static void main(String[] args) throws Exception {
        String java = args[0];
        String fatJar = args[1];
        Path launcher = Path.of(args[2]);
        Path workspace = Path.of(args[3]);
        Path install = launcher.toAbsolutePath().getParent();
        System.setProperty("it.install", install.toString());

        Path ideSettings = workspace.resolve(".metadata/.plugins/org.eclipse.ui.ide/dialog_settings.xml");
        Files.createDirectories(ideSettings.getParent());
        Files.writeString(ideSettings, """
                <?xml version="1.0" encoding="UTF-8"?>
                <section name="Workbench">
                    <section name="NewWizardAction">
                        <item key="NewWizardSelectionPage.STORE_SELECTED_ID" value="org.eclipse.ui.wizards.new.project"/>
                        <list key="NewWizardSelectionPage.STORE_EXPANDED_CATEGORIES_ID">
                            <item value="org.eclipse.ui.Basic"/>
                        </list>
                    </section>
                </section>
                """, StandardCharsets.UTF_8);

        System.out.println("snapshotting install files ...");
        List<String> beforeAll = snapshot(install);
        System.out.println(beforeAll.size() + " files");

        Process server = new ProcessBuilder(java, "-jar", fatJar).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        toServer = new PrintStream(server.getOutputStream(), true, StandardCharsets.UTF_8);
        fromServer = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(8 * 60_000);
                System.err.println("IT watchdog: killing server");
                server.destroyForcibly();
            } catch (InterruptedException e) {
                // finished in time
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        long javaPid = -1;
        boolean ok = false;
        try {
            Map<String, Object> init = request("initialize", Map.of("protocolVersion", "2025-06-18"));
            System.out.println("initialize.instructions = " + ((Map<?, ?>) init.get("result")).get("instructions"));
            List<?> tools = (List<?>) ((Map<?, ?>) request("tools/list", Map.of()).get("result")).get("tools");
            check(tools.size() == 3, "server registers exactly 3 MCP tools, got " + tools.size());

            Map<String, Object> connectArgs = new LinkedHashMap<>();
            connectArgs.put("mode", "launch");
            connectArgs.put("path", launcher.toString());
            connectArgs.put("workspace", workspace.toString());
            Map<String, Object> connect = callTool("connect", connectArgs);
            System.out.println("connect -> " + Json.write(connect));
            check(!isError(connect), "connect failed");
            Map<?, ?> connected = (Map<?, ?>) payload(connect);
            javaPid = ((Number) connected.get("pid")).longValue();
            check("i1".equals(connected.get("instance")), "instance id");

            Map<String, Object> shells = null;
            long deadline = System.currentTimeMillis() + 180_000;
            while (System.currentTimeMillis() < deadline) {
                shells = callTool("list_shells", Map.of());
                if (!isError(shells) && !((List<?>) ((Map<?, ?>) payload(shells)).get("shells")).isEmpty()) {
                    break;
                }
                System.out.println("waiting for a shell: " + Json.write(payload(shells)));
                Thread.sleep(3000);
            }
            System.out.println("list_shells -> " + Json.write(shells));
            check(shells != null && !isError(shells), "list_shells failed");
            List<?> rows = (List<?>) ((Map<?, ?>) payload(shells)).get("shells");
            check(!rows.isEmpty(), "list_shells returned no shell");
            check(((Map<?, ?>) payload(shells)).get("snapshot") instanceof Long, "list_shells carries a snapshot id");

            Map<String, Object> dialog = null;
            while (System.currentTimeMillis() < deadline) {
                dialog = callTool("read_dialog", Map.of());
                if (!isError(dialog)) {
                    break;
                }
                System.out.println("waiting for read_dialog during startup: " + Json.write(payload(dialog)));
                Thread.sleep(2000);
            }
            System.out.println("read_dialog -> " + Json.write(payload(dialog)));
            check(dialog != null && !isError(dialog), "read_dialog failed");

            Map<String, Object> status = callTool("get_status", Map.of());
            System.out.println("get_status -> " + Json.write(status));
            check(!isError(status), "get_status failed");

            wizardFlow();
            actFlow();
            ok = true;
        } finally {
            stopInstance(javaPid, launcher);
            server.destroy();
            watchdog.interrupt();
        }

        System.out.println("snapshotting install files again ...");
        List<String> afterAll = snapshot(install);
        // Install mode lets Eclipse write its own runtime files under configuration\; bundles.info must still match.
        java.util.function.Predicate<String> stable = l -> !l.contains("\\configuration\\") || l.contains("bundles.info");
        List<String> before = beforeAll.stream().filter(stable).toList();
        List<String> after = afterAll.stream().filter(stable).toList();
        boolean unchanged = before.equals(after);
        System.out.println("install unchanged: " + unchanged + " (" + after.size() + " files)");
        if (!unchanged) {
            after.stream().filter(l -> !before.contains(l)).limit(10).forEach(l -> System.out.println("  changed/new: " + l));
            before.stream().filter(l -> !after.contains(l)).limit(10).forEach(l -> System.out.println("  changed/gone: " + l));
        }
        System.out.println(ok && unchanged ? "IT PASSED" : "IT FAILED");
        System.exit(ok && unchanged ? 0 : 1);
    }

    private static Map<String, Object> step(String tool, Map<String, Object> args) throws IOException {
        Map<String, Object> reply = callTool(tool, args);
        String text = Json.write(payload(reply));
        System.out.println(tool + (isError(reply) ? " [ERROR] -> " : " -> ") + (text.length() > 1800 ? text.substring(0, 1800) + "..." : text));
        return reply;
    }

    private static void actFlow() throws Exception {
        System.out.println("--- M4/M5/M6 act flow");
        Map<String, Object> inst = step("list_instances", Map.of());
        check(!isError(inst) && Json.write(payload(inst)).contains("\"launchedByDriver\":true"), "list_instances shows the launched instance");

        check(!isError(step("journal_note", Map.of("note", "it run", "kind", "finding"))), "journal_note");
        check(Json.write(payload(step("get_journal", Map.of()))).contains("it run"), "get_journal returns the note");
        check(!isError(step("set_trace", Map.of("enabled", true))), "set_trace");
        check(!isError(step("get_logs", Map.of())), "get_logs");

        Map<String, Object> run = step("run_script", Map.of("script", "cmd", "args", List.of("/c", "echo", "driver-it")));
        check(!isError(run), "run_script");
        String pid = String.valueOf(((Map<?, ?>) payload(run)).get("process"));
        check(!isError(step("wait_for", Map.of("condition", "process_exit", "process", pid, "timeout_seconds", 20))), "wait_for process_exit");
        check(Json.write(payload(step("read_process_output", Map.of("process", pid)))).contains("driver-it"), "read_process_output");

        check(!isError(step("list_native_dialogs", Map.of())), "list_native_dialogs");
        check(!isError(step("list_launch_configs", Map.of())), "list_launch_configs");
        check(!isError(step("wait_for", Map.of("condition", "jobs_idle", "timeout_seconds", 60))), "wait_for jobs_idle");
        check(!isError(step("wait_for", Map.of("condition", "ui_idle", "timeout_seconds", 30))), "wait_for ui_idle");

        check(!isError(step("open_view", Map.of("view", "org.eclipse.ui.views.PropertySheet"))), "open_view");
        check(Json.write(payload(step("read_views", Map.of()))).contains("PropertySheet"), "read_views lists the opened view");
        check(!isError(step("activate_view", Map.of("view", "org.eclipse.ui.views.PropertySheet"))), "activate_view");

        Map<String, Object> pref = step("set_preference", Map.of("node", "com.bosch.eclipsedriver.it", "key", "probe", "value", "1"));
        check(!isError(pref), "set_preference");

        interactFlow();
        projectFlow();
        Map<String, Object> bad = step("click", Map.of("label", "no-such-control-xyz"));
        check(isError(bad), "click on a missing control must fail");

        check(!isError(step("wait_for", Map.of("condition", "shell_opened", "title", "never-opens-xyz", "timeout_seconds", 1))) == false,
                "wait_for must time out for a missing shell");
        check(!isError(step("disconnect", Map.of("stop", false))) , "disconnect");
        Map<String, Object> after = step("list_shells", Map.of());
        check(isError(after), "after disconnect there is no instance");
        attachFlow(javaPidOf(inst));
    }

    private static long javaPidOf(Map<String, Object> listInstances) {
        return ((Number) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) payload(listInstances)).get("connected")).get(0)).get("pid")).longValue();
    }

    private static void attachFlow(long pid) throws Exception {
        System.out.println("--- M5 attach to the running instance (pid " + pid + ")");
        Map<String, Object> att = step("connect", Map.of("mode", "attach", "pid", pid));
        check(!isError(att), "connect attach");
        check(!isError(step("list_shells", Map.of())), "list_shells after attach");
        check(!isError(step("disconnect", Map.of())), "disconnect after attach");
        installFlow(pid);
    }

    private static void installFlow(long pid) throws Exception {
        System.out.println("--- M5 install, relaunch, uninstall");
        check(isError(step("connect", Map.of("mode", "install", "pid", pid))), "install without confirm must be refused");
        try {
            Map<String, Object> inst = step("connect", Map.of("mode", "install", "pid", pid, "confirm_restart", true, "timeout_seconds", 240));
            check(!isError(inst), "connect install");
            boolean shells = false;
            for (int i = 0; i < 40 && !shells; i++) {
                shells = !isError(step("list_shells", Map.of()));
                if (!shells) {
                    Thread.sleep(3000);
                }
            }
            check(shells, "list_shells after install");
            check(!isError(step("disconnect", Map.of("stop", true))), "disconnect stop");
            Thread.sleep(5000);
        } finally {
            Map<String, Object> un = step("uninstall_agent", Map.of("path", System.getProperty("it.install")));
            check(!isError(un), "uninstall_agent");
        }
    }

    private static void projectFlow() throws Exception {
        System.out.println("--- testdata: import AUTOSAR_CAN and open its files");
        Path src = Path.of("A:\\sandbox\\Work\\Spec_Manual_Testing\\sandbox\\AUTOSAR_CAN");
        Path copy = Path.of(System.getProperty("java.io.tmpdir"), "ed-testdata", "AUTOSAR_CAN");
        if (!java.nio.file.Files.exists(src)) {
            System.out.println("testdata missing, skipped");
            return;
        }
        try (var walk = java.nio.file.Files.walk(copy.getParent() == null ? copy : copy.getParent())) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (java.io.IOException ignored) { }
        try (var walk = java.nio.file.Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path t = copy.resolve(src.relativize(p).toString());
                if (java.nio.file.Files.isDirectory(p)) {
                    java.nio.file.Files.createDirectories(t);
                } else {
                    java.nio.file.Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        check(!isError(step("menu_select", Map.of("path", "File/Import..."))), "open import wizard");
        String tree = exactClassRef("Tree", "Import");
        check(!isError(step("select", Map.of("ref", tree, "path", "General/Existing Projects into Workspace"))), "select Existing Projects");
        check(!isError(step("wizard_next", Map.of())), "wizard_next");
        System.out.println("import page -> " + Json.write(payload(step("read_dialog", Map.of()))));
        Map<?, ?> page = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        String combo = null;
        for (Object o : (List<?>) page.get("inputs")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if ("combo".equals(m.get("kind")) && Boolean.TRUE.equals(m.get("enabled"))) {
                combo = (String) m.get("ref");
                break;
            }
        }
        check(combo != null, "root directory combo found");
        String nested = null;
        for (Object o : (List<?>) page.get("options")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if ("Search for nested projects".equals(m.get("label"))) {
                nested = (String) m.get("ref");
            }
        }
        check(nested != null, "nested projects checkbox found");
        check(!isError(step("check", Map.of("ref", nested))), "check");
        check(optionSelected(nested), "checkbox is selected after check");
        check(!isError(step("uncheck", Map.of("ref", nested))), "uncheck");
        check(!optionSelected(nested), "checkbox is cleared after uncheck");
        check(!isError(step("set_text", Map.of("ref", combo, "value", copy.toString()))), "set root directory");
        step("press_key", Map.of("key", "Enter", "ref", combo));
        Thread.sleep(2000);
        System.out.println("after root -> " + Json.write(payload(step("read_dialog", Map.of()))));
        Map<?, ?> afterRoot = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        boolean canFinish = Boolean.TRUE.equals(((Map<?, ?>) ((Map<?, ?>) afterRoot.get("wizard")).get("buttons")).get("finish") instanceof Map<?, ?> f ? f.get("enabled") : null);
        if (canFinish) {
            check(!isError(step("wizard_finish", Map.of())), "wizard_finish imports the project");
        } else {
            System.out.println("project already in the workspace from an earlier run; cancelling the wizard");
            step("wizard_cancel", Map.of());
        }
        check(!isError(step("wait_for", Map.of("condition", "jobs_idle", "timeout_seconds", 120))), "jobs idle after import");
        Thread.sleep(1000);
        System.out.println("shells -> " + Json.write(payload(step("list_shells", Map.of()))));
        Object problems = payload(step("read_problems", Map.of("limit", 5)));
        System.out.println("problems -> " + Json.write(problems));
        String file = "/AUTOSAR_CAN/Project_EcucValues.arxml";
        Map<String, Object> pe = step("open_view", Map.of("view", "Project Explorer"));
        System.out.println("open_view -> " + Json.write(payload(pe)));
        Map<?, ?> views = (Map<?, ?>) payload(step("read_views", Map.of()));
        String peRef = null;
        for (Object o : (List<?>) views.get("views")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if ("org.eclipse.ui.navigator.ProjectExplorer".equals(m.get("id"))) {
                peRef = (String) m.get("ref") != null ? (String) m.get("ref") : (String) m.get("control");
            }
        }
        System.out.println("project explorer control -> " + peRef + " views=" + Json.write(views));
        Map<?, ?> trees = (Map<?, ?>) payload(step("find_elements", Map.of("class", "Tree")));
        String peTree = null;
        for (Object o : (List<?>) trees.get("matches")) {
            Map<?, ?> m = (Map<?, ?>) o;
            Map<String, Object> probe = step("read_tree", Map.of("ref", m.get("ref"), "limit", 20));
            String dump = Json.write(payload(probe));
            if (dump.contains("AUTOSAR_CAN")) {
                peTree = (String) m.get("ref");
            }
        }
        check(peTree != null, "project explorer tree found");
        Map<String, Object> sel = step("select", Map.of("ref", peTree, "path", "AUTOSAR_CAN/Project_EcucValues.arxml"));
        System.out.println("select file -> " + Json.write(payload(sel)));
        check(!isError(sel), "select file in explorer");
        Map<String, Object> rc = step("right_click_menu", Map.of("ref", peTree));
        System.out.println("right_click_menu -> " + Json.write(payload(rc)));
        check(!isError(rc), "right_click_menu");
        step("press_key", Map.of("key", "Escape"));
        Map<String, Object> dc = step("double_click", Map.of("ref", peTree));
        System.out.println("double_click -> " + Json.write(payload(dc)));
        check(!isError(dc), "double_click opens the file");
        Thread.sleep(3000);
        System.out.println("editor after double_click -> " + Json.write(payload(step("read_editor", Map.of("limit", 1)))));
        Map<String, Object> open = step("open_editor", Map.of("path", file));
        System.out.println("open_editor -> " + Json.write(payload(open)));
        check(!isError(open), "open_editor");
        Thread.sleep(3000);
        Map<String, Object> ed = step("read_editor", Map.of("limit", 5));
        System.out.println("read_editor -> " + Json.write(payload(ed)));
        check(!isError(ed), "read_editor on the opened arxml");
        Map<String, Object> sv = step("save", Map.of());
        System.out.println("save -> " + Json.write(payload(sv)));
        check(!isError(sv), "save");
        Map<String, Object> ce = step("close_editor", Map.of());
        System.out.println("close_editor -> " + Json.write(payload(ce)));
        check(!isError(ce), "close_editor");
    }

    private static boolean optionSelected(String ref) throws Exception {
        Map<?, ?> page = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        for (Object o : (List<?>) page.get("options")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (ref.equals(m.get("ref"))) {
                return Boolean.TRUE.equals(m.get("selected"));
            }
        }
        return false;
    }

    private static void interactFlow() throws Exception {
        System.out.println("--- M4 interaction flow (New Project wizard)");
        check(!isError(step("menu_select", Map.of("path", "File/New/Project..."))), "open wizard");
        check(!isError(step("set_text", Map.of("class", "Text", "label", "Wizards", "value", "Gen"))), "set_text filter");
        check(!isError(step("type_text", Map.of("class", "Text", "label", "Wizards", "value", "e"))), "type_text appends");
        Map<?, ?> filter = (Map<?, ?>) payload(step("read_text", Map.of("label", "Wizards")));
        check(Json.write(filter).contains("Gene"), "filter text is 'Gene'");
        check(!isError(step("set_text", Map.of("class", "Text", "label", "Wizards", "value", ""))), "clear filter");
        String tree = exactClassRef("Tree");
        check(!isError(step("expand", Map.of("ref", tree, "path", "General"))), "expand General");
        check(!isError(step("select", Map.of("ref", tree, "path", "General/Project"))), "select General/Project");
        check(!isError(step("collapse", Map.of("ref", tree, "path", "General"))), "collapse General");
        check(!isError(step("hover", Map.of("ref", tree))), "hover");
        check(!isError(step("scroll", Map.of("ref", tree, "direction", "down", "amount", 2))), "scroll");
        check(!isError(step("focus", Map.of("ref", tree))), "focus");
        check(!isError(step("move_resize_shell", Map.of("shell", "New Project", "width", 700, "height", 600))), "move_resize_shell");
        check(!isError(step("click", Map.of("class", "Button", "text", "Cancel", "shell", "New Project"))), "click Cancel closes the wizard");
        Thread.sleep(500);
        check(!Json.write(payload(step("list_shells", Map.of()))).contains("\"New Project\""), "wizard is closed");

        System.out.println("--- M4 key press closes a dialog");
        check(!isError(step("menu_select", Map.of("path", "File/New/Project..."))), "reopen wizard");
        check(!isError(step("press_key", Map.of("key", "Escape"))), "press_key Escape");
        Thread.sleep(800);
        check(!Json.write(payload(step("list_shells", Map.of()))).contains("\"New Project\""), "Escape closed the wizard");

        System.out.println("--- M4 preferences dialog");
        check(!isError(step("open_preferences", Map.of())), "open_preferences");
        check(!isError(step("dialog_dismiss", Map.of())), "dismiss preferences");

        System.out.println("--- M4 native dialog (File/Open File...)");
        check(!isError(step("menu_select", Map.of("path", "File/Import..."))), "open import wizard");
        String itree = exactClassRef("Tree", "Import");
        check(!isError(step("select", Map.of("ref", itree, "path", "General/File System"))), "select File System");
        check(!isError(step("wizard_next", Map.of())), "wizard_next");
        check(!isError(step("click", Map.of("class", "Button", "text", "Browse...", "label", "From directory", "shell", "Import"))), "click Browse...");
        boolean found = false;
        for (int i = 0; i < 10 && !found; i++) {
            Thread.sleep(500);
            found = !Json.write(payload(step("list_native_dialogs", Map.of()))).contains("\"count\":0");
        }
        check(found, "native Open dialog detected");
        Map<String, Object> blocked = step("list_shells", Map.of());
        System.out.println("while native dialog is open: " + Json.write(payload(blocked)));
        check(!isError(step("native_dialog_screenshot", Map.of())), "native_dialog_screenshot");
        String tmpDir = System.getProperty("java.io.tmpdir").replaceAll("[\\\\/]$", "");
        check(!isError(step("native_dialog_set_path", Map.of("path", tmpDir))), "native_dialog_set_path");
        check(!isError(step("native_dialog_confirm", Map.of())), "native_dialog_confirm");
        Thread.sleep(800);
        check(Json.write(payload(step("list_native_dialogs", Map.of()))).contains("\"count\":0"), "native dialog closed by confirm");
        String shellsAfter = Json.write(payload(step("read_widget_tree", Map.of("shell", "Import"))));
        System.out.println("import tree contains tmp dir: " + shellsAfter.toLowerCase().contains(tmpDir.toLowerCase().replace("\\", "\\\\")));
        check(!isError(step("click", Map.of("class", "Button", "text", "Browse...", "label", "From directory", "shell", "Import"))), "click Browse... again");
        found = false;
        for (int i = 0; i < 10 && !found; i++) {
            Thread.sleep(500);
            found = !Json.write(payload(step("list_native_dialogs", Map.of()))).contains("\"count\":0");
        }
        check(found, "native dialog detected again");
        check(!isError(step("native_dialog_press", Map.of("button", "Cancel"))), "native_dialog_press Cancel");
        Thread.sleep(500);
        found = !Json.write(payload(step("list_native_dialogs", Map.of()))).contains("\"count\":0");
        check(!found, "press Cancel closed the dialog");
        step("wizard_cancel", Map.of());
        Thread.sleep(800);
        check(Json.write(payload(step("list_native_dialogs", Map.of()))).contains("\"count\":0"), "native dialog closed");
        check(!isError(step("get_status", Map.of())), "UI responsive after native dialog");
    }

    private static String exactClassRef(String cls) throws Exception {
        return exactClassRef(cls, "New Project");
    }

    private static String exactClassRef(String cls, String shell) throws Exception {
        Map<?, ?> found = (Map<?, ?>) payload(step("find_elements", Map.of("class", cls, "shell", shell)));
        for (Object o : (List<?>) (found.get("elements") != null ? found.get("elements") : found.get("matches"))) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (cls.equals(m.get("class"))) {
                return (String) m.get("ref");
            }
        }
        throw new AssertionError("no element of class " + cls);
    }

    private static void wizardFlow() throws Exception {
        System.out.println("--- M2 wizard flow");
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            Map<?, ?> main = (Map<?, ?>) payload(callTool("read_dialog", Map.of()));
            if (main.get("title") != null && !main.get("lists").toString().equals("[]")) {
                break;
            }
            Thread.sleep(2000);
        }
        Map<String, Object> bogus = step("menu_select", Map.of("path", "File/New/zzz"));
        check(isError(bogus) && "not-found".equals(((Map<?, ?>) payload(bogus)).get("code")), "bogus menu path must be not-found");
        Map<String, Object> beforeWizShells = step("list_shells", Map.of());
        long snapBeforeWiz = ((Number) ((Map<?, ?>) payload(beforeWizShells)).get("snapshot")).longValue();
        Map<String, Object> open = step("menu_select", Map.of("path", "File/New/Project..."));
        check(!isError(open), "menu_select File/New/Project... failed");
        Map<String, Object> diffWizOpened = step("state_diff", Map.of("since", snapBeforeWiz));
        check(!isError(diffWizOpened), "state_diff after opening wizard failed");
        Map<?, ?> diffWizMap = (Map<?, ?>) payload(diffWizOpened);
        check(Boolean.TRUE.equals(diffWizMap.get("changed")), "state_diff must report changed=true after opening wizard");
        check(((List<?>) diffWizMap.get("shellsOpened")).contains("New Project"), "state_diff must report New Project opened");
        Map<?, ?> dialog = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        check(dialog.get("wizard") != null, "read_dialog did not report a wizard for the New Project dialog");
        Map<?, ?> wiz1 = (Map<?, ?>) dialog.get("wizard");
        check("Select a wizard".equals(wiz1.get("pageTitle")), "expected Select a wizard page, got " + wiz1.get("pageTitle"));

        System.out.println("--- M3 T3.2 read_tree and read_text on wizard page 1");
        Map<String, Object> wizTreePage = step("read_tree", Map.of("offset", 0, "limit", 3));
        check(!isError(wizTreePage), "read_tree on wizard failed");
        Map<?, ?> wizTreePayload = (Map<?, ?>) payload(wizTreePage);
        List<?> wizTreeItems = (List<?>) wizTreePayload.get("items");
        check(wizTreeItems.size() == 3, "expected paged limit 3 items from read_tree, got " + wizTreeItems.size());
        check(((Number) wizTreePayload.get("totalCount")).intValue() > 3, "expected totalCount > 3 on wizard tree");
        check(Boolean.TRUE.equals(wizTreePayload.get("hasMore")), "expected hasMore=true on wizard tree page 1");

        Map<String, Object> wizTreeSub = step("read_tree", Map.of("parent_path", "General", "offset", 0, "limit", 5));
        check(!isError(wizTreeSub), "read_tree with parent_path General failed");
        List<?> subItems = (List<?>) ((Map<?, ?>) payload(wizTreeSub)).get("items");
        check(!subItems.isEmpty(), "expected General category to have child items");

        Map<String, Object> wizFilterText = step("read_text", Map.of("label", "Wizards", "offset", 0, "limit", 5));
        check(!isError(wizFilterText), "read_text on wizard filter text failed");
        check("widget".equals(((Map<?, ?>) payload(wizFilterText)).get("source")), "read_text source must be widget");

        Map<String, Object> next = step("wizard_next", Map.of());
        check(!isError(next), "wizard_next failed");
        Map<?, ?> page2 = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        Map<?, ?> wiz2 = (Map<?, ?>) page2.get("wizard");
        check("Project".equals(wiz2.get("pageTitle")), "expected Project page after wizard_next, got " + wiz2.get("pageTitle"));
        Map<String, Object> nextDisabled = step("wizard_next", Map.of());
        check(isError(nextDisabled) && "disabled".equals(((Map<?, ?>) payload(nextDisabled)).get("code")), "expected disabled Next on Project page");
        Map<String, Object> back = step("wizard_back", Map.of());
        check(!isError(back), "wizard_back failed");
        Map<?, ?> page1Again = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        check("Select a wizard".equals(((Map<?, ?>) page1Again.get("wizard")).get("pageTitle")), "expected Select a wizard after wizard_back");
        Map<String, Object> cancel = step("wizard_cancel", Map.of());
        check(!isError(cancel), "wizard_cancel failed");
        List<?> closed = (List<?>) ((Map<?, ?>) payload(cancel)).get("closed");
        check(!closed.isEmpty(), "wizard_cancel did not close the wizard");
        step("list_shells", Map.of());

        System.out.println("--- M2 error/message dialog flow");
        Map<String, Object> rte = step("menu_select", Map.of("path", "RTE/RTE Code Generation..."));
        if (isError(rte)) {
            rte = step("menu_select", Map.of("path", "Run/Run"));
        }
        check(!isError(rte), "menu_select for error/message dialog failed");
        Map<?, ?> errDialog = (Map<?, ?>) payload(step("read_dialog", Map.of()));
        check("application".equals(errDialog.get("modality")) || "primary".equals(errDialog.get("modality")),
                "expected modal dialog, got " + errDialog.get("modality"));
        check(!((List<?>) errDialog.get("labels")).isEmpty(), "expected message labels in dialog");
        Map<String, Object> dismiss = step("dialog_confirm", Map.of());
        check(!isError(dismiss), "dialog_confirm failed");
        check(!((List<?>) ((Map<?, ?>) payload(dismiss)).get("closed")).isEmpty(), "dialog_confirm did not close dialog");
        step("list_shells", Map.of());

        System.out.println("--- M3 T3.1 widget tree, find_elements, read_element");
        Map<String, Object> wtree = step("read_widget_tree", Map.of("depth", 3));
        check(!isError(wtree), "read_widget_tree failed");
        Map<?, ?> rootNode = (Map<?, ?>) ((Map<?, ?>) payload(wtree)).get("root");
        check(rootNode.get("ref") != null && "Shell".equals(rootNode.get("class")), "read_widget_tree root must be Shell with ref");
        check(((Map<?, ?>) payload(wtree)).get("snapshot") instanceof Long, "read_widget_tree carries snapshot");

        Map<String, Object> found = step("find_elements", Map.of("class", "Tree"));
        check(!isError(found), "find_elements failed");
        List<?> matches = (List<?>) ((Map<?, ?>) payload(found)).get("matches");
        check(!matches.isEmpty(), "expected at least one Tree widget on main shell");
        String treeRef = (String) ((Map<?, ?>) matches.get(0)).get("ref");

        Map<String, Object> elem = step("read_element", Map.of("ref", treeRef));
        check(!isError(elem), "read_element by ref failed");
        Map<?, ?> elemMap = (Map<?, ?>) payload(elem);
        check("Tree".equals(elemMap.get("class")), "read_element class must be Tree");
        check(elemMap.get("bounds") != null, "read_element must include bounds");

        Map<String, Object> ambig = step("read_element", Map.of("class", "Tree"));
        check(isError(ambig) && "ambiguous-match".equals(((Map<?, ?>) payload(ambig)).get("code")),
                "expected ambiguous-match when multiple Trees match");

        Map<String, Object> tablesFound = step("find_elements", Map.of("class", "Table"));
        check(!isError(tablesFound), "find_elements Table failed");
        List<?> tableMatches = (List<?>) ((Map<?, ?>) payload(tablesFound)).get("matches");
        if (!tableMatches.isEmpty()) {
            String tableRef = (String) ((Map<?, ?>) tableMatches.get(0)).get("ref");
            Map<String, Object> tablePage = step("read_table", Map.of("ref", tableRef, "offset", 0, "limit", 10));
            check(!isError(tablePage), "read_table by ref failed");
            check(((Map<?, ?>) payload(tablePage)).get("columns") instanceof List<?>, "read_table must return columns list");
        }

        System.out.println("--- M3 T3.3 read_workbench, read_menu, read_toolbar, read_status_line, read_clipboard");
        Map<String, Object> wb = step("read_workbench", Map.of());
        check(!isError(wb), "read_workbench failed");
        Map<?, ?> wbMap = (Map<?, ?>) payload(wb);
        check(wbMap.get("perspective") != null, "read_workbench must report perspective");
        check(!((List<?>) wbMap.get("menu")).isEmpty(), "read_workbench must report top-level menu items");

        Map<String, Object> menu = step("read_menu", Map.of("path", "File/New", "depth", 1));
        check(!isError(menu), "read_menu File/New failed");
        List<?> menuItems = (List<?>) ((Map<?, ?>) payload(menu)).get("items");
        check(!menuItems.isEmpty(), "read_menu File/New must return items");

        Map<String, Object> toolbar = step("read_toolbar", Map.of());
        check(!isError(toolbar), "read_toolbar failed");
        List<?> toolbarItems = (List<?>) ((Map<?, ?>) payload(toolbar)).get("items");
        check(!toolbarItems.isEmpty(), "read_toolbar must return toolbar items");

        Map<String, Object> statusLine = step("read_status_line", Map.of());
        check(!isError(statusLine), "read_status_line failed");
        check(((Map<?, ?>) payload(statusLine)).get("items") instanceof List<?>, "read_status_line must return items list");

        Map<String, Object> clipboard = step("read_clipboard", Map.of());
        check(!isError(clipboard), "read_clipboard failed");
        check(((Map<?, ?>) payload(clipboard)).get("text") instanceof String, "read_clipboard must return text string");

        System.out.println("--- M3 T3.4 screenshot and state_diff");
        long sinceSnap = ((Number) ((Map<?, ?>) payload(clipboard)).get("snapshot")).longValue();
        Map<String, Object> diffNoChange = step("state_diff", Map.of("since", sinceSnap));
        check(!isError(diffNoChange), "state_diff failed");
        check(Boolean.FALSE.equals(((Map<?, ?>) payload(diffNoChange)).get("changed")), "expected changed=false on idle workbench");

        Map<String, Object> shotShell = step("screenshot", Map.of());
        check(!isError(shotShell), "screenshot of shell failed");
        Map<?, ?> shotMap = (Map<?, ?>) payload(shotShell);
        Path shotFile = Path.of((String) shotMap.get("path"));
        check(Files.isRegularFile(shotFile) && Files.size(shotFile) > 0, "screenshot PNG must exist and be non-empty");
        Files.deleteIfExists(shotFile);

        Map<String, Object> shotElem = step("screenshot", Map.of("ref", treeRef));
        check(!isError(shotElem), "screenshot of element failed");
        Path elemFile = Path.of((String) ((Map<?, ?>) payload(shotElem)).get("path"));
        check(Files.isRegularFile(elemFile) && Files.size(elemFile) > 0, "element screenshot PNG must exist and be non-empty");
        Files.deleteIfExists(elemFile);

        System.out.println("--- M3 T3.5 read_views, read_selection, read_outline, read_properties, read_editor");
        Map<String, Object> views = step("read_views", Map.of());
        check(!isError(views), "read_views failed");
        List<?> viewList = (List<?>) ((Map<?, ?>) payload(views)).get("views");
        check(!viewList.isEmpty(), "read_views must return open views");

        Map<String, Object> sel = step("read_selection", Map.of());
        check(!isError(sel), "read_selection failed");
        check(((Map<?, ?>) payload(sel)).get("kind") instanceof String, "read_selection must report kind");

        Map<String, Object> outline = step("read_outline", Map.of("offset", 0, "limit", 10));
        check(!isError(outline), "read_outline failed");
        check(((Map<?, ?>) payload(outline)).get("items") instanceof List<?>, "read_outline must return items list");

        Map<String, Object> props = step("read_properties", Map.of("offset", 0, "limit", 10));
        check(!isError(props), "read_properties failed");
        check(((Map<?, ?>) payload(props)).get("properties") instanceof List<?>, "read_properties must return properties list");

        Map<String, Object> noEditor = step("read_editor", Map.of());
        check(isError(noEditor) && "not-found".equals(((Map<?, ?>) payload(noEditor)).get("code")),
                "read_editor with no open editor must return not-found");

        System.out.println("--- M3 T3.6 read_console and read_progress");
        Map<String, Object> console = step("read_console", Map.of("offset", 0, "limit", 20));
        check(!isError(console), "read_console failed");
        check(((Map<?, ?>) payload(console)).get("consoles") instanceof List<?>, "read_console must return consoles list");

        Map<String, Object> progress = step("read_progress", Map.of("include_system", true));
        check(!isError(progress), "read_progress failed");
        Map<?, ?> progressMap = (Map<?, ?>) payload(progress);
        check(progressMap.get("jobs") instanceof List<?>, "read_progress must return jobs list");
        check(progressMap.get("idle") instanceof Boolean, "read_progress must report idle boolean");

        System.out.println("--- M3 T3.7 read_problems and read_error_log");
        Map<String, Object> problems = step("read_problems", Map.of("offset", 0, "limit", 20));
        check(!isError(problems), "read_problems failed");
        Map<?, ?> probMap = (Map<?, ?>) payload(problems);
        check(probMap.get("problems") instanceof List<?>, "read_problems must return problems list");
        check(probMap.get("counts") instanceof Map<?, ?>, "read_problems must return counts map");

        Map<String, Object> errLog = step("read_error_log", Map.of("offset", 0, "limit", 10));
        check(!isError(errLog), "read_error_log failed");
        Map<?, ?> errLogMap = (Map<?, ?>) payload(errLog);
        check(errLogMap.get("entries") instanceof List<?>, "read_error_log must return entries list");

        System.out.println("--- M3 T3.8 read_junit and T3.9 read_debug");
        Map<String, Object> junit = step("read_junit", Map.of("limit", 5));
        check(!isError(junit), "read_junit failed");
        Map<?, ?> junitMap = (Map<?, ?>) payload(junit);
        check(Boolean.TRUE.equals(junitMap.get("available")), "read_junit must report available=true on ISOLAR-AB");
        check(junitMap.get("runs") instanceof List<?>, "read_junit must return runs list");

        Map<String, Object> debug = step("read_debug", Map.of());
        check(!isError(debug), "read_debug failed");
        Map<?, ?> debugMap = (Map<?, ?>) payload(debug);
        check(debugMap.get("launches") instanceof List<?>, "read_debug must return launches list");
        check(debugMap.get("breakpoints") instanceof List<?>, "read_debug must return breakpoints list");

        System.out.println("--- M3 T3.10 all 26 observe tools while modal dialog is open");
        observeAllWithModalOpen(treeRef, sinceSnap);
    }

    private static void observeAllWithModalOpen(String mainTreeRef, long sinceSnap) throws Exception {
        Map<String, Object> openModal = step("menu_select", Map.of("path", "File/New/Project..."));
        check(!isError(openModal), "opening modal New Project wizard for T3.10 failed");
        try {
            // 1. list_shells
            Map<String, Object> r1 = step("list_shells", Map.of());
            check(!isError(r1), "modal list_shells");
            // 2. read_dialog
            Map<String, Object> r2 = step("read_dialog", Map.of());
            check(!isError(r2) && "New Project".equals(((Map<?, ?>) payload(r2)).get("title")), "modal read_dialog");
            // 3. read_widget_tree
            Map<String, Object> r3 = step("read_widget_tree", Map.of("depth", 2));
            check(!isError(r3), "modal read_widget_tree");
            // 4. find_elements
            Map<String, Object> r4 = step("find_elements", Map.of("class", "Button"));
            check(!isError(r4), "modal find_elements");
            String btnRef = (String) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) payload(r4)).get("matches")).get(0)).get("ref");
            // 5. read_element
            Map<String, Object> r5 = step("read_element", Map.of("ref", btnRef));
            check(!isError(r5), "modal read_element");
            // 6. read_text
            Map<String, Object> r6 = step("read_text", Map.of("label", "Wizards", "offset", 0, "limit", 5));
            check(!isError(r6), "modal read_text");
            // 7. read_tree
            Map<String, Object> r7 = step("read_tree", Map.of("offset", 0, "limit", 5));
            check(!isError(r7), "modal read_tree");
            // 8. read_table (query main shell table by ref or expect structured not-found on wizard shell)
            Map<String, Object> r8 = step("read_table", Map.of("offset", 0, "limit", 5));
            check(!isError(r8) || "not-found".equals(((Map<?, ?>) payload(r8)).get("code")), "modal read_table");
            // 9. read_workbench
            Map<String, Object> r9 = step("read_workbench", Map.of());
            check(!isError(r9), "modal read_workbench");
            // 10. read_menu (on main workbench shell)
            Map<String, Object> r10 = step("read_menu", Map.of("shell", "ISOLAR-A/B", "path", "File", "depth", 1));
            check(!isError(r10), "modal read_menu");
            // 11. read_toolbar (on main workbench shell)
            Map<String, Object> r11 = step("read_toolbar", Map.of("shell", "ISOLAR-A/B"));
            check(!isError(r11), "modal read_toolbar");
            // 12. read_status_line (on main workbench shell)
            Map<String, Object> r12 = step("read_status_line", Map.of("shell", "ISOLAR-A/B"));
            check(!isError(r12), "modal read_status_line");
            // 13. read_clipboard
            Map<String, Object> r13 = step("read_clipboard", Map.of());
            check(!isError(r13), "modal read_clipboard");
            // 14. screenshot
            Map<String, Object> r14 = step("screenshot", Map.of());
            check(!isError(r14), "modal screenshot");
            Files.deleteIfExists(Path.of((String) ((Map<?, ?>) payload(r14)).get("path")));
            // 15. state_diff
            Map<String, Object> r15 = step("state_diff", Map.of("since", sinceSnap));
            check(!isError(r15), "modal state_diff");
            // 16. read_editor (structured not-found when no editor open)
            Map<String, Object> r16 = step("read_editor", Map.of());
            check(!isError(r16) || "not-found".equals(((Map<?, ?>) payload(r16)).get("code")), "modal read_editor");
            // 17. read_views
            Map<String, Object> r17 = step("read_views", Map.of());
            check(!isError(r17), "modal read_views");
            // 18. read_selection
            Map<String, Object> r18 = step("read_selection", Map.of());
            check(!isError(r18), "modal read_selection");
            // 19. read_outline
            Map<String, Object> r19 = step("read_outline", Map.of("offset", 0, "limit", 5));
            check(!isError(r19), "modal read_outline");
            // 20. read_properties
            Map<String, Object> r20 = step("read_properties", Map.of("offset", 0, "limit", 5));
            check(!isError(r20), "modal read_properties");
            // 21. read_console
            Map<String, Object> r21 = step("read_console", Map.of("offset", 0, "limit", 10));
            check(!isError(r21), "modal read_console");
            // 22. read_progress
            Map<String, Object> r22 = step("read_progress", Map.of("include_system", true));
            check(!isError(r22), "modal read_progress");
            // 23. read_problems
            Map<String, Object> r23 = step("read_problems", Map.of("offset", 0, "limit", 10));
            check(!isError(r23), "modal read_problems");
            // 24. read_error_log
            Map<String, Object> r24 = step("read_error_log", Map.of("offset", 0, "limit", 5));
            check(!isError(r24), "modal read_error_log");
            // 25. read_junit
            Map<String, Object> r25 = step("read_junit", Map.of("limit", 5));
            check(!isError(r25), "modal read_junit");
            // 26. read_debug
            Map<String, Object> r26 = step("read_debug", Map.of());
            check(!isError(r26), "modal read_debug");
        } finally {
            step("wizard_cancel", Map.of());
        }
    }

    private static void check(boolean cond, String what) {
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> request(String method, Object params) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId++);
        req.put("method", method);
        req.put("params", params);
        toServer.println(Json.write(req));
        String line = fromServer.readLine();
        if (line == null) {
            throw new IOException("server closed its output");
        }
        return (Map<String, Object>) Json.parse(line);
    }

    private static Map<String, Object> callTool(String name, Map<String, Object> args) throws IOException {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("name", name);
        arguments.put("args", args);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "call_tool");
        params.put("arguments", arguments);
        return request("tools/call", params);
    }

    private static boolean isError(Map<String, Object> reply) {
        return Boolean.TRUE.equals(((Map<?, ?>) reply.get("result")).get("isError"));
    }

    private static Object payload(Map<String, Object> reply) {
        List<?> content = (List<?>) ((Map<?, ?>) reply.get("result")).get("content");
        return Json.parse((String) ((Map<?, ?>) content.get(0)).get("text"));
    }

    /** Stops only what this test started: the agent's JVM, and launcher stubs started with the driver's launch.ini for this install. */
    private static void stopInstance(long javaPid, Path launcher) {
        if (javaPid >= 0) {
            ProcessHandle.of(javaPid).ifPresent(ProcessHandle::destroyForcibly);
        }
        String exe = launcher.toAbsolutePath().toString().toLowerCase();
        for (int pass = 0; pass < 3; pass++) {
            ProcessHandle.allProcesses()
                    .filter(p -> p.info().commandLine().map(c -> c.toLowerCase().contains(exe) && c.contains("launch.ini")).orElse(false))
                    .forEach(ProcessHandle::destroyForcibly);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static List<String> snapshot(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).map(p -> {
                try {
                    return p + "|" + Files.size(p) + "|" + Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return p + "|unreadable";
                }
            }).sorted().toList();
        }
    }
}
