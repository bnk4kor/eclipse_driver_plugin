# session

Generated from the catalog. Do not edit.

## connect

Launch, attach to or install the agent into an Eclipse instance; returns an instance id.

Schema: `{"type":"object","properties":{"mode":{"type":"string","enum":["launch","attach","install"]},"path":{"type":"string"},"pid":{"type":"integer"},"workspace":{"type":"string"},"timeout_seconds":{"type":"integer"},"confirm_restart":{"type":"boolean"}},"required":["mode"]}`

Example: `{"mode":"launch","path":"A:\\tools\\ISOLAR-AB"}`

## disconnect

Detach from an instance; stops it only if the driver launched it and stop is true.

Schema: `{"type":"object","properties":{"stop":{"type":"boolean"}}}`

Example: `{"stop":false}`

## list_instances

Connected instances and running Eclipse JVMs: pid, product, launch name, agent loaded.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## get_status

Instance state, agent version, UI-thread responsiveness and modal state.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## get_logs

Server and agent log lines, filtered by level and age.

Schema: `{"type":"object","properties":{"level":{"type":"string","enum":["error","warn","info","debug"]},"since_seconds":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"level":"warn","limit":50}`

## set_trace

Turn the opt-in call trace (every call with timing) on or off; optionally set the trace file.

Schema: `{"type":"object","properties":{"enabled":{"type":"boolean"},"path":{"type":"string"}},"required":["enabled"]}`

Example: `{"enabled":true}`

## journal_note

Record a tester finding, feedback or step in the session journal.

Schema: `{"type":"object","properties":{"note":{"type":"string"},"kind":{"type":"string","enum":["finding","feedback","step","bug"]}},"required":["note"]}`

Example: `{"note":"Finish stays disabled","kind":"bug"}`

## get_journal

Read the session journal notes.

Schema: `{"type":"object","properties":{"limit":{"type":"integer"}}}`

Example: `{}`

## install_agent

Place the agent in an install directory and register it (backup kept). Needs confirm true.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"confirm":{"type":"boolean"}},"required":["path"]}`

Example: `{"path":"A:\\tools\\ISOLAR-AB","confirm":true}`

## uninstall_agent

Remove the agent from an install directory and restore the original files from backup.

Schema: `{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}`

Example: `{"path":"A:\\tools\\ISOLAR-AB"}`
