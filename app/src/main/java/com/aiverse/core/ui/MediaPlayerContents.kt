package com.aiverse.core.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Music
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import java.io.File
import kotlinx.coroutines.delay

/**
 * 音频播放内容：居中卡片，标题 + 可拖动进度条 + 时间 + 播放/暂停。
 *
 * 用系统 [MediaPlayer]（照搬 [com.aiverse.core.tts.OnlineTtsEngine] 的模型）：分段串行 + stop 取消
 * 在这里退化为「单段播放 + 手动 seek」，播放器随组合进入创建、随离开释放。
 */
@Composable
internal fun AudioPlayerContent(file: File, title: String) {
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }
    // 用户拖动/点击进度条期间为 true，暂停 500ms 轮询覆盖，避免滑块被 currentPosition 拽回抖动
    var isSeeking by remember { mutableStateOf(false) }

    val player = remember(file) {
        MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    prepared = true
                    durationMs = it.duration.toLong().coerceAtLeast(0L)
                    it.start()
                    playing = true
                }
                setOnCompletionListener {
                    playing = false
                    positionMs = 0L
                    runCatching { it.seekTo(0) }
                }
                setOnErrorListener { _, _, _ ->
                    failed = true
                    prepared = false
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    // 播放中按 500ms 轮询进度；暂停/播完时协程取消，进度停在原地。
    // 拖动进度条期间（isSeeking）暂停覆盖，让 onValueChange 的目标位置保持到 seek 完成。
    LaunchedEffect(player, playing) {
        while (playing) {
            if (!isSeeking) {
                positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = FeatherIcons.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = title.ifBlank { stringResource(R.string.common_file) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (failed) {
                    Text(
                        text = stringResource(R.string.common_media_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Slider(
                        // Material3 Slider 的 value 是 0..1 归一化比例，不是毫秒绝对值；
                        // 直接传 positionMs（毫秒）会超出默认 valueRange=0f..1f 被 clamp 到 1.0，滑块瞬间跳最右。
                        value = if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { fraction ->
                            isSeeking = true
                            positionMs = (fraction * durationMs).toLong().coerceIn(0L, durationMs)
                        },
                        onValueChangeFinished = {
                            if (prepared) {
                                runCatching { player.seekTo(positionMs.toInt()) }
                            }
                            isSeeking = false
                        },
                        enabled = prepared && durationMs > 0L,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMediaTime(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMediaTime(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!prepared) return@IconButton
                            if (playing) {
                                runCatching { player.pause() }
                                playing = false
                            } else {
                                runCatching { player.start() }
                                playing = true
                            }
                        },
                        enabled = prepared,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (playing) FeatherIcons.Pause else FeatherIcons.Play,
                            contentDescription = stringResource(
                                if (playing) R.string.common_pause else R.string.common_play
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 视频播放内容：ExoPlayer + PlayerView 铺满全屏（套在 [MediaPlayerHost] 的 Dialog 里）。
 *
 * PlayerView 自带控制器提供进度条、播放/暂停与时间；全屏按钮切换 [AspectRatioFrameLayout.RESIZE_MODE_FIT]
 * 与 `FILL`（放大铺满），因宿主 Dialog 本身就是全屏窗口，FIT↔FILL 即「全屏/还原」的语义。
 */
@Composable
internal fun VideoPlayerContent(file: File) {
    val context = LocalContext.current
    var playerError by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playerError = true
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = if (fullscreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // 控制器上的全屏按钮：切换铺满 / 适应（宿主 Dialog 已是全屏窗口）
                    setFullscreenButtonClickListener {
                        fullscreen = !fullscreen
                        resizeMode = if (fullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                // decorFitsSystemWindows = false 之后要自己让开系统栏，否则控制器底部钮被导航栏盖住
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
        if (playerError) {
            Text(
                text = stringResource(R.string.common_media_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.lg)
            )
        }
    }
}

/**
 * 内联语音条（微信 / QQ / Telegram 风格）：左侧播放·暂停圆形开关 + 中间从左到右填充的进度条 +
 * 右侧剩余秒数，底部小字显示文件大小与路径。不再弹播放器，直接嵌在消息气泡的附件行里。
 *
 * 播放模型照搬 [AudioPlayerContent]：系统 [MediaPlayer] 单段播放，随组合进入创建、离开释放。
 * 与弹出式不同：**默认不自动播放**（用户点 ▶ 才开始），播完复位到开头，右侧秒数显示剩余倒计时。
 *
 * @param file 宿主本地文件；为 null / 不存在 / 空文件时禁用播放并显示「媒体加载失败」。
 * @param title 文件名（兼作无障碍描述与失败占位；留空回退「文件」）。
 * @param sizeText 已格式化的文件大小（如 "1.2 MB"），由调用方用 `formatBytes` 生成——core 层不反向依赖 feature。
 * @param pathText 文件位置/路径（如容器路径或文件名），底部以次要色显示；为空则不显示。
 * @param containerColor 语音条底色，由调用方传入以与消息气泡风格协调。
 * @param cornerRadius 语音条圆角（按密度缩放），由调用方传入。
 */
@Composable
internal fun VoiceMessageBar(
    file: File?,
    title: String,
    sizeText: String,
    pathText: String,
    containerColor: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val t = LocalUIDensityTokens.current
    // 文件不存在 / 空文件直接视为不可用：禁用播放，但文件信息（大小 / 路径）仍展示，对定位仍有价值。
    val usableFile = remember(file) { file?.takeIf { it.isFile && it.length() > 0L } }

    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }

    val player = remember(usableFile) {
        usableFile?.let { f ->
            MediaPlayer().apply {
                try {
                    setDataSource(f.absolutePath)
                    setOnPreparedListener { mp ->
                        prepared = true
                        durationMs = mp.duration.toLong().coerceAtLeast(0L)
                        // 语音条默认不自动播放，等待用户点 ▶ 才开始
                    }
                    setOnCompletionListener { mp ->
                        playing = false
                        positionMs = 0L
                        runCatching { mp.seekTo(0) }
                    }
                    setOnErrorListener { _, _, _ ->
                        failed = true
                        prepared = false
                        true
                    }
                    prepareAsync()
                } catch (e: Exception) {
                    failed = true
                }
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.let { p ->
                runCatching { p.stop() }
                runCatching { p.release() }
            }
        }
    }

    // 播放中按 500ms 轮询进度，驱动进度条从左到右 + 右侧剩余秒数倒计时；暂停 / 播完时协程取消，进度停在原地。
    LaunchedEffect(player, playing) {
        while (playing && player != null) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
            delay(500)
        }
    }

    val enabled = usableFile != null && prepared && !failed
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    // 平滑推进度，避免 500ms 轮询的跳变，形成「从左到右连续填充」的动画效果
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300),
        label = "voiceProgress"
    )
    // 未播放显示总时长；播放中显示剩余倒计时（"-0:12"）；播完复位回总时长。
    val timeText = if (playing) {
        "-" + formatMediaTime((durationMs - positionMs).coerceAtLeast(0L))
    } else {
        formatMediaTime(durationMs)
    }

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = t.sm, vertical = t.sm),
            verticalArrangement = Arrangement.spacedBy(t.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(t.sm)
            ) {
                // 左：播放 / 暂停开关（圆形）
                Box(
                    modifier = Modifier
                        .size(t.touchTarget)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(enabled = enabled) {
                            if (playing) {
                                runCatching { player?.pause() }
                                playing = false
                            } else {
                                runCatching { player?.start() }
                                playing = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            !enabled -> FeatherIcons.Music
                            playing -> FeatherIcons.Pause
                            else -> FeatherIcons.Play
                        },
                        contentDescription = if (enabled) {
                            stringResource(if (playing) R.string.common_pause else R.string.common_play)
                        } else {
                            stringResource(R.string.common_media_load_failed)
                        },
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 中：进度条（从左到右填充）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(t.xs)
                        .clip(RoundedCornerShape(t.rPill))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .background(
                                if (enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                    )
                }
                // 右：剩余秒数（播放中倒计时）/ 总时长
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 底：文件名 · 大小 · 路径（小字，次要色）。文件名在前（Telegram 风格），路径与文件名相同时去重。
            val fileInfo = remember(title, sizeText, pathText) {
                buildString {
                    append(title)
                    if (sizeText.isNotBlank()) {
                        append(" · ")
                        append(sizeText)
                    }
                    if (pathText.isNotBlank() && pathText != title) {
                        append(" · ")
                        append(pathText)
                    }
                }
            }
            Text(
                text = fileInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
