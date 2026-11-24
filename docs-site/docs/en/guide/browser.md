# Built-in Browser

The app has a built-in WebView browser that supports AI-automated web page operations. The AI can operate the browser in the background, and screenshots can also be taken in the background (without opening the panel first).

## Opening the Browser

Tap "Built-in browser" in the card at the bottom of the sidebar:

-   **Large screen (tablet)**: the browser opens side by side with chat in the right column, and you can drag the divider to adjust its width.
-   **Narrow screen (phone)**: the browser opens full screen; press the back key to return to chat.

## Manual Browsing

Type a URL in the address bar and press Enter to navigate; if what you type doesn't look like a URL (e.g. it contains spaces or Chinese), it's handed to the built-in search engine Bing automatically. A refresh button is provided on the right of the address bar, and the bottom bar below includes back, forward, new tab, multi-tab management, and a more settings menu (the three-bar icon). While a page loads, a linear progress bar appears below the address bar.

Tapping the three-bar icon on the right of the bottom bar opens the "Browser settings & tools" panel, which centrally manages dark mode, developer tools, browser identification (User-Agent), screenshots, bookmarks, history, the home page, and clearing the cache (**bookmarks, history, home page, and screenshots since 1.0.3**).

## Local File Preview

The address bar supports local file addresses, so you can open HTML files on the device directly:

-   Real device path: `file:///storage/emulated/0/Download/index.html`
-   Container path (the path the AI sees inside the container, automatically mapped to the real file): `~/workspace/index.html`, `/etc/xxx.html`

The AI can also open a local page and take a screenshot for analysis via the `navigate` action of the `browser` tool.

**Note**: in remote workspace mode the workspace files are on the remote side, so only the page itself can be loaded and the relative resources (CSS/JS/images) referenced by the HTML will fail; local workspaces are not affected.

## Bookmarks / History / Home Page / Screenshots / Clear Cache

In the bottom bar's three-bar menu, besides dark mode, developer tools, and browser identification, five management capabilities are provided (**since 1.0.3**):

### Bookmarks

In "Three-bar → Bookmarks" you can "Add current page" to save the current URL into bookmarks; adding the same URL again updates the title and moves it to the top. Bookmarks are listed in reverse order of when they were added; tap to jump, and swipe left to delete. There's no limit on the number of bookmarks — you manage them yourself.

### History

"Three-bar → History" lists browsing history by most recent visit, and a page load completion is automatically recorded as one visit; visiting the same URL again updates the title and moves it to the top, and **at most the most recent 200 entries are kept**. Tapping a history item reopens it directly, and "Clear" at the top right asks for a second confirmation (this operation cannot be undone).

### Home Page

"Three-bar → Home page" lets you "Set to current page" to make the current URL the home page, or "Clear home page"; when unset it shows "Not set". When a new tab has no address, the home page loads automatically, for quickly returning to frequently used sites.

### Screenshots

"Three-bar → Screenshot" captures the current page as a JPEG, saved **without needing storage permission** into the container workspace at `~/workspace/.aiverse/browser-screenshots/`; after saving, the system share sheet is brought up automatically so you can send it to other apps. No storage permission is requested and the media library isn't used. The container path is what's shown in the Toast.

### Clear Cache

"Three-bar → Clear Cache" wipes the browser's HTTP cache, cookies, and site data (such as LocalStorage and login states), then automatically refreshes the current page so the change takes effect immediately. Because this signs you out of websites you've logged into and cannot be undone, a second confirmation is shown after you tap it. Use it to "reset" the browser when troubleshooting page issues or switching accounts.

## Web Page File Upload (since 1.0.0)

The browser panel supports file upload controls in web pages: tapping a file selection button on a page (``) opens the system file picker, letting you select files on the device to upload to the page (such as images or documents) without needing an external file manager.

## Developer Tools

Turn on the "Developer tools" switch in the bottom bar's three-bar menu to open or collapse the mobile developer tools (based on Eruda) at any time:

