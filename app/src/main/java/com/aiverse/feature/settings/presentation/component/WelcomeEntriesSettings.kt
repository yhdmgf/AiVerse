package com.aiverse.feature.settings.presentation.component

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.AppSwitch
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.SegmentedTabs
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeEntryAction
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_ALERT_CIRCLE
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_CODE
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_DEFAULT
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_GIT_BRANCH
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_TERMINAL
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.Bookmark
import compose.icons.feathericons.Calendar
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.Clock
import compose.icons.feathericons.Code
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.Database
import compose.icons.feathericons.Edit
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Folder
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Layers
import compose.icons.feathericons.MessageCircle
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Search
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Share
import compose.icons.feathericons.Star
import compose.icons.feathericons.Tag
import compose.icons.feathericons.Target
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Tool
import compose.icons.feathericons.Upload
import compose.icons.feathericons.User
import compose.icons.feathericons.Zap
import java.util.UUID

/** 欢迎卡片（新对话入口）数量上限，与仓库侧保持一致（仓库私有常量）。 */
internal const val WELCOME_ENTRIES_MAX = 6

/** 矢量图标选择网格每行列数（图标槽位数量），保证排布整齐。 */
private const val WELCOME_ICON_GRID_COLUMNS = 5

/** 点击后行为 -> 分段控件标签资源。 */
internal fun WelcomeEntryAction.labelRes(): Int = when (this) {
    WelcomeEntryAction.OPEN_TERMINAL -> R.string.settings_welcome_entry_action_terminal
    WelcomeEntryAction.OPEN_GIT -> R.string.settings_welcome_entry_action_git
    WelcomeEntryAction.FILL_PROMPT -> R.string.settings_welcome_entry_action_fill
    WelcomeEntryAction.OPEN_BROWSER -> R.string.settings_welcome_entry_action_browser
    WelcomeEntryAction.OPEN_HISTORY -> R.string.settings_welcome_entry_action_history
}

/**
 * 可选矢量图标（Feather）简单名列表：图标选择弹层的矢量网格。
 * 均为 compose.icons.feathericons.* 既有图标；预置入口的图标名也在其中。
 */
private val WELCOME_VECTOR_ICONS = listOf(
    VECTOR_ICON_TERMINAL,
    VECTOR_ICON_GIT_BRANCH,
    VECTOR_ICON_CODE,
    VECTOR_ICON_ALERT_CIRCLE,
    VECTOR_ICON_DEFAULT,
    "Search",
    "FileText",
    "Folder",
    "Settings",
    "Zap",
    "Bookmark",
    "Target",
    "Cpu",
    "Database",
    "Tool",
    "Globe",
    "MessageSquare",
    "Edit",
    "Calendar",
    "Clock",
    "Star",
    "User",
    "Tag",
    "Share",
    "Layers"
)

