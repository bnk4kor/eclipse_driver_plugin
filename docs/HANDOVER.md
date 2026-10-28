# Handover: eclipse-driver (2026-10-27)

## 1. Current Status
- **Completed milestones:**
  - **M1 (T1.0–T1.9):** Skeleton, protocol JSON codec, catalog model & validator, MCP stdio adapter (3 meta-tools: `list_tools`, `describe_tool`, `call_tool`), `build.cmd`, embedded agent extraction, HTTP/JSON RPC endpoint on `127.0.0.1`, `UiExecutor`, `list_shells`, `connect` (launch mode), and live ISOLAR-AB launch integration test.
  - **M2 (T2.1–T2.5):** `ElementRegistry` with weak widget references and snapshot IDs, `menu_select`, `read_dialog`, wizard navigation (`wizard_next`, `wizard_back`, `wizard_finish`, `wizard_cancel`), dialog actions (`dialog_confirm`, `dialog_dismiss`), and live wizard + modal dialog integration test (`LaunchIT`).
  - **M3 (T3.1–T3.10):** Full observe set (15 generic + 11 Eclipse-aware = 26 observe tools):
    - T3.1: `Matcher` (case-insensitive substring on `text`, `label`, `id`, `class`, `tooltip` with `ambiguous-match` candidates), `read_widget_tree` (depth-bounded with stable refs), `find_elements`, and `read_element` (by ref or matcher).
    - T3.2: `Paging` (pure-JDK window and line slicing), `ContentHandlers` (`read_text` for widget/editor/console, `read_tree` paged without expanding collapsed lazy nodes, `read_table` paged with columns and row values).
    - T3.3: `Labels.accelerator`, `WorkbenchHandlers` (`read_workbench` with perspective/editors/menu/toolbar/statusLine, `read_menu` by path/depth without clicking, `read_toolbar`, `read_status_line`, `read_clipboard`).
    - T3.4: `StateSnapshots` (pure-JDK bounded snapshot history and diffing), `VisualHandlers` (`screenshot` for shell, element ref, or explicit region to PNG; `state_diff` since a snapshot id).
    - T3.5: `TextCursor` (pure-JDK character offset to 1-based line/column and selection span math), `EclipseHandlers` (`read_editor` with path/dirty/cursor/selection/form-editor pages/paged content, `read_views`, `read_selection`, `read_outline`, `read_properties`).
    - T3.6: `read_console` (with console switcher and paged lines) and `read_progress` (`Job.getJobManager()` running/waiting/sleeping jobs + Progress view entries).
    - T3.7: `LogParser` (pure-JDK parser for `.metadata/.log` and `IMarker` severities), `read_problems` (workspace problem markers + counts), and `read_error_log` (paged `.metadata/.log` entries with stack traces).
    - T3.8: `read_junit` (`JUnitCorePlugin.getModel().getTestRunSessions()` runs, counts, failure traces, with optional bundle handling).
    - T3.9: `read_debug` (`DebugPlugin` launches, processes, debug targets, threads, stack frames, variables, and breakpoints).
    - T3.10: Live integration test in `LaunchIT.java` exercising all 26 observe tools while the modal `New Project` wizard dialog is open.
- **Next pending task:** Owner sign-off on **Checkpoint M3** (and **M1/M2**), then **M4 (`T4.1 wait_for` .. `T4.11`)** in `tasks/todo.md`.
- **Test & build state:**
  - `build.cmd all` -> **95 passed, 0 failed** (packages `dist\eclipse-driver-server.jar` and runs all unit + packaging tests).
  - `build.cmd it` -> **IT PASSED**, target install unchanged (`51,929` files before and after).

## 2. Key Paths & Environment
- **Server / Protocol / Tests / Build:** `A:\sandbox\workspace\eclipse_driver`
  - Specification: `A:\sandbox\workspace\eclipse_driver\SPEC.md`
  - Implementation plan: `A:\sandbox\workspace\eclipse_driver\tasks\plan.md`
  - Task checklist: `A:\sandbox\workspace\eclipse_driver\tasks\todo.md`
  - Architecture notes: `A:\sandbox\workspace\eclipse_driver\docs\ARCHITECTURE.md`
  - Build script: `A:\sandbox\workspace\eclipse_driver\build.cmd`
  - Distributable jar: `A:\sandbox\workspace\eclipse_driver\dist\eclipse-driver-server.jar`
