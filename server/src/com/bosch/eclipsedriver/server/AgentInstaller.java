package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * install_agent and uninstall_agent (FR-S8, 2.2). Writes only {@code <install>\eclipse-driver\} and one line in
 * bundles.info, after saving {@code bundles.info.eclipse-driver.bak}. Uninstall restores the original bytes.
 */
public final class AgentInstaller {
    public static final String FOLDER = "eclipse-driver";
    public static final String BACKUP = "bundles.info.eclipse-driver.bak";
    public static final String SYMBOLIC_NAME = "com.bosch.eclipsedriver.agent";

    private AgentInstaller() {
    }

    static Path bundlesInfo(Path install) {
        return install.resolve("configuration").resolve("org.eclipse.equinox.simpleconfigurator").resolve("bundles.info");
    }

    public static Map<String, Object> install(Path install, Path agentJar) {
        Path info = bundlesInfo(install);
        if (!Files.isRegularFile(info)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no bundles.info at " + info)
                    .hint("path must be an Eclipse install folder that uses simpleconfigurator");
        }
        Path folder = install.resolve(FOLDER);
        Path backup = info.resolveSibling(BACKUP);
        requireWritable(info.getParent());
        requireWritable(info);
        if (Files.exists(folder)) {
            requireWritable(folder);
        } else {
            requireWritable(install);
        }
        boolean createdFolder = !Files.exists(folder);
        boolean createdBackup = false;
        try {
            String version = bundleVersion(agentJar);
            Files.createDirectories(folder);
            Path jar = folder.resolve("agent-bundle.jar");
            Path tmpJar = folder.resolve("agent-bundle.jar.tmp");
            Files.copy(agentJar, tmpJar, StandardCopyOption.REPLACE_EXISTING);

            if (!Files.exists(backup)) {
                Files.copy(info, backup);
                createdBackup = true;
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(backup, StandardCharsets.UTF_8));
            lines.removeIf(l -> l.startsWith(SYMBOLIC_NAME + ","));
            lines.add(SYMBOLIC_NAME + "," + version + "," + FOLDER + "/agent-bundle.jar,4,true");
            Path tmpInfo = info.resolveSibling("bundles.info.eclipse-driver.tmp");
            Files.write(tmpInfo, lines, StandardCharsets.UTF_8);

            Files.move(tmpJar, jar, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmpInfo, info, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("installed", true);
            out.put("install", install.toString());
            out.put("agentJar", jar.toString());
            out.put("bundlesInfo", info.toString());
            out.put("backup", backup.toString());
            out.put("version", version);
            out.put("notes", "The agent stays dormant unless the driver starts the instance with its enable property.");
            return out;
        } catch (IOException | RuntimeException e) {
            rollback(folder, createdFolder, backup, createdBackup);
            if (e instanceof ToolError te) {
                throw te;
            }
            throw new ToolError(ErrorCode.INSTALL_NOT_WRITABLE, "could not write under " + install + ": " + e.getMessage())
                    .with("path", install.toString())
                    .hint("nothing was left half-written; use mode launch, or run with write access");
        }
    }

    public static Map<String, Object> uninstall(Path install) {
        Path info = bundlesInfo(install);
        Path folder = install.resolve(FOLDER);
        Path backup = info.resolveSibling(BACKUP);
        if (!Files.exists(backup) && !Files.exists(folder)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "the agent is not installed in " + install);
        }
        requireWritable(info);
        try {
            if (Files.exists(backup)) {
                Files.move(backup, info, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteTree(folder);
        } catch (IOException e) {
            throw new ToolError(ErrorCode.INSTALL_NOT_WRITABLE, "could not restore " + info + ": " + e.getMessage())
                    .with("path", info.toString());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uninstalled", true);
        out.put("install", install.toString());
        out.put("bundlesInfoSha256", sha(info));
        return out;
    }

    public static boolean isInstalled(Path install) {
        return Files.isRegularFile(install.resolve(FOLDER).resolve("agent-bundle.jar"));
    }

    static String bundleVersion(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            String v = jf.getManifest() == null ? null : jf.getManifest().getMainAttributes().getValue("Bundle-Version");
            if (v == null) {
                throw new ToolError(ErrorCode.INTERNAL_ERROR, "agent jar has no Bundle-Version: " + jar);
            }
            return v;
        }
    }

    private static void requireWritable(Path p) {
        if (!Files.isWritable(p)) {
            throw new ToolError(ErrorCode.INSTALL_NOT_WRITABLE, "not writable: " + p)
                    .with("path", p.toString())
                    .hint("install needs write access to " + p + "; nothing was changed");
        }
    }

    private static void rollback(Path folder, boolean createdFolder, Path backup, boolean createdBackup) {
        try {
            if (createdFolder) {
                deleteTree(folder);
            } else {
                Files.deleteIfExists(folder.resolve("agent-bundle.jar.tmp"));
            }
            if (createdBackup) {
                Files.deleteIfExists(backup);
            }
            Files.deleteIfExists(backup.resolveSibling("bundles.info.eclipse-driver.tmp"));
        } catch (IOException ignored) {
            // best effort: the caller already reports the original failure
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    static String sha(Path file) {
        try {
            return Extractor.sha256(file);
        } catch (IOException e) {
            return "unreadable";
        }
    }
}
