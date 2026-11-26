# Role System

A role is a reusable "AI persona": each role contains a name, an avatar, and settings (prompt fragments) that influence the AI's behavior and tone. When a conversation starts, the system injects the selected role's settings into the system prompt so the AI answers according to that persona.

The app ships with a number of preset roles that work out of the box; you can also create custom roles and switch them for a conversation at any time.

Role Settings Influence the AI

The selected role's settings are injected into the system prompt at the start of a conversation, directly affecting the AI's tone and behavior.

## Switching the Conversation Role

The top bar of the chat shows the role used by the current conversation. Tap it to open the role selection drawer, which lists all available roles (with avatars and names); selecting one binds that role to the **current conversation**.

-   **Default Role**: Choosing "Default Role" means no specific role is bound and the AI answers in its default manner.
-   **Bound to the Current Conversation**: Switching roles only affects the current conversation and does not change other conversations.
-   Role avatars appear in the top bar, the role selection drawer, the chat input area, and other places.

## New Conversations Carry Over the Last Role

After you have switched roles, newly created conversations carry over the role you chose last, so you do not have to switch every time (**from 1.0.3**).

Existing older conversations each keep their original role and are unaffected.

## Role and Workspace Binding

In the role edit page you can assign a "bound workspace" to a role. When bound, new conversations created with that role selected are automatically placed in the corresponding workspace, achieving a "switch role = switch environment" isolation experience (similar to qwenpaw's workspace mechanism).

-   **Not Bound**: Default behavior; new conversations follow the currently selected workspace.
-   **Bound Workspace**: When a new conversation is created with this role selected, it is automatically placed in the role's bound workspace; the session management page's bucketing and filtering work automatically.
-   **Compatibility**: Old data (workspaceId = null) is fully compatible with unchanged behavior; backup export and import both preserve this field.

Once a workspace is bound, conversations are bucketed by workspace. For how to view and filter conversations by workspace, see [Session Management](/en/guide/sessions).

## Managing Roles

Under "Settings → Roles" you can manage all roles. Built-in preset roles and custom roles are managed together.

When creating or editing a role, the following fields are available:

-   **Name**: the role's display name, with a length limit (about 8 Chinese characters / 24 Latin characters); you get a prompt if it is too long.
-   **Description**: a one-line summary of what the role is for, to help tell roles apart in the list.
-   **Identity prompt text**: the core setting actually injected into the system prompt that shapes the AI's tone and behavior.
-   **Tags**: for categorization; separate multiple tags with commas.
-   **Avatar**: an Emoji, an image (a hand-entered URL/path or picked from your album), or none.
-   **Bound workspace**: optional; when a new conversation is created with this role selected, it is placed in the bound workspace automatically.

-   **Create**: Tap the "＋" in the top-right corner, fill in the name, pick an avatar from your album, and set the prompt content.
-   **Edit**: Tap a role to enter its detail page, then tap edit to change the name, avatar, and settings content.
-   **Name Length**: The name has a length limit; you get a prompt if it is too long.
-   **Enable / Disable**: You can disable custom roles you do not need; once disabled they no longer appear in the switchable list. Built-in preset roles cannot be disabled, and their persona text is always injected. If you do not want a built-in role's default persona, you can clear its persona text on the edit page.
-   **Delete**: You can delete custom roles.

## Importing a Role from a Markdown File

In the "Identity" group of the role edit page, tap "Import" to fill in a role quickly from a Markdown or plain-text file (`.md` / `.txt`):

-   **Body**: the file's body is written into the "identity prompt" box.
-   **Description**: if the body starts with a leading comment of the form `<!-- description -->`, it is extracted as the "description" (only filled in when the description is empty).
-   **Name**: if the name is still empty, it defaults to the file name (without the extension); it is truncated automatically if it exceeds the length limit.

A name or description you have already filled in is not overwritten by the imported content, so you can build the skeleton first and import the body afterwards.

## Roles and Backup

Roles are exported and imported together with "Backup & Restore". After restoring a backup, the roles and their bindings to conversations are restored as well.
