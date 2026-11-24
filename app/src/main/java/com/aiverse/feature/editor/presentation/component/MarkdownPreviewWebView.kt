package com.aiverse.feature.editor.presentation.component

import android.net.Uri
import android.view.ContextThemeWrapper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.editor.presentation.MarkdownPreviewViewModel

/**
 * Markdown / HTML 预览：用 WebView 渲染完整 Markdown（含内嵌 HTML）或原生 HTML，还原 GitHub 观感。
 *
 * [html] 为空时按 Markdown 渲染：原文经 [MarkdownPreviewViewModel.buildHtml] 套进内置模板，
 * 由模板里的 marked + DOMPurify 在页面内解析，故这条路径必须开 JS。
 * [html] 非空时按原生 HTML 渲染：内容直接 load 进 WebView，同样启用 JS——附件 HTML 会执行页面自带脚本
 * （canvas 小游戏、交互页等），禁 JS 会黑屏只剩背景色；附件 HTML 虽不可信，但预览环境已沙箱化：
 * 无 JS 桥、禁文件/内容访问、资源仅经 assetLoader 虚拟 https origin、外链一律拦下，风险与 Markdown 路径同级。
 *
 * 预览页里的本地文件链接会像浏览器一样就地导航过去（Markdown 目标由 [MarkdownPreviewViewModel] 渲染成新预览页）；
 * 返回键优先回退 WebView 历史、到头再退出预览；外部链接不跳转。
 *
 * 与聊天区的 Compose 渲染是两条独立路径，互不影响。
 */
@Composable
fun MarkdownPreviewWebView(
    text: String,
    path: String,
    onExitPreview: () -> Unit,
    modifier: Modifier = Modifier,
    html: String? = null,
    viewModel: MarkdownPreviewViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (dark) BG_DARK else BG_LIGHT
    val exitPreview by rememberUpdatedState(onExitPreview)
    val renderHtml = html != null

    val webView = remember {
        val themedContext = ContextThemeWrapper(context, webViewThemeRes(dark))
        WebView(themedContext).apply {
            // 两条路径都开 JS：Markdown 靠页内 marked+DOMPurify 解析；原生 HTML 需要执行页面自带脚本（canvas/交互页），
            // 禁 JS 会黑屏只剩背景色。附件 HTML 虽不可信，但预览环境已沙箱化：无 JS 桥、禁文件/内容访问、
            // 资源仅经 assetLoader 虚拟 https origin、外链一律拦下，风险与 Markdown 路径同级。
            settings.javaScriptEnabled = true
            // 页面只经 assetLoader 取资源，不暴露本地文件系统
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(bgColor)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    // 预览环境断外网：只放行虚拟 origin（assetLoader）。开 JS 后附件脚本可能主动 fetch/加外部子资源，
                    // 若放行外部 host 会把预览内容外联泄露；这里对外部 host 一律回空 404，与「外链一律拦下」一致。
                    if (request.url.host != MarkdownPreviewViewModel.ASSET_HOST) {
                        FileLogger.w(TAG, "预览拦截外联请求: ${request.url}")
                        return WebResourceResponse(
                            "text/plain", "utf-8", 404, "Not Found", emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    val response = viewModel.assetLoader.shouldInterceptRequest(request.url)
                    FileLogger.v(TAG, "预览资源请求: ${request.url} -> ${if (response == null) "走网络" else "已拦截"}")
                    return response
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    // 页内锚点（#xxx）：仅 fragment 变化、path 与当前页面一致，放行交给 WebView 滚动
                    if (url.fragment != null && url.path == view.url?.let { Uri.parse(it).path }) return false
                    // 指向工作区的文件链接放行，让 WebView 就地导航（Markdown 由 assetLoader 渲染成预览页）
                    if (viewModel.containerPathForUrl(url) != null) return false
                    // 其余（外链等）一律拦下
                    return true
                }
            }
        }
    }

    // WebView 的 isLightTheme 由上下文主题决定，深色切换时同步更新，避免滚动条等控件配色不匹配。
    LaunchedEffect(dark) {
        (webView.context as? ContextThemeWrapper)?.setTheme(webViewThemeRes(dark))
        webView.setBackgroundColor(bgColor)
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    LaunchedEffect(text, html, path, dark) {
        if (renderHtml) {
            webView.loadDataWithBaseURL(viewModel.baseUrl(path), html!!, "text/html", "utf-8", null)
        } else {
            val rendered = viewModel.buildHtml(text, dark)
            webView.loadDataWithBaseURL(viewModel.baseUrl(path), rendered, "text/html", "utf-8", null)
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else exitPreview()
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private fun webViewThemeRes(dark: Boolean): Int =
    if (dark) R.style.Theme_AICode_WebView_Dark else R.style.Theme_AICode_WebView

private const val BG_DARK = 0xFF0D1117.toInt()
private const val BG_LIGHT = 0xFFFFFFFF.toInt()
private const val TAG = "MarkdownPreview"
