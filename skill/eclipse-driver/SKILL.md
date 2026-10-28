---
name: eclipse-driver
description: Observe and operate a running Eclipse RCP application (e.g. ISOLAR-AB) through the eclipse-driver MCP server. Use for launching, inspecting, clicking, typing, waiting and handling dialogs in Eclipse.
---

# eclipse-driver

The MCP server exposes a few meta-tools (list/describe/call). Call any tool below by name with its JSON args; use describe to see a schema.

## Loop
1. `connect` (mode `launch` with `path`, `attach` with `pid`, or `install` with `confirm_restart: true`).
2. Observe (`read_workbench`, `read_widget_tree`, `find_elements`, `screenshot`).
3. Act with refs/matchers from the observation.
4. `wait_for` the expected condition, then observe again to verify. Use `state_diff` to see what changed.
5. On `ui-thread-blocked`, a native dialog is open: use `list_native_dialogs` and the `native_dialog_*` tools.

## Tools
- session: connect, disconnect, list_instances, get_status, get_logs, set_trace, journal_note, get_journal, install_agent, uninstall_agent
- launch-process: run_script, read_process_output, stop_process, list_launch_configs, launch, terminate_launch
- observe: list_shells, read_dialog, read_widget_tree, find_elements, read_element, read_text, read_tree, read_table, read_workbench, read_menu, read_toolbar, read_status_line, read_clipboard, screenshot, state_diff
- observe-eclipse: read_editor, read_views, read_selection, read_outline, read_properties, read_console, read_progress, read_problems, read_error_log, read_junit, read_debug
- act-pointer-keyboard: click, double_click, right_click_menu, hover, drag_drop, scroll, press_key, key_chord, type_text, set_text, focus, move_resize_shell, maximize_minimize
- act-widgets: select, check, uncheck, expand, collapse
- act-workbench: menu_select, context_menu_select, toolbar_click, open_view, activate_view, close_view, open_perspective, reset_perspective, open_editor, save, save_all, close_editor, run_command, quick_access, open_preferences, set_preference, undo, redo
- act-dialogs-wizards: wizard_next, wizard_back, wizard_finish, wizard_cancel, dialog_confirm, dialog_dismiss
- wait: wait_for (conditions include `process_exit`)
- native-dialogs: list_native_dialogs, native_dialog_set_path, native_dialog_confirm, native_dialog_cancel, native_dialog_press, native_dialog_screenshot

## Recipes
- Open a view: `open_view`, then `read_views`.
- Change a preference: `set_preference`, or `open_preferences` then `set_text`/`check`, then `dialog_confirm`.
- File chooser (Browse): click Browse, `list_native_dialogs`, `native_dialog_set_path`, `native_dialog_confirm`.
- Run a launch config: `list_launch_configs`, `launch`, `wait_for`, `read_console`.
- Run a script: `run_script`, `wait_for` process_exit, `read_process_output`.
- Debug a failure: `read_error_log`, `read_problems`, `get_logs`.

## Rules
- Never guess refs; re-read after any action (stale-ref means re-observe).
- Prefer `wait_for` over fixed sleeps.
- `install_agent`/`connect install` change the install and restart it; ask the user first.
- Only loopback; no code evaluation tool exists.
