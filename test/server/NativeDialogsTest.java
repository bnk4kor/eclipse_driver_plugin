package server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.server.NativeDialogs;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class NativeDialogsTest {
    public void testParseDialogListJson() {
        String raw = "[{\"hwnd\":131072,\"title\":\"Open File\",\"pid\":4560,\"className\":\"#32770\",\"pathField\":\"A:\\\\test.arxml\",\"buttons\":[\"Open\",\"Cancel\"]}]";
        List<Map<String, Object>> dialogs = NativeDialogs.parseListOutput(raw);
        Assert.eq(1, dialogs.size());
        Assert.eq("Open File", dialogs.get(0).get("title"));
        Assert.eq(4560L, dialogs.get(0).get("pid"));
        Assert.eq("A:\\test.arxml", dialogs.get(0).get("pathField"));
    }

    public void testParseEmptyDialogListOutput() {
        Assert.eq(List.of(), NativeDialogs.parseListOutput(""));
        Assert.eq(List.of(), NativeDialogs.parseListOutput("[]"));
    }

    public void testAugmentBlockedErrorAddsDialogName() {
        ToolError blocked = new ToolError(ErrorCode.UI_THREAD_BLOCKED, "the UI thread did not respond within 10000 ms");
        List<Map<String, Object>> open = List.of(Map.of("title", "Select File", "hwnd", 99L, "pid", 1234L));
        ToolError augmented = NativeDialogs.augmentUiBlocked(blocked, open);
        Assert.eq(ErrorCode.UI_THREAD_BLOCKED, augmented.code());
        Assert.isTrue(augmented.getMessage().contains("Select File"), augmented.getMessage());
        Assert.eq(open, augmented.toMap().get("nativeDialogs"));
    }
}
