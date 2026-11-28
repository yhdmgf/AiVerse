package com.aiverse.feature.plugins.presentation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import compose.icons.feathericons.X
import com.aiverse.R
import com.aiverse.core.theme.AIEditorTheme
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.util.FileLogger
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import compose.icons.FeatherIcons
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 插件应用内登录的承载页（`webview.login`）。
 *
 * 流程：用户在确认弹窗允许 → 加载目标站点登录页（独立 [Profile] 的专属 cookie jar）→
 * 宿主每次导航后判完成条件（当前 URL host 命中 `targetDomain`，可选要求 `cookieName` 出现）
 * → 读取该 URL 的 Cookie（含 HttpOnly，[CookieManager] 不受 HttpOnly 限制）、按 declared
 * domain 二次过滤 → 回传 `{url, cookies}` 给挂起的 [PluginWebBridge.webviewLogin]。
 *
 * 安全边界：
 * - Cookie 仅内存传递：不落盘、不写日志；[FileLogger] 只记 pluginId / 错误码，绝不记 Cookie 值。
 * - resolve/reject 后立即清空 profile Cookie 并删除该 profile，拆掉隔离 jar，用后即清。
 * - 旧设备 `ProfileStore` 不可用时回退默认 `CookieManager`（见 [LoginProfile] 的爆炸半径注释）。
 *
 * 与 [PluginImportActivity] 同构：`@AndroidEntryPoint` + `ComponentActivity` + `AIEditorTheme`。
 * 宿主无关、返回栈干净：系统返回键 / 关闭按钮 → CANCELLED。
 */
@AndroidEntryPoint
class PluginLoginActivity : ComponentActivity() {

    private var requestStage by mutableStateOf(LoginStage.Confirm)
    private var loading by mutableStateOf(true)
    private var loginWebView by mutableStateOf<WebView?>(null)
    private var finished = false

    private val loginUrl by lazy { intent?.getStringExtra(EXTRA_LOGIN_URL).orEmpty() }
    private val targetDomain by lazy { intent?.getStringExtra(EXTRA_TARGET_DOMAIN).orEmpty() }
    private val pluginName by lazy { intent?.getStringExtra(EXTRA_PLUGIN_NAME).orEmpty() }
    private val cookieName by lazy { intent?.getStringExtra(EXTRA_COOKIE_NAME)?.takeIf { it.isNotEmpty() } }
    private val requestId by lazy { intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty() }

    /** 登录页专用 cookie jar；null 表示旧设备走默认 CookieManager。 */
    private var loginProfile: LoginProfile? = null

