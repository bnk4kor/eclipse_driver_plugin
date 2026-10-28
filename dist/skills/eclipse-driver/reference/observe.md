# observe

Generated from the catalog. Do not edit.

## list_shells

All open shells and dialogs with title, modality, active flag and bounds.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## read_dialog

Read a dialog or wizard: message, labels, buttons, inputs, tables, trees, banners and wizard page state. Default: the modal or active shell.

Schema: `{"type":"object","properties":{"shell":{"type":"string"}}}`

Example: `{"shell":"New Project"}`

## read_widget_tree

Bounded-depth widget tree of a shell or subtree root with stable element refs.

Schema: `{"type":"object","properties":{"shell":{"type":"string"},"root":{"type":"string"},"depth":{"type":"integer"}}}`

Example: `{"depth":4}`

## find_elements

Find widgets in a shell matching text, label, id, class or tooltip (case-insensitive substring); returns element refs.

Schema: `{"type":"object","properties":{"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"class":"Button"}`

## read_element

Read one widget's full state (text, label, enabled, visible, focus, bounds, tooltip, selection) by ref or matcher.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"ref":"e1"}`

## read_text

Read paged text lines of a Text, StyledText, active editor or console.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"source":{"type":"string","enum":["widget","editor","console"]},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"source":"editor","offset":0,"limit":100}`

## read_tree

Read a paged slice of a Tree or TreeItem without expanding collapsed lazy nodes.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"parent_path":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"tooltip":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"},"depth":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_table

Read columns and a paged slice of rows from a Table.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"tooltip":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}}}`

Example: `{"offset":0,"limit":50}`

## read_workbench

Read active workbench window, perspective, open editors with dirty flag, toolbar and menu items, and status line.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## read_menu

Read the menu bar structure or a submenu by path without clicking items.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"shell":{"type":"string"},"depth":{"type":"integer"}}}`

Example: `{"path":"File/New","depth":2}`

## read_toolbar

Read toolbar items (ref, label, tooltip, style, enabled, checked) in a shell.

Schema: `{"type":"object","properties":{"shell":{"type":"string"},"ref":{"type":"string"}}}`

Example: `{}`

## read_status_line

Read status line text and visible labels at the bottom of a workbench shell.

Schema: `{"type":"object","properties":{"shell":{"type":"string"}}}`

Example: `{}`

## read_clipboard

Read current plain-text content of the system clipboard.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## screenshot

Capture a PNG screenshot of a shell, an element or a screen region.

Schema: `{"type":"object","properties":{"path":{"type":"string"},"ref":{"type":"string"},"shell":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"},"width":{"type":"integer"},"height":{"type":"integer"}}}`

Example: `{}`

## state_diff

Return what changed since a given snapshot id.

Schema: `{"type":"object","properties":{"since":{"type":"integer"}},"required":["since"]}`

Example: `{"since":1}`