/**
 * 新对话入口管理弹层：列出全部入口（含停用项），支持启停、排序、点击编辑、左滑删除；
 * 顶部「恢复预设」与列表末尾「添加入口」（满 [WELCOME_ENTRIES_MAX] 禁用）。删除/恢复均有二次确认。
 *
 * @param entries 全部欢迎卡片快照（含预置与自定义，按展示顺序）。
 * @param onAddEntry 新增（满 6 个时由调用方决定是否提示）。
 * @param onUpdateEntry 更新。
 * @param onRemoveEntry 删除（按 id）。
 * @param onSetEntryEnabled 启用/停用。
 * @param onMoveEntry 移动到目标索引（0 起）。
 * @param onRestoreDefaults 恢复默认 4 个。
 * @param onSaveIconImage 把所选图片保存为图标文件，回调返回绝对路径或 null（失败）。
 * @param onDismiss 关闭弹层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeEntriesSheet(
    entries: List<WelcomeEntry>,
    onAddEntry: (WelcomeEntry) -> Unit,
    onUpdateEntry: (WelcomeEntry) -> Unit,
    onRemoveEntry: (String) -> Unit,
    onSetEntryEnabled: (String, Boolean) -> Unit,
    onMoveEntry: (String, Int) -> Unit,
    onRestoreDefaults: () -> Unit,
    onSaveIconImage: (Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var editingTarget by remember { mutableStateOf<WelcomeEntry?>(null) }
    var addingNew by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var deletingTarget by remember { mutableStateOf<WelcomeEntry?>(null) }
    val sheetState = rememberModalBottomSheetState()

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_welcome_entries),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { restoreConfirm = true }) {
                    Text(stringResource(R.string.settings_welcome_entries_restore_preset))
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_welcome_entries_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.semanticColors.subtleText,
                    modifier = Modifier.padding(vertical = Spacing.lg)
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    WelcomeEntryRow(
                        entry = entry,
                        canMoveUp = index > 0,
                        canMoveDown = index < entries.lastIndex,
                        onToggleEnabled = { onSetEntryEnabled(entry.id, it) },
                        onMoveUp = { onMoveEntry(entry.id, index - 1) },
                        onMoveDown = { onMoveEntry(entry.id, index + 1) },
                        onClick = { editingTarget = entry },
                        onDeleteRequest = { deletingTarget = entry }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = { addingNew = true },
                enabled = entries.size < WELCOME_ENTRIES_MAX,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(FeatherIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.settings_welcome_entries_add))
            }
        }
    }

    if (restoreConfirm) {
        AlertDialog(
            onDismissRequest = { restoreConfirm = false },
            title = { Text(stringResource(R.string.settings_welcome_entries_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_welcome_entries_restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreConfirm = false
                        onRestoreDefaults()
                    }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    deletingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTarget = null },
            title = { Text(stringResource(R.string.settings_welcome_entries_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_welcome_entries_delete_confirm_message, target.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingTarget = null
                        onRemoveEntry(target.id)
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (editingTarget != null || addingNew) {
        WelcomeEntryEditSheet(
            initial = editingTarget,
            onSave = { entry ->
                if (addingNew) onAddEntry(entry) else onUpdateEntry(entry)
                editingTarget = null
                addingNew = false
            },
            onDelete = { id ->
                onRemoveEntry(id)
                editingTarget = null
            },
            onSaveIconImage = onSaveIconImage,
            onDismiss = {
                editingTarget = null
                addingNew = false
            }
        )
    }
}

/** 管理列表单行：图标缩略 + 标题/说明 + 启停开关 + 上下移按钮，整行点击编辑，左滑删除。 */
@Composable
private fun WelcomeEntryRow(
    entry: WelcomeEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    SwipeToDeleteRow(
        onDelete = onDeleteRequest,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeEntryIconView(iconKind = entry.iconKind, iconValue = entry.iconValue)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.semanticColors.subtleText
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AppSwitch(checked = entry.enabled, onCheckedChange = onToggleEnabled)
            Spacer(Modifier.width(Spacing.xs))
            Column(verticalArrangement = Arrangement.Center) {
                MoveIconButton(
                    icon = FeatherIcons.ChevronUp,
                    enabled = canMoveUp,
                    onClick = onMoveUp
                )
                MoveIconButton(
                    icon = FeatherIcons.ChevronDown,
                    enabled = canMoveDown,
                    onClick = onMoveDown
                )
            }
        }
    }
}

/** 上下移小按钮：收窄最小触摸目标，避免两枚按钮撑高整行。 */
@Composable
private fun MoveIconButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.semanticColors.subtleText.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * [WelcomeIconKind.VECTOR] 的 iconValue（Feather 图标简单名）→ ImageVector；未知名回退 MessageCircle。
 * 供设置页图标槽渲染；其他区块如需渲染 VECTOR 也复用本函数。
 */
