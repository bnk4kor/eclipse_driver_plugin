package com.bosch.eclipsedriver.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The tester's notes for the session (FR-S7). */
public final class Journal {
    private final List<Map<String, Object>> notes = new ArrayList<>();

    public synchronized Map<String, Object> add(String note, String kind) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("index", (long) notes.size() + 1);
        n.put("time", Instant.now().toString());
        n.put("kind", kind == null ? "finding" : kind);
        n.put("note", note);
        notes.add(n);
        return n;
    }

    public synchronized Map<String, Object> read(int limit) {
        int from = Math.max(0, notes.size() - limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", (long) notes.size());
        out.put("notes", new ArrayList<>(notes.subList(from, notes.size())));
        return out;
    }
}
