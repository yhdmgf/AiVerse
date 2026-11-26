# Subagents

Version note

Subagents are available from 1.0.0 onward.

A subagent is an independent working session spawned from the main session. When you face complex tasks such as lengthy research, parallel multi-module review, or architecture comparison, you can have the AI dispatch subagents to handle them in parallel, **without blocking the current main conversation**.

A subagent has its own independent context and tool capabilities. Once dispatched, it works independently in the background; you can stay in the main session and keep discussing, and at any time click into the sub-session to check real-time progress, ask follow-up questions, or pause it manually.

When suitable subagents exist and part of the work can be done independently, the AI proactively distributes that portion of the tasks while continuing to push the main line forward, taking charge of coordination, verification, and integrating results. Simple or tightly coupled tasks are completed directly by the main session — there is no need to delegate all work to subagents.

Subagents come in two kinds:

-   **Default general subagent**: automatically inherits the parent session's model configuration and all available tools;
-   **Custom subagent**: supports freely specifying the model, a tool whitelist/blacklist, and a dedicated prompt.

## How to Use

In the main session, simply state your dispatch request to the AI, for example:

-   "Dispatch a subagent to review `app/build.gradle.kts` and list the potential issues"
-   "Dispatch two subagents at the same time — one to research database migration options, one to research UI component library choices"
-   "Dispatch a read-only research subagent to map out the current login and authentication flow"

Before dispatching, the AI pops up a confirmation to ask for your authorization; once you agree, the subagent starts in the background and the main session immediately becomes idle again, ready to take your other instructions.

## Placement and Management in the Sidebar

The session list in the sidebar clearly shows the parent-child relationship between the main session and its subagents:

-   **Expand and collapse**: a session that has spawned subagents shows the subagent count and an expand/collapse arrow at the end of the row. Tap the arrow to expand and view all its subagents;
-   **Status indicator**: a running subagent shows a green running status light;
-   **Independent interaction**: tapping a subagent switches you into its session page, which looks exactly like a regular session and supports independent conversation, pausing, or regeneration;
-   **Session actions**: long-press a subagent item to rename, export, or delete it; deleting the main session also cleans up all subagents under it.

## Context and Information Passing

Independent Context with Two-Way Sync

A subagent has an independent context and by default obtains the main session's task background through the dispatch brief, keeping it lightweight and focused:

-   **Task dispatch**: when dispatching a subagent, the AI distills a clear goal and completion criteria, key file paths, verified background, user constraints, the modifiable scope, and the expected output, packaging them as an initial instruction handed to the subagent. If you manually instruct a dispatch in the main session, it is also recommended that you clearly state the key background and expected output;
-   **Result summary**: the subagent's final report should fully state its conclusions, evidence, changed files, verification results, and unfinished items, so the main session can understand the result from the final report alone. After a subagent finishes, the main session automatically receives a completion notification that **directly includes a result summary and the files changed in this run**; the main session summarizes for you based on that summary, and only reads the subagent's full output again when you need the complete report or want to verify details. If the main session was calling other tools at the time, a hint below the card indicates the subagent is ready and it automatically follows up after the current operation;
-   **Full-process traceability**: the main session usually only reads the subagent's final conclusions. If you want to view the subagent's complete tool-call log, reasoning trail, or intermediate output, just click into that sub-session from the sidebar to see all the details.
-   **Dedicated cards**: when dispatching subagents, the chat stream shows a structured task card styled like web search results rather than an ordinary tool record — the top overview labels the total number of "subagent tasks" and the "dispatched" status, and below it each dispatched subagent is listed in order (01, 02...) with its role, model tier (or inherited), initial task description, and fallback hint, laid out neatly for both single-task and batch multi-task dispatch. Tapping any task card takes you straight into the corresponding sub-session; the execution details and completion summary are carried by the subsequent main-session notifications and replies, keeping the dispatch cards from becoming cluttered and bloated.

## Two-Way Communication with the Main Session

Subagents and the main session support two-way asynchronous communication without blocking each other:

-   **Main session → subagent**: within the authorized task scope, the main session can append messages to a subagent (supplementary instructions, adjusted division of labor, course correction, or follow-up questions on existing results), and **can send repeatedly**. A running subagent receives the message in its next turn; a finished or paused subagent is re-awakened and continues working;
-   **Subagent → main session**: while running, a subagent can proactively report key progress to the main session, ask for help, or submit matters that need coordination or a decision. The main session receives a system notification and can reply directly.

These messages appear in the UI as lightweight hint bars rather than occupying ordinary chat bubbles; when the main session is busy, the message is automatically handled after the current operation ends, and when idle it triggers a new turn immediately.

## Automatic Dispatch and Manual Specification

The main session AI knows all currently available subagents and their areas of expertise:

