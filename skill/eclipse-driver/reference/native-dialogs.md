# native-dialogs

Generated from the catalog. Do not edit.

## list_native_dialogs

Native OS dialogs of the target process: title, owner pid, path field, buttons.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## native_dialog_set_path

Type a path into the file field of a native dialog.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"title":{"type":"string"}},"required":["path"]}`

Example: `{"path":"C:\\temp\\a.arxml"}`

## native_dialog_confirm

Press Open, Save or OK in a native dialog.

Schema: `{"type":"object","properties":{"title":{"type":"string"}}}`

Example: `{}`

## native_dialog_cancel

Cancel a native dialog.

Schema: `{"type":"object","properties":{"title":{"type":"string"}}}`

Example: `{}`

## native_dialog_press

Press a named button in a native dialog.

Schema: `{"type":"object","properties":{"button":{"type":"string"},"title":{"type":"string"}},"required":["button"]}`

Example: `{"button":"Yes"}`

## native_dialog_screenshot

Screenshot of a native dialog to a PNG file.

Schema: `{"type":"object","properties":{"title":{"type":"string"},"path":{"type":"string"}}}`

Example: `{}`
