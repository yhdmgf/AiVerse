# Chat Interface Tour

This page explains what each button and area of the main interface does.

When you start a new blank conversation, the welcome message appears near the top-center of the page. After you tap the input box at the bottom and the keyboard comes up, the welcome message moves up with the input box, trying to stay between the top title bar and the input box; when the keyboard is dismissed it returns to its original position.

**New conversation entry cards** (**since 1.0.3**): below the welcome message there's a row of clickable quick entry cards; tapping one executes it directly — open the terminal, view Git changes, or fill a frequently used prompt into the input box. Four are preset by default: Open terminal, View Git changes, Write code for me, and Troubleshoot errors. These cards can be added, removed, reordered, enabled/disabled, and given custom icons in "Settings → Appearance → New conversation entries", up to six.

## Top Title Bar

-   **Menu button** (top left): opens the sidebar to manage the session list and browse files.
-   **Session title and model name**: the top line is the current session name, and the line below is the model in use. Tap the title to rename the session via a dialog; tap the model name to open the model switcher sheet to pick a model grouped by provider.
-   **Role pill**: an avatar-plus-role-name pill on the title bar; tapping it opens the role drawer to switch the current session's role. See [Roles](https://aivapp.cn/en/guide/roles.md).
-   **Reasoning effort entry** (always shown, top right): a lightning icon that opens the reasoning-effort sheet to adjust the current model's thinking level. See [Reasoning modes](https://aivapp.cn/en/advanced/reasoning-modes.md).
-   **Context usage ring** (always shown, left of "···"): a mini-gauge ring reflecting how much of the model's available input budget is used; tap it to pop a bubble below the ring with a usage breakdown. See [Context usage and compression](#context-usage-and-compression) and [Token stats](https://aivapp.cn/en/guide/token-stats.md).
-   **More button** (**since 1.0.3**): the "···" (horizontal three dots) on the right of the title bar opens the more actions menu, which now holds the following entries:
    -   **New session**: starts a new conversation. If the current workspace still has an empty session with no messages sent, it switches to and reuses it instead of creating a new one.
    -   **Message navigation** (**since 1.0.0**): opens a floating timeline of the current session's messages, listing only the messages you sent and the AI's final answers; tap one to jump to that message. The former top-level "History" entry has been removed.
    -   **Open Git**: jumps to the Git version management page.
    -   **Open terminal**: jumps to the terminal page.
    -   **Plugin entries**: when plugins with a chat entry are installed, their entries and actions are listed as a group at the bottom of the menu.
-   **Scroll fade-out** (**since 1.0.3**): when you scroll the message list, the top title bar fades out to give up reading space and restores once scrolling stops; the context usage bubble opens and closes with a transition animation.

In remote SSH mode, there's an extra line below the title bar: on the left is the SSH connection status (a dot plus text), and on the right is the current session's cumulative token count (up arrow for input, down arrow for output). This line isn't shown in local mode.

## Sidebar

Tap the menu button at the top left, or swipe right from the left edge of the screen. The top has two tabs, "Sessions" and "Files".

### Sessions Tab

Sessions are arranged from newest to oldest by last reply time, and grouped by time (Today / Yesterday / Within 7 days / Within 30 days / earlier by month). Pinned sessions are placed separately in a "Pinned" group at the very front, with a light-blue card background.

A short press switches sessions, and **a long press pops up an actions menu**:

-   **Pin / Unpin**: once pinned, it's fixed at the very front.
-   **Rename**: change the session title without affecting the ordering.
-   **Export**: export this session.
-   **Batch select**: enter multi-select mode, supporting checking multiple sessions, a one-tap "Select all", and "Batch delete" (with a second confirmation). Tap the × at the top left or press the back key to exit multi-select mode.
-   **Delete**: shows a confirmation dialog; once confirmed, the session and all its messages are deleted.