- **OSGi Agent Plugin Project:** `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent`
  - Plain-JDK core classes (unit-tested in `build.cmd test`): `src\com\bosch\eclipsedriver\agent\core\*.java`
  - SWT/Eclipse handlers (compiled against target plugins in `build.cmd package`): `src\com\bosch\eclipsedriver\agent\*.java`
- **JDK (Java 21):** `C:\toolbase\_ldata\java_jdk\.98-21.0.7.6` (never use the Java 8 on `PATH`).
- **Agent compile classpath (PDE target plugins):** `A:\sandbox\workspace\Orion_WS\Target\Target_12.14.0_Oct26\ecl_target\4.36_with_Artop_4.19\eclipse\plugins`
- **Sample target install (read-only):** `A:\sandbox\Products\ISOLAR-AB\ISOLAR-AB_V12.13.0RC1\ISOLAR-AB.exe`

## 3. Implemented Catalog Tools (35 of 91)
| Category | Tools |
|---|---|
| `session` (2) | `connect`, `get_status` |
| `observe` (15) | `list_shells`, `read_dialog`, `read_widget_tree`, `find_elements`, `read_element`, `read_text`, `read_tree`, `read_table`, `read_workbench`, `read_menu`, `read_toolbar`, `read_status_line`, `read_clipboard`, `screenshot`, `state_diff` |
| `observe-eclipse` (11) | `read_editor`, `read_views`, `read_selection`, `read_outline`, `read_properties`, `read_console`, `read_progress`, `read_problems`, `read_error_log`, `read_junit`, `read_debug` |
| `act-workbench` (1) | `menu_select` |
| `act-dialogs-wizards` (6) | `wizard_next`, `wizard_back`, `wizard_finish`, `wizard_cancel`, `dialog_confirm`, `dialog_dismiss` |

## 4. Verification Evidence (M1, M2, T3.1)
Commands run:
```bat
A:\sandbox\workspace\eclipse_driver\build.cmd all
A:\sandbox\workspace\eclipse_driver\build.cmd it
```
Key output from `build.cmd it`:
- **Launch & connect:** `connect` -> `{"instance":"i1","pid":14404,"mode":"launch",...}`
- **Bogus menu path:** `menu_select` (`"File/New/zzz"`) -> `{"code":"not-found","message":"no item 'zzz' in 'File/New'","candidates":["AUTOSAR Project","RTA-CAR Project","Project...",...]}`
- **Open wizard & read page 1:** `menu_select` (`"File/New/Project..."`) -> `opened: ["New Project"]`; `read_dialog` -> `wizard.pageTitle: "Select a wizard"`, `next.enabled: true` (pre-seeded via `.metadata/.plugins/org.eclipse.ui.ide/dialog_settings.xml` in the test workspace).
- **Wizard Next / disabled check / Back / Cancel:**
  - `wizard_next` -> `read_dialog` reports `wizard.pageTitle: "Project"`, `back.enabled: true`, `next.enabled: false`, `finish.enabled: false`.
  - `wizard_next` on page 2 -> `{"code":"disabled","message":"button 'Next >' is disabled",...}`.
  - `wizard_back` -> `read_dialog` reports `wizard.pageTitle: "Select a wizard"`.
  - `wizard_cancel` -> `{"shell":"New Project","pressed":"Cancel","opened":[],"closed":["New Project"]}`.
- **Modal message dialog flow:**
  - `menu_select` (`"RTE/RTE Code Generation..."`) -> `{"clicked":"RTE/RTE Code Generation...","opened":["Information"],"closed":[]}`.
  - `read_dialog` -> `{"shell":"e22","title":"Information","modality":"application","labels":["The chosen operation is not enabled."],"buttons":[{"ref":"e21","label":"OK","enabled":true}],...}`.
  - `dialog_confirm` -> `{"shell":"Information","pressed":"OK","opened":[],"closed":["Information"]}`.
- **T3.1 Observe tools (`read_widget_tree`, `find_elements`, `read_element`):**
  - `read_widget_tree` (`depth: 3`) -> returns main `Shell` (`ref: "e3"`) and bounded children with refs and `snapshot: 14`.
  - `find_elements` (`class: "Tree"`) -> returns `e1` and `e2`.
  - `read_element` (`ref: "e1"`) -> returns full state (`class`, `text`, `label`, `enabled`, `visible`, `focused`, `bounds`, `selection`).
  - `read_element` (`class: "Tree"`) -> returns `{"code":"ambiguous-match","message":"2 elements matched {class=tree}","candidates":[...]}`.
