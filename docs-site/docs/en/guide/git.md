# Git Version Management

Open the Git page in a project workspace to manage code versions visually. There are five tabs at the bottom: Status, Branches, Commits, Issues, and PR. If the current directory is not a Git repository, you can initialize it as an empty repository in one tap (default branch `main`), or enter a remote address to clone an existing repository directly (**since 1.0.0**).

## Multi-Directory Detection

The Git page automatically detects Git repositories in the current workspace root and its **one level of direct subdirectories**:

-   The current repository name is shown below the top bar title; tap it to bring up a repository selection list and switch between multiple repositories.
-   The repository selection list only shows detected Git repositories, and the currently selected repository is highlighted in the primary color.
-   If the workspace root is not a Git repository but a subdirectory is, the subdirectory repository is selected automatically, showing its status, branches, and commits.
-   When neither the workspace root nor any subdirectory contains a Git repository, a "Not a Git repository" message appears. You can "Initialize a new repository" (runs `git init` at the current workspace root) or tap "Select an existing repository".

## Credentials and Signing

The key icon in the top-right corner opens the "Credentials and Signing" page, which contains two tabs — Signing and Credentials. **You must set up signing before first use, otherwise you cannot commit.**

The "+" in the top-right of the Credentials page adds a new Git credential. Two methods are available:

-   **Add manually**: fill in the remote host, username, and access token (Token / PAT).
-   **Quick sign-in** (**since 1.0.0**): authorize with your GitHub account and add a credential in one step, without creating a Token yourself. After selecting GitHub, the app shows a device code; tapping "Copy and go to authorize" copies the device code and opens the GitHub verification page. After you paste the code and authorize, the app completes the sign-in automatically and writes the `github.com` credential into the list. You can also tap "Copy device code" on its own and open the browser manually.

Quick sign-in is based on the fine-grained authorization of a GitHub App: which repositories can be accessed and which permissions are granted are determined by the App's registration configuration and selected by you at install time, so full access to all repositories under your account is no longer needed. After sign-in, if AiVerse has not been installed on your repositories yet, a dialog will prompt you and offer a "Go to install" button; tap it to jump to the GitHub installation page and choose the repositories to authorize.

If quick sign-in says "This app has not enabled the device code flow", the corresponding GitHub App has not turned on Device Flow; "Invalid client ID" means the app configuration is wrong.

While you leave the app to authorize in a browser, the sign-in dialog keeps waiting in the background (the device code is valid for 15 minutes) and retries automatically on brief network interruptions; by the time you return it has usually finished.

## Status

View the working tree status of the current repository.

The top card shows the current branch name, how many commits it is ahead of or behind the remote (up/down arrows), and the number of staged, modified, and untracked files.

**Commit** is a full-width primary button. You must stage changes and set up signing before it can be tapped; after tapping, you fill in the commit message.

A secondary row has four buttons:

-   **Stage all**: add all changes to the staging area.
-   **Stash** (**since 1.0.0**): open the stash box; see the "Stash" section below.
-   **Pull / Push**: requires a configured remote. When the current branch has no upstream, pushing establishes the association automatically.

To the right of the group titles there are also:

-   **Unstage all**: in the staged group, asks for confirmation before executing. This only unstages; it does not delete file content.
-   **Discard all** (**since 1.0.0**): in the modified group, reverts all unstaged changes back to the last staged or committed content. Staged content is unaffected, but discarded changes cannot be recovered; asks for confirmation before executing.

File changes are split into three groups: "Staged / Modified / Untracked":

-   Staged: tap a row to view the diff between the staging area and the last commit; "−" at the end of the row unstages it.
-   Modified: tap a row to view the diff between the working tree and the last commit; "+" at the end of the row stages the file. Deleted files also appear in this group, with a red D status badge.
-   Untracked: tap a row to view the content of this new file (shown entirely as an addition); "+" at the end of the row stages it. Git collapses newly created directories into a single row (such as `feature/`); tap it to expand the files inside, then tap an individual file to view its content.

If the working tree has uncommitted changes, tapping "Pull" first warns that conflicts may occur, and only proceeds after you confirm.

### Commit Dialog (enhanced since 1.0.0)

In the window that pops up when you tap "Commit changes":

-   **AI generate**: tap the button on the right of the title bar to let the AI read the diff between the staging area and the working tree and automatically generate a commit message that follows Conventional Commits. While generating, the button shows "Generating…"; if generation fails, you are prompted to check the model or network.
-   **Quick types**: a row of chips above the input field (`feat`, `fix`, `refactor`, `chore`, `docs`, `style`, `perf`, `test`); tapping one applies the corresponding prefix to the current message.
-   **Amend the last commit (--amend)**: a toggle at the bottom. When enabled, this commit is merged into the previous one instead of creating a new entry.

