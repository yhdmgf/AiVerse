# Shizuku Tools

Shizuku lets an ordinary app execute system commands. After AiVerse integrates it, the AI gains a tool named `Shizuku` through which it can run commands such as `pm`, `am`, and `cmd` on the phone's system, making up for the local container (PRoot) not having system privileges.

## Prerequisite: Install and Start Shizuku

1.  Install Shizuku from the [Shizuku website](https://shizuku.rikka.app/download/) or an app store.
2.  Open Shizuku and start the service by following the in-app instructions:

-   Android 11 and above: after enabling "Wireless Debugging", you can start it directly on the phone;
-   Android 10 and below: you need to connect to a computer and start it with adb.

## Authorizing in AiVerse

Open "Settings → App Permissions → Shizuku"; the page shows the current status:

-   **Not Installed**: Tap to go download and install it.
-   **Service Not Running**: Tap to open Shizuku and start the service.
-   **Not Authorized**: Tap to request authorization; Shizuku shows an authorization dialog — choose "Allow".
-   **Ready**: Authorization is complete and the AI can use the `Shizuku` tool.

The status refreshes automatically when you return to AiVerse from the Shizuku app.

## Usage and Safety

`Shizuku` operates the host Android system directly, so its privileges are high-risk. It is therefore treated as a **high-risk tool** in tool authorization:

-   **Every call pops up an authorization confirmation**: including read-only commands like `ls`; it does not go through the built-in safety whitelist and is not auto-allowed because of existing rules.
-   **"Always Allow" is not supported**: the "Always Allow" button in the dialog is unavailable (a reason is shown); you can only allow a single use, and no memory rule is created.
-   **AUTO mode also shows the dialog**: AUTO's "allow all" has no effect on it. Only after enabling "Settings → Tool Authorization → Disable Safety Interception" does AUTO restore automatic allowance (BUILD mode still confirms each time and cannot be memorized).
-   **In PLAN mode**, this tool is blocked like any other write operation.
-   **Deny rules** in project permission rules still apply to it (a matching `deny` is rejected outright).

## Phone Control (PhoneControl)

Besides the general command tool, AiVerse also provides the `PhoneControl` tool, which controls the phone's screen and apps directly through the **accessibility service** without needing Shizuku authorization. The entry is under "Settings → Phone Control", where you must first enable its "Accessibility Permission" service (that service only works when the AI is doing something on the phone).

Supported operations:

-   **Read Screen** (`read_screen`): Returns a list of the interactive elements on the current screen (such as `[1] button Like`); the AI locates an element's id by its text.
-   **Click Element** (`click_element`): Clicks by id through accessibility, without relying on screenshot coordinates.
-   **Open App** (`open_app`): Opens the matching app by specifying a `package_name` (such as `com.tencent.mm`).
-   **Tap** (`tap`): Performs a tap at the specified `x`, `y` coordinates.
-   **Swipe** (`swipe`): Performs a swipe from the specified start to end coordinates, with an optional duration.
-   **Long Press** (`long_press`): Long presses at the specified coordinates, with an optional duration.
-   **Input Text** (`input_text`): Types text into the focused field (does not support special characters such as `;` `&` `|` \` \` $ \\\`).
-   **Key Event** (`key_event`): Supports `BACK` / `HOME` / `RECENT` / `NOTIFICATIONS` / `QUICK_SETTINGS`.
-   **List Installed Apps** (`list_apps`): Returns the apps installed on the device; it can list third-party apps only.

`PhoneControl` manipulates the phone directly, which is an **extremely high-risk** operation. Its safety policy (all adjustable on the "Settings → Phone Control" page):

-   **Confirmation mode** (default "Ask every time"): controls when a confirmation pops up, with three options —
    -   **Ask every time** (default): confirms before entering a new app, sending, deleting, or paying.
    -   **Task-level skip**: asks only once per task; later actions within the same task no longer ask again.
    -   **Always skip**: never asks and acts directly; this is the riskiest option, so enable it with care.
-   **Deny list takes priority**: when the action targets an app on the "deny" list (banking/payment/brokerage apps are built in by default), it is denied regardless of the confirmation mode — this is a safety floor that cannot be bypassed.
-   **Confirmation uses a floating window**: when confirmation is needed, the dialog pops up as a system floating window, so you see "Deny / Allow" whether the app is in the foreground or you are on another page. Modes that need confirmation depend on the "Display Over Other Apps" permission; without it, the tool is rejected outright and prompts you to enable it (System Settings → Apps → AiVerse → Display Over Other Apps).
-   **App rules**: you can set a rule per app, with priority "deny > confirm each step > normal". Adding banking/payment apps to "deny" makes the AI leave them completely untouched; "confirm each step" makes every action inside that app ask you.
-   **Unlocks** (all off by default): let the AI do what is normally reserved for you, with three switches — **system pages** (taps in system settings, permission pages, the installer, security center, and the notification shade), **secret fields** (filling in password, verification code, and payment code inputs), and **this app's own UI** (reading and operating AiVerse's own interface). When enabled, the AI is no longer restricted accordingly; enable them sparingly as needed.
-   **Actions such as sending, payment, and deletion ask you first** (in modes that need confirmation); the AI does not fill in passwords or verification codes on your behalf, does not pay for you, and does not change system settings or permissions.
-   It is **blocked in PLAN mode**; **deny rules** in project permission rules still apply.
-   **Recent actions**: the page keeps the last several phone-control actions so you can review what the AI did.

### Dynamic Island (Floating Bar)

During phone control operations, a **floating bar** (dynamic island) stays at the top of the screen so you can pause or stop at any time and avoid operations getting out of control:

-   **Tap to Expand and View**: When collapsed into a small ball, **tap the ball** to expand the panel, where you can see the AI's current operation and the last few steps in real time. The ball is not dead while the AI is working either — it **pulses and shows the first character of the current action**, so you know the AI is still operating even when it is collapsed.
-   **Result Card After Each Step**: After each step finishes, expanding the floating bar shows a **result card** (a title of "Completed" or "Operation Failed" + a checkmark/cross badge + a scrollable result body) and provides three buttons: **Copy** (copy the result to the clipboard), **Done** (collapse the floating bar), and **Back to App** (bring up the AiVerse main interface to view the result).
-   **Idle Opacity**: The opacity of the floating bar while operations are paused.
-   **Auto-Collapse When Idle**: It collapses into a small ball; tap the ball to expand it again.
-   **Return to the App When Done**: When enabled, once an operation ends and the floating bar idles and collapses, it automatically returns to the AiVerse main interface to view the result.

\> Both the ball and the panel tidy themselves up automatically after a period of inactivity (the collapsed ball disappears after roughly 30 seconds idle) and do not linger forever; they reappear when the AI starts a new step.

\> The accessibility service under "Settings → Phone Control" must be enabled before `PhoneControl` can run.

**From 1.0.0**, pressing the back button on the phone control page returns to the main settings menu.

## FAQ

**A command reports "Shizuku is not ready"**

Follow the prompts on the settings page in order: installed → service running → authorized. After the device reboots, the Shizuku service usually needs to be started again (on Android 11+ you can re-enable wireless debugging and start it from within Shizuku).

**The `Shizuku` tool fails to execute but there is no obvious error**

Some commands require specific system privileges, and `Shizuku` does not have every privilege. You can compare using `Bash` in the terminal to confirm whether the problem is the command itself or the privileges.
