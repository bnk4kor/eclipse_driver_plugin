# wait

Generated from the catalog. Do not edit.

## wait_for

Wait for a condition with a timeout; always returns a result or a timeout error.

Schema: `{"type":"object","properties":{"condition":{"type":"string","enum":["shell_opened","shell_closed","text_appears","element_enabled","jobs_idle","ui_idle","console_contains","editor_open","junit_finished","process_exit"]},"title":{"type":"string"},"text":{"type":"string"},"ref":{"type":"string"},"shell":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"console":{"type":"string"},"process":{"type":"string"},"timeout_seconds":{"type":"integer"}},"required":["condition"]}`

Example: `{"condition":"shell_opened","title":"Progress","timeout_seconds":30}`
