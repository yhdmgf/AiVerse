# Plugin Installation and Usage

\> Applies to AiVerse 1.0.0 and above. This page covers how to install and use plugins; to write your own, see the [Plugin Development Guide](../advanced/plugin-dev.md).

A plugin is a zip package that extends AiVerse's capabilities: once installed it can give the AI new tools, new skills, or new providers, add new interface entries in places such as settings, the sidebar, and the chat menu, or bring in new MCP servers. All plugins are managed centrally under "**Settings → Plugins**".

Note

Plugins come from third-party developers, so confirm the source is trustworthy before installing. A plugin runs inside the container as a separate process (Node or Python) and has the ability to read and write files, access the network, and execute commands; only pure-page plugins run in the WebView sandbox. The store audits plugins for security, but the host cannot guarantee the behavior of third-party plugins.

## What Plugins Can Provide

-   **New AI Tools**: Register tools that the AI can call; after installation they appear in the AI's tool list and are invoked just like built-in tools.
-   **Skills and Subagents**: Give the AI standard procedures (skills) or dedicated personas (subagents) for certain kinds of tasks.
-   **New Interface Entries**: Add entries at the end of a settings section, the bottom of the sidebar, in the chat menu, in the file tree long-press menu, and similar places; tapping them opens a page provided by the plugin.
-   **MCP Servers**: Bring external tools in through MCP (see [MCP Servers](./mcp.md)); they start and stop with the plugin.
-   **AI Providers**: Add a provider with a "Plugin" tag under "Settings → AI Providers".
-   **Floating Window, Backup Participation, etc.**: Some plugins also request a floating window (overlay), participate in backup, or call Shizuku and similar capabilities, all of which require separate authorization from you in the plugin details; proxying model requests is a declarative capability and counts toward the corresponding channel's usage.

Note

A plugin that only registers "tools" and has no interface entries will not show any extra buttons in the interface — that is expected, because its capabilities show up directly in the AI's tool list.

## Three Ways to Install

Open "**Settings → Plugins**"; the top has "Installed", "Store", and "In Development" tabs, with a "+" button in the top-right corner.

### Method 1: Install from a zip Package

1.  Get the plugin's zip package (do not extract it).
2.  Go to "Settings → Plugins", tap the "**+**" in the top-right corner, and select the zip from your files.
3.  The host unpacks it, validates the manifest, and installs it; once done, an entry appears in the "Installed" list.

Note

The root of the zip must directly contain the plugin content (including `plugin.json`); do not compress the outer folder along with it. The per-package install limit is 16 MB; anything larger fails to install.

Plugin Package Naming Rules (Required for Store Publishing)

A plugin package submitted to the store must strictly follow this archive file name format:

text

```
authorname.pluginname_version.zip
```

-   **The first segment of the ID must be the author name and must not start with `aiverse`**: the plugin `id` looks like `authorname.pluginname` (for example `yhdmgf.desktop-pet`), and the host enforces that the first segment of the `id` equals the publisher `publisher` (author name). `aiverse.*` is an official reserved namespace; a third-party plugin using it is judged by the store as impersonating official plugins and rejected.
-   **Author name rules**: starts with a lowercase letter and contains only lowercase letters, digits, and hyphens (`^[a-z][a-z0-9-]*$`).
-   **Version**: joined with an underscore `_`, semantic versioning without a `v` prefix (e.g. `yhdmgf.desktop-pet_1.1.0.zip`).
-   File names that do not match this format are rejected during the store's upload/review steps. Local zip import during development is not subject to this naming restriction, but publishing must follow it.

### Method 2: Install from the Plugin Store

1.  Go to "Settings → Plugins" and switch to the "**Store**" tab.
2.  Browse or search for plugins and tap into a detail page to view the introduction, version, author, and official badge.
3.  Tap install; the host downloads and installs it, and it appears in the "Installed" list once done.

The store list comes from the official plugin index and is updated from time to time; if you cannot find a new plugin, **pull to refresh** on the store page.

**Mirror acceleration URL**: the store index and plugin packages are hosted on GitHub. When the direct connection is slow or fails, you can set a mirror acceleration URL (a URL prefix) in the plugin settings. It only applies to GitHub-hosted index and plugin packages; leave it empty to connect directly. The URL must start with `http(s)` and contain no user info; invalid input is ignored.

### Method 3: In-Development Directory (Edit and Try)

This suits writing your own plugin or wanting to edit a plugin's source directly, without repeatedly importing a zip:

1.  Tap the **gear** in the top-right corner of "Settings → Plugins" to open **Developer Mode**.
2.  Once enabled, every subdirectory containing a `plugin.json` under `~/.aiverse/plugin-dev//` appears in the "**In Development**" tab.
3.  Changing files in those directories **automatically repackages and replaces** them, with no need to re-import.
4.  Swipe a project left in the "In Development" list to delete its development folder and source code; the installed plugin is unaffected.

Note

To write a plugin yourself, see the [Plugin Development Guide](../advanced/plugin-dev.md): it covers the package structure, manifest fields, every capability you can declare, and a minimal runnable example.

## Enabling and Disabling

Each plugin card in the "Installed" list has a switch on its right side:

-   **On**: The plugin's capabilities take effect — registered tools, interface entries, MCP servers, and so on are all wired in.
-   **Off**: The plugin's capabilities are withdrawn as a whole; registered tools and entries disappear, but the plugin's files are kept.

If the switch is **grayed out and cannot be toggled**, the plugin's manifest or content is unusable (failed parsing, a digest mismatch, or missing contributions — the plugin status is an error). The preview during install/import points out the specific field problems one by one; for an installed plugin this situation is a bug in the plugin itself and needs to be fixed by the developer.

## Managing Installed Plugins

Tap a plugin card to enter its **detail page**, where you can:

-   View the plugin's name, version, publisher, license, source repository, source, and contributed content.
-   View and separately authorize special capabilities: Shizuku commands, the floating window, and backup read/write can be authorized individually; model invocation is declarative and is not authorized separately.
-   For plugins with a configuration page, the detail page gets extra "Plugin Management / Plugin Configuration" segment tabs at the top; switch to "Plugin Configuration" to enter it.
-   Disable / Uninstall: Uninstalling deletes the plugin's files and cannot be undone.

**Updates**: A plugin is only recognized as an "update" when its **version number changes**; reinstalling with an unchanged version number is treated as a replacement, not an update.

## Troubleshooting Checklist

If you installed a plugin but it is not behaving as expected, check the following in order:

1.  **Is the plugin in the "Installed" list?** If not, the install did not finish — install it again.
2.  **Can you toggle the switch?** If it is gray, the manifest failed to parse and the detail page points out the specific field.
3.  **Is the switch turned on?** While off, the plugin's tools and entries have no effect.
4.  **Can't find the entry in the interface?** A plugin that only registers tools has no interface entry by design — look in the AI's tool list. For a plugin that does have an interface entry, use the slot table in the development docs to confirm where it declared itself.
5.  **Is a plugin missing from the store?** Pull to refresh the store. The store index updates when you enter the store, pull to refresh, or run a startup check; when offline it shows the last cache, and freshly published plugins may take a moment to appear.
6.  **Still not working?** Report the error message from the plugin's detail page to the plugin author.