-   **Full-featured console**: includes Console (view logs and execute JavaScript code), Elements (inspect and live-edit the DOM tree and CSS styles), Network (view network requests and responses), Resources (view LocalStorage, Cookies, and other data), Sources, and more.
-   **Instantly available**: once enabled, a floating gear icon appears at the bottom right of the page; tap it to expand the full console panel. Turning off the developer tools switch in the menu fully closes it and removes the floating ball.
-   **Desktop debugging integration**: when enabled, Chromium's `WebContentsDebugging` is enabled too, supporting connecting to a computer via USB and visiting `chrome://inspect` in the Chrome browser for desktop-grade remote inspection.

## Dark Mode

The browser has a built-in dark mode that follows the app's appearance by default: when the app is dark the page darkens automatically, and when the app is light it returns to normal. You can manually turn "Dark mode" on or off in the bottom bar's three-bar menu; after a manual choice it no longer follows the app's appearance, and returns to following automatically when switched back to match the app's appearance.

Dark mode uses a dual-track rendering architecture:

-   **Native dark first**: it declares `prefers-color-scheme: dark` and `color-scheme: dark` to the page, and modern sites (such as GitHub, Google, etc.) automatically enable their officially fine-tuned native dark themes, with clear text and dark areas that don't invert to white.
-   **Dimming light-background sites**: for light-background sites without dark styles, a semi-transparent dark mask is layered over the top of the page to dim it overall (keeping the original colors and only reducing brightness, without inverting), for a gentle look. The mask applies to page rendering, so an AI screenshot will likewise show the dimmed colors.
-   **Anti white-flash**: at page startup the dark viewport background is set immediately and dark preferences are injected in advance, avoiding a white-screen flash at the instant of a page refresh.

## Browser Identification (User-Agent)

In the "Browser identification (User-Agent)" area of the bottom bar's three-bar menu, you can switch the device environment for web access in one tap, and the current page refreshes automatically to apply the change:

-   **System default**: uses the native WebView identification of the current device.
-   **Android phone**: simulates an Android Chrome Mobile environment.
-   **Android tablet**: simulates an Android Chrome Tablet large-screen environment.
-   **Desktop**: simulates a Windows PC desktop Chrome browser and forces requesting desktop web pages.
-   **Apple iPhone**: simulates an iOS Mobile Safari environment.
-   **Apple iPad**: simulates an iPadOS Tablet Safari environment.
-   **Custom**: manually enter any custom User-Agent string.

## AI Automation

The AI can control the browser via the `browser` tool to perform the following actions:

Action

Description

`navigate`

