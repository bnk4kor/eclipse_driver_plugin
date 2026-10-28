package com.bosch.eclipsedriver.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        InstanceRegistry registry = new InstanceRegistry();
        AgentClient client = new AgentClient();
        Path base = Extractor.defaultBase();
        Supplier<Path> extracted = () -> {
            try {
                return Extractor.embedded().extract(base);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        Supplier<Path> agentJar = () -> extracted.get().resolve("agent-bundle.jar");
        Supplier<Path> stubJar = () -> extracted.get().resolve("attach-stub.jar");
        Launcher launcher = new Launcher(agentJar, base.resolve("work"), client, registry);
        Attacher attacher = new Attacher(agentJar, stubJar, base.resolve("work"), client, registry);
        DriverBackend backend = new DriverBackend(registry, launcher, client)
                .withPackaging(agentJar, attacher, new TraceLog(base.resolve("trace").resolve("trace.jsonl")));
        new McpServer(new MetaTools(backend)).run(System.in, System.out);
    }
}
