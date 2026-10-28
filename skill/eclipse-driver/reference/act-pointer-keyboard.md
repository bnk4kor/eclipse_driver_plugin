# act-pointer-keyboard

Generated from the catalog. Do not edit.

## click

Click a widget by ref or matcher. Returns once any dialog it opens appears.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"button":{"type":"string","enum":["left","middle","right"]},"modifiers":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"}}}`

Example: `{"label":"Browse..."}`

## double_click

Double-click a widget, tree item or table row.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"ref":"e1"}`

## right_click_menu

Right-click a widget and read the context menu items it shows (menu is left open unless path is given).

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"path":{"type":"string"}}}`

Example: `{"ref":"e1"}`

## hover

Move the pointer over a widget so tooltips appear.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"ref":"e1"}`

## drag_drop

Drag one widget or item onto another widget or item.

Schema: `{"type":"object","properties":{"from_ref":{"type":"string"},"to_ref":{"type":"string"}},"required":["from_ref","to_ref"]}`

Example: `{"from_ref":"e1","to_ref":"e2"}`

## scroll

Scroll a widget by lines.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"direction":{"type":"string","enum":["up","down","left","right"]},"amount":{"type":"integer"}}}`

Example: `{"ref":"e1","direction":"down","amount":3}`

## press_key

Send one real key event (such as Enter or F5) with optional modifiers to a widget or the focus.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"key":{"type":"string"},"modifiers":{"type":"string"}},"required":["key"]}`

Example: `{"key":"Enter"}`

## key_chord

Send a key chord such as Ctrl+Shift+S.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"chord":{"type":"string"}},"required":["chord"]}`

Example: `{"chord":"Ctrl+S"}`

## type_text

Type text character by character into the focused widget or a target.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"value":{"type":"string"}},"required":["value"]}`

Example: `{"value":"hello"}`

## set_text

Set the value of a Text, Combo or StyledText directly.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"value":{"type":"string"}},"required":["value"]}`

Example: `{"label":"Name","value":"demo"}`

## focus

Give keyboard focus to a widget or bring a shell to the front.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"ref":"e1"}`

## move_resize_shell

Move and resize a shell.

Schema: `{"type":"object","properties":{"shell":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"},"width":{"type":"integer"},"height":{"type":"integer"}}}`

Example: `{"width":900,"height":700}`

## maximize_minimize

Maximize, minimize or restore a shell.

Schema: `{"type":"object","properties":{"state":{"type":"string","enum":["maximize","minimize","restore"]},"shell":{"type":"string"}},"required":["state"]}`

Example: `{"state":"maximize"}`