    /** 当前登录 WebView 实际使用的 CookieManager，必须和 WebView 所绑定的 profile 一致。 */
    private var loginCookieManager: CookieManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentActivity.set(this)
        if (loginUrl.isBlank() || targetDomain.isBlank() || requestId.isBlank()) {
            FileLogger.w(TAG, "缺少登录参数")
            finish()
            return
        }
        setContent {
            AIEditorTheme {
                when (requestStage) {
                    LoginStage.Confirm -> ConfirmScreen(
                        pluginName = pluginName,
                        domain = targetDomain,
                        onAllow = ::startLogin,
                        onCancel = { cancel() }
                    )
                    LoginStage.Web -> LoginScreen(
                        loading = loading,
                        onClose = { cancel() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        currentActivity.compareAndSet(this, null)
        // 兜底：无论正常 resolve/reject 还是系统回收，离开时都拆掉 WebView 并清 cookie jar。
        cleanupWebView()
        super.onDestroy()
    }

    /** 用户允许后创建登录 WebView 并加载登录页（主线程）。 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin() {
        if (finished) return
        requestStage = LoginStage.Web
        val profile = LoginProfile.create()
        loginProfile = profile
        loginWebView = PluginWebViewFactory.createLoginWebView(
            context = this,
            profile = profile?.platformProfile,
            onCookieManagerResolved = { loginCookieManager = it },
            onNavigation = ::onNavigation,
            onMainFrameError = { reject("INTERNAL", "sign-in page failed to load") },
            onLoadingChanged = { loading = it }
        ).also { it.loadUrl(loginUrl) }
    }

    /**
     * 每次导航后判完成条件：当前 URL host 命中 targetDomain（或其子域），且可选 cookieName
     * 已出现在该 URL 的 Cookie 中。满足即取 Cookie、二次过滤、resolve。
     */
    private fun onNavigation(url: String?) {
        if (finished || url.isNullOrBlank()) return
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return
        if (!(host == targetDomain || host.endsWith(".$targetDomain"))) return
        val cookieManager = loginCookieManager ?: CookieManager.getInstance()
        val raw = runCatching { cookieManager.getCookie(url) }.getOrNull()
        if (raw.isNullOrBlank()) return
        if (cookieName != null && parseCookies(raw).none { it.first == cookieName }) return
        val cookies = parseFilteredCookies(raw)
        resolve(PluginLoginActivity.Result.Success(url, cookies))
    }

    /**
     * 把 `CookieManager.getCookie(url)` 返回的 `name=value; ...` 串解析成 JSON 对象。
     *
     * 域约束说明：`getCookie(url)` 按 URL 作用域取值，属主域命中 targetDomain 的 cookie 才会
     * 出现在结果里（真正的域约束由 [onNavigation] 的 host 判定保证）。本方法**不再**逐条校验
     * cookie 属主域，只做格式转换并排除可能混入的属性名（Secure / HttpOnly / SameSite），
     * 请勿据本方法名误以为存在一道独立的按域过滤。
     */
    private fun parseFilteredCookies(raw: String) = buildJsonObject {
        parseCookies(raw).forEach { (key, value) ->
            if (!key.equals("Secure", true) && !key.equals("HttpOnly", true) && !key.equals("SameSite", true)) {
                put(key, value)
            }
        }
    }

    private fun parseCookies(raw: String): List<Pair<String, String>> =
        raw.split(';')
            .map { it.trim() }
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
            }

    private fun resolve(result: Result) {
        if (finished) return
        finished = true
        PluginLoginActivity.complete(requestId, result)
        cleanupWebView()
        finish()
    }

    private fun reject(code: String, reason: String) {
        if (finished) return
        FileLogger.w(TAG, "登录未完成: code=$code")
        resolve(Result.Failure(code, reason))
    }

    private fun cancel() {
        if (finished) return
        reject("CANCELLED", "webview login cancelled")
    }

    /**
     * 拆掉登录 WebView 并清空本次登录的隔离 cookie jar。
     *
     * `ProfileStore` 不可用（旧 WebView / 旧设备）时会回退默认 `CookieManager`；这条降级路径
     * 不能 `removeAllCookies(null)`，否则会连带清掉浏览器/其它 WebView 的登录态。只有 WebView
     * 确实绑定了独立 profile 时，才清空并删除该 profile。
     */
    private fun cleanupWebView() {
        loginWebView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
        }
        loginWebView = null
        val profile = loginProfile
        if (profile != null) {
            // 只有 WebView 确实绑上了独立 profile，才清这个隔离 jar；回退默认 jar 时不能 removeAllCookies，
            // 否则会把浏览器/其它 WebView 的登录态一起清掉。
            val profileCookieManager = runCatching { profile.cookieManager }.getOrNull()
            if (profileCookieManager != null && loginCookieManager === profileCookieManager) {
                runCatching { profileCookieManager.removeAllCookies(null) }
                runCatching { profileCookieManager.flush() }
            } else {
                loginCookieManager?.let(::clearCookiesForLogin)
            }
            profile.release()
        } else {
            loginCookieManager?.let(::clearCookiesForLogin)
        }
        loginCookieManager = null
        loginProfile = null
    }

    /** 降级到默认 jar 时，只清登录页与目标域名的 Cookie，不动整个全局 jar。 */
    private fun clearCookiesForLogin(cookieManager: CookieManager) {
        val urls = listOfNotNull(
            loginUrl.takeIf { it.isNotBlank() },
            "https://$targetDomain/"
        )
        urls.forEach { url ->
            val names = runCatching {
                parseCookies(cookieManager.getCookie(url).orEmpty()).map { it.first }
            }.getOrDefault(emptyList())
            names.forEach { name ->
                runCatching { cookieManager.setCookie(url, "$name=; Max-Age=0; Path=/") }
            }
        }
        runCatching { cookieManager.flush() }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun ConfirmScreen(
        pluginName: String,
        domain: String,
        onAllow: () -> Unit,
        onCancel: () -> Unit
    ) {
        AdaptiveModalBottomSheet(
            onDismissRequest = onCancel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.plugin_webview_login_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.plugin_webview_login_confirm_body, pluginName, domain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.plugin_webview_login_confirm_cancel))
                    }
                    Button(
                        onClick = onAllow,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.plugin_webview_login_confirm_allow))
                    }
                }
                // Spacing 由主题 token 提供；此处仅占位以保持底部安全间距。
                Spacer(modifier = Modifier.height(Spacing.xs))
            }
        }
    }

    @Composable
    private fun LoginScreen(
        loading: Boolean,
        onClose: () -> Unit
    ) {
        val t = LocalUIDensityTokens.current
        BackHandler(onBack = onClose)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = FeatherIcons.X,
                        contentDescription = stringResource(R.string.plugin_webview_login_confirm_cancel),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.plugin_webview_login_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(t.touchTarget / 2))
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                loginWebView?.let { view ->
                    AndroidView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    /** 登录结果：成功带最终 URL 与已过滤 Cookie；失败带宿主错误码（不含 Cookie 值）。 */
    sealed interface Result {
        data class Success(val url: String, val cookies: JsonObject) : Result
        data class Failure(val code: String, val reason: String) : Result
    }

    private enum class LoginStage { Confirm, Web }

    companion object {
        private const val TAG = "PluginLoginActivity"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_LOGIN_URL = "loginUrl"
        const val EXTRA_TARGET_DOMAIN = "targetDomain"
        const val EXTRA_PLUGIN_NAME = "pluginName"
        const val EXTRA_COOKIE_NAME = "cookieName"

        /**
         * pending-result 注册表：bridge 用 requestId 挂起等待，Activity 完成后 set 结果。
         * 仅内存持有，不落盘。
         */
        private val pending = ConcurrentHashMap<String, CompletableDeferred<Result>>()
        private val currentActivity = AtomicReference<PluginLoginActivity?>(null)

        /** 注册一个挂起结果；已存在（并发登录）返回非 null。requestId 由调用方（bridge）生成并显式传入。 */
        fun register(requestId: String, deferred: CompletableDeferred<Result>): CompletableDeferred<Result>? =
            pending.putIfAbsent(requestId, deferred)

        /** bridge 收尾（正常/超时/取消/页面关闭）时移除注册项。 */
        fun unregister(id: String) {
            pending.remove(id)
        }

        /** Activity 完成登录后回填结果。 */
        fun complete(id: String, result: Result) {
            pending[id]?.complete(result)
        }

        /**
         * 插件页面关闭（[PluginWebBridge.close]）时的联动：取消挂起结果、结束登录 Activity。
         * Cookie 清理由 Activity onDestroy 的 [cleanupWebView] 完成。
         */
        fun cancelForBridge() {
            val activity = currentActivity.getAndSet(null) ?: return
            // 以 CANCELLED 回填，令仍在 await 的 bridge 协程立刻恢复并走 finally.unregister；
            // 不 clear() 整表，避免抹掉注册项导致 unregister 语义失效。
            pending.forEach { (_, deferred) ->
                deferred.complete(Result.Failure("CANCELLED", "webview login cancelled"))
            }
            runCatching { activity.finish() }
        }
    }
}

