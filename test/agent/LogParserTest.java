package agent;

import com.bosch.eclipsedriver.agent.core.LogParser;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class LogParserTest {
    public void testParseEclipseWorkspaceLogWithNestedSubentriesAndFilter() {
        String sample = """
                !SESSION 2026-10-26 10:00:00.000 -----------------------------------------------
                eclipse.buildId=unknown
                !ENTRY org.eclipse.core.resources 2 100 2026-10-26 10:01:02.123
                !MESSAGE Workspace warning message
                !ENTRY com.bosch.autosartool 4 0 2026-10-26 10:02:03.456
                !MESSAGE Build failed with exception
                !STACK 0
                java.lang.IllegalStateException: Boom
                \tat com.bosch.Foo.bar(Foo.java:42)
                !SUBENTRY 1 com.bosch.autosartool 1 0 2026-10-26 10:02:03.457
                !MESSAGE Sub info entry
                """;
        List<Map<String, Object>> all = LogParser.parse(sample, null);
        Assert.eq(3, all.size());
        // Newest first
        Assert.eq("info", all.get(0).get("severity"));
        Assert.eq("Sub info entry", all.get(0).get("message"));
        Assert.eq("error", all.get(1).get("severity"));
        Assert.eq("com.bosch.autosartool", all.get(1).get("plugin"));
        Assert.eq("Build failed with exception", all.get(1).get("message"));
        Assert.isTrue(((String) all.get(1).get("stack")).contains("IllegalStateException: Boom"), "stack trace");
        Assert.eq("warning", all.get(2).get("severity"));

        List<Map<String, Object>> errorsOnly = LogParser.parse(sample, "error");
        Assert.eq(1, errorsOnly.size());
        Assert.eq("Build failed with exception", errorsOnly.get(0).get("message"));
    }

    public void testMarkerSeverityMapping() {
        Assert.eq("error", LogParser.markerSeverity(2));
        Assert.eq("warning", LogParser.markerSeverity(1));
        Assert.eq("info", LogParser.markerSeverity(0));
    }
}
