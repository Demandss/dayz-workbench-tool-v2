<h1 align="center">DayZ Workbench Tool</h1>

<p align="center">
A Windows desktop tool for debugging DayZ mods: it sets up and starts the game client, a local server and Workbench with the mods you pick, and it comes with an editor for DayZ <code>.layout</code> files.
</p>

<p align="center">
<img src="https://img.shields.io/badge/platform-Windows-blue.svg" alt="Platform: Windows">
<img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="License: GPL-3.0">
</p>

> This is an independent community project. It is not affiliated with, endorsed by or supported by Bohemia Interactive. DayZ is a trademark of Bohemia Interactive.

## What it does

**Main program**

- Finds DayZ and DayZ Tools in your Steam libraries and keeps the paths of the game, the work drive and Workbench.
- Keeps two lists of mods, client and server, with the Steam Workshop mods and your local ones. Enabled unpacked sources get junctions in the game folder for file patching and hot script recompilation; these game links are removed when the program exits.
- Prepares a hidden `P:\.Workbench` folder for Workbench. It contains persistent junctions to enabled unpacked sources and a `mod.cpp` named `WorkDir`; packed mods are passed separately by their full paths.
- Starts the client, a sandbox client, the server and Workbench with the launch arguments you set, and stops the processes you put on the auto kill list.
- Opens the layout editor in a second window.
- Interface in English and Russian.

**Layout editor**

- Opens and saves DayZ `.layout` files, also from the game's own archives (read only).
- Canvas with zoom and pan, widget tree, palette, properties panel, undo and redo, and a file watcher that notices changes from outside.
- Editors for `.imageset` and `.styles` files and a viewer for textures.
- Generates script templates for a layout.
- A local [MCP](https://modelcontextprotocol.io) server, so that an AI assistant can read and change the open layout and look at it. See [MCP](#mcp).

## Requirements

- Windows 10 or 11. The program uses PowerShell for its file dialogs, symbolic links for mods and the Windows registry to find Steam.
- DayZ and DayZ Tools from Steam, and a work drive (`P:` by default).
- Nothing else: the installer and the portable archive carry their own Java runtime.

## Install

Take the latest build from the [Releases](../../releases) page:

- `.msi` is an installer (per user).
- `DayzWorkbenchTool.zip` is a portable archive: unpack it anywhere and start the program from there.

## First launch

On the first start the program asks for the paths of the game, the work drive and Workbench, and tries to detect them itself. After that, run **Tools → fixscripts**.

To test a mod on a local server you also need the DayZ Server from Steam (it is a separate download). Copy its `mpmissions` folder and the `serverDZ.cfg` file into your game folder.

Workbench always uses `P:\.Workbench`, independently of the selected work drive. A mod with an immediate `Addons\*.pbo` file is treated as packed; other enabled mod folders are linked into WorkDir under their folder names. Sources do not need their own `mod.cpp`, and an existing WorkDir `mod.cpp` is preserved. The whole `P:` drive is not mounted in Workbench. Workbench starts with its executable directory as the working directory.

The WorkDir folder is managed by the application: do not place files or links there manually. Preparation errors, unavailable paths and conflicting source folder names stop Workbench from launching and show the affected paths. Only links recorded as created by the application are removed or replaced.

## MCP

The layout editor can start a small MCP server for an assistant. It is off until you switch it on in the editor's MCP window.

- It listens on `127.0.0.1` only, so nothing outside your computer can reach it.
- Every request has to carry a bearer token that is made on first use and kept in the editor's settings. The window has a button to make a new one.
- An assistant can change files, so connect only assistants you trust.

The window can copy a ready prompt with the address and the connection command.

## Build from source

You need JDK 25. Gradle downloads the rest.

```
./gradlew :app:run                  # the main program
./gradlew :layout-editor:run        # the layout editor on its own
./gradlew ktlintCheckAll            # style check, also run by CI
./gradlew :core:test :app:test :layout-editor:test
./gradlew :app:packagePortable :app:packageReleaseMsi
```

The project has three modules: `core` (settings, `.layout` parser and serializer, shared UI), `app` (the main program) and `layout-editor`.

## Contributing

Bug reports and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). To report a security problem, read [SECURITY.md](SECURITY.md) first.

## License

[GNU General Public License v3.0](LICENSE). You may use, change and share the program. If you distribute it or a changed version, you have to give the source code to the people who get it, under the same license.
