# Architecture notes: eclipse-driver

Draft from spike T1.0 (2026-10-26). Completed in T6.6.

## Launch mechanism (proven on ISOLAR-AB 12.13.0RC1)

Nothing is written into the install. The server builds these files in its own work folder and starts the install's launcher executable:

1. **Own launcher ini.** Copy `<install>\ISOLAR-AB.ini`, insert `-configuration <workdir>\cfg` before `-vm`, and add the system properties `-Declipse.driver.token=<token>` and `-Declipse.driver.ready=<file>` to the `-vmargs`. Start with `ISOLAR-AB.exe --launcher.ini <own ini>`, working directory = install folder. Relative paths in the ini (`plugins/...`, `./javalibs/...`) resolve against the install, so they keep working.
2. **Own configuration area.** Copy `configuration\config.ini` and the folder `configuration\org.eclipse.equinox.simpleconfigurator` (which holds `bundles.info`) into `<workdir>\cfg`. Relative bundle paths in `bundles.info` still resolve against the install area.
3. **Agent registration.** Append one entry to the `osgi.bundles` line of the copied `config.ini`: `reference\:file\:<absolute path to agent-bundle.jar>@4\:start`. The agent jar stays in the extracted folder under `%LOCALAPPDATA%\eclipse-driver\<version>\`.

Evidence (throwaway spike bundle): agent `start` ran on thread `Start Level: Equinox Container`, `osgi.configuration.area` pointed at the work folder, the install file list (path, size, timestamp for 21,314 files) was identical before and after launch, and the loopback socket accepted a connection.

Known effects:
- First start with a fresh configuration area took about 33 s until the agent started. A reused work folder will be faster. The server should keep one folder per install and refresh `config.ini` on every launch.
- The install's default workspace (`C:\ETASData\ISOLAR-AB\workspace`) is shared with normal use. `connect` should take an optional `workspace` argument (`-data`) so the AI can use its own.
- `ISOLAR-AB.exe` stays alive as a stub next to the real `javaw` process, so the server must take the JVM pid from the agent's ready file, not from the process it started.

## Transport (decision)

HTTP/1.1 with JSON bodies (`POST /rpc`) over a plain `java.net.ServerSocket` bound to `127.0.0.1`, hand-written and about 100 lines.

Why:
- No dependency and no need for `jdk.httpserver`, which a trimmed runtime may lack and which OSGi may not expose to the bundle.
- Request and response are plain text, so a problem can be checked with `curl`.
- WebSocket gives push events, but the server polls (`wait_for` runs on the agent side and answers when done), so push is not needed.

Handshake: the server picks a random token and a ready-file path and passes both as system properties. The agent stays dormant unless the token property is set. When active, it binds port 0 on `127.0.0.1`, then writes `{pid, port, version}` to the ready file. Every request carries the token in a header, and a wrong token is rejected.

## Final structure (M4-M6)
- Catalog (protocol) is the single source of the 91 tools; the server handles session, process, install, attach, trace, journal and native-dialog tools itself and forwards the rest over loopback JSON-RPC with a token to the agent.
- Agent routes in AgentHandler; UI work runs on the SWT thread through UiExecutor; wait_for polls off the UI thread. Pointer actions fire widget state plus Selection events (clicks that open modal dialogs return via asyncExec); key and drag use Display.post.
- Attach: AttachStub (Agent-Class, Java 17 release) installs the agent bundle through the OSGi system BundleContext. Install: AgentInstaller adds eclipse-driver\agent-bundle.jar and one bundles.info line, with a backup restored by uninstall. The bundle is not lazy so it autostarts.
- Native dialogs: ui-thread-blocked errors are augmented with #32770 windows of the target pid (PowerShell/Win32).
- build.cmd also generates skill\eclipse-driver\reference from the catalog (tools\GenRef.java).
