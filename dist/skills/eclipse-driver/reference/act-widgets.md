# act-widgets

Generated from the catalog. Do not edit.

## select

Select an item in a combo, list, table, tree or tab folder by item text, index or tree path.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"item":{"type":"string"},"index":{"type":"integer"},"path":{"type":"string"}}}`

Example: `{"label":"Perspective","item":"Java"}`

## check

Check a check box, radio button or toggle.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"label":"Enable"}`

## uncheck

Uncheck a check box or toggle.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"}}}`

Example: `{"label":"Enable"}`

## expand

Expand a tree node by ref or tree path.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"path":{"type":"string"}}}`

Example: `{"path":"General/Workspace"}`

## collapse

Collapse a tree node by ref or tree path.

Schema: `{"type":"object","properties":{"ref":{"type":"string"},"shell":{"type":"string"},"text":{"type":"string"},"label":{"type":"string"},"id":{"type":"string"},"class":{"type":"string"},"tooltip":{"type":"string"},"path":{"type":"string"}}}`

Example: `{"path":"General"}`