- **T3.2 Observe tools (`read_text`, `read_tree`, `read_table`):**
  - `read_tree` (`offset: 0, limit: 3` on New Project wizard) -> `{"ref":"e5","offset":0,"limit":3,"totalCount":9,"hasMore":true,"selected":["Project"],"items":[{"ref":"e13","text":"General","expanded":true,"childCount":2},{"ref":"e14","text":"Artop Example Wizards","expanded":false,"childCount":1},{"ref":"e15","text":"Business Intelligence and Reporting Tools","expanded":false,"childCount":1}],"snapshot":5}`.
  - `read_tree` (`parent_path: "General", offset: 0, limit: 5`) -> `{"ref":"e5","parentRef":"e13","parentText":"General","offset":0,"limit":5,"totalCount":2,"hasMore":false,"selected":["Project"],"items":[{"ref":"e16","text":"ISOLAR Project","expanded":false,"childCount":0},{"ref":"e17","text":"Project","expanded":false,"childCount":0}],"snapshot":6}`.
  - `read_text` (`label: "Wizards", offset: 0, limit: 5`) -> `{"source":"widget","ref":"e4","text":"type filter text","lines":["type filter text"],"offset":0,"limit":5,"totalLines":1,"hasMore":false,"snapshot":7}`.
- **T3.3 Observe tools (`read_workbench`, `read_menu`, `read_toolbar`, `read_status_line`, `read_clipboard`):**
  - `read_workbench` -> `{"windowTitle":"eclipse-driver-it-workspace - ISOLAR-A/B 12.13.0RC1 Standalone","shell":"e3","perspective":{"id":"com.bosch.autosartool.ui.aatperspective","label":"AUTOSAR"},"editors":[],"menu":[...],"toolbar":[...],"statusLine":""}`.
  - `read_menu` (`path: "File/New", depth: 1`) -> `{"shell":"...","path":"File/New","items":[{"label":"AUTOSAR Project","style":"push","enabled":true},...,{"label":"Other...","accelerator":"Ctrl+N","style":"push","enabled":true}],"snapshot":18}`.
  - `read_toolbar` -> returns toolbar items with `ref`, `toolbarRef`, `label`, `tooltip`, `style` (`push`/`check`/`radio`/`drop_down`), `enabled`, `checked`.
  - `read_status_line` -> `{"shell":"...","ref":"e53","text":"","items":[],"snapshot":20}`.
  - `read_clipboard` -> `{"text":"...","hasText":true,"snapshot":21}`.
- **T3.4 Observe tools (`screenshot`, `state_diff`):**
  - `state_diff` (`since: 4` after opening `File/New/Project...`) -> `{"since":4,"changed":true,"shellsOpened":["New Project"],"shellsClosed":[],"activeShellChanged":{"before":"eclipse-driver-it-workspace - ISOLAR-A/B 12.13.0RC1 Standalone","after":"New Project"},"elementsAdded":[...],"snapshot":5}`.
  - `state_diff` (`since: 23` on idle workbench) -> `{"since":23,"changed":false,"shellsOpened":[],"shellsClosed":[],"activeShellChanged":null,"elementsAdded":[],"elementsRemoved":[],"elementsChanged":[],"snapshot":24}`.
  - `screenshot` (`{}`) -> `{"path":"...\\eclipse-driver-shot-....png","bytes":58718,"width":1024,"height":768,"bounds":{"x":192,"y":192,"width":1024,"height":768},"snapshot":25}`.
  - `screenshot` (`ref: "e8"`) -> `{"path":"...\\eclipse-driver-shot-....png","bytes":166,"width":245,"height":31,"bounds":{"x":204,"y":494,"width":245,"height":31},"snapshot":26}`.
