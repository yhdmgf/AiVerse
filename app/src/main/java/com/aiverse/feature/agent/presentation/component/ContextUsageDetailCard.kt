package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.glassOr
import com.aiverse.core.theme.semanticColors
import com.aiverse.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.X

/**
 * 上下文用量明细面板（内嵌在用量浮窗里）：
 * 顶部「已用 tokens / 上下文上限（当前模型）/ 占用比例」+ 右侧 × 关闭，进度条上标出自动压缩
 * 阈值界限（如 85%），下方「达到阈值时自动压缩」说明与「立即压缩」按钮，再下为文件/MCP/思考分类明细。
 * 关闭走右上角 × 或再点圆环，不再单独提供「收起」入口。
 */
@Composable
internal fun ContextUsageDetailCard(
    detail: ContextUsageDetail,
    /** 立即压缩当前会话上下文。 */
    onCompact: () -> Unit,
    /** 关闭上下文用量面板（右侧关闭按钮）。 */
    onClose: () -> Unit,
    /** 自动压缩触发阈值（上下文窗口百分比），进度条上在此位置画界限标记。 */
    thresholdPercent: Int = 85,
    modifier: Modifier = Modifier
) {
    val usedRatio = detail.usedRatio.coerceIn(0f, 1f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = glassOr(MaterialTheme.semanticColors.surfaceGlass, MaterialTheme.colorScheme.surface)
    ) {
        // 浮窗内不再放「收起」入口：点 × 或再点圆环即可关闭，避免多一个把卡片缩成一行飘在半空的出口。
        Column(modifier = Modifier.padding(Spacing.sm)) {
            // 已用 tokens / 上下文上限（当前模型）/ 占用比例 + 右侧 × 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.chat_context_used_over_budget,
                        formatTokenCount(detail.usedTokens),
                        formatTokenCount(detail.budgetTokens)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = percentLabel(usedRatio * 100),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = contextRingColor(usedRatio)
                )
                Spacer(Modifier.width(Spacing.xs))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        FeatherIcons.X,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))

            // 进度条 + 阈值界限标记（如 85% 处竖线，提示到达自动压缩线，非强制）
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val thresholdX = maxWidth * (thresholdPercent.coerceIn(0, 100) / 100f)
                LinearProgressIndicator(
                    progress = { usedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = contextRingColor(usedRatio),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(6.dp)
                        .offset(x = thresholdX - 1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }

            Spacer(Modifier.height(2.dp))

            // 自动压缩说明 + 立即压缩
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.chat_context_auto_compact, thresholdPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onCompact,
                    enabled = detail.usedTokens > 0,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_context_compact_now),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 分类明细
            DetailRow(stringResource(R.string.chat_context_file_tokens), detail.fileTokens, detail.usedTokens, MaterialTheme.colorScheme.primary)
            DetailRow(stringResource(R.string.chat_context_mcp_tokens), detail.mcpTokens, detail.usedTokens, MaterialTheme.colorScheme.tertiary)
            DetailRow(stringResource(R.string.chat_context_thinking_tokens), detail.thinkingTokens, detail.usedTokens, MaterialTheme.colorScheme.secondary)
            DetailRow(stringResource(R.string.chat_context_other_tokens), detail.otherTokens, detail.usedTokens, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val pct = if (total > 0) (value * 100f / total) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${formatTokenCount(value)}  (${percentLabel(pct)})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

/** 百分比数字：统一保留两位小数（极小占比显示 0.00%，避免出现 0.0000%）。 */
private fun percentNumber(pct: Float): String =
    String.format(java.util.Locale.US, "%.2f", pct)

/** 百分比标签：数字 + %，文案走 stringResource。 */
@Composable
private fun percentLabel(pct: Float): String =
    stringResource(R.string.chat_context_usage_percent_decimal, percentNumber(pct))

/** 把 token 数格式化成易读形式（委托 Long 版重载，保持 %.1f 格式一致）。 */
internal fun formatTokenCount(value: Int): String = formatTokenCount(value.toLong())
