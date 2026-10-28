package agent;

import com.bosch.eclipsedriver.agent.core.UiExecutor;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class UiExecutorTest {
    private static ToolError failure(Runnable r) {
        try {
            r.run();
        } catch (ToolError e) {
            return e;
        }
        throw new AssertionError("expected a ToolError");
    }

    public void testRunsWorkOnTheSchedulerThreadAndReturnsResult() {
        ExecutorService ui = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-ui"));
        try {
            UiExecutor ex = new UiExecutor(() -> ui::execute);
            Assert.eq("fake-ui", ex.call(Duration.ofSeconds(2), () -> Thread.currentThread().getName()));
        } finally {
            ui.shutdownNow();
        }
    }

    public void testBusyUiThreadGivesUiThreadBlockedAndWorkIsDroppedLater() throws Exception {
        ExecutorService ui = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        ui.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        AtomicInteger ran = new AtomicInteger();
        try {
            UiExecutor ex = new UiExecutor(() -> ui::execute);
            ToolError e = failure(() -> ex.call(Duration.ofMillis(100), () -> ran.incrementAndGet()));
            Assert.eq("ui-thread-blocked", e.code());
            release.countDown();
            ui.submit(() -> { }).get();
            Assert.eq(0, ran.get());
        } finally {
            release.countDown();
            ui.shutdownNow();
        }
    }

    public void testSlowWorkGivesTimeout() {
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            UiExecutor ex = new UiExecutor(() -> ui::execute);
            ToolError e = failure(() -> ex.call(Duration.ofMillis(100), () -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                return 1;
            }));
            Assert.eq("timeout", e.code());
        } finally {
            ui.shutdownNow();
        }
    }

    public void testToolErrorPassesThroughAndOtherFailuresAreWrapped() {
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            UiExecutor ex = new UiExecutor(() -> ui::execute);
            Assert.eq("stale-ref", failure(() -> ex.call(Duration.ofSeconds(2), () -> {
                throw new ToolError("stale-ref", "gone");
            })).code());
            ToolError wrapped = failure(() -> ex.call(Duration.ofSeconds(2), () -> {
                throw new IllegalStateException("widget disposed");
            }));
            Assert.eq("internal-error", wrapped.code());
            Assert.isTrue(wrapped.getMessage().contains("widget disposed"), "cause text kept");
        } finally {
            ui.shutdownNow();
        }
    }

    public void testSchedulerIsResolvedPerCall() {
        ExecutorService ui = Executors.newSingleThreadExecutor();
        try {
            boolean[] ready = {false};
            UiExecutor ex = new UiExecutor(() -> {
                if (!ready[0]) {
                    throw new ToolError("not-found", "no display yet");
                }
                return ui::execute;
            });
            Assert.eq("not-found", failure(() -> ex.call(Duration.ofSeconds(1), () -> 1)).code());
            ready[0] = true;
            Assert.eq(1, ex.call(Duration.ofSeconds(2), () -> 1));
        } finally {
            ui.shutdownNow();
        }
    }
}
