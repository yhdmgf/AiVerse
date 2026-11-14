package com.aiverse.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.glassOr
import com.aiverse.core.theme.semanticColors
import com.aiverse.feature.agent.domain.model.TodoItem
import com.aiverse.feature.agent.domain.model.TodoStatus
import com.aiverse.core.ui.ExpandableChevronIcon
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckSquare
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.X

/**
 * 位于输入框上方的待办任务常驻面板：
 * - 仅在当前会话有待办项时显示；
 * - 支持折叠为单行紧凑摘要与展开查看完整列表；
 * - 记住各会话的展开/折叠状态；
 * - 弹窗/键盘叠加时支持联动强制收起。
 */
@Composable
fun TodoDashboardBar(
    items: List<TodoItem>,
    sessionId: String,
    modifier: Modifier = Modifier,
    forceCollapse: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    if (items.isEmpty()) return

    // 按会话隔离记忆展开状态，新会话默认收起
    var isExpanded by rememberSaveable(sessionId) { mutableStateOf(false) }
    // 手动关闭（issue #54）：点 × 整条收起，本会话内不再显示，直到有新待办活动。
    var dismissed by rememberSaveable(sessionId) { mutableStateOf(false) }

    // 键盘弹出时自动收起一次，避免输入框被挤压；但不持续压制——收起后用户仍可点箭头展开查看
    // 完整任务列表（展开列表已 heightIn 限高，不会顶开输入框）。此前用 effectiveExpanded =
    // isExpanded && !forceCollapse 持续压制，导致键盘弹出时点箭头无效（#44）。
    LaunchedEffect(forceCollapse) {
        if (forceCollapse) isExpanded = false
    }

    LaunchedEffect(isExpanded) {
        onExpandedChange(isExpanded)
    }

    val totalCount = items.size
    val completedCount = items.count { it.status == TodoStatus.COMPLETED }
    // 全部完成 → 自动收成一行「全部完成」摘要；有未完成项 → 视为活动状态。
    val allDone = totalCount > 0 && completedCount == totalCount
    val inProgressItem = items.firstOrNull { it.status == TodoStatus.IN_PROGRESS }
    // 全部完成时自动收起（issue #54）：只在完成瞬间收一次，不覆盖用户后续手动展开。
    LaunchedEffect(allDone) {
        if (allDone) isExpanded = false
    }
    // 关闭后出现新的活动待办（未完成项）→ 自动恢复显示。
    LaunchedEffect(items) {
        if (items.any { it.status != TodoStatus.COMPLETED }) {
            dismissed = false
        }
    }
    if (dismissed) return

    val cardBgColor = glassOr(MaterialTheme.semanticColors.surfaceGlass, MaterialTheme.colorScheme.surface)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs),
        shape = RoundedCornerShape(Radius.lg),
        color = cardBgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 8.dp)
        ) {
            // 单行标题栏（点击切换折叠/展开）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FeatherIcons.CheckSquare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.todo_dashboard_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Spacing.xs))

                // 展开/收起箭头移到标题左侧，同时充当展开状态指示器；整行点击仍切换展开态。
                ExpandableChevronIcon(
                    expanded = isExpanded,
                    contentDescription = if (isExpanded) {
                        stringResource(R.string.common_collapse_action)
                    } else {
                        stringResource(R.string.common_expand)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 16.dp
                )

                Spacer(Modifier.width(Spacing.sm))

                // 进度胶囊或当前进行中的任务简述
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (completedCount == totalCount && totalCount > 0) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    }
                ) {
                    Text(
                        text = if (completedCount == totalCount && totalCount > 0) {
                            stringResource(R.string.todo_dashboard_all_done)
                        } else {
                            stringResource(R.string.todo_dashboard_progress, completedCount, totalCount)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (!isExpanded && inProgressItem != null) {
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = inProgressItem.subject,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // 手动关闭入口（issue #54）：有进行中任务时不给关闭入口，避免进行中状态被一键隐藏；
                // 全部完成后 × 独占右端并用 md 间距与左侧内容拉开，减少误触。
                if (inProgressItem == null) {
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { dismissed = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FeatherIcons.X,
                            contentDescription = stringResource(R.string.common_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 展开时展示待办列表（平滑淡入展开、向上卷折淡出）
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.xs))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items.forEach { todo ->
                            TodoItemRow(item = todo.toParsedItem())
                        }
                    }
                }
            }
        }
    }
}

private fun TodoItem.toParsedItem(): ParsedTodoItem = ParsedTodoItem(
    id = id,
    subject = subject,
    description = description,
    status = status.name.lowercase(),
    priority = priority,
    order = order
)
