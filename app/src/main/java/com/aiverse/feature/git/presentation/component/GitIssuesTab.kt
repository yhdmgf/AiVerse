package com.aiverse.feature.git.presentation.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.floatingTabBarContentBottomPadding
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.git.domain.model.IssueLabel
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.User
import coil.compose.AsyncImage

/** GitHub issue 状态色：open 绿 / closed 紫。 */
private val IssueOpenColor = Color(0xFF2DA44E)
private val IssueClosedColor = Color(0xFF8250DF)

/** 按排序方式对 issue 列表排序（ISO-8601 时间戳可直接字典序比较）。 */
internal fun sortIssues(issues: List<GitHubIssue>, sort: String): List<GitHubIssue> {
    return when (sort) {
        "oldest" -> issues.sortedWith(compareBy({ it.createdAt }, { it.number }))
        "updated" -> issues.sortedWith(compareByDescending<GitHubIssue> { it.updatedAt }.thenByDescending { it.number })
        "comments" -> issues.sortedWith(compareByDescending<GitHubIssue> { it.comments }.thenByDescending { it.number })
        else -> issues.sortedWith(compareByDescending<GitHubIssue> { it.createdAt }.thenByDescending { it.number })
    }
}

/**
 * GitHub issue 列表 tab：顶部 open/closed 过滤 + 排序，下方 issue 列表。
 * 支持多选批量操作（长按进入多选，顶部批量栏批量关闭/加标签）。
 * 非 GitHub 仓库或无 token 时展示对应提示态，不做任何写操作。
 */
