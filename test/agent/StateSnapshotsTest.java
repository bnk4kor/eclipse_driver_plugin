package agent;

import com.bosch.eclipsedriver.agent.core.StateSnapshots;
import com.bosch.eclipsedriver.agent.core.StateSnapshots.ElementSnap;
import com.bosch.eclipsedriver.agent.core.StateSnapshots.UiSnap;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class StateSnapshotsTest {
    public void testDiffReportsOpenedAndClosedShellsAndActiveShellChange() {
        StateSnapshots ring = new StateSnapshots();
        ring.record(new UiSnap(1L, List.of("Workbench"), "Workbench", List.of()));
        UiSnap s2 = new UiSnap(2L, List.of("Workbench", "New Project"), "New Project", List.of());
        ring.record(s2);

        Map<String, Object> diff = ring.diff(1L, s2);
        Assert.eq(1L, diff.get("since"));
        Assert.eq(List.of("New Project"), diff.get("shellsOpened"));
        Assert.eq(List.of(), diff.get("shellsClosed"));
        Assert.eq("Workbench", ((Map<?, ?>) diff.get("activeShellChanged")).get("before"));
        Assert.eq("New Project", ((Map<?, ?>) diff.get("activeShellChanged")).get("after"));
        Assert.eq(true, diff.get("changed"));
    }

    public void testDiffReportsAddedRemovedAndChangedElements() {
        StateSnapshots ring = new StateSnapshots();
        ring.record(new UiSnap(1L, List.of("New Project"), "New Project", List.of(
                new ElementSnap("e1", "Text", "", true, "Project name"),
                new ElementSnap("e2", "Button", "Next >", false, ""),
                new ElementSnap("e3", "Label", "Old Banner", true, ""))));
        UiSnap s2 = new UiSnap(2L, List.of("New Project"), "New Project", List.of(
                new ElementSnap("e1", "Text", "MyProj", true, "Project name"),
                new ElementSnap("e2", "Button", "Next >", true, ""),
                new ElementSnap("e4", "Button", "Finish", true, "")));
        ring.record(s2);

        Map<String, Object> diff = ring.diff(1L, s2);
        List<?> added = (List<?>) diff.get("elementsAdded");
        Assert.eq(1, added.size());
        Assert.eq("e4", ((Map<?, ?>) added.get(0)).get("ref"));

        Assert.eq(List.of("e3"), diff.get("elementsRemoved"));

        List<?> changed = (List<?>) diff.get("elementsChanged");
        Assert.eq(2, changed.size());
        Map<?, ?> c1 = (Map<?, ?>) changed.get(0);
        Assert.eq("e1", c1.get("ref"));
        Assert.eq("", ((Map<?, ?>) c1.get("text")).get("before"));
        Assert.eq("MyProj", ((Map<?, ?>) c1.get("text")).get("after"));

        Map<?, ?> c2 = (Map<?, ?>) changed.get(1);
        Assert.eq("e2", c2.get("ref"));
        Assert.eq(false, ((Map<?, ?>) c2.get("enabled")).get("before"));
        Assert.eq(true, ((Map<?, ?>) c2.get("enabled")).get("after"));
    }

    public void testUnchangedSnapshotReportsChangedFalse() {
        StateSnapshots ring = new StateSnapshots();
        UiSnap s1 = new UiSnap(1L, List.of("Workbench"), "Workbench", List.of(
                new ElementSnap("e1", "Button", "OK", true, "")));
        ring.record(s1);
        UiSnap s2 = new UiSnap(2L, List.of("Workbench"), "Workbench", List.of(
                new ElementSnap("e1", "Button", "OK", true, "")));
        ring.record(s2);

        Map<String, Object> diff = ring.diff(1L, s2);
        Assert.eq(false, diff.get("changed"));
        Assert.eq(null, diff.get("activeShellChanged"));
        Assert.eq(List.of(), diff.get("shellsOpened"));
        Assert.eq(List.of(), diff.get("shellsClosed"));
        Assert.eq(List.of(), diff.get("elementsAdded"));
        Assert.eq(List.of(), diff.get("elementsRemoved"));
        Assert.eq(List.of(), diff.get("elementsChanged"));
    }

    public void testUnknownOrEvictedSnapshotThrowsNotFound() {
        StateSnapshots ring = new StateSnapshots();
        UiSnap s1 = new UiSnap(1L, List.of("Workbench"), "Workbench", List.of());
        ring.record(s1);
        try {
            ring.diff(99L, s1);
            throw new AssertionError("expected not-found");
        } catch (ToolError e) {
            Assert.eq("not-found", e.code());
        }
    }
}
