package com.bosch.eclipsedriver.protocol;

import java.util.List;
import java.util.Map;

/** Checks args against the schema subset used by the catalog: type, required, enum, properties, items. */
public final class SchemaValidator {
    private SchemaValidator() {
    }

    /** Returns one message per problem, empty when the value is valid. */
    public static List<String> validate(Map<String, Object> schema, Object value) {
        List<String> problems = new java.util.ArrayList<>();
        check(schema, value, "args", problems);
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static void check(Map<String, Object> schema, Object value, String path, List<String> problems) {
        Object type = schema.get("type");
        if (type != null && !matchesType((String) type, value)) {
            problems.add(path + " must be " + type + " but is " + describe(value));
            return;
        }
        Object allowed = schema.get("enum");
        if (allowed instanceof List<?> list && !list.contains(value)) {
            problems.add(path + " must be one of " + list + " but is " + Json.write(value));
        }
        if (value instanceof Map<?, ?> obj) {
            Object required = schema.get("required");
            if (required instanceof List<?> names) {
                for (Object n : names) {
                    if (!obj.containsKey(n)) {
                        problems.add(path + "." + n + " is required");
                    }
                }
            }
            Object props = schema.get("properties");
            if (props instanceof Map<?, ?> propMap) {
                for (Map.Entry<?, ?> e : obj.entrySet()) {
                    Object sub = propMap.get(e.getKey());
                    if (sub == null) {
                        problems.add(path + "." + e.getKey() + " is not a known argument");
                    } else {
                        check((Map<String, Object>) sub, e.getValue(), path + "." + e.getKey(), problems);
                    }
                }
            }
        }
        if (value instanceof List<?> arr && schema.get("items") instanceof Map<?, ?> itemSchema) {
            for (int i = 0; i < arr.size(); i++) {
                check((Map<String, Object>) itemSchema, arr.get(i), path + "[" + i + "]", problems);
            }
        }
    }

    private static boolean matchesType(String type, Object v) {
        return switch (type) {
            case "object" -> v instanceof Map;
            case "array" -> v instanceof List;
            case "string" -> v instanceof String;
            case "boolean" -> v instanceof Boolean;
            case "integer" -> v instanceof Long || v instanceof Integer;
            case "number" -> v instanceof Number;
            default -> throw new IllegalArgumentException("unsupported schema type " + type);
        };
    }

    private static String describe(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Map) {
            return "object";
        }
        if (v instanceof List) {
            return "array";
        }
        if (v instanceof String) {
            return "string";
        }
        if (v instanceof Boolean) {
            return "boolean";
        }
        return v instanceof Long || v instanceof Integer ? "integer" : "number";
    }
}
