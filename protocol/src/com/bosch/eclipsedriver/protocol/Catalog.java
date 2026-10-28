package com.bosch.eclipsedriver.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tool catalog: single source for the server, the agent, the meta-tools and the skill reference files.
 * Params are written as "name:type[!][(a|b)]" separated by commas: "!" marks required, "(a|b)" an enum.
 */
public final class Catalog {
    public static final String SESSION = "session";
    public static final String OBSERVE = "observe";
    public static final String OBSERVE_ECLIPSE = "observe-eclipse";
    public static final String WORKBENCH = "act-workbench";
    public static final String DIALOGS = "act-dialogs-wizards";
    public static final String LAUNCH = "launch-process";
    public static final String POINTER = "act-pointer-keyboard";
    public static final String WIDGETS = "act-widgets";
    public static final String WAIT = "wait";
    public static final String NATIVE = "native-dialogs";
    private static final String TARGET = "ref:string,shell:string,text:string,label:string,id:string,class:string,tooltip:string";

    private static final Map<String, ToolSpec> TOOLS = new LinkedHashMap<>();

    static {
        add("connect", SESSION, "Launch, attach to or install the agent into an Eclipse instance; returns an instance id.",
                "mode:string!(launch|attach|install),path:string,pid:integer,workspace:string,timeout_seconds:integer,confirm_restart:boolean",
                "{\"mode\":\"launch\",\"path\":\"A:\\\\tools\\\\ISOLAR-AB\"}");
        add("disconnect", SESSION, "Detach from an instance; stops it only if the driver launched it and stop is true.",
                "stop:boolean", "{\"stop\":false}");
        add("list_instances", SESSION, "Connected instances and running Eclipse JVMs: pid, product, launch name, agent loaded.",
                "", "{}");
        add("get_status", SESSION, "Instance state, agent version, UI-thread responsiveness and modal state.",
                "", "{}");
        add("get_logs", SESSION, "Server and agent log lines, filtered by level and age.",
                "level:string(error|warn|info|debug),since_seconds:integer,limit:integer", "{\"level\":\"warn\",\"limit\":50}");
        add("set_trace", SESSION, "Turn the opt-in call trace (every call with timing) on or off; optionally set the trace file.",
                "enabled:boolean!,path:string", "{\"enabled\":true}");
        add("journal_note", SESSION, "Record a tester finding, feedback or step in the session journal.",
                "note:string!,kind:string(finding|feedback|step|bug)", "{\"note\":\"Finish stays disabled\",\"kind\":\"bug\"}");
        add("get_journal", SESSION, "Read the session journal notes.",
                "limit:integer", "{}");
        add("install_agent", SESSION, "Place the agent in an install directory and register it (backup kept). Needs confirm true.",
                "path:string!,confirm:boolean", "{\"path\":\"A:\\\\tools\\\\ISOLAR-AB\",\"confirm\":true}");
        add("uninstall_agent", SESSION, "Remove the agent from an install directory and restore the original files from backup.",
                "path:string!", "{\"path\":\"A:\\\\tools\\\\ISOLAR-AB\"}");
        add("run_script", LAUNCH, "Start a script or executable with arguments; returns a process id. Waits for exit when wait is true.",
                "script:string!,args:array,cwd:string,wait:boolean,timeout_seconds:integer", "{\"script\":\"test.cmd\",\"wait\":true}");
        add("read_process_output", LAUNCH, "Read buffered output lines of a process the driver started, from a line index.",
                "process:string!,since:integer,limit:integer", "{\"process\":\"p1\",\"since\":0}");
        add("stop_process", LAUNCH, "Stop a process the driver started.",
                "process:string!", "{\"process\":\"p1\"}");
        add("list_launch_configs", LAUNCH, "Launch configurations known to the instance with name and type.",
                "", "{}");
        add("launch", LAUNCH, "Run or debug a launch configuration by name.",
                "name:string!,mode:string(run|debug)", "{\"name\":\"MyTests\",\"mode\":\"run\"}");
        add("terminate_launch", LAUNCH, "Terminate a running launch by configuration name (all running launches if omitted).",
                "name:string", "{}");
        add("list_shells", OBSERVE, "All open shells and dialogs with title, modality, active flag and bounds.",
                "", "{}");
        add("read_dialog", OBSERVE, "Read a dialog or wizard: message, labels, buttons, inputs, tables, trees, banners and wizard page state. Default: the modal or active shell.",
                "shell:string", "{\"shell\":\"New Project\"}");
        add("read_widget_tree", OBSERVE, "Bounded-depth widget tree of a shell or subtree root with stable element refs.",
                "shell:string,root:string,depth:integer", "{\"depth\":4}");
        add("find_elements", OBSERVE, "Find widgets in a shell matching text, label, id, class or tooltip (case-insensitive substring); returns element refs.",
                "shell:string,text:string,label:string,id:string,class:string,tooltip:string", "{\"class\":\"Button\"}");
        add("read_element", OBSERVE, "Read one widget's full state (text, label, enabled, visible, focus, bounds, tooltip, selection) by ref or matcher.",
                "ref:string,shell:string,text:string,label:string,id:string,class:string,tooltip:string", "{\"ref\":\"e1\"}");
        add("read_text", OBSERVE, "Read paged text lines of a Text, StyledText, active editor or console.",
                "ref:string,source:string(widget|editor|console),shell:string,text:string,label:string,id:string,class:string,tooltip:string,offset:integer,limit:integer",
                "{\"source\":\"editor\",\"offset\":0,\"limit\":100}");
        add("read_tree", OBSERVE, "Read a paged slice of a Tree or TreeItem without expanding collapsed lazy nodes.",
                "ref:string,parent_path:string,shell:string,text:string,label:string,id:string,tooltip:string,offset:integer,limit:integer,depth:integer",
                "{\"offset\":0,\"limit\":50}");
        add("read_table", OBSERVE, "Read columns and a paged slice of rows from a Table.",
                "ref:string,shell:string,text:string,label:string,id:string,tooltip:string,offset:integer,limit:integer",
                "{\"offset\":0,\"limit\":50}");
        add("read_workbench", OBSERVE, "Read active workbench window, perspective, open editors with dirty flag, toolbar and menu items, and status line.",
                "", "{}");
        add("read_menu", OBSERVE, "Read the menu bar structure or a submenu by path without clicking items.",
                "path:string,shell:string,depth:integer", "{\"path\":\"File/New\",\"depth\":2}");
        add("read_toolbar", OBSERVE, "Read toolbar items (ref, label, tooltip, style, enabled, checked) in a shell.",
                "shell:string,ref:string", "{}");
        add("read_status_line", OBSERVE, "Read status line text and visible labels at the bottom of a workbench shell.",
                "shell:string", "{}");
        add("read_clipboard", OBSERVE, "Read current plain-text content of the system clipboard.",
                "", "{}");
        add("screenshot", OBSERVE, "Capture a PNG screenshot of a shell, an element or a screen region.",
                "path:string,ref:string,shell:string,x:integer,y:integer,width:integer,height:integer", "{}");
        add("state_diff", OBSERVE, "Return what changed since a given snapshot id.",
                "since:integer!", "{\"since\":1}");
        add("read_editor", OBSERVE_ECLIPSE, "Read active or named editor: path, dirty flag, cursor, selection, form-editor pages and paged content.",
                "title:string,id:string,offset:integer,limit:integer", "{}");
        add("read_views", OBSERVE_ECLIPSE, "Read open views in the active workbench page with id, title, active flag and control ref.",
                "", "{}");
        add("read_selection", OBSERVE_ECLIPSE, "Read the current selection in the active workbench part or a named view.",
                "part_id:string", "{}");
        add("read_outline", OBSERVE_ECLIPSE, "Read the Outline view tree items (paged).",
                "parent_path:string,offset:integer,limit:integer,depth:integer", "{\"offset\":0,\"limit\":50}");
        add("read_properties", OBSERVE_ECLIPSE, "Read the Properties view entries and tabbed property sections (paged).",
                "offset:integer,limit:integer", "{\"offset\":0,\"limit\":50}");
        add("read_console", OBSERVE_ECLIPSE, "Read Console view output with console switcher and paged lines.",
                "console:string,offset:integer,limit:integer", "{\"offset\":0,\"limit\":100}");
        add("read_progress", OBSERVE_ECLIPSE, "Read running Eclipse jobs, progress monitors and Progress view entries.",
                "include_system:boolean", "{}");
        add("read_problems", OBSERVE_ECLIPSE, "Read workspace problem markers and Problems view entries with severity, message and resource (paged).",
                "severity:string(error|warning|info|all),offset:integer,limit:integer", "{\"offset\":0,\"limit\":50}");
        add("read_error_log", OBSERVE_ECLIPSE, "Read Eclipse Error Log / workspace .log entries with severity, plugin, message and stack trace (paged).",
                "severity:string(error|warning|info|all),offset:integer,limit:integer", "{\"offset\":0,\"limit\":50}");
        add("read_junit", OBSERVE_ECLIPSE, "Read JUnit test runs, counts, failures and stack traces.",
                "limit:integer", "{}");
        add("read_debug", OBSERVE_ECLIPSE, "Read active debug launches, threads, stack frames, variables and breakpoints.",
                "launch:string,frame_limit:integer", "{}");
        add("click", POINTER, "Click a widget by ref or matcher. Returns once any dialog it opens appears.",
                TARGET + ",button:string(left|middle|right),modifiers:string,x:integer,y:integer", "{\"label\":\"Browse...\"}");
        add("double_click", POINTER, "Double-click a widget, tree item or table row.",
                TARGET, "{\"ref\":\"e1\"}");
        add("right_click_menu", POINTER, "Right-click a widget and read the context menu items it shows (menu is left open unless path is given).",
                TARGET + ",path:string", "{\"ref\":\"e1\"}");
        add("hover", POINTER, "Move the pointer over a widget so tooltips appear.",
                TARGET, "{\"ref\":\"e1\"}");
        add("drag_drop", POINTER, "Drag one widget or item onto another widget or item.",
                "from_ref:string!,to_ref:string!", "{\"from_ref\":\"e1\",\"to_ref\":\"e2\"}");
        add("scroll", POINTER, "Scroll a widget by lines.",
                TARGET + ",direction:string(up|down|left|right),amount:integer", "{\"ref\":\"e1\",\"direction\":\"down\",\"amount\":3}");
        add("press_key", POINTER, "Send one real key event (such as Enter or F5) with optional modifiers to a widget or the focus.",
                TARGET + ",key:string!,modifiers:string", "{\"key\":\"Enter\"}");
        add("key_chord", POINTER, "Send a key chord such as Ctrl+Shift+S.",
                TARGET + ",chord:string!", "{\"chord\":\"Ctrl+S\"}");
        add("type_text", POINTER, "Type text character by character into the focused widget or a target.",
                TARGET + ",value:string!", "{\"value\":\"hello\"}");
        add("set_text", POINTER, "Set the value of a Text, Combo or StyledText directly.",
                TARGET + ",value:string!", "{\"label\":\"Name\",\"value\":\"demo\"}");
        add("focus", POINTER, "Give keyboard focus to a widget or bring a shell to the front.",
                TARGET, "{\"ref\":\"e1\"}");
        add("move_resize_shell", POINTER, "Move and resize a shell.",
                "shell:string,x:integer,y:integer,width:integer,height:integer", "{\"width\":900,\"height\":700}");
        add("maximize_minimize", POINTER, "Maximize, minimize or restore a shell.",
                "state:string!(maximize|minimize|restore),shell:string", "{\"state\":\"maximize\"}");
        add("select", WIDGETS, "Select an item in a combo, list, table, tree or tab folder by item text, index or tree path.",
                TARGET + ",item:string,index:integer,path:string", "{\"label\":\"Perspective\",\"item\":\"Java\"}");
        add("check", WIDGETS, "Check a check box, radio button or toggle.",
                TARGET, "{\"label\":\"Enable\"}");
        add("uncheck", WIDGETS, "Uncheck a check box or toggle.",
                TARGET, "{\"label\":\"Enable\"}");
        add("expand", WIDGETS, "Expand a tree node by ref or tree path.",
                TARGET + ",path:string", "{\"path\":\"General/Workspace\"}");
        add("collapse", WIDGETS, "Collapse a tree node by ref or tree path.",
                TARGET + ",path:string", "{\"path\":\"General\"}");
        add("menu_select", WORKBENCH, "Select a menu item by path such as File/New/Project... Returns once a dialog opens or after a short settle time.",
                "path:string!,shell:string", "{\"path\":\"File/New/Project...\"}");
        add("context_menu_select", WORKBENCH, "Right-click a widget and select an item of its context menu by path.",
                TARGET + ",path:string!", "{\"ref\":\"e1\",\"path\":\"Delete\"}");
        add("toolbar_click", WORKBENCH, "Click a toolbar item by ref, label or tooltip.",
                "ref:string,label:string,tooltip:string,shell:string", "{\"tooltip\":\"Save\"}");
        add("open_view", WORKBENCH, "Open a view by id or title and activate it.",
                "view:string!", "{\"view\":\"org.eclipse.ui.console.ConsoleView\"}");
        add("activate_view", WORKBENCH, "Bring an open view to the front by id or title.",
                "view:string!", "{\"view\":\"Problems\"}");
        add("close_view", WORKBENCH, "Close a view by id or title.",
                "view:string!", "{\"view\":\"Problems\"}");
        add("open_perspective", WORKBENCH, "Switch to a perspective by id or label.",
                "perspective:string!", "{\"perspective\":\"AUTOSAR\"}");
        add("reset_perspective", WORKBENCH, "Reset the active perspective to its default layout.",
                "", "{}");
        add("open_editor", WORKBENCH, "Open a workspace file or absolute path in an editor.",
                "path:string!,editor_id:string", "{\"path\":\"/proj/readme.txt\"}");
        add("save", WORKBENCH, "Save the active editor. Reports the dialog it opens, if any.",
                "", "{}");
        add("save_all", WORKBENCH, "Save all dirty editors.",
                "", "{}");
        add("close_editor", WORKBENCH, "Close the active or named editor; reports a save prompt instead of deciding for you.",
                "title:string,all:boolean", "{\"title\":\"readme.txt\"}");
        add("run_command", WORKBENCH, "Execute an Eclipse command by id.",
                "command:string!", "{\"command\":\"org.eclipse.ui.file.save\"}");
        add("quick_access", WORKBENCH, "Search Quick Access (Ctrl+3) and run a result.",
                "query:string!,select:string", "{\"query\":\"console\"}");
        add("open_preferences", WORKBENCH, "Open the Preferences dialog, optionally on a page.",
                "page:string", "{\"page\":\"General\"}");
        add("set_preference", WORKBENCH, "Set a preference value by node and key; returns the previous value.",
                "node:string!,key:string!,value:string!", "{\"node\":\"org.eclipse.ui.editors\",\"key\":\"tabWidth\",\"value\":\"4\"}");
        add("undo", WORKBENCH, "Undo in the active editor or workbench.",
                "", "{}");
        add("redo", WORKBENCH, "Redo in the active editor or workbench.",
                "", "{}");
        add("wait_for", WAIT, "Wait for a condition with a timeout; always returns a result or a timeout error.",
                "condition:string!(shell_opened|shell_closed|text_appears|element_enabled|jobs_idle|ui_idle|console_contains|editor_open|junit_finished|process_exit),"
                        + "title:string,text:string,ref:string,shell:string,label:string,id:string,class:string,tooltip:string,console:string,process:string,timeout_seconds:integer",
                "{\"condition\":\"shell_opened\",\"title\":\"Progress\",\"timeout_seconds\":30}");
        add("list_native_dialogs", NATIVE, "Native OS dialogs of the target process: title, owner pid, path field, buttons.",
                "", "{}");
        add("native_dialog_set_path", NATIVE, "Type a path into the file field of a native dialog.",
                "path:string!,title:string", "{\"path\":\"C:\\\\temp\\\\a.arxml\"}");
        add("native_dialog_confirm", NATIVE, "Press Open, Save or OK in a native dialog.",
                "title:string", "{}");
        add("native_dialog_cancel", NATIVE, "Cancel a native dialog.",
                "title:string", "{}");
        add("native_dialog_press", NATIVE, "Press a named button in a native dialog.",
                "button:string!,title:string", "{\"button\":\"Yes\"}");
        add("native_dialog_screenshot", NATIVE, "Screenshot of a native dialog to a PNG file.",
                "title:string,path:string", "{}");
        for (String step : new String[] {"next", "back", "finish", "cancel"}) {
            add("wizard_" + step, DIALOGS, "Press the wizard's " + step + " button. Returns the dialogs that opened or closed as a result.",
                    "shell:string", "{}");
        }
        add("dialog_confirm", DIALOGS, "Confirm the current dialog: its default button, else OK, Yes, Open, Save or Finish. Reports any dialog that opens next.",
                "shell:string", "{}");
        add("dialog_dismiss", DIALOGS, "Dismiss the current dialog: Cancel, Close or No, else close the shell. Reports any dialog that opens next.",
                "shell:string", "{}");
    }

