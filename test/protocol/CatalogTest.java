package protocol;

import com.bosch.eclipsedriver.protocol.Catalog;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.SchemaValidator;
import com.bosch.eclipsedriver.protocol.ToolError;
import com.bosch.eclipsedriver.protocol.ToolSpec;
import edtest.Assert;
import java.util.List;
import java.util.Map;

public class CatalogTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(String json) {
        return (Map<String, Object>) Json.parse(json);
    }

    public void testEveryEntryIsComplete() {
        for (ToolSpec t : Catalog.all()) {
            Assert.isTrue(!t.category().isBlank(), t.name() + " has no category");
            Assert.isTrue(!t.summary().isBlank(), t.name() + " has no summary");
            Assert.eq("object", t.schema().get("type"));
            Assert.eq(List.of(), SchemaValidator.validate(t.schema(), t.example()));
        }
    }

    public void testM1EntriesExist() {
        Assert.isTrue(Catalog.get("connect") != null, "connect");
        Assert.isTrue(Catalog.get("get_status") != null, "get_status");
        Assert.isTrue(Catalog.get("list_shells") != null, "list_shells");
    }

    public void testSchemaShapeFromParamDsl() {
        Map<String, Object> s = Catalog.get("connect").schema();
        Assert.eq(List.of("mode"), s.get("required"));
        Map<?, ?> mode = (Map<?, ?>) ((Map<?, ?>) s.get("properties")).get("mode");
        Assert.eq(List.of("launch", "attach", "install"), mode.get("enum"));
    }

    public void testValidArgsPass() {
        Assert.eq(List.of(), SchemaValidator.validate(Catalog.get("connect").schema(), obj("{\"mode\":\"attach\",\"pid\":42}")));
    }

    public void testMissingRequiredWrongTypeUnknownAndBadEnum() {
        Map<String, Object> schema = Catalog.get("connect").schema();
        Assert.eq(List.of("args.mode is required"), SchemaValidator.validate(schema, obj("{}")));
        Assert.eq(List.of("args.pid must be integer but is string"), SchemaValidator.validate(schema, obj("{\"mode\":\"attach\",\"pid\":\"x\"}")));
        Assert.eq(List.of("args.colour is not a known argument"), SchemaValidator.validate(schema, obj("{\"mode\":\"launch\",\"colour\":1}")));
        Assert.eq(1, SchemaValidator.validate(schema, obj("{\"mode\":\"fly\"}")).size());
    }

    public void testClosestNamesSuggestsTypoFix() {
        Assert.eq("list_shells", Catalog.closest("list_shel", 3).get(0));
        Assert.eq(3, Catalog.closest("zzz", 3).size());
    }

    public void testCheckCallErrors() {
        Assert.eq("connect", Catalog.checkCall("connect", obj("{\"mode\":\"launch\"}")).name());
        try {
            Catalog.checkCall("list_shel", obj("{}"));
            throw new AssertionError("expected unknown-tool");
        } catch (ToolError e) {
            Assert.eq("unknown-tool", e.code());
            Assert.eq("list_shells", ((List<?>) e.toMap().get("closest")).get(0));
        }
        try {
            Catalog.checkCall("connect", obj("{}"));
            throw new AssertionError("expected invalid-args");
        } catch (ToolError e) {
            Assert.eq("invalid-args", e.code());
            Assert.eq(Catalog.get("connect").schema(), e.toMap().get("schema"));
        }
    }

    public void testFindByCategoryAndQuery() {
        Assert.eq(15, Catalog.find("observe", null).size());
        Assert.eq(11, Catalog.find("observe-eclipse", null).size());
        Assert.eq(13, Catalog.find("act-pointer-keyboard", null).size());
        Assert.eq(5, Catalog.find("act-widgets", null).size());
        Assert.eq(18, Catalog.find("act-workbench", null).size());
        Assert.eq(6, Catalog.find("act-dialogs-wizards", null).size());
        Assert.eq(1, Catalog.find("wait", null).size());
        Assert.eq(6, Catalog.find("native-dialogs", null).size());
        Assert.eq(1, Catalog.find(null, "get_status").size());
        Assert.eq(
                Map.of(
                        "observe", 15,
                        "observe-eclipse", 11,
                        "session", 10,
                        "launch-process", 6,
                        "act-pointer-keyboard", 13,
                        "act-widgets", 5,
                        "act-workbench", 18,
                        "act-dialogs-wizards", 6,
                        "wait", 1,
                        "native-dialogs", 6),
                Catalog.categoryCounts());
    }

    public void testT31ToT39ToolsAreInCatalog() {
        Assert.isTrue(Catalog.get("read_widget_tree") != null, "read_widget_tree");
        Assert.isTrue(Catalog.get("find_elements") != null, "find_elements");
        Assert.isTrue(Catalog.get("read_element") != null, "read_element");
        Assert.isTrue(Catalog.get("read_text") != null, "read_text");
        Assert.isTrue(Catalog.get("read_tree") != null, "read_tree");
        Assert.isTrue(Catalog.get("read_table") != null, "read_table");
        Assert.isTrue(Catalog.get("read_workbench") != null, "read_workbench");
        Assert.isTrue(Catalog.get("read_menu") != null, "read_menu");
        Assert.isTrue(Catalog.get("read_toolbar") != null, "read_toolbar");
        Assert.isTrue(Catalog.get("read_status_line") != null, "read_status_line");
        Assert.isTrue(Catalog.get("read_clipboard") != null, "read_clipboard");
        Assert.isTrue(Catalog.get("screenshot") != null, "screenshot");
        Assert.isTrue(Catalog.get("state_diff") != null, "state_diff");
        Assert.eq(List.of("since"), Catalog.get("state_diff").schema().get("required"));
        Assert.isTrue(Catalog.get("read_editor") != null, "read_editor");
        Assert.isTrue(Catalog.get("read_views") != null, "read_views");
        Assert.isTrue(Catalog.get("read_selection") != null, "read_selection");
        Assert.isTrue(Catalog.get("read_outline") != null, "read_outline");
        Assert.isTrue(Catalog.get("read_properties") != null, "read_properties");
        Assert.isTrue(Catalog.get("read_console") != null, "read_console");
        Assert.isTrue(Catalog.get("read_progress") != null, "read_progress");
        Assert.isTrue(Catalog.get("read_problems") != null, "read_problems");
        Assert.isTrue(Catalog.get("read_error_log") != null, "read_error_log");
        Assert.isTrue(Catalog.get("read_junit") != null, "read_junit");
        Assert.isTrue(Catalog.get("read_debug") != null, "read_debug");
    }

    public void testM4ToolsAreInCatalog() {
        for (String name : new String[] {
                "wait_for",
                "click", "double_click", "right_click_menu", "hover", "drag_drop", "scroll",
                "press_key", "key_chord", "type_text", "set_text",
                "focus", "move_resize_shell", "maximize_minimize",
                "select", "check", "uncheck", "expand", "collapse",
                "context_menu_select", "toolbar_click",
                "open_view", "activate_view", "close_view",
                "open_perspective", "reset_perspective",
                "open_editor", "save", "save_all", "close_editor",
                "run_command", "quick_access", "open_preferences", "set_preference", "undo", "redo",
                "list_native_dialogs", "native_dialog_set_path", "native_dialog_confirm",
                "native_dialog_cancel", "native_dialog_press", "native_dialog_screenshot"}) {
            Assert.isTrue(Catalog.get(name) != null, name);
        }
        Assert.eq(List.of("condition"), Catalog.get("wait_for").schema().get("required"));
        Assert.eq(List.of("path"), Catalog.get("native_dialog_set_path").schema().get("required"));
    }

    public void testCatalogHas91CompleteTools() {
        Assert.eq(91, Catalog.all().size());
        for (var t : Catalog.all()) {
            Assert.isTrue(!t.summary().isBlank(), t.name() + " summary");
            Assert.isTrue(t.schema() != null && t.example() != null, t.name() + " schema/example");
            Assert.eq(List.of(), SchemaValidator.validate(t.schema(), t.example()));
        }
    }

    public void testMenuSelectNeedsAPath() {
        Map<String, Object> schema = Catalog.get("menu_select").schema();
        Assert.eq(List.of("args.path is required"), SchemaValidator.validate(schema, obj("{}")));
        Assert.eq(List.of(), SchemaValidator.validate(schema, obj("{\"path\":\"File/New/Project...\"}")));
    }

    public void testReadDialogIsInCatalogWithOptionalShell() {
        Map<String, Object> schema = Catalog.get("read_dialog").schema();
        Assert.eq(List.of(), SchemaValidator.validate(schema, obj("{}")));
        Assert.eq(List.of(), SchemaValidator.validate(schema, obj("{\"shell\":\"New Project\"}")));
    }
}
