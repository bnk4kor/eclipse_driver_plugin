package server;

import com.bosch.eclipsedriver.agent.core.RpcServer;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.server.AgentClient;
import com.bosch.eclipsedriver.server.DriverBackend;
import com.bosch.eclipsedriver.server.Instance;
import com.bosch.eclipsedriver.server.InstanceRegistry;
import com.bosch.eclipsedriver.server.Launcher;
import edtest.Assert;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DriverBackendTest {
    private final InstanceRegistry registry = new InstanceRegistry();
    private final AgentClient client = new AgentClient();
    private final DriverBackend backend = new DriverBackend(registry, new Launcher(() -> Path.of("none.jar"), Path.of("none"), client, registry), client);

    private static String codeOf(Runnable r) {
        try {
            r.run();
        } catch (ToolError e) {
            return e.code();
        }
        return "no error";
    }

    public void testNoInstanceIsNotConnected() {
        Assert.eq("not-connected", codeOf(() -> backend.call("list_shells", Map.of(), null)));
    }

    public void testConnectModesOtherThanLaunchAreRejectedForNow() {
        Assert.eq("invalid-args", codeOf(() -> backend.call("connect", Map.of("mode", "attach"), null)));
        Assert.eq("restart-not-confirmed", codeOf(() -> backend.call("connect", Map.of("mode", "install", "path", "x"), null)));
        Assert.eq("invalid-args", codeOf(() -> backend.call("connect", Map.of("mode", "launch"), null)));
    }

    public void testConnectLaunchWithBadPathIsNotFound() {
        Assert.eq("not-found", codeOf(() -> backend.call("connect", Map.of("mode", "launch", "path", "A:\\does\\not\\exist"), null)));
    }

    public void testCallsAreForwardedWithTokenAndErrorsKeepTheirShape() throws Exception {
        try (RpcServer agent = new RpcServer("tok", (method, params) -> {
            if (method.equals("list_shells")) {
                return Map.of("shells", List.of());
            }
            throw new ToolError(ErrorCode.STALE_REF, "gone").hint("re-read").with("snapshot", 3L);
        })) {
            int port = agent.start();
            registry.add(new Instance("i1", 1, port, "tok", "x", null, true));
            Assert.eq(Map.of("shells", List.of()), backend.call("list_shells", Map.of(), null));
            Assert.eq(Map.of("shells", List.of()), backend.call("list_shells", Map.of(), "i1"));
            try {
                backend.call("read_dialog", Map.of(), null);
                throw new AssertionError("expected stale-ref");
            } catch (ToolError e) {
                Assert.eq("stale-ref", e.code());
                Assert.eq(List.of("re-read"), e.toMap().get("hints"));
                Assert.eq(3L, e.toMap().get("snapshot"));
            }
            Assert.eq("not-found", codeOf(() -> backend.call("list_shells", Map.of(), "i9")));
        }
    }

    public void testWrongTokenAndDeadAgentAreStructuredErrors() throws Exception {
        int port;
        try (RpcServer agent = new RpcServer("tok", (m, p) -> Map.of())) {
            port = agent.start();
            registry.add(new Instance("i1", 1, port, "wrong", "x", null, true));
            Assert.eq("unauthorized", codeOf(() -> backend.call("get_status", Map.of(), null)));
        }
        Assert.eq("not-connected", codeOf(() -> backend.call("get_status", Map.of(), null)));
    }
}
