package com.aiverse.feature.plugins.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.plugins.domain.newmodel.PluginPage
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.PathHomeResolver
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 插件页 WebView 的统一创建入口。
 *
 * 设置页面板与进入设置时的预热共用同一套配置，避免两处各写一份导致行为不一致。
 * 页面资源只经 [WebViewAssetLoader] 的虚拟域加载：`/plugin/` 映射插件安装目录，
 * `/__aicode/` 提供宿主的 `aicode.js` 与动态主题变量，`/workspace-file/` 只读映射当前工作区树
 * （`~/workspace/…` 与 `~/.aiverse/…`，即生图工作台的 attachments / generated-images），
 * 不暴露工作区之外的本地文件系统。
 *
 * HTML 响应附带 CSP，禁止远程脚本、iframe 与 object；资源字节走进程内缓存减少重复读盘，
 * 但响应头保持 `Cache-Control: no-store`，插件覆盖与 APK 更新后 WebView 不会继续用旧内容。
 *
 * 由 AiCode（`com.aicode.feature.plugin.presentation.PluginWebViewFactory`）移植。
 *
 * 降级说明：AiVerse 当前不内置 `plugin-ui/`（`aicode.js`、`vue.global.prod.js` 等）宿主 UI 资产，
 * [hostUiAssetsAvailable] 为 false 时 [`__aicode/`] 路径不提供这些资产（[HostUiAssetHandler] 返回 null），
 * 页面贡献由 [PluginWebPanelScreen] 给出明确的不支持提示。
 */
object PluginWebViewFactory {

    const val PAGE_BASE_URL = "https://appassets.androidplatform.net/plugin/"

    fun pageUrl(page: PluginPage): String {
        val version = page.pluginVersion.takeIf { it.isNotBlank() }?.let { "?v=$it" }.orEmpty()
        return PAGE_BASE_URL + page.pageFile + version
    }

    /**
     * 页面可用的只读工作区文件来源；供 `/workspace-file/` 虚拟路径解析为可读本地文件。
     * 实现负责把工作区容器路径映射到宿主本地文件并**强制工作区包含关系**（含 canonical 前缀与符号链接逃逸校验），
     * 解析不出本地文件（远程降级）或越出工作区时返回 null。
     * 没有可用工作区后端（如页面浮层）时传 [EmptyWorkspaceFiles]，该路径一律不提供内容。
     */
    interface WorkspaceFiles {
        /** 把工作区容器路径（`~/…` 或裸相对路径）解析为可读本地文件；不在工作区内或不可读时返回 null。 */
        fun resolveFile(workspacePath: String): File?
    }

    object EmptyWorkspaceFiles : WorkspaceFiles {
        override fun resolveFile(workspacePath: String): File? = null
    }