/**
 * 登录页专用 cookie jar。`ProfileStore` 提供完全独立于默认 profile（插件页 WebView 所用）的
 * 专属 CookieManager，可读到 HttpOnly Cookie；不可用时回退 null，由调用方走默认 jar
 * （见 [PluginLoginActivity.cleanupWebView] 的降级清理说明）。
 */
internal class LoginProfile private constructor(private val profile: Profile) {
    val platformProfile: Profile get() = profile
    val cookieManager: CookieManager get() = profile.cookieManager

    /** 删除该 profile，拆掉隔离 jar。 */
    fun release() {
        runCatching { ProfileStore.getInstance().deleteProfile(PROFILE_NAME) }
    }

    companion object {
        private const val PROFILE_NAME = "plugin-login"

        /** 取或建独立 profile；WebView/Profile 能力缺失时返回 null（走默认 jar 降级）。 */
        fun create(): LoginProfile? = runCatching {
            // ProfileStore.getOrCreateProfile 需 API 28+ 且当前 WebView 支持 MULTI_PROFILE；
            // 只判 SDK 会把「旧 WebView 跑在新系统」误当成可用，后续 setProfile 失败、jar 对不上。
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return null
            val store = ProfileStore.getInstance()
            val profile = store.getOrCreateProfile(PROFILE_NAME)
            LoginProfile(profile)
        }.getOrNull()
    }
}
