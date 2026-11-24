package com.aiverse.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.X

/**
 * 三种全屏查看器（图片 / 音视频 / PDF）共用的顶部栏，保证返回箭头与三点菜单在三处视觉一致。
 *
 * 两种形态：
 * - 文件树（`isFileTree = true`）：左侧返回箭头（[FeatherIcons.ChevronLeft]，文案 `common_back`）、
 *   右侧横向三点（[FeatherIcons.MoreHorizontal]，文案 `common_more`）。点返回 = 关闭查看器；
 *   点三点的实际动作由宿主传入的 [onMore] 决定（core 不感知 feature，`onMore` 为空实现时即 no-op 兜底）。
 * - 旧形态（聊天附件 / Markdown，`isFileTree = false`）：保持原样，仅右上 × 关闭，无菜单。
 *
 * 顶部标题（音视频 / PDF 有，图片无）也在这里一并处理：文件树形态下标题居中、左右各让开一个触控目标宽度
 * （返回 / 三点各占一侧）；旧形态保持左对齐、右侧让开关闭钮。历史硬编码的 `56.dp` 让位宽度收敛为
 * [LocalUIDensityTokens.touchTarget]，随密度档缩放。
 *
 * 按钮规格与历史关闭钮一致：IconButton + `Spacing.sm` 内边距 + `systemBarsPadding` 让开系统栏。
 */
@Composable
internal fun ViewerHeader(
    isFileTree: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onMore: () -> Unit,
) {
    val tokens = LocalUIDensityTokens.current
    Box(modifier = Modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isFileTree) TextAlign.Center else TextAlign.Start,
                modifier = if (isFileTree) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .systemBarsPadding()
                        .fillMaxWidth()
                        .padding(
                            start = tokens.touchTarget,
                            end = tokens.touchTarget,
                            top = Spacing.sm,
                            bottom = Spacing.xs
                        )
                } else {
                    Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding()
                        .padding(
                            start = Spacing.lg,
                            top = Spacing.sm,
                            end = tokens.touchTarget,
                            bottom = Spacing.xs
                        )
                }
            )
        }

        if (isFileTree) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(Spacing.sm)
            ) {
                Icon(
                    imageVector = FeatherIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onMore,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(Spacing.sm)
            ) {
                Icon(
                    imageVector = FeatherIcons.MoreHorizontal,
                    contentDescription = stringResource(R.string.common_more),
                    tint = Color.White
                )
            }
        } else {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
