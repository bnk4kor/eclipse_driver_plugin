package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Starts an Eclipse install with the agent, without writing into the install:
 * own launcher ini, own configuration area, agent jar from the extracted folder.
 */
public final class Launcher {
    /** The launcher executable and its ini. */
    public record Target(Path exe, Path ini) {
        public Path install() {
            return exe.toAbsolutePath().getParent();
        }
    }

    private final Supplier<Path> agentJar;
    private final Path workBase;
    private final AgentClient client;
    private final InstanceRegistry registry;

    public Launcher(Supplier<Path> agentJar, Path workBase, AgentClient client, InstanceRegistry registry) {
        this.agentJar = agentJar;
        this.workBase = workBase;
        this.client = client;
        this.registry = registry;
    }

    /** Accepts an install folder, a launcher executable or its ini. */
    public static Target findLauncher(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            List<Target> found = new ArrayList<>();
            try (Stream<Path> files = Files.list(path)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    String name = p.getFileName().toString();
                    if (name.toLowerCase().endsWith(".exe")) {
                        Path ini = path.resolve(name.substring(0, name.length() - 4) + ".ini");
                        if (Files.isRegularFile(ini)) {
                            found.add(new Target(p, ini));
                        }
                    }
                }
            }
            if (found.isEmpty()) {
                throw new ToolError(ErrorCode.NOT_FOUND, "no launcher (.exe with a matching .ini) in " + path);
            }
            if (found.size() > 1) {
                List<String> names = found.stream().map(t -> t.exe().getFileName().toString()).sorted().toList();
                throw new ToolError(ErrorCode.AMBIGUOUS_MATCH, "several launchers in " + path + ": " + names)
                        .hint("pass the path of the launcher executable you want")
                        .with("candidates", names);
            }
            return found.get(0);
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        Path dir = path.toAbsolutePath().getParent();
        Path exe = dir.resolve(base + ".exe");
        Path ini = dir.resolve(base + ".ini");
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(ini)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "need both " + exe + " and " + ini);
        }
        return new Target(exe, ini);
    }

    public Instance launch(Path path, String workspace, Duration timeout) {
        try {
            return doLaunch(path, workspace, timeout);
        } catch (IOException e) {
            throw new ToolError(ErrorCode.LAUNCH_FAILED, "launch failed: " + e);
        }
    }

    /** Install mode: starts the install with its own configuration (agent already installed), plus the enable property. */
    public Instance relaunchInstalled(Path path, String workspace, Duration timeout) {
        try {
            return start(findLauncher(path), workspace, timeout, false);
        } catch (IOException e) {
            throw new ToolError(ErrorCode.LAUNCH_FAILED, "relaunch failed: " + e);
        }
    }

    private Instance doLaunch(Path path, String workspace, Duration timeout) throws IOException {
        return start(findLauncher(path), workspace, timeout, true);
    }

    private Instance start(Target target, String workspace, Duration timeout, boolean ownConfig) throws IOException {
        Path install = target.install();
        Path work = workBase.resolve(Integer.toHexString(install.toString().toLowerCase().hashCode()));
        Files.createDirectories(work);
        Path cfg = work.resolve("cfg");
        if (ownConfig) {
            ConfigArea.prepare(install, cfg, agentJar.get());
        }

        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        Path ready = work.resolve("ready-" + token.substring(0, 8) + ".json");
        Files.deleteIfExists(ready);

        List<String> ini = LauncherIni.build(Files.readAllLines(target.ini(), StandardCharsets.UTF_8),
                ownConfig ? slash(cfg) : null, workspace == null ? null : slash(Path.of(workspace)), token, slash(ready));
        Path ownIni = work.resolve("launch.ini");
        Files.write(ownIni, ini, StandardCharsets.UTF_8);

        Path log = work.resolve("launch.log");
        Process process = new ProcessBuilder(target.exe().toString(), "--launcher.ini", ownIni.toString())
                .directory(install.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        long deadline = System.nanoTime() + timeout.toNanos();
        while (!Files.exists(ready)) {
            if (!process.isAlive() && process.exitValue() != 0) {
                throw failure("the launcher exited with code " + process.exitValue() + " before the agent started", log);
            }
            if (System.nanoTime() > deadline) {
                process.destroy();
                throw failure("the agent did not report ready within " + timeout.toSeconds() + " s", log);
            }
            sleep(250);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) Json.parse(Files.readString(ready, StandardCharsets.UTF_8));
        Instance instance = new Instance(registry.nextId(), ((Number) info.get("pid")).longValue(),
                ((Number) info.get("port")).intValue(), token, install.toString(), workspace, true);
        client.call(instance, "ping", Map.of());
        registry.add(instance);
        return instance;
    }

    private static ToolError failure(String message, Path log) {
        return new ToolError(ErrorCode.LAUNCH_FAILED, message)
                .hint("launcher output tail: " + tail(log, 20))
                .hint("full launcher output: " + log);
    }

    private static String tail(Path log, int lines) {
        try {
            List<String> all = Files.readAllLines(log, StandardCharsets.UTF_8);
            String text = String.join(" | ", all.subList(Math.max(0, all.size() - lines), all.size()));
            return text.isBlank() ? "(empty)" : text;
        } catch (IOException e) {
            return "(unreadable)";
        }
    }

    private static String slash(Path p) {
        return p.toAbsolutePath().toString().replace('\\', '/');
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "interrupted while waiting for the agent");
        }
    }
}
