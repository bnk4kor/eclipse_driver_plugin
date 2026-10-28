package com.bosch.eclipsedriver.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded in-memory log of the server (FR-S5). Never writes to stdout, which carries the MCP protocol. */
public final class ServerLog {
    private static final int CAPACITY = 2000;
    private static final List<String> LEVELS = List.of("error", "warn", "info", "debug");

    private final List<Map<String, Object>> entries = new ArrayList<>();

    public synchronized void log(String level, String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", Instant.now().toString());
        e.put("epochMs", System.currentTimeMillis());
        e.put("level", level);
        e.put("message", message);
        entries.add(e);
        if (entries.size() > CAPACITY) {
            entries.remove(0);
        }
    }

    public void info(String message) {
        log("info", message);
    }

    public void warn(String message) {
        log("warn", message);
    }

    public void error(String message) {
        log("error", message);
    }

    /** Entries at or above the given severity (error is the most severe), newer than the age, newest last, at most limit. */
    public synchronized List<Map<String, Object>> read(String level, Long sinceSeconds, int limit) {
        int max = level == null ? LEVELS.indexOf("debug") : LEVELS.indexOf(level);
        long cutoff = sinceSeconds == null ? 0 : System.currentTimeMillis() - sinceSeconds * 1000;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            boolean levelOk = LEVELS.indexOf((String) e.get("level")) <= max;
            boolean ageOk = ((Long) e.get("epochMs")) >= cutoff;
            if (levelOk && ageOk) {
                out.add(e);
            }
        }
        return new ArrayList<>(out.subList(Math.max(0, out.size() - limit), out.size()));
    }
}