internal fun welcomeEntryVectorIcon(name: String): ImageVector = when (name) {
    VECTOR_ICON_TERMINAL -> FeatherIcons.Terminal
    VECTOR_ICON_GIT_BRANCH -> FeatherIcons.GitBranch
    VECTOR_ICON_CODE -> FeatherIcons.Code
    VECTOR_ICON_ALERT_CIRCLE -> FeatherIcons.AlertCircle
    VECTOR_ICON_DEFAULT -> FeatherIcons.MessageCircle
    "Search" -> FeatherIcons.Search
    "FileText" -> FeatherIcons.FileText
    "Folder" -> FeatherIcons.Folder
    "Settings" -> FeatherIcons.Settings
    "Zap" -> FeatherIcons.Zap
    "Bookmark" -> FeatherIcons.Bookmark
    "Target" -> FeatherIcons.Target
    "Cpu" -> FeatherIcons.Cpu
    "Database" -> FeatherIcons.Database
    "Tool" -> FeatherIcons.Tool
    "Globe" -> FeatherIcons.Globe
    "MessageSquare" -> FeatherIcons.MessageSquare
    "Edit" -> FeatherIcons.Edit
    "Calendar" -> FeatherIcons.Calendar
    "Clock" -> FeatherIcons.Clock
    "Star" -> FeatherIcons.Star
    "User" -> FeatherIcons.User
    "Tag" -> FeatherIcons.Tag
    "Share" -> FeatherIcons.Share
    "Layers" -> FeatherIcons.Layers
    else -> FeatherIcons.MessageCircle
}

