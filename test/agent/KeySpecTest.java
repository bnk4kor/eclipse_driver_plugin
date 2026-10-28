package agent;

import com.bosch.eclipsedriver.agent.core.KeySpec;
import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;

public class KeySpecTest {
    public void testSingleCharacterAndNamedKey() {
        KeySpec a = KeySpec.parseKey("a");
        Assert.eq('a', a.character());
        Assert.eq(0, a.keyCode());

        KeySpec enter = KeySpec.parseKey("Enter");
        Assert.eq('\r', enter.character());
        Assert.eq(13, enter.keyCode());

        KeySpec esc = KeySpec.parseKey("Escape");
        Assert.eq((char) 27, esc.character());
        Assert.eq(27, esc.keyCode());

        KeySpec f3 = KeySpec.parseKey("F3");
        Assert.eq((char) 0, f3.character());
        Assert.eq((1 << 24) + 12, f3.keyCode());

        KeySpec down = KeySpec.parseKey("ArrowDown");
        Assert.eq((char) 0, down.character());
        Assert.eq((1 << 24) + 2, down.keyCode());
    }

    public void testChordParsingWithModifiers() {
        KeySpec ctrlS = KeySpec.parseChord("Ctrl+S");
        Assert.eq(KeySpec.CTRL, ctrlS.stateMask());
        Assert.eq('s', ctrlS.character());

        KeySpec ctrlShiftR = KeySpec.parseChord("Ctrl+Shift+R");
        Assert.eq(KeySpec.CTRL | KeySpec.SHIFT, ctrlShiftR.stateMask());
        Assert.eq('r', ctrlShiftR.character());

        KeySpec altF4 = KeySpec.parseChord("Alt+F4");
        Assert.eq(KeySpec.ALT, altF4.stateMask());
        Assert.eq((1 << 24) + 13, altF4.keyCode());
    }

    public void testModifiersListParsing() {
        int mask = KeySpec.parseModifiers(List.of("Ctrl", "Shift"));
        Assert.eq(KeySpec.CTRL | KeySpec.SHIFT, mask);
        Assert.eq(0, KeySpec.parseModifiers(null));
    }

    public void testUnknownKeyThrowsInvalidArgs() {
        try {
            KeySpec.parseKey("Hyperdrive");
            throw new AssertionError("expected invalid-args");
        } catch (ToolError e) {
            Assert.eq(ErrorCode.INVALID_ARGS, e.code());
        }
    }
}
