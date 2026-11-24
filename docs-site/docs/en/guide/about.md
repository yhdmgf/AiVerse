# About and Updates

"Settings → About" shows app information and is also the entry point for checking updates.

## Version and Checking for Updates

The card at the top shows the app icon, app name, and a one-line description; the "Version" row below it shows the current version number.

**Tap the version row to check for updates manually**: it queries the GitHub release list for a newer version (filtered by the update channel you selected). When a new version is found, a dialog pops up saying "New version found" and lists **the complete changelog from your current version to the latest version**; tapping "Download and install" downloads the package matching your device's architecture inside the app (through the accelerated mirror by default), and once verified it directly launches the system installer — the first time you need to allow the app to "install unknown apps"; after granting, return to the app and tap "Install" once more. The package is already downloaded and won't be downloaded again.

Closing the dialog during the download won't interrupt it; tapping the "Version" row again returns to the progress screen. If the download fails, the dialog explains the reason, and you can tap "Retry" or switch to "Download in browser" to fetch the package yourself from the Release page.

If you're already on the latest version, it shows "Already the latest version"; a network failure shows "Check failed" along with the error message.

If it says **the package signature doesn't match the current app**, the package isn't signed by the current app (for example, a version installed from another channel), so you can only handle it manually via "Download in browser"; the in-app update won't install over it.

## Automatic Update Checks

"Automatic update checks", "Update channel", and "Download mirror source" now live under the "Update settings" row on the About page, collapsed by default; tap once to expand.

Enabled by default. When you open the app and reach the home page, it checks asynchronously in the background, **at most once per day** (if it has already checked that day, it won't repeat). When a new version is found, the update dialog pops up automatically; on network failure it fails silently without bothering you.

After turning it off, it no longer checks automatically, but tapping the version row manually still works.

## Update Channel

"Stable" by default:

-   **Stable**: only checks official releases and filters out RC previews.
-   **Latest**: includes RC previews.

Switch to Latest if you want to try new features early; stay on Stable if you prefer stability.

Whichever channel you choose, what gets downloaded is the package for the version shown in the dialog itself: if the Latest channel detects an RC preview, you get that RC's package, not an older stable one; when you tap "Download in browser" it also jumps straight to the corresponding Release page by that version's tag.

## Download Mirror Sources

The in-app update package download **always tries three addresses in a fixed order**, falling back automatically (unrelated to the "Download mirror source" setting):

1.  **gitcode mirror**: `https://gitcode.com/aiverse/aiverse` (a GitHub mirror reachable directly from mainland China).
2.  **Direct GitHub**: downloads straight from `github.com`.
3.  **v6.gh-proxy.org mirror**: the `https://v6.gh-proxy.org/` acceleration node.

It tries gitcode first; if the download fails (network error, or a mismatched file size or sha256), it switches to the direct connection automatically, and only if that fails too does it use the v6 mirror; the download is reported as failed only when all three addresses fail. So a single source being unavailable won't deadlock the update — it just takes a bit longer.

The "Download mirror source" setting on the settings page is only for configuring a custom acceleration prefix (with presets for the gh-proxy CDN, preferred nodes, and direct GitHub, supporting the `{url}` placeholder syntax — fill it in and tap "Save"); the in-app update package download currently **does not use** this setting and always tries in the fixed order above.

## Other Entries

-   **GitHub repository**: opens the source repository in the system browser.
-   **Feedback**: jumps to GitHub to create a new issue, making it easy to report bugs or suggest features.
-   **Open source license**: this project uses GPL-3.0; tap to open the full LICENSE file.
-   **Terms of Service**: opens the [Terms of Service](https://aivapp.cn/md/terms.md) in the system browser to learn about usage rules, AI and operation risks, third-party fees, and open source rights.
-   **Privacy Policy**: opens the [Privacy Policy](https://aivapp.cn/md/privacy.md) in the system browser to learn how data storage, network transmission, logs, and backups are handled.

## First-Launch Confirmation

When you launch the app for the first time, an agreement confirmation dialog covers the main interface, containing two blue links: "Terms of Service" and "Privacy Policy". Tapping either link opens the full text in the system browser; after reading, return to the app to decide whether to agree. The main interface stays visible while the confirmation state is being read; first-launch onboarding and automatic update checks start only after you agree.

-   **Disagree**: exits the app.
-   **Agree**: you can continue using it, and the confirmation state is remembered so you won't be prompted again on later launches.

To read them again, go to "Settings → About" at any time and tap "Terms of Service" or "Privacy Policy" respectively.
