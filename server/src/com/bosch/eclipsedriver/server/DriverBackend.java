package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles the server-side catalog tools itself (connect, session, process, native dialogs, install) and forwards
 * every other catalog tool to the agent of the selected instance.
 */
public final class DriverBackend implements Backend {
    private static final long DEFAULT_LAUNCH_TIMEOUT_SECONDS = 180;

    private final InstanceRegistry registry;
    private final Launcher launcher;
    private final AgentClient client;
    private final ServerLog log = new ServerLog();
    private final Journal journal = new Journal();
    private final Processes processes = new Processes();
    private final NativeDialogs nativeDialogs = new NativeDialogs();
    private TraceLog trace = new TraceLog(Path.of(System.getProperty("java.io.tmpdir"), "eclipse-driver-trace.jsonl"));
    private Attacher attacher;
    private Supplier<Path> agentJar = () -> {
        throw new ToolError(ErrorCode.INTERNAL_ERROR, "no embedded agent jar is configured");
    };

    public DriverBackend(InstanceRegistry registry, Launcher launcher, AgentClient client) {
        this.registry = registry;
        this.launcher = launcher;
        this.client = client;
    }

    /** Adds the pieces that need the extracted jars and a writable work folder. */
    public DriverBackend withPackaging(Supplier<Path> agentJar, Attacher attacher, TraceLog trace) {
        this.agentJar = agentJar;
        this.attacher = attacher;
        this.trace = trace;
        return this;
    }

    @Override
    public Object call(String tool, Map<String, Object> args, String instance) {
        long start = System.nanoTime();
        String error = null;
        try {
            return dispatch(tool, args, instance);
        } catch (ToolError e) {
            error = e.code();
            log.warn(tool + " failed: " + e.code() + ": " + e.getMessage());
            throw e;
        } finally {
            trace.record(tool, (System.nanoTime() - start) / 1_000_000, error);
        }
    }

    private Object dispatch(String tool, Map<String, Object> args, String instance) {
        return switch (tool) {
            case "connect" -> connect(args);
            case "disconnect" -> disconnect(args, instance);
            case "list_instances" -> listInstances();
            case "get_logs" -> logs(args);
            case "set_trace" -> trace.set(Boolean.TRUE.equals(args.get("enabled")), (String) args.get("path"));
            case "journal_note" -> journal.add((String) args.get("note"), (String) args.get("kind"));
            case "get_journal" -> journal.read(args.get("limit") instanceof Number n ? (int) n.longValue() : 100);
            case "install_agent" -> installAgent(args);
            case "uninstall_agent" -> AgentInstaller.uninstall(installFolder((String) args.get("path")));
            case "run_script" -> processes.run(args);
            case "read_process_output" -> processes.read(args);
            case "stop_process" -> processes.stop(args);
            case "list_native_dialogs", "native_dialog_set_path", "native_dialog_confirm", "native_dialog_cancel",
                    "native_dialog_press", "native_dialog_screenshot" -> nativeDialogs.handle(tool, args, registry.resolve(instance));
            case "wait_for" -> "process_exit".equals(args.get("condition"))
                    ? processes.waitExit(requireString(args, "process", "wait_for process_exit needs 'process'"),
                            Duration.ofSeconds(args.get("timeout_seconds") instanceof Number n ? n.longValue() : 10))
                    : forward(tool, args, instance);
            default -> forward(tool, args, instance);
        };
    }

    private Object forward(String tool, Map<String, Object> args, String instance) {
        Instance target = registry.resolve(instance);
        try {
            Object result = client.call(target, tool, args);
            if (tool.equals("get_status") && result instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("uiResponsive"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) m;
                status.put("nativeDialogs", nativeDialogs.findOpenDialogs(target.pid()));
            }
            return result;
        } catch (ToolError e) {
            if (ErrorCode.UI_THREAD_BLOCKED.equals(e.code())) {
                throw NativeDialogs.augmentUiBlocked(e, nativeDialogs.findOpenDialogs(target.pid()));
            }
            throw e;
        }
    }

    private Object connect(Map<String, Object> args) {
        String mode = (String) args.get("mode");
        long seconds = args.get("timeout_seconds") instanceof Number n ? n.longValue() : DEFAULT_LAUNCH_TIMEOUT_SECONDS;
        String workspace = (String) args.get("workspace");
        Instance started;
        switch (mode) {
            case "launch" -> {
                if (!(args.get("path") instanceof String path)) {
                    throw new ToolError(ErrorCode.INVALID_ARGS, "mode launch needs 'path' (install folder or launcher executable)");
                }
                started = launcher.launch(Path.of(path), workspace, Duration.ofSeconds(seconds));
            }
            case "attach" -> {
                if (!(args.get("pid") instanceof Number pid)) {
                    throw new ToolError(ErrorCode.INVALID_ARGS, "mode attach needs 'pid' (see list_instances)");
                }
                if (attacher == null) {
                    throw new ToolError(ErrorCode.INTERNAL_ERROR, "attach is not configured in this server");
                }
                started = attacher.attach(pid.longValue(), Duration.ofSeconds(Math.min(seconds, 60)));
            }
            case "install" -> started = connectInstall(args, workspace, seconds);
            default -> throw new ToolError(ErrorCode.INVALID_ARGS, "unknown connect mode '" + mode + "'");
        }
        log.info("connected " + started.id() + " pid " + started.pid() + " mode " + mode);
        Object status = client.call(started, "get_status", Map.of());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance", started.id());
        out.put("pid", started.pid());
        out.put("mode", mode);
        out.put("install", started.install());
        out.put("workspace", started.workspace());
        out.put("status", status);
        out.put("notes", mode.equals("attach")
                ? "Attached to a running instance; no restart. The agent is loaded in memory only and nothing was written to the install."
                : "Started from the install's .ini. Command-line arguments the user passed outside the .ini are not recovered.");
        return out;
    }

