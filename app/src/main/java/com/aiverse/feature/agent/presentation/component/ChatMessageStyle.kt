package com.aiverse.feature.agent.presentation.component

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.ChatBubbleRole
import com.aiverse.core.theme.ChatBubbleStyle
import com.aiverse.core.theme.LocalAssistantBubbleStyle
import com.aiverse.core.theme.LocalUserBubbleStyle
import com.aiverse.core.theme.LocalAssistantBubbleOpacity
import com.aiverse.core.theme.LocalUserBubbleOpacity
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Copy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 消息区（DSH 扁平文档流风格）的共用尺寸与色板。
 *
 * 设计基线：整块对话区不再用「每段文字各套一个描边卡片」的堆叠，而是**一页文档**——
 * 助手正文直接铺在页面底色上，用户消息是一枚浅色药丸，工具调用是带 1px 细线的扁平行，
 * 只有代码/差异这类「内容块」才升起来变成带描边的白底卡。
 *
 * 颜色全部取自当前主题的语义槽位（不写死 hex），因此四套预设 + 深色模式 + 莫奈取色
 * 都能自动适配；深色下的色阶关系由 `copy(alpha)` 叠在主题表面上得到，与浅色同构。
 */
internal object ChatStyle {
    /** 细线/描边宽度：扁平风格里唯一的分隔手段。 */
    val hairline = 1.dp

    /** 用户药丸圆角。 */
    val bubbleCorner = 14.dp

    /** 代码卡/差异卡圆角。 */
    val cardCorner = 10.dp

    /** 行内小面板（指令、结果）圆角。 */
    val panelCorner = 8.dp

    /** 工具行最小高度：保证指腹可点，布局紧凑不松散。 */
    val toolRowMinHeight = 32.dp

    /** 代码卡头部高度。 */
    val codeHeaderHeight = 36.dp

    /** 行首图标格：思考行 / 工具行 / 工具分组头共用同一格宽，三行的图标与后续文字才会左右对齐。 */
    val rowIconSize = 16.dp
}

/** 当前角色的气泡样式（由全局 CompositionLocal 注入）。 */
@Composable
private fun chatBubbleStyleOf(role: ChatBubbleRole): ChatBubbleStyle = when (role) {
    ChatBubbleRole.USER -> LocalUserBubbleStyle.current
    ChatBubbleRole.ASSISTANT -> LocalAssistantBubbleStyle.current
}

/** 聊天气泡底色（按所选聊天气泡样式返回）：用户与助手两侧可各自独立配置，并应用各自气泡透明度。 */
@Composable
internal fun chatBubbleColor(role: ChatBubbleRole): Color {
    val base = chatBubbleColorFor(chatBubbleStyleOf(role))
    val opacity = when (role) {
        ChatBubbleRole.USER -> LocalUserBubbleOpacity.current
        ChatBubbleRole.ASSISTANT -> LocalAssistantBubbleOpacity.current
    }
    return base.copy(alpha = opacity / 100f)
}

/** 聊天气泡底色：按具体样式取值，供设置页预览复用同一推导。 */
@Composable
internal fun chatBubbleColorFor(style: ChatBubbleStyle): Color = when (style) {
    ChatBubbleStyle.CLASSIC_OUTLINE,
    ChatBubbleStyle.SOFT_SHADOW,
    ChatBubbleStyle.MINIMAL_WHITE -> MaterialTheme.colorScheme.surface
    ChatBubbleStyle.DARK_MODE -> Color(0xFF121212)
    ChatBubbleStyle.COMIC -> Color.White
    ChatBubbleStyle.FRESH_GREEN -> Color(0xFF4CAF50)
    ChatBubbleStyle.HEARTBEAT -> Color(0xFFF2F2F2)
    ChatBubbleStyle.ORIGINAL -> MaterialTheme.colorScheme.primary
    // 无气泡样式：用户与助手两侧都不套容器（正文直接铺在页面底色上），此值不再作为气泡底色渲染，
    // 仅保留给块级元素配色（chatBlockColorsFor）做深浅推导，避免深色模式下代码块/表格配色失联。
    ChatBubbleStyle.NO_BUBBLE -> MaterialTheme.colorScheme.surface
}

/** 聊天气泡文字色（按所选聊天气泡样式返回）：用户与助手两侧可各自独立配置。 */
@Composable
internal fun chatBubbleTextColor(role: ChatBubbleRole): Color =
    chatBubbleTextColorFor(chatBubbleStyleOf(role))