-   **Automatic matching**: when your request matches a subagent's purpose description (`description`), the AI proactively picks that dedicated subagent;
-   **Manual specification**: you can also specify explicitly in your instruction, for example "Use the Explore subagent to find all network request entry points".

## Built-in Subagent: Explore

On first launch, the system automatically releases the built-in **Explore** read-only exploration subagent into the global directory (located at `~/.aiverse/agents/explore.md`), ready to use out of the box.

Its core purpose is read-only codebase exploration and logic mapping:

-   **Purely read-only and safe**: it only exposes code and file retrieval tools (`readFile`, `list`, `search`, `websearch`, `webfetch`, `loadSkill`) and **contains no file-writing or terminal-command tools**, so it won't modify your project and doesn't need frequent authorization pop-ups;
-   **Model customization**: by default it inherits the current session's model. If you want to configure a more economical model for it, just fill in the corresponding `provider` and `model` in `explore.md`. If you'd rather let the AI pick the model automatically based on task difficulty, use "Settings → Default Model → Subagent model tier" to configure light/balanced/powerful tiers, and the AI chooses a tier at dispatch time (the definition hard-codes priority for `model`, in which case the tier has no effect);
-   **Persistently retained**: this file is only released when it doesn't exist on first run, and any personal modifications you make are fully preserved across App upgrades.

## Custom Subagents

There are two ways to create a custom subagent, and they are fully equivalent (saving in the App writes out the same `.md` file):

-   **Create in the App** (recommended): the "＋" in the top-right of "Settings → Subagents" — just fill in the form, and you can pick the model directly from your configured providers;
-   **Hand-write the definition file**: put a Markdown file into the corresponding directory yourself, which suits bringing in an existing definition from another project or managing it with git.

Two optional directories:

