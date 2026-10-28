# Spec: eclipse-driver

Status: DRAFT for review. Source: `eclipse_control_mcp.txt` (original draft) plus the requirements interview of 2026-10-26.
Where the interview changed the draft, this document follows the interview and says so in section 14.

## 1. Objective

### 1.1 What
eclipse-driver is an MCP server that lets an AI client observe and operate a running Eclipse-based RCP application (the Eclipse IDE or any RCP product) the way a human does: read dialogs, views and editors, click, type, run wizards, run tests, read results.

### 1.2 Why
ISOLAR-AB ships its own MCP server, but that server is model-level (AUTOSAR data). It cannot reach the GUI. The owner wants an AI to run edit, build, test, fix loops on its own and to act as a feature tester that reports usability feedback.

### 1.3 Who
The owner, using Claude Code, on ISOLAR-AB first. Sample target: `A:\sandbox\Products\ISOLAR-AB\ISOLAR-AB_V12.13.0RC1\` (Equinox 3.23.100, SWT 3.130.0, bundled Temurin JDK 21.0.7 in `javalibs\jdk`, no SWTBot). The tool must also work on RCP installs it has never seen.

### 1.4 Target scenarios (the product is judged by these)
1. The AI edits an AUTOSAR configuration in an editor, opens the Build Control view and runs the build.
2. The AI starts a JUnit Plug-in Test through a Debug configuration, or through the owner's launch script (`run_script`), which streams output and returns the exit code.
3. The AI reads the parent instance's JUnit, Console and Problems views.
4. The JUnit Plug-in Test starts a second (child) instance of the tool. The AI attaches to the child explicitly, observes it, and may act in it (for example, to handle an unexpected dialog).
5. The AI edits the testcase and reruns, repeating until green.
6. The AI works through a Browse button that opens a native OS file dialog, end to end.
7. As a feature tester, the AI uses the tool like a user, records findings in a session journal, and the opt-in call trace supplies evidence for improvement feedback.

## 2. Architecture (fixed by the owner)

Two processes. They must not be collapsed.

1. **Agent**: one OSGi bundle (a single plugin) injected into the target JVM. It runs inside the UI runtime so it can reach the SWT `Display`, workbench and jobs. It exposes a loopback-only, token-authenticated RPC endpoint.
2. **Server**: a standalone process speaking MCP over stdio. It launches or attaches to the target, loads the agent, and translates MCP tool calls to agent RPC. It also handles native OS dialogs, which the agent cannot see.

Three connect modes are required. For an instance that is already running, the order is: attach first, then install-and-relaunch if attach fails and the owner confirms the restart.

- **Launch** (default for a new start): start the install from its path (a directory or a single launcher or jar) with the agent bundle added from a location outside the install. Allowed mechanisms: launcher args, `-vmargs`, `-Dosgi.bundles`, `-Dosgi.framework.extensions`, or a separate configuration area. Writes nothing into the install directory.
- **Attach** (default for a running instance): connect to an already running instance through the JVM Attach API, load the attach stub (a small Java agent), and have it install and start the OSGi agent bundle from a path outside the install. No restart. If attach is disabled, fail with a clear error that includes diagnostics and offers install-and-relaunch.
- **Install-and-relaunch** (opt-in, see 2.2): copy the agent bundle into a folder beside the launcher executable, register it with OSGi, and relaunch the instance. This is the most reliable way to load the agent. It is allowed to write into the install directory.

The RPC transport (HTTP+JSON or WebSocket) is chosen and justified in a short `ARCHITECTURE.md` that is a deliverable.

### 2.1 Packaging: the embedded agent jars
The distributable is one server jar. The agent is not a separate download. It is embedded in the server jar as a resource and extracted when needed.

```
dist\
  eclipse-driver-server.jar
    META-INF/MANIFEST.MF          Main-Class = server entry point
    <server and protocol classes>
    embedded/agent-bundle.jar     The OSGi bundle that runs inside the target JVM. Holds all in-process
                                  logic: UI executor, element registry, matchers, dialogs, workbench, text,
                                  jobs, debug, JUnit, RPC endpoint. Manifest has Bundle-SymbolicName and
                                  Require-Bundle or Import-Package on the target's own bundles. Contains
                                  the protocol classes, and nothing from the target.
    embedded/attach-stub.jar      Tiny Java agent for attach mode. Manifest has Agent-Class. Its only job:
                                  find the running OSGi framework, install and start agent-bundle.jar.
    embedded/manifest.properties  Version and SHA-256 of both jars.
  skill\eclipse-driver\           See 6.11.
