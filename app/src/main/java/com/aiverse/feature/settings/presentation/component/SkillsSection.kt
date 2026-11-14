package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.core.ui.SelectionActionBar
import com.aiverse.core.ui.selectionListBottomContentPadding
import com.aiverse.core.ui.SelectionActionButton
import com.aiverse.core.ui.SelectionActionIconButton
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.feature.agent.domain.skill.SkillScope
import com.aiverse.feature.settings.presentation.SkillUiEntry
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.Box
import compose.icons.feathericons.Check
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Download
import compose.icons.feathericons.Share2
import compose.icons.feathericons.Trash2

/**
 * 技能二级页：与「工具授权」一致的折叠分组列表——「当前项目 / 全局」两组各自可折叠，
 * 每行一个技能（图标 + 名称 + 描述），左滑删除，点击行进入详情。
 *
 * 列表走 [LazyColumn] 只组合可见行：技能数量随项目增长且没有上限，
 * 用 Column + verticalScroll 一次性组合全部行会让滚动明显掉帧。
 *
 * 顶栏右上角「多选」进入批量模式（[selection] 由 SettingsScreen 持有）：可批量启用 / 停用 / 删除。
 * 全选覆盖两组（含折叠起来的组），避免「点了全选却漏掉折叠里的」。
 */