-   **Global configuration**: `~/.aiverse/agents/.md`, shared by all projects and not lost on upgrade. It follows the execution environment: in local mode it is `/root/.aiverse/agents/` inside the container (mapped to the phone App's private directory); in remote mode it refers to `~/.aiverse/agents/` on the **server**, with reads and writes done over SSH;
-   **Project-level configuration**: `/.aiverse/agents/.md`, effective only for the current workspace and can be committed to version control along with the codebase. In remote mode this refers to the workspace on the **server**, with reads and writes done over SSH.

When definitions share the same name, the project-level one takes precedence. The file consists of a YAML configuration (frontmatter) at the top and the prompt body:

markdown

```
---
name: researcher
description: Read-only code and technical research: broadly search code, consult web resources, and summarize conclusions; do not modify files
provider: DeepSeek
model: deepseek-reasoner
reasoningEffort: high
mode: plan
tools: [readFile, list, search, websearch, webfetch]
disallowedTools: [Bash, terminal]
inject: [base, projectRules]
---
You are a senior code research expert. First use search to locate entry points, then use readFile to verify in depth.
When outputting conclusions, always cite the exact `file path:line number`; clearly mark any unverified inference as such.
```

### Configuration Fields

Field

Required

Description

`name`

No

Identifier name of the subagent; defaults to the file name if omitted

`description`

Recommended

Briefly describes applicable scenarios and specialties; the main session AI uses it to decide when to select this subagent

`provider`

No

AI provider name; inherits the parent session's configuration if omitted

`model`

No

Model name; inherits the parent session's configuration if omitted

`reasoningEffort`

No

Thinking effort: `none` / `minimal` / `low` / `medium` / `high` / `xhigh` / `max`

`mode`

No

Default run mode: `build` / `plan` / `auto`; inherits the parent session's current mode if omitted. **It can only be stricter than the parent session, never looser**: if the main session is in PLAN, writing `build` / `auto` still runs as `plan`

`tools`

No

Tool whitelist. **Omitting it means all available tools are open**

`disallowedTools`

No

Tool blacklist, which takes precedence over the whitelist and supports the `mcp__*` prefix wildcard

`inject`

No

Injected system rule snippets; see the table below

### inject Rule Snippets

Value

Meaning

`base`

A lightweight baseline dedicated to subagents: includes tool-usage conventions and path conventions, lightweight and token-efficient

`mainRules`

Full general rules: behavioral guidelines and rule baseline fully consistent with the main session

`skills`

Skill support: injects the list of available skills, allowing the subagent to load and run project skills

`memory`

Memory support: injects global and project long-term memory summaries; see [Memory and Project Rules](https://aivapp.cn/md/guide/memory.md)

`projectRules`

Project rules: injects `AGENTS.md` or `CLAUDE.md` constraints from global (`~/.aiverse`) and the workspace; see [Memory and Project Rules](https://aivapp.cn/md/guide/memory.md)

`base` and `mainRules` are the lightweight and full versions of the same rule set — **normally pick only one**. Enabling both won't error, but it injects the overlapping rule text twice, needlessly consuming context.

If the `inject` field is omitted, it defaults to `[base, skills, memory, projectRules]`. If you set `inject: [none]`, only the prompt you write in the Markdown body is used.

## Managing in Settings

Go to "Settings → Subagents" to browse all recognized subagents grouped by "Current project / Global".

-   **Create**: tap the "＋" in the top-right. In the form you can enter a name and purpose description, choose the scope (current project / global), **select the model and thinking effort** from your configured providers, check the tool whitelist and blacklist, toggle the prompt snippets to inject, and finally write the agent prompt body;
-   **Edit**: tap into a subagent's detail page, then tap the pencil icon in the top-right. Changing the name also renames its definition file;
-   **Enable / disable**: the switch at the top of the detail page. Once disabled, it no longer appears in the main session AI's dispatchable list and the AI can no longer dispatch it by name, but the definition file is retained and already-running sub-sessions are unaffected;
-   **Capability labels**: in the dispatchable list the main session sees, each subagent is labeled with three capabilities — "File read / File modify / Run command" (derived from the tool whitelist and blacklist you checked on the detail page), and the AI dispatches accordingly — it won't assign modification tasks to a read-only subagent, nor send a subagent without command capability to run build or verification commands; in plan mode the latter two are additionally labeled "No (PLAN read-only)", indicating it is limited by the current mode rather than the subagent's own configuration;
-   **Delete**: swipe left on an item in the list to delete the corresponding `.md` definition file; this cannot be undone.

### Batch Operations

The "Multi-select" button in the top-right of the list (a box with a check) lets you handle multiple subagents at once:

1.  Tap "Multi-select" to enter multi-select mode; a checkbox appears on the left of each row and tapping the row selects it; the "Current project / Global" groups share one selection set.
2.  The top action bar provides "Enable / Disable / Delete" and "Select all / Deselect all"; the rightmost "×" or the system back key exits multi-select.
3.  "Select all" includes the collapsed group.
4.  Deletion pops up a confirmation dialog; after confirming, definition files are deleted one by one with "Deleting…" shown during the process, and the list refreshes and exits multi-select when done. In remote mode deletion goes over SSH, so when there are many items wait for the hint to disappear before leaving the page.

In multi-select mode you cannot tap into a detail page, nor swipe to delete a single item.

When creating and editing, the form avoids the soft keyboard; when the prompt body is long you can scroll up and down to keep editing the end of the content.

Enable/disable state is stored in `agents.json`, following the same convention as the skill disable list: the global list lives at `~/.aiverse/agents.json` in the current execution environment — in local mode it is saved in the phone App's private directory, and in remote mode on the server; the two are independent and are not automatically migrated or synced. The project-level list lives at `/.aiverse/agents.json` in local mode, while in remote mode it is still saved in the phone App's private directory, isolated by server and workspace. The two levels are unioned, and disabling in either scope takes effect.

After toggling enable/disable in the App, the list refreshes. In local mode, manually editing a definition or the list refreshes the list within a few seconds; in remote mode, changes to files on the server are not watched, so after editing manually you need to leave and re-enter the settings page to refresh the list.

## Runtime Mechanics and Limits

-   **Concurrency cap**: **6 subagents run simultaneously by default** (adjustable in "Settings → General → Preferences", no less than 1); beyond that you must wait for existing tasks to finish or pause some manually;
-   **No nesting**: a subagent cannot spawn further sub-level subagents;
-   **Two-way communication**: the main session can append messages to a running or finished subagent at any time (repeatedly), and a running subagent can also proactively report to or ask the main session for help; messages arrive asynchronously without blocking each other.
-   **Default mode**: a subagent inherits the main session's mode at dispatch time by default. A subagent dispatched in plan (PLAN) mode can likewise only do read-only exploration — the list the main session sees then labels "File modify / Run command" as "No (PLAN read-only)", and the dispatch result carries the same reminder, avoiding discovering only after dispatch that it can't be done; in auto (AUTO) mode, like the main session, there are no per-action pop-ups. A custom subagent can use the `mode` field to **narrow** permissions (for example fixing `mode: plan` on a read-only subagent so it stays read-only whenever dispatched), but cannot widen them beyond the parent session — this way PLAN mode can't be bypassed by "dispatch a subagent to change it";
-   **Authorization control**: subagents follow the same tool-authorization security mechanism as the main session. If a subagent attempts to write files or run system commands, it likewise triggers an authorization confirmation dialog (the dialog clearly marks which subagent it comes from);
-   **Manual stop is also reported to the main session**: when you tap stop in a sub-session, or directly delete a running subagent, the main session receives a "manually terminated" notification (rather than an execution failure), and the AI does not re-dispatch on its own; the concurrency slot is also released immediately;
-   **Lifecycle**: deleting the parent session cascades a cleanup of all subagent sessions it contains;
-   **When definition changes take effect**: after adding, renaming, or modifying a subagent definition, already-open sessions keep using the list from when they started; only **opening a new session** lets the AI see the change.
