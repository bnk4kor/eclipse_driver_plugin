package agent;

import com.bosch.eclipsedriver.agent.core.MenuPath;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;

public class MenuPathTest {
    public void testParseSplitsOnSlashAndTrims() {
        Assert.eq(List.of("File", "New", "Project..."), MenuPath.parse("File/New/Project..."));
        Assert.eq(List.of("File", "New"), MenuPath.parse(" File / New "));
    }

    public void testEscapedSlashStaysInsideASegment() {
        Assert.eq(List.of("Window", "Show View", "Problems/Markers"), MenuPath.parse("Window/Show View/Problems\\/Markers"));
    }

    public void testEmptyPathAndEmptySegmentsAreRejected() {
        Assert.eq("invalid-args", codeOf(() -> MenuPath.parse("")));
        Assert.eq("invalid-args", codeOf(() -> MenuPath.parse("File//New")));
        Assert.eq("invalid-args", codeOf(() -> MenuPath.parse("/File")));
    }

    public void testMatchIgnoresMnemonicAcceleratorCaseAndEllipsis() {
        Assert.isTrue(MenuPath.matches("&File", "file"), "mnemonic and case");
        Assert.isTrue(MenuPath.matches("&Save\tCtrl+S", "Save"), "accelerator");
        Assert.isTrue(MenuPath.matches("Proj&ect...", "Project"), "item has ellipsis, path does not");
        Assert.isTrue(MenuPath.matches("Project", "Project..."), "path has ellipsis, item does not");
        Assert.isTrue(!MenuPath.matches("Project", "Projects"), "different word");
        Assert.isTrue(!MenuPath.matches("", "File"), "empty item");
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
