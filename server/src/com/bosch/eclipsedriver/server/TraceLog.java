package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in call trace (FR-S6): one JSON line per catalog call with name, duration and outcome. */
public final class TraceLog {
    private final Path defaultFile;
    private Path file;
    private boolean enabled;

    public TraceLog(Path defaultFile) {
        this.defaultFile = defaultFile;
    }

    public synchronized Map<String, Object> set(boolean on, String path) {
        enabled = on;
        if (on) {
            file = path == null || path.isBlank() ? defaultFile : Path.of(path).toAbsolutePath();
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                enabled = false;
                throw new ToolError(ErrorCode.INTERNAL_ERROR, "cannot create trace folder " + file.getParent() + ": " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("path", enabled ? file.toString() : null);
        return out;
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized void record(String tool, long durationMs, String errorCode) {
        if (!enabled) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("time", Instant.now().toString());
        line.put("tool", tool);
        line.put("ms", durationMs);
        line.put("ok", errorCode == null);
        if (errorCode != null) {
            line.put("error", errorCode);
        }
        try {
            Files.writeString(file, Json.write(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            enabled = false;
        }
    }
}
