import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Build helper: java tools\Pack.java <version> <outDir> <jar>... writes outDir\manifest.properties with SHA-256 per jar. */
public final class Pack {
    public static void main(String[] args) throws Exception {
        String version = args[0];
        Path out = Path.of(args[1]);
        StringBuilder files = new StringBuilder();
        StringBuilder hashes = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            Path jar = Path.of(args[i]);
            String name = jar.getFileName().toString();
            files.append(files.length() == 0 ? "" : ",").append(name);
            hashes.append(name).append(".sha256=").append(sha256(jar)).append('\n');
        }
        Files.writeString(out.resolve("manifest.properties"), "version=" + version + "\nfiles=" + files + "\n" + hashes);
    }

    private static String sha256(Path file) throws IOException, java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
