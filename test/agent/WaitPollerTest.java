package agent;

import com.bosch.eclipsedriver.agent.core.WaitPoller;
import com.bosch.eclipsedriver.agent.core.WaitPoller.PollResult;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WaitPollerTest {
    public void testSucceedsImmediatelyWhenMet() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> out = WaitPoller.await(
                "ui_idle",
                Duration.ofMillis(200),
                Duration.ofMillis(10),
                () -> {
                    calls.incrementAndGet();
                    return PollResult.met(Map.of("idle", true));
                });
        Assert.eq("ui_idle", out.get("condition"));
        Assert.eq(true, out.get("met"));
        Assert.eq(true, out.get("idle"));
        Assert.eq(1, calls.get());
    }

    public void testPollsUntilConditionBecomesTrue() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> out = WaitPoller.await(
                "shell_opened",
                Duration.ofMillis(500),
                Duration.ofMillis(10),
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) {
                        return PollResult.pending("still waiting, attempt " + n);
                    }
                    return PollResult.met(Map.of("shell", "New Project"));
                });
        Assert.eq(true, out.get("met"));
        Assert.eq("New Project", out.get("shell"));
        Assert.eq(3, calls.get());
    }

    public void testTimesOutWithStructuredErrorAndLastDiagnostic() {
        try {
            WaitPoller.await(
                    "jobs_idle",
                    Duration.ofMillis(50),
                    Duration.ofMillis(15),
                    () -> PollResult.pending("2 jobs running: Build, Indexer"));
            throw new AssertionError("expected timeout");
        } catch (ToolError e) {
            Assert.eq(ErrorCode.TIMEOUT, e.code());
            Assert.isTrue(e.getMessage().contains("jobs_idle"), e.getMessage());
            Assert.isTrue(e.getMessage().contains("2 jobs running"), e.getMessage());
        }
    }

    public void testClampTimeoutBounds() {
        Assert.eq(10_000L, WaitPoller.timeoutMillis(null));
        Assert.eq(100L, WaitPoller.timeoutMillis(0));
        Assert.eq(2_500L, WaitPoller.timeoutMillis(2500));
        Assert.eq(300_000L, WaitPoller.timeoutMillis(999_999));
    }
}
