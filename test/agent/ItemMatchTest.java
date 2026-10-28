package agent;

import com.bosch.eclipsedriver.agent.core.ItemMatch;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;

public class ItemMatchTest {
    public void testSplitPathWithEscape() {
        Assert.eq(List.of("General", "Workspace"), ItemMatch.splitPath("General/Workspace"));
        Assert.eq(List.of("A/B", "C"), ItemMatch.splitPath("A\\/B/C"));
        Assert.eq(List.of("x"), ItemMatch.splitPath(" /x/ "));
        Assert.fails(() -> ItemMatch.splitPath(" "), "empty");
        Assert.fails(() -> ItemMatch.splitPath("//"), "no segments");
    }

    public void testExactBeatsPartial() {
        List<String> items = List.of("Java", "JavaScript", "Debug");
        Assert.eq(0, ItemMatch.pick(items, "java", "item"));
        Assert.eq(1, ItemMatch.pick(items, "script", "item"));
        Assert.eq(2, ItemMatch.pick(items, " Debug ", "item"));
    }

    public void testNotFoundAndAmbiguousCarryCandidates() {
        List<String> items = List.of("Alpha one", "Alpha two");
        try {
            ItemMatch.pick(items, "alpha", "item");
            Assert.isTrue(false, "expected ambiguous");
        } catch (ToolError e) {
            Assert.eq(ErrorCode.AMBIGUOUS_MATCH, e.code());
            Assert.eq(List.of("Alpha one", "Alpha two"), e.toMap().get("candidates"));
        }
        try {
            ItemMatch.pick(items, "zzz", "item");
            Assert.isTrue(false, "expected not-found");
        } catch (ToolError e) {
            Assert.eq(ErrorCode.NOT_FOUND, e.code());
            Assert.eq(items, e.toMap().get("candidates"));
        }
    }
}
