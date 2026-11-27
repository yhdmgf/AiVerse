# File Browsing and Code Editing

Version Note

The file tree and code editing pages have been available since 1.0.0.

The "Files" tab at the top of the sidebar is used to browse the files of the current workspace and the current container. Tapping a file opens the full-screen code editing page by default (except images and install packages — see "Images and Install Packages" below). Both local workspaces and remote SSH workspaces are supported, and the read/write method is decided automatically according to the current mode.

## Browsing Files

The "Files" page is an in-place expanding, indented file tree with **two default roots**. After you enable "Show in file tree" for an SSH connection under "Connections & Sync", a server root appears as well:

-   **`workspace`**: the current workspace, expanded by default.
-   **Current container**: its name is the currently selected container (such as "Built-in Alpine"), and its icon matches the "Containers and Images" list in settings. When expanded it shows the container's entire file system (`/etc`, `/root`, `/usr`, etc.), collapsed by default. In a local PRoot container you see the files inside the container; under remote SSH you see the files on the remote server.
-   **SSH server (optional)**: turn on the "Show in file tree" switch on an SFTP connection under "Settings → Connections & Sync". It appears as an independent root after the container root, labeled with the connection name; expanding it shows the server's entire `/`. It does not follow the execution mode: even in local mode you can browse and edit remote files over a separate SFTP connection.

Each root can be collapsed and expanded independently, and there is a refresh button at the top right.

At the top of the file tree is a **breadcrumb navigation** (`workspace / subdirectory / …`; tapping a non-final segment folds back to that level and collapses the deeper ones). The "⋯" menu on the right gathers: new file, new folder, import (pick files from the system file picker into the workspace root), search (filter currently visible nodes by file name), show/hide hidden files, edit mode, refresh, and sorting by name / size / modification time.

