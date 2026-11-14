package com.aiverse.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckSquare
import compose.icons.feathericons.MoreHorizontal

/**
 * 列表多选状态。key 由各页面自定义（提供商 id / `作用域:名称` / 提示词编号 / 镜像 id），
 * 这样一套组件能服务所有「可批量操作的字符串键列表」，页面侧只需提供 key 映射。
 *
 * 约定：
 * - key 必须在列表内唯一，且数据刷新后保持稳定（不要用下标）。
 * - 数据刷新后调用 [retainOnly] 剔除已消失的选中项，避免批量操作打到已删除的条目。
 * - 退出多选（[exit]）会清空选中集合，返回键与顶栏开关共用它。
 */
class ListSelectionState {

    /** 是否处于多选模式。false 时列表行为与改造前完全一致（左滑删除、点击进详情）。 */
    var isActive by mutableStateOf(false)
        private set

    private val _selected = mutableStateListOf<String>()

    val selectedCount: Int get() = _selected.size

    /** 当前选中 key 的快照：批量回调触发时取一次，避免边遍历边被改动。 */
    val selectedKeys: List<String> get() = _selected.toList()

    fun isSelected(key: String): Boolean = key in _selected

    fun toggle(key: String) {
        if (!_selected.remove(key)) _selected.add(key)
    }

    fun enter() {
        isActive = true
    }

    fun exit() {
        isActive = false
        _selected.clear()
    }

    fun clear() {
        _selected.clear()
    }

    /** 全选 / 取消全选：整体替换选中集合。 */
    fun replaceAll(keys: Collection<String>) {
        _selected.clear()
        _selected.addAll(keys)
    }

    /** 数据刷新后剔除失效 key（被删掉、被筛掉的条目）。 */
    fun retainOnly(keys: Set<String>) {
        _selected.retainAll(keys)
    }
}

/**
 * 创建一份多选状态。设置页各列表的多选状态由 SettingsScreen 统一持有（顶栏按钮在那边），
 * 离开列表页进详情 / 编辑时显式退出，因此这里用 remember 而不做跨配置持久化。
 */
@Composable
fun rememberListSelectionState(): ListSelectionState = remember { ListSelectionState() }

/**
 * 顶部栏右上角的多选开关：与模型页同一套图标与配色（`FeatherIcons.CheckSquare`）。
 * 列表为空时调用方不显示它；[enabled] = false 时置灰不可点（如提示词全部为只读来源，
 * 进多选也无项可勾）。
 */
@Composable
fun SelectionToggleAction(selection: ListSelectionState, enabled: Boolean = true) {
    IconButton(
        onClick = { if (selection.isActive) selection.exit() else selection.enter() },
        enabled = enabled
    ) {
        Icon(
            FeatherIcons.CheckSquare,
            contentDescription = stringResource(R.string.common_select_mode),
            tint = if (selection.isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 底部浮动多选操作栏的内容高度（不含底部安全区）。由密度令牌换算（标准档 ≈ 60dp），
 * 供浮动栏自身与各页列表 contentPadding 底部留白共用，避免最后一项被浮动栏遮住。
 */
@Composable
fun selectionActionBarHeight(): Dp = LocalUIDensityTokens.current.fieldHeight + Spacing.sm

/** 普通页 / 仅多选栏页普通浏览的底部基础留白（随密度缩放）。 */
@Composable
fun listBottomContentPadding(): Dp = LocalUIDensityTokens.current.xl

/** 仅多选栏页：多选激活时让位操作栏高度，否则统一基础留白（随密度缩放）。 */
@Composable
fun selectionListBottomContentPadding(selectionActive: Boolean): Dp =
    if (selectionActive) LocalUIDensityTokens.current.xl + selectionActionBarHeight()
    else listBottomContentPadding()

/**
 * 底部浮动多选操作栏（原「列表顶部内联操作条」改为固定在屏幕底部的浮动栏）。
 *
 * 布局：左侧「已选 N 项」，中间常用动作（[primaryActions] 平铺外露），右侧「⋯」更多菜单
 * （全选/取消全选 + [moreActions] 追加的低频动作）。底部空间充足，「已选 N 项」用
 * [TextOverflow.Ellipsis] 不换行，窄屏也不会再被动作挤折行。
 *
 * 调用约定：
 * - 放在 Box 内并用 `Modifier.align(Alignment.BottomCenter)` 贴底；外层 Box 负责底部安全区
 *   （`navigationBarsPadding()`）。
 * - 全选/取消全选默认收进「⋯」菜单第一项；低频动作（重连、分享等）经 [moreActions] 追加，
 *   菜单项建议用图标+文字（遵循项目「溢出收纳 ⋯ 菜单」约定）。
 * - 退出多选：顶栏多选开关（[SelectionToggleAction]）/ 返回键（[BackHandler]）即可，底部栏不再单设退出。
 *
 * [primaryActions] 里的动作建议用 [SelectionActionButton]（文字）与 [SelectionActionIconButton]（图标），
 * 保持同一套尺寸与配色；危险动作（删除）用 `tint = MaterialTheme.colorScheme.error` 并配合
 * [BatchDeleteConfirmDialog] 二次确认。
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    moreActions: @Composable ColumnScope.() -> Unit = {},
    primaryActions: @Composable RowScope.() -> Unit = {}
) {
    // 浮动栏只在多选模式出现：顺手把返回键接管成「退出多选」，与模型页行为一致。
    BackHandler { onExit() }

    val t = LocalUIDensityTokens.current
    var moreExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .height(selectionActionBarHeight()),
        shape = RoundedCornerShape(t.rLg),
        color = MaterialTheme.semanticColors.cardSurface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.common_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            primaryActions()
            // 「⋯」更多：全选/取消全选 + 页面低频动作（重连、分享等）
            Box {
                IconButton(
                    onClick = { moreExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        FeatherIcons.MoreHorizontal,
                        contentDescription = stringResource(R.string.common_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    FeatherIcons.CheckSquare,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    stringResource(
                                        if (allSelected) R.string.common_deselect_all else R.string.common_select_all
                                    )
                                )
                            }
                        },
                        onClick = {
                            moreExpanded = false
                            onToggleSelectAll()
                        }
                    )
                    moreActions()
                }
            }
        }
    }
}

/**
 * 底部浮动操作栏上的紧凑动作按钮。常用动作（启用/停用/删除等）在 [SelectionActionBar]
 * 的 primaryActions 里平铺外露，内边距压到 [Spacing.sm]、文字用 labelMedium、图标 16dp。
 */
@Composable
fun SelectionActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
        modifier = modifier.height(36.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * 底部浮动操作栏上的图标按钮：危险/次要动作（如删除）用图标并配 contentDescription，
 * 与 [SelectionActionButton] 保持同一套 36dp 尺寸与配色。
 */
@Composable
fun SelectionActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 批量删除二次确认弹窗。[inProgress] 为 true 时确认按钮转圈并锁定两个按钮，
 * 避免删除中途被关掉导致「看不见到底成没成」。
 */
@Composable
fun BatchDeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    inProgress: Boolean = false,
    progressText: String? = null
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (progressText != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !inProgress) {
                if (inProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
