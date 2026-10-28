package agent;

import com.bosch.eclipsedriver.agent.core.RpcServer;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class RpcServerTest {
    private static final String TOKEN = "s3cret";

    private final RpcServer server = new RpcServer(TOKEN, (method, params) -> {
        if (method.equals("boom")) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no such thing").hint("try again");
        }
        if (method.equals("crash")) {
            throw new IllegalStateException("bug");
        }
        return Map.of("echo", method, "n", params.getOrDefault("n", 0L));
    });

    private HttpResponse<String> post(int port, String token, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            b.header(RpcServer.TOKEN_HEADER, token);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(String json) {
        return (Map<String, Object>) Json.parse(json);
    }

    public void testBindsLoopbackOnly() throws Exception {
        try (server) {
            server.start();
            Assert.isTrue(server.address().isLoopbackAddress(), "must bind loopback, was " + server.address());
        }
    }

    public void testGoodTokenGetsResult() throws Exception {
        try (server) {
            int port = server.start();
            HttpResponse<String> r = post(port, TOKEN, "/rpc", "{\"method\":\"ping\",\"params\":{\"n\":5}}");
            Assert.eq(200, r.statusCode());
            Map<String, Object> reply = obj(r.body());
            Assert.eq(true, reply.get("ok"));
            Assert.eq(Map.of("echo", "ping", "n", 5L), reply.get("result"));
        }
    }

    public void testWrongOrMissingTokenIsRejectedWithoutRunningHandler() throws Exception {
        try (server) {
            int port = server.start();
            Assert.eq(401, post(port, "nope", "/rpc", "{\"method\":\"ping\"}").statusCode());
            HttpResponse<String> none = post(port, null, "/rpc", "{\"method\":\"ping\"}");
            Assert.eq(401, none.statusCode());
            Assert.eq("unauthorized", ((Map<?, ?>) obj(none.body()).get("error")).get("code"));
        }
    }

    public void testOtherPathIsNotFound() throws Exception {
        try (server) {
            int port = server.start();
            Assert.eq(404, post(port, TOKEN, "/other", "{}").statusCode());
        }
    }

    public void testToolErrorsAndBugsBecomeStructuredErrors() throws Exception {
        try (server) {
            int port = server.start();
            Map<String, Object> notFound = obj(post(port, TOKEN, "/rpc", "{\"method\":\"boom\"}").body());
            Assert.eq(false, notFound.get("ok"));
            Assert.eq("not-found", ((Map<?, ?>) notFound.get("error")).get("code"));
            Assert.eq("internal-error", ((Map<?, ?>) obj(post(port, TOKEN, "/rpc", "{\"method\":\"crash\"}").body()).get("error")).get("code"));
            Assert.eq("invalid-args", ((Map<?, ?>) obj(post(port, TOKEN, "/rpc", "not json").body()).get("error")).get("code"));
            Assert.eq("invalid-args", ((Map<?, ?>) obj(post(port, TOKEN, "/rpc", "{\"params\":{}}").body()).get("error")).get("code"));
        }
    }

    public void testBlankTokenIsRefused() {
        Assert.fails(() -> new RpcServer(" ", (m, p) -> null), "token");
    }
}
