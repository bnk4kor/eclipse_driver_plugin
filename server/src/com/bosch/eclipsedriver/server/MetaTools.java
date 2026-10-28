package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.Catalog;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.SchemaValidator;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.protocol.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The three MCP-level tools: browse the catalog, describe tools, call a tool. */
public final class MetaTools {
    public static final String INSTRUCTIONS =
            "Use list_tools to browse, describe_tool for schemas, call_tool to run. Start with connect.";

    private static final String LIST_SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"category\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"}}}";
    private static final String DESCRIBE_SCHEMA = "{\"type\":\"object\",\"required\":[\"names\"],\"properties\":{"
            + "\"names\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}";
    private static final String CALL_SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{"
            + "\"name\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"},\"instance\":{\"type\":\"string\"}}}";

    private final Backend backend;

    public MetaTools(Backend backend) {
        this.backend = backend;
    }

    /** MCP tool definitions for tools/list. */
    public List<Object> definitions() {
        return List.of(
                def("list_tools", "Browse the tool catalog. No arguments: categories with counts. With category or query: name and one-line summary per match.", LIST_SCHEMA),
                def("describe_tool", "Full schema, description and example call for 1 to 8 catalog tools.", DESCRIBE_SCHEMA),
                def("call_tool", "Run a catalog tool. args are checked against its schema first.", CALL_SCHEMA));
    }

    private static Map<String, Object> def(String name, String description, String schemaJson) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("inputSchema", Json.parse(schemaJson));
        return m;
    }

    /** Runs a meta-tool and returns its structured result; throws ToolError on failure. */
    public Object run(String tool, Object arguments) {
        String schema = switch (tool) {
            case "list_tools" -> LIST_SCHEMA;
            case "describe_tool" -> DESCRIBE_SCHEMA;
            case "call_tool" -> CALL_SCHEMA;
            default -> throw new ToolError(ErrorCode.UNKNOWN_TOOL, "no MCP tool named '" + tool + "'")
                    .hint("the MCP tools are list_tools, describe_tool and call_tool");
        };
        @SuppressWarnings("unchecked")
        Map<String, Object> args = arguments == null ? new LinkedHashMap<>() : (Map<String, Object>) arguments;
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) Json.parse(schema);
        List<String> problems = SchemaValidator.validate(schemaMap, args);
        if (!problems.isEmpty()) {
            throw new ToolError(ErrorCode.INVALID_ARGS, String.join("; ", problems)).with("schema", schemaMap);
        }
        return switch (tool) {
            case "list_tools" -> listTools(args);
            case "describe_tool" -> describeTool(args);
            default -> callTool(args);
        };
    }

    private Object listTools(Map<String, Object> args) {
        String category = (String) args.get("category");
        String query = (String) args.get("query");
        Map<String, Object> out = new LinkedHashMap<>();
        if (category == null && query == null) {
            List<Object> cats = new ArrayList<>();
            Catalog.categoryCounts().forEach((name, count) -> {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("category", name);
                c.put("tools", count);
                cats.add(c);
            });
            out.put("categories", cats);
            return out;
        }
        List<Object> tools = new ArrayList<>();
        for (ToolSpec t : Catalog.find(category, query)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", t.name());
            row.put("category", t.category());
            row.put("summary", t.summary());
            tools.add(row);
        }
        out.put("tools", tools);
        return out;
    }

    private Object describeTool(Map<String, Object> args) {
        List<?> names = (List<?>) args.get("names");
        if (names.isEmpty() || names.size() > 8) {
            throw new ToolError(ErrorCode.INVALID_ARGS, "names must hold 1 to 8 tool names, got " + names.size());
        }
        List<Object> described = new ArrayList<>();
        for (Object n : names) {
            ToolSpec t = Catalog.require(String.valueOf(n));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", t.name());
            row.put("category", t.category());
            row.put("summary", t.summary());
            row.put("schema", t.schema());
            row.put("example", t.example());
            described.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", described);
        return out;
    }

    private Object callTool(Map<String, Object> args) {
        String name = (String) args.get("name");
        Object toolArgs = args.get("args") == null ? new LinkedHashMap<String, Object>() : args.get("args");
        Catalog.checkCall(name, toolArgs);
        @SuppressWarnings("unchecked")
        Map<String, Object> toolArgMap = (Map<String, Object>) toolArgs;
        return backend.call(name, toolArgMap, (String) args.get("instance"));
    }
}
