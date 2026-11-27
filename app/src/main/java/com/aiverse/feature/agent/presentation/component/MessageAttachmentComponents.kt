package com.aiverse.feature.agent.presentation.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aiverse.R
import com.aiverse.core.theme.Brand
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.LocalImageViewer
import com.aiverse.core.ui.decodeSampledBitmap
import com.aiverse.core.ui.copyToShareCache
import com.aiverse.core.ui.isShareableFile
import com.aiverse.core.ui.LocalMediaViewer
import com.aiverse.core.ui.openFileExternal
import com.aiverse.core.ui.readImageSize
import com.aiverse.core.ui.saveImageToGallery
import com.aiverse.core.ui.MediaKind
import com.aiverse.core.ui.ThumbnailCache
import com.aiverse.core.ui.VoiceMessageBar
import com.aiverse.core.ui.mediaKindOf
import com.aiverse.core.util.ApkInstaller
import com.aiverse.core.util.shareableMimeType
import com.aiverse.feature.agent.presentation.AgentAttachment
import com.aiverse.feature.workspace.domain.FileAccessProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import compose.icons.feathericons.Maximize
import compose.icons.feathericons.Music
import compose.icons.feathericons.Video
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 附件缩略图边长：一行一个文件时左侧的小图，够看清是截图还是照片即可。 */
private val AttachmentThumbSize = 44.dp

/**
 * 已发送 / 已生成附件的列表：**一行一个文件**。
 *
 * 每行是「左侧缩略图（图片放真缩略图，其余按类型给图标）+ 文件名 + 大小 · 容器路径」，
 * 整行可点。不再用横向滚动的方形卡片带：76dp 的格子里文件名必被截断，多个文件还得左右滑，
 * 一行一个才能一眼看清发的是哪几份。
 *
 * 点击语义：图片一律走内置全屏查看器（隐式依赖组合树上层提供 [LocalImageViewer]，聊天区都满足）；
 * 其余非图片（含 md/html）用调用方给的 [onClick] —— 工具卡片传的是「系统 app 打开」，助手气泡不传即不可点。
 */