### Merge Conflicts (since 1.0.0)

If conflicts arise after merging a branch, the Status page lists the conflicting files separately (status badge is purple-red U). After resolving the conflicts in the editor, stage the corresponding files.

When there are no unresolved conflicts but the merge has not been committed yet, the page shows "Merge pending commit". At this point, review and stage the changes you want to keep, then tap "Commit changes" to finish the merge; this does not mean there are still conflicts.

If you want to abort this merge, tap "Abort merge" to revert to the pre-merge state. Do not abort a merge just to dismiss the prompt.

## Stash (since 1.0.0)

Stash temporarily puts aside your current changes to return the working tree to a clean state, so you can retrieve them later. Tap the "Stash" button on the Status page to open the stash box:

-   **Stash changes**: you can enter an optional note and check "Include untracked files (-u)" to stash new files as well.
-   **Pop**: takes out this stash and removes it from the stash list.
-   **Apply**: takes out the content but keeps this stash record.
-   **Drop**: deletes a single stash; asks for confirmation before executing.
-   **Clear all**: deletes all stashes; asks for confirmation before executing.

Neither drop nor clear can be undone.

## Discard and Delete (since 1.0.0)

Version note

The long-press action menu on the Status page (discard changes, restore file, delete untracked item) and "Discard all" are available since 1.0.0; "Add to .gitignore" in the menu is available since 1.0.0.

**Long-press any file row** to bring up an action menu from the bottom, with the full path shown at the top of the menu. The available actions depend on the group the file is in:

Group

Menu items

Conflicting files

View diff, stage, copy path

Staged

View diff, discard changes (reverts to the last commit; both staged and unstaged changes are lost), copy path

Modified

View diff, discard changes (reverts to the last staged or committed content), copy path

Deleted files within modified

View diff, **restore file** (brings the file back from the last commit), copy path

Untracked files

View diff, **add to .gitignore** (ignore this file / ignore files of this type), delete file, copy path

Untracked directories

**Add to .gitignore** (ignore the entire directory), delete directory, copy path

The consequences of the two kinds of operations are completely different, so keep them straight:

-   **Discard changes / restore file**: applies to files Git already tracks. Their content lives in the version history, so whether you deleted them, or a terminal command or the AI deleted them, they can be brought back. The cost is that uncommitted changes this time are lost.
-   **Delete file / delete directory**: only appears on untracked items. These files were never in the version history, and **Git cannot recover them after deletion**.

All discard and delete operations pop up a confirmation dialog before executing, explaining what will be lost.

## Branches

Displayed in four groups: HEAD, local branches, remote branches, and tags. The current branch card shows the branch name and the counts of the three kinds of references. Local branches are shown as a path tree, with the current branch highlighted.

**Switch branches**: tap any branch or tag, and it switches after confirmation. Tapping a remote branch creates a local tracking branch and then switches to it. Switching to a tag enters a detached HEAD state (commits at that point belong to no branch, so avoid working in that state for long).

**Tap a branch row** to bring up an action menu:

-   **Switch**: switch to that branch.
-   **Merge branch** (**since 1.0.0**): merge that branch into the current branch; the confirmation dialog offers "Create a merge commit (--no-ff)". The current branch itself does not show this item.
-   **Rename**: rename the local branch.
-   **Delete**: local branches use a safe delete, so only merged branches can be deleted; unmerged branches are refused with a reason. Deleting a remote branch acts directly on the remote and is **irreversible**. The branch you are currently on cannot be deleted.

**New branch**: tap the + to the right of the local branches group title, fill in the branch name, choose the base branch (default is the current branch); the "create and switch" toggle is on by default.

**Tags**: tap the + to the right of the tags group title to create a lightweight tag from the current HEAD; tap an existing tag → action menu → delete to remove a local tag.

### Naming suggestion for the first push of a master branch (since 1.0.0)

If the current local branch is named `master` and has no upstream yet, a dialog suggests renaming it to `main` (the default main branch on GitHub and GitLab) the first time you push. You can choose "Rename to main and push", or "Keep master and push".

## Commits

Shows recent commits with a colored branch topology graph. Each entry shows the short hash, author, relative time, commit message, and a marker for the branch or tag it belongs to.

**Search filter** (**since 1.0.0**): a search box above the list lets you filter in real time by commit message, author, or short hash.

Tapping a commit opens a bottom detail panel: the full commit message, author and time, full hash (both the short and full hash can be copied), whether it is a merge commit, and the list of files changed in this commit. Tap a file to view its diff.

Above the detail panel there is also a row of advanced actions (**since 1.0.0**):

