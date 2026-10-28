package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCP over stdio: one JSON-RPC message per line in, one per line out. */
public final class McpServer {
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private final MetaTools tools;

    public McpServer(MetaTools tools) {
        this.tools = tools;
    }

    public void run(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        PrintStream writer = new PrintStream(out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String reply = handle(line);
            if (reply != null) {
                writer.println(reply);
            }
        }
    }

    /** Handles one request line; returns the reply line, or null for notifications. */
    public String handle(String line) {
        Object id = null;
        try {
            Object parsed;
            try {
                parsed = Json.parse(line);
            } catch (IllegalArgumentException e) {
                return error(null, -32700, e.getMessage());
            }
            if (!(parsed instanceof Map<?, ?> request)) {
                return error(null, -32600, "request must be a JSON object");
            }
            id = request.get("id");
            String method = (String) request.get("method");
            boolean notification = !request.containsKey("id");
            Object params = request.get("params");
            if (method == null) {
                return notification ? null : error(id, -32600, "missing method");
            }
            Object result = switch (method) {
                case "initialize" -> initialize(params);
                case "ping" -> new LinkedHashMap<String, Object>();
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(params);
                default -> {
                    if (notification) {
                        yield null;
                    }
                    yield new RpcError(-32601, "method not found: " + method);
                }
            };
            if (notification) {
                return null;
            }
            if (result instanceof RpcError e) {
                return error(id, e.code, e.message);
            }
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("jsonrpc", "2.0");
            reply.put("id", id);
            reply.put("result", result);
            return Json.write(reply);
        } catch (RuntimeException e) {
            return error(id, -32603, "internal error: " + e);
        }
    }

    private record RpcError(int code, String message) {
    }

    private Object initialize(Object params) {
        String version = DEFAULT_PROTOCOL_VERSION;
        if (params instanceof Map<?, ?> p && p.get("protocolVersion") instanceof String v) {
            version = v;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", version);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", "eclipse-driver", "version", "0.1.0"));
        result.put("instructions", MetaTools.INSTRUCTIONS);
        return result;
    }

    private Object toolsList() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools.definitions());
        return result;
    }

    private Object toolsCall(Object params) {
        if (!(params instanceof Map<?, ?> p) || !(p.get("name") instanceof String name)) {
            return new RpcError(-32602, "tools/call needs params.name");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        boolean isError = false;
        Object payload;
        try {
            payload = tools.run(name, p.get("arguments"));
        } catch (ToolError e) {
            payload = e.toMap();
            isError = true;
        } catch (RuntimeException e) {
            payload = new ToolError(ErrorCode.INTERNAL_ERROR, String.valueOf(e)).toMap();
            isError = true;
        }
        result.put("content", List.of(Map.of("type", "text", "text", Json.write(payload))));
        result.put("isError", isError);
        return result;
    }

    private static String error(Object id, int code, String message) {
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("jsonrpc", "2.0");
        reply.put("id", id);
        reply.put("error", Map.of("code", code, "message", message));
        return Json.write(reply);
    }
}
