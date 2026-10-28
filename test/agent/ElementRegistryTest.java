package agent;

import com.bosch.eclipsedriver.agent.core.ElementRegistry;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.LinkedHashMap;
import java.util.Map;

public class ElementRegistryTest {
    /** Stands in for an SWT widget: identity equality, can be disposed, has a label that can change. */
    private static final class Fake {
        boolean disposed;
        String label;

        Fake(String label) {
            this.label = label;
        }
    }

    private final ElementRegistry<Fake> registry = new ElementRegistry<>(new ElementRegistry.Probe<>() {
        @Override
        public boolean isDisposed(Fake w) {
            return w.disposed;
        }

        @Override
        public String fingerprint(Fake w) {
            if (w.disposed) {
                throw new IllegalStateException("Widget is disposed");
            }
            return w.label;
        }
    });

    private static ToolError failure(Runnable r) {
        try {
            r.run();
        } catch (ToolError e) {
            return e;
        }
        throw new AssertionError("expected a ToolError");
    }

    public void testRegisterThenResolveInSameSnapshot() {
        Fake ok = new Fake("OK");
        String ref = registry.register(ok);
        Assert.isTrue(registry.resolve(ref) == ok, "same widget back");
    }

    public void testSameWidgetKeepsItsRef() {
        Fake ok = new Fake("OK");
        Assert.eq(registry.register(ok), registry.register(ok));
        Assert.isTrue(!registry.register(ok).equals(registry.register(new Fake("OK"))), "different widget, different ref");
    }

    public void testUnknownRefIsNotFound() {
        registry.register(new Fake("A"));
        Assert.eq("not-found", failure(() -> registry.resolve("e999")).code());
        Assert.eq("not-found", failure(() -> registry.resolve("e0")).code());
        Assert.eq("not-found", failure(() -> registry.resolve("button")).code());
    }

    public void testRefFromOlderSnapshotStillWorksWhileWidgetMatches() {
        Fake ok = new Fake("OK");
        String ref = registry.register(ok);
        registry.advance();
        registry.advance();
        Assert.isTrue(registry.resolve(ref) == ok, "unchanged widget resolves across snapshots");
    }

    public void testRefFromOlderSnapshotIsStaleWhenWidgetNoLongerMatches() {
        Fake button = new Fake("Next >");
        String ref = registry.register(button);
        long old = registry.snapshot();
        button.label = "Finish";
        registry.advance();
        ToolError e = failure(() -> registry.resolve(ref));
        Assert.eq("stale-ref", e.code());
        Assert.eq(registry.snapshot(), e.toMap().get("snapshot"));
        Assert.isTrue(registry.snapshot() > old, "snapshot moved on");
        Assert.isTrue(!e.toMap().get("hints").toString().equals("[]"), "stale-ref tells the caller how to recover");
    }

    public void testChangeInsideTheSameSnapshotIsNotStale() {
        Fake field = new Fake("Name");
        String ref = registry.register(field);
        field.label = "Name*";
        Assert.isTrue(registry.resolve(ref) == field, "same snapshot: only disposal matters");
    }

    public void testDisposedWidgetIsStaleAndNeverThrowsFromTheProbe() {
        Fake dialog = new Fake("Dialog");
        String ref = registry.register(dialog);
        dialog.disposed = true;
        Assert.eq("stale-ref", failure(() -> registry.resolve(ref)).code());
        registry.advance();
        Assert.eq("stale-ref", failure(() -> registry.resolve(ref)).code());
    }

    public void testStampAddsAFreshSnapshotIdToEveryResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shells", 1L);
        long before = registry.snapshot();
        Map<String, Object> stamped = registry.stamp(result);
        Assert.eq(before + 1, stamped.get("snapshot"));
        Assert.eq(registry.snapshot(), stamped.get("snapshot"));
        Assert.eq(1L, stamped.get("shells"));
        Assert.eq(before + 2, registry.stamp(new LinkedHashMap<>()).get("snapshot"));
    }

    public void testPruneDropsRefsOfDisposedWidgets() {
        Fake a = new Fake("A");
        Fake b = new Fake("B");
        registry.register(a);
        registry.register(b);
        a.disposed = true;
        registry.advance();
        Assert.eq(1, registry.size());
    }
}
