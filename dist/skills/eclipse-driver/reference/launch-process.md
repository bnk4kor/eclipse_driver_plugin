# launch-process

Generated from the catalog. Do not edit.

## run_script

Start a script or executable with arguments; returns a process id. Waits for exit when wait is true.

Schema: `{"type":"object","properties":{"script":{"type":"string"},"args":{"type":"array"},"cwd":{"type":"string"},"wait":{"type":"boolean"},"timeout_seconds":{"type":"integer"}},"required":["script"]}`

Example: `{"script":"test.cmd","wait":true}`

## read_process_output

Read buffered output lines of a process the driver started, from a line index.

Schema: `{"type":"object","properties":{"process":{"type":"string"},"since":{"type":"integer"},"limit":{"type":"integer"}},"required":["process"]}`

Example: `{"process":"p1","since":0}`

## stop_process

Stop a process the driver started.

Schema: `{"type":"object","properties":{"process":{"type":"string"}},"required":["process"]}`

Example: `{"process":"p1"}`

## list_launch_configs

Launch configurations known to the instance with name and type.

Schema: `{"type":"object","properties":{}}`

Example: `{}`

## launch

Run or debug a launch configuration by name.

Schema: `{"type":"object","properties":{"name":{"type":"string"},"mode":{"type":"string","enum":["run","debug"]}},"required":["name"]}`

Example: `{"name":"MyTests","mode":"run"}`

## terminate_launch

Terminate a running launch by configuration name (all running launches if omitted).

Schema: `{"type":"object","properties":{"name":{"type":"string"}}}`

Example: `{}`
