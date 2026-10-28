package server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.server.McpServer;
import com.bosch.eclipsedriver.server.MetaTools;
import edtest.Assert;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McpServerTest {
    private final List<String> backendCalls = new ArrayList<>();

    private McpServer server() {
        return new McpServer(new MetaTools((tool, args, instance) -> {
            backendCalls.add(tool + " " + Json.write(args) + " " + instance);
            if (tool.equals("get_status")) {
                return Map.of("state", "ready");
            }
            throw new ToolError(ErrorCode.NOT_CONNECTED, "no instance is connected");
        }));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(String json) {
        return (Map<String, Object>) Json.parse(json);
    }

    private Map<String, Object> rpc(String method, String params) {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\",\"params\":" + params + "}";
        return obj(server().handle(line));
    }

    /** Parses the JSON payload carried in the text content of a tools/call result. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> reply) {
        Map<String, Object> result = (Map<String, Object>) reply.get("result");
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        return obj((String) content.get(0).get("text"));
    }

    private static boolean isError(Map<String, Object> reply) {
        return (Boolean) ((Map<?, ?>) reply.get("result")).get("isError");
    }

    public void testInitializeCarriesInstructions() {
        Map<String, Object> reply = rpc("initialize", "{\"protocolVersion\":\"2024-11-05\"}");
        Map<?, ?> result = (Map<?, ?>) reply.get("result");
        Assert.eq(7L, reply.get("id"));
        Assert.eq("2024-11-05", result.get("protocolVersion"));
        Assert.eq(MetaTools.INSTRUCTIONS, result.get("instructions"));
        Assert.isTrue(MetaTools.INSTRUCTIONS.contains("Start with connect"), "instructions text");
    }

    public void testToolsListRegistersExactlyThree() {
        Map<?, ?> result = (Map<?, ?>) rpc("tools/list", "{}").get("result");
        List<?> tools = (List<?>) result.get("tools");
        Assert.eq(3, tools.size());
        Assert.eq("list_tools", ((Map<?, ?>) tools.get(0)).get("name"));
        Assert.eq("describe_tool", ((Map<?, ?>) tools.get(1)).get("name"));
        Assert.eq("call_tool", ((Map<?, ?>) tools.get(2)).get("name"));
    }

    public void testNotificationGetsNoReply() {
        Assert.eq(null, server().handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    public void testUnknownMethodAndBadJson() {
        Map<?, ?> err = (Map<?, ?>) rpc("nope", "{}").get("error");
        Assert.eq(-32601L, err.get("code"));
        Map<?, ?> parse = (Map<?, ?>) obj(server().handle("{oops")).get("error");
        Assert.eq(-32700L, parse.get("code"));
    }

    @SuppressWarnings("unchecked")
    public void testListToolsCategoriesThenQuery() {
        Map<String, Object> all = payload(rpc("tools/call", "{\"name\":\"list_tools\",\"arguments\":{}}"));
        Assert.eq(com.bosch.eclipsedriver.protocol.Catalog.categoryCounts().size(), ((List<?>) all.get("categories")).size());
        Map<String, Object> hit = payload(rpc("tools/call", "{\"name\":\"list_tools\",\"arguments\":{\"query\":\"shells\"}}"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) hit.get("tools");
        Assert.eq(1, rows.size());
        Assert.eq("list_shells", rows.get(0).get("name"));
        Assert.isTrue(!rows.get(0).containsKey("schema"), "list_tools must not return schemas");
    }

    public void testDescribeToolReturnsSchemaAndAllowsToolsWithRequiredArgs() {
        Map<String, Object> reply = rpc("tools/call", "{\"name\":\"describe_tool\",\"arguments\":{\"names\":[\"connect\",\"list_shells\"]}}");
        Assert.isTrue(!isError(reply), "describe_tool must succeed for connect");
        List<?> tools = (List<?>) payload(reply).get("tools");
        Assert.eq(2, tools.size());
        Assert.isTrue(((Map<?, ?>) tools.get(0)).containsKey("schema"), "schema");
        Assert.isTrue(((Map<?, ?>) tools.get(0)).containsKey("example"), "example");
    }

    public void testDescribeToolRejectsMoreThanEightAndUnknownNames() {
        String nine = "\"get_status\",".repeat(8) + "\"get_status\"";
        Map<String, Object> many = rpc("tools/call", "{\"name\":\"describe_tool\",\"arguments\":{\"names\":[" + nine + "]}}");
        Assert.isTrue(isError(many), "9 names must fail");
        Assert.eq("invalid-args", payload(many).get("code"));
        Map<String, Object> unknown = rpc("tools/call", "{\"name\":\"describe_tool\",\"arguments\":{\"names\":[\"list_shel\"]}}");
        Assert.eq("unknown-tool", payload(unknown).get("code"));
    }

    public void testCallToolValidatesBeforeBackend() {
        Map<String, Object> bad = rpc("tools/call", "{\"name\":\"call_tool\",\"arguments\":{\"name\":\"connect\",\"args\":{}}}");
        Assert.isTrue(isError(bad), "bad args must fail");
        Assert.eq("invalid-args", payload(bad).get("code"));
        Assert.isTrue(payload(bad).containsKey("schema"), "invalid-args carries the schema");
        Map<String, Object> unknown = rpc("tools/call", "{\"name\":\"call_tool\",\"arguments\":{\"name\":\"list_shel\"}}");
        Assert.eq("unknown-tool", payload(unknown).get("code"));
        Assert.eq(0, backendCalls.size());
    }

    public void testCallToolForwardsToBackendAndReturnsBackendErrors() {
        McpServer s = server();
        Map<String, Object> ok = obj(s.handle("{\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"call_tool\",\"arguments\":{\"name\":\"get_status\",\"instance\":\"i1\"}}}"));
        Assert.eq("ready", payload(ok).get("state"));
        Assert.eq("get_status {} i1", backendCalls.get(0));
        Map<String, Object> notConnected = obj(s.handle("{\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"call_tool\",\"arguments\":{\"name\":\"list_shells\"}}}"));
        Assert.isTrue(isError(notConnected), "backend error");
        Assert.eq("not-connected", payload(notConnected).get("code"));
    }

    public void testRunLoopReadsLinesAndWritesReplies() throws Exception {
        String in = "{\"id\":1,\"method\":\"ping\"}\n\n{\"method\":\"notifications/initialized\"}\n{\"id\":2,\"method\":\"tools/list\"}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        server().run(new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)), out);
        String[] lines = out.toString(StandardCharsets.UTF_8).split("\\R");
        Assert.eq(2, lines.length);
        Assert.eq(1L, obj(lines[0]).get("id"));
        Assert.eq(2L, obj(lines[1]).get("id"));
    }
}
