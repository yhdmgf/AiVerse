package com.aiverse.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import java.io.File

/** 媒体类型：驱动播放器选型（系统 MediaPlayer 播音频 / ExoPlayer 播视频）。 */
enum class MediaKind { AUDIO, VIDEO }

private val AUDIO_EXTS = setOf(
    "mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "opus", "mid", "midi", "amr", "3gp", "3g2"
)
private val VIDEO_EXTS = setOf(
    "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "3g2", "ts", "flv", "wmv", "mpeg", "mpg"
)

/**
 * 按文件名后缀 + MIME 判定媒体类型。两者皆命中且冲突时，MIME 优先（扩展名可能被改名掩盖真实类型）。
 * 无法识别（如 `.zip`、`.pdf`）返回 null，调用方应回落系统打开 / 编辑器。
 */
fun mediaKindOf(fileName: String, mimeType: String = ""): MediaKind? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.startsWith("audio/", ignoreCase = true) || ext in AUDIO_EXTS -> MediaKind.AUDIO
        mimeType.startsWith("video/", ignoreCase = true) || ext in VIDEO_EXTS -> MediaKind.VIDEO
        else -> null
    }
}

/**
 * 媒体来源。与 [ImageSource] 同一套路：把「宿主本地文件」与「AI 视角容器 / 远程路径」区分开，
 * 远程 SSH 模式要先经文件访问抽象整份下载到宿主才能喂播放器。
 */
sealed interface MediaSource {
    /** 宿主本地文件绝对路径，可以直接交给播放器。 */
    data class LocalFile(val path: String) : MediaSource

    /** AI 视角的容器 / 远程路径，需要先经 [resolve]（通常走 FileAccessProvider.copyToLocal）落到宿主文件。 */
    data class ContainerPath(val path: String) : MediaSource
}

/**
 * 一次播放请求。
 *
 * [sources] 按顺序尝试，前一个拿不到就退到下一个 —— 远程 SSH 模式下已发送附件记的 `localPath`
 * 是 `createTempFile` + `deleteOnExit` 的临时文件，进程重启或缓存被清掉后就没了，必须能退回容器路径重拉。
 */
@Immutable
data class MediaViewerRequest(
    val sources: List<MediaSource>,
    /** 文件名，兼作无障碍描述与失败提示；留空回退到「文件」。 */
    val title: String = "",
    /** 音频 / 视频，决定渲染哪个播放器。由调用方按文件名 + MIME 判定。 */
    val kind: MediaKind
)

/** 打开应用内播放器的句柄：调用方只管 show，不关心加载与播放。 */
@Stable
fun interface MediaViewer {
    fun show(request: MediaViewerRequest)
}

/**
 * 默认是空实现：没挂 [MediaPlayerHost] 的组合树里点音视频静默无反应，不会崩。
 * 与 `LocalImageViewer` 同一套路 —— 用 CompositionLocal 而不是一路加回调参数。
 */
val LocalMediaViewer = staticCompositionLocalOf<MediaViewer> { MediaViewer { } }

@Stable
class MediaViewerState : MediaViewer {
    var request by mutableStateOf<MediaViewerRequest?>(null)
        private set

    override fun show(request: MediaViewerRequest) {
        this.request = request
    }

    fun dismiss() {
        request = null
    }
}

@Composable
fun rememberMediaViewerState(): MediaViewerState = remember { MediaViewerState() }

private sealed interface MediaLoad {
    object Loading : MediaLoad
    object Failed : MediaLoad
    data class Ready(val file: File) : MediaLoad
}

/**
 * 应用内音视频播放器宿主。挂在组合树里任意位置即可 —— [Dialog] 是独立 window，不占父布局尺寸。
 *
 * 全屏外壳直接复用 [ImageViewerHost] 的同款 Dialog 参数（`usePlatformDefaultWidth = false` +
 * `decorFitsSystemWindows = false` + 近黑背板 + 右上关闭），保证音视频查看与图片查看视觉一致。
 *
 * @param resolve 把 [MediaSource] 解成宿主 [File]；实现方自行切 IO 线程，返回 null 表示这个来源拿不到。
 */
@Composable
fun MediaPlayerHost(
    state: MediaViewerState,
    resolve: suspend (MediaSource) -> File?
) {
    val request = state.request ?: return
    val fileState by produceState<MediaLoad>(MediaLoad.Loading, request, resolve) {
        value = request.sources.firstNotNullOfOrNull { resolve(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.let { MediaLoad.Ready(it) }
            ?: MediaLoad.Failed
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
                // 媒体查看器背板刻意不随主题走：浅色模式下也该是近黑
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
        ) {
            when (val loaded = fileState) {
                MediaLoad.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                MediaLoad.Failed -> MediaLoadFailedPlaceholder(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = state::dismiss)
                        .padding(Spacing.lg)
                )

                is MediaLoad.Ready -> when (request.kind) {
                    // 聊天消息里的音频已改内联语音条（VoiceMessageBar，见 MediaPlayerContents），
                    // 此处弹出式 AudioPlayerContent 仅服务输入框待发送附件的预览；视频仍走弹出式（不受影响）。
                    MediaKind.AUDIO -> AudioPlayerContent(
                        file = loaded.file,
                        title = request.title
                    )
                    MediaKind.VIDEO -> VideoPlayerContent(
                        file = loaded.file
                    )
                }
            }

            // 顶部标题（让开安全区与右上关闭钮）。视频铺满时叠在画面上，白字在小图上也看得清。
            if (fileState is MediaLoad.Ready && request.title.isNotBlank()) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding()
                        .padding(start = Spacing.lg, top = Spacing.sm, end = 56.dp, bottom = Spacing.xs)
                )
            }

            IconButton(
                onClick = state::dismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // decorFitsSystemWindows = false 之后要自己让开系统栏
                    .systemBarsPadding()
                    .padding(Spacing.sm)
            ) {
                Icon(
                    imageVector = FeatherIcons.X,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MediaLoadFailedPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = FeatherIcons.X,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(R.string.common_media_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/** 背板不透明度，与 ImageViewer 保持一致。Compose 的 Dialog 本身还会给 window 加一层 dim。 */
private const val BACKDROP_ALPHA = 0.92f

/** 把毫秒格式化成 `mm:ss`（< 1 小时）或 `h:mm:ss`。纯数字与冒号，无需本地化。 */
internal fun formatMediaTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}
