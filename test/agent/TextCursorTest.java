package agent;

import com.bosch.eclipsedriver.agent.core.TextCursor;
import edtest.Assert;
import java.util.Map;

public class TextCursorTest {
    public void testCursorAndSelectionComputation() {
        String text = "line one\nline two\r\nline three";
        Map<String, Object> c0 = TextCursor.locate(text, 0);
        Assert.eq(1L, c0.get("line"));
        Assert.eq(1L, c0.get("column"));
        Assert.eq(0L, c0.get("offset"));

        // Offset 9 is start of "line two" (line 2, col 1)
        Map<String, Object> c9 = TextCursor.locate(text, 9);
        Assert.eq(2L, c9.get("line"));
        Assert.eq(1L, c9.get("column"));
        Assert.eq(9L, c9.get("offset"));

        // Offset 21 is 'n' in "line three" (9 + 8 + 2 = 19 is 'l', 21 is 'n' -> line 3, col 3)
        Map<String, Object> c21 = TextCursor.locate(text, 21);
        Assert.eq(3L, c21.get("line"));
        Assert.eq(3L, c21.get("column"));
        Assert.eq(21L, c21.get("offset"));
    }

    public void testSelectionSpan() {
        String text = "hello\nworld";
        Map<String, Object> sel = TextCursor.selection(text, 6, 11);
        Assert.eq("world", sel.get("text"));
        Assert.eq(6L, sel.get("offset"));
        Assert.eq(5L, sel.get("length"));
        Assert.eq(2L, sel.get("startLine"));
        Assert.eq(1L, sel.get("startColumn"));
        Assert.eq(2L, sel.get("endLine"));
        Assert.eq(6L, sel.get("endColumn"));
    }
}
