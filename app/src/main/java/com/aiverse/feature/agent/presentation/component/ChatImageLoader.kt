package com.aiverse.feature.agent.presentation.component

import androidx.compose.ui.graphics.ImageBitmap
import com.aiverse.core.ui.ImageSource
import com.aiverse.core.ui.ImageViewerRequest
import com.aiverse.core.ui.MediaKind
import com.aiverse.core.ui.MediaSource
import com.aiverse.core.ui.MediaViewerRequest
import com.aiverse.core.ui.decodeSampledBitmap
import com.aiverse.core.ui.mediaKindOf
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.presentation.AgentAttachment
import com.aiverse.feature.workspace.domain.FileAccessProvider
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "ChatImageLoader"

/** 远程 URL 图片下载客户端（延迟创建）。 */
private val urlClient by lazy { OkHttpClient() }

/**
 * 聊天区图片加载器：把 [ImageSource] 落成位图交给全屏查看器。全项目只有这一份解码策略。
 *
 * [ImageSource.ContainerPath] 经 [FileAccessProvider.copyToLocal] 落到宿主文件 —— 本地 PRoot 模式
 * 只是路径映射、不产生拷贝；远程 SSH 模式会把文件 base64 拉回来写进临时文件。
 */
internal fun chatImageLoader(
    fileAccess: FileAccessProvider,
    maxEdge: Int,
    maxPixels: Long
): suspend (ImageSource) -> ImageBitmap? = { source ->
    withContext(Dispatchers.IO) {
        try {
            when (source) {
                is ImageSource.LocalFile ->
                    decodeExistingFile(File(source.path), maxEdge, maxPixels)

                is ImageSource.ContainerPath ->
                    decodeExistingFile(fileAccess.copyToLocal(source.path), maxEdge, maxPixels)

                is ImageSource.Base64 ->
                    decodeSampledBitmap(Base64.getDecoder().decode(source.data), maxEdge, maxPixels)

                is ImageSource.Url ->
                    decodeRemoteUrl(source.url, maxEdge, maxPixels)
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "图片加载失败: $source", e)
            null
        }
    }
}

/**
 * 下载远程图片 URL 并采样解码（GitHub user-attachments 等无需鉴权图片）。
 * 非 2xx、空体、解码失败均返回 null（查看器显示失败占位）。
 */
private fun decodeRemoteUrl(url: String, maxEdge: Int, maxPixels: Long): ImageBitmap? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "AiVerse")
        .get()
        .build()
    urlClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val bytes = resp.body?.bytes() ?: return null
        if (bytes.isEmpty()) return null
        return decodeSampledBitmap(bytes, maxEdge, maxPixels)
    }
}

private fun decodeExistingFile(file: File, maxEdge: Int, maxPixels: Long): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return decodeSampledBitmap(file.absolutePath, maxEdge, maxPixels)
}

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
 * Markdown 内嵌图片的查看请求：link 就是 AI 视角的容器路径。
 *
 * 库把 alt 直接交给 Image 当 contentDescription，不经过 transformer，所以这里拿不到它，
 * 退而取路径末段的文件名当标题。
 */
internal fun markdownViewerRequest(link: String): ImageViewerRequest = ImageViewerRequest(
    sources = listOf(ImageSource.ContainerPath(link)),
    title = link.trimEnd('/').substringAfterLast('/')
)

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