/** 聊天气泡文字色：按具体样式取值，供设置页预览复用同一推导。 */
@Composable
internal fun chatBubbleTextColorFor(style: ChatBubbleStyle): Color = when (style) {
    ChatBubbleStyle.DARK_MODE -> Color.White
    ChatBubbleStyle.FRESH_GREEN -> Color(0xFF1B1B1B)
    ChatBubbleStyle.ORIGINAL -> MaterialTheme.colorScheme.onPrimary
    else -> MaterialTheme.colorScheme.onSurface
}

/** 气泡内块级元素（Markdown 表格/代码块）配色：跟随气泡底色深浅联动，避免深底气泡里
 *  主题浅灰块底压白字看不清。深底气泡 → 块底用同色系略亮的深色 + 浅字；浅底气泡 → 沿用
 *  主题默认（mutedSurface 底 + onSurface 字）。返回 Pair(块底色?, 块文字色?)，null = 主题默认。 */
@Composable
internal fun chatBlockColors(role: ChatBubbleRole): Pair<Color?, Color?> =
    chatBlockColorsFor(chatBubbleStyleOf(role))

/** 气泡内块级元素配色：按具体样式取值，供设置页预览复用同一推导。 */
@Composable
internal fun chatBlockColorsFor(style: ChatBubbleStyle): Pair<Color?, Color?> {
    val bubble = chatBubbleColorFor(style)
    return if (bubble.luminance() < 0.5f) {
        // 深底气泡：块底叠白提亮（仍是同色系深色），块文字与气泡正文同色（浅色）
        lerp(bubble, Color.White, 0.12f) to chatBubbleTextColorFor(style)
    } else {
        // 浅底气泡：沿用主题默认，不改
        null to null
    }
}

/** 聊天气泡描边（按所选聊天气泡样式返回）：仅白描边/漫画/心动有边，其余无。 */
@Composable
internal fun chatBubbleBorder(role: ChatBubbleRole): BorderStroke? =
    chatBubbleBorderFor(chatBubbleStyleOf(role))

/** 聊天气泡描边：按具体样式取值，供设置页预览复用同一推导（不依赖角色）。 */
@Composable
internal fun chatBubbleBorderFor(style: ChatBubbleStyle): BorderStroke? = when (style) {
    ChatBubbleStyle.CLASSIC_OUTLINE -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
    ChatBubbleStyle.COMIC -> BorderStroke(2.dp, Color.Black)
    ChatBubbleStyle.HEARTBEAT -> BorderStroke(1.5.dp, Color(0xFFBDBDBD))
    // 无气泡样式：用户与助手两侧都不套容器（正文直接铺底），故统一无描边。
    ChatBubbleStyle.NO_BUBBLE -> null
    else -> null
}

/** 聊天气泡圆角（按所选聊天气泡样式返回）：清新绿大圆角，其余按密度令牌 rMd（标准档 10dp）。 */
@Composable
internal fun chatBubbleShape(role: ChatBubbleRole): CornerBasedShape =
    chatBubbleShapeFor(chatBubbleStyleOf(role))

/** 聊天气泡圆角：按具体样式取值，供设置页预览复用同一推导（不依赖角色）。 */
@Composable
internal fun chatBubbleShapeFor(style: ChatBubbleStyle): CornerBasedShape {
    val t = LocalUIDensityTokens.current
    return if (style == ChatBubbleStyle.FRESH_GREEN) RoundedCornerShape(24.dp)
    else RoundedCornerShape(t.bubbleCorner)
}

/** 聊天气泡阴影（按所选聊天气泡样式返回）：柔和投影有阴影，其余无。 */
@Composable
internal fun chatBubbleShadowElevation(role: ChatBubbleRole): Dp {
    val style = when (role) {
        ChatBubbleRole.USER -> LocalUserBubbleStyle.current
        ChatBubbleRole.ASSISTANT -> LocalAssistantBubbleStyle.current
    }
    return if (style == ChatBubbleStyle.SOFT_SHADOW) 4.dp else 0.dp
}

/** 1px 细线：扁平风格里唯一的分组手段。 */
@Composable
internal fun chatHairlineColor(): Color = MaterialTheme.colorScheme.outlineVariant

