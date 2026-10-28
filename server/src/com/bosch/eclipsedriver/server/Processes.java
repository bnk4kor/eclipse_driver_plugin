package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Processes the driver started (run_script, read_process_output, stop_process, wait_for process_exit). */
public final class Processes {
    private static final int MAX_LINES = 20_000;

    private static final class Managed {
        final String id;
        final String command;
        final Process process;
        final List<String> lines = new ArrayList<>();
        long dropped;

        Managed(String id, String command, Process process) {
            this.id = id;
            this.command = command;
            this.process = process;
        }

        synchronized void add(String line) {
            lines.add(line);
            if (lines.size() > MAX_LINES) {
                lines.remove(0);
                dropped++;
            }
        }
    }

    private final Map<String, Managed> processes = new LinkedHashMap<>();
    private int counter;

    /** Builds the command line: batch files go through cmd.exe, everything else runs directly. */
    static List<String> commandLine(String script, List<?> args) {
        List<String> cmd = new ArrayList<>();
        String lower = script.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            cmd.addAll(List.of("cmd.exe", "/c", script));
        } else if (lower.endsWith(".ps1")) {
            cmd.addAll(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script));
        } else {
            cmd.add(script);
        }
        if (args != null) {
            for (Object a : args) {
                cmd.add(String.valueOf(a));
            }
        }
        return cmd;
    }

    public Map<String, Object> run(Map<String, Object> args) {
        String script = (String) args.get("script");
        List<?> scriptArgs = args.get("args") instanceof List<?> l ? l : List.of();
        List<String> cmd = commandLine(script, scriptArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (args.get("cwd") instanceof String cwd && !cwd.isBlank()) {
            pb.directory(Path.of(cwd).toFile());
        }
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new ToolError(ErrorCode.NOT_FOUND, "cannot start '" + script + "': " + e.getMessage())
                    .hint("give a full path to an existing script or executable");
        }
        Managed m;
        synchronized (this) {
            m = new Managed("p" + (++counter), String.join(" ", cmd), p);
            processes.put(m.id, m);
        }
        Thread reader = new Thread(() -> pump(m), "process-" + m.id);
        reader.setDaemon(true);
        reader.start();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("process", m.id);
        out.put("pid", p.pid());
        out.put("command", m.command);
        if (Boolean.TRUE.equals(args.get("wait"))) {
            long seconds = args.get("timeout_seconds") instanceof Number n ? n.longValue() : 600;
            boolean done = waitFor(p, Duration.ofSeconds(seconds));
            joinQuietly(reader);
            out.put("exited", done);
            if (done) {
                out.put("exitCode", (long) p.exitValue());
            }
            out.putAll(slice(m, 0, 200));
        } else {
            out.put("exited", !p.isAlive());
        }
        return out;
    }

    public Map<String, Object> read(Map<String, Object> args) {
        Managed m = require((String) args.get("process"));
        int since = args.get("since") instanceof Number n ? (int) n.longValue() : 0;
        int limit = args.get("limit") instanceof Number n ? (int) n.longValue() : 200;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("process", m.id);
        out.put("exited", !m.process.isAlive());
        if (!m.process.isAlive()) {
            out.put("exitCode", (long) m.process.exitValue());
        }
        out.putAll(slice(m, since, limit));
        return out;
    }

    public Map<String, Object> stop(Map<String, Object> args) {
        Managed m = require((String) args.get("process"));
        ProcessHandle handle = m.process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        m.process.destroyForcibly();
        waitFor(m.process, Duration.ofSeconds(5));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("process", m.id);
        out.put("stopped", !m.process.isAlive());
        return out;
    }

    /** wait_for process_exit: returns when the process ended or throws timeout. */
    public Map<String, Object> waitExit(String id, Duration timeout) {
        Managed m = require(id);
        long start = System.nanoTime();
        if (!waitFor(m.process, timeout)) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            throw new ToolError(ErrorCode.TIMEOUT, "wait_for 'process_exit' timed out after " + ms + " ms (process " + id + " still running)")
                    .with("condition", "process_exit")
                    .with("elapsedMs", ms);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("condition", "process_exit");
        out.put("met", true);
        out.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        out.put("process", id);
        out.put("exitCode", (long) m.process.exitValue());
        return out;
    }

    private static Map<String, Object> slice(Managed m, int since, int limit) {
        synchronized (m) {
            long base = m.dropped;
            int from = (int) Math.max(0, Math.min(m.lines.size(), since - base));
            int to = Math.min(m.lines.size(), from + Math.max(1, limit));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lines", new ArrayList<>(m.lines.subList(from, to)));
            out.put("offset", base + from);
            out.put("next", base + to);
            out.put("totalLines", base + m.lines.size());
            out.put("hasMore", to < m.lines.size());
            return out;
        }
    }

    private Managed require(String id) {
        Managed m;
        synchronized (this) {
            m = processes.get(id);
        }
        if (m == null) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no driver-started process '" + id + "'")
                    .hint("known processes: " + processes.keySet());
        }
        return m;
    }

    private static void pump(Managed m) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(m.process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = r.readLine()) != null) {
                m.add(line);
            }
        } catch (IOException e) {
            m.add("[output closed: " + e.getMessage() + "]");
        }
    }

    private static boolean waitFor(Process p, Duration timeout) {
        try {
            return p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
