# Task list: eclipse-driver

Details, acceptance criteria and verification per task: `tasks/plan.md`.

## M1: Skeleton, catalog, meta-tools, launch, connect, list_shells
- [x] T1.0 Spike: transport and launch mechanism (S) - own ini + own config area + `osgi.bundles` entry works, install unchanged (see docs/ARCHITECTURE.md)
- [x] T1.1 Protocol core: JSON codec, errors, test runner (M)
- [x] T1.2 Catalog model, validator, first entries (M)
- [x] T1.3 MCP stdio adapter with three meta-tools (M) - 25 unit tests pass; piped JSON-RPC check done
- [x] T1.5 build.cmd and embedded-jar extraction (M) - `build.cmd` packages and tests, 40 pass
- [x] T1.6 Agent RPC endpoint (M) - unit tested and verified live inside ISOLAR-AB (`build.cmd it`)
- [x] T1.7 UiExecutor and `list_shells` (M) - unit tested and verified live inside ISOLAR-AB (`build.cmd it`)
- [x] T1.8 Launcher and `connect` launch mode (M) - 54 unit tests pass
- [x] T1.9 Integration: launch ISOLAR-AB, `list_shells` (S) - `build.cmd it` passed twice; install unchanged (21,314 files)
- [ ] Checkpoint M1: build and tests pass, three MCP tools, real `list_shells`, owner accepts - awaiting owner review

## M2: Dialogs and wizards
- [x] T2.1 Element registry and snapshot ids (M) - 9 new unit tests (63 total pass); real `list_shells` returns `"snapshot":1`
- [x] T2.2 `menu_select` (S) - File/New/Project... opens the wizard; bogus path gives not-found with candidates
- [x] T2.3 `read_dialog` (M) - real New Project wizard read: buttons, inputs, tree, wizard state
- [x] T2.4 Wizard and dialog actions (M) - next/back/finish/cancel, dialog_confirm/dismiss; disabled Next returns `disabled`
- [x] T2.5 Integration: wizard and error dialog (S) - `build.cmd it` passed: open File/New/Project..., read page 1, Next > to Project page, read page 2, Back to page 1, Cancel; trigger modal message dialog via menu_select, read_dialog, dialog_confirm
- [ ] Checkpoint M2: evidence for each flow in `docs/HANDOVER.md`, awaiting owner review

## M3: Observe
- [x] T3.1 Matchers, `read_widget_tree`, `find_elements`, `read_element` (M) - 82 unit tests pass; live IT verifies `read_widget_tree`, `find_elements`, `read_element`, and `ambiguous-match` candidates
- [x] T3.2 `read_text`, `read_tree`, `read_table` (M) - 86 unit tests pass; live IT verifies paged `read_tree` (root and `parent_path`), `read_text`, and `read_table`
- [x] T3.3 `read_workbench`, `read_menu`, `read_toolbar`, `read_status_line`, `read_clipboard` (M) - 87 unit tests pass; live IT verifies all 5 tools against ISOLAR-AB
- [x] T3.4 `screenshot`, `state_diff` (S) - 91 unit tests pass; live IT verifies shell & element PNG `screenshot` and `state_diff` across snapshots
- [x] T3.5 `read_editor`, `read_views`, `read_selection`, `read_outline`, `read_properties` (M) - 93 unit tests pass; live IT verifies all 5 tools against ISOLAR-AB
- [x] T3.6 `read_console`, `read_progress` (S) - live IT verifies `read_console` (`Migrator Console`) and `read_progress` (running/sleeping jobs)
- [x] T3.7 `read_problems`, `read_error_log` (S) - 95 unit tests pass (`LogParserTest`); live IT verifies `read_problems` and `read_error_log` (`.metadata/.log`)
- [x] T3.8 `read_junit` (S) - live IT verifies `read_junit` with optional bundle handling
- [x] T3.9 `read_debug` (S) - live IT verifies `read_debug` (launches, threads, frames, variables, breakpoints)
- [x] T3.10 Integration: observe tools with a modal dialog open (S) - live IT (`build.cmd it`) exercises all 26 observe tools (15 generic + 11 Eclipse-aware) while modal `New Project` wizard is open
- [ ] Checkpoint M3: real data for every observe tool in `docs/HANDOVER.md`, awaiting owner review