/** 弱面板底色（工具结果、代码区底）：浅色下接近 `#F9FAFB`。 */
@Composable
internal fun chatMutedSurfaceColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

/** 内容卡底色（差异/代码卡）：浅色下是白底 + 细线，深色下降一档成为「纸面」。 */
@Composable
internal fun chatCardSurfaceColor(): Color = MaterialTheme.colorScheme.surface

/** 横向 1px 细线，占满可用宽度。 */
@Composable
internal fun ChatHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(ChatStyle.hairline)
            .background(chatHairlineColor())
    )
}

/** 消息下方的时间 / token / 耗时小字，统一 12sp 弱化灰。 */
@Composable
internal fun ChatMetaText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private val CHAT_CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 气泡下方的时间戳（如 `17:55`）。 */
internal fun formatClockTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CHAT_CLOCK_FORMATTER)

/**
 * 「思考」字形：矢量原子/思考图标（对齐用户参考图）。
 * 资源载入 R.drawable.ic_thinking，颜色跟随调用方给的主题色 tint，等比缩放到指定尺寸。
 */
@Composable
internal fun ThinkingGlyph(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 16.dp
) {
    Icon(
        painter = painterResource(R.drawable.ic_thinking),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}

/**
 * 内容卡：白底 + 1px 细线 + 可选头部（标题 + 复制）与页脚统计。
 *
 * 参考图里「写入 .dsh-write-test.txt」下面那块就是它：头部一行放文件路径与「复制」，
 * 正文是等宽代码/差异，页脚给 `+3 −0 · 1 个文件` 这类汇总。
 */
@Composable
internal fun ChatCodeCard(
    title: String?,
    copyText: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    val t = LocalUIDensityTokens.current
    val shape = RoundedCornerShape(t.rMd)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(chatCardSurfaceColor())
            .border(ChatStyle.hairline, chatHairlineColor(), shape)
    ) {
        if (!title.isNullOrBlank() || !copyText.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ChatStyle.codeHeaderHeight)
                    .padding(start = t.sm, end = t.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!copyText.isNullOrBlank()) {
                    ChatCopyAction(copyText = copyText)
                }
            }
            ChatHairline()
        }
        content()
        if (!footer.isNullOrBlank()) {
            ChatHairline()
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = t.sm, vertical = t.xs)
            )
        }
    }
}

/** 行内等宽小面板：工具「指令」「结果」这类不需要复制按钮的次要内容块。 */
@Composable
internal fun ChatMonoPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val t = LocalUIDensityTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.chipCorner))
            .background(chatMutedSurfaceColor())
            .padding(horizontal = t.sm, vertical = t.sm)
    ) {
        content()
    }
}

/**
 * 手指拖动允许在边界接力外层；松手后的惯性不得越过内层窗口。
 * 内层为 LazyColumn（如思考展开窗口）时用无参重载——该行为不依赖具体滚动状态。
 */
@Composable
internal fun rememberBoundNestedScrollConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (source == NestedScrollSource.SideEffect) Offset(0f, available.y) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }
}

/** 内层为 ScrollState（如工具结果窗口）时以滚动状态作记忆键。 */
@Composable
internal fun rememberBoundNestedScrollConnection(scrollState: ScrollState): NestedScrollConnection {
    return remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (source == NestedScrollSource.SideEffect) Offset(0f, available.y) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }
}

/** 兼容旧引用的兜底。 */
internal val InnerScrollConsumeRemainder = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * 卡片头部的「复制」文字按钮：点完短暂变成「已复制」。
 *
 * 高度取 32dp（含内边距后命中区约 36dp）：这是密度很高的消息区里的次级动作，
 * 与工具行 40dp 的主命中区错开层级，避免整屏都是 44dp 的大按钮。
 */
@Composable
internal fun ChatCopyAction(
    copyText: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.chat_copy)
) {
    var copied by remember(copyText) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val t = LocalUIDensityTokens.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(t.chipCorner))
            .clickable {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("chat-copy", copyText)))
                    copied = true
                }
            }
            .heightIn(min = t.chipHeight)
            .padding(horizontal = t.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.xs)
    ) {
        Icon(
            imageVector = if (copied) FeatherIcons.Check else FeatherIcons.Copy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (copied) stringResource(R.string.chat_copied) else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (copied) {
        LaunchedEffect(copied) {
            delay(1500)
            copied = false
        }
    }
}