    private Instance connectInstall(Map<String, Object> args, String workspace, long seconds) {
        if (!Boolean.TRUE.equals(args.get("confirm_restart"))) {
            throw new ToolError(ErrorCode.RESTART_NOT_CONFIRMED,
                    "mode install registers the agent in the install and restarts the instance")
                    .hint("call connect again with confirm_restart true; unsaved work in a running instance is lost")
                    .hint("the relaunch uses the install's .ini; arguments given outside the .ini are not recovered");
        }
        Path launcherPath;
        boolean running = false;
        if (args.get("pid") instanceof Number pid) {
            launcherPath = launcherOf(pid.longValue());
            running = true;
        } else if (args.get("path") instanceof String p) {
            launcherPath = Path.of(p);
        } else {
            throw new ToolError(ErrorCode.INVALID_ARGS, "mode install needs 'path' or 'pid'");
        }
        Path install = launcherInstall(launcherPath);
        if (running) {
            stopTree(((Number) args.get("pid")).longValue());
        }
        AgentInstaller.install(install, agentJar.get());
        return launcher.relaunchInstalled(launcherPath, workspace, Duration.ofSeconds(seconds));
    }

    /** The launcher executable of a running Eclipse: the process itself or the nearest parent that has a matching .ini. */
    private static Path launcherOf(long pid) {
        ProcessHandle h = ProcessHandle.of(pid).orElseThrow(() -> new ToolError(ErrorCode.NOT_FOUND, "no process with pid " + pid));
        for (ProcessHandle p = h; p != null; p = p.parent().orElse(null)) {
            String command = p.info().command().orElse(null);
            if (command != null && command.toLowerCase().endsWith(".exe")) {
                Path ini = Path.of(command.substring(0, command.length() - 4) + ".ini");
                if (Files.isRegularFile(ini)) {
                    return Path.of(command);
                }
            }
        }
        throw new ToolError(ErrorCode.NOT_FOUND, "cannot find the launcher executable of pid " + pid)
                .hint("pass 'path' (install folder) instead of 'pid'");
    }

    private static void stopTree(long pid) {
        ProcessHandle.of(pid).ifPresent(h -> {
            List<ProcessHandle> all = new ArrayList<>();
            h.parent().ifPresent(all::add);
            all.add(h);
            all.forEach(ProcessHandle::destroy);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (h.isAlive() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (h.isAlive()) {
                all.forEach(ProcessHandle::destroyForcibly);
            }
        });
    }

    private Object disconnect(Map<String, Object> args, String instance) {
        Instance target = registry.resolve(instance);
        registry.remove(target.id());
        boolean stop = Boolean.TRUE.equals(args.get("stop"));
        boolean stopped = false;
        if (stop && target.launchedByDriver()) {
            stopTree(target.pid());
            stopped = true;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance", target.id());
        out.put("disconnected", true);
        out.put("stopped", stopped);
        if (stop && !target.launchedByDriver()) {
            out.put("note", "the instance was not launched by the driver, so it was left running");
        }
        return out;
    }

    private Object listInstances() {
        List<Object> connected = new ArrayList<>();
        for (Instance i : registry.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instance", i.id());
            row.put("pid", i.pid());
            row.put("install", i.install());
            row.put("workspace", i.workspace());
            row.put("launchedByDriver", i.launchedByDriver());
            row.put("alive", ProcessHandle.of(i.pid()).map(ProcessHandle::isAlive).orElse(false));
            row.put("agentLoaded", true);
            connected.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connected", connected);
        if (attacher != null) {
            List<Object> running = new ArrayList<>();
            for (Map<String, Object> jvm : attacher.runningJvms(ProcessHandle.current().pid())) {
                long pid = ((Number) jvm.get("pid")).longValue();
                jvm.put("agentLoaded", registry.all().stream().anyMatch(i -> i.pid() == pid));
                running.add(jvm);
            }
            out.put("runningJvms", running);
        }
        return out;
    }

    private Object logs(Map<String, Object> args) {
        Long since = args.get("since_seconds") instanceof Number n ? n.longValue() : null;
        int limit = args.get("limit") instanceof Number n ? (int) n.longValue() : 100;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lines", log.read((String) args.get("level"), since, limit));
        return out;
    }

    private Object installAgent(Map<String, Object> args) {
        if (!Boolean.TRUE.equals(args.get("confirm"))) {
            throw new ToolError(ErrorCode.RESTART_NOT_CONFIRMED, "install_agent writes into the install folder")
                    .hint("call again with confirm true; only eclipse-driver\\ and one bundles.info line are written, with a backup");
        }
        return AgentInstaller.install(installFolder((String) args.get("path")), agentJar.get());
    }

    /** Accepts an install folder or its launcher executable. */
    private static Path installFolder(String path) {
        Path p = Path.of(path);
        return Files.isDirectory(p) ? p.toAbsolutePath() : launcherInstall(p);
    }

    private static Path launcherInstall(Path launcherPath) {
        try {
            return Launcher.findLauncher(launcherPath).install();
        } catch (java.io.IOException e) {
            throw new ToolError(ErrorCode.NOT_FOUND, "cannot resolve install for " + launcherPath + ": " + e.getMessage());
        }
    }

    private static String requireString(Map<String, Object> args, String key, String message) {
        if (args.get(key) instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new ToolError(ErrorCode.INVALID_ARGS, message);
    }
}
