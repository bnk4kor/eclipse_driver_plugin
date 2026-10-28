# observe-eclipse

Generated from the catalog. Do not edit.

## read_editor

Read active or named editor: path, dirty flag, cursor, selection, form-editor pages and paged content.

Schema: `{"type":"object","properties":{"title":{"type":"string"},"id":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{}`

## read_views

Read open views in the active workbench page with id, title, active flag and control ref.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## read_selection

Read the current selection in the active workbench part or a named view.

Schema: `{"type":"object","properties":{"part_id":{"type":"string"}}}`

Example: `{}`

## read_outline

Read the Outline view tree items (paged).

Schema: `{"type":"object","properties":{"parent_path":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"},"depth":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_properties

Read the Properties view entries and tabbed property sections (paged).

Schema: `{"type":"object","properties":{"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_console

Read Console view output with console switcher and paged lines.

Schema: `{"type":"object","properties":{"console":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"offset":0,"limit":100}`

## read_progress

Read running Eclipse jobs, progress monitors and Progress view entries.

Schema: `{"type":"object","properties":{"include_system":{"type":"boolean"}}}`

Example: `{}`

## read_problems

Read workspace problem markers and Problems view entries with severity, message and resource (paged).

Schema: `{"type":"object","properties":{"severity":{"type":"string","enum":["error","warning","info","all"]},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_error_log

Read Eclipse Error Log / workspace .log entries with severity, plugin, message and stack trace (paged).

Schema: `{"type":"object","properties":{"severity":{"type":"string","enum":["error","warning","info","all"]},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_junit

Read JUnit test runs, counts, failures and stack traces.

Schema: `{"type":"object","properties":{"limit":{"type":"integer"}}}`

Example: `{}`

## read_debug

Read active debug launches, threads, stack frames, variables and breakpoints.

Schema: `{"type":"object","properties":{"launch":{"type":"string"},"frame_limit":{"type":"integer"}}}`

Example: `{}`
