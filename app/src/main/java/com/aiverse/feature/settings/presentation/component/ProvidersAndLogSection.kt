package com.aiverse.feature.settings.presentation.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.HapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.LocalSurfaceGlassStyle
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.core.ui.SelectionActionBar
import com.aiverse.core.ui.selectionListBottomContentPadding
import com.aiverse.core.ui.SelectionActionButton
import com.aiverse.core.ui.SelectionActionIconButton
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.feature.settings.domain.model.AIProviderConfig
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Star
import compose.icons.feathericons.Trash2
import androidx.compose.ui.res.stringResource
import com.aiverse.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 提供商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditorScreen]，左滑删除。
 * 每行一张独立圆角卡片（圆角 / 阴影与 [SettingsGroup] 分组卡同源），
 * 长按内容区域可调整提供商顺序（持久化到 sortOrder）。
 *
 * 顶栏右上角「多选」进入批量模式（[selection] 由 SettingsScreen 持有）：勾选后可批量启用 / 停用 / 删除。
 * 多选模式下拖拽排序停用、整行点击改为勾选（与模型页一致）。
 */
@Composable
internal fun ProvidersSection(
    providers: List<AIProviderConfig>,
    selection: ListSelectionState,
    onEdit: (AIProviderConfig) -> Unit,
    onDelete: (AIProviderConfig) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onBatchDelete: (Set<String>) -> Unit,
    onBatchSetEnabled: (Set<String>, Boolean) -> Unit,
    onTogglePin: (String, Boolean) -> Unit
) {
    val t = LocalUIDensityTokens.current
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val allKeys = remember(providers) { providers.map { it.id } }

    // 列表刷新（删除 / 外部改动）后剔除已消失的选中项；全空了就退出多选，避免顶栏按钮停在选中态。
    LaunchedEffect(allKeys, selection.isActive) {
        selection.retainOnly(allKeys.toSet())
        if (selection.isActive && providers.isEmpty()) selection.exit()
    }

    if (providers.isEmpty()) {
        EmptyHint(stringResource(R.string.providers_empty))
        return
    }
    // 置顶的提供商归入「常用」专区，其余进「其他」；两区内各自保持原有 sortOrder 顺序。
    val pinnedProviders = remember(providers) { providers.filter { it.pinned } }
    val otherProviders = remember(providers) { providers.filter { !it.pinned } }
    val isSectioned = pinnedProviders.isNotEmpty()
    val lazyListState = rememberLazyListState()
    // 拖拽排序仅在「无置顶」的单列表下启用：一旦分区（有常用专区），分区 header 会让 LazyColumn
    // 索引与 providers 列表错位，且置顶本身已是排序手段，故分区态禁用全局拖拽，避免索引错乱。
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (!isSectioned) onReorder(from.index, to.index)
    }
    val hapticFeedback = LocalHapticFeedback.current
    // 非拖拽态阴影跟随 SettingsGroup 规则（浅色玻璃 0 / 否则 elevationSm），拖拽态升到 elevationMd。
    val dark = isSystemInDarkTheme()
    val restElevation = if (LocalSurfaceGlassStyle.current && !dark) 0.dp else t.elevationSm
    val allSelected = selection.selectedCount > 0 && selection.selectedCount == allKeys.size

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = selectionListBottomContentPadding(selection.isActive)),
            verticalArrangement = Arrangement.spacedBy(t.listCellGap)
        ) {
            if (!isSectioned) {
                // 无置顶：保持原有单列表 + 长按拖拽排序，零行为变化。
                itemsIndexed(
                    items = otherProviders,
                    key = { _, provider -> provider.id }
                ) { _, provider ->
                    ProviderCard(
                        provider = provider,
                        selection = selection,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onToggleSelect = { selection.toggle(provider.id) },
                        onTogglePin = onTogglePin,
                        reorderableState = reorderableState,
                        enableDrag = true,
                        hapticFeedback = hapticFeedback,
                        restElevation = restElevation
                    )
                }
            } else {
                item(key = "section_pinned") {
                    ProviderSectionHeader(text = stringResource(R.string.provider_pinned_section))
                }
                itemsIndexed(
                    items = pinnedProviders,
                    key = { _, provider -> provider.id }
                ) { _, provider ->
                    ProviderCard(
                        provider = provider,
                        selection = selection,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onToggleSelect = { selection.toggle(provider.id) },
                        onTogglePin = onTogglePin,
                        reorderableState = reorderableState,
                        enableDrag = false,
                        hapticFeedback = hapticFeedback,
                        restElevation = restElevation
                    )
                }
                item(key = "section_other") {
                    ProviderSectionHeader(text = stringResource(R.string.provider_other_section))
                }
                itemsIndexed(
                    items = otherProviders,
                    key = { _, provider -> provider.id }
                ) { _, provider ->
                    ProviderCard(
                        provider = provider,
                        selection = selection,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onToggleSelect = { selection.toggle(provider.id) },
                        onTogglePin = onTogglePin,
                        reorderableState = reorderableState,
                        enableDrag = false,
                        hapticFeedback = hapticFeedback,
                        restElevation = restElevation
                    )
                }
            }
        }
        if (selection.isActive) {
            SelectionActionBar(
                selectedCount = selection.selectedCount,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) selection.clear() else selection.replaceAll(allKeys)
                },
                onExit = { selection.exit() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val enabled = selection.selectedCount > 0
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_enable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selection.selectedKeys.toSet(), true) }
                )
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_disable),
                    enabled = enabled,
                    onClick = { onBatchSetEnabled(selection.selectedKeys.toSet(), false) }
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
        val targets = selection.selectedKeys.toSet()
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.providers_batch_delete_title),
            message = stringResource(R.string.providers_batch_delete_confirm, targets.size),
            onConfirm = {
                showBatchDeleteConfirm = false
                selection.exit()
                onBatchDelete(targets)
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }
}

