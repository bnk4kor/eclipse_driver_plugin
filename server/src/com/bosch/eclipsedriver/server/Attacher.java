package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** connect mode attach: loads the attach stub into a running JVM, which starts the agent bundle without a restart. */
public final class Attacher {
    private final Supplier<Path> agentJar;
    private final Supplier<Path> stubJar;
    private final Path workBase;
    private final AgentClient client;
    private final InstanceRegistry registry;

    public Attacher(Supplier<Path> agentJar, Supplier<Path> stubJar, Path workBase, AgentClient client, InstanceRegistry registry) {
        this.agentJar = agentJar;
        this.stubJar = stubJar;
        this.workBase = workBase;
        this.client = client;
        this.registry = registry;
    }

    public Instance attach(long pid, Duration timeout) {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        Path ready = workBase.resolve("ready-attach-" + token.substring(0, 8) + ".json");
        Path error = ready.resolveSibling(ready.getFileName() + ".error");
        try {
            Files.createDirectories(workBase);
            Files.deleteIfExists(ready);
            Files.deleteIfExists(error);
        } catch (IOException e) {
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "cannot prepare work folder " + workBase + ": " + e);
        }
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(Long.toString(pid));
            vm.loadAgent(stubJar.get().toString(), token + "|" + ready + "|" + agentJar.get());
        } catch (Exception e) {
            throw new ToolError(ErrorCode.LAUNCH_FAILED, "attach to pid " + pid + " failed: " + e)
                    .hint("the JVM may have -XX:+DisableAttachMechanism, belong to another user, or not exist")
                    .hint("use connect mode install (needs confirm_restart true) instead")
                    .with("diagnostics", diagnostics(pid));
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException ignored) {
                    // already gone
                }
            }
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!Files.exists(ready)) {
            if (Files.exists(error)) {
                throw new ToolError(ErrorCode.LAUNCH_FAILED, "the attach stub could not start the agent: " + read(error))
                        .hint("this JVM may not be an OSGi/Eclipse application; use connect mode install");
            }
            if (System.nanoTime() > deadline) {
                throw new ToolError(ErrorCode.TIMEOUT, "the agent did not report ready within " + timeout.toSeconds() + " s after attach")
                        .hint("use connect mode install (needs confirm_restart true)");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ToolError(ErrorCode.INTERNAL_ERROR, "interrupted while waiting for the agent");
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) Json.parse(read(ready));
        String install = installOf(pid);
        String workspace = workspaceOf(pid);
        Instance instance = new Instance(registry.nextId(), pid, ((Number) info.get("port")).intValue(), token, install, workspace, false);
        client.call(instance, "ping", Map.of());
        registry.add(instance);
        return instance;
    }

    /** The install folder: the folder of the nearest launcher (self or parent) that has a matching .ini, else the JVM's folder. */
    static String installOf(long pid) {
        for (ProcessHandle p = ProcessHandle.of(pid).orElse(null); p != null; p = p.parent().orElse(null)) {
            String cmd = p.info().command().orElse(null);
            if (cmd != null && cmd.toLowerCase().endsWith(".exe")
                    && java.nio.file.Files.isRegularFile(Path.of(cmd.substring(0, cmd.length() - 4) + ".ini"))) {
                return Path.of(cmd).getParent().toString();
            }
        }
        return ProcessHandle.of(pid).flatMap(h -> h.info().command()).map(c -> Path.of(c).getParent()).map(Path::toString).orElse(null);
    }

    /** The workspace from -data on the JVM command line, or from the launcher's, when given there. */
    static String workspaceOf(long pid) {
        for (ProcessHandle p = ProcessHandle.of(pid).orElse(null); p != null; p = p.parent().orElse(null)) {
            String[] a = p.info().arguments().orElse(new String[0]);
            for (int i = 0; i + 1 < a.length; i++) {
                if (a[i].equals("-data")) {
                    return a[i + 1];
                }
            }
        }
        return null;
    }

    /** Running JVMs this machine's attach API can see, with a flag for the ones that look like Eclipse applications. */
    public List<Map<String, Object>> runningJvms(long ownPid) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (VirtualMachineDescriptor d : VirtualMachine.list()) {
            long pid = Long.parseLong(d.id());
            if (pid == ownPid) {
                continue;
            }
            String name = d.displayName();
            String command = ProcessHandle.of(pid).flatMap(h -> h.info().command()).orElse("");
            String hay = (name + " " + command).toLowerCase();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pid", pid);
            row.put("displayName", name);
            row.put("command", command);
            row.put("eclipseLike", hay.contains("eclipse") || hay.contains("equinox") || hay.contains("osgi")
                    || hay.contains("isolar") || hay.contains("runtime-"));
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> diagnostics(long pid) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("processExists", ProcessHandle.of(pid).isPresent());
        d.put("visibleToAttachApi", VirtualMachine.list().stream().anyMatch(v -> v.id().equals(Long.toString(pid))));
        d.put("serverJava", System.getProperty("java.version"));
        return d;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(unreadable)";
        }
    }
}
