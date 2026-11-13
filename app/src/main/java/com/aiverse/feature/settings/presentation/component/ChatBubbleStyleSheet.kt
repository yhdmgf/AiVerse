package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.ChatBubbleStyle
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.agent.presentation.component.chatBubbleBorderFor
import com.aiverse.feature.agent.presentation.component.chatBubbleColorFor
import com.aiverse.feature.agent.presentation.component.chatBubbleShapeFor
import com.aiverse.feature.agent.presentation.component.chatBubbleTextColorFor
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/** 聊天气泡样式选择弹窗：列出 9 种，每项为「色点 + 名字」短项，弹层高度约三分之一屏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatBubbleStyleSheet(
    title: String,
    selected: ChatBubbleStyle,
    onSelect: (ChatBubbleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.34f).dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl)
        ) {
            SectionLabel(title)
            ChatBubbleStyle.entries.forEach { style ->
                val isSelected = style == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(style) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 样式预览：用能体现该气泡外观的小图替代单纯色点，9 种样式一眼可辨。
                    ChatBubblePreview(style)
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = stringResource(style.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    if (isSelected) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 聊天气泡样式小预览：以约 52×32dp 的圆角块模拟气泡外观，颜色/圆角/描边/阴影复用
 * ChatMessageStyle 的同一推导，占位「文字条」示意气泡内正文。NO_BUBBLE 不套底色、
 * 仅浅描边，体现「正文直接铺底、无容器」。
 */
@Composable
private fun ChatBubblePreview(style: ChatBubbleStyle) {
    val t = LocalUIDensityTokens.current
    val shape = chatBubbleShapeFor(style)
    val noBubble = style == ChatBubbleStyle.NO_BUBBLE
    val bubbleColor = chatBubbleColorFor(style)
    val textColor = chatBubbleTextColorFor(style)
    // 柔和投影：仅 SOFT_SHADOW 浮起，其余无。
    val shadowElevation = if (style == ChatBubbleStyle.SOFT_SHADOW) t.elevationSm else 0.dp
    // 描边：无气泡样式或白底样式（无内置描边且底色接近弹窗底）统一补浅描边保证可见，
    // 有内置描边的样式（经典/漫画/心动）保留自身描边特征。
    val border = chatBubbleBorderFor(style)
        ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Box(
        modifier = Modifier
            .size(width = t.scale(52), height = t.scale(32))
            .then(if (shadowElevation > 0.dp) Modifier.shadow(shadowElevation, shape, clip = false) else Modifier)
            .border(border, shape)
            .clip(shape)
            .background(if (noBubble) Color.Transparent else bubbleColor),
        contentAlignment = Alignment.CenterStart
    ) {
        // 占位「文字条」：直观示意气泡内正文。
        Box(
            modifier = Modifier
                .padding(start = t.xs)
                .size(width = t.scale(20), height = t.scale(6))
                .clip(RoundedCornerShape(t.rPill))
                .background(textColor.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.sm)
    )
}