Navigate to the specified URL (supports `http(s)`; local files support `file://` or container paths; input that doesn't look like a URL is searched with the built-in search engine Bing by keyword), waiting for the page to finish loading

`evaluate`

Execute arbitrary JavaScript (supports Promise/async, returns native JSON)

`click`

Click an element (full event chain, compatible with React/Vue)

`fill`

Fill form fields (native setter + React valueTracker hack)

`select`

Select a native dropdown `` (by value or visible text)

`hover`

Hover an element (dispatches mouseenter/over/move, can expand dropdown menus)

`press`

Press a key (Enter/Escape/Tab/arrow keys/Backspace/Delete/Space/ordinary characters)

`getText`

Extract page text (a selector can be specified), with script/style filtered out

`getHtml`

Extract page HTML (a selector can be specified)

`getBackbone`

Extract the accessibility tree (role/name/ref, `maxDepth` can be specified); refs can be used directly for subsequent actions

`screenshot`

Capture the current page and return the image for a vision model to analyze (supports background off-screen screenshots)

`console`

Get the page console logs (can filter by level, can clear)

`wait`

Wait for a condition to be met (`text=` / `text*=` / `selector=` / `domStable`, with an optional `timeout`)

`scroll`

Scroll the page (scroll to a specified element or to the bottom)

`dialog`

Handle a pending `confirm`/`prompt` dialog (accept or dismiss); when the browser panel is visible, the user handles it in the UI

`back` / `forward` / `reload`

Browser navigation control; back/forward wait for the navigation to complete

`clearCache`

Clear the browser's HTTP cache, cookies, and site data (including login states), then refresh the current page

How `alert`/`confirm`/`prompt` pop-ups are handled depends on whether the browser panel is open:

-   **Panel is open**: a "Web alert / Web confirm / Web prompt" dialog pops up inside the panel, and you directly choose OK or Cancel (the title already indicates it comes from the web page). The AI doesn't answer on your behalf here and it needs you to handle it.
-   **Panel not open (AI operating in the background)**: `confirm`/`prompt` are suspended, a `pendingDialog` field appears in the tool response, and the AI uses the `dialog` action to accept or dismiss it (auto-dismissed after 30 seconds unhandled); `alert` is confirmed directly and doesn't block.

**Note**: when the browser panel isn't open, a web page can't pop up any native UI (dropdown lists, dialogs, etc.) — this is intentional, to avoid a background page disguising pop-ups as the app's own interface.

## Selector Formats

The selectors of `click`, `fill`, `hover`, `getText`, `getHtml`, `scroll`, and `wait` support:

-   `ref=e22`: an element reference returned by `getBackbone`, locating an element in the snapshot directly
-   CSS: `#id`, `.class`, `a[href=...]`
-   `text=登录`: exact match on element text
-   `text*=登录`: contains match
-   `role=button[name="登录"]`: match by role and name
-   `xpath=//a[@href]`: an XPath expression

`getBackbone` returns the accessibility tree: each node looks like `{role,name,ref,url,value,children}`, where `role` is the accessible role derived from the tag / `role` attribute (`link`/`button`/`textbox`/`heading`/`navigation`…), `name` is the accessible name (`aria-label`/`aria-labelledby`/`alt`/`placeholder`/associated `label`/content text), and `ref` is only assigned to interactive elements. `script`/`style` and invisible elements are filtered out, and nodes beyond `maxDepth` are marked with `truncated:true`.

## What to Do About Operation Timeouts

When the AI opens a web page it waits for the page to finish loading, and if it still isn't done after more than 30 seconds it reports an operation timeout. This doesn't mean the browser isn't initialized: first open the browser panel to check whether the page is already displayed, then check the network, proxy, or login state, and only after confirming let the AI continue operating. If you only need the page's body text or static HTML, you can have the AI use web fetching instead of waiting for the browser to render.

When you stop the AI yourself, any in-progress browser tool call is cancelled too, and the stop won't be misreported as the browser not being ready.

## Search Providers (websearch tool)

The AI's `websearch` tool uses the search service that is **currently selected** in "Settings → Search providers" (the highlighted row in the list; `websearch` queries use that service).

**Built-in services**: Bing (default), Zhipu, Doubao, Tavily, Exa, SearXNG, LinkUp, Brave, Metaso, Ollama, Perplexity, Firecrawl, Jina, Bocha, Grok, Tinyfish, Serper, DeepSeek.

Actions on the management page:

-   **Add a service**: use the "＋" at the top right to pick one from the service list, then fill in that service's configuration items (most need an API Key, and some offer a custom address, model, search engine, language, search depth, etc.).
-   **Select**: tap a service row to make it the service in use; the selected row is highlighted.
-   **Reorder**: long-press a service row and drag to adjust the order.
-   **Delete**: swipe left on a service row to delete it (at least one must be kept).
-   **Configure**: tap a service row to enter its details, where you can set the API Key, custom address, search depth, number of results, etc., and use "Test" to verify connectivity.
-   **Number of results**: the number of results returned per search (1-20).

Each service has different configuration items, and a service without its key filled in can't be used. For the difference from `webfetch` (fetching web pages) and `browser` (rendering web pages), see below.

## Difference from websearch / webfetch

-   **websearch**: search engine queries, retrieving search result snippets.
-   **webfetch**: fetches a web page's HTTP content (plain text or HTML), without JS rendering.
-   **browser**: full WebView rendering, supporting JS-driven dynamic pages, interactive operations, and screenshot analysis.

When a page's content depends on JavaScript rendering, requires logging in before access, or needs interactive operations like clicking / filling forms, use the `browser` tool.
