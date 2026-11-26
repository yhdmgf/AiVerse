# Session Management

"Settings → Data & Diagnostics → Session Management" lists every chat session across all your workspaces in one place, grouped by the workspace each session belongs to. From here you can search, filter, move, pin, rename, export, and delete sessions without opening each workspace first.

## Opening the Page

Go to **Settings → Data & Diagnostics → Session Management**. The list you see covers every workspace known to the app, not just the one you currently have open.

## Browsing by Workspace

Sessions are bucketed by the workspace they belong to, and each bucket shows the workspace name, how many sessions it holds, and a source tag:

-   **Local** / **External folder** / **Remote·Connected** — workspaces the app currently knows about. The workspace you have open right now is listed first.
-   **Local·Other env** / **External·Offline** / **Remote·Offline** — sessions whose workspace is no longer connected or no longer present (for example a folder you moved, or a remote host you're not connected to). These are **read-only**: you can read and search them, but you can't continue chatting in them until the workspace is available again.

Each bucket header can be tapped to collapse or expand it. Inside a bucket, pinned sessions come first, then the rest ordered by most recently updated.

## Search and Scope Filter

At the top of the page there's a search field and a three-way segmented filter.

The filter narrows which workspaces are shown:

-   **All** — every workspace.
-   **Current env** — only the workspaces on the current environment (the built-in, external, and connected remote workspaces the app can see right now).
-   **Remote·Offline** — only the offline and remote buckets that aren't part of the current environment.

The search field matches session **titles**, workspace **paths**, and the **text inside messages**. When a keyword hits a message body rather than a title, a **Message matches** section appears under the workspace list, showing the session title and a snippet of the surrounding text with the keyword in context. Tapping a match opens that session's detail page, where you can read through its messages to find the one you were looking for.

Search is limited to about 80 message matches and the query is debounced as you type.

## Batch Actions

Long-press a session (or tap **Select** in a session's menu) to enter multi-select mode. In this mode, tapping sessions toggles their checkboxes and a floating bar appears at the bottom:

-   **Select all / clear** — selects every session across all buckets, or clears the selection.
-   **Move** — moves the selected sessions to another workspace. The workspace each session currently lives in is disabled as a target.
-   **Delete** — deletes the selected sessions after a confirmation dialog. Deleting a session clears its messages and sub-sessions and cannot be undone.

Moving sessions to a different **environment** (an offline, remote, or otherwise separate workspace) pops up a second confirmation, because it re-binds the sessions to a different path. Sessions can't be moved into the workspaces of the current online environment, since those are the sessions' running environment and moving them there would break that binding.

Swipe a session row left to delete just that one.

## Single Session Actions

Long-press a session to open its action menu:

-   **View messages** — opens the session's detail page.
-   **Open in chat** — jumps straight into that session in the chat interface.
-   **Move to Workspace** — picks a target workspace (same flow as batch move, including the cross-environment confirmation).
-   **Pin** / **Unpin** — pins the session to the top of its bucket. Pinned sessions also stay on top in the workspace's own chat list.
-   **Rename Session** — sets a new title.
-   **Export Session** — saves the session to a `.tar.gz` file via the system file picker, named `aiverse-session-<title>-<timestamp>.tar.gz`. The export is a plaintext archive with no passphrase.
-   **Select** — enters multi-select mode with this session pre-selected.
-   **Delete Session** — deletes the session after a confirmation dialog. A session that is currently running can't be deleted; the option is greyed out.

## Session Detail Page

Tapping a session opens a detail page with the session's metadata at the top — title, source tag, workspace path, message count, time, model, and a **Running** marker if it's still active — followed by its messages in order.

Each message card shows the role (User / Assistant / Tool) and a timestamp, plus:

-   A **copy** button for the message text.
-   For tool messages, the command that was run in monospace.
-   A collapsible **reasoning** section you can expand or hide.
-   The message body; tool output is shown in monospace and truncated to a limited number of lines.

The row of action pills at the top mirrors the menu — **Open in chat**, **Move to Workspace**, **Rename Session**, **Export Session**, and **Delete** (with a confirmation). For an offline workspace, a note says it's read-only.
