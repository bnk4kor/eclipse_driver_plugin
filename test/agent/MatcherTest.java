package agent;

import com.bosch.eclipsedriver.agent.core.Matcher;
import com.bosch.eclipsedriver.agent.core.Matcher.Candidate;
import com.bosch.eclipsedriver.protocol.ToolError;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class MatcherTest {
    private static List<Candidate<String>> sample() {
        return List.of(
                new Candidate<>("w1", "e1", "Button", "OK", "", null, "Confirm action", true),
                new Candidate<>("w2", "e2", "Button", "Cancel", "", null, "Abort action", true),
                new Candidate<>("w3", "e3", "Text", "hello", "Project name", "proj.name", "Enter project name", true),
                new Candidate<>("w4", "e4", "Text", "", "Location", "proj.loc", null, true),
                new Candidate<>("w5", "e5", "Button", "Browse...", "Location", null, "Browse filesystem", false));
    }

    public void testMatchByTextCaseInsensitiveSubstring() {
        Matcher m = Matcher.from(Map.of("text", "canc"));
        List<Candidate<String>> hits = m.filter(sample());
        Assert.eq(1, hits.size());
        Assert.eq("e2", hits.get(0).ref());
    }

    public void testMatchByLabelIdClassAndTooltip() {
        Assert.eq("e3", Matcher.from(Map.of("label", "project name")).single(sample()).ref());
        Assert.eq("e4", Matcher.from(Map.of("id", "proj.loc")).single(sample()).ref());
        Assert.eq("e5", Matcher.from(Map.of("class", "button", "label", "Location")).single(sample()).ref());
        Assert.eq("e1", Matcher.from(Map.of("tooltip", "confirm")).single(sample()).ref());
    }

    public void testEmptyMatcherThrowsInvalidArgs() {
        try {
            Matcher.from(Map.of());
            throw new AssertionError("expected invalid-args");
        } catch (ToolError e) {
            Assert.eq("invalid-args", e.code());
        }
    }

    public void testSingleThrowsNotFoundWhenNothingMatches() {
        try {
            Matcher.from(Map.of("text", "nonexistent")).single(sample());
            throw new AssertionError("expected not-found");
        } catch (ToolError e) {
            Assert.eq("not-found", e.code());
        }
    }

    public void testSingleThrowsAmbiguousMatchWithCandidates() {
        try {
            Matcher.from(Map.of("class", "Button")).single(sample());
            throw new AssertionError("expected ambiguous-match");
        } catch (ToolError e) {
            Assert.eq("ambiguous-match", e.code());
            List<?> candidates = (List<?>) e.toMap().get("candidates");
            Assert.eq(3, candidates.size());
        }
    }
}
