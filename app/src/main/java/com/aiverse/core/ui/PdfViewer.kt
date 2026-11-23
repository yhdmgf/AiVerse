package com.aiverse.core.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.feature.workspace.domain.FileAccessProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.FileText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 来源。与 [ImageSource] / [MediaSource] 同一套路：把「宿主本地文件」与「AI 视角容器 / 远程路径」
 * 区分开，远程 SSH 模式要先经文件访问抽象整份下载到宿主才能喂 [PdfRenderer]。
 */
sealed interface PdfSource {
    /** 宿主本地文件绝对路径，可以直接打开。 */
    data class LocalFile(val path: String) : PdfSource

    /** AI 视角的容器 / 远程路径，需要先经 [resolve]（通常走 FileAccessProvider.copyToLocal）落到宿主文件。 */
    data class ContainerPath(val path: String) : PdfSource
}

/**
 * 一次预览请求。
 *
 * [sources] 按顺序尝试，前一个拿不到就退到下一个 —— 远程 SSH 模式下已发送附件记的 `localPath`
 * 是 `createTempFile` + `deleteOnExit` 的临时文件，进程重启或缓存被清掉后就没了，必须能退回容器路径重拉。
 */
@Immutable
data class PdfViewerRequest(
    val sources: List<PdfSource>,
    /** 文件名，兼作无障碍描述与顶部标题；留空则不显示标题。 */
    val title: String = "",
    /** 打开来源：文件树打开时顶部栏换成返回箭头 + 三点菜单入口；默认 null = 聊天 / Markdown 旧行为。 */
    val origin: ViewerOrigin? = null
)

/** 打开应用内 PDF 查看器的句柄：调用方只管 show，不关心加载、渲染与翻页。 */
@Stable
fun interface PdfViewer {
    fun show(request: PdfViewerRequest)
}

/**
 * 默认是空实现：没挂 [PdfViewerHost] 的组合树里点 PDF 静默无反应，不会崩。
 * 与 `LocalImageViewer` / `LocalMediaViewer` 同一套路 —— 用 CompositionLocal 而不是一路加回调参数。
 */
val LocalPdfViewer = staticCompositionLocalOf<PdfViewer> { PdfViewer { } }

@Stable
class PdfViewerState : PdfViewer {
    var request by mutableStateOf<PdfViewerRequest?>(null)
        private set

    override fun show(request: PdfViewerRequest) {
        this.request = request
    }

    fun dismiss() {
        request = null
    }
}

@Composable
fun rememberPdfViewerState(): PdfViewerState = remember { PdfViewerState() }

/**
 * 应用内 PDF 查看器的取源解析器：把 [PdfSource] 落成宿主 [File]。
 *
 * [PdfSource.ContainerPath] 经 [com.aiverse.feature.workspace.domain.FileAccessProvider.copyToLocal]
 * —— 本地 PRoot 模式只是路径映射、不产生拷贝；远程 SSH 模式整份下载到临时文件。
 * 本地路径失效或空文件一律返回 null（查看器显示失败占位）。
 */
fun pdfFileResolver(fileAccess: FileAccessProvider): suspend (PdfSource) -> File? =
    { source ->
        withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    is PdfSource.LocalFile -> File(source.path)
                    is PdfSource.ContainerPath -> fileAccess.copyToLocal(source.path)
                }
            }.getOrNull()?.takeIf { it.isFile && it.length() > 0L }
        }
    }

/** 按文件名 + MIME 判定是否 PDF（扩展名可能被改名掩盖真实类型，MIME 命中同样算）。 */
fun isPdfFile(fileName: String, mimeType: String = ""): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext == "pdf" || mimeType.equals("application/pdf", ignoreCase = true)
}

/** 已发送附件的预览请求：localPath 优先、containerPath 兜底（远程临时副本可能被清）。 */
fun pdfViewerRequest(fileName: String, containerPath: String, localPath: String): PdfViewerRequest =
    PdfViewerRequest(
        sources = listOfNotNull(
            localPath.takeIf { it.isNotBlank() }?.let { PdfSource.LocalFile(it) },
            containerPath.takeIf { it.isNotBlank() }?.let { PdfSource.ContainerPath(it) }
        ),
        title = fileName
    )

/**
 * 应用内 PDF 查看器宿主（issue #176，原生 [PdfRenderer]，不新建 Activity、不加导航路由）。
 *
 * 全屏外壳与 [ImageViewerHost] / [MediaPlayerHost] 保持同一套 Dialog 参数（`usePlatformDefaultWidth = false`
 * + `decorFitsSystemWindows = false` + 近黑背板 + 右上关闭），三种全屏查看器视觉一致。
 *
 * 内存策略：**只渲染当前页 ± 邻页**（[PREPAGE_COUNT]），`pageCount` 仅记总数，绝不全量渲染；
 * 按渲染上限宽算目标尺寸而不是按原始 dpi，防超大页 OOM。翻页离开的页由 LRU 逐出时 recycle。
 *
 * @param resolve 把 [PdfSource] 解成宿主 [File]；实现方自行切 IO 线程，返回 null 表示这个来源拿不到。
 * @param actions 文件操作菜单 slot（可选，仅 feature 层注入）。仅当请求 [PdfViewerRequest.origin]
 *   为 [ViewerOrigin.FILE_TREE] 且本参数非 null 时，右上三点才弹出它；core 不感知 feature，
 *   不传或 origin 非 FILE_TREE 时点三点为 no-op，行为与旧版一致。
 */
@Composable
fun PdfViewerHost(
    state: PdfViewerState,
    resolve: suspend (PdfSource) -> File?,
    actions: (@Composable (request: PdfViewerRequest, onDismissViewer: () -> Unit) -> Unit)? = null
) {
    val request = state.request ?: return
    // 取源 + 打开渲染器 + 渲染首页全在一条 IO 协程里，只给一次 Loading；
    // 打开后的渲染器交给 [PdfPager] 随其离开组合关闭，中途换文件也不会漏掉 fd 与位图缓存。
    val fileState by produceState<PdfFileLoad>(PdfFileLoad.Loading, request, resolve) {
        value = withContext(Dispatchers.IO) {
            request.sources.firstNotNullOfOrNull { resolve(it) }
                ?.takeIf { it.isFile && it.length() > 0L }
                ?.let { file ->
                    runCatching {
                        PdfPageRenderer(file).let { renderer ->
                            try {
                                val (bitmap, page) = renderer.goTo(0)
                                PdfFileLoad.Ready(PdfPage(renderer, page, bitmap))
                            } catch (t: Exception) {
                                // 首页渲染失败（加密 / 损坏）时渲染器已打开，必须就地关掉再抛，否则漏 fd。
                                renderer.close()
                                throw t
                            }
                        }
                    }.getOrNull()
                }
        } ?: PdfFileLoad.Failed
    }

    Dialog(
        onDismissRequest = state::dismiss,
        properties = DialogProperties(
            // 不设则 dialog window 是 WRAP_CONTENT，内容铺不满屏
            usePlatformDefaultWidth = false,
            // 不设则内容被系统栏 inset 挤进安全区，黑底在状态栏处断开
            decorFitsSystemWindows = false
        )
    ) {
        // 浅色主题下系统栏图标是深色，压在黑底上看不见。dialog 有自己的 window，单独调它的图标色。
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // PDF 查看器背板刻意不随主题走：浅色模式下也该是近黑，与图片 / 媒体查看器一致
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
        ) {
            when (val loaded = fileState) {
                PdfFileLoad.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                PdfFileLoad.Failed -> PdfLoadFailedPlaceholder(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = state::dismiss)
                        .padding(Spacing.lg)
                )

                is PdfFileLoad.Ready -> PdfPager(
                    initial = loaded.page,
                    contentDescription = request.title.ifBlank { stringResource(R.string.common_pdf_preview) },
                    onDismiss = state::dismiss
                )
            }

            // 顶部栏 + 标题：文件树来源换成返回箭头（左）+ 三点（右），标题居中；聊天 / Markdown
            // 保持旧的左对齐标题 + 右上 × 关闭。历史硬编码 56.dp 让位宽度已收敛为 touchTarget token。
            val isFileTree = request.origin == ViewerOrigin.FILE_TREE
            var actionsNonce by remember { mutableIntStateOf(0) }
            ViewerHeader(
                isFileTree = isFileTree,
                title = if (fileState is PdfFileLoad.Ready) request.title else "",
                onDismiss = state::dismiss,
                onMore = { actionsNonce++ }
            )
            // 每次点三点换一个 key，强制下方 slot 重新组合，重置其内部 sheet 可见态以便再次打开；
            // actions == null 或 origin != FILE_TREE 时不组合任何东西 —— 点三点 no-op。
            if (isFileTree && actions != null && actionsNonce > 0) {
                key(actionsNonce) {
                    actions(request, state::dismiss)
                }
            }
        }
    }
}

private sealed interface PdfFileLoad {
    data object Loading : PdfFileLoad
    data object Failed : PdfFileLoad
    data class Ready(val page: PdfPage) : PdfFileLoad
}

/** 已打开的渲染器与其当前页位图；打包在一起，渲染器就绪前不进组合。 */
private class PdfPage(
    val renderer: PdfPageRenderer,
    val page: Int,
    val bitmap: ImageBitmap
)

/**
 * 单页 + 翻页手势 + 页码条。
 *
 * 渲染器已由宿主打开（[initial]），这里只承接当前页状态并随本组合离开关闭渲染器
 * （关 fd / 渲染器 / recycle 全部位图）。
 */
@Composable
private fun PdfPager(
    initial: PdfPage,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    var container by remember { mutableStateOf(IntSize.Zero) }
    var currentPage by remember { mutableStateOf(initial) }

    Box(modifier = Modifier.fillMaxSize()) {
        PdfPageContent(
            page = currentPage,
            container = container,
            contentDescription = contentDescription,
            onPageChanged = { page, bitmap -> currentPage = PdfPage(initial.renderer, page, bitmap) },
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .onSizeChanged { container = it }
        )

        if (initial.renderer.pageCount > 0) {
            Text(
                text = stringResource(R.string.pdf_viewer_page, currentPage.page + 1, initial.renderer.pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = Spacing.sm)
                    .background(Color.Black.copy(alpha = PAGE_BAR_ALPHA), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
    }
}

/** 当前页内容 + 翻页手势。翻页只在未放大时响应；[ZoomableImage] 放大后会吞掉拖动。 */
@Composable
private fun PdfPageContent(
    page: PdfPage,
    container: IntSize,
    contentDescription: String,
    onPageChanged: (Int, ImageBitmap) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renderer = page.renderer
    val pageCount = renderer.pageCount

    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }

    fun turnTo(delta: Int) {
        val target = page.page + delta
        if (target < 0 || target >= pageCount) return
        val (next, index) = renderer.goTo(target)
        onPageChanged(index, next)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            // tap 写在 drag 之前：tap 的 waitForUpOrCancellation 只有在 drag 未消费事件时才成立，
            // 顺序写反的话一划就同时触发翻页。
            .pointerInput(pageCount, container) {
                detectTapGestures { tap ->
                    if (pageCount <= 1) return@detectTapGestures
                    val width = container.width
                    when {
                        width <= 0 -> Unit
                        tap.x < width / 3f -> turnTo(-1)
                        tap.x > width * 2f / 3f -> turnTo(1)
                    }
                }
            }
            .pointerInput(pageCount, container) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (pageCount <= 1) return@detectHorizontalDragGestures
                    if (dragAmount < 0f && page.page < pageCount - 1) turnTo(1)
                    else if (dragAmount > 0f && page.page > 0) turnTo(-1)
                }
            }
    ) {
        // 单页缩放：复用图片查看器的 ZoomableImage，scale > 1 时它自己吞掉拖动，不会误触翻页；
        // 点在 Fit 后的空白黑边（onBackgroundTap）关闭查看器，点在正文交给上面的左右 1/3 翻页。
        ZoomableImage(
            bitmap = page.bitmap,
            contentDescription = contentDescription,
            onTap = {},
            modifier = Modifier.fillMaxSize(),
            onBackgroundTap = onDismiss
        )
    }
}

/**
 * 单个 PDF 文件的按需渲染器：持有 [PdfRenderer] 与小 LRU 位图缓存。
 *
 * 缓存只装「当前页 + 邻页」([CACHE_CAPACITY])，翻页只渲染缺失的页，来回翻不重复解码；
 * 逐出与 [close] 时 recycle，避免长文档全量渲染把内存吃满。
 */
private class PdfPageRenderer(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val cache = LinkedHashMap<Int, Bitmap>(CACHE_CAPACITY, 0.75f, true)

    val pageCount: Int = renderer.pageCount

    var page: Int = 0
        private set

    /** 翻到 [target]（自动夹到有效范围），返回该页位图与最终页码。 */
    fun goTo(target: Int): Pair<ImageBitmap, Int> {
        page = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return cachedOrRender(page).asImageBitmap() to page
    }

    private fun cachedOrRender(index: Int): Bitmap {
        cache[index]?.let { return it }
        val bitmap = renderPage(index)
        cache[index] = bitmap
        prepage(index)
        return bitmap
    }

    /** 预渲染当前页之后的一页，快速连续翻页时不白屏；超出容量的最旧页 recycle。 */
    private fun prepage(index: Int) {
        (index + 1).takeIf { it < pageCount }?.let { neighbor ->
            cache.getOrPut(neighbor) { renderPage(neighbor) }
        }
        // 缓存容量是个小常量（[CACHE_CAPACITY]），翻页只多装一页，这里挤掉最旧的即可。
        // 逐出时只移出缓存、不 recycle：被挤掉的位图可能仍被当前 UI（PdfPager.currentPage /
        // ZoomableImage）持有并绘制，立即 recycle 会 use-after-recycle 崩溃。改为交给 GC 回收
        // （现代 Android 位图像素在 native，Java 对象回收即释放），close() 时再统一 recycle 残留。
        while (cache.size > CACHE_CAPACITY) {
            val eldest = cache.entries.iterator()
            if (!eldest.hasNext()) break
            eldest.next()
            eldest.remove()
        }
    }

    private fun renderPage(index: Int): Bitmap {
        renderer.openPage(index).use { page ->
            // 按渲染上限宽而不是按原始 dpi：A4 300dpi 会算出 2480×3508，一页就吃掉 ~35MB，
            // 多页缓存直接 OOM。目标宽固定为上限，页高按比例，最终由 ContentScale.Fit 缩进屏幕。
            val ratio = page.height.toFloat() / page.width.coerceAtLeast(1)
            val width = RENDER_MAX_WIDTH_PX
            val height = (width * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
        for (bitmap in cache.values) bitmap.recycle()
        cache.clear()
    }
}

@Composable
private fun PdfLoadFailedPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = FeatherIcons.FileText,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(R.string.common_pdf_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/** 背板不透明度，与 ImageViewer / MediaPlayerViewer 保持一致。 */
private const val BACKDROP_ALPHA = 0.92f

/** 页码条底色不透明度。 */
private const val PAGE_BAR_ALPHA = 0.55f

/** 邻页预渲染页数。 */
private const val PREPAGE_COUNT = 1

/** 位图缓存容量：当前页 + 后一页（向前翻会重新渲染，但翻回时多数仍在容量内）。 */
private const val CACHE_CAPACITY = PREPAGE_COUNT + 1

/**
 * 单页渲染目标宽（像素）。PDF 页按 72dpi 记录尺寸，按原始 dpi 渲染 A4 会有 2480px 宽，
 * 一页就吃掉 ~35MB；压到这个上限后单页约 4-6MB，缓存两页可接受。清晰度仍够手机屏看。
 */
private const val RENDER_MAX_WIDTH_PX = 1240
