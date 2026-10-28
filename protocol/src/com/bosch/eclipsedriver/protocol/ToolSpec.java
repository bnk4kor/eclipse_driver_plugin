package com.bosch.eclipsedriver.protocol;

import java.util.Map;

/** One catalog entry. The schema is a JSON-schema subset: type, required, enum, properties, items. */
public record ToolSpec(String name, String category, String summary, Map<String, Object> schema, Map<String, Object> example) {
}
