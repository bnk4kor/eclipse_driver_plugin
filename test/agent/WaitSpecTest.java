package agent;

import com.bosch.eclipsedriver.agent.core.PointerSpec;
import com.bosch.eclipsedriver.agent.core.WaitSpec;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.Map;

public class WaitSpecTest {
    public void testButtons() {
        Assert.eq(1, PointerSpec.button(null));
        Assert.eq(2, PointerSpec.button("Middle"));
        Assert.eq(3, PointerSpec.button("right"));
        Assert.fails(() -> PointerSpec.button("fourth"), "unknown button");
    }

    public void testScrollDelta() {
        Assert.eq(3, PointerSpec.scrollDelta(null, null)[1]);
        Assert.eq(-5, PointerSpec.scrollDelta("up", 5)[1]);
        Assert.eq(-2, PointerSpec.scrollDelta("left", 2)[0]);
        Assert.fails(() -> PointerSpec.scrollDelta("sideways", 1), "unknown direction");
        Assert.fails(() -> PointerSpec.scrollDelta("down", 0), "amount");
    }

    public void testProcessExitIsRejected() {
        try {
            WaitSpec.parse(Map.of("condition", "process_exit"));
            Assert.isTrue(false, "expected invalid-args");
        } catch (ToolError e) {
            Assert.eq(ErrorCode.INVALID_ARGS, e.code());
        }
    }

    public void testRequiredArgsPerCondition() {
        Assert.fails(() -> WaitSpec.parse(Map.of()), "unknown condition");
        Assert.fails(() -> WaitSpec.parse(Map.of("condition", "shell_closed")), "title");
        Assert.fails(() -> WaitSpec.parse(Map.of("condition", "text_appears")), "text");
        Assert.fails(() -> WaitSpec.parse(Map.of("condition", "element_enabled")), "ref");
        Assert.eq("Progress", WaitSpec.parse(Map.of("condition", "shell_opened", "title", "Progress")).title());
    }

    public void testTimeoutDefaultsAndClamps() {
        Assert.eq(10_000L, WaitSpec.parse(Map.of("condition", "jobs_idle")).timeoutMs());
        Assert.eq(30_000L, WaitSpec.parse(Map.of("condition", "jobs_idle", "timeout_seconds", 30)).timeoutMs());
        Assert.eq(300_000L, WaitSpec.parse(Map.of("condition", "ui_idle", "timeout_seconds", 99999)).timeoutMs());
    }
}