    /**
     * 页面浮层：透明 WebView，禁用滚动条与外部导航，页面资源仍只走虚拟域。
     */
    fun createOverlay(
        context: Context,
        page: PluginPage,
        onPageFinished: (WebView) -> Unit,
        onMainFrameError: () -> Unit
    ): WebView = create(
        context = context,
        page = page,
        dark = false,
        backgroundColor = android.graphics.Color.TRANSPARENT,
        themeVariables = { emptyMap() },
        workspaceFiles = EmptyWorkspaceFiles,
        onPageFinished = onPageFinished,
        onMainFrameError = onMainFrameError,
        onFileChooserRequest = null
    ).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        settings.domStorageEnabled = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = android.view.View.OVER_SCROLL_NEVER
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
    }

    /**
     * 应用内登录页 WebView（`webview.login`）：与 [`create()`] 完全独立。
     *
     * 与插件页的关键差异：不经过 [WebViewAssetLoader] 虚拟域、不加 CSP、不阻断外链——
     * 这里要加载远程 https 登录页并跟随 OAuth 跳转。
     *
     * 注意：仅在构造后拿 `Profile.cookieManager` 读 Cookie 是不够的；WebView 本身默认仍跑在
     * 默认 profile 上，页面请求与脚本写入的 Cookie 会落在默认 jar，和登录侧读的 profile 对不上。
     * 这里必须先 [WebViewCompat.setProfile] 把 WebView 绑到独立 profile，再对同一个 CookieManager
     * 打开第三方 Cookie，纯 CSR 登录页的 CDN 子资源与风控脚本才有会话可用。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun createLoginWebView(
        context: Context,
        profile: Profile?,
        onCookieManagerResolved: (CookieManager) -> Unit,
        onNavigation: (String?) -> Unit,
        onMainFrameError: () -> Unit,
        onLoadingChanged: (Boolean) -> Unit
    ): WebView {
        return WebView(context).apply {
            val boundProfile = profile?.let { target ->
                runCatching { WebViewCompat.setProfile(this, target.name) }
                    .onFailure { FileLogger.w("PluginWebViewFactory", "登录 WebView 绑定独立 profile 失败: ${it.message}") }
                    .isSuccess
            } == true
            val cookieManager = if (boundProfile && profile != null) {
                profile.cookieManager
            } else {
                CookieManager.getInstance()
            }
            cookieManager.setAcceptCookie(true)
            // 部分登录页的静态资源与风控 SDK 走第三方域，默认拦截会让纯 CSR 页面只剩空壳。
            cookieManager.setAcceptThirdPartyCookies(this, true)
            onCookieManagerResolved(cookieManager)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(android.graphics.Color.WHITE)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    onLoadingChanged(newProgress < 100)
                }
            }
            webViewClient = object : WebViewClient() {
                // 放行导航：登录页要能跳转、能走 OAuth，不阻断外链。
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = false

                override fun onPageFinished(view: WebView, url: String?) {
                    onLoadingChanged(false)
                    onNavigation(url ?: view.url)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    // SPA 登录常靠 history.pushState 换地址，onPageFinished 不触发，这里补判。
                    onNavigation(url ?: view.url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) onMainFrameError()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        page: PluginPage,
        dark: Boolean,
        backgroundColor: Int,
        themeVariables: () -> Map<String, String>,
        workspaceFiles: WorkspaceFiles,
        onPageFinished: (WebView) -> Unit,
        onMainFrameError: () -> Unit,
        onFileChooserRequest: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit)? = null
    ): WebView {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/plugin/", PluginPageAssetHandler(page.installDir.canonicalFile))
            .addPathHandler("/__aicode/", HostUiAssetHandler(context) { themeVariables() })
            .addPathHandler("/workspace-file/", WorkspaceAssetHandler(workspaceFiles))
            .build()

        return WebView(ContextThemeWrapper(context, webViewThemeRes(dark))).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            setBackgroundColor(backgroundColor)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    FileLogger.d(
                        "PluginWebView",
                        "[${page.pluginId}] ${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                    )
                    return true
                }

                // 网页 `<input type=file>`：转发给宿主选择器（UI 线程，valueCallback 拿到结果后必须调用一次，
                // 成功传 uri 数组、取消传 null）；宿主未接入选择器（onFileChooserRequest 为空实现）时返回 false，
                // 交回 WebView 默认行为。
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    val forward = onFileChooserRequest ?: return false
                    return try {
                        forward(filePathCallback, fileChooserParams)
                        true
                    } catch (e: Exception) {
                        FileLogger.e("PluginWebView", "[${page.pluginId}] onShowFileChooser failed", e)
                        filePathCallback.onReceiveValue(null)
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = true

                override fun onPageFinished(view: WebView, url: String?) {
                    onPageFinished(view)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) onMainFrameError()
                }
            }
        }
    }

    fun webViewThemeRes(dark: Boolean): Int =
        if (dark) R.style.Theme_AICode_WebView_Dark else R.style.Theme_AICode_WebView

    private class PluginPageAssetHandler(private val root: File) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val file = File(root, path).canonicalFile
            val prefix = root.path + File.separator
            if (file != root && !file.path.startsWith(prefix)) return null
            if (!file.isFile) return null
            val bytes = cachedBytes(file) ?: return null
            return webResource(mimeOf(path), bytes)
        }

        private fun cachedBytes(file: File): ByteArray? {
            // 键含 canonical path + mtime + size：插件同版本覆盖文件会换键，不会读到旧字节。
            val key = "plugin:${file.path}:${file.lastModified()}:${file.length()}"
            ResourceCache.get(key)?.let { return it }
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            ResourceCache.put(key, bytes)
            return bytes
        }
    }

    /**
     * 只读映射当前工作区树（`~/workspace/…`、`~/.aiverse/…`）到虚拟域 `/workspace-file/`。
     *
     * 防穿越：拒绝空段 / `.` / `..` / 反斜杠 / 控制字符；包含关系（canonical 前缀 + 符号链接逃逸）
     * 由 [WorkspaceFiles] 实现强制。只提供读；字节走 [ResourceCache]（键含 mtime + size，
     * 工作区文件被覆盖即换键）。
     */
    private class WorkspaceAssetHandler(
        private val workspaceFiles: WorkspaceFiles
    ) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            if (path.isBlank() || path.contains('\\') || path.any { it.isISOControl() }) return null
            if (path.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
            val containerPath = path.trim('/').ifEmpty { "~" }
            // 回传的选中文件是宿主绝对路径（已是 copyToLocal 落盘的可信产物，直接用本地 File，
            // 不再走容器映射——远程模式对绝对路径再 copyToLocal 会误当容器路径走 SFTP）；既有图片
            // 引用是 `~/…` 容器路径，仍交 resolveFile 做工作区包含校验。
            val resolved = if (containerPath.startsWith("/")) {
                File(containerPath).takeIf { it.isFile }
            } else {
                workspaceFiles.resolveFile("~/$containerPath")?.takeIf { it.isFile }
            } ?: return null
            val canonical = runCatching { resolved.canonicalFile }.getOrNull() ?: return null
            val bytes = cachedBytes(canonical) ?: return null
            return webResource(mimeOf(path), bytes)
        }

        private fun cachedBytes(file: File): ByteArray? {
            // 键含 canonical path + mtime + size：工作区文件被覆盖会换键，不会读到旧字节。
            val key = "workspace:${file.path}:${file.lastModified()}:${file.length()}"
            ResourceCache.get(key)?.let { return it }
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            ResourceCache.put(key, bytes)
            return bytes
        }
    }

    private class HostUiAssetHandler(
        private val context: Context,
        private val variables: () -> Map<String, String>
    ) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            if (path == "theme.css") {
                // 跟随主题动态生成，不进程内缓存，也不让 WebView 复用旧副本。
                val css = PluginThemeTokens.css(variables())
                return WebResourceResponse(
                    "text/css",
                    "utf-8",
                    ByteArrayInputStream(css.toByteArray(Charsets.UTF_8))
                ).apply {
                    setResponseHeaders(mapOf("Cache-Control" to "no-store"))
                }
            }
            // 宿主 UI 资产（aicode.js / vue.global.prod.js 等）由 AiVerse 内置；当前未内置。
            // TODO(P3b): AiVerse 尚未内置 plugin-ui/ 资产。宿主接入 aicode.js 并随 APK 分发后，
            //  打开下面的 plugin-ui 读取逻辑；在此之前页面贡献由 PluginWebPanelScreen 降级提示。
            if (path.isBlank() || path.startsWith("/") || path.split('/').any { it == ".." || it == "." }) {
                return null
            }
            val bytes = cachedBytes(path) ?: return null
            return webResource(mimeOf(path), bytes)
        }

        private fun cachedBytes(path: String): ByteArray? {
            // 宿主资源随 APK 固定，进程内不会变化，键只用路径。
            val key = "host:$path"
            ResourceCache.get(key)?.let { return it }
            val bytes = runCatching {
                context.assets.open("plugin-ui/$path").use { it.readBytes() }
            }.getOrNull() ?: return null
            ResourceCache.put(key, bytes)
            return bytes
        }
    }

    /**
     * 插件页资源禁用浏览器缓存：APK 更新后 WebView 不能继续用旧版 aicode.js。
     * 内存缓存只省掉重复读盘，不改变这条响应头。
     */
    private fun webResource(mime: String, bytes: ByteArray): WebResourceResponse =
        WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes)).apply {
            setResponseHeaders(
                buildMap {
                    put("Cache-Control", "no-store")
                    contentSecurityPolicyForMime(mime)?.let { put("Content-Security-Policy", it) }
                }
            )
        }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    /**
     * 进程内资源缓存，只用于避免每次 shouldInterceptRequest 都读磁盘。
     *
     * LRU 上限约 [MAX_ENTRIES] 项 / [MAX_BYTES] 字节：插件资源键含 mtime 与 size，
     * 宿主资源键只用路径。`theme.css` 是动态生成内容，不经过这里。
     */
    private object ResourceCache {
        private const val MAX_ENTRIES = 64
        private const val MAX_BYTES = 8 * 1024 * 1024

        private class Entry(val bytes: ByteArray)

        private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
        private var totalBytes = 0

        @Synchronized
        fun get(key: String): ByteArray? = entries[key]?.bytes

        @Synchronized
        fun put(key: String, bytes: ByteArray) {
            if (bytes.size > MAX_BYTES) return
            entries.remove(key)?.let { totalBytes -= it.bytes.size }
            entries[key] = Entry(bytes)
            totalBytes += bytes.size
            val iterator = entries.entries.iterator()
            while ((entries.size > MAX_ENTRIES || totalBytes > MAX_BYTES) && iterator.hasNext()) {
                val entry = iterator.next()
                totalBytes -= entry.value.bytes.size
                iterator.remove()
            }
        }
    }
}