@Composable
internal fun IssuesTab(
    issues: List<GitHubIssue>,
    loading: Boolean,
    loaded: Boolean,
    filter: String,
    isGitHub: Boolean,
    hasToken: Boolean,
    error: String?,
    remoteUrl: String,
    listState: LazyListState,
    sort: String = "newest",
    selectionMode: Boolean = false,
    selected: Set<Long> = emptySet(),
    bulkBusy: Boolean = false,
    onLoad: () -> Unit = {},
    onFilterChange: (String) -> Unit,
    onOpenIssue: (Long) -> Unit,
    onSortChange: (String) -> Unit = {},
    onToggleSelectionMode: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onBulkClose: () -> Unit = {},
    onBulkAddLabels: (List<String>) -> Unit = {},
    onBulkAssign: (List<String>) -> Unit = {}
) {
    // remoteUrl 变化（如切换仓库）时懒加载；仅 GitHub 且有 token 时触发。
    LaunchedEffect(remoteUrl) {
        if (isGitHub && hasToken && !loaded && !loading) onLoad()
    }

    // 批量加标签弹窗的输入状态。
    var labelDialogVisible by remember { mutableStateOf(false) }
    var labelInput by remember { mutableStateOf("") }
    // 批量分配受理人弹窗的输入状态。
    var assigneeDialogVisible by remember { mutableStateOf(false) }
    var assigneeInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isGitHub && hasToken) {
            if (selectionMode) {
                BulkActionBar(
                    selectedCount = selected.size,
                    busy = bulkBusy,
                    onClear = onClearSelection,
                    onSelectAll = onSelectAll,
                    onBulkClose = onBulkClose,
                    onBulkAddLabel = { labelDialogVisible = true },
                    onBulkAssign = { assigneeDialogVisible = true }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    IssueFilterChip(
                        selected = filter == "open",
                        label = stringResource(R.string.git_issues_open),
                        onClick = { onFilterChange("open") }
                    )
                    IssueFilterChip(
                        selected = filter == "closed",
                        label = stringResource(R.string.git_issues_closed),
                        onClick = { onFilterChange("closed") }
                    )
                    Spacer(Modifier.weight(1f))
                    SortDropdown(current = sort, onSelect = onSortChange)
                }
            }
        }

        when {
            !isGitHub -> EmptyState(stringResource(R.string.git_issues_not_github))
            !hasToken -> EmptyState(stringResource(R.string.git_issues_need_token))
            loading -> IssueLoading()
            error != null -> IssueError(onRetry = onLoad)
            issues.isEmpty() -> EmptyState(stringResource(R.string.git_no_issues))
            else -> {
                val sorted = remember(issues, sort) { sortIssues(issues, sort) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = floatingTabBarContentBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(sorted, key = { it.number }) { issue ->
                        IssueRow(
                            issue = issue,
                            selectionMode = selectionMode,
                            selected = issue.number in selected,
                            onOpen = { onOpenIssue(issue.number) },
                            onToggleSelection = { onToggleSelection(issue.number) },
                            onLongPress = onToggleSelectionMode
                        )
                    }
                }
            }
        }
    }

    if (labelDialogVisible) {
        AlertDialog(
            onDismissRequest = { labelDialogVisible = false },
            title = { Text(stringResource(R.string.git_issue_bulk_label_hint)) },
            text = {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.git_issue_label_names)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val names = labelInput.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                        if (names.isNotEmpty()) onBulkAddLabels(names)
                        labelDialogVisible = false
                        labelInput = ""
                    }
                ) { Text(stringResource(R.string.git_issue_bulk_label_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { labelDialogVisible = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (assigneeDialogVisible) {
        AlertDialog(
            onDismissRequest = { assigneeDialogVisible = false },
            title = { Text(stringResource(R.string.git_issue_bulk_assign_hint)) },
            text = {
                OutlinedTextField(
                    value = assigneeInput,
                    onValueChange = { assigneeInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.git_issue_bulk_assign_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val names = assigneeInput.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                        if (names.isNotEmpty()) onBulkAssign(names)
                        assigneeDialogVisible = false
                        assigneeInput = ""
                    }
                ) { Text(stringResource(R.string.git_issue_bulk_assign_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { assigneeDialogVisible = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    busy: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onBulkClose: () -> Unit,
    onBulkAddLabel: () -> Unit,
    onBulkAssign: () -> Unit
) {
    Surface(color = MaterialTheme.semanticColors.cardSurface, shape = RoundedCornerShape(Radius.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.git_issue_selected_count, selectedCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.git_issue_clear_selection)) }
                TextButton(onClick = onSelectAll) { Text(stringResource(R.string.git_issue_select_all)) }
                TextButton(onClick = onBulkClose, enabled = !busy && selectedCount > 0) {
                    Text(stringResource(R.string.git_issue_bulk_close))
                }
                TextButton(onClick = onBulkAddLabel, enabled = !busy && selectedCount > 0) {
                    Text(stringResource(R.string.git_issue_bulk_add_label))
                }
                TextButton(onClick = onBulkAssign, enabled = !busy && selectedCount > 0) {
                    Icon(FeatherIcons.User, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.git_issue_bulk_assign))
                }
            }
        }
    }
}

/**
 * 排序入口：顶部区最右侧的低调小箭头，点击弹 DropdownMenu 列出全部排序选项；
 * 当前选中项用主题色标注（不再用一排高亮 chip，避免抢注意力）。
 */
@Composable
private fun SortDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.git_issue_sort_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortMenuItem("newest", R.string.git_issue_sort_newest, current, onSelect) { expanded = false }
            SortMenuItem("oldest", R.string.git_issue_sort_oldest, current, onSelect) { expanded = false }
            SortMenuItem("updated", R.string.git_issue_sort_updated, current, onSelect) { expanded = false }
            SortMenuItem("comments", R.string.git_issue_sort_comments, current, onSelect) { expanded = false }
        }
    }
}

@Composable
private fun SortMenuItem(
    value: String,
    labelRes: Int,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                color = if (current == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = {
            onDismiss()
            onSelect(value)
        }
    )
}

@Composable
private fun IssueFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.semanticColors.cardSurface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.semanticColors.subtleBorder,
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun IssueLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.git_issues_loading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IssueError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.git_issues_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssueRow(
    issue: GitHubIssue,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit
) {
    val open = issue.state == "open"
    val stateColor = if (open) IssueOpenColor else IssueClosedColor
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.semanticColors.cardSurface,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelection else onOpen,
                onLongClick = if (selectionMode) null else onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                    Spacer(Modifier.width(Spacing.xs))
                } else {
                    IssueStateIcon(
                        open = open,
                        color = stateColor,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                // 创建者头像（与 git 提交记录一致；加载失败时留空，名字由下方元信息行展示）。
                if (issue.userLogin.isNotEmpty()) {
                    IssueAvatar(
                        name = issue.userLogin,
                        size = 18.dp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Spacing.xs))
                // 序号：不点进详情也能直接看。
                Text(
                    text = "#${issue.number}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${issue.userLogin} • ${formatIssueDate(issue.createdAt)} • ${
                    stringResource(R.string.git_issue_comments, issue.comments)
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 受理人：头像 + 名字（头像失败时仍显示名字）。
            if (issue.assignees.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    issue.assignees.take(3).forEach { assignee ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            IssueAvatar(name = assignee.login, size = 16.dp)
                            Text(
                                text = assignee.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (issue.labels.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    issue.labels.take(4).forEach { label -> LabelChip(label) }
                }
            }
        }
    }
}

/**
 * issue 用户头像（Coil 异步加载，按 URL 缓存，同一 URL 不重复拉取）：
 * 加载失败或用户名无法生成头像 URL 时不渲染头像，由调用方在其旁显示名字文字作为降级（无首字母占位）。
 */
@Composable
private fun IssueAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val url = githubAvatarUrl(name)
    if (url.isEmpty()) return
    var failed by remember { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    )
}

@Composable
internal fun LabelChip(label: IssueLabel) {
    val bg = label.color?.let { hex ->
        runCatching { Color(0xFF000000L or hex.toLong(16)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.surfaceVariant
    Surface(
        color = bg.copy(alpha = 0.18f),
        shape = RoundedCornerShape(Radius.pill)
    ) {
        Text(
            text = label.name,
            style = MaterialTheme.typography.labelSmall,
            color = bg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** GitHub 时间戳（ISO-8601）→ 日期部分；解析失败返回原串。 */
internal fun formatIssueDate(iso: String): String = iso.substringBefore('T').ifBlank { iso }

/**
 * issue 状态图标：open 用 GitHub 官方观感「外圈描边圆 + 圆心实心小点」（中心点直径约外圈直径 40%）；
 * closed 保持原有打勾圆。
 */
@Composable
internal fun IssueStateIcon(open: Boolean, color: Color, modifier: Modifier = Modifier) {
    if (!open) {
        Icon(
            imageVector = FeatherIcons.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = modifier
        )
    } else {
        Canvas(modifier = modifier) {
            val stroke = size.minDimension * 0.12f
            val outerRadius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color,
                radius = outerRadius,
                center = center,
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = color,
                radius = outerRadius * 0.4f,
                center = center
            )
        }
    }
}
