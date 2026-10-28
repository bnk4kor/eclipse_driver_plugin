# Implementation Plan: eclipse-driver

Source: `SPEC.md` (approved with the 2026-10-26 answers). Task checklist: `tasks/todo.md`.

## Overview

Build an MCP server (standalone Java 21 process, stdio) plus one OSGi agent bundle that lets an AI observe and operate a running Eclipse RCP application. The server exposes three MCP meta-tools (`list_tools`, `describe_tool`, `call_tool`) over a catalog of 91 tools defined once as data in `protocol/`. Work follows the spec milestones M1 to M6. Every tool is a vertical slice: catalog entry, agent handler, server routing, test.

## Architecture decisions

- **Two processes, fixed by the owner.** Agent (inside the target JVM, UI-thread access) and server (MCP stdio, launcher, attacher, native dialogs).
- **Catalog as data.** `protocol/` holds name, category, summary, schema and example per tool. Server validator, `list_tools`, `describe_tool`, skill reference files and the count test all read it. No second copy.
- **No libraries.** Hand-written JSON codec and MCP stdio loop (owner: "lightweight things which are easy to understand"). Schema validation is a small subset: `type`, `required`, `enum`, `properties`, `items`.
- **Plain-JDK unit tests with a tiny hand-written runner** (assumption, see open questions). Tests live in `test\<module>\`, run by `build.cmd test`.
- **Shared protocol sources.** `protocol/src` is compiled into the server jar and into the agent bundle. In Eclipse the agent project sees it through a linked source folder. `build.cmd` compiles it directly.
- **Agent projects live in the Orion workspace** (`...\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent`, already created). Everything else lives in `A:\sandbox\workspace\eclipse_driver`.
- **Optional target bundles.** JUnit, debug, console and IDE bundles are imported with `resolution:=optional`, so the agent still resolves on an RCP that lacks them.
- **Transport and port handoff** are decided by the spike T1.0, then written into `docs/ARCHITECTURE.md`. Starting assumption: HTTP+JSON over `java.net` sockets (no `jdk.httpserver`, no OSGi `com.sun.*` export problem). The server chooses the token and passes it as `-Declipse.driver.token`. The agent writes its port to a ready file.
- **Build** is `build.cmd` with JDK `C:\toolbase\_ldata\java_jdk\.98-21.0.7.6`, compiling the agent against `plugins\*.jar` of the first `.target` location.

## Dependency graph

```
T1.0 spike (transport, launch mechanism) ─┬─> T1.6 agent endpoint ─> T1.7 UiExecutor + list_shells ─┐
T1.1 protocol core ─> T1.2 catalog ─> T1.3 MCP adapter ──────────────────────────────────────────────┼─> T1.8 launcher + connect ─> T1.9 IT
                         T1.5 build.cmd + extractor ────────────────────────────────────────────────┘
                                                      │