-   Tapping a directory expands or collapses it in place; tapping a file opens the editing page directly (images and install packages — see "Images and Install Packages" below) — no need to drill down and back level by level.
-   Directories are listed before files, and within each kind they are sorted by name.
-   Files show different icons by type (code, script, configuration, document, image, audio/video, archive, etc.), using the same monochrome line icons as the rest of the App, tinted to follow the theme color.
-   The coloring rules are similar to VS Code: hidden files starting with `.` are shown in a light, muted color, and entries matched by the workspace root's `.gitignore` are shown in orange (only the workspace root's `.gitignore` is read, not those in subdirectories; files in the container are unaffected).
-   **File names have three display modes** (switched under "Settings → Appearance → Files", taking effect immediately): **single line, horizontal scrolling** (default, the historical behavior — names don't wrap or truncate, the whole tree shares one horizontal scroll, and you swipe left/right to see the full name) / **at most two lines** (overly long names wrap automatically onto a second line) / **single line with ellipsis** (overly long names are truncated at the end with `…`). The same group also has switches such as "Show hidden files", "Indent density", and "Muted coloring".
-   Directories you have expanded or collapsed are **remembered per workspace**: closing and reopening the App, or switching away and back to the same workspace, restores the previous expansion state.
-   The container root's directory is only read when expanded; if it can't be read (container not installed, remote not connected), that row is marked red, without affecting the workspace tree. The SSH server root connects on demand too; authentication failures or permission errors only mark that root red. Remote roots have no file watcher, so refresh after editing remote files.

In local mode, the AI file tools support reading and writing files through symbolic links, including links pointing to absolute paths inside the container. Reading or writing follows the link target; deleting, renaming, or moving the link itself leaves the target content in place. When the target doesn't exist, reading reports that the file doesn't exist, and a circular link raises an error. When a project's authorized configuration directory uses such links, the rules can still be read and saved.

When the AI makes a partial modification to a file, it is compatible with the LF line endings common on Linux and the CRLF endings common on Windows, without converting the whole file first. The replacement content follows the line-ending format of the target region (when the target region has no line endings, the file's format is used), and unmodified regions are left as they are. Indentation and other characters still need to match exactly; if the AI says it can't find the original text, have it re-read the corresponding region before editing.

## Accessing from the System File Picker

In an app that supports the system file picker, choose AiVerse to access two directories: `projects` (local projects) and `aiverse` (AI configuration). The top level of AiVerse is only for showing entry points — you cannot create or delete there; after entering a subdirectory you can read and write files according to the granted authorization. Symbolic links pointing outside the shared scope are not shown, and other internal files cannot be accessed through them.

## Images, Audio/Video, and Install Packages

**Images** in the file tree (png / jpg / jpeg / gif / webp / bmp) open an in-app full-screen viewer when tapped, where you can pinch to zoom and double-tap to enlarge, consistent with viewing images in the chat area. Among them, **GIFs and animated WebPs play frame by frame in the viewer**, while static images still display as before; **Motion Photos** taken by the phone show a "Motion Photo" badge in the top-left corner of the viewer. **APK / APKS / XAPK** directly invoke the system installer when tapped (the first time, you are guided to enable "install unknown apps"). **Audio** (mp3 / m4a / aac / wav / ogg / flac / opus / mid / amr / 3gp, etc.) and **video** (mp4 / mkv / webm / avi / mov / ts / flv, etc.) open an **in-app full-screen player** when tapped (**from 1.0.3**): a near-black backdrop, an × at the top right to close, and the file name shown at the top; video fills the screen, and audio is a centered card with a draggable progress bar and play / pause. None of these three kinds enter the code editor; all other files still open the editing page as usual. When a file has been moved or deleted, or is an empty file, the player shows "Cannot play this file; it may have been moved or deleted".

## Opening Files from the Conversation

When the AI references code in the conversation, it writes the path as a clickable link (`file path:line number`, underlined). Tapping such a link opens the editing page directly, and links with line numbers also scroll automatically to that line and try to **keep it near the middle of the screen** (when the target line is near the beginning or end of the file, it may sit at the edge due to the scroll range). Once you scroll after opening, automatic positioning is handed back to you and the view is no longer pulled back to the target line.

Files in the workspace, the AI configuration directory (`~/.aiverse/...`), and container system files (`/etc/...`, `/root/...`) are supported, and they can be opened under a remote SSH workspace as well. Links to ordinary web addresses are unaffected and still open in the system browser; bare domains such as `www.example.com` and `example.cn` are automatically prefixed with `https://` and handed to the browser, while source suffixes such as `README.md` and `script.sh` still open as files.

Tapping a path or directory that doesn't exist, or a file over the size limit, shows the corresponding failure message on the editing page.

## List Refresh

Under a local workspace, file changes **refresh automatically** — not limited to changes made by the AI: commands you run in the terminal, or edits made with other apps, are sensed as well.

It watches the workspace root, the container root (when expanded), and all currently **expanded** directories, without recursing the whole tree, so it won't consume much system resources even in a large repository; once you collapse a directory, it is no longer watched.

There are two other refresh moments: the sidebar re-reads once each time it appears (under a wide-screen persistent sidebar, returning to the chat page from any other page also counts); and the refresh button to the right of the workspace root row can be used to re-read manually at any time.

A remote SSH workspace cannot watch for changes on the remote file system and **can only** refresh at these two moments — if you find the list doesn't match reality, tap the refresh button. In remote mode, reading a directory goes over the network, so there may be a brief loading state, and on failure the specific reason is shown where the list would be.

## Create, Rename, Delete

**Long-press a directory row** (the workspace root, the container root, or any subdirectory) and choose "New file" or "New folder" from the bottom menu; the new entry is placed under that directory. If you long-pressed a collapsed directory, it is expanded first so you can see the result.

The name can only be a single entry name: if it contains `/`, `\`, or is `.` or `..`, the confirm button is disabled — this prevents accidentally creating in the wrong directory. If an entry with the same name already exists, or the directory is not writable, a failure message is shown and the original file is not overwritten.

**Long-press a file or subdirectory row** to rename, send to chat, and delete, or open it with another app (root rows can only create):

-   **Rename**: only changes the name within the same directory; it does not move across directories.
-   **Send to chat**: appends the file as an `@relative/path` reference into the current session's input box and automatically closes the sidebar; no need to enter multi-select mode first. This item is not shown for entries outside the workspace (e.g., local folders).
-   **Delete**: a file is deleted directly; a folder is deleted recursively along with everything inside it, so a confirmation dialog appears first.
-   **Open with another app**: hands the file to a system app (Office documents, images, archives, special formats that rely on external scripts, etc.); this item is not shown for directories. Remote files are downloaded to the device in full before being opened.
-   **Properties**: view detailed information about a file or folder, popping up a bottom drawer styled the same as the long-press menu. The drawer shows: **Type** (for files, the MIME type inferred from the extension, with text-like types shown as `text/plain`; for folders, it shows "Folder"), **Size** (a file shows its size in bytes directly; a folder first shows "Calculating…" and, once the background scan finishes, fills in "N files · size" — large remote directories may be slow, and the result may already be stale by the time you look at it if files have changed), **Location** (the file's full path inside the container), **Modified**, and **Permissions** (`rwx`).

Deletion Cannot Be Undone

Deletion does not go to a recycle bin and is not protected by checkpoints. Think it through before confirming, especially when deleting a folder.

## Copy, Cut, and Paste

Long-press a file or subdirectory and choose "Copy" or "Cut", then long-press the target directory and choose "Paste here". When the target already has an entry with the same name, it first asks whether to overwrite; overwriting replaces the existing entry and its content and cannot be undone.

You cannot overwrite a source entry with a paste in its own directory, nor copy or move a folder into itself or one of its own subdirectories. Such operations are rejected and the source is left unchanged.

## Multi-Select Batch Operations

Choose **Edit mode** from the "⋯" menu at the top of the file tree (or long-press any entry) to enter edit mode, where a checkbox appears in front of each row; tap an entry or its checkbox to select it. "Done" in the menu or the system back key exits.

-   Selecting a folder selects the whole tree together (descendants are all highlighted, inferred through the ancestors); individual entries inside a selected folder can be tapped to deselect, and the folder then shows a half-selected state.
-   A **floating action bar** appears at the bottom (rounded, raised, horizontally scrollable); its left end shows "N selected", common actions are exposed, and **Export and Select all are tucked under "⋯" on the right**. The actions are:

-   **Share**: opens the system share sheet to share the selected content; multiple files or folders are packed into a zip first.
-   **Export**: packs the selected content into a zip and lets you choose where to save it with the system file picker.
-   **Copy**: puts the selected entries into the batch clipboard (pasting does not delete the source).
-   **Move**: puts the selected entries into the batch clipboard and marks them for moving (pasting deletes the source), with a bottom hint "N items cut — tap the target directory to paste".
-   **Paste**: there is no separate button — when there is content on the clipboard, **tapping the target directory row** pastes the whole batch there (pasting does not delete the source).
-   **Delete**: deletes the selected entries in bulk, using the same **second confirmation** dialog as the settings page (deletion cannot be undone).
-   **Deselect**: clears the selection and exits edit mode.

After copying or moving, **tapping the target directory row** pastes the whole batch there; a move-paste deletes the source while a copy keeps it. When the target already has an entry with the same name, that entry is skipped (not overwritten, leaving the source file unchanged), and the remaining entries are handled as usual.

## Code Editing Page

After tapping a file, the sidebar closes and you enter the full-screen editing page.

At the top left is back; at the top right, in order, are undo, redo, save, and settings. Undo and redo are grayed out when there is nothing to act on, and the save button is only tappable when there are unsaved changes.

-   **Undo steps by typing pauses**: continuous fast typing counts as one step, and after a pause of about half a second, typing again counts as a new step. Once you undo all the way back to how it looked when opened, the asterisk before the file name disappears and the save button is grayed out — at that point, leaving won't prompt you to save either.
-   When a Markdown file is open, an extra **preview toggle** button appears to the left of the undo button; tap it to switch to the rendered view and tap again to return to the source. Preview and source share the same editing state, so switching doesn't lose unsaved changes.
-   The preview is a full Markdown render: **HTML embedded in the document (centered headings, images, table layout, badges, etc.) is displayed as usual**, code blocks have syntax highlighting, and the colors follow the app's light/dark theme. Relative-path images in the document (such as `docs/a.png`) are read from the current workspace, and they display under a remote workspace too; network images (badges, online charts, etc.) need a connection to load. Tapping links inside the preview doesn't navigate away, to avoid leaving the current document.
-   The preview doesn't render LaTeX formulas; for documents with formulas, switch back to the source to view them.
-   Native HTML files run their own scripts when previewed (such as canvas mini-games and interactive pages), so pages that rely on scripts to display render properly and stay interactive.
-   Below the navigation bar is a small file-name bar: the file name on the left (with an asterisk in front when there are unsaved changes), and the cursor's row/column position and the file encoding on the right.
-   The **bottom symbol shortcut bar** scrolls horizontally; leftmost are cursor movement left/right and indent (Tab), followed by symbols commonly used in programming (brackets, quotes, operators, `;` `:` `|` `&`, etc.), which are inserted at the cursor when tapped. When the soft keyboard is up, the symbol bar floats above the keyboard.
-   When there are unsaved changes, tapping back or pressing the system back key first shows a confirmation dialog, where you can choose "Don't save" or "Save and exit".
-   **After scrolling to the end of the file you can still swipe up half a screen**: the last few lines aren't flush against the bottom of the screen, making it convenient to edit at the end of a file.

### Syntax Highlighting

The following languages are built in: Kotlin, Java, Python, JavaScript/JSX, TypeScript/TSX, JSON/JSONC, YAML, Shell, Go, Rust, C/C++, CSS, XML, HTML, Markdown, PHP, Lua. `.kts` is highlighted as Kotlin. Unrecognized types are shown as plain text without affecting editing.

The colors use VS Code's default theme (dark Dark+ / light Light+), switching automatically with the app theme; switching the theme while editing doesn't lose unsaved content.

### Editor Settings

Tap the settings button at the top right to enter a separate settings page; changes take effect immediately and are remembered, applied again the next time you open it:

-   **Font size**: adjusted with a slider.
-   **Word wrap**: when enabled, long lines wrap instead of scrolling horizontally.
-   **Show indent guides**: enabled by default.
-   **Show wrap arrows**: when word wrap is on, draws an indicator arrow at each wrap point — disabled by default.
-   **Show whitespace**: visualizes spaces, tabs, and newlines — disabled by default.

### Saving and Concurrency

Saving **overwrites the whole file**, and on success it shows "Saved".

No External Concurrency Detection

The AI tools or the terminal may be changing the same file at the same time, and saving directly overwrites it with the editor's content without telling you the file was changed externally. If you and the AI might edit the same file at the same time, let one side finish before you start, to avoid overwriting each other.

### Limitations

-   **File size limit 2 MB**. Files over the limit are not opened — the content has to be read entirely into memory, and anything larger will lag noticeably or even crash.
-   **Binary files are not supported**. On opening, it sniffs the start of the file; if it's identified as binary (images, executables, archives, etc.), it shows a message directly rather than loading gibberish as text.

## Where Workspace Files Live on the Phone

The "Files" page browses the workspace content, while workspace files and the AI configuration are stored in the app's private directory, which ordinary file managers don't show by default. If you want to open or back them up with an external tool, there are several ways:

**File manager mounting (recommended)**: the App implements a standard DocumentsProvider, so you can use the phone's built-in "Files" manager or a third-party file manager (MT Manager, Solid Explorer, etc.) to find and mount the App's private root directory. Once mounted you can see:

-   `projects/`: workspace projects. External workspaces added via "Add external workspace" are not here; they live in whatever device directory you selected yourself.
-   `aiverse/`: corresponds to `~/.aiverse` inside the container, containing `skills/` (skills), `docs/` (documents), `mcp.json` (MCP configuration), and more.

Mounting steps using MT Manager as an example: open MT Manager → tap the menu at the top left (three horizontal lines) → the three dots at the top right of the sidebar → "Add local storage" → once the system file picker opens, tap the three dots at the top left and choose "AiVerse" → tap "Use this folder" at the bottom.

**Built-in terminal**: switch to the terminal page and `cd ~/.aiverse` to view it directly, using `vi` or `cat` to view and edit.

**Let the AI handle it**: the AI runs inside the container, so you can say "print out `~/.aiverse/mcp.json` so I can look at it" or "write a new skill under `~/.aiverse/skills/`".

**Rooted devices**: go directly into `/data/data/com.aiverse/files/` (on some systems the equivalent path is `/data/user/0/com.aiverse/files/`). There, `projects/` is the workspace root, `aiverse/` corresponds to `~/.aiverse`, and `rootfs/` is the container system. Note that the package name of a debug build is `com.aiverse.debug`, and the two private directories are fully isolated.