/**
 * 新增/编辑共用弹层：预览卡片 + 标题/说明 + 行为分段 + 提示词（填充提示词时）+ 图标选择 + 保存/删除。
 *
 * @param initial 编辑现有项；null 表示新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeEntryEditSheet(
    initial: WelcomeEntry?,
    onSave: (WelcomeEntry) -> Unit,
    onDelete: (String) -> Unit,
    onSaveIconImage: (Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initial != null
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subtitle by remember { mutableStateOf(initial?.subtitle ?: "") }
    var action by remember { mutableStateOf(initial?.action ?: WelcomeEntryAction.FILL_PROMPT) }
    var promptText by remember { mutableStateOf(initial?.promptText ?: "") }
    var iconKind by remember { mutableStateOf(initial?.iconKind ?: WelcomeIconKind.VECTOR) }
    var iconValue by remember { mutableStateOf(initial?.iconValue ?: VECTOR_ICON_DEFAULT) }
    var showIconPicker by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(
                    if (isEdit) R.string.settings_welcome_entry_edit_title
                    else R.string.settings_welcome_entry_add_title
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WelcomeEntryPreviewCard(
                iconKind = iconKind,
                iconValue = iconValue,
                title = title,
                subtitle = subtitle
            )

            AppTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                label = stringResource(R.string.settings_welcome_entry_title),
                placeholder = stringResource(R.string.settings_welcome_entry_title_hint),
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.settings_welcome_entry_title_empty)) }
                } else {
                    null
                },
                colors = dialogTextFieldColors()
            )

            AppTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = stringResource(R.string.settings_welcome_entry_subtitle),
                placeholder = stringResource(R.string.settings_welcome_entry_subtitle_hint),
                colors = dialogTextFieldColors()
            )

            // stringResource 不能在非组合 lambda 里调用，提前在当前组合作用域取好标签。
            val actionOptions = WelcomeEntryAction.entries.map { entryAction ->
                entryAction to stringResource(entryAction.labelRes())
            }
            var actionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = actionExpanded,
                onExpandedChange = { actionExpanded = it }
            ) {
                AppTextField(
                    value = actionOptions.firstOrNull { it.first == action }?.second.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = stringResource(R.string.settings_welcome_entry_action),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    colors = dialogTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { actionExpanded = false }
                ) {
                    actionOptions.forEach { (entryAction, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                action = entryAction
                                actionExpanded = false
                            }
                        )
                    }
                }
            }

            if (action == WelcomeEntryAction.FILL_PROMPT) {
                AppTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = stringResource(R.string.settings_welcome_entry_prompt),
                    placeholder = stringResource(R.string.settings_welcome_entry_prompt_hint),
                    singleLine = false,
                    minLines = 2,
                    colors = dialogTextFieldColors()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_welcome_entry_icon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { showIconPicker = true }) {
                    Text(stringResource(R.string.settings_welcome_entry_icon_select))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (isEdit) {
                    OutlinedButton(
                        onClick = { deleteConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                        } else {
                            onSave(
                                WelcomeEntry(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    title = title.trim(),
                                    subtitle = subtitle.trim(),
                                    iconKind = iconKind,
                                    iconValue = iconValue,
                                    action = action,
                                    promptText = if (action == WelcomeEntryAction.FILL_PROMPT) {
                                        promptText.trim()
                                    } else {
                                        ""
                                    },
                                    enabled = initial?.enabled ?: true,
                                    isPreset = initial?.isPreset ?: false
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.settings_welcome_entries_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_welcome_entries_delete_confirm_message, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        initial?.id?.let(onDelete)
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showIconPicker) {
        WelcomeIconPickerSheet(
            initialKind = iconKind,
            initialValue = iconValue,
            onSaveIconImage = onSaveIconImage,
            onConfirm = { kind, value ->
                iconKind = kind
                iconValue = value
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}

/** 编辑弹层顶部预览卡片：图标 + 标题 + 说明（随表单实时联动）。 */
@Composable
private fun WelcomeEntryPreviewCard(
    iconKind: WelcomeIconKind,
    iconValue: String,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(Radius.mdLarge),
        color = MaterialTheme.semanticColors.cardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeEntryIconView(
                iconKind = iconKind,
                iconValue = iconValue,
                size = 48.dp
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { stringResource(R.string.settings_welcome_entry_title_hint) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 图标选择弹层：矢量图标（Feather 网格）/ 文字（≤3 字）/ 上传图片（保存到本地路径）。
 * 点「完成」回传所选 [WelcomeIconKind] 与值。
 *
 * @param initialKind 当前图标类型（决定初始分段）。
 * @param initialValue 当前图标值。
 * @param onSaveIconImage 上传图片时保存到本地，回调返回绝对路径或 null（失败）。
 * @param onConfirm 用户点「完成」后回传所选类型与值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeIconPickerSheet(
    initialKind: WelcomeIconKind,
    initialValue: String,
    onSaveIconImage: (Uri, (String?) -> Unit) -> Unit,
    onConfirm: (WelcomeIconKind, String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialTab = when (initialKind) {
        // EMOJI 为旧数据兼容，加载时已迁移为 VECTOR，此处并入矢量分段兜底。
        WelcomeIconKind.VECTOR, WelcomeIconKind.EMOJI -> 0
        WelcomeIconKind.TEXT -> 1
        WelcomeIconKind.IMAGE -> 2
    }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var vectorIcon by remember {
        mutableStateOf(if (initialKind == WelcomeIconKind.VECTOR) initialValue.ifBlank { VECTOR_ICON_DEFAULT } else "")
    }
    var text by remember { mutableStateOf(if (initialKind == WelcomeIconKind.TEXT) initialValue else "") }
    var imagePath by remember { mutableStateOf(if (initialKind == WelcomeIconKind.IMAGE) initialValue else "") }
    var uploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            onSaveIconImage(uri) { path ->
                uploading = false
                if (path != null) {
                    imagePath = path
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_welcome_icon_upload_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    val sheetState = rememberModalBottomSheetState()

    // 「完成」是否可用：当前分段下已有合法选择（上传中或为空时禁用）。
    val canDone = when (selectedTab) {
        0 -> vectorIcon.isNotBlank()
        1 -> text.isNotBlank()
        else -> imagePath.isNotBlank() && !uploading
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.settings_welcome_icon_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SegmentedTabs(
                selected = selectedTab,
                labels = listOf(
                    stringResource(R.string.settings_welcome_icon_vector),
                    stringResource(R.string.settings_welcome_icon_text),
                    stringResource(R.string.settings_welcome_icon_image)
                ),
                onSelect = { selectedTab = it }
            )

            when (selectedTab) {
                0 -> VectorIconPickerSection(
                    selected = vectorIcon,
                    onSelect = { vectorIcon = it }
                )
                1 -> AppTextField(
                    value = text,
                    onValueChange = { if (it.length <= 3) text = it },
                    label = stringResource(R.string.settings_welcome_icon_text),
                    placeholder = stringResource(R.string.settings_welcome_icon_text_hint),
                    singleLine = true,
                    colors = dialogTextFieldColors()
                )
                else -> ImagePickerSection(
                    imagePath = imagePath,
                    uploading = uploading,
                    onPick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            Button(
                onClick = {
                    val (kind, value) = when (selectedTab) {
                        0 -> WelcomeIconKind.VECTOR to vectorIcon
                        1 -> WelcomeIconKind.TEXT to text
                        else -> WelcomeIconKind.IMAGE to imagePath
                    }
                    onConfirm(kind, value)
                },
                enabled = canDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

/** 矢量图标分段内容：Feather 图标整齐网格点选（每行 5 个圆角槽位，选中高亮）。 */
@Composable
private fun VectorIconPickerSection(
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        WELCOME_VECTOR_ICONS.chunked(WELCOME_ICON_GRID_COLUMNS).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)
            ) {
                rowIcons.forEach { iconName ->
                    val isSelected = iconName == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.semanticColors.mutedSurface
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(Radius.md)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(iconName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = welcomeEntryVectorIcon(iconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 上传图片分段内容：当前图标预览 + 选择图片按钮（含保存中状态）。 */
@Composable
private fun ImagePickerSection(
    imagePath: String,
    uploading: Boolean,
    onPick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (imagePath.isNotBlank()) {
            WelcomeEntryIconView(
                iconKind = WelcomeIconKind.IMAGE,
                iconValue = imagePath,
                size = 64.dp
            )
        }
        Button(
            onClick = onPick,
            enabled = !uploading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(FeatherIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                stringResource(
                    if (uploading) R.string.settings_welcome_icon_uploading
                    else R.string.settings_welcome_icon_image_pick
                )
            )
        }
    }
}

/**
 * 欢迎卡片图标视图：VECTOR 用 Feather 矢量图标，TEXT 用文本渲染，IMAGE 用 coil 异步加载（圆角裁切），
 * EMOJI 为旧数据兼容回退（渲染默认矢量图标）。
 * 图标槽统一 40dp 底色卡片，供管理列表/编辑预览/图标选择共用。
 */
@Composable
internal fun WelcomeEntryIconView(
    iconKind: WelcomeIconKind,
    iconValue: String,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.semanticColors.mutedSurface),
        contentAlignment = Alignment.Center
    ) {
        when (iconKind) {
            WelcomeIconKind.IMAGE -> {
                if (iconValue.isNotBlank()) {
                    AsyncImage(
                        model = iconValue,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            WelcomeIconKind.VECTOR -> {
                Icon(
                    imageVector = welcomeEntryVectorIcon(iconValue),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            // 文字图标：≤3 字，用小一号字号避免撑破固定图标槽。
            WelcomeIconKind.TEXT -> {
                Text(
                    text = iconValue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // EMOJI：旧数据兼容兜底；加载时已迁移为 VECTOR，此处仅渲染默认矢量图标。
            else -> {
                Icon(
                    imageVector = welcomeEntryVectorIcon(VECTOR_ICON_DEFAULT),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}