M2: T2.1 registry/snapshots ─> T2.2 menu_select, T2.3 read_dialog ─> T2.4 wizard tools ─> T2.5 IT
M3: T3.1 matchers/tree ─> (T3.2 .. T3.9 observe slices, parallel) ─> T3.10 modal IT
M4: T4.1 wait_for ─> T4.2..T4.8 act slices (parallel)      T4.9 ─> T4.10 native dialogs (server only, parallel) ─> T4.11 IT
M5: T5.1 attach ─> T5.2 multi-instance ─> T5.4 install+relaunch    T5.3 install files (parallel)    T5.5, T5.6 launch/process ─> T5.7 IT
M6: T6.1 trace/journal    T6.2 catalog+reference ─> T6.3 SKILL ─> T6.4 budget    T6.5 build.cmd final    T6.6 docs ─> T6.7 scenario 7
```

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Agent does not load under simpleconfigurator in launch mode (Q2) | High, blocks M1 | Spike T1.0 first. Try in order: separate config area with a copied `bundles.info`, `osgi.framework.extensions`, `-Dosgi.bundles`. Pick the first that works with nothing written to the install. |
| Attach disabled on the product (`-XX:+DisableAttachMechanism`) or stub cannot find the framework | High, blocks scenario 4 | Recommend pulling an attach feasibility spike into M1 (see open questions). Install-and-relaunch is the fallback. |
| Native dialog automation without a dependency (Q4) | High, blocks scenario 6 | Owner approval gate before T4.9. Proposal: PowerShell UI Automation called from the server, no extra library. |
| Synthetic input (`Display.post`) unreliable | Medium | T4.2 tries `Display.post`, falls back to `notifyListeners`, documents which tool uses which. |
| JUnit child instance does not carry the agent (Q5) | Medium | Attach by pid (T5.7). |
| Install-and-relaunch rejected by ISOLAR patcher or license check (Q13) | Medium | Proven in T5.4 on a copy-safe path first. `uninstall_agent` hash test (T5.3) runs before any relaunch. |
| 91 tool schemas drift from handlers | Medium | Catalog is the single source. Test T6.2 counts names and compares with the spec. |
| Agent logic needing SWT cannot be unit tested | Medium | Keep matchers, registry, diffing and JSON in plain classes (unit tested). SWT code stays thin, covered by integration tests. |

## Definition of done per tool (applies to every tool task)

1. Catalog entry exists with category, summary, schema and example.
2. Agent handler returns structured JSON. Errors use the R1 codes.
3. Server routes the call and rejects bad args with `invalid-args`.
4. Unit test for the pure logic. One real call against ISOLAR-AB recorded as evidence in the milestone report.

## Common commands

- Unit tests: `build.cmd test`
- Integration tests: `build.cmd it --target A:\sandbox\Products\ISOLAR-AB\ISOLAR-AB_V12.13.0RC1`
- Build artifacts: `build.cmd`

## Tasks

Size: XS 1 file, S 1-2, M 3-5. No task is larger than M.

### Phase M1: Skeleton, catalog, meta-tools, launch, connect, list_shells

**T1.0 Spike: transport and launch mechanism** (S, deps: none)
Prove the agent loads in a launched ISOLAR-AB with nothing written to the install, and that the agent can open a loopback socket from inside the OSGi framework. Throwaway code. Result goes to a first draft of `docs/ARCHITECTURE.md`.
- Acceptance: the chosen load mechanism is named. A log line from `Activator.start` appears in the launched instance. The install directory hash is unchanged. Transport choice with justification is written.
- Verify: launch with the mechanism, show the log line, compare file hashes of the install before and after.
- Files: `docs/ARCHITECTURE.md`, agent `Activator.java`.

**T1.1 Protocol core: JSON codec, errors, test runner** (M, deps: none)
- Acceptance: parse and serialize all JSON types incl. escapes and unicode. `ToolError` carries `code`, `message`, `hints`, with all R1 codes as constants. `build.cmd test` compiles `protocol` and runs tests.
- Verify: `build.cmd test` passes. A deliberately bad JSON input fails with a clear error.
- Files: `protocol/src/.../Json.java`, `ToolError.java`, `ErrorCode.java`, `test/protocol/JsonTest.java`, `test/TestRunner.java`.

**T1.2 Catalog model, validator, first entries** (M, deps: T1.1)
- Acceptance: `ToolSpec` (name, category, summary, schema, example). Validator supports `type`, `required`, `enum`, `properties`, `items`. Entries for `connect`, `get_status`, `list_shells`. `invalid-args` response includes the tool schema. `unknown-tool` lists closest names.
- Verify: `build.cmd test`: valid args pass, missing or wrong-type args fail with `invalid-args`, typo `list_shel` suggests `list_shells`.
- Files: `protocol/src/.../Catalog.java`, `ToolSpec.java`, `SchemaValidator.java`, `test/protocol/CatalogTest.java`.

**T1.3 MCP stdio adapter with three meta-tools** (M, deps: T1.2)
- Acceptance: `initialize` returns the `instructions` string from 6.11. `tools/list` returns exactly three tools. `list_tools` (category and query), `describe_tool` (1 to 8 names), `call_tool` (validates, then dispatches; with no connection returns `not-connected`).
- Verify: unit test feeds JSON-RPC lines through the loop and checks replies. Manual: pipe `initialize`, `tools/list`, `list_tools` into the server jar and show output.
- Files: `server/src/.../McpServer.java`, `MetaTools.java`, `Main.java`, `test/server/McpServerTest.java`.

**T1.5 build.cmd and embedded-jar extraction** (M, deps: T1.1; agent jar content grows with T1.6, T1.7)
- Acceptance: `build.cmd` compiles protocol, builds `agent-bundle.jar` (manifest from the agent project, classes compiled against the target `plugins\*.jar`), compiles the server, writes `manifest.properties` with SHA-256, assembles `dist\eclipse-driver-server.jar`. First run extracts to `%LOCALAPPDATA%\eclipse-driver\<version>\` and verifies the hashes. A tampered extracted file is re-extracted. (Attach stub added in T5.1.)
- Verify: `build.cmd` produces the jar. Packaging unit test: embedded jar present, hash matches, `Bundle-SymbolicName` set, no class from target bundles inside.
- Files: `build.cmd`, `server/src/.../Extractor.java`, `test/server/PackagingTest.java`.

**T1.6 Agent RPC endpoint** (M, deps: T1.0, T1.1)
- Acceptance: agent stays dormant without `eclipse.driver.token`. With it, binds `127.0.0.1` only, requires the token on every request (wrong token is rejected), writes its port to the ready file. One `ping` method.
- Verify: unit test on the endpoint class without SWT (token accepted and rejected, non-loopback bind refused). Integration evidence in T1.9.
- Files: agent `Activator.java`, `RpcServer.java`, `test/agent/RpcServerTest.java`.

**T1.7 UiExecutor and `list_shells`** (M, deps: T1.2, T1.6)
- Acceptance: all widget access goes through `UiExecutor.call(timeout, work)`, which waits off the UI thread and throws `timeout` or `ui-thread-blocked`. `list_shells` returns title, modality, active flag and bounds for every shell, including disposed-safe handling (R2).
- Verify: unit test of the executor with a fake display runner (timeout path). Real result in T1.9.
- Files: agent `UiExecutor.java`, `ShellHandlers.java`, `test/agent/UiExecutorTest.java`.

**T1.8 Launcher and `connect` (launch mode)** (M, deps: T1.0, T1.3, T1.5, T1.6)
- Acceptance: `connect` mode `launch` reads the launcher `.ini`, starts the install with the agent from the extracted jar using the mechanism from T1.0, waits for the ready file, registers the instance, returns an instance id. `get_status` reports state and agent version. `call_tool` forwards to the agent with the token. Launch failure returns a structured error with the process output tail.
- Verify: unit test of `.ini` parsing and argument assembly. Integration in T1.9.
- Files: `server/src/.../Launcher.java`, `IniReader.java`, `AgentClient.java`, `InstanceRegistry.java`, tests.

**T1.9 Integration: launch ISOLAR-AB and `list_shells`** (S, deps: T1.7, T1.8)
- Acceptance: through `call_tool`: `connect` launch, then `list_shells` returns the main shell. Install files hash-identical before and after.
- Verify: `build.cmd it --target <ISOLAR-AB path>`; paste real output in the report.
- Files: `it/src/.../LaunchIT.java`, `it/run`.

**Checkpoint M1**
- [ ] `build.cmd` and `build.cmd test` pass, actual output in the report
- [ ] Server registers exactly three MCP tools
- [ ] Real `list_shells` result from ISOLAR-AB in the report
- [ ] Owner accepts the report before M2

### Phase M2: Dialogs and wizards

**T2.1 Element registry and snapshot ids** (M, deps: T1.7)
- Acceptance: registry maps element refs to widget handles. Every UI-dependent response carries a snapshot id. A ref from an older snapshot whose widget changed or was disposed returns `stale-ref`. Handles are held weakly or by id, so a disposed widget never throws (R2, R6).
- Verify: `build.cmd test` with a fake handle type: stale, disposed and valid cases.
- Files: agent `ElementRegistry.java`, `Snapshot.java`, `test/agent/ElementRegistryTest.java`.

**T2.2 `menu_select`** (S, deps: T2.1)
- Acceptance: path form `File/New/Project...` selects the item. If the action opens a modal dialog the call returns without blocking. Missing item returns `not-found` with the available siblings.
- Verify: integration call opens the New wizard.
- Files: agent `MenuHandlers.java`, catalog entry.

**T2.3 `read_dialog`** (M, deps: T2.1)
- Acceptance: for a shell returns message text, labels, buttons (label, enabled), inputs (label, value, enabled), tables and trees, banners, and wizard page title, description and Back/Next/Finish/Cancel state. Works while the dialog is modal.
- Verify: integration read of the New Project wizard and of an error dialog.
- Files: agent `DialogReader.java`, catalog entry, `test/agent` for any pure label-association logic.

**T2.4 Wizard and dialog actions** (M, deps: T2.3)
- Acceptance: `wizard_next`, `wizard_back`, `wizard_finish`, `wizard_cancel`, `dialog_confirm`, `dialog_dismiss`. A destructive result reports the resulting dialog (R4). Disabled button returns `disabled`.
- Verify: integration in T2.5.
- Files: agent `WizardHandlers.java`, catalog entries.

**T2.5 Integration: wizard and error dialog** (S, deps: T2.2, T2.4)
- Acceptance: open File > New > Project, read it, Next then Back, cancel. Trigger an error dialog and read it. All through `call_tool`.
- Verify: `build.cmd it`, output pasted in the report.
- Files: `it/src/.../WizardIT.java`.

**Checkpoint M2**
- [ ] Evidence for each flow above
- [ ] Owner accepts the report before M3

### Phase M3: Observe

**T3.1 Matchers, `read_widget_tree`, `find_elements`, `read_element`** (M, deps: T2.1)
- Acceptance: matcher on text, label, id, class, tooltip; `ambiguous-match` lists candidates. Tree is depth-bounded with refs.
- Verify: matcher unit tests. Integration call on the main shell.
- Files: agent `Matcher.java`, `WidgetHandlers.java`, tests.

**T3.2 `read_text`, `read_tree`, `read_table`** (M, deps: T3.1)
- Acceptance: paged reads (offset, limit) that do not expand lazy trees fully. Text, StyledText, editor and console content.
- Verify: integration on a tree with many nodes. Unit test on paging math.

**T3.3 `read_workbench`, `read_menu`, `read_toolbar`, `read_status_line`, `read_clipboard`** (M, deps: T3.1)
- Acceptance: menu structure read without clicking. Window, perspective, editors with dirty flag.
- Verify: integration.

**T3.4 `screenshot`, `state_diff`** (S, deps: T3.1)
- Acceptance: PNG of shell, element or region. `state_diff` returns what changed since a snapshot id.
- Verify: unit test of diff logic. Integration saves a PNG that is non-empty.

**T3.5 `read_editor`, `read_views`, `read_selection`, `read_outline`, `read_properties`** (M, deps: T3.1)
- Acceptance: editor path, dirty flag, cursor, selection, form-editor pages.
- Verify: integration with an editor open.

**T3.6 `read_console`, `read_progress`** (S, deps: T3.1)
- Acceptance: console output with switcher. Running jobs and Progress view entries. Missing `org.eclipse.ui.console` bundle gives a clear error.
- Verify: integration shows a running job.

**T3.7 `read_problems`, `read_error_log`** (S, deps: T3.1)
- Acceptance: severity, message, resource.
- Verify: integration.

**T3.8 `read_junit`** (S, deps: T3.1)
- Acceptance: runs, failures, traces. Optional bundle handling.
- Verify: integration after a JUnit run (full proof in T5.7).

**T3.9 `read_debug`** (S, deps: T3.1)
- Acceptance: launches, threads, frames, variables, breakpoints.
- Verify: integration.

**T3.10 Integration: observe tools with a modal dialog open** (S, deps: T3.1 to T3.9)
- Acceptance: every observe tool returns real data while a modal dialog is open.
- Verify: `build.cmd it`, results for all 15 plus 11 observe tools in the report.

**Checkpoint M3**
- [ ] Every observe tool has real data from ISOLAR-AB in the report
- [ ] Owner accepts the report before M4

### Phase M4: Act and native dialogs

**T4.1 `wait_for`** (M, deps: T2.1)
- Acceptance: conditions shell opened or closed, text appears, element enabled, jobs idle, UI idle, console contains, editor open, JUnit finished, process exit. Always has a timeout, always returns, never blocks the UI thread (R5).
- Verify: unit test of polling and timeout. Integration per condition that can be triggered.

**T4.2 Pointer actions** (M, deps: T3.1)
`click`, `double_click`, `right_click_menu`, `hover`, `scroll`. Button and modifiers. A click that opens a modal returns once it appears.
- Verify: click a Browse-style button that opens an SWT dialog.

**T4.3 Keyboard and text** (S, deps: T3.1)
`press_key`, `key_chord`, `type_text`, `set_text`.
- Verify: unit test of key-chord parsing. Integration types into a text field.

**T4.4 Widget actions** (S, deps: T3.1)
`select` (combo, list, table, tree, tab), `check`, `uncheck`, `expand`, `collapse`.
- Verify: integration on a preferences page.

**T4.5 Window control and drag-drop** (S, deps: T3.1)
`focus`, `move_resize_shell`, `maximize_minimize`, `drag_drop`.
- Verify: integration on a dialog shell.

**T4.6 Workbench: menus, views, perspectives** (M, deps: T3.3)
`context_menu_select`, `toolbar_click`, `open_view`, `activate_view`, `close_view`, `open_perspective`, `reset_perspective`.
- Verify: open Build Control view and Console view.

**T4.7 Workbench: editors and commands** (M, deps: T3.5)
`open_editor`, `save`, `save_all`, `close_editor`, `run_command`, `quick_access`, `undo`, `redo`. Close without saving reports the resulting dialog (R4).
- Verify: integration opens, edits and saves a file.

**T4.8 Preferences** (S, deps: T4.4)
`open_preferences`, `set_preference`.
- Verify: integration sets and reads back a value.

**T4.9 Native dialog detection and screenshot** (M, deps: none; owner approval of approach, Q4)
`list_native_dialogs`, `native_dialog_screenshot`. Widget tools return `ui-thread-blocked` naming the dialog while one is open.
- Verify: unit test of the output parser. Integration with a real Browse button.

**T4.10 Native dialog actions** (M, deps: T4.9)
`native_dialog_set_path`, `native_dialog_confirm`, `native_dialog_cancel`, `native_dialog_press`.
- Verify: integration in T4.11.

**T4.11 Integration: scenarios 1 and 6** (M, deps: T4.1 to T4.10)
- Scenario 1: edit an AUTOSAR configuration in an editor, open Build Control, run the build.
- Scenario 6: click a Browse button, handle the native file dialog, confirm the value arrives in the field.
- Verify: `build.cmd it`, full call log in the report.

**Checkpoint M4**
- [ ] Scenarios 1 and 6 evidence
- [ ] Owner accepts the report before M5

### Phase M5: Attach, install, multi-instance, process and launch

**T5.1 Attach stub, attach mode, `list_instances`** (M, deps: T1.8)
- Acceptance: `attach-stub.jar` (`Agent-Class`) added to the fat jar and extracted. `connect` mode `attach` by pid loads the stub, which installs and starts the agent bundle from the extracted path, no restart. If attach is disabled, the error carries diagnostics and suggests mode `install`. `list_instances` shows pid, product, launch name, agent loaded.
- Verify: packaging test extended. Integration attaches to a running instance. Disabled-attach case tested with `-XX:+DisableAttachMechanism`.

**T5.2 Multi-instance routing and `disconnect`** (S, deps: T5.1)
- Acceptance: `instance` argument on every call, default first connected. `disconnect` detaches, stops only if the driver launched it and the caller asks.
- Verify: unit test of routing. Integration with two instances.

**T5.3 `install_agent`, `uninstall_agent`** (M, deps: T1.5)
- Acceptance: place `eclipse-driver\agent-bundle.jar`, add one line to `bundles.info` after saving `bundles.info.eclipse-driver.bak`. Not writable returns `install-not-writable` naming the path, nothing half-written. Uninstall restores files byte-identical.
- Verify: hash test on a copy of the install configuration, before and after. Real install checked in T5.4.

**T5.4 Install-and-relaunch `connect`** (M, deps: T5.2, T5.3)
- Acceptance: `connect` mode `install` fails with `restart-not-confirmed` unless `confirm_restart: true`. Stops the instance, relaunches from the `.ini` plus the enable property, reports the limit on arguments outside the `.ini`. Agent dormant on a normal start.
- Verify: integration on ISOLAR-AB, then `uninstall_agent` and hash compare of touched files. Answers Q13.

**T5.5 `run_script`, `read_process_output`, `stop_process`** (M, deps: T1.8; script path from Q6)
- Acceptance: run the owner's launch script with inputs, stream output, return the exit code. Buffered reads and stop for driver-started processes.
- Verify: unit test with a trivial script. Integration with the real script.

**T5.6 `list_launch_configs`, `launch`, `terminate_launch`** (M, deps: T3.9)
- Acceptance: list configurations, run or debug by name, terminate.
- Verify: integration launches a configuration and terminates it.

**T5.7 Integration: scenarios 2 to 5** (M, deps: T5.1 to T5.6)
- Scenarios: JUnit Plug-in Test via Debug configuration or `run_script`; read JUnit, Console, Problems; attach to the child instance by pid and act in it; edit the test and rerun until green.
- Verify: `build.cmd it`, call log in the report.

**Checkpoint M5**
- [ ] Scenarios 2 to 5 evidence
- [ ] `uninstall_agent` byte-identical result in the report
- [ ] Owner accepts the report before M6

### Phase M6: Packaging, skill, docs

**T6.1 Trace, logs, journal** (S, deps: T1.8)
`set_trace`, `get_logs`, `journal_note`, `get_journal`. Trace records every call with timing and the catalog tool name.
- Verify: unit test. Integration shows a trace file after a session.

**T6.2 Catalog completeness and reference generation** (M, deps: M5 tools done)
- Acceptance: test asserts 91 catalog names, each with category, summary, schema and example. `build.cmd` generates `skill/eclipse-driver/reference/<category>.md`.
- Verify: `build.cmd test`, generated files listed.

**T6.3 `SKILL.md`** (S, deps: T6.2)
- Acceptance: loop, category index, recipes for the seven scenarios, rules. Test fails if it names a tool not in the catalog or omits one.
- Verify: `build.cmd test`.

**T6.4 Context budget test** (S, deps: T6.2, T6.3)
- Acceptance: meta-tool definitions plus `instructions` at most 1,500 tokens, `SKILL.md` at most 3,000 (4 characters per token). A typical session costs under 25% of direct registration.
- Verify: test prints the real numbers, pasted in the report.

**T6.5 Final `build.cmd`** (M, deps: T6.2)
- Acceptance: one command produces `dist\` with the fat jar (both embedded jars, `manifest.properties`) and `skill\eclipse-driver\`. Targets `test`, `it`, `package`.
- Verify: run from a clean tree, packaging unit test passes.

**T6.6 README and `ARCHITECTURE.md`** (S, deps: T6.5)
- Acceptance: install, MCP client config snippet, tool reference pointer, limits, troubleshooting. Transport justification final.
- Verify: follow the README from a clean checkout.

**T6.7 Scenario 7 and smoke check** (S, deps: T6.1, T6.5)
- Acceptance: a tester session with journal notes and trace. A smoke check on one other RCP if available (Q9).
- Verify: journal and trace excerpts in the report.

**Checkpoint: Complete**
- [ ] All seven scenarios demonstrated against ISOLAR-AB
- [ ] Context budget numbers in the report
- [ ] Owner accepts

## Open questions

1. **Unit test runner:** assumption is a tiny hand-written runner, because JUnit would be a new dependency. Confirm, or approve JUnit from the target's `plugins`.
2. **Attach spike timing:** recommend a short attach feasibility check at the end of M1, because it decides whether the agent must support late loading. The spec order puts attach in M5. Pull it forward?
3. **Native dialogs (Q4):** approve PowerShell UI Automation from the server, before T4.9.
4. **Launch script (Q6):** path and parameters, before T5.5.
5. **Child instance (Q5):** confirm attach by pid for the JUnit child.
6. **Skill install path (Q11)** and **second RCP for the smoke check (Q9)**: before M6.
7. **Task order change:** `menu_select` moved from M4 to M2, because the M2 evidence needs it to open the wizard. OK?