/** 居中空态提示。 */
@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 分区小标题（常用 / 其他），左侧一条主题色短竖条 + 文字。 */
@Composable
internal fun ProviderSectionHeader(text: String) {
    val t = LocalUIDensityTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单张提供商卡片：包 [ReorderableItem] 支持长按拖拽排序，内里是 [ProviderItem]。
 * [enableDrag] 为 false 时（分区态）不接拖拽把手，仅保留点击 / 左滑 / 星标置顶。
 */
@Composable
private fun ProviderCard(
    provider: AIProviderConfig,
    selection: ListSelectionState,
    onEdit: (AIProviderConfig) -> Unit,
    onDelete: (AIProviderConfig) -> Unit,
    onToggleSelect: () -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    reorderableState: ReorderableLazyListState,
    enableDrag: Boolean,
    hapticFeedback: HapticFeedback,
    restElevation: androidx.compose.ui.unit.Dp
) {
    val t = LocalUIDensityTokens.current
    ReorderableItem(
        state = reorderableState,
        key = provider.id
    ) { isDragging ->
        val dragScale by animateFloatAsState(
            targetValue = if (isDragging) 0.95f else 1f,
            animationSpec = tween(durationMillis = if (isDragging) 120 else 220),
            label = "providerDragScale"
        )
        val dragElevation by animateDpAsState(
            targetValue = if (isDragging) t.elevationMd else restElevation,
            animationSpec = tween(durationMillis = if (isDragging) 120 else 220),
            label = "providerDragElevation"
        )
        Surface(
            shape = t.cardShape,
            color = MaterialTheme.semanticColors.cardSurface,
            shadowElevation = dragElevation,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                }
        ) {
            ProviderItem(
                provider = provider,
                onEdit = { onEdit(provider) },
                onDelete = { onDelete(provider) },
                selectionMode = selection.isActive,
                selected = selection.isSelected(provider.id),
                onToggleSelect = onToggleSelect,
                onTogglePin = { pinned -> onTogglePin(provider.id, pinned) },
                dragModifier = if (!enableDrag || selection.isActive) {
                    Modifier
                } else {
                    Modifier.longPressDraggableHandle(
                        onDragStarted = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDragStopped = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    )
                }
            )
        }
    }
}

/**
 * 提供商行：左侧品牌 logo（按提供商名称自动识别），
 * 中部两行（名称 / 类型 + 模型数量 pills），右侧状态 pill + 箭头。
 * 整行点击进入编辑，左滑露出删除按钮；长按左侧信息区域可调整顺序。
 */
@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onTogglePin: (Boolean) -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    val sortDescription = stringResource(R.string.provider_sort_long_press)
    // 状态色与 MCP 行一致：启用用主题 tertiary（绿调），停用用 outline（灰）。
    val statusColor = if (provider.isEnabled) MaterialTheme.semanticColors.success else MaterialTheme.colorScheme.outline

    SwipeToDeleteRow(
        onDelete = onDelete,
        onClick = onEdit,
        selectionMode = selectionMode,
        selected = selected,
        onToggleSelect = onToggleSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(dragModifier)
                    .semantics {
                        contentDescription = sortDescription
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧品牌 logo 容器
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    ProviderLogoIcon(
                        provider = provider,
                        size = 22.dp,
                        modifier = Modifier.size(22.dp).align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.md))

                // 中间：名称 / 类型 + 模型数量
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        McpPill(
                            text = providerTypeLabel(provider.type),
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        McpPill(
                            text = stringResource(R.string.provider_models_count_tag, provider.models.size),
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 置顶星标：点按即时置顶 / 取消置顶；置顶后星标高亮（primary），未置顶为灰。
            IconToggleButton(
                checked = provider.pinned,
                onCheckedChange = { onTogglePin(!provider.pinned) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = FeatherIcons.Star,
                    contentDescription = stringResource(if (provider.pinned) R.string.provider_unpin else R.string.provider_pin),
                    tint = if (provider.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.semanticColors.subtleText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Spacing.xs))

            // 启用/停用状态 pill + 右箭头
            McpPill(
                text = stringResource(if (provider.isEnabled) R.string.common_enabled else R.string.common_disabled),
                textColor = statusColor,
                backgroundColor = statusColor.copy(alpha = 0.12f)
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