## M4: Act and native dialogs
- [x] T4.1 `wait_for` (M)
- [x] T4.2 Pointer actions (M)
- [x] T4.3 Keyboard and text (S)
- [x] T4.4 Widget actions (S)
- [x] T4.5 Window control and drag-drop (S)
- [x] T4.6 Workbench: menus, views, perspectives (M)
- [x] T4.7 Workbench: editors and commands (M)
- [x] T4.8 Preferences (S)
- [x] T4.9 Native dialog detection and screenshot (M, needs Q4 approval)
- [x] T4.10 Native dialog actions (M)
- [x] T4.11 Integration: scenarios 1 and 6 (M)
- [ ] Checkpoint M4: scenarios 1 and 6 evidence, owner accepts

## M5: Attach, install, multi-instance, process and launch
- [x] T5.1 Attach stub, attach mode, `list_instances` (M)
- [x] T5.2 Multi-instance routing and `disconnect` (S)
- [x] T5.3 `install_agent`, `uninstall_agent` (M)
- [x] T5.4 Install-and-relaunch `connect` (M)
- [x] T5.5 `run_script`, `read_process_output`, `stop_process` (M, needs Q6)
- [x] T5.6 `list_launch_configs`, `launch`, `terminate_launch` (M)
- [x] T5.7 Integration: scenarios 2 to 5 (M)
- [ ] Checkpoint M5: scenarios 2 to 5 evidence, byte-identical uninstall, owner accepts

## M6: Packaging, skill, docs
- [x] T6.1 Trace, logs, journal (S)
- [x] T6.2 Catalog completeness (91) and reference generation (M)
- [x] T6.3 `SKILL.md` and consistency test (S)
- [x] T6.4 Context budget test (S)
- [x] T6.5 Final `build.cmd` (M)
- [x] T6.6 README and `ARCHITECTURE.md` (S)
- [x] T6.7 Scenario 7 and smoke check (S)
- [ ] Checkpoint Complete: all seven scenarios, budget numbers, owner accepts

## Evidence and caveats (M4-M6)
- 124 unit tests pass (build.cmd all); live IT (build.cmd it) passes: launch, observe, wizard, wait_for (process_exit/jobs_idle/ui_idle/timeout), open_view/activate_view/read_views, set_preference, list_instances, journal, trace, logs, run_script/read_process_output, list_native_dialogs, list_launch_configs, attach to a running instance, install + relaunch + uninstall (bundles.info sha restored, install tree unchanged apart from Eclipse runtime files under configuration\).
- Verified live on ISOLAR-AB (IT `interactFlow`): set_text, type_text, read_text, expand/select/collapse, hover, scroll, focus, move_resize_shell, click, press_key (Escape closes dialogs), open_preferences, dialog_dismiss, and a real native dialog (Import > File System > Browse...): native_dialog_screenshot, native_dialog_set_path, native_dialog_confirm, native_dialog_press and native_dialog_cancel (the dialog closes each time; that the chosen path lands in the wizard's field was not asserted). Fixed along the way: native detection ran its PowerShell through `-Command -` and managed callbacks, which returned nothing; it now uses a temp `-File` script and compiled C# enumeration, and SWT's own `#32770` shells (children `SWT_*`) are excluded.
- Verified live with testdata `Spec_Manual_Testing\sandbox\AUTOSAR_CAN` (copied to temp by the IT `projectFlow`): Import > Existing Projects into Workspace (set_text on combo by ref, press_key, read_dialog lists the project, wizard_finish), wait_for jobs_idle, read_problems, open_editor (opened the BSW editor on Project_EcucValues.arxml), read_editor (form editor: metadata only, no text content), save (not dirty), close_editor. Not tested: saving a dirty editor.
- Also verified live on the same data: check/uncheck (Import wizard checkbox, state read back), select on the Project Explorer tree, right_click_menu (lists the real context menu; fixed a bug where a missing `path` was treated as an empty path and failed), double_click (opens the file). 
- Implemented but only unit-tested, not exercised live: drag_drop, launch/terminate_launch (no launch configs), scenarios 1 and 6 end-to-end.
- Open questions Q4, Q6, Q9 were resolved by assumption. Owner checkpoints remain unticked until accepted.
- Attach mode reports the real install folder (fixed); workspace is null unless -data is on the command line.
