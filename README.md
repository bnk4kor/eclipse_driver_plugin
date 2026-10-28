# eclipse-driver

**Give your AI assistant hands and eyes inside Eclipse.**

`eclipse-driver` lets AI assistants like **Claude Code**, **GitHub Copilot**, and **VS Code** see, click, type, and navigate inside a live Eclipse RCP application (such as **ISOLAR-AB**). Instead of clicking through deep menus, multi-page wizards, and preference trees yourself, describe what you want in plain English and watch your AI do it.

---

## What You Can Do With It

- **Automate repetitive UI workflows** — *"Open File > New > Project, create an AUTOSAR project, and import my ARXML files."*
- **Navigate complex editors & views** — Inspect trees, tables, properties, editors, and outlines without hunting through perspectives.
- **Diagnose issues instantly** — *"Check the Problems view and Error Log, tell me why RTE code generation failed, and screenshot the error dialog."*
- **Drive native Windows file dialogs** — Seamlessly selects files and folders even when Eclipse opens native OS *Browse...* dialogs.
- **Work alongside your live session** — Launch a fresh, isolated Eclipse session or attach directly to the Eclipse window you already have open.

---

## Requirements

- **Windows** (64-bit)
- **Java 21+** (used to run the driver; your Eclipse application keeps using its own bundled Java)
  - If Java 8 is your default on `PATH`, set `ECLIPSE_DRIVER_JAVA_HOME` (or `JAVA_HOME`) to a Java 21+ folder.
- **An Eclipse RCP application** (e.g., ISOLAR-AB)

---

## Installation

No build step required—everything is ready to run out of the box.

### Option 1: Install as a Plugin (Recommended)

**Claude Code (from GitHub):**
```powershell
claude plugin install eclipse-driver --marketplace https://bosch.ghe.com/bnk4kor/eclipse_driver.git
```

**Claude Code (from a downloaded folder):**
```powershell
claude plugin marketplace add C:\tools\eclipse-driver\dist
claude plugin install eclipse-driver@eclipse-driver
```

**GitHub Copilot CLI:**
```powershell
copilot plugin install C:\tools\eclipse-driver\dist
```

Restart your session after installing, and you're ready to go.

---

### Option 2: Manual MCP Configuration (VS Code, Cursor, Copilot, Claude)

1. Download or clone this repository (for example to `C:\tools\eclipse-driver`).
2. Add `eclipse-driver` to your MCP configuration file:

   **VS Code (`.vscode\mcp.json`):**
   ```json
   {
     "servers": {
       "eclipse-driver": {
         "type": "stdio",
         "command": "C:\\tools\\eclipse-driver\\dist\\eclipse-driver-server.cmd",
         "args": [],
         "env": {
           "ECLIPSE_DRIVER_JAVA_HOME": "C:\\Java\\jdk-21"
         }
       }
     }
   }
   ```

   **Claude Code (`~/.claude.json`) or Copilot CLI (`~/.copilot/mcp-config.json`):**
   ```json
   {
     "mcpServers": {
       "eclipse-driver": {
         "type": "stdio",
         "command": "C:\\tools\\eclipse-driver\\dist\\eclipse-driver-server.cmd",
         "args": [],
         "env": {
           "ECLIPSE_DRIVER_JAVA_HOME": "C:\\Java\\jdk-21"
         }
       }
     }
   }
   ```
   *(The `env` block is only needed if your system `JAVA_HOME` or `PATH` points to an older Java version.)*

---

## Quick Start: Prompts to Try

Once installed, just talk to your assistant:

> **"Launch ISOLAR-AB from `C:\ETAS\ISOLAR-AB` using workspace `C:\ws` and tell me what views and editors are open."**

> **"Attach to my running ISOLAR-AB window, open the Preferences dialog, and find all settings related to code generation."**

> **"Run RTE Code Generation from the menu, wait for it to finish, and summarize any warnings or errors from the console and Problems view."**

> **"Take a screenshot of the current Eclipse window and list the items in the AR Explorer tree."**

---

## How It Connects to Your Eclipse

You are always in control of how the AI interacts with your installation:

- **Launch Mode (Default)** — Starts Eclipse with a separate, private configuration area. **Your Eclipse installation folder is never modified.**
- **Attach Mode** — Connects on the fly to an Eclipse instance you already have running on your desktop. Nothing is written to disk.
- **Install Mode (Optional)** — Registers the driver inside your Eclipse installation so it starts ready to connect every time (with a full backup that can be cleanly uninstalled anytime).

---

## Troubleshooting

- **"Java 1.8 found, but Java 21 or newer is required"**
  Point `ECLIPSE_DRIVER_JAVA_HOME` in your environment variables (or in the `env` section of your `mcp.json`) to a JDK 21+ directory, then restart your AI client.
- **Cannot attach to a running Eclipse window**
  Make sure Eclipse and your terminal/IDE are running under the same Windows user account, or ask the assistant to launch a new Eclipse session instead.