/** 插件页 HTML 的 CSP：脚本只允许包内与内联，远程脚本、iframe、object 全部阻断。 */
internal const val HTML_CONTENT_SECURITY_POLICY: String =
    "default-src 'self' appassets.androidplatform.net data: blob:; " +
        "script-src 'self' appassets.androidplatform.net 'unsafe-inline' 'unsafe-eval'; " +
        "style-src 'self' appassets.androidplatform.net 'unsafe-inline' https: data:; " +
        "img-src 'self' appassets.androidplatform.net data: blob: https:; " +
        "font-src 'self' appassets.androidplatform.net data: https:; " +
        "media-src 'self' appassets.androidplatform.net data: blob: https:; " +
        "connect-src 'self' appassets.androidplatform.net http: https:; " +
        "frame-src 'none'; " +
        "object-src 'none'; " +
        "base-uri 'none'; " +
        "frame-ancestors 'none'"

/** 只有 HTML 需要 CSP；CSS/JS/图片等子资源不加，避免多余响应头。 */
internal fun contentSecurityPolicyForMime(mime: String): String? =
    if (mime == "text/html") HTML_CONTENT_SECURITY_POLICY else null

/**
 * [PluginWebViewFactory.WorkspaceFiles] 的默认实现：把工作区容器路径映射到宿主本地文件，
 * 并强制结果落在当前工作区根内（canonical 前缀 + 符号链接逃逸校验）。
 *
 * 本地模式（[FileAccessProvider.copyToLocal] 只做路径映射）下严格包含校验；
 * 远程 SSH 模式 `copyToLocal` 会把文件下载到 App cache，落点不在工作区树内，
 * 此时降级为信任后端（返回缓存副本），仍只读，且只能读到工作区树内存在的文件。
 */
internal class PluginWorkspaceFiles(
    private val fileAccess: FileAccessProvider,
    private val homeResolver: PathHomeResolver,
    private val workspacePath: () -> String
) : PluginWebViewFactory.WorkspaceFiles {

    private val rootFile by lazy { runCatching { fileAccess.copyToLocal(workspacePath()) }.getOrNull() }
    private val rootCanonical by lazy { rootFile?.let { runCatching { it.canonicalFile }.getOrNull() } }

    override fun resolveFile(workspacePath: String): File? {
        val root = rootFile ?: return null
        val expanded = homeResolver.expandHome(workspacePath.trim()).ifEmpty { workspacePath() }
        val file = runCatching { fileAccess.copyToLocal(expanded) }.getOrNull() ?: return null
        if (!file.isFile) return null
        val rootCanonical = rootCanonical ?: return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val prefix = rootCanonical.path + File.separator
        if (canonical == rootCanonical || canonical.path.startsWith(prefix)) return canonical
        // 远程模式降级：copyToLocal 的缓存副本不在工作区前缀下，只要后端确认文件存在即可读。
        return if (runCatching { fileAccess.isFile(expanded) }.getOrDefault(false)) file else null
    }
}