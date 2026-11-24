package com.aiverse.feature.agent.presentation.component

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiverse.core.ui.ImageSource
import com.aiverse.core.ui.ImageViewerRequest
import com.aiverse.core.ui.MediaKind
import com.aiverse.core.ui.MediaSource
import com.aiverse.core.ui.MediaViewerRequest
import com.aiverse.core.ui.decodeSampledBitmap
import com.aiverse.core.ui.ViewerPeek
import com.aiverse.core.ui.mediaKindOf
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.presentation.AgentAttachment
import com.aiverse.feature.workspace.domain.FileAccessProvider
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ChatImageLoader"

/**
 * 聊天区图片加载器：把 [ImageSource] 落成位图交给全屏查看器。全项目只有这一份解码策略。
 *
 * [ImageSource.ContainerPath] 经 [FileAccessProvider.copyToLocal] 落到宿主文件 —— 本地 PRoot 模式
 * 只是路径映射、不产生拷贝；远程 SSH 模式会把文件 base64 拉回来写进临时文件。
 *
 * 解码按来源分流：远程 URL、SVG、GIF / 动图 WebP 交给统一 [ImageLoader]（其注册的解码器覆盖
 * BitmapFactory 解不了的格式）；其余本地文件与 base64 仍走 [decodeSampledBitmap]，保留 inSampleSize
 * 采样、总像素兜底与 OOM 降档重试这道长截图 / 超大图的真实防线。
 */
internal fun chatImageLoader(
    context: android.content.Context,
    imageLoader: ImageLoader,
    fileAccess: FileAccessProvider,
    maxEdge: Int,
    maxPixels: Long
): suspend (ImageSource) -> ImageBitmap? = { source ->
    withContext(Dispatchers.IO) {
        try {
            val sampled = when (source) {
                is ImageSource.LocalFile ->
                    decodeExistingFile(File(source.path), maxEdge, maxPixels)

                is ImageSource.ContainerPath ->
                    decodeExistingFile(fileAccess.copyToLocal(source.path), maxEdge, maxPixels)

                is ImageSource.Base64 ->
                    decodeSampledBitmap(Base64.getDecoder().decode(source.data), maxEdge, maxPixels)

                is ImageSource.Url -> null
            }
            sampled ?: decodeViaCoil(context, imageLoader, fileAccess, source, maxEdge)
        } catch (e: Exception) {
            FileLogger.w(TAG, "图片加载失败: $source", e)
            null
        }
    }
}

/**
 * 经统一 [ImageLoader] 解码：远程 URL 必走这条路，本地 SVG / GIF / 动图 WebP 因 BitmapFactory
 * 解不出（SVG 直接失败、动图只取首帧）也交由 Coil 的对应解码器处理。
 *
 * 注意 [ImageSource.ContainerPath] 是容器内路径（如 `~/workspace/...`），Coil 在 Android 宿主解析不了，
 * 必须先 [FileAccessProvider.copyToLocal] 落到宿主文件再交给 Coil，否则直接加载失败
 * （表现为查看器报「文件可能已被移动或删除」）。
 */
private suspend fun decodeViaCoil(
    context: android.content.Context,
    imageLoader: ImageLoader,
    fileAccess: FileAccessProvider,
    source: ImageSource,
    maxEdge: Int
): ImageBitmap? {
    if (!needsCoil(source)) return null
    val data: Any = when (source) {
        is ImageSource.LocalFile -> File(source.path)
        is ImageSource.ContainerPath -> fileAccess.copyToLocal(source.path)
        is ImageSource.Base64 -> source.data
        is ImageSource.Url -> source.url
    }
    val result = imageLoader.execute(
        ImageRequest.Builder(context)
            .data(data)
            .size(maxEdge)
            .build()
    )
    val bitmap = ((result as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        ?: return null
    return bitmap.asImageBitmap()
}

private fun needsCoil(source: ImageSource): Boolean = when (source) {
    is ImageSource.Base64 -> false
    is ImageSource.Url -> needsCoil(source.url)
    is ImageSource.LocalFile -> needsCoil(source.path)
    is ImageSource.ContainerPath -> needsCoil(source.path)
}

/**
 * 按路径 / URL 判断是否必须交由 Coil 解码：远程 URL、SVG，以及 BitmapFactory 解不出或只能取
 * 首帧的动图（GIF / 动图 WebP）。internal 供聊天正文图片 transformer 与查看器共用。
 */
internal fun needsCoil(pathOrUrl: String): Boolean {
    val ext = pathOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
    return pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") ||
        ext == "svg" || ext == "gif" || ext == "webp" || ext == "heif" || ext == "heic"
}

private fun decodeExistingFile(file: File, maxEdge: Int, maxPixels: Long): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return decodeSampledBitmap(file.absolutePath, maxEdge, maxPixels)
}

/**
 * 全屏查看器的动图 / 动态照片探查器：在静态解码之前先跑一趟，把动图从静态图里分出来。
 *
 * - 动图（GIF / 动图 WebP）交 `rememberAsyncImagePainter` 逐帧播放 —— 静态解码链
 *   （[decodeSampledBitmap] / Coil 首帧）只会拿到第一帧；
 * - 动态照片（Motion Photo，JPEG/HEIF 里嵌 GCamera / Samsung 的 XMP 标记）本身仍是静态图，
 *   但带标记好让查看器打角标，返回 [ViewerPeek.StaticMotionPhoto]。
 *
 * 动图判定靠扩展名 + 文件头魔数双保险：远程图 / 临时副本扩展名可能丢，靠魔数兜底。
 * 动态照片只对 JPEG / HEIF 类容器嗅探，避免把普通 GIF / WebP 误标。
 */
internal fun motionAwarePeek(fileAccess: FileAccessProvider): suspend (ImageSource) -> ViewerPeek? = { source ->
    withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                is ImageSource.Url ->
                    if (looksAnimated(source.url)) ViewerPeek.Animated(source.url) else null

                is ImageSource.LocalFile -> peekFile(File(source.path))

                is ImageSource.ContainerPath -> peekFile(fileAccess.copyToLocal(source.path))

                is ImageSource.Base64 -> {
                    val bytes = Base64.getDecoder().decode(source.data)
                    when {
                        looksAnimated(bytes) -> ViewerPeek.Animated(
                            "data:image/gif;base64,${source.data}"
                        )
                        hasMotionPhotoMarker(bytes) -> ViewerPeek.StaticMotionPhoto
                        else -> null
                    }
                }
            }
        }.getOrNull()
    }
}

private fun peekFile(file: File): ViewerPeek? {
    if (!file.isFile || file.length() <= 0L) return null
    return when {
        looksAnimated(file) -> ViewerPeek.Animated(file)
        hasMotionPhotoMarker(file) -> ViewerPeek.StaticMotionPhoto
        else -> null
    }
}

/** 按扩展名猜是否动图容器（GIF / WebP）。扩展名是弱信号，可能丢，需魔数兜底。 */
private fun looksAnimated(pathOrUrl: String): Boolean {
    val ext = pathOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
    return ext == "gif" || ext == "webp"
}

private fun looksAnimated(file: File): Boolean =
    looksAnimated(file.name) || looksAnimatedHeader(readHeader(file))

private fun looksAnimated(bytes: ByteArray): Boolean = looksAnimatedHeader(readHeader(bytes))

/** 取判魔数要用的头部：文件读前 [HEADER_SCAN_BYTES] 字节，数组直接取前缀。 */
private fun readHeader(file: File): ByteArray =
    file.inputStream().use { input ->
        val buf = ByteArray(HEADER_SCAN_BYTES)
        val n = input.read(buf)
        if (n <= 0) ByteArray(0) else buf.copyOf(n)
    }

private fun readHeader(bytes: ByteArray): ByteArray =
    bytes.copyOf(minOf(HEADER_SCAN_BYTES, bytes.size))

private fun looksAnimatedHeader(header: ByteArray): Boolean {
    if (header.size < 12) return false
    // GIF: "GIF87a" / "GIF89a"
    if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
        return true
    }
    // WebP: "RIFF"...."WEBP"。是否动图要看 VP8X 的 ANIM 位，这里从宽：整份交给 Coil，
    // 静态 WebP 也会被正常解码成单帧，不会误伤。
    val riff = header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
        header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
    val webp = header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
        header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
    return riff && webp
}

/**
 * 动态照片标记：Google 相机 `GCamera:MotionPhoto` / 新版 `GCamera:MotionPhotoVersion`、
 * 三星 `MotionPhoto` / `MotionPhotoData`，都落在 XMP 里。JPEG 的 XMP 在文件头部 APP1 段、
 * HEIF 在 ftyp 附近的 meta box，都在文件头 / 尾部，扫这两段就够，不必读整份大图。
 */
private fun hasMotionPhotoMarker(file: File): Boolean {
    if (!isMotionPhotoContainer(file.name)) return false
    // 头部只读一次，供下方的动图判定与标记扫描共用。
    val head = readHeader(file)
    if (scanForMotionMarker(head)) return true
    // 尾部：部分厂商把 XMP / 动图数据放文件尾。读的时候向前多带 MOTION_MARKER.size 个字节做重叠，
    // 避免标记恰好横跨「头尾分段截断点」被切成两半而漏检；重叠多扫一点无副作用。
    val tail = runCatching {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val start = maxOf(0L, len - HEADER_SCAN_BYTES)
            raf.seek(start)
            val tailLen = len - start
            val overlap = minOf(MOTION_MARKER.size.toLong(), start).toInt()
            val buf = ByteArray((tailLen + overlap).toInt())
            raf.seek(start - overlap)
            raf.readFully(buf)
            buf
        }
    }.getOrNull() ?: return false
    return scanForMotionMarker(tail)
}

private fun hasMotionPhotoMarker(bytes: ByteArray): Boolean {
    if (!isMotionPhotoContainerBytes(bytes)) return false
    return scanForMotionMarker(bytes)
}

private fun isMotionPhotoContainer(pathOrUrl: String): Boolean {
    val ext = pathOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
    return ext in MOTION_PHOTO_EXTS
}

private fun isMotionPhotoContainerBytes(bytes: ByteArray): Boolean {
    // JPEG: FF D8 FF；HEIC/HEIF: 偏移 4 起 "ftyp"。
    val jpeg = bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    val heif = bytes.size >= 8 &&
        bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
        bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
    return jpeg || heif
}

private fun scanForMotionMarker(bytes: ByteArray): Boolean {
    if (bytes.size < MOTION_MARKER.size) return false
    val limit = bytes.size - MOTION_MARKER.size
    var i = 0
    while (i <= limit) {
        var j = 0
        while (j < MOTION_MARKER.size && bytes[i + j] == MOTION_MARKER[j]) j++
        if (j == MOTION_MARKER.size) return true
        i++
    }
    return false
}

private const val HEADER_SCAN_BYTES = 64 * 1024

private val MOTION_PHOTO_EXTS = setOf("jpg", "jpeg", "heic", "heif")

// 只匹配一次能覆盖 GCamera:MotionPhoto / GCamera:MotionPhotoVersion / MotionPhotoData。
private val MOTION_MARKER = "MotionPhoto".toByteArray(Charsets.UTF_8)

/**
 * 待发送图片附件的查看请求。
 *
 * `localPath` 优先、base64 兜底：`decodeByteArray` 要完整 byte[] 常驻，加上本来就压在内存里的
 * base64 字符串（UTF-16 实占两倍），一张 5MB 的图瞬时要多吃十几 MB；从磁盘解码没这份开销。
 */
internal fun PendingUploadAttachment.toViewerRequest(): ImageViewerRequest = ImageViewerRequest(
    sources = listOfNotNull(
        localPath.takeIf { it.isNotBlank() }?.let { ImageSource.LocalFile(it) },
        image?.base64Data?.takeIf { it.isNotBlank() }?.let { ImageSource.Base64(it) }
    ),
    title = fileName
)

/**
 * 已发送附件的查看请求。
 *
 * 带上 `containerPath` 兜底：远程模式下 `sendFile` 记的 `localPath` 来自 `createTempFile` +
 * `deleteOnExit`，进程重启或缓存被清掉后文件就没了，这时得能退回容器路径重新拉一次。
 */
internal fun AgentAttachment.toViewerRequest(): ImageViewerRequest = ImageViewerRequest(
    sources = listOfNotNull(
        localPath.takeIf { it.isNotBlank() }?.let { ImageSource.LocalFile(it) },
        containerPath.takeIf { it.isNotBlank() }?.let { ImageSource.ContainerPath(it) }
    ),
    title = fileName
)

/**
 * Markdown 内嵌图片的查看请求：link 是 AI 视角的容器路径或远程 URL。
 *
 * http(s) 远程图包成 [ImageSource.Url]（查看器经统一加载器直接拉取，支持动图 / SVG），
 * 其余按容器路径处理（本地 PRoot 仅映射，远程 SSH 先下载到临时文件）。
 *
 * 库把 alt 直接交给 Image 当 contentDescription，不经过 transformer，所以这里拿不到它，
 * 退而取路径末段的文件名当标题。
 */
internal fun markdownViewerRequest(link: String): ImageViewerRequest {
    val url = link.trim()
    val source = if (url.startsWith("http://") || url.startsWith("https://")) {
        ImageSource.Url(url)
    } else {
        ImageSource.ContainerPath(link)
    }
    return ImageViewerRequest(
        sources = listOf(source),
        title = link.trimEnd('/').substringAfterLast('/')
    )
}

/** 已发送附件是否为应用内可播放的音视频（按文件名 + MIME 判定）。 */
internal val AgentAttachment.isMedia: Boolean
    get() = mediaKindOf(fileName, mimeType) != null

/** 已发送附件的播放请求：localPath 优先、containerPath 兜底（远程临时副本可能被清）。 */
internal fun AgentAttachment.toMediaRequest(): MediaViewerRequest = MediaViewerRequest(
    sources = listOfNotNull(
        localPath.takeIf { it.isNotBlank() }?.let { MediaSource.LocalFile(it) },
        containerPath.takeIf { it.isNotBlank() }?.let { MediaSource.ContainerPath(it) }
    ),
    title = fileName,
    kind = mediaKindOf(fileName, mimeType) ?: MediaKind.AUDIO
)

/** 待发送附件是否为应用内可播放的音视频。 */
internal val PendingUploadAttachment.isMedia: Boolean
    get() = mediaKindOf(fileName, mimeType) != null

/** 待发送附件的播放请求：localPath（已落宿主）优先、containerPath 兜底。 */
internal fun PendingUploadAttachment.toMediaRequest(): MediaViewerRequest = MediaViewerRequest(
    sources = listOfNotNull(
        localPath.takeIf { it.isNotBlank() }?.let { MediaSource.LocalFile(it) },
        containerPath.takeIf { it.isNotBlank() }?.let { MediaSource.ContainerPath(it) }
    ),
    title = fileName,
    kind = mediaKindOf(fileName, mimeType) ?: MediaKind.AUDIO
)

/**
 * 应用内媒体播放器的取源解析器：把 [MediaSource] 落成宿主 [File]。
 *
 * [MediaSource.ContainerPath] 经 [FileAccessProvider.copyToLocal] —— 本地 PRoot 模式只是路径映射、
 * 不产生拷贝；远程 SSH 模式整份下载到临时文件。本地路径失效或空文件一律返回 null（播放器显示失败占位）。
 */
internal fun mediaFileResolver(fileAccess: FileAccessProvider): suspend (MediaSource) -> File? = { source ->
    withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                is MediaSource.LocalFile -> File(source.path)
                is MediaSource.ContainerPath -> fileAccess.copyToLocal(source.path)
            }
        }.getOrNull()?.takeIf { it.isFile && it.length() > 0L }
    }
}