For sessions that have spawned subagents, the end of the row shows the subagent count and an expand arrow; after expanding you can view its subagents below (**since 1.0.0**), see [Subagents](https://aivapp.cn/md/guide/subagent.md).

Below the sidebar tab bar is **chat history search** (**since 1.0.0**): type a keyword to search message content across all sessions of the current workspace, and the list area shows the hits in place (session title plus the matched snippet, with the keyword highlighted). Tapping a result switches to that session and locates the message; tapping the × on the right of the search box or clearing the content returns to the session list. Search only covers the current workspace and only matches the body text between you and the AI, not tool execution output.

### Files Tab

Browse the files of the current workspace; tapping a file opens the code editing page (**since 1.0.0**), see [File Browsing and Code Editing](https://aivapp.cn/md/guide/files.md).

The bottom of the sidebar has three entries: "Favorites" (**since 1.0.0**), "Browser" (**since 1.0.0**), and "Settings".

## Favorites

Favorites are for saving important message content to review later in one place. Tap "**Favorite**" in a message's actions menu to favorite that content; the "Favorites" entry at the bottom of the sidebar opens the favorites list:

-   The list shows the favorited message content; tap to view details (including the source session).
-   Each one can be **copied** or **deleted**.
-   Favorites are exported with backups, see [Backup and Restore](https://aivapp.cn/md/guide/backup.md).

## Toolbar Below the Input Box

From left to right:

-   **Mode button**: shows the current mode name; tap it to pop the mode menu up and pick from auto, build, plan, and expert. See [The Four Modes](https://aivapp.cn/md/guide/modes.md).
-   **Model button**: shows the current model; tap to pop up a selection dialog. There's a search box at the top for fuzzy filtering, models are grouped by provider, and tapping a group header collapses or expands it (the collapsed state is remembered and kept when you reopen the dialog or restart; matched groups expand automatically while searching). Each row is labeled with capability tags (`Image`, `Tools`, input/output length); tap to switch.
-   **Workspace button**: shows the current workspace name; tap to pop up the workspace management panel (see below).
-   **File button**: upload a file as an attachment that the AI can read the contents of.
-   **Image button**: upload an image as an attachment. Tapping opens the system gallery directly and supports multi-select. It requires the current model to support image input. What's sent to the model is a downscaled copy (long edge at most 1568 pixels), while the original is still saved in the workspace, so the request size and traffic are much smaller. Large photos taken with the camera can also be uploaded directly (original up to 40 MB). Whether a model supports images is judged first by its capability tag, and **since 1.0.3** it also falls back to judging by model name (to cover relays that don't declare the capability but actually support vision); when an image is skipped it shows "Image omitted: the current model is determined not to support image input; you can enable support in the provider settings".
-   **Web search toggle**: a globe icon; a single tap toggles web search, tinting with the brand primary color when enabled; a long press jumps to "Settings → Search providers". This button is off by default and can be enabled in "Settings → Appearance → Input box quick bar". See [Search providers](https://aivapp.cn/md/guide/browser.md).
-   **Upload app button** (**since 1.0.3**): upload an app (APK) already installed on the device as an attachment. Tapping pops up an app selection drawer listing the apps installed on the device, each row showing the icon, app name, package name, and size; there's a search box at the top to filter in real time by app name or package name, showing "No matching apps" when there's no match. After selecting an app, it's exported as an attachment and sent to the AI, going through the same path as file / image attachments, with a preview card shown before sending that can be removed; exporting a larger app shows an "Exporting xxx…" progress indicator. If the app is a split-APK app (with multiple split APKs), it's automatically packed into a single complete `.apks` file before sending, with a note that "This app has splits and has been packed into a .apks full package". A typical use is having the AI analyze the APK of an installed app, for example to troubleshoot issues, inspect permissions, or study an app's composition.
-   **Optimize input button** (**since 1.0.3**): a single tap hands the content already written in the input box to the model to polish and fills it back; tapping does nothing when the input box is empty. Long-pressing enters the "Optimize input dedicated model" setting (following the current chat model by default, see [Default Models](https://aivapp.cn/md/guide/default-models.md)). This button is off by default and can be enabled in "Settings → Appearance → Input box quick bar". Once the polished text is filled back, the button turns into a ↺ restore state in place: each polish stores a version, a single tap steps back to the previous version, and you can keep tapping to go back through earlier versions (down to the text before any polishing); a long press pops up "Restore original / Continue optimizing"; once the message is sent the whole restore history is discarded and the restore state disappears.
-   **Send button**: when the AI is idle it's an ordinary send; while the AI is working, an empty input box shows a stop button, and a non-empty one shows an orange send button (the message gets queued).

Once the content you type grows beyond the input box's height, an **expand button** appears at its top right: tap it to edit this draft in full screen, and tap "Collapse" at the top right or press the system back key to exit full screen. Changes made in full screen sync back to the input box in real time — both sides are the same draft, and after collapsing you can just press send.

Uploaded attachments appear as preview cards above the input box and can be removed before sending. Uploading larger files takes some time, during which an uploading indicator appears above the input box and turns into a preview card automatically once done. Tapping an image thumbnail lets you [view the full image in full screen](#viewing-images-in-full-screen).

Unsent text drafts are persisted locally per session.

When you scroll the message list, the input box area fades to 40% opacity to reduce distraction and restores once scrolling stops.

## AI Question Panel

When the AI needs you to choose an approach, it shows a question panel above the chat input box. Choose a preset option, or choose "Other" and fill in your own answer, then tap "Confirm" to submit; you can also tap "Supplement" to go back to the chat input box and add details.

When filling in "Other", the panel shrinks the visible area as the keyboard comes up. When there are many questions, you can scroll up and down inside the panel to find the input box and the bottom action buttons. The panel title can be tapped to collapse or expand. **Since 1.0.0**, the panel layout is more compact, with narrower option spacing and height to reduce how much it covers the input area.

## Workspace Management

The panel that pops up when you tap the workspace button lets you:

-   **Switch workspaces**: tap to switch. If there's a running AI session or terminal tab, a confirmation dialog pops up first — switching stops them.
-   **Create an internal workspace** (local mode): enter a name to create a standalone folder in the app's private space. In remote SSH mode the entry shows as "New workspace" and the folder is created on the server.
-   **Add an external workspace** (local mode): use an existing directory on the device as a workspace. The first tap shows a risk confirmation dialog (the AI will read and write your real files directly), and you can check "Don't ask again". You can only choose folders from "Internal storage" or "SD card"; other file providers like cloud drives aren't supported.
-   **Delete a workspace**: an internal workspace is deleted along with its files; an external workspace added via "Add external workspace" only has the association removed and **its directory contents are not deleted**.

A workspace is the project root the AI operates in, and the AI's code reads/writes and command execution are confined to the current workspace.

## Slash Commands

Typing `/` in the input box pops up a command menu, with **built-in commands listed first (alphabetically) and skills after them (also alphabetically)**. When there are many commands, you can scroll up and down inside the menu; continuing to type the command name filters the list, and tapping an entry completes it into the input box.

Built-in commands:

-   `/compress`: manually triggers context compression for the current session.
-   `/init`: has the AI analyze the current workspace's codebase and generate or improve the project rules file `AGENTS.md` (improving `CLAUDE.md` when the project root already has `CLAUDE.md` but no `AGENTS.md`). You can pass arguments to specify focus areas, e.g. `/init focus on testing and release process`.
-   `/usage`: view today's and cumulative call counts, token usage, and estimated cost (global statistics, across sessions).
-   `/goal`: set / view / pause / resume / clear the current session's ongoing goal, with the AI autonomously looping toward it until achieved or the budget runs out. See [Session Goals](https://aivapp.cn/md/guide/goal.md).

Skills:

-   Every enabled skill can be triggered directly with `/skillname`, and the AI executes that skill's body as this round's instruction; the menu shows a "Skill" marker to the right of skills.
-   Skills can take arguments, e.g. `/pdf-report a4`: the arguments replace the `$ARGUMENTS` placeholder in the skill's body; when the body has no placeholder, the arguments are appended to the end.

Commands are queued while the AI is busy and execute automatically once it's idle. The command text is shown in the conversation as a user message and enters the context.

## @ File References

Typing `@` in the input box pops up a file search menu; type a keyword to fuzzy-match files of the current workspace (matched by filename and path, case-insensitive). Tapping an entry inserts `@relative/path` into the input box as a reference for the AI — the AI reads the file as needed after receiving it.

-   The file list is generated by ripgrep inside the container and automatically respects `.gitignore`, skipping directories like `.git` and `build`.
-   Each time you open the `@` menu the list is fetched fresh (the first time in a large workspace there may be a brief load), and it's discarded on close, ensuring you get the latest files.
-   Local mode requires the container to be initialized first; if the container isn't ready, the menu says it can't search.
-   Remote mode requires the remote server to have `rg` installed.

## Context Usage and Compression

The mini-gauge ring in the top title bar reflects how much of the model's available input budget is used, not cumulative consumption. Before sending it estimates the usage added by the new input, the reply, and tool results, and calibrates with the actual report after the model returns; the screen-reading description distinguishes estimated usage. After switching models, compressing, or reverting, the old usage baseline is no longer carried over and subsequent requests are recalculated.

As the context approaches the threshold, earlier conversation turns into a handoff summary while the most recent messages are kept. History organization and summary material processing happen in the background, and you can still browse messages during compression. After a successful compression, the divider and summary card appear at the position that triggered this compression, not at the truncation point of the compressed history. When the compression model's window is small it processes in chunks, which may increase waiting time and call cost; if the summary is empty, truncated, or can't free enough space, the original history isn't replaced. An explicit context overflow is automatically retried with compression at most once; if it still fails, please shorten your input, reduce tools, or check the model's window setting.

When the model's window is very small (e.g. a locally deployed 30k context), the system prompt and tool definitions themselves take up most of the window. If that portion plus the most recent messages that must be kept already exceeds the trigger line, compression can't free space either, and in that case it won't compress repeatedly. Please confirm the model's context window is filled in correctly in "Settings → AI Providers", and try to reduce the enabled tools, MCP, and memory entries.

## Message Queue

The input box remains usable while the AI works. Typing content and tapping the orange send button won't interrupt the current task — instead it joins the **pending send queue** (**since 1.0.0**; `/` commands, `@` file references, and the attachment entry are also made available while busy and queuing, and commands and attachments enter the queue together, executing automatically once idle):

-   The queue panel appears above the input box, listing the queued messages' numbers and summaries; tap the title bar to collapse / expand, and it auto-collapses when a dialog or the keyboard comes up.
-   After the current round of task finishes, the next one in the queue is sent automatically, and multiple are executed in order.
-   **Drag to reorder**: press and hold the drag handle (☰) on the left of each row and drag up or down to adjust the execution order of queued messages.
-   **Edit**: tap the pencil icon on a row to modify that message's content; it updates once confirmed.
-   **Insert immediately**: tapping the lightning icon on a row sends that message immediately to the working AI — rather than waiting for the current round to end, it's injected directly into the tool results the AI is currently processing, letting it adjust within that round per the new content. If the AI isn't calling tools in the current round (outputting plain text), it's sent as the next one at the end of the round.
-   Tapping "Stop" skips the current round and immediately executes the next one in the queue.
-   The × to the right of each message removes it from the queue.

The queue only lives in memory

When you switch workspaces or the process is terminated by the system, the queue is cleared, so don't keep important content only in the queue.

## Replies and Tool Cards

The AI generates replies with a **typewriter effect**, displaying character by character, with the speed auto-adjusting to the model's output speed: when the model outputs fast, the display speeds up to match, lagging only slightly behind without falling further and further behind. The thinking preview line and tool execution output keep updating in real time as usual, unaffected by the typewriter; when generation ends, the small remaining portion completes quickly (about a few tenths of a second) rather than the whole block suddenly jumping out.

If you leave midway and come back (switching to the terminal, the settings page, or another session that's also outputting), the part already output is shown up to the current progress without re-typing from the start; new content produced afterward continues to display character by character.

The streaming output stage stays consistent with after it's persisted: the AI reply is likewise rendered with the selected bubble style (styles like Classic white outline and Soft shadow have a bubble background, while **No bubble** mode lays it directly on the page), so the streaming tail won't look disconnected from the bubble after persisting.

**Errors from a failed request** (**since 1.0.3**): they're no longer pinned at the end of the message flow or concentrated in one place on the page. The failure bubble is now **attached below the message that triggered the failure** — scrolling along with the message flow, so you can match which round / which message went wrong. Once the next message succeeds, the old failure bubble doesn't follow along to behind the new message. Only when the failure can't be associated with a specific message (for example, an authentication or connection failure that occurs before any reply is produced) is the failure bubble shown at the end of the session's message flow.

The message area uses a **flat document flow** layout: what you send is a light-colored bubble on the right with the send time noted below; the AI's reply by default has an outline bubble around it (you can change it to No bubble or other styles in "Settings → Appearance"; when set to **No bubble** it's laid directly on the page), and it keeps the same bubble appearance both during character-by-character output and once finally settled.

Every AI reply is wrapped in a **task fold** (**since 1.0.0**), gathering that whole round's process together:

-   The fold header shows this round's status: "Running" while running, turning into "Completed Xs" once done (X is this round's duration); tap it to expand or collapse.
-   While running, the body, thinking, and tool calls are shown in the order they happened, without moving the explanatory text that came before a tool call to after it; only after the task ends is the final answer kept separately below the process.
-   **It auto-collapses when the task completes**, leaving only the final answer; once expanded you can look back over that round's process (thinking, tool calls, the transitions in between).
-   After you manually expand or collapse it, it stays as you chose (switching sessions, scrolling it off screen and coming back doesn't change it); it only auto-collapses on task completion if you haven't manually touched it.
-   When you open older records, chat only loads the most recent portion, and you have to scroll to the top to keep loading earlier ones. If you happen to start from the middle of a round, the topmost snippet of the process is also folded into the fold header (the full round's duration isn't available, so it only shows "Completed"); once you load forward to the start of that round, it merges back into that round's normal fold.
-   After you manually expand the process, it stays expanded while the AI continues calling tools or adding messages. The process content renders by visible range, so you can keep scrolling down to view it; when loading earlier records upward, the expanded process and your current reading position are preserved.
-   If the AI answered in one go this round with no thinking and no tool calls, no fold header is shown, consistent with before.

**Each message you send** has the send time and "Copy / Revert / More" buttons always below it, so you can copy or revert any one at any time. **The AI reply's buttons only attach to the very latest message of the whole session** (within a round of task the AI may take multiple steps with tool calls in between, and the buttons only attach to the last step; and they only appear when the round completed normally — they don't show on an active pause or an error). **Since 1.0.0**, the read-aloud button for intermediate messages isn't shown before the whole round of reply ends; the read-aloud entry only appears on the final answer once the whole round completes. Each round's usage, cache hit rate, and duration are totaled per round and attached below the round's final reply, see [Usage and Duration Below a Reply](#usage-and-duration-below-a-reply).

The AI's thinking process takes its own line, **collapsed by default**; when collapsed it shows the thinking icon, status, and duration; tap the line to expand and view the content, and tap again to collapse. When the body starts outputting or tool calls start being prepared, the thinking status becomes "Thinking done" and the duration stops increasing, not including the subsequent body generation time. The expanded thinking content is shown in segments within a height-limited window, and long content can be scrolled up and down within the window. Your manual expand or collapse choice is kept, and ending thinking and moving to tool calls, scrolling it off screen and back, or switching pages won't reset it; a new segment of thinking still defaults to collapsed.

While waiting for the model to respond, a status line shows at the bottom of the message area: a white highlight sweeps back and forth across the text, followed by three bouncing dots (the dots don't take part in the highlight). When the model is preparing a tool call (the tool name arrives first while arguments are still being transmitted, which may take a few seconds), this line directly says what operation is about to be performed instead of always showing "Thinking" — for example `Editing file`, `Executing command`, `Searching the web`. At that point the thinking content and duration may already have stopped changing, since the thinking phase has ended and tool arguments are being generated; the bottom still shows the corresponding dynamic status hint, so even with thinking or body text still above, there's never a whole wait with no feedback.

Tool calls are folded into a single line as "one consecutive batch":

-   **`N tool calls`**: multiple consecutive tool calls within a round of task are merged into this line, **collapsed by default** — it doesn't auto-expand while running either, and you can tap it open yourself to see details. As long as any tool in this batch is still running, **the text of this line itself** carries a highlight sweeping back and forth. Tapping the line lets you manually expand or collapse it at any time, and **after a manual choice it keeps showing per your choice** — it won't be changed back by automatic rules, nor revert to the default state from scrolling off screen or switching to the terminal or settings page and back.
-   When expanded, each call takes a line and **the whole group is indented one level inward**, so you can tell at a glance these lines belong to the group above rather than being independent content at the same level as the group header. If you already manually expanded a single tool, later consecutive calls forming a group won't hide it; once you manually collapsed a group, it keeps staying collapsed.
-   The tool currently running shows **what it's doing** at the end of the row (visible in the collapsed state too) rather than a generic "Running": `Executing command`, `Editing file`, `Reading file`, `Viewing image`, `Searching the web`, `Operating browser`, `Generating image`, `Organizing todos`, `Starting subagent`, `Asking user`, `Reading/writing memory`; tools whose specific action can't be recognized (MCP tools, etc.) show `Calling tool`.
-   Tap a single line to view the "Instruction" and "Execution result"; larger tool results are organized in the background, and after expanding you may wait a moment before the full details appear. When the content is too long it's viewed by scrolling up and down within a fixed-height window, with a bottom fade indicating there's more below. A single line's expand / collapse is likewise remembered, and scrolling away and back won't auto-collapse it. While a tool is still outputting, scrolling within the window temporarily freezes the content you're viewing to avoid jumping from new output replacing text; after scrolling back to the bottom of the window and stopping, live updates resume. While you browse, the tool keeps executing, and the outer chat doesn't auto-scroll and steal your position. Within the tool and thinking windows, press-dragging to the top or bottom and continuing to drag can carry the outer chat list; after a quick fling and release, the window's inertia stops at the boundary and doesn't carry the outer list.
-   File-changing tools (write file, edit file) are **collapsed by default just like other tools**; tapping to expand shows a **diff card**: the file path at the top left, a "Copy" at the top right to copy the diff, the body being a colorized `+ / −` diff with line numbers, and a summary of this change at the bottom (e.g. `+3 −0 · 1 file`).

If a tool card's bottom shows a "Background task / subagent finished" hint (a green dot means success, a red dot means failure or manual termination), it means a background task or subagent finished during that round and was captured by the current session, and the AI continues handling it automatically.

## Viewing Images in Full Screen

Any image in chat can be tapped to view the full image, consistently in four places:

-   A pending image attachment above the input box;
-   An image attachment inside a message bubble already sent;
-   An image shown in the body of an AI reply (a chart or screenshot the AI generated, or one it read from the workspace);
-   An image the AI sent you with the send-file tool (or image generation).

**Image attachments inside a message bubble** are now shown as an **inline large image card**: a gallery card capped at 4:3, displayed with Fit so vertical images or long screenshots aren't cropped; tap the card to open the full-screen viewer. Below the card, small text keeps the "name · size · container path", and below the image are three action buttons:

-   **Save**: saves the image to the system gallery — on Android 10 and above no storage permission is needed, and it's saved to `Pictures/AiVerse`; on Android below 10 it shows that it's not supported.
-   **Share**: opens the system share sheet.
-   **Insert into chat**: adds the image to the input area as a reference image for the next message.

Once full screen: pinch with two fingers to zoom, drag with one finger after zooming to view details, and double-tap to toggle between the original and the zoomed view. Tap the image, press the back key, or tap the × at the top right to close (tapping the image while zoomed doesn't close it, to avoid mis-taps while viewing details). On a tablet, whether the chat area takes half the screen or the full screen, the full image fills the entire screen.

Once an image file has been moved or deleted it can't be opened and shows "Failed to load image". A GIF only shows its first frame.

## Files Sent by the AI

Files the AI sends with `sendFile` (and the images it generates) appear in the chat area:

-   **Images**: shown as an **inline large image card** — a 4:3-capped gallery card displayed with Fit, so vertical images or long screenshots aren't cropped; tap the card to open the full-screen viewer; below the card small text keeps the "name · size · container path", and below the image are three action buttons (Save / Share / Insert into chat), same as an image attachment inside a message bubble (see [Viewing Images in Full Screen](#viewing-images-in-full-screen)).
-   **Video**: shown as an **inline player** — it plays directly in the message flow, with a full-screen entry, no autoplay by default, a placeholder while loading, and it falls back to a one-line file row on load or playback failure.
-   **Audio**: shown as an **inline voice bar** (unchanged), tap to play directly in the app — the filename shows at the top, with play / pause and a draggable progress bar, and an × at the top right to close. For remote sessions, audio is fetched locally before playing; once a file is moved or deleted it shows "Unable to play this file; it may have been moved or deleted".
-   **Other types**: **one file per line** — a type icon on the left, the filename with "size · path" in the middle, and a `›` hint on the right that's tappable; tap to hand it to the corresponding system app to open (an install package first confirms "Allow installing unknown apps").
-   **Tool cards and other compact places still keep a one-line thumbnail** — for example, an image the AI generated in a tool call or read from the workspace is still shown as a one-line thumbnail near the tool card.
-   **Document-type attachments open a dialog on tap** (**since 1.0.3**): tapping text-type attachments in a session such as Markdown / HTML / plain text pops up a choice panel first, where you can pick "Preview in app" (opening the editor's preview mode) or "Open with another app" to hand it to a system app. When you pick "Preview in app", HTML attachments run their own scripts in the preview page (such as canvas mini-games), so pages that rely on scripts open properly; external links are always blocked and won't navigate away.
-   Once a file has been moved or deleted, tapping it shows "The file doesn't exist or has been moved"; when the location doesn't support sharing to other apps, a corresponding note is given. Files in external workspaces added via "Add external workspace" and directories mounted from custom images can be tapped open as well.
-   Each file is placed at the **location of the tool call** that sent the file, so when reviewing records you can see "at which step the AI sent this file"; file-carrying tools **don't participate in the "N tool calls" fold**, and it shows regardless of whether the group is collapsed.
-   A single call takes at most 10 files, with a default per-file size cap of 100MB (adjustable in "Preferences → Tools"); if even one doesn't exist or exceeds the limit, the whole batch isn't sent, and the AI receives the failure reason and retries after correcting it.

## Usage and Duration Below a Reply

Below the body of an AI reply there's a line of statistics, **calculated as "this round's total" and attached only below each round's final reply** (the buttons are arranged "Copy → Stats → More options", with a `···` actions entry at the end of the line):

-   **Token usage**: `↑` is this round's input tokens and `↓` is output tokens. Within a round of task the model may be called multiple times (every tool call triggers another request), and this is the sum over all requests in this round, not the number for a single step.
-   **Cache hit rate** (**since 1.0.0**): the percentage next to the database icon, the proportion of this round's total input that hit the server-side cache. The higher the hit rate, the cheaper and faster. It isn't shown when the cache isn't hit or the channel doesn't return cache data. This field has been recorded since 1.0.0 and isn't visible on old messages.
-   **This round's total duration** (**since 1.0.0**): the time next to the clock icon, attached only below the final reply of a round of task. It counts from when you pressed send to when the AI finished, including the tool executions in between and the time waiting for you to tap authorization. Under a minute it shows as `12s`, and longer as `2:05` (minutes:seconds) or `1:02:05` (hours:minutes:seconds). It isn't shown while the task is still running and only appears once complete.

The cumulative usage and cache hit rate across the whole session and the whole channel can be seen in [Token Usage Statistics](https://aivapp.cn/md/guide/token-stats.md).