-   **Branch**: create a new branch from this commit and switch to it.
-   **Tag**: create a tag on this commit.
-   **Reset**: reset the current branch to this commit, with a choice of `Soft` (keep the working tree and staging area) or `Mixed` (keep the working tree, empty the staging area).

Scrolling down near the bottom automatically loads earlier commits; once everything is loaded it shows "No more".

## Issues (since 1.0.0)

The fourth tab on the Git page, "Issues", manages issues on the hosting platform. Supported: **GitHub / Gitee / GitCode (including atomgit.com)**. The repository must be linked to the corresponding remote and a token for that host (`github.com` / `gitee.com` / `gitcode.com`) must be configured:

-   **List**: each row shows the issue number and title, with the creator's/assignee's avatar at the end of the row. The top provides:
-   **Sort**: sort by newest, oldest, and other dimensions.
-   **Filter state**: filter by `open` / `closed` and other states.
-   **Search**: filter issues by keyword.
-   **Detail**: tap an issue to view its full detail, including description, comments, labels, assignee, milestone, and more.
-   **Write actions**:
-   **Create / Edit**: fill in a title and description.
-   **Close**: requires choosing a close reason (`completed` / `not_planned`).
-   **Reopen**: reopen a closed issue.
-   **Comment**: post a comment.
-   **Labels / Assignee / Milestone**: set or modify.
-   **Lock**: lock the issue to prevent further comments.
-   **Multi-select batch**: long-press a list item to enter multi-select; a "N selected" action bar appears at the top, letting you **batch close**, **batch label** (separate multiple labels with English commas), and **batch assign** (enter user logins, comma-separated, **since 1.0.3**); write actions return a success/partial-failure message.
-   **Create and ask AI**: tap + at the top right of the list to open the new-issue form with a title, description, labels, and assignees; on the detail page, "Ask AI to fix this issue" turns the issue info into a prompt. The AI reads the issue, edits code, runs tests, creates and pushes a branch, then calls `createPullRequest` to open a pull request that references the issue.
-   **AI tools**: the AI can use `listIssues` / `getIssue` to view the list and detail automatically; `getIssue` returns the first 10 comments by default (use `commentLimit` for up to 100), and comments often contain supplementary details, so the AI should read them before working on the issue; `createIssue` / `updateIssue` can create, close, comment, change labels and assignees, and require your confirmation before executing.

## Pull Requests

The fifth tab on the Git page, "PR", lets you browse, review, and manage GitHub pull requests. The repository must use a GitHub remote and have a GitHub token configured:

-   **List**: each row shows state (open / draft / merged / closed), title, number, author, head → base branch, and CI summary. The top lets you filter by open / closed / all, sort by newest, oldest, or last updated, and create a pull request with + on the right.
-   **CI status**: the list fetches check status (passed / failed / running) for the first 20 pull requests, while the detail page always fetches the full result. When no GitHub Actions checks exist, it falls back to legacy commit statuses.
-   **Detail**: tap a pull request to see its description, labels, line stats, commit history, changed files, CI check details (failed checks include a summary), and comments; you can post or delete your own comments.
-   **Manage**: close or reopen a pull request from the detail page; open, non-draft pull requests can be merged with a merge commit, squash, or rebase after confirmation. Merge failures are usually caused by conflicts, branch protection, or failing checks.
-   **Create**: tap + on the list to create from an existing branch, or commit the current changes to a new branch first (the app stages, commits, and pushes automatically).
-   **AI review**: tap "Ask AI to review" on the detail page to put a prompt with the pull request number, title, branches, and CI status into the current chat draft. After you send it, the AI calls `getPullRequest` to confirm the scope and `reviewPr` to fetch the diff and review it file by file.
-   **AI fix CI**: when CI has failed checks, "Ask AI to fix CI" appears; the prompt includes the failed check names so the AI can inspect the failure and fix the workspace code.
-   **AI tools**: `listPullRequests` / `getPullRequest` are read-only and auto-approved; `createPullRequest` (create) and `updatePullRequest` (close / reopen / merge / comment) are write actions and require your confirmation.
-   **Token safety**: the GitHub token is only used by the local repository layer and the container credential helper; it is never written into chats, prompts, logs, or tool results.
-   **Limitations**: Approve / Request changes review actions are not available yet.

### AI Commit and PR

When you ask AI to fix an issue or CI failure, it uses the container's git to commit and push directly:

