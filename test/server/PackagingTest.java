package server;

import com.bosch.eclipsedriver.server.Extractor;
import edtest.Assert;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PackagingTest {
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Extractor fake(Map<String, byte[]> files) {
        return new Extractor(name -> {
            byte[] data = files.get(name);
            if (data == null) {
                throw new FileNotFoundException(name);
            }
            return new ByteArrayInputStream(data);
        });
    }

    private static Map<String, byte[]> embedded(byte[] jar, String sha) {
        Map<String, byte[]> m = new HashMap<>();
        m.put("agent-bundle.jar", jar);
        m.put("manifest.properties", ("version=9.9\nfiles=agent-bundle.jar\nagent-bundle.jar.sha256=" + sha + "\n").getBytes(StandardCharsets.UTF_8));
        return m;
    }

    public void testExtractWritesVerifiedFilesAndIsIdempotent() throws Exception {
        Path base = Files.createTempDirectory("ed-extract");
        byte[] jar = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Extractor ex = fake(embedded(jar, sha(jar)));
        Path dir = ex.extract(base);
        Assert.isTrue(dir.getFileName().toString().startsWith("9.9-"), dir.toString());
        Assert.eq("jar-bytes", Files.readString(dir.resolve("agent-bundle.jar")));
        long before = Files.getLastModifiedTime(dir.resolve("agent-bundle.jar")).toMillis();
        Thread.sleep(20);
        ex.extract(base);
        Assert.eq(before, Files.getLastModifiedTime(dir.resolve("agent-bundle.jar")).toMillis());
    }

    public void testTamperedFileIsReExtracted() throws Exception {
        Path base = Files.createTempDirectory("ed-extract");
        byte[] jar = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Extractor ex = fake(embedded(jar, sha(jar)));
        Path file = ex.extract(base).resolve("agent-bundle.jar");
        Files.writeString(file, "tampered");
        ex.extract(base);
        Assert.eq("jar-bytes", Files.readString(file));
    }

    public void testCorruptEmbeddedJarIsRejectedAndLeavesNothing() throws Exception {
        Path base = Files.createTempDirectory("ed-extract");
        byte[] jar = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Extractor ex = fake(embedded(jar, sha("other".getBytes(StandardCharsets.UTF_8))));
        try {
            ex.extract(base);
            throw new AssertionError("expected a corrupt-jar failure");
        } catch (IOException e) {
            Assert.isTrue(e.getMessage().contains("corrupt"), e.getMessage());
        }
        Path leftovers = Files.list(base).findFirst().orElseThrow();
        Assert.isTrue(!Files.exists(leftovers.resolve("agent-bundle.jar")), "no half-written jar");
        Assert.isTrue(!Files.exists(leftovers.resolve("agent-bundle.jar.tmp")), "no temp file left");
    }

    /** Runs only when build.cmd passes -Ded.fatjar; checks the real distributable. */
    public void testFatJarEmbedsAgentWithMatchingHash() throws Exception {
        String fat = System.getProperty("ed.fatjar");
        if (fat == null) {
            System.out.println("SKIP testFatJarEmbedsAgentWithMatchingHash (no -Ded.fatjar; run build.cmd package)");
            return;
        }
        Path base = Files.createTempDirectory("ed-fat");
        Path dir;
        try (JarFile jar = new JarFile(fat)) {
            Assert.isTrue(jar.getManifest().getMainAttributes().getValue("Main-Class") != null, "Main-Class");
            Assert.isTrue(jar.getEntry("embedded/agent-bundle.jar") != null, "embedded agent-bundle.jar");
            Assert.isTrue(jar.getEntry("embedded/manifest.properties") != null, "embedded manifest.properties");
            Extractor ex = new Extractor(name -> jar.getInputStream(jar.getEntry("embedded/" + name)));
            dir = ex.extract(base);
            Assert.isTrue(Files.size(dir.resolve("agent-bundle.jar")) > 0, "agent jar extracted");
        }
        try (JarFile agent = new JarFile(dir.resolve("agent-bundle.jar").toFile())) {
            Assert.eq("com.bosch.eclipsedriver.agent", agent.getManifest().getMainAttributes().getValue("Bundle-SymbolicName").split(";")[0]);
            Set<String> allowedRoots = Set.of("META-INF/", "com/bosch/eclipsedriver/");
            for (var entries = agent.entries(); entries.hasMoreElements();) {
                JarEntry e = entries.nextElement();
                boolean parentDir = e.isDirectory() && "com/bosch/eclipsedriver/".startsWith(e.getName());
                boolean ok = parentDir || allowedRoots.stream().anyMatch(e.getName()::startsWith);
                Assert.isTrue(ok, "agent bundle holds a foreign entry: " + e.getName());
            }
        }
    }
}
