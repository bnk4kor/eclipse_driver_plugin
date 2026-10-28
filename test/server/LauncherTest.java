package server;

import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.server.ConfigArea;
import com.bosch.eclipsedriver.server.Launcher;
import com.bosch.eclipsedriver.server.LauncherIni;
import edtest.Assert;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LauncherTest {
    private static final List<String> INI = List.of("-startup", "plugins/launcher.jar", "--launcher.library", "plugins/lib",
            "-vm", "./javalibs/jdk/bin/javaw.exe", "-vmargs", "-Xmx1g", "-DtoolName=X");

    public void testIniInsertsOwnOptionsBeforeVmAndPropsAfterVmargs() {
        List<String> out = LauncherIni.build(INI, "C:/cfg", "C:/ws", "tok", "C:/ready.json");
        Assert.eq(List.of("-startup", "plugins/launcher.jar", "--launcher.library", "plugins/lib",
                "-configuration", "C:/cfg", "-data", "C:/ws",
                "-vm", "./javalibs/jdk/bin/javaw.exe", "-vmargs",
                "-Declipse.driver.token=tok", "-Declipse.driver.ready=C:/ready.json", "-Xmx1g", "-DtoolName=X"), out);
    }

    public void testIniReplacesExistingConfigurationAndKeepsDataWhenNoWorkspaceGiven() {
        List<String> in = List.of("-configuration", "old", "-data", "@user.home/ws", "-vm", "java", "-vmargs", "-Xmx1g");
        List<String> out = LauncherIni.build(in, "C:/cfg", null, "t", "r");
        Assert.eq(List.of("-data", "@user.home/ws", "-configuration", "C:/cfg", "-vm", "java", "-vmargs",
                "-Declipse.driver.token=t", "-Declipse.driver.ready=r", "-Xmx1g"), out);
        List<String> withWs = LauncherIni.build(in, "C:/cfg", "C:/ws", "t", "r");
        Assert.eq(List.of("-configuration", "C:/cfg", "-data", "C:/ws"), withWs.subList(0, 4));
        Assert.isTrue(!withWs.contains("old") && !withWs.contains("@user.home/ws"), "old values dropped");
    }

    public void testIniWithoutVmOrVmargsGetsBothAppended() {
        List<String> out = LauncherIni.build(List.of("-startup", "x.jar"), "C:/cfg", null, "t", "r");
        Assert.eq(List.of("-startup", "x.jar", "-configuration", "C:/cfg", "-vmargs",
                "-Declipse.driver.token=t", "-Declipse.driver.ready=r"), out);
    }

    private static Path fakeInstall() throws Exception {
        Path install = Files.createTempDirectory("ed-install");
        Files.createDirectories(install.resolve("configuration/org.eclipse.equinox.simpleconfigurator"));
        Files.writeString(install.resolve("configuration/config.ini"),
                "eclipse.product=p\nosgi.bundles=reference\\:file\\:a.jar@1\\:start\nosgi.startLevel=10\n", StandardCharsets.ISO_8859_1);
        Files.writeString(install.resolve("configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"), "x,1,plugins/x.jar,4,false\n");
        Files.writeString(install.resolve("App.exe"), "");
        Files.writeString(install.resolve("App.ini"), "-vm\njava\n");
        return install;
    }

    public void testConfigAreaAddsAgentAndLeavesInstallUntouched() throws Exception {
        Path install = fakeInstall();
        String before = Files.readString(install.resolve("configuration/config.ini"), StandardCharsets.ISO_8859_1);
        Path cfg = Files.createTempDirectory("ed-cfg");
        ConfigArea.prepare(install, cfg, Path.of("C:\\x\\agent-bundle.jar"));
        List<String> lines = Files.readAllLines(cfg.resolve("config.ini"), StandardCharsets.ISO_8859_1);
        Assert.eq("osgi.bundles=reference\\:file\\:a.jar@1\\:start,reference:file:C:/x/agent-bundle.jar@4:start", lines.get(1));
        Assert.eq("eclipse.product=p", lines.get(0));
        Assert.eq("x,1,plugins/x.jar,4,false\n", Files.readString(cfg.resolve("org.eclipse.equinox.simpleconfigurator/bundles.info")));
        Assert.eq(before, Files.readString(install.resolve("configuration/config.ini"), StandardCharsets.ISO_8859_1));
        ConfigArea.prepare(install, cfg, Path.of("C:\\x\\agent-bundle.jar"));
        Assert.eq(1L, Files.readAllLines(cfg.resolve("config.ini"), StandardCharsets.ISO_8859_1).stream().filter(l -> l.contains("agent-bundle")).count());
    }

    public void testConfigAreaAddsOsgiBundlesLineWhenMissing() throws Exception {
        Path install = fakeInstall();
        Files.writeString(install.resolve("configuration/config.ini"), "eclipse.product=p\n", StandardCharsets.ISO_8859_1);
        Path cfg = Files.createTempDirectory("ed-cfg");
        ConfigArea.prepare(install, cfg, Path.of("C:\\x\\a.jar"));
        Assert.eq("osgi.bundles=reference:file:C:/x/a.jar@4:start", Files.readAllLines(cfg.resolve("config.ini")).get(1));
    }

    public void testConfigAreaMissingConfigIniIsNotFound() throws Exception {
        Path empty = Files.createTempDirectory("ed-empty");
        try {
            ConfigArea.prepare(empty, Files.createTempDirectory("ed-cfg"), Path.of("a.jar"));
            throw new AssertionError("expected not-found");
        } catch (ToolError e) {
            Assert.eq("not-found", e.code());
        }
    }

    public void testFindLauncherByFolderExeAndIni() throws Exception {
        Path install = fakeInstall();
        Assert.eq("App.exe", Launcher.findLauncher(install).exe().getFileName().toString());
        Assert.eq("App.ini", Launcher.findLauncher(install.resolve("App.exe")).ini().getFileName().toString());
        Assert.eq("App.exe", Launcher.findLauncher(install.resolve("App.ini")).exe().getFileName().toString());
        Assert.eq(install.toAbsolutePath(), Launcher.findLauncher(install).install());
    }

    public void testFindLauncherAmbiguousListsCandidates() throws Exception {
        Path install = fakeInstall();
        Files.writeString(install.resolve("App_CLI.exe"), "");
        Files.writeString(install.resolve("App_CLI.ini"), "");
        try {
            Launcher.findLauncher(install);
            throw new AssertionError("expected ambiguous-match");
        } catch (ToolError e) {
            Assert.eq("ambiguous-match", e.code());
            Assert.eq(List.of("App.exe", "App_CLI.exe"), e.toMap().get("candidates"));
        }
    }

    public void testFindLauncherNeedsExeAndIni() throws Exception {
        Path dir = Files.createTempDirectory("ed-none");
        Files.writeString(dir.resolve("Lonely.exe"), "");
        Assert.eq("not-found", codeOf(() -> Launcher.findLauncher(dir)));
        Assert.eq("not-found", codeOf(() -> Launcher.findLauncher(dir.resolve("Lonely.exe"))));
    }

    private interface Throwing {
        void run() throws Exception;
    }

    private static String codeOf(Throwing r) {
        try {
            r.run();
        } catch (ToolError e) {
            return e.code();
        } catch (Exception e) {
            return e.toString();
        }
        return "no error";
    }
}
