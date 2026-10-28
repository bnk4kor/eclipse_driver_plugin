package server;

import com.bosch.eclipsedriver.protocol.Catalog;
import edtest.Assert;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkillTest {
    public void testSkillNamesEveryCatalogToolAndIsWithinBudget() throws Exception {
        Path p = Path.of("skill", "eclipse-driver", "SKILL.md");
        if (!Files.exists(p)) {
            return;
        }
        String text = Files.readString(p);
        for (var t : Catalog.all()) {
            Assert.isTrue(text.contains(t.name()), "SKILL.md omits tool " + t.name());
        }
        Assert.isTrue(text.length() / 4 <= 3000, "SKILL.md exceeds 3000 tokens");
    }
}
