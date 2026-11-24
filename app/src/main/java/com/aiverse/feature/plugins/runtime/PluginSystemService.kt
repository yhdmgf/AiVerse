package com.aiverse.feature.plugins.runtime

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.plugins.domain.PluginHostException
import com.aiverse.feature.plugins.domain.PluginHostPaths
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.overlay.AppForegroundMonitor
import com.aiverse.feature.plugins.presentation.PluginLoginActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.net.URISyntaxException
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 插件可用的宿主系统能力：剪贴板、系统分享、打开链接、宿主 App 信息与日志。
 *
 * 每个入口都以插件启用为前提（停用/损坏的插件不能借用宿主身份操作系统），参数校验在切主线程之前完成，
 * 失败统一抛 [PluginHostException]，code 稳定：`PLUGIN_INACTIVE`、`PARAM_INVALID`、`TOO_LARGE`、
 * `NO_ACTIVITY`、`SERVICE_UNAVAILABLE`、`APP_INFO_UNAVAILABLE`。
 *
 * 剪贴板与 Intent 都在主线程执行：剪贴板服务与 `startActivity` 都面向 UI，放主线程避免跨线程的时序问题。
 */
@Singleton
class PluginSystemService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val appForegroundMonitor: AppForegroundMonitor
) {

    /** 读取剪贴板第一条文本；剪贴板为空（或内容不是文本）时返回空串。 */
    suspend fun clipboardRead(pluginId: String): JsonElement {
        ensureActive(pluginId)
        return withContext(Dispatchers.Main) {
            val clip = clipboard().primaryClip
            val text = if (clip == null || clip.itemCount == 0) {
                ""
            } else {
                clip.getItemAt(0).text?.toString().orEmpty()
            }
            if (text.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES) {
                throw PluginHostException("TOO_LARGE", "clipboard text too large")
            }
            buildJsonObject { put("text", text) }
        }
    }

    /** 写入剪贴板。即使在后台也允许写。 */
    suspend fun clipboardWrite(pluginId: String, text: String): JsonElement {
        ensureActive(pluginId)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES) {
            throw PluginHostException("TOO_LARGE", "clipboard text too large")
        }
        return withContext(Dispatchers.Main) {
            clipboard().setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
            buildJsonObject { put("ok", true) }
        }
    }

    /** 拉起系统分享面板；[title] 非空时同时用作选择器标题与分享主题。 */
    suspend fun shareText(pluginId: String, text: String, title: String? = null): JsonElement {
        ensureActive(pluginId)
        if (text.length > MAX_SHARE_CHARS) {
            throw PluginHostException("TOO_LARGE", "share text too long")
        }
        val subject = title?.trim()?.takeIf { it.isNotEmpty() }
        withContext(Dispatchers.Main) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                },
                subject
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                throw PluginHostException("NO_ACTIVITY", "no activity to handle share")
            }
        }
        return ok()
    }

    /**
     * 拉起系统分享面板分享一张工作区图片；[file] 为已解析的本地文件（工作区内或远程下载的缓存副本）。
     *
     * 与 [shareText] 同一套规则：插件须启用、主线程 `startActivity`、无接收方抛 `NO_ACTIVITY`。
     * 通过 [FileProvider] 授权只读 URI（工作区在 `filesDir` 下，已被 `file_paths.xml` 的
     * `files-path` 覆盖；远程缓存副本落 `cache-path`）。大小上限为生成图量级（[MAX_SHARE_IMAGE_BYTES]）。
     */
    suspend fun shareImage(pluginId: String, file: File, title: String? = null): JsonElement {
        ensureActive(pluginId)
        if (!file.isFile) throw PluginHostException("NOT_FOUND", "not a file: ${file.path}")
        if (file.length() > MAX_SHARE_IMAGE_BYTES) {
            throw PluginHostException("TOO_LARGE", "share image too large")
        }
        return shareWithStream(file, title, shareImageMime(file.name), CLIP_IMAGE_LABEL)
    }

    /**
     * 拉起系统分享面板分享一个工作区文件；[file] 为已解析的本地文件（工作区内或远程下载的缓存副本）。
     *
     * 与 [shareImage] 共用 [shareWithStream] 链路，仅 mime 交给 [shareFileMime] 按扩展名推断
     * （识别不出退回 `application/octet-stream`）。大小兜底沿用 [MAX_SHARE_IMAGE_BYTES]。
     */
    suspend fun shareFile(pluginId: String, file: File, title: String? = null): JsonElement {
        ensureActive(pluginId)
        if (!file.isFile) throw PluginHostException("NOT_FOUND", "not a file: ${file.path}")
        if (file.length() > MAX_SHARE_IMAGE_BYTES) {
            throw PluginHostException("TOO_LARGE", "share file too large")
        }
        return shareWithStream(file, title, shareFileMime(file.name), CLIP_FILE_LABEL)
    }

    /**
     * ACTION_SEND + FileProvider 的共享主体；[mime] 与 [clipLabel] 由调用方按图片/通用文件各自传入。
     * 主线程 `startActivity`，无接收方抛 `NO_ACTIVITY`；`subject` 为空则不附带标题。
     */
    private suspend fun shareWithStream(file: File, title: String?, mime: String, clipLabel: String): JsonElement {
        val subject = title?.trim()?.takeIf { it.isNotEmpty() }
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(clipLabel, uri)
                    if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                subject
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                throw PluginHostException("NO_ACTIVITY", "no activity to handle share")
            }
        }
        return ok()
    }

    /** 用系统浏览器或其他已注册的 App 打开 http/https 链接。 */
    suspend fun openUrl(pluginId: String, url: String): JsonElement {
        ensureActive(pluginId)
        val target = requireHttpUrl(url)
        withContext(Dispatchers.Main) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(target))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                throw PluginHostException("NO_ACTIVITY", "no activity to open url")
            }
        }
        return ok()
    }

    /**
     * 打开受控登录 WebView 完成第三方网页登录，回传 `{ url, finalUrl, cookies }`；用户取消时抛 `LOGIN_CANCELED`。
     *
     * 为兼容两类调用方，同时接受两套参数风格：
     * - Nox / 主进程风格：`url`（登录页）、`cookieUrls`（数组，取首个 host 作为登录完成的目标域）、
     *   `title`/`finishText`/`accentColor`/`showClose`（后者在本宿主不支持，忽略）。
     * - AiVerse / 配置页风格：`loginUrl`（登录页）、`targetDomain`（登录完成的目标域）、`cookieName`、`timeoutMs`。
     *
     * 登录完成的判定复用 [PluginLoginActivity]：导航到 `targetDomain`（或其子域）后读取已过滤 Cookie 回传。
     */
    suspend fun openLoginWebView(pluginId: String, params: JsonObject): JsonElement {
        ensureActive(pluginId)
        // 1) 登录页地址：兼容 url / loginUrl
        val loginUrl = (params["url"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: PluginHostPaths.requireString(params, "loginUrl").trim()
        requireHttpUrl(loginUrl)
        // 2) 目标域：优先 cookieUrls 首个 host（Nox 风格），否则 targetDomain（AiVerse 风格），
        //    都没有则退回 loginUrl 自身 host。
        val targetDomain = (params["cookieUrls"] as? JsonArray)?.firstOrNull()?.let {
            runCatching { URI((it as? JsonPrimitive)?.contentOrNull.orEmpty()).host?.lowercase() }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
            ?: (params["targetDomain"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: runCatching { URI(loginUrl).host?.lowercase() }.getOrNull()
            ?: throw PluginHostException("PARAM_INVALID", "cannot derive targetDomain")
        if (!LOGIN_DOMAIN.matches(targetDomain)) {
            throw PluginHostException("PARAM_INVALID", "invalid targetDomain")
        }
        val loginHost = runCatching { URI(loginUrl).host?.lowercase() }.getOrNull()
        if (loginHost == null || !(loginHost == targetDomain || loginHost.endsWith(".$targetDomain"))) {
            throw PluginHostException("PARAM_INVALID", "loginUrl host does not match targetDomain")
        }
        val cookieName = (params["cookieName"] as? JsonPrimitive)?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
        val timeoutMs = params["timeoutMs"]?.let {
            (it as? JsonPrimitive)?.longOrNull
                ?: throw PluginHostException("PARAM_INVALID", "timeoutMs must be a number")
        }?.coerceIn(MIN_LOGIN_TIMEOUT_MS, MAX_LOGIN_TIMEOUT_MS) ?: DEFAULT_LOGIN_TIMEOUT_MS

        // 同一时间只允许一个登录：requestId 由宿主生成并登记，Activity 回填与收尾注销靠它对齐。
        val loginRequestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<PluginLoginActivity.Result>()
        if (PluginLoginActivity.register(loginRequestId, deferred) != null) {
            throw PluginHostException("REQUEST_EXISTS", "a webview login is already in progress")
        }
        try {
            withContext(Dispatchers.Main) {
                val intent = Intent(context, PluginLoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(PluginLoginActivity.EXTRA_REQUEST_ID, loginRequestId)
                    .putExtra(PluginLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                    .putExtra(PluginLoginActivity.EXTRA_TARGET_DOMAIN, targetDomain)
                    .putExtra(PluginLoginActivity.EXTRA_PLUGIN_NAME, resolvePluginName(pluginId))
                cookieName?.let { intent.putExtra(PluginLoginActivity.EXTRA_COOKIE_NAME, it) }
                context.startActivity(intent)
            }
            val outcome = withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: throw PluginHostException("TIMEOUT", "webview login timed out")
            return when (outcome) {
                is PluginLoginActivity.Result.Success -> buildJsonObject {
                    put("url", outcome.url)
                    // finalUrl 别名：Nox 风格调用方读 finalUrl，配置页读 url，两者都拿到。
                    put("finalUrl", outcome.url)
                    put("cookies", outcome.cookies)
                }
                is PluginLoginActivity.Result.Failure ->
                    throw PluginHostException(outcome.code, outcome.reason)
            }
        } finally {
            PluginLoginActivity.unregister(loginRequestId)
        }
    }

    /** 登录确认弹窗展示用插件名：取清单本地化名（按系统 locale 解析），取不到回退 pluginId。 */
    private fun resolvePluginName(pluginId: String): String {
        val localeTag = runCatching {
            context.resources.configuration.locales[0].toLanguageTag()
        }.getOrNull().orEmpty()
        val plugin = pluginManager.plugins.value.firstOrNull { it.id == pluginId }
        return plugin?.manifest?.name?.resolve(localeTag)
            ?.takeIf { it.isNotBlank() } ?: pluginId
    }

    /** 宿主 App 的包名、版本、系统 API 级别、界面语言与前后台状态。 */
    suspend fun appInfo(pluginId: String): JsonElement {
        ensureActive(pluginId)
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw PluginHostException("APP_INFO_UNAVAILABLE", "cannot read host package info")
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return buildJsonObject {
            put("packageName", context.packageName)
            put("versionName", info.versionName.orEmpty())
            put("versionCode", versionCode)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("languageTag", Locale.getDefault().toLanguageTag())
            put("isForeground", appForegroundMonitor.isForeground.value)
        }
    }

    /** 写一行宿主日志；[tag] 缺省为 `plugin:<pluginId>`，便于按插件过滤。 */
    suspend fun log(pluginId: String, level: String, message: String, tag: String? = null): JsonElement {
        ensureActive(pluginId)
        if (message.length > MAX_LOG_MESSAGE_CHARS) {
            throw PluginHostException("TOO_LARGE", "log message too long")
        }
        val logTag = when {
            tag.isNullOrEmpty() -> DEFAULT_TAG_PREFIX + pluginId
            tag.length > MAX_LOG_TAG_CHARS -> throw PluginHostException("PARAM_INVALID", "log tag too long")
            else -> tag
        }
        when (level) {
            "debug" -> FileLogger.d(logTag, message)
            "info" -> FileLogger.i(logTag, message)
            "warn" -> FileLogger.w(logTag, message)
            "error" -> FileLogger.e(logTag, message)
            else -> throw PluginHostException("PARAM_INVALID", "unknown log level: $level")
        }
        return ok()
    }

    private fun ensureActive(pluginId: String) {
        pluginManager.ensureLoaded()
        pluginManager.plugins.value.firstOrNull { it.id == pluginId && it.isActive }
            ?: throw PluginHostException("PLUGIN_INACTIVE", "plugin is not active: $pluginId")
    }

    private fun clipboard(): ClipboardManager =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?: throw PluginHostException("SERVICE_UNAVAILABLE", "clipboard service unavailable")

    /** 只放行 http/https；URI 解析失败、缺 scheme 或没有 authority 都算参数非法。 */
    private fun requireHttpUrl(url: String): String {
        if (url.length > MAX_URL_CHARS) throw PluginHostException("TOO_LARGE", "url too long")
        val parsed = try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw PluginHostException("PARAM_INVALID", "invalid url")
        }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            throw PluginHostException("PARAM_INVALID", "only http/https urls are allowed")
        }
        if (parsed.rawAuthority.isNullOrEmpty()) {
            throw PluginHostException("PARAM_INVALID", "url has no host")
        }
        return url
    }

    private fun ok(): JsonElement = buildJsonObject { put("ok", true) }

    /** 按扩展名给精确图片 mime；识别不出退回通配的 image 斜杠星，让接收方自行决定。 */
    private fun shareImageMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "image/*"
    }

    /** 按扩展名给精确 mime；识别不出退回 `application/octet-stream`，让接收方自行决定。 */
    private fun shareFileMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }

    private companion object {
        const val CLIP_LABEL = "text"
        const val CLIP_IMAGE_LABEL = "image"
        const val CLIP_FILE_LABEL = "file"
        const val MAX_CLIPBOARD_BYTES = 1024 * 1024
        const val MAX_SHARE_CHARS = 100_000
        const val MAX_SHARE_IMAGE_BYTES = 20L * 1024 * 1024
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val MAX_URL_CHARS = 4096
        const val MAX_LOG_MESSAGE_CHARS = 2000
        const val MAX_LOG_TAG_CHARS = 64
        const val DEFAULT_TAG_PREFIX = "plugin:"

        /** 登录页目标域合法性：一个或多个点分隔的域名标签。 */
        val LOGIN_DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
        const val DEFAULT_LOGIN_TIMEOUT_MS = 5L * 60 * 1000
        const val MIN_LOGIN_TIMEOUT_MS = 10_000L
        const val MAX_LOGIN_TIMEOUT_MS = 10L * 60 * 1000
    }
}