@Composable
internal fun MessageAttachmentList(
    attachments: List<AgentAttachment>,
    /** 紧凑行（工具卡片缩略图行）还是内联大卡（消息气泡图片画廊大图 / 视频内联播放器）。 */
    compact: Boolean = false,
    /** 当前消息是否为用户发送：图片 / 视频卡下方的文件名与路径按发送方对齐。 */
    isUser: Boolean = false,
    onClick: ((AgentAttachment) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        attachments.forEach { attachment ->
            MessageAttachmentRow(
                attachment = attachment,
                compact = compact,
                isUser = isUser,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun MessageAttachmentRow(
    attachment: AgentAttachment,
    compact: Boolean = false,
    isUser: Boolean = false,
    onClick: ((AgentAttachment) -> Unit)? = null
) {
    val t = LocalUIDensityTokens.current
    // 附件媒体类型：音频改内联语音条（不弹播放器），视频在消息气泡改内联播放器（issue #238）。
    val mediaKind = mediaKindOf(attachment.fileName, attachment.mimeType)
    val isAudio = mediaKind == MediaKind.AUDIO
    val isVideo = mediaKind == MediaKind.VIDEO
    if (isAudio) {
        // 音频：内联语音条，替代原「缩略图 + 文件名 + 大小·路径」行，不再弹应用内播放器。
        val fallbackName = stringResource(R.string.common_file)
        val name = attachment.fileName.ifBlank { fallbackName }
        VoiceMessageBar(
            file = remember(attachment.localPath) {
                attachment.localPath.takeIf { it.isNotBlank() }?.let(::File)
            },
            title = name,
            sizeText = formatBytes(attachment.sizeBytes),
            pathText = attachment.containerPath.ifBlank { attachment.fileName },
            containerColor = chatMutedSurfaceColor(),
            cornerRadius = t.imageCorner
        )
        return
    }
    // 消息气泡（compact=false）里图片 / 视频改内联大卡：图片按自身比例定高（宽图 3:1 不再留大片空白）
    // + 下方文件名/路径，视频内联播放器 + 下方文件名/路径；保存/分享/插入收进消息「更多」菜单。
    // 工具卡片（compact=true）保持原缩略图行，行为不变。
    if (!compact && attachment.isImage) {
        LargeImageAttachment(attachment = attachment, isUser = isUser)
        return
    }
    if (!compact && isVideo) {
        InlineVideoAttachment(attachment = attachment, isUser = isUser)
        return
    }
    CompactAttachmentRow(attachment = attachment, onClick = onClick)
}

/**
 * 紧凑附件行：左侧缩略图 + 文件名 + 大小 · 容器路径，整行可点。
 *
 * 工具卡片（compact=true）的图片 / 视频仍走这里；消息气泡里图片解码失败、视频取源失败也回退到这里。
 * 点击语义：图片一律走内置全屏查看器（隐式依赖组合树上层提供 [LocalImageViewer]，聊天区都满足）；
 * 视频走应用内播放器；其余非图片（含 md/html）用调用方给的 [onClick] —— 工具卡片传的是「系统 app 打开」，
 * 助手气泡不传即不可点。
 */
@Composable
private fun CompactAttachmentRow(
    attachment: AgentAttachment,
    onClick: ((AgentAttachment) -> Unit)? = null
) {
    val viewer = LocalImageViewer.current
    val mediaViewer = LocalMediaViewer.current
    val t = LocalUIDensityTokens.current
    val mediaKind = mediaKindOf(attachment.fileName, attachment.mimeType)
    val isVideo = mediaKind == MediaKind.VIDEO
    // 图片走内置全屏查看器；视频走应用内播放器；其余非图片交给调用方的「系统 app 打开」。
    val handler: (() -> Unit)? = when {
        attachment.isImage -> ({ viewer.show(attachment.toViewerRequest()) })
        isVideo -> ({ mediaViewer.show(attachment.toMediaRequest()) })
        onClick != null -> ({ onClick(attachment) })
        else -> null
    }
    val fallbackName = stringResource(R.string.common_file)
    val name = attachment.fileName.ifBlank { fallbackName }
    // 读屏与长按提示用：图片是「预览」、音视频是「播放」、其余是「打开」，三者落到的地方不一样。
    val actionLabel = when {
        attachment.isImage -> stringResource(R.string.chat_attachment_preview, name)
        isVideo -> stringResource(R.string.chat_attachment_play, name)
        else -> stringResource(R.string.chat_attachment_open, name)
    }
    // 次要信息：大小 + 容器路径。路径对「AI 发过来的文件」是关键信息（能直接在终端/编辑器里找到），
    // 整行放不下时靠省略号截断，不换行。
    val meta = remember(attachment.sizeBytes, attachment.containerPath) {
        listOf(formatBytes(attachment.sizeBytes), attachment.containerPath)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    Surface(
        shape = RoundedCornerShape(t.imageCorner),
        color = chatMutedSurfaceColor(),
        modifier = Modifier
            .fillMaxWidth()
            // 先裁圆角再挂 clickable：涟漪才会被裁在圆角内，不会溢出成直角方块
            .clip(RoundedCornerShape(t.imageCorner))
            .then(
                if (handler != null) {
                    Modifier.clickable(onClickLabel = actionLabel, onClick = handler)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AttachmentThumb(attachment = attachment)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 有动作才给指示箭头：不可点的行不该长得像能点（如助手气泡里的非图片附件）
            if (handler != null) {
                Icon(
                    FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = Brand.IconGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 内联大图的加载状态：解码与宽高都就绪后按比例渲染；失败回退紧凑行。 */
private sealed interface LargeImageLoad {
    object Loading : LargeImageLoad
    object Failed : LargeImageLoad
    data class Ready(val bitmap: ImageBitmap, val size: Pair<Int, Int>?) : LargeImageLoad
}

/**
 * 消息气泡里的图片内联大卡（issue #238 / #304）：
 * - 宽图（宽高比 ≥ 4:3）按自身比例定高（3:1 就是卡宽 ÷ 3），不再统一按 4:3 留出上下大片空白；
 *   超宽全景图有最小高度，[ContentScale.Fit] 居中留边、不裁切；
 * - 竖图 / 长截图（宽高比 < 4:3）按自然比例显示，最大高上限按 UIDensityTokens.scale 缩放（约 400dp）
 *   防长截图顶满屏，不裁切。
 * 点击大图走内置全屏查看器（[LocalImageViewer]）；保存 / 分享 / 插入聊天收进消息「更多」菜单，
 * 不再在图片下方常驻独立操作行。图片下方固定展示文件名与容器路径。
 *
 * 大图独立解码链：不走 44dp 缩略图（[ThumbnailCache]），按卡宽采样解码（[decodeSampledBitmap]），
 * 宽高比经 [readImageSize] 只读边界，不申请像素内存。
 */
@Composable
private fun LargeImageAttachment(
    attachment: AgentAttachment,
    isUser: Boolean = false
) {
    val viewer = LocalImageViewer.current
    val t = LocalUIDensityTokens.current
    val fileAccess = LocalChatFileAccess.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth
        // 卡宽（px）：decodeSampledBitmap 的 maxEdge 是像素，先把布局宽换算成 px 作为采样基准。
        val cardWidthPx = with(density) { cardWidth.toPx() }.toInt()
        val state by produceState<LargeImageLoad>(LargeImageLoad.Loading, attachment, fileAccess, cardWidthPx) {
            val file = withContext(Dispatchers.IO) { resolveLocalFile(attachment, fileAccess) }
            if (file == null) {
                value = LargeImageLoad.Failed
                return@produceState
            }
            val size = withContext(Dispatchers.IO) { readImageSize(file.absolutePath) }
            // 采样 maxEdge 取「显示所需较大边」而非只看卡宽：竖图 / 长截图显示高度可到 t.scale(400)dp
            // （通常大于卡宽），只按卡宽采样会让解码分辨率低于显示分辨率、被 Fit 轻微放大而发虚。
            // 显示高与卡高同一口径（横图封顶 4:3 / 竖图自然比例封顶），保证解码 >= 显示、不虚也不超采。
            val aspect = if (size != null && size.second > 0) {
                size.first.toFloat() / size.second.toFloat()
            } else {
                4f / 3f
            }
            val displayHeight = largeImageCardHeight(cardWidth, aspect, t.scale(400), t.scale(120))
            val maxEdgePx = with(density) { maxOf(cardWidth, displayHeight).toPx() }.toInt()
            val bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(file.absolutePath, maxEdgePx) }
            value = if (bitmap != null) LargeImageLoad.Ready(bitmap, size) else LargeImageLoad.Failed
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            when (val loaded = state) {
                LargeImageLoad.Failed -> CompactAttachmentRow(attachment = attachment, onClick = null)
                else -> {
                    val (bitmap, size) = when (loaded) {
                        is LargeImageLoad.Ready -> loaded.bitmap to loaded.size
                        else -> null to null
                    }
                    // 宽高比：读不到边界时按横图 4:3 兜底，避免长宽未知时卡高失控。
                    val aspect = if (size != null && size.second > 0) {
                        size.first.toFloat() / size.second.toFloat()
                    } else {
                        4f / 3f
                    }
                    // 宽图按自身比例定高（超宽给下限），竖图 / 长截图自然比例但封顶（约 400dp），防顶满屏且不裁切。
                    val cardHeight = largeImageCardHeight(cardWidth, aspect, t.scale(400), t.scale(120))
                    Surface(
                        shape = RoundedCornerShape(t.imageCorner),
                        color = chatMutedSurfaceColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                                .clip(RoundedCornerShape(t.imageCorner))
                                .clickable { viewer.show(attachment.toViewerRequest()) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                ComposeImage(
                                    bitmap = bitmap,
                                    contentDescription = attachment.fileName.ifBlank {
                                        stringResource(R.string.common_image_preview)
                                    },
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // 解码中：居中转圈占位
                                CircularProgressIndicator(
                                    modifier = Modifier.size(t.scale(28))
                                )
                            }
                        }
                    }
                    MediaAttachmentCaption(attachment = attachment, isUser = isUser)
                }
            }
        }
    }
}

/**
 * 内联大卡显示高度（issue #304）：
 * - 宽图（宽高比 ≥ 4:3）按自身比例（卡高 = 卡宽 / aspect），3:1 的图就是卡宽的 1/3；
 *   极宽全景图（如 10:1）卡高会被压得很薄，这里给 [minHeight] 下限，图仍按 Fit 居中、两侧留白不裁切；
 * - 竖图 / 长截图（宽高比 < 4:3）按自然比例，但不超过 [maxHeight]（约 400dp），防长截图顶满屏。
 * 采样口径（[LargeImageAttachment] 的 maxEdge）与渲染卡高共用这一份，保证解码 >= 显示、不虚也不超采。
 */
private fun largeImageCardHeight(cardWidth: Dp, aspect: Float, maxHeight: Dp, minHeight: Dp): Dp =
    if (aspect >= 4f / 3f) {
        (cardWidth / aspect).coerceAtLeast(minHeight)
    } else {
        (cardWidth / aspect).coerceAtMost(maxHeight)
    }

/** 内联视频的加载状态：取源就绪后渲染播放器；失败回退紧凑行。 */
private sealed interface InlineVideoLoad {
    object Loading : InlineVideoLoad
    object Failed : InlineVideoLoad
    data class Ready(val file: File) : InlineVideoLoad
}

/**
 * 消息气泡里的视频内联播放器（issue #238）：有界卡片 + ExoPlayer/PlayerView + 控制器 +
 * 右下角全屏入口（复用 [LocalMediaViewer]）。取源统一走 [mediaFileResolver]（远程 SSH 先整份下载）。
 * 默认不自动播放，用户点 ▶ 才播；失败时回退紧凑行（仍可点开全屏播放器重试）。
 */
@Composable
private fun InlineVideoAttachment(
    attachment: AgentAttachment,
    isUser: Boolean = false
) {
    val mediaViewer = LocalMediaViewer.current
    val t = LocalUIDensityTokens.current
    val fileAccess = LocalChatFileAccess.current
    val resolve = remember(fileAccess) { mediaFileResolver(fileAccess) }
    val request = remember(attachment) { attachment.toMediaRequest() }
    val state by produceState<InlineVideoLoad>(InlineVideoLoad.Loading, request, resolve) {
        val file = request.sources.firstNotNullOfOrNull { resolve(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
        value = if (file != null) InlineVideoLoad.Ready(file) else InlineVideoLoad.Failed
    }
    when (val loaded = state) {
        InlineVideoLoad.Failed -> CompactAttachmentRow(attachment = attachment, onClick = null)
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Surface(
                    shape = RoundedCornerShape(t.imageCorner),
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(t.scale(200))
                    ) {
                        if (loaded is InlineVideoLoad.Ready) {
                            InlineVideoPlayer(file = loaded.file)
                        } else {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        // 全屏入口：复用应用内播放器（内含 FIT↔FILL 全屏切换），不再重复造全屏。
                        IconButton(
                            onClick = { mediaViewer.show(request) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.sm)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Maximize,
                                contentDescription = stringResource(R.string.chat_attachment_fullscreen),
                                tint = Color.White
                            )
                        }
                    }
                }
                MediaAttachmentCaption(attachment = attachment, isUser = isUser)
            }
        }
    }
}

/**
 * 有界视频播放器：ExoPlayer + PlayerView，[AspectRatioFrameLayout.RESIZE_MODE_FIT] 居中显示，
 * 自带控制器（进度 / 播放暂停 / 时间）。随组合进入创建、离开释放；默认不自动播放。
 */
@Composable
private fun InlineVideoPlayer(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 图片 / 视频内联卡下方的固定说明：文件名 + 容器路径（issue #304）。
 * 与紧凑附件行同一口径（[attachment.containerPath]），用户消息按发送方右对齐，助手消息左对齐。
 */
@Composable
private fun MediaAttachmentCaption(
    attachment: AgentAttachment,
    isUser: Boolean = false
) {
    val textAlign = if (isUser) TextAlign.End else TextAlign.Start
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        val path = attachment.containerPath.ifBlank { attachment.fileName }
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 保存图片到相册（消息「更多」菜单用）。文件失效时经 [resolveLocalFile] 按 containerPath 重取；
 * Toast 文案区分成功 / 低版本不支持 / 其他失败。
 */
internal suspend fun saveImageAttachment(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
) {
    val local = withContext(Dispatchers.IO) { resolveLocalFile(attachment, fileAccess) }
    if (local == null) {
        Toast.makeText(context, context.getString(R.string.chat_attachment_save_failed), Toast.LENGTH_SHORT).show()
        return
    }
    val ok = saveImageToGallery(context, local, attachment.mimeType)
    val messageRes = when {
        ok -> R.string.chat_attachment_saved
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> R.string.chat_attachment_save_unsupported
        else -> R.string.chat_attachment_save_failed
    }
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
}

/** 分享图片 / 视频（消息「更多」菜单用）；不可分享的位置先拷进 cacheDir 再经 FileProvider 授权。 */
internal suspend fun shareAttachment(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
) {
    val uri = withContext(Dispatchers.IO) {
        val local = resolveLocalFile(attachment, fileAccess) ?: return@withContext null
        val shareable = if (isShareableFile(context, local)) local else copyToShareCache(context, local)
        shareable?.let {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            }.getOrNull()
        }
    }
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_unshareable), Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = shareableMimeType(attachment.mimeType, attachment.fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.chat_attachment_share)))
    }
}

@Composable
private fun AttachmentThumb(attachment: AgentAttachment) {
    val t = LocalUIDensityTokens.current
    Surface(
        shape = RoundedCornerShape(t.imageCorner / 2),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(AttachmentThumbSize)
    ) {
        if (attachment.isImage) {
            AttachmentImageThumb(attachment = attachment)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    attachmentFileIcon(attachment),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentImageThumb(attachment: AgentAttachment) {
    // 读盘 + 解码放 IO 线程：以前在 remember 里同步 decodeFile，滚到带图消息时会卡主线程。
    // 采样与 OOM 兜底统一走 decodeSampledBitmap。
    val path = attachment.localPath
    val bitmap by produceState<ImageBitmap?>(ThumbnailCache.peek(path), path) {
        value = ThumbnailCache.peek(path) ?: withContext(Dispatchers.IO) { ThumbnailCache.load(path) }
    }
    val loaded = bitmap
    if (loaded != null) {
        ComposeImage(
            bitmap = loaded,
            contentDescription = attachment.fileName.ifBlank { stringResource(R.string.common_image_preview) },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                FeatherIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 打开已发送的非图片附件（用系统 app）。默认空实现，与 `LocalImageViewer` 同一套路 ——
 * 卡片渲染在 `ToolMessageBody` 这类中间层，用 CompositionLocal 避免一路加回调参数、
 * 免去中间层为转发 lambda 改签名。
 */
fun interface AttachmentOpener {
    fun open(attachment: AgentAttachment)
}

internal val LocalAttachmentOpener = staticCompositionLocalOf<AttachmentOpener> { AttachmentOpener { } }

/**
 * 用系统对应 app 打开附件文件（APK 走安装器，其余交 [openFileExternal] 授权分享）。
 *
 * 卡片记录的 [AgentAttachment.localPath] 不保证仍然有效：远程 SSH 模式下它是 `copyToLocal` 的临时副本
 * （进程重启或系统清缓存后消失），外部本地目录工作区、自定义挂载源的文件又落在 FileProvider 声明的目录树之外。
 * 因此失效时退回 [AgentAttachment.containerPath] 经 [fileAccess] 重取，无法授权的位置由 [openFileExternal]
 * 复制进 cacheDir（已由 cache-path 覆盖）再分享。文件确实不存在与最终无法分享分别提示，不再混用一句文案。
 */
internal suspend fun openSentAttachment(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
) {
    val local = withContext(Dispatchers.IO) { resolveLocalFile(attachment, fileAccess) }
    if (local == null) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_missing), Toast.LENGTH_SHORT).show()
        return
    }
    // APK 安装包：统一走 ApkInstaller（含「允许安装未知应用」的授权判定与设置页引导）。
    // 外部本地目录工作区、自定义挂载源的文件不在 FileProvider 目录树内，先拷进 cacheDir 再授权。
    if (isApk(attachment)) {
        val installable = withContext(Dispatchers.IO) {
            if (isShareableFile(context, local)) local else copyToShareCache(context, local)
        }
        if (installable == null) {
            Toast.makeText(context, context.getString(R.string.chat_open_file_unshareable), Toast.LENGTH_SHORT).show()
            return
        }
        when (ApkInstaller.install(context, installable)) {
            ApkInstaller.Result.STARTED -> Unit
            ApkInstaller.Result.NEED_PERMISSION -> {
                Toast.makeText(context, context.getString(R.string.chat_open_apk_permission), Toast.LENGTH_LONG).show()
                if (!ApkInstaller.openInstallPermissionSettings(context)) {
                    Toast.makeText(context, context.getString(R.string.chat_open_file_no_app), Toast.LENGTH_SHORT).show()
                }
            }
            ApkInstaller.Result.NO_HANDLER ->
                Toast.makeText(context, context.getString(R.string.chat_open_file_no_app), Toast.LENGTH_SHORT).show()
        }
        return
    }
    // 非 APK：交给 core 的统一「用系统应用打开」（FileProvider 授权 + ACTION_VIEW + 无应用兜底 Toast）。
    openFileExternal(context, local, attachment.mimeType)
}

/** 非图片附件的缩略图标：音视频按类型给 Music/Video，其余通用文件图标。 */
private fun attachmentFileIcon(attachment: AgentAttachment): ImageVector =
    when (mediaKindOf(attachment.fileName, attachment.mimeType)) {
        MediaKind.AUDIO -> FeatherIcons.Music
        MediaKind.VIDEO -> FeatherIcons.Video
        null -> FeatherIcons.FileText
    }

/** 卡片记录的宿主路径失效（远程临时副本被清、文件被删或移动）时，退回容器路径重取。 */
private suspend fun resolveLocalFile(attachment: AgentAttachment, fileAccess: FileAccessProvider): File? {
    attachment.localPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.let { return it }
    val containerPath = attachment.containerPath
    if (containerPath.isBlank()) return null
    return runCatching { fileAccess.copyToLocal(containerPath) }.getOrNull()?.takeIf { it.isFile }
}

private fun isApk(attachment: AgentAttachment): Boolean {
    if (attachment.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)) return true
    val name = attachment.fileName.lowercase()
    return name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk")
}
