package com.aiverse.feature.agent.presentation.component

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import compose.icons.FeatherIcons
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiverse.core.ui.ImageSource
import com.aiverse.core.ui.THUMBNAIL_MAX_EDGE
import com.aiverse.core.ui.ThumbnailCache
import com.aiverse.core.ui.rememberAppImageLoader
import com.aiverse.feature.workspace.domain.FileEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件树里本地图片的小缩略图（占用 20dp 类型图标位，见 ChatDrawer.FileTreeIcon）。
 *
 * 仅本地工作区 / 本机文件夹的 [FileEntry.localFile] 非 null；远程工作区恒为 null，由调用方直接
 * 回退类型图标，不发起任何网络请求。是否值得出图走 [shouldThumbnail]：非目录 + 扩展名是图片 +
 * 体积在 [FILE_TREE_THUMB_MAX_BYTES] 之内；超阈值不读盘、直接回退，避免大图拖慢列表滚动。
 *
 * 解码复用 core/ui 的 [ThumbnailCache]（LRU、按路径索引、组合期 peek 无 IO）与内部采样解码
 * （inSampleSize 采样 + 总像素兜底 + OOM 降档重试），不在此重造解码 / 缓存。BitmapFactory 解不了
 * 的格式（SVG、动图 GIF/WebP、HEIF）按 [needsCoil] 分流给进程内共享的 Coil 加载器，同
 * ChatImageLoader 的分流范式。解码在 `Dispatchers.IO` 完成，主线程只显示 [ImageBitmap]；
 * 失败 / OOM / 不支持的格式返回 null，由调用方回退到 `fileTypeIconFor(name)` 类型图标，列表不卡。
 */

/** 文件树缩略图的体积阈值（字节）：超过它不读盘直接回退类型图标。纯 Kotlin 常量，非 dp。 */
const val FILE_TREE_THUMB_MAX_BYTES = 5L * 1024 * 1024

/** 该条目是否值得尝试出缩略图：本地文件 + 非目录 + 图片扩展名 + 体积不超阈值。 */
internal fun shouldThumbnail(entry: FileEntry): Boolean =
    entry.localFile != null &&
        !entry.isDirectory &&
        entry.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS &&
        entry.size in 1..FILE_TREE_THUMB_MAX_BYTES

@Composable
fun FileTreeThumbnail(
    entry: FileEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = rememberAppImageLoader()
    val localFile = entry.localFile
    // 缓存命中时同步拿到初值，避免闪一下类型图标；未命中为 null，后台解码完再刷新。
    val state: State<ImageBitmap?> = produceState<ImageBitmap?>(
        initialValue = localFile?.let { ThumbnailCache.peek(it.path) },
        key1 = localFile
    ) {
        val file = localFile ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { decodeFileTreeThumbnail(context, imageLoader, file) }.getOrNull()
        }
    }
    val decoded = state.value
    if (decoded != null) {
        Image(
            bitmap = decoded,
            contentDescription = entry.name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // 解码失败 / 不支持的格式 / OOM：回退到与其它文件一致的单色类型图标，不留空白占位。
        Icon(
            imageVector = fileTypeIconFor(entry.name),
            contentDescription = entry.name,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 后台解码单个文件树缩略图：先走 BitmapFactory 采样缓存，解不出再按需交给 Coil。 */
private suspend fun decodeFileTreeThumbnail(
    context: Context,
    imageLoader: ImageLoader,
    file: File
): ImageBitmap? {
    val path = file.path
    ThumbnailCache.load(path)?.let { return it }
    if (!needsCoil(path)) return null
    // SVG / 动图 GIF/WebP / HEIF：BitmapFactory 解不出或只取首帧，交给注册了对应解码器的 Coil。
    val result = imageLoader.execute(
        ImageRequest.Builder(context)
            .data(ImageSource.LocalFile(path))
            .size(THUMBNAIL_MAX_EDGE)
            .build()
    )
    val bitmap = (result as? SuccessResult)?.drawable as? BitmapDrawable ?: return null
    return bitmap.bitmap.asImageBitmap()
}