@Composable
internal fun SkillsSection(
    projectName: String?,
    entries: List<SkillUiEntry>,
    selection: ListSelectionState,
    deletingInProgress: Boolean,
    onDelete: (SkillUiEntry) -> Unit,
    onOpenDetail: (SkillUiEntry) -> Unit,
    onBatchDelete: (List<SkillUiEntry>) -> Unit,
    onBatchSetEnabled: (List<SkillUiEntry>, Boolean) -> Unit,
    onBatchShare: (List<SkillUiEntry>) -> Unit,
    onBatchExport: (List<SkillUiEntry>) -> Unit,
    defaultSkillInstallScope: SkillScope,
    onSetDefaultSkillInstallScope: (SkillScope) -> Unit
) {
    val t = LocalUIDensityTokens.current
    // 只在数据变化时分组，避免每次重组都产生两份新 List。
    val (projectSkills, globalSkills) = remember(entries) {
        entries.filter { it.scope == SkillScope.PROJECT } to entries.filter { it.scope == SkillScope.GLOBAL }
    }

    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val allKeys = remember(entries) { entries.map { skillSelectionKey(it) } }
    val keyToEntry = remember(entries) { entries.associateBy { skillSelectionKey(it) } }

    // 列表刷新后剔除已消失的选中项；全空了就退出多选。
    LaunchedEffect(allKeys, selection.isActive) {
        selection.retainOnly(allKeys.toSet())
        if (selection.isActive && entries.isEmpty()) selection.exit()
    }

    var projectExpanded by rememberSaveable { mutableStateOf(true) }
    var globalExpanded by rememberSaveable { mutableStateOf(true) }
    val allSelected = selection.selectedCount > 0 && selection.selectedCount == allKeys.size
    val selectedEntries: List<SkillUiEntry> = selection.selectedKeys.mapNotNull { keyToEntry[it] }

    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 顶部始终渲染「技能默认安装作用域」设置项：技能为空时也可见（issue #85）。
        SkillDefaultScopeSetting(
            defaultSkillInstallScope = defaultSkillInstallScope,
            onSetDefaultSkillInstallScope = onSetDefaultSkillInstallScope
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.lg)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            FeatherIcons.Book,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.skills_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.skills_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
        // 批量删除期间给一行文字提示：远程模式下逐条 SFTP 递归删可能持续数秒，没有反馈会以为没生效。
        if (deletingInProgress) {
            Text(
                text = stringResource(R.string.common_batch_deleting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.xs)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = selectionListBottomContentPadding(selection.isActive)),
            verticalArrangement = Arrangement.spacedBy(t.listCellGap)
        ) {
            item(key = "project-header", contentType = "header") {
                CollapsibleGroupHeader(
                    text = if (projectName != null) {
                        stringResource(R.string.perm_current_project, projectName)
                    } else {
                        stringResource(R.string.perm_current_project_none)
                    },
                    expanded = projectExpanded,
                    onToggle = { projectExpanded = !projectExpanded }
                )
            }
            if (projectExpanded) {
                if (projectSkills.isEmpty()) {
                    item(key = "project-empty", contentType = "empty") {
                        SettingsGroup { SkillEmptyHint(stringResource(R.string.skills_no_project_skills)) }
                    }
                } else {
                    itemsIndexed(
                        items = projectSkills,
                        key = { _, entry -> "project:${entry.name}" },
                        contentType = { _, _ -> "skill-row" }
                    ) { index, entry ->
                        LazyGroupItem(index = index, lastIndex = projectSkills.lastIndex) {
                            SkillRow(
                                entry = entry,
                                onDelete = { onDelete(entry) },
                                onClick = { onOpenDetail(entry) },
                                selectionMode = selection.isActive,
                                selected = selection.isSelected(skillSelectionKey(entry)),
                                onToggleSelect = { selection.toggle(skillSelectionKey(entry)) }
                            )
                        }
                    }
                }
            }

            item(key = "global-header", contentType = "header") {
                CollapsibleGroupHeader(
                    text = stringResource(R.string.perm_global),
                    expanded = globalExpanded,
                    onToggle = { globalExpanded = !globalExpanded }
                )
            }
            if (globalExpanded) {
                if (globalSkills.isEmpty()) {
                    item(key = "global-empty", contentType = "empty") {
                        SettingsGroup { SkillEmptyHint(stringResource(R.string.skills_no_global_skills)) }
                    }
                } else {
                    itemsIndexed(
                        items = globalSkills,
                        key = { _, entry -> "global:${entry.name}" },
                        contentType = { _, _ -> "skill-row" }
                    ) { index, entry ->
                        LazyGroupItem(index = index, lastIndex = globalSkills.lastIndex) {
                            SkillRow(
                                entry = entry,
                                onDelete = { onDelete(entry) },
                                onClick = { onOpenDetail(entry) },
                                selectionMode = selection.isActive,
                                selected = selection.isSelected(skillSelectionKey(entry)),
                                onToggleSelect = { selection.toggle(skillSelectionKey(entry)) }
                            )
                        }
                    }
                }
            }
        }
        }
        }
        if (selection.isActive) {
            val enabled = selection.selectedCount > 0
            SelectionActionBar(
                selectedCount = selection.selectedCount,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) selection.clear() else selection.replaceAll(allKeys)
                },
                onExit = { selection.exit() },
                modifier = Modifier.align(Alignment.BottomCenter),
                moreActions = {
                    DropdownMenuItem(
                        text = { SkillMenuRow(FeatherIcons.Share2, stringResource(R.string.skills_share)) },
                        enabled = enabled,
                        onClick = { onBatchShare(selectedEntries) }
                    )
                    DropdownMenuItem(
                        text = { SkillMenuRow(FeatherIcons.Download, stringResource(R.string.skills_export)) },
                        enabled = enabled,
                        onClick = { onBatchExport(selectedEntries) }
                    )
                }
            ) {
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_enable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selectedEntries, true) }
                )
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_disable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selectedEntries, false) }
                )
                SelectionActionIconButton(
                    icon = FeatherIcons.Trash2,
                    contentDescription = stringResource(R.string.common_batch_delete),
                    enabled = enabled,
                    onClick = { showBatchDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showBatchDeleteConfirm) {
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.skills_batch_delete_title),
            message = stringResource(R.string.skills_batch_delete_confirm, selectedEntries.size),
            onConfirm = {
                showBatchDeleteConfirm = false
                selection.exit()
                onBatchDelete(selectedEntries)
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }
}