```

- **Extraction**: on first use the server extracts both jars to `%LOCALAPPDATA%\eclipse-driver\<version>\`, verifies the SHA-256 against `manifest.properties`, and reuses them on later runs. Launch and attach modes point the target JVM at these extracted files.
- **Install mode** copies `agent-bundle.jar` from the extraction folder into the install (see 2.2). It is the same file, so there is one build artifact.
- **Build order in `build.cmd`**: compile `protocol`, build `agent-bundle.jar`, build `attach-stub.jar`, compile `server`, then assemble the fat jar with the JDK `jar` tool and embed the two jars and `manifest.properties`.
- **Agent compile classpath** (provisional, see open question 14): `build.cmd` compiles the agent with `javac` against the target's `plugins\*.jar` (argument `--plugins`, default the sample install). A PDE export of the agent plugin is accepted as an alternative input. The dev `.target` is now known (see section 3); the `build.cmd` compile classpath is the `plugins` folder of the first location in that file, and is confirmed in M1.
- **Unit test**: the fat jar contains both embedded jars, the hashes match, the bundle manifest has `Bundle-SymbolicName`, the stub manifest has `Agent-Class`, and the bundle contains no class from the target's bundles.

### 2.2 Install-and-relaunch mode
Used when attach is disabled or fails, or when the owner asks for it with `connect` mode `install`.

1. **Place**: create `<install>\eclipse-driver\` next to the launcher executable and copy `agent-bundle.jar` into it.
2. **Register**: add the bundle to OSGi in the way the install uses. For simpleconfigurator installs (such as ISOLAR-AB) add one line to `configuration\org.eclipse.equinox.simpleconfigurator\bundles.info`. For other installs use the mechanism that applies (for example `osgi.bundles` in `config.ini`). Before the first edit, save the original as `<file>.eclipse-driver.bak`.
3. **Relaunch**: read the launcher `.ini` next to the executable, stop the running instance, and start it again with the `.ini` arguments plus the enable property below. This destroys unsaved state, so the call fails with `restart-not-confirmed` unless the caller passes `confirm_restart: true`.
4. **Dormant by default**: the agent starts the RPC endpoint only when the enable property carrying the session token is present. A normal user launch has no such property, so the agent does nothing and opens no port.
5. **Revert**: `uninstall_agent` removes the folder, restores `bundles.info` from the backup, and leaves the instance running unless the caller asks for a restart. After uninstall the touched files must be byte-identical to the originals.
6. **Failure**: if the install is not writable, fail with `install-not-writable` and name the path. Nothing is left half-written.
7. **Limit**: the child instance started by a JUnit Plug-in Test does not get the agent this way (PDE gives it its own configuration). Scenario 4 uses attach by pid for the child.

## 3. Tech stack and environment

Observed on the owner's machine (2026-10-26):

| Item | Finding |
|---|---|
| Java on PATH | 1.8.0_501 only. No `JAVA_HOME`. |
| Maven / Gradle | Not installed. No `~/.m2`. |
| Sample target JDK | Temurin 21.0.7, has `javac`, `jar`, `jdk.attach`, `jdk.httpserver`, `java.net.http`. |
| Sample target plugins | `org.eclipse.osgi_3.23.100`, `org.eclipse.swt_3.130.0`, `org.eclipse.ui.workbench_3.135.100`, `org.eclipse.debug.ui_3.18.800`, `org.eclipse.jdt.junit_3.17.100`, `org.eclipse.pde.launching_3.13.400`, `org.eclipse.pde.junit.runtime_3.8.100`, `org.eclipse.ui.console_3.14.300`. No SWTBot. |
| Sample target launch model | `config.ini` uses simpleconfigurator and `osgi.framework.extensions` (patcher, fx.osgi). Launcher ini uses `-vm ./javalibs/jdk/bin/javaw.exe`. |

Decisions:

- **Language**: Java.
- **Agent**: one OSGi plugin `com.bosch.eclipsedriver.agent`, a PDE plugin project at `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent`. Developed in the owner's Eclipse against the owner's target file `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\Target_12.14\2027.1.target` (resolved locally: directory locations under `A:\sandbox\workspace\Orion_WS\Target\Target_12.14.0_Oct26\`, base Eclipse 4.36 with Artop 4.19). The `.target` is the compile classpath only. The agent declares the target's own bundles in its manifest and bundles nothing from them.
- **SWT access**: direct SWT (`Display.syncExec`, `asyncExec`, widget-tree traversal). SWTBot is not a runtime dependency, because the sample target does not ship it.
- **Server**: a separate fat jar that embeds the agent bundle jar and the attach stub jar, and extracts them to a config area on first use (see 2.1).
- **Java release**: 21. The dev target (Eclipse 4.36) and the sample target (Temurin 21.0.7) both run on Java 21. The server jar runs on the owner's JDK `C:\toolbase\_ldata\java_jdk\.98-21.0.7.6` (Temurin 21.0.7, has `java`, `javac`, `jar`).
- **Libraries**: none. The MCP stdio protocol and the JSON codec are hand-written, small and easy to read (owner decision 2026-10-26: "lightweight things which are easy to understand").
- **Build**: the owner's draft asked for a multi-module Maven build. The interview replaced that with a JDK-only build because Maven is not installed. The dev build is the PDE project. The ship build is `build.cmd` using the bundled JDK and `jar`, with no Maven. Tycho is an alternative if the owner asks for it.
- **Dependencies**: no dependency beyond the JDK without approval. An MCP or JSON library is permitted if the owner approves it (see Open Questions).

## 4. Commands (proposed, to be confirmed in planning)

```
Build ship artifact : build.cmd                      (agent jar + fat server jar -> dist\)
Unit tests          : build.cmd test
Integration tests   : build.cmd it --target <install-path>
Package only        : build.cmd package
```

`build.cmd` uses the JDK located through an explicit `--jdk` argument or `%ECLIPSE_DRIVER_JDK%`, falling back to the sample target's `javalibs\jdk`. It must not rely on the Java 8 on PATH.

MCP client config (to be finalised in the README):

```json
{ "mcpServers": { "eclipse-driver": { "command": "<jdk>\\bin\\java.exe", "args": ["-jar", "<dist>\\eclipse-driver-server.jar"] } } }
```

## 5. Project structure

```
protocol/    DTOs, error codes, and the tool catalog (name, category, summary, schema, example) as data.
             Compiled into both the agent jar and the server jar. Single source for 6.11.