    private Catalog() {
    }

    private static void add(String name, String category, String summary, String params, String exampleJson) {
        @SuppressWarnings("unchecked")
        Map<String, Object> example = (Map<String, Object>) Json.parse(exampleJson);
        TOOLS.put(name, new ToolSpec(name, category, summary, schema(params), example));
    }

    private static Map<String, Object> schema(String params) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Object> required = new ArrayList<>();
        for (String p : params.isEmpty() ? new String[0] : params.split(",(?![^(]*\\))")) {
            int colon = p.indexOf(':');
            String name = p.substring(0, colon);
            String rest = p.substring(colon + 1);
            Map<String, Object> prop = new LinkedHashMap<>();
            int paren = rest.indexOf('(');
            if (paren >= 0) {
                prop.put("enum", new ArrayList<Object>(List.of(rest.substring(paren + 1, rest.length() - 1).split("\\|"))));
                rest = rest.substring(0, paren);
            }
            if (rest.endsWith("!")) {
                required.add(name);
                rest = rest.substring(0, rest.length() - 1);
            }
            Map<String, Object> ordered = new LinkedHashMap<>();
            ordered.put("type", rest);
            ordered.putAll(prop);
            props.put(name, ordered);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    public static List<ToolSpec> all() {
        return Collections.unmodifiableList(new ArrayList<>(TOOLS.values()));
    }

    public static ToolSpec get(String name) {
        return TOOLS.get(name);
    }

    /** Returns the tool when the name exists and args fit its schema; otherwise throws unknown-tool or invalid-args. */
    public static ToolSpec checkCall(String name, Object args) {
        ToolSpec spec = require(name);
        List<String> problems = SchemaValidator.validate(spec.schema(), args);
        if (!problems.isEmpty()) {
            throw new ToolError(ErrorCode.INVALID_ARGS, String.join("; ", problems))
                    .hint("fix the arguments to match the schema and call again")
                    .with("schema", spec.schema());
        }
        return spec;
    }

    /** Returns the tool or throws unknown-tool with the closest names. */
    public static ToolSpec require(String name) {
        ToolSpec spec = TOOLS.get(name);
        if (spec == null) {
            List<String> near = closest(String.valueOf(name), 3);
            throw new ToolError(ErrorCode.UNKNOWN_TOOL, "no catalog tool named '" + name + "'")
                    .hint("did you mean " + near + "? Use list_tools to browse.")
                    .with("closest", near);
        }
        return spec;
    }

    /** Category name to tool count, sorted by name. */
    public static Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (ToolSpec t : TOOLS.values()) {
            counts.merge(t.category(), 1, Integer::sum);
        }
        return counts;
    }

    /** Tools in the given category (null for all) whose name or summary contains the query (null for any). */
    public static List<ToolSpec> find(String category, String query) {
        List<ToolSpec> out = new ArrayList<>();
        for (ToolSpec t : TOOLS.values()) {
            boolean categoryOk = category == null || t.category().equals(category);
            boolean queryOk = query == null
                    || t.name().toLowerCase().contains(query.toLowerCase())
                    || t.summary().toLowerCase().contains(query.toLowerCase());
            if (categoryOk && queryOk) {
                out.add(t);
            }
        }
        return out;
    }

    /** Up to {@code limit} catalog names closest to the given name, best first. */
    public static List<String> closest(String name, int limit) {
        List<String> names = new ArrayList<>(TOOLS.keySet());
        names.sort((a, b) -> Integer.compare(distance(name, a), distance(name, b)));
        return new ArrayList<>(names.subList(0, Math.min(limit, names.size())));
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[b.length()];
    }
}
