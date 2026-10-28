package server;

import com.bosch.eclipsedriver.protocol.Catalog;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.server.AgentInstaller;
import com.bosch.eclipsedriver.server.Journal;
import com.bosch.eclipsedriver.server.MetaTools;
import com.bosch.eclipsedriver.server.Processes;
import com.bosch.eclipsedriver.server.TraceLog;
import edtest.Assert;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class M6ServerTest {
    private static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    private static Path fakeAgentJar(Path dir) throws Exception {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Bundle-SymbolicName", "com.bosch.eclipsedriver.agent");
        mf.getMainAttributes().putValue("Bundle-Version", "0.1.0");
        Path jar = dir.resolve("agent.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            out.putNextEntry(new ZipEntry("x.txt"));
            out.write(1);
            out.closeEntry();
        }
        return jar;
    }

    public void testInstallThenUninstallRestoresBundlesInfoByteForByte() throws Exception {
        Path tmp = Files.createTempDirectory("ed-install");
        try {
            Path install = tmp.resolve("eclipse");
            Path info = install.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info");
            Files.createDirectories(info.getParent());
            Files.writeString(info, "#encoding=UTF-8\n#version=1\na.b,1.0.0,plugins/a.jar,4,false\n", StandardCharsets.UTF_8);
            String before = sha(info);
            Path jar = fakeAgentJar(tmp);

            Map<String, Object> r = AgentInstaller.install(install, jar);
            Assert.eq(true, r.get("installed"));
            Assert.isTrue(Files.readString(info).contains("com.bosch.eclipsedriver.agent,0.1.0,eclipse-driver/agent-bundle.jar,4,true"), "line added");
            Assert.isTrue(AgentInstaller.isInstalled(install), "installed");
            AgentInstaller.install(install, jar);
            Assert.eq(1, Files.readAllLines(info).stream().filter(l -> l.startsWith("com.bosch.eclipsedriver.agent,")).count() == 1 ? 1 : 0);

            AgentInstaller.uninstall(install);
            Assert.eq(before, sha(info));
            Assert.isTrue(!Files.exists(install.resolve("eclipse-driver")), "folder removed");
            Assert.eq("not-found", codeOf(() -> AgentInstaller.uninstall(install)));
        } finally {
            rm(tmp);
        }
    }

    public void testInstallWithoutBundlesInfoIsNotFound() throws Exception {
        Path tmp = Files.createTempDirectory("ed-install2");
        try {
            Path jar = fakeAgentJar(tmp);
            Assert.eq("not-found", codeOf(() -> AgentInstaller.install(tmp, jar)));
        } finally {
            rm(tmp);
        }
    }

    public void testJournalKeepsNotesInOrder() {
        Journal j = new Journal();
        j.add("one", null);
        j.add("two", "bug");
        Map<String, Object> r = j.read(10);
        Assert.eq(2L, r.get("count"));
        Assert.eq(2, ((List<?>) r.get("notes")).size());
        Assert.eq(1, ((List<?>) j.read(1).get("notes")).size());
    }

    public void testTraceWritesOnlyWhenEnabled() throws Exception {
        Path tmp = Files.createTempDirectory("ed-trace");
        try {
            TraceLog t = new TraceLog(tmp.resolve("t.jsonl"));
            t.record("click", 5, null);
            Assert.isTrue(!Files.exists(tmp.resolve("t.jsonl")), "disabled writes nothing");
            t.set(true, null);
            t.record("click", 5, null);
            t.record("save", 7, "timeout");
            List<String> lines = Files.readAllLines(tmp.resolve("t.jsonl"));
            Assert.eq(2, lines.size());
            Assert.isTrue(lines.get(1).contains("\"error\":\"timeout\""), "error recorded");
            t.set(false, null);
            Assert.isTrue(!t.enabled(), "off");
        } finally {
            rm(tmp);
        }
    }

    public void testProcessRunReadAndExit() {
        Processes p = new Processes();
        Map<String, Object> r = p.run(Map.of("script", "cmd", "args", List.of("/c", "echo", "hello-driver"), "wait", true));
        Assert.eq(true, r.get("exited"));
        Assert.eq(0L, r.get("exitCode"));
        Assert.isTrue(Json.write(r).contains("hello-driver"), "output captured");
        String id = (String) r.get("process");
        Assert.eq(0L, p.waitExit(id, Duration.ofSeconds(5)).get("exitCode"));
        Assert.eq("not-found", codeOf(() -> p.read(Map.of("process", "p99"))));
    }

    public void testProcessStopKillsLongRunner() {
        Processes p = new Processes();
        Map<String, Object> r = p.run(Map.of("script", "ping", "args", List.of("-n", "30", "127.0.0.1")));
        String id = (String) r.get("process");
        Assert.eq("timeout", codeOf(() -> p.waitExit(id, Duration.ofMillis(300))));
        Assert.eq(true, p.stop(Map.of("process", id)).get("stopped"));
    }

    public void testContextBudget() {
        MetaTools meta = new MetaTools(null);
        int metaChars = Json.write(meta.definitions()).length() + MetaTools.INSTRUCTIONS.length();
        Assert.isTrue(metaChars / 4 <= 1500, "meta-tools + instructions " + metaChars / 4 + " tokens");
        int direct = 0;
        for (var t : Catalog.all()) {
            direct += t.name().length() + t.summary().length() + Json.write(t.schema()).length();
        }
        Assert.isTrue(metaChars * 4 < direct, "meta " + metaChars + " chars should be under 25% of direct " + direct);
    }

    private static void rm(Path dir) throws Exception {
        try (var w = Files.walk(dir)) {
            for (Path p : w.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String codeOf(Runnable r) {
        try {
            r.run();
        } catch (ToolError e) {
            return e.code();
        }
        return "no error";
    }
}