agent/       PDE plugin project: META-INF/MANIFEST.MF, build.properties, src/. UI-thread executor,
             element registry, matchers, dialogs, workbench, text, jobs, debug, junit, RPC endpoint.
server/      MCP stdio adapter (three meta-tools), catalog validator, launcher, attacher, native-dialog handler,
             process manager, tracing.
skill/       eclipse-driver/SKILL.md (hand-written) and reference/*.md (generated from the catalog).
it/          Integration tests that launch and attach to a real install.
docs/        ARCHITECTURE.md (transport choice and justification).
build.cmd    The one-command build.
README.md    Install, MCP client config snippet, tool reference, limits, troubleshooting.
```

Work only inside `A:\sandbox\workspace\eclipse_driver` (server, protocol, skill, docs, `build.cmd`) and the agent plugin project `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\com.bosch.eclipsedriver.agent`. In the layout below, `agent/` is that plugin project; `build.cmd` references it by path. The target install is read-only, except for the `eclipse-driver\` folder and the one registration entry that `install_agent` writes (2.2).

## 6. Functional requirements

Every tool has a typed JSON schema, a precise description and structured JSON output. No prose blobs. Every response that depends on UI state carries a state snapshot id so that stale element references are detected. Every tool accepts an optional `instance` argument, defaulting to the first connected instance.

The tools in 6.1 to 6.9 are the **catalog**. They are not registered one by one as MCP tools. Section 6.11 defines how the AI discovers and calls them without loading all of their schemas into its context.

### 6.1 Session and instances
| Id | Tool | Requirement |
|---|---|---|
| FR-S1 | `connect` | Mode `launch` (install path), `attach` (pid) or `install` (pid or path, needs `confirm_restart: true`, see 2.2). Returns an instance id. If attach fails, the error carries diagnostics and suggests mode `install`. |
| FR-S2 | `disconnect` | Detach, and stop the instance only if this driver launched it and the caller asks. |
| FR-S3 | `list_instances` | Running Eclipse JVMs with pid, product, launch configuration name when known, and whether the agent is loaded. |
| FR-S4 | `get_status` | Instance state, agent version, UI-thread responsiveness, modal state. |
| FR-S5 | `get_logs` | Server and agent log lines, filtered by level and time. |
| FR-S6 | `set_trace` | Turns the opt-in call trace on or off. |
| FR-S7 | `journal_note`, `get_journal` | Record and read the tester's findings and feedback for the session. |
| FR-S8 | `install_agent`, `uninstall_agent` | Place and register, or remove, the agent in an install directory (2.2). `uninstall_agent` restores the original files from backup. |

### 6.2 Launch and process (the loop tools)
| Id | Tool | Requirement |
|---|---|---|
| FR-L1 | `run_script` | Run the owner's test launch script with given inputs, stream its output, return the exit code. |
| FR-L2 | `read_process_output`, `stop_process` | Read buffered output from, and stop, a process the driver started. |
| FR-L3 | `list_launch_configs` | Launch configurations known to the instance. |
| FR-L4 | `launch` | Run or debug a launch configuration by name. |
| FR-L5 | `terminate_launch` | Terminate a running launch. |

### 6.3 Observe, generic (all must work while a modal dialog is open)
| Id | Tool | Requirement |
|---|---|---|
| FR-O1 | `list_shells` | All open shells and dialogs: title, modality, active flag, bounds. |
| FR-O2 | `read_dialog` | For a shell: message text, labels, buttons (label, enabled), inputs (label, value, enabled), tables and trees, error and warning banners, and wizard page title, description and Back/Next/Finish/Cancel state. |
| FR-O3 | `read_workbench` | Active window, perspective, open editors with dirty flag, toolbar and menu items (enabled, checked), status line. |
| FR-O4 | `read_widget_tree` | Bounded-depth widget tree of a shell or view, with stable element refs. |
| FR-O5 | `find_elements` | Matcher query (text, label, id, class, tooltip) that returns refs. |
| FR-O6 | `read_element` | One widget's full state: text, enabled, visible, focus, bounds, tooltip, selection. |
| FR-O7 | `read_text` | Content of a Text, StyledText, editor or console. |
| FR-O8 | `read_tree`, `read_table` | Paged reads, because large trees are lazy. |
| FR-O9 | `read_menu`, `read_toolbar`, `read_status_line` | Menu structure without clicking it, toolbar items, status line. |
| FR-O10 | `screenshot` | PNG of a shell, an element or a region. |
| FR-O11 | `state_diff` | What changed since a snapshot id. |
| FR-O12 | `read_clipboard` | Current clipboard text. |

### 6.4 Observe, Eclipse-aware
| Id | Tool | Requirement |
|---|---|---|
| FR-E1 | `read_editor` | Path, dirty flag, cursor, selection, form-editor pages, content. |
| FR-E2 | `read_views`, `read_selection`, `read_outline`, `read_properties` | Open views, the current selection, Outline and Properties content. |
| FR-E3 | `read_console` | Console view output, with a console switcher. |
| FR-E4 | `read_progress` | Running jobs, progress monitors, Progress view entries. |
| FR-E5 | `read_problems`, `read_error_log` | Entries with severity, message, resource. |
| FR-E6 | `read_junit` | Test runs, failures and traces. |
| FR-E7 | `read_debug` | Launches, threads, stack frames, variables, breakpoints. |

### 6.5 Act, pointer and keyboard
| Id | Tool | Requirement |
|---|---|---|
| FR-A1 | `click`, `double_click`, `right_click_menu`, `hover`, `drag_drop`, `scroll` | Target by element ref or matcher. `click` supports button and modifiers. A click that opens a modal dialog returns once the dialog appears. |
| FR-A2 | `press_key`, `key_chord`, `type_text` | Real key events. |
| FR-A3 | `set_text` | Set the value directly. |
| FR-A4 | `focus`, `move_resize_shell`, `maximize_minimize` | Window control. |

### 6.6 Act, widgets
| Id | Tool | Requirement |
|---|---|---|
| FR-W1 | `select` | Combo, list, table, tree, tab. |
| FR-W2 | `check`, `uncheck`, `expand`, `collapse` | Buttons and tree nodes. |

### 6.7 Act, workbench
| Id | Tool | Requirement |
|---|---|---|
| FR-B1 | `menu_select`, `context_menu_select`, `toolbar_click` | Path form such as `File/New/Project...`. |
| FR-B2 | `open_view`, `activate_view`, `close_view`, `open_perspective`, `reset_perspective` | View and perspective control. |
| FR-B3 | `open_editor`, `save`, `save_all`, `close_editor` | Editor control. |
| FR-B4 | `run_command` | Execute an Eclipse command by id. |
| FR-B5 | `quick_access` | Ctrl+3 search and run. |
| FR-B6 | `open_preferences`, `set_preference` | Preference pages and values. |
| FR-B7 | `undo`, `redo` | Edit history. |

### 6.7a Act, dialogs and wizards
| Id | Tool | Requirement |
|---|---|---|
| FR-D1 | `wizard_next`, `wizard_back`, `wizard_finish`, `wizard_cancel` | Wizard navigation. |
| FR-D2 | `dialog_confirm`, `dialog_dismiss` | Confirm or dismiss the current dialog. |

### 6.8 Wait
| Id | Tool | Requirement |
|---|---|---|
| FR-X1 | `wait_for` | Conditions: shell opened or closed, text appears, element enabled, jobs idle, UI idle, console contains, editor open, JUnit finished, process exit. It always has a timeout and always returns a result. It never hangs. |

### 6.9 Native OS dialogs (Windows only, handled from the server process)
The agent cannot see a native dialog, because the UI thread is inside the OS dialog loop. The server handles them from outside the JVM.

| Id | Tool | Requirement |
|---|---|---|
| FR-N1 | `list_native_dialogs` | Title, owner pid, current filename or path field. |
| FR-N2 | `native_dialog_set_path` | Type a path into the file field. |
| FR-N3 | `native_dialog_confirm` | Press Enter or the Open, Save or OK button. |
| FR-N4 | `native_dialog_cancel` | Press Escape or Cancel. |
| FR-N5 | `native_dialog_press` | Press a named button. |
| FR-N6 | `native_dialog_screenshot` | Screenshot of the native dialog. |

While a native dialog is open, widget tools fail with `ui-thread-blocked` and name the dialog.

Scope limit: native dialogs of the target's own process, basic handling only. General desktop automation is out of scope.

### 6.10 Total scope
91 catalog tools, counted by tool name in 6.1 to 6.9 (session 10, launch-process 6, observe 15, observe-eclipse 11, act-pointer-keyboard 13, act-widgets 5, act-workbench 18, act-dialogs-wizards 6, wait 1, native-dialogs 6). The list above is the complete required set. A unit test counts the catalog and compares it to this number. Adding tools beyond it needs owner approval. The three meta-tools of 6.11 are the only MCP-level tools and are part of the approved set.

### 6.11 Tool discovery: skill plus meta-tools (context budget)
**Problem.** An MCP server normally sends every tool schema to the client at session start. 91 tools would fill the AI's context before it does any work, and most sessions use fewer than 15 of them.

**Decision.** The server registers only three MCP tools. The catalog is browsed on demand. A skill teaches the AI how.

| Meta-tool | Input | Output |
|---|---|---|
| `list_tools` | optional `category`, optional `query` (substring or keyword) | Name, category and a one-line summary per match. No schemas. With no arguments, returns the category list with tool counts. |
| `describe_tool` | `names` (1 to 8 tool names) | Full JSON schema, description, error codes and one example call for each name. |
| `call_tool` | `name`, `args` (object), optional `instance` | The catalog tool's structured result or structured error. |

Rules:
- The server validates `args` against the catalog schema before the call. A bad call returns `invalid-args` with the schema of that tool, so the AI can correct itself in one step.
- An unknown name returns `unknown-tool` with the closest names.
- Categories are the section headings of 6.1 to 6.9: session, launch-process, observe, observe-eclipse, act-pointer-keyboard, act-widgets, act-workbench, act-dialogs-wizards, wait, native-dialogs.
- The catalog is defined once, in `protocol/`, as data (name, category, summary, schema, example). The agent, the server, `list_tools`, `describe_tool`, the validator and the skill reference files are all generated from or checked against it. No second copy to drift.
- The MCP `initialize` response carries a short `instructions` string: "Use list_tools to browse, describe_tool for schemas, call_tool to run. Start with connect." This makes the server usable by a client with no skill support.

**Skill.** `skill/eclipse-driver/` is shipped inside the distributable and installed by copying it to the client's skills folder.

```
skill/eclipse-driver/
  SKILL.md              When to use. The loop: connect, observe, act, wait. Category index with one line per
                        tool name. Short recipes for the seven scenarios in 1.4. Rules the AI must follow
                        (snapshot ids, modal dialogs, native dialogs, when to use wait_for).
  reference/<category>.md   One file per category with full schemas and examples. Read only when needed.
```

- The AI reads `SKILL.md`, picks the category, reads at most one reference file or calls `describe_tool`, then calls `call_tool`.
- `reference/*.md` is generated from the catalog by `build.cmd`. It is never hand-edited. `SKILL.md` is hand-written, and a unit test fails if it names a tool that is not in the catalog or omits one that is.
- Without the skill, `list_tools` and `describe_tool` give the same information. The skill is a faster path, not a requirement.

**Context budget (measured, not assumed).**
- Three meta-tool definitions plus the `instructions` string: at most 1,500 tokens.
- `SKILL.md`: at most 3,000 tokens.
- Counted with a documented approximation (4 characters per token), checked by a unit test.
- A typical session (browse one category, describe 3 tools) must cost less than 25% of what registering all catalog tools directly would cost. The test computes both numbers from the catalog.

**Trade-offs, accepted.**
- One extra round trip per new tool the first time it is used. Mitigated by recipes in `SKILL.md` and by `describe_tool` taking up to 8 names at once.
- Client-side permission rules cannot target one catalog tool by name, because all calls are `call_tool`. Mitigated by R4 (the destructive-action report) and by `call_tool` logging the catalog name in the trace.
- `call_tool` hides the catalog name from client UIs that display tool calls. The trace and the journal record the real name.

## 7. Rules for every tool

- **R1 Structured errors**: fail fast with `{code, message, hints}`. Required codes: `not-found`, `ambiguous-match` (lists candidates), `disabled`, `timeout`, `stale-ref`, `ui-thread-blocked`, `not-connected`, `unknown-tool` (lists closest names), `invalid-args` (includes the tool's schema), `install-not-writable` (names the path), `restart-not-confirmed`.
- **R2 Disposed or hidden widgets** never crash the agent. They produce `stale-ref` or `not-found`.
- **R3 Modal dialogs**: observation tools work while a modal dialog is open. An action that opens a modal dialog returns, it does not block.
- **R4 Destructive actions** (delete, overwrite, close without saving) work like any action, but the response must report the resulting dialog so the caller can decide.
- **R5 Threading**: all widget access runs on the UI thread through one executor. The UI thread is never blocked while waiting. Waits poll or use callbacks off the UI thread.
- **R6 Snapshot ids**: every UI-dependent response carries a snapshot id, and element refs from an older snapshot are rejected as `stale-ref` when the widget no longer matches.

## 8. Non-functional requirements

- **Security**: bind to `127.0.0.1` only. A random per-session token is required on every request. No arbitrary eval tool of any kind.
- **Install safety**: launch and attach modes never write into the target install directory. Install-and-relaunch writes only the `eclipse-driver\` folder and one registration entry (with a backup), only after `confirm_restart: true`. `uninstall_agent` reverts it. The agent stays dormant on any start without the enable property.
- **Observability**: structured logging on both sides. An opt-in trace records every tool call with timing.
- **Packaging**: one command produces the distributable, which is a fat server jar with the agent embedded, plus the `skill/eclipse-driver/` folder.
- **Context cost**: the MCP surface and skill stay inside the budget in 6.11. A unit test enforces it.
- **Docs**: README covers install, the MCP client config snippet, tool reference, limits and troubleshooting. `docs/ARCHITECTURE.md` justifies the transport choice.
- **Scope discipline**: deliver what is specified. No extra features, abstractions or dependencies.

## 9. Code style

Clean separation: protocol DTOs, UI-thread executor, element registry, feature areas (dialogs, workbench, text, jobs, debug, junit), and the MCP adapter. No god classes. No static mutable singletons except the minimal agent bootstrap. Comments only for a non-obvious why.

```java
// Every widget access goes through one executor; callers never touch the Display directly.
public final class UiExecutor {
    private final Display display;

    public <T> T call(Duration timeout, Supplier<T> work) {
        // runs work via display.asyncExec, waits off the UI thread, throws ToolError("timeout") or ("ui-thread-blocked")
    }
}
```

## 10. Testing strategy

- **Unit tests** (plain JDK, no Eclipse needed): matchers, element-ref registry, error mapping, and the protocol and JSON codec. Run with `build.cmd test`.
- **Catalog tests**: every catalog tool has a category, summary, schema and example. `SKILL.md` and the catalog name the same tools. `call_tool` rejects bad args with `invalid-args` and unknown names with `unknown-tool`. The context budget in 6.11 holds.
- **Integration tests** (`it` module) launch a real Eclipse install and run end-to-end flows:
  1. Open File > New > Project wizard and read it.
  2. Navigate Back and Next, then cancel.
  3. Trigger an error dialog and read it.
  4. Read the Console.
  5. Observe a running job.
  6. Attach to an already running instance.
  7. Install-and-relaunch, then `uninstall_agent`. Hash the touched install files before and after, and compare.
- Packaging tests: see the unit test in 2.1.
- Success is shown only by a real tool call or test result. A flow that cannot be tested here is reported as untested.
- Stop and report after 3 failed attempts at the same problem.

## 11. Boundaries

**Always**
- Route widget access through the UI executor.
- Return structured errors.
- Run unit tests and the packaging command after each milestone and report actual output.
- Report, per milestone: files changed, the command run, the real result, known limitations, and anything not verified.

**Ask first**
- Downloading anything large (an Eclipse distribution, for example).
- Adding any dependency beyond the JDK, including an MCP or JSON library or SWTBot.
- Adding tools beyond section 6.
- Running `git init`.

**Never**
- Write into the target install directory, except what `install_agent` does (2.2). Never touch any other install file.
- Add an eval tool or bind to a non-loopback address.
- Delete a file the agent did not create.
- Push or publish anything.

## 12. Milestones and success criteria

Work in milestones. Report with evidence after each. Do not start the next until the owner accepts the report.

| M | Scope | Done when |
|---|---|---|
| M1 | Skeleton, catalog, three meta-tools, launch, connect, `list_shells` | `build.cmd` produces the jars. The server registers exactly three MCP tools. `list_tools`, `describe_tool` and `call_tool` work for the M1 catalog entries. The server launches ISOLAR-AB from its path. In launch mode the agent loads with nothing written to the install, from the extracted embedded jar. A real `call_tool` of `list_shells` returns the main shell. |
| M2 | `read_dialog` and wizard tools | The File > New > Project wizard is opened, read, stepped Back and Next, and cancelled, with tool output as evidence. An error dialog is triggered and read. |
| M3 | Full observe set (6.3, 6.4) | Every observe tool returns real data on ISOLAR-AB, including while a modal dialog is open. |
| M4 | Full act set (6.5 to 6.8, native dialogs 6.9) | Scenarios 1 and 6 of section 1.4 run end to end. |
| M5 | Attach, install-and-relaunch, multi-instance | Attach by pid works, with a clear diagnostic when attach is disabled. Install-and-relaunch works on ISOLAR-AB and `uninstall_agent` restores the touched files byte-identical. Scenarios 2 to 5 run, including attaching to the child instance. |
| M6 | Packaging, skill and docs | One command makes the distributable, including `skill/eclipse-driver/` with generated reference files. The context budget test passes with real numbers. README and ARCHITECTURE.md are complete. Scenario 7 (journal and trace) works. |

Overall success: all seven scenarios in section 1.4 demonstrated against ISOLAR-AB, and a smoke check against at least one other RCP install if one is available.

## 13. Out of scope

- Model-level AUTOSAR APIs.
- Remote or non-loopback access.
- Any eval tool.
- Non-SWT UIs (for example, a JavaFX-only RCP).
- Desktop automation beyond native dialogs of the Eclipse process.
- Maven, publishing, and `git init`.

## 14. Differences from the original draft

| Draft | This spec | Reason |
|---|---|---|
| Multi-module Maven build | JDK-only build, `build.cmd`, PDE project for development | Maven is not installed. The owner's rationale was that a `.target` file is awkward from the command line. |
| About 45 tools, plus "add much more" | 91 tools in a fixed catalog | Agreed in the interview. Adds launch, process, JUnit, debug, journal and native-dialog tools. |
| Use SWTBot where it helps | Direct SWT only | The sample target ships no SWTBot. |
| Single instance | Multiple instances, optional `instance` argument | The JUnit Plug-in Test starts a child instance. |
| Never write into the install directory, no exceptions | Launch and attach keep the rule. A third opt-in mode, install-and-relaunch, may write one folder and one registration entry, reversible. | Owner decision 2026-10-26: the rule does not apply while connecting to an RCP. Gives a reliable path when attach is disabled. |
| Packaging: "fat server jar" only | Fat server jar embeds two jars (agent bundle, attach stub) plus a hash manifest, extracted on first use. See 2.1. | Owner asked for the embedding to be explicit. |
| Native dialogs not mentioned | Basic native dialog handling, Windows only | Browse buttons open OS dialogs. |
| Agent built against the target's bundles | Single PDE plugin developed against the owner's `.target` | Owner's development setup. |
| Every tool registered as an MCP tool | Three MCP meta-tools plus a skill. The AI browses the catalog and picks the tools it needs. | 91 schemas would fill the AI's context before work starts. Added after review on 2026-10-26. |

## 15. Open questions

1. **Target file** (answered 2026-10-26): `A:\sandbox\workspace\Orion_WS\WS\12.14.0_Oct26\Target_12.14\2027.1.target`, resolved locally. Java release pinned to 21.
2. **Launch mechanism on ISOLAR**: the product uses simpleconfigurator, so `-Dosgi.bundles` may not load the agent. The mechanism (extra configuration area, a framework extension, or editing a copied `bundles.info`) must be proven in M1.
3. **Transport**: HTTP or WebSocket. Note that `jdk.httpserver` may be missing from a trimmed JRE, so the agent may need to rely on `java.net` sockets. Decided in `ARCHITECTURE.md`.
4. **Native dialog implementation**: the server must drive Win32 dialogs with no dependency. Options are PowerShell with UI Automation, or an approved library such as JNA. The choice needs owner approval. A Java 17 release pin also rules out the Foreign Function API.
5. **Child instance agent loading**: the JUnit Plug-in Test launch starts the child through PDE, so the child may not load the agent by itself. Attach by pid is the planned path. Confirm that this is acceptable, or say how the launch script can inject the agent.
6. **Launch script**: path and parameters of the owner's test launch script, needed for `run_script`.
7. **JSON and MCP library** (answered 2026-10-26): no library. Hand-write a small MCP stdio protocol and JSON codec.
8. **Server JVM** (answered 2026-10-26): `C:\toolbase\_ldata\java_jdk\.98-21.0.7.6` (Java 21.0.7).
9. **Other RCP installs**: is there a second RCP product available for the smoke check?
10. **Hot tools**: should a few tools (for example `connect`, `read_dialog`, `click`, `wait_for`) also be registered directly as MCP tools, to save the first `call_tool` round trip? Cost is about 300 tokens each. The spec default is no: all catalog tools go through `call_tool`.
11. **Skill install path**: which skills folder does the owner's Claude Code read (user level or project level), so the README and `build.cmd` can copy the skill there?
12. **Relaunch arguments** (decided 2026-10-26): the server reads the launcher `.ini` next to the executable (for ISOLAR-AB: `-vm`, `-vmargs`, and the rest) and relaunches from it, adding the enable property. It does not rely on `ProcessHandle.info()`. Command-line arguments the user passed outside the `.ini` are not recovered. The tool reports this limit in the `connect` result.
13. **Install integrity and rights**: the install folder is writable for the owner (confirmed 2026-10-26). Still open: does ISOLAR's patcher or a license check object to a changed `bundles.info`? Proven in M5.
14. **Agent compile for `build.cmd`**: the `.target` is now known. Proposal: compile against `plugins\*.jar` of the first target location. Confirmed in M1.
15. **Other clients**: must the tool work with MCP clients that have no skill support? The spec assumes yes, through `list_tools` and the `instructions` string, with the skill as an optional accelerator.
