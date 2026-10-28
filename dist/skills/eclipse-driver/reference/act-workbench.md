# act-workbench

Generated from the catalog. Do not edit.

## menu_select

Select a menu item by path such as File/New/Project... Returns once a dialog opens or after a short settle time.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"shell":{"type":"string"}},"required":["path"]}`

Example: `{"path":"File/New/Project..."}`

## context_menu_select

Right-click a widget and select an item of its context menu by path.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"path":{"type":"string"}},"required":["path"]}`

Example: `{"ref":"e1","path":"Delete"}`

## toolbar_click

Click a toolbar item by ref, label or tooltip.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"label":{"type":"string"},"tooltip":{"type":"string"},"shell":{"type":"string"}}}`

Example: `{"tooltip":"Save"}`

## open_view

Open a view by id or title and activate it.

Schema: `{"type":"object","properties":{"view":{"type":"string"}},"required":["view"]}`

Example: `{"view":"org.eclipse.ui.console.ConsoleView"}`

## activate_view

Bring an open view to the front by id or title.

Schema: `{"type":"object","properties":{"view":{"type":"string"}},"required":["view"]}`

Example: `{"view":"Problems"}`

## close_view

Close a view by id or title.

Schema: `{"type":"object","properties":{"view":{"type":"string"}},"required":["view"]}`

Example: `{"view":"Problems"}`

## open_perspective

Switch to a perspective by id or label.

Schema: `{"type":"object","properties":{"perspective":{"type":"string"}},"required":["perspective"]}`

Example: `{"perspective":"AUTOSAR"}`

## reset_perspective

Reset the active perspective to its default layout.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## open_editor

Open a workspace file or absolute path in an editor.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"editor_id":{"type":"string"}},"required":["path"]}`

Example: `{"path":"/proj/readme.txt"}`

## save

Save the active editor. Reports the dialog it opens, if any.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## save_all

Save all dirty editors.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## close_editor

Close the active or named editor; reports a save prompt instead of deciding for you.

Schema: `{"type":"object","properties":{"title":{"type":"string"},"all":{"type":"boolean"}}}`

Example: `{"title":"readme.txt"}`

## run_command

Execute an Eclipse command by id.

Schema: `{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}`

Example: `{"command":"org.eclipse.ui.file.save"}`

## quick_access

Search Quick Access (Ctrl+3) and run a result.

Schema: `{"type":"object","properties":{"query":{"type":"string"},"select":{"type":"string"}},"required":["query"]}`

Example: `{"query":"console"}`

## open_preferences

Open the Preferences dialog, optionally on a page.

Schema: `{"type":"object","properties":{"page":{"type":"string"}}}`

Example: `{"page":"General"}`

## set_preference

Set a preference value by node and key; returns the previous value.

Schema: `{"type":"object","properties":{"node":{"type":"string"},"key":{"type":"string"},"value":{"type":"string"}},"required":["node","key","value"]}`

Example: `{"node":"org.eclipse.ui.editors","key":"tabWidth","value":"4"}`

## undo

Undo in the active editor or workbench.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## redo

Redo in the active editor or workbench.

Schema: `{"type":"object","properties":{}}`

Example: `{}`