/** 多选 key：同名技能可能同时存在于项目层与全局层，必须带作用域前缀。 */
private fun skillSelectionKey(entry: SkillUiEntry): String = "${entry.scope.name}:${entry.name}"

/** 单个技能行：图标 + 名称/描述 + 右箭头；左滑删除，点击行进入详情。 */
@Composable
private fun SkillRow(
    entry: SkillUiEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    // 行背景由 SwipeToDeleteRow 提供，这里不再重复画一层，省一次整行绘制。
    SwipeToDeleteRow(
        onDelete = onDelete,
        onClick = onClick,
        selectionMode = selectionMode,
        selected = selected,
        onToggleSelect = onToggleSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.xs, top = t.listCellPaddingVertical, bottom = t.listCellPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    McpPill(
                        text = stringResource(if (entry.disabled) R.string.common_disabled else R.string.common_enabled),
                        textColor = if (entry.disabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary,
                        backgroundColor = (if (entry.disabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.12f)
                    )
                }
                Text(
                    text = entry.description.ifBlank { stringResource(R.string.mcp_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 分组内空状态：一行灰字，与行内容对齐。 */
@Composable
private fun SkillEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)
    )
}

/**
 * 「技能默认安装作用域」设置项：顶部设置组内一行，点击弹单选弹窗（项目 / 全局）。
 * 独立 DataStore（[SkillInstallSettingsRepository]）生效，与提示词列表无数据联动（issue #85）。
 */
@Composable
private fun SkillDefaultScopeSetting(
    defaultSkillInstallScope: SkillScope,
    onSetDefaultSkillInstallScope: (SkillScope) -> Unit
) {
    var showSkillScopeSheet by remember { mutableStateOf(false) }

    SettingsGroup(
        modifier = Modifier
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.sm)
    ) {
        SettingsRow(
            icon = FeatherIcons.Box,
            title = stringResource(R.string.prompt_skill_default_scope),
            subtitle = stringResource(
                if (defaultSkillInstallScope == SkillScope.PROJECT) {
                    R.string.prompt_skill_scope_project_hint
                } else {
                    R.string.prompt_skill_scope_global_hint
                }
            ),
            onClick = { showSkillScopeSheet = true },
            trailing = {
                Text(
                    text = stringResource(
                        if (defaultSkillInstallScope == SkillScope.PROJECT) {
                            R.string.prompt_skill_scope_project
                        } else {
                            R.string.prompt_skill_scope_global
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.semanticColors.subtleText
                )
            }
        )
    }

    if (showSkillScopeSheet) {
        AlertDialog(
            onDismissRequest = { showSkillScopeSheet = false },
            title = { Text(stringResource(R.string.prompt_skill_default_scope)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkillScopeOptionRow(
                        scope = SkillScope.PROJECT,
                        current = defaultSkillInstallScope,
                        onClick = {
                            onSetDefaultSkillInstallScope(SkillScope.PROJECT)
                            showSkillScopeSheet = false
                        }
                    )
                    SkillScopeOptionRow(
                        scope = SkillScope.GLOBAL,
                        current = defaultSkillInstallScope,
                        onClick = {
                            onSetDefaultSkillInstallScope(SkillScope.GLOBAL)
                            showSkillScopeSheet = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSkillScopeSheet = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 单选弹窗中的一行：作用域名 + 说明，当前选中项右侧打勾。 */
@Composable
private fun SkillScopeOptionRow(
    scope: SkillScope,
    current: SkillScope,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (scope == SkillScope.PROJECT) R.string.prompt_skill_scope_project
                    else R.string.prompt_skill_scope_global
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    if (scope == SkillScope.PROJECT) R.string.prompt_skill_scope_project_hint
                    else R.string.prompt_skill_scope_global_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semanticColors.subtleText
            )
        }
        if (scope == current) {
            Icon(
                FeatherIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
