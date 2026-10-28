package protocol;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class JsonTest {
    public void testRoundTripObject() {
        String text = "{\"a\":1,\"b\":[true,false,null],\"c\":{\"d\":\"x\"},\"e\":1.5}";
        Object parsed = Json.parse(text);
        Assert.eq(text, Json.write(parsed));
    }

    public void testIntegersAreLongAndFractionsAreDouble() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"i\":42,\"f\":4.5,\"x\":1e2}");
        Assert.eq(42L, m.get("i"));
        Assert.eq(4.5, m.get("f"));
        Assert.eq(100.0, m.get("x"));
    }

    public void testEscapesAndUnicode() {
        Assert.eq("a\"b\\c\n\t/", Json.parse("\"a\\\"b\\\\c\\n\\t\\/\""));
        Assert.eq("\u00e4\u20ac", Json.parse("\"\\u00e4\\u20ac\""));
        Assert.eq("\"\\u0001\"", Json.write("\u0001"));
        Assert.eq("\"\\n\\\"\"", Json.write("\n\""));
        Assert.eq("\u00e4", Json.parse(Json.write("\u00e4")));
    }

    public void testSurrogatePairSurvives() {
        String emoji = new String(Character.toChars(0x1F600));
        Assert.eq(emoji, Json.parse(Json.write(emoji)));
        Assert.eq(emoji, Json.parse("\"\\ud83d\\ude00\""));
    }

    public void testListsAndEmptyContainers() {
        Assert.eq(List.of(), Json.parse(" [ ] "));
        Assert.eq(Map.of(), Json.parse("{}"));
        Assert.eq("[1,2]", Json.write(List.of(1, 2)));
    }

    public void testBadInputIsRejected() {
        Assert.fails(() -> Json.parse("{\"a\":}"), "bad JSON at position");
        Assert.fails(() -> Json.parse("[1,2"), "unexpected end");
        Assert.fails(() -> Json.parse("{\"a\":1} x"), "trailing");
        Assert.fails(() -> Json.parse("\"abc"), "unterminated");
        Assert.fails(() -> Json.parse("{a:1}"), "string key");
        Assert.fails(() -> Json.parse("tru"), "expected true");
        Assert.fails(() -> Json.write(Double.NaN), "NaN");
    }

    public void testToolErrorShape() {
        ToolError e = new ToolError(ErrorCode.NOT_FOUND, "no such shell").hint("call list_shells").with("candidates", List.of("a"));
        Assert.eq("{\"code\":\"not-found\",\"message\":\"no such shell\",\"hints\":[\"call list_shells\"],\"candidates\":[\"a\"]}",
                Json.write(e.toMap()));
    }
}
