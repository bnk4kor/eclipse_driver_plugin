import com.bosch.eclipsedriver.protocol.Catalog;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates skill/eclipse-driver/reference/<category>.md from the catalog. Usage: GenRef <outDir> */
public final class GenRef {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Map<String, StringBuilder> byCategory = new LinkedHashMap<>();
        for (ToolSpec t : Catalog.all()) {
            StringBuilder sb = byCategory.computeIfAbsent(t.category(), k -> new StringBuilder("# " + k + "\n\nGenerated from the catalog. Do not edit.\n"));
            sb.append("\n## ").append(t.name()).append("\n\n").append(t.summary()).append("\n\n");
            sb.append("Schema: `").append(Json.write(t.schema())).append("`\n\n");
            sb.append("Example: `").append(Json.write(t.example())).append("`\n");
        }
        for (var e : byCategory.entrySet()) {
            Files.writeString(out.resolve(e.getKey() + ".md"), e.getValue().toString(), StandardCharsets.UTF_8);
        }
        System.out.println("reference: " + byCategory.size() + " files in " + out);
    }
}