- **T3.5 Eclipse-aware observe tools (`read_views`, `read_selection`, `read_outline`, `read_properties`, `read_editor`):**
  - `read_views` -> `{"activePartId":"com.bosch.autosartool.ui.explorer","activePartTitle":"AR Explorer","views":[{"id":"com.bosch.autosartool.ui.explorer","title":"AR Explorer",...},{"id":"org.eclipse.ui.views.PropertySheet","title":"Properties",...},...],"snapshot":27}`.
  - `read_selection` -> `{"partId":"com.bosch.autosartool.ui.explorer","partTitle":"AR Explorer","kind":"empty","empty":true,"count":0,"items":[],"snapshot":28}`.
  - `read_outline` -> `{"open":false,"viewId":"org.eclipse.ui.views.ContentOutline","message":"Outline view is not open","items":[],"totalCount":0,"hasMore":false,"snapshot":29}`.
  - `read_properties` -> `{"open":true,"viewId":"org.eclipse.ui.views.PropertySheet","title":"Properties","ref":"e100","offset":0,"limit":10,"totalCount":0,"hasMore":false,"properties":[],"snapshot":30}`.
  - `read_editor` (no editor open) -> `{"code":"not-found","message":"no editor is open in the workbench","hints":["open a file or model in an editor first"]}`.
- **T3.6–T3.10 Observe tools (`read_console`, `read_progress`, `read_problems`, `read_error_log`, `read_junit`, `read_debug` + modal open):**
  - `read_console` -> `{"activeConsole":"Migrator Console","consoles":[{"name":"Migrator Console","type":"org.eclipse.ui.MessageConsole","active":true}],"ref":"e282","text":"","lines":[],"offset":0,"limit":20,"totalLines":0,"hasMore":false,"snapshot":31}`.
  - `read_progress` (`include_system: true`) -> `{"idle":true,"runningCount":0,"totalJobs":4,"jobs":[{"name":"Update Job","state":"sleeping","system":true,"user":false,"priority":30},{"name":"Compacting resource model","state":"sleeping","system":true,"user":false,"priority":50},{"name":"Workbench Auto-Save Job","state":"sleeping","system":true,"user":false,"priority":30},{"name":"Updating script repository","state":"sleeping","system":false,"user":false,"priority":30}],"progressViewOpen":false,"progressViewEntries":[],"snapshot":32}`.
  - `read_problems` -> `{"counts":{"errors":0,"warnings":0,"infos":0},"offset":0,"limit":20,"totalCount":0,"hasMore":false,"problems":[],"snapshot":33}`.
  - `read_error_log` (`limit: 5`) -> `{"logFile":"...\\.metadata\\.log","offset":0,"limit":5,"totalCount":136,"hasMore":true,"entries":[{"severity":"info","plugin":"org.eclipse.core.resources","code":0,"timestamp":"2026-10-27 12:39:53.838","message":"RefreshJob: refresh is activated"},...],"snapshot":58}`.
  - `read_junit` -> `{"available":true,"viewOpen":false,"runCount":0,"runs":[],"snapshot":35}`.
  - `read_debug` -> `{"launchCount":0,"launches":[],"breakpointsEnabled":true,"breakpointCount":0,"breakpoints":[],"snapshot":36}`.
  - **Modal test (`T3.10`):** All 26 observe tools executed and returned structured data while modal `New Project` (`modality: "primary"`) was open.

## 5. How to Resume
1. Read `A:\sandbox\workspace\eclipse_driver\SPEC.md`, `A:\sandbox\workspace\eclipse_driver\tasks\plan.md`, and `A:\sandbox\workspace\eclipse_driver\tasks\todo.md`.
2. Run `A:\sandbox\workspace\eclipse_driver\build.cmd all` to confirm the baseline (`95 passed, 0 failed`).
3. After owner acceptance of M3, proceed with **M4 (`T4.1 wait_for` .. `T4.11`)**.

## 6. Standing Constraints (from `SPEC.md`)
- Work only inside `A:\sandbox\workspace\eclipse_driver` and `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent`.
- Never modify `A:\sandbox\Products\ISOLAR-AB\ISOLAR-AB_V12.13.0RC1` (except in M5 `install_agent` test with backup/restore verification).
- Never bind to a non-loopback address or add an eval tool.
- Never run `git init` or add external dependencies without owner approval.

## Status (final session)
All 91 catalog tools implemented (M1-M6). build.cmd all: 117 passed, 0 failed; build.cmd it: passed, install unchanged. Skill at skill\eclipse-driver\SKILL.md. Not live-verified: attach (JVM attach permission), install mode on a real install, native-dialog tools, pointer/key paths (Display.post). The agent bundle is no longer lazy-activated so install mode autostarts.
