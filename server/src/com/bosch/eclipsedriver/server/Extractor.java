package com.bosch.eclipsedriver.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/**
 * Extracts the embedded jars (listed in manifest.properties) to a versioned folder, verifying SHA-256.
 * A file that already exists with the right hash is left alone, so a running target keeps its jar.
 */
public final class Extractor {
    /** Opens an embedded resource by name, relative to the embedded folder. */
    public interface Resources {
        InputStream open(String name) throws IOException;
    }

    private final Resources resources;

    public Extractor(Resources resources) {
        this.resources = resources;
    }

    public static Extractor embedded() {
        return new Extractor(name -> {
            InputStream in = Extractor.class.getResourceAsStream("/embedded/" + name);
            if (in == null) {
                throw new FileNotFoundException("embedded resource missing: " + name);
            }
            return in;
        });
    }

    public static Path defaultBase() {
        String local = System.getenv("LOCALAPPDATA");
        Path root = local != null ? Path.of(local) : Path.of(System.getProperty("user.home"), ".local");
        return root.resolve("eclipse-driver");
    }

    /** Extracts all listed files to {@code base/<version>/} and returns that folder. */
    public Path extract(Path base) throws IOException {
        Properties manifest = new Properties();
        try (InputStream in = resources.open("manifest.properties")) {
            manifest.load(in);
        }
        String version = require(manifest, "version");
        Path target = base.resolve(version);
        Files.createDirectories(target);
        for (String file : require(manifest, "files").split(",")) {
            file = file.trim();
            String expected = require(manifest, file + ".sha256");
            Path out = target.resolve(file);
            if (Files.exists(out) && sha256(out).equals(expected)) {
                continue;
            }
            Path tmp = target.resolve(file + ".tmp");
            try (InputStream in = resources.open(file)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            String actual = sha256(tmp);
            if (!actual.equals(expected)) {
                Files.deleteIfExists(tmp);
                throw new IOException("embedded " + file + " is corrupt: sha256 " + actual + " but manifest says " + expected);
            }
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String require(Properties p, String key) throws IOException {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IOException("manifest.properties has no '" + key + "'");
        }
        return v.trim();
    }
}
