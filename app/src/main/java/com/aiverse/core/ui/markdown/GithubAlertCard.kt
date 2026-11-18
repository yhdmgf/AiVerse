package com.aiverse.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Announcement
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiverse.core.markdown.GithubFlavoredMarkdown
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote

/**
 * GitHub 告警块（`> [!NOTE]` 等）的渲染。
 *
 * 对齐 GitHub 官方观感：标题固定为英文类型名、左侧一道强调色竖条 + 淡色底、图标用
 * Material 矢量图标（不使用 emoji）。配色取 GitHub 的明/暗两套 accent：
 *
 * | 类型 | 浅色 | 深色 |
 * | --- | --- | --- |
 * | Note | `#0969DA` | `#4493F8` |
 * | Tip | `#1A7F37` | `#3FB950` |
 * | Important | `#8250DF` | `#AB7DF8` |
 * | Warning | `#9A6700` | `#D29922` |
 * | Caution | `#CF222E` | `#F85149` |
 *
 * 非告警的普通引用块仍走库默认的 [MarkdownBlockQuote]，聊天页的引用块观感不受影响。
 */
@Composable
fun GithubAlertBlockQuote(model: MarkdownComponentModel) {
    // 引用块源码 = 整篇 content 里该节点的切片
    val raw = remember(model.content, model.node) {
        val start = model.node.startOffset.coerceIn(0, model.content.length)
        val end = model.node.endOffset.coerceIn(start, model.content.length)
        model.content.substring(start, end)
    }
    val alert = remember(raw) { GithubFlavoredMarkdown.parseAlert(raw) }

    if (alert == null) {
        MarkdownBlockQuote(model.content, model.node, model.typography.paragraph)
        return
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = alertAccent(alert.type, isDark)
    val container = accent.copy(alpha = if (isDark) 0.16f else 0.09f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
    ) {
        // 左侧强调色竖条
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(alertAccent(alert.type, isDark))
        )
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = alertIcon(alert.type),
                    contentDescription = alert.type.title,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = alert.type.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            if (alert.body.isNotBlank()) {
                // 正文内部仍支持完整 Markdown（粗体、链接、行内代码、列表）
                Markdown(
                    content = alert.body,
                    colors = LocalMarkdownColors.current,
                    typography = model.typography,
                    padding = LocalMarkdownPadding.current,
                    dimens = LocalMarkdownDimens.current,
                )
            }
        }
    }
}

/** GitHub octicons 的类型语义 → Material 矢量图标（octicons 自带版权，这里用语义近似的内置图标）。 */
private fun alertIcon(type: GithubFlavoredMarkdown.AlertType): ImageVector = when (type) {
    GithubFlavoredMarkdown.AlertType.NOTE -> Icons.Outlined.Info
    GithubFlavoredMarkdown.AlertType.TIP -> Icons.Outlined.Lightbulb
    GithubFlavoredMarkdown.AlertType.IMPORTANT -> Icons.Outlined.Announcement
    GithubFlavoredMarkdown.AlertType.WARNING -> Icons.Outlined.Warning
    GithubFlavoredMarkdown.AlertType.CAUTION -> Icons.Outlined.Dangerous
}

private fun alertAccent(type: GithubFlavoredMarkdown.AlertType, dark: Boolean): Color =
    if (dark) {
        when (type) {
            GithubFlavoredMarkdown.AlertType.NOTE -> Color(0xFF4493F8)
            GithubFlavoredMarkdown.AlertType.TIP -> Color(0xFF3FB950)
            GithubFlavoredMarkdown.AlertType.IMPORTANT -> Color(0xFFAB7DF8)
            GithubFlavoredMarkdown.AlertType.WARNING -> Color(0xFFD29922)
            GithubFlavoredMarkdown.AlertType.CAUTION -> Color(0xFFF85149)
        }
    } else {
        when (type) {
            GithubFlavoredMarkdown.AlertType.NOTE -> Color(0xFF0969DA)
            GithubFlavoredMarkdown.AlertType.TIP -> Color(0xFF1A7F37)
            GithubFlavoredMarkdown.AlertType.IMPORTANT -> Color(0xFF8250DF)
            GithubFlavoredMarkdown.AlertType.WARNING -> Color(0xFF9A6700)
            GithubFlavoredMarkdown.AlertType.CAUTION -> Color(0xFFCF222E)
        }
    }

/** 给 `markdownComponents(blockQuote = ...)` 用的包装。 */
@Composable
fun githubAlertBlockQuote(): (@Composable (MarkdownComponentModel) -> Unit) =
    { model -> GithubAlertBlockQuote(model) }
