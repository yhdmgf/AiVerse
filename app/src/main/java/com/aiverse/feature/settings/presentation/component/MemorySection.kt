package com.aiverse.feature.settings.presentation.component

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.feature.agent.domain.memory.Memory
import com.aiverse.feature.agent.domain.memory.MemoryScope
import com.aiverse.feature.settings.presentation.MemoryViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckSquare
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X

/** 编辑目标：null 表示新建，非 null 为编辑既有记忆。 */
private data class MemoryEditorTarget(
    val memory: Memory?,
    val scope: MemoryScope = MemoryScope.GLOBAL
)

/**
 * 记忆设置页：全局记忆与项目记忆分两组卡片展示，风格与「提示词」页一致
 * （白卡行 + 图标 + 标题/摘要 + 来源徽章）。点击行进入编辑，底部可新建。
 */
@Composable
internal fun MemorySection(viewModel: MemoryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editorTarget by remember { mutableStateOf<MemoryEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<Memory?>(null) }
    // 多选模式状态：铅笔进入 / 退出，勾选行切换选中。
    val selection = remember { ListSelectionState() }
    var deleteSelectedConfirm by remember { mutableStateOf(false) }
    val allMemories = remember(state.global, state.project) { state.global + state.project }
    val savedText = stringResource(R.string.memory_saved)
    val saveFailedText = stringResource(R.string.memory_save_failed)
    val deletedText = stringResource(R.string.memory_deleted)
    val deleteFailedText = stringResource(R.string.memory_delete_failed)

    LaunchedEffect(Unit) { viewModel.refresh() }
    // 多选模式返回键退出；数据刷新后剔除已失效的选中项（被删除/消失的记忆）。
    BackHandler(enabled = selection.isActive) { selection.exit() }
    LaunchedEffect(state.global, state.project) {
        selection.retainOnly(allMemories.map { memoryKey(it) }.toSet())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
    ) {
        MemoryTopBar(
            selection = selection,
            allKeys = allMemories.map { memoryKey(it) },
            onAddNew = { editorTarget = MemoryEditorTarget(null) },
            onDeleteSelected = { deleteSelectedConfirm = true }
        )
        Spacer(Modifier.height(Spacing.md))
        SettingsGroupHeader(text = stringResource(R.string.memory_global_title))
        SettingsGroup {
            if (state.global.isEmpty()) {
                MemoryEmptyRow(text = stringResource(R.string.memory_empty_global))
            } else {
                state.global.forEachIndexed { index, memory ->
                    if (index > 0) SettingsDivider()
                    MemoryRow(
                        memory = memory,
                        badge = stringResource(R.string.memory_scope_global),
                        onClick = { editorTarget = MemoryEditorTarget(memory) },
                        selection = selection,
                        selectionKey = memoryKey(memory)
                    )
                }
            }
        }

        val projectHeader = if (state.hasWorkspace && state.projectName.isNotBlank()) {
            stringResource(R.string.memory_project_title_named, state.projectName)
        } else {
            stringResource(R.string.memory_project_title)
        }
        SettingsGroupHeader(text = projectHeader)
        SettingsGroup {
            when {
                !state.hasWorkspace -> MemoryEmptyRow(text = stringResource(R.string.memory_no_workspace))
                state.project.isEmpty() -> MemoryEmptyRow(text = stringResource(R.string.memory_empty_project))
                else -> state.project.forEachIndexed { index, memory ->
                    if (index > 0) SettingsDivider()
                    MemoryRow(
                        memory = memory,
                        badge = stringResource(R.string.memory_scope_project),
                        onClick = { editorTarget = MemoryEditorTarget(memory) },
                        selection = selection,
                        selectionKey = memoryKey(memory)
                    )
                }
            }
        }
    }

    editorTarget?.let { target ->
        MemoryEditorDialog(
            target = target,
            hasWorkspace = state.hasWorkspace,
            onDismiss = { editorTarget = null },
            onDelete = { memory -> deleteTarget = memory },
            onSave = { name, description, content, scope ->
                viewModel.save(name, description, content, scope) { ok ->
                    if (ok) {
                        Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
                        editorTarget = null
                    } else {
                        Toast.makeText(context, saveFailedText, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    deleteTarget?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.memory_delete_confirm_title)) },
            text = { Text(stringResource(R.string.memory_delete_confirm_message, memory.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(memory) { ok ->
                        editorTarget = null
                        Toast.makeText(
                            context,
                            if (ok) deletedText else deleteFailedText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (deleteSelectedConfirm) {
        val selectedMemories = allMemories.filter { selection.isSelected(memoryKey(it)) }
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.memory_delete_selected_confirm_title),
            message = stringResource(R.string.memory_delete_selected_confirm_message, selectedMemories.size),
            onConfirm = {
                deleteSelectedConfirm = false
                viewModel.deleteMemories(selectedMemories) { ok ->
                    selection.exit()
                    Toast.makeText(
                        context,
                        if (ok) deletedText else deleteFailedText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { deleteSelectedConfirm = false }
        )
    }
}

@Composable
private fun MemoryEmptyRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 记忆行：普通模式点击进编辑；多选模式（[selection] 非空）左侧出现勾选框，点击行切换选中。
 */
@Composable
private fun MemoryRow(
    memory: Memory,
    badge: String,
    onClick: () -> Unit,
    selection: ListSelectionState? = null,
    selectionKey: String? = null
) {
    if (selection == null || selectionKey == null) {
        SettingsRow(
            icon = FeatherIcons.FileText,
            title = memory.name,
            subtitle = memory.description.takeIf { it.isNotBlank() },
            onClick = onClick,
            trailing = { MemoryBadge(badge) }
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selection.toggle(selectionKey) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selection.isSelected(selectionKey),
                onCheckedChange = { selection.toggle(selectionKey) }
            )
            SettingsRow(
                icon = FeatherIcons.FileText,
                title = memory.name,
                subtitle = memory.description.takeIf { it.isNotBlank() },
                onClick = null,
                trailing = { MemoryBadge(badge) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MemoryBadge(badge: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * 记忆页顶部操作栏：
 * - 普通模式：左上角铅笔进入多选；右上角箭头（新建并展开）+ 加号（新建记忆）。
 * - 多选模式：左侧显示已选数量，右侧为全选 / 删除 / 取消退出。
 */
@Composable
private fun MemoryTopBar(
    selection: ListSelectionState,
    allKeys: List<String>,
    onAddNew: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!selection.isActive) {
            IconButton(onClick = { selection.enter() }) {
                Icon(
                    imageVector = FeatherIcons.Edit2,
                    contentDescription = stringResource(R.string.common_select_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddNew) {
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = stringResource(R.string.memory_add_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onAddNew) {
                Icon(
                    imageVector = FeatherIcons.Plus,
                    contentDescription = stringResource(R.string.memory_add),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            val allSelected = allKeys.isNotEmpty() && selection.selectedCount == allKeys.size
            Text(
                text = stringResource(R.string.common_selected_count, selection.selectedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (allSelected) selection.clear() else selection.replaceAll(allKeys)
                }
            ) {
                Icon(
                    imageVector = FeatherIcons.CheckSquare,
                    contentDescription = stringResource(
                        if (allSelected) R.string.common_deselect_all else R.string.common_select_all
                    ),
                    tint = if (allSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = selection.selectedCount > 0
            ) {
                Icon(
                    imageVector = FeatherIcons.Trash2,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = { selection.exit() }) {
                Icon(
                    imageVector = FeatherIcons.X,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 记忆的唯一多选键：同一作用域内名称唯一（对应文件名），组合避免全局/项目同名冲突。 */
private fun memoryKey(memory: Memory): String = "${memory.scope.name}::${memory.name}"

@Composable
private fun MemoryEditorDialog(
    target: MemoryEditorTarget,
    hasWorkspace: Boolean,
    onDismiss: () -> Unit,
    onDelete: (Memory) -> Unit,
    onSave: (String, String, String, MemoryScope) -> Unit
) {
    val editing = target.memory
    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var description by remember(editing) { mutableStateOf(editing?.description.orEmpty()) }
    var content by remember(editing) { mutableStateOf(editing?.content.orEmpty()) }
    var scope by remember(editing, target.scope) {
        mutableStateOf(editing?.scope ?: target.scope)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (editing == null) R.string.memory_editor_new else R.string.memory_editor_edit
                ),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (editing == null) {
                    Text(
                        text = stringResource(R.string.memory_scope),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ScopeChip(
                            label = stringResource(R.string.memory_scope_global),
                            selected = scope == MemoryScope.GLOBAL,
                            enabled = true,
                            onClick = { scope = MemoryScope.GLOBAL }
                        )
                        ScopeChip(
                            label = stringResource(R.string.memory_scope_project),
                            selected = scope == MemoryScope.PROJECT,
                            enabled = hasWorkspace,
                            onClick = { scope = MemoryScope.PROJECT }
                        )
                    }
                    if (!hasWorkspace) {
                        Text(
                            text = stringResource(R.string.memory_no_workspace),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.memory_name),
                    placeholder = stringResource(R.string.memory_name_hint),
                    enabled = editing == null
                )
                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.memory_description),
                    placeholder = stringResource(R.string.memory_description_hint)
                )
                AppTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = stringResource(R.string.memory_content),
                    placeholder = stringResource(R.string.memory_content_hint),
                    singleLine = false,
                    maxLines = 8,
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim(), content, scope) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = { onDelete(editing) }) {
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) bg else bg.copy(alpha = 0.4f),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
