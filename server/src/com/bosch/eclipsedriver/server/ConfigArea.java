package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Builds the driver's own configuration area from the install's, with the agent bundle added. The install is only read. */
public final class ConfigArea {
    private ConfigArea() {
    }

    public static void prepare(Path install, Path cfg, Path agentJar) throws IOException {
        Path source = install.resolve("configuration");
        Path configIni = source.resolve("config.ini");
        if (!Files.isRegularFile(configIni)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no configuration\\config.ini under " + install)
                    .hint("path must be an Eclipse install folder or its launcher executable");
        }
        Files.createDirectories(cfg);
        deleteTreeIfExists(cfg.resolve("org.eclipse.osgi"));
        String entry = "reference:file:" + agentJar.toAbsolutePath().toString().replace('\\', '/') + "@4:start";
        // shortcut: assumes osgi.bundles is one line (as Equinox writes it), upgrade if an install uses continuation lines
        List<String> lines = new ArrayList<>(Files.readAllLines(configIni, StandardCharsets.ISO_8859_1));
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("osgi.bundles=")) {
                String value = lines.get(i).substring("osgi.bundles=".length());
                lines.set(i, "osgi.bundles=" + (value.isBlank() ? "" : value + ",") + entry);
                found = true;
            }
        }
        if (!found) {
            lines.add("osgi.bundles=" + entry);
        }
        Files.write(cfg.resolve("config.ini"), lines, StandardCharsets.ISO_8859_1);

        Path simple = source.resolve("org.eclipse.equinox.simpleconfigurator");
        if (Files.isDirectory(simple)) {
            copyTree(simple, cfg.resolve(simple.getFileName()));
        }
    }

    private static void deleteTreeIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
