package com.bosch.eclipsedriver.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured tool failure: {code, message, hints} plus optional extra fields such as a schema or candidates. */
@SuppressWarnings("serial")
public final class ToolError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final List<String> hints = new ArrayList<>();
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ToolError(String code, String message) {
        super(message);
        this.code = code;
    }

    public ToolError hint(String hint) {
        hints.add(hint);
        return this;
    }

    public ToolError with(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", getMessage());
        m.put("hints", new ArrayList<>(hints));
        m.putAll(details);
        return m;
    }
}