-   Inside the workspace repository it runs `git status` / `git diff --stat`, creates a branch, then `git add` / `git commit` / `git push -u origin `.
-   The push credential is injected automatically by the custom container helper: the workspace repository loads `credential.helper` via `GIT_CONFIG_GLOBAL=~/.aiverse/.gitconfig`; the helper reads `~/.aiverse/git-credentials`, reverses and base64-decodes it, and picks the token by host. **The reversal and decoding happen inside the helper; the AI does not need the algorithm and never reverses or reads the file.**
-   Before committing, the AI first checks the commit identity (`git config user.name` / `user.email`), then whether the credential file exists (`test -s ~/.aiverse/git-credentials`); if either is missing it stops and tells you to open the key icon on the Git page → Credentials & Identity to set the identity or add a github.com token, then continues after configuration.
-   The AI never reads the token, and the token never enters chats, prompts, logs, or tool results. `createPullRequest` / `updatePullRequest` use the same token in the app's local repository layer.
-   After the push succeeds the AI calls `createPullRequest`; if authentication fails, there is no remote, or branch protection blocks the push, it reports the original git error for you to handle.
-   This only applies to HTTPS remotes; SSH remotes use ssh-agent.

## Diff View

When you tap a file or commit to view the diff, a separate diff page opens:

-   The diff is shown in color with line numbers as `+ / −`; the top bar can **open the file in the editor** (**since 1.0.0**), jumping straight to that file in the code editor.
-   If a single-side file exceeds 2 MB, it directly shows "File too large" and does not display content (the same applies to binary files).

## Very Large Output

To protect phone memory, the Git page does not read extremely large content in full: when reading git output for status, branches, commit list, etc. exceeds the limit, it shows "Output too large, truncated. Narrow the scope and retry" and stops trying to load.

## Common Error Messages

When an operation fails, Git's raw error is translated into a Chinese message. If a particular error has no matching translation, Git's original text is shown directly for easier troubleshooting.

Scenario

Message

Current directory is not a repository

The current directory is not a Git repository

Abnormal repository owner

Git detected an abnormal owner of the repository directory (common on external storage) and cannot operate safely

Repository in use / leftover lock files

The repository is in use by another Git operation; please try again later

Corrupt index

The Git index file is corrupt; please close and reopen the repository

Disk write failure

Cannot write repository files; please check storage permissions or free space

Deleting the current branch

Cannot delete the branch you are currently on; switch to another branch first

Deleting a branch with unmerged commits

This branch has unmerged commits and cannot be safely deleted

Duplicate new branch name

A branch with the same name already exists

Invalid branch name

The branch name is invalid

Duplicate new tag name

A tag with the same name already exists

Deleting a non-existent tag

The tag does not exist

Switching to a non-existent branch/reference

The branch, tag, or commit does not exist

Operated path does not exist

The specified file or path does not exist

Untracked files would be overwritten

There are untracked files that would be overwritten; move or delete them before continuing

Local changes would be overwritten on switch

You have uncommitted local changes that would be overwritten; commit or stash them before switching branches

In detached HEAD state

You are in a detached HEAD state and this operation cannot be performed; switch to a branch first

Merge conflict

The merge produced conflicts; go to the Status page to view and resolve them

Unresolved conflicts

There are unresolved conflicts; resolve them or abort the merge first

No common history

The two branches have no common history and cannot be merged

Target cannot be merged

Cannot merge: the target is not a valid branch or commit

Cannot fast-forward

Cannot fast-forward the merge; there are diverged commits, use a regular merge instead

Local changes during merge

You have uncommitted local changes; commit or stash them before merging

Commit signing not configured

Commit signing is not configured; enter your username and email in settings

Nothing to commit

There are no changes to commit

Empty commit message

The commit message cannot be empty

Committing with unresolved conflicts

The merge conflicts have not been resolved yet; cannot commit

Branch not linked to remote

The current branch has no linked remote branch; cannot pull

No pull branch specified

No remote branch to pull was specified

No remote configured

No remote repository is configured; cannot push

No local commits to push

There are no local commits to push

Local and remote diverged

The local and remote branches have diverged; pull and merge or rebase before pushing

Push rejected (remote has updates)

Push rejected; the remote has newer commits, pull first

Push rejected by remote

The remote rejected this push, possibly due to branch protection or permission limits

Remote branch does not exist

The remote branch does not exist

Remote repository unavailable

The remote repository is unavailable or you have no access permission

Remote authentication failed

Remote authentication failed; check your credential configuration (username/password/Token)

Remote repository does not exist

The remote repository does not exist or you have no access permission

Domain resolution failed

Cannot resolve the remote host address; check your network or proxy

Connection failed or timed out

Failed to connect to the remote repository or the connection timed out; check your network or proxy

Connection dropped

The connection to the remote repository was interrupted

Certificate/TLS failure

Failed to establish a secure connection (server certificate validation or TLS handshake failed)

Clone directory not empty

The current workspace directory is not empty; cannot clone directly into it

Nothing to stash

There are no changes to stash
