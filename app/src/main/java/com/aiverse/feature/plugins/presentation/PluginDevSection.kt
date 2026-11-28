package com.aiverse.feature.plugins.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppSwitch
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.feature.plugins.domain.InstalledPlugin
import com.aiverse.feature.settings.presentation.component.LazyGroupItem
import com.aiverse.feature.settings.presentation.component.McpPill
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsEmptyState
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Code
import compose.icons.feathericons.Folder
import compose.icons.feathericons.Package
import compose.icons.feathericons.Power
import compose.icons.feathericons.Tag

/**
 * 开发者侧载目录里的插件项目：列表版式与已安装一致，点击进入独立详情页。
 *
 * [PluginDevProject] 由同包 PluginsViewModel.kt 提供；[PluginIconContent] 在 PluginIconView.kt（同包 internal）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevPluginsPage(
    projects: List<PluginDevProject>,
    plugins: List<InstalledPlugin>,
    devRootPath: String,
    listState: LazyListState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDetail: (PluginDevProject) -> Unit,
    onSetEnabled: (PluginDevProject, Boolean) -> Unit,
    onDeleteProject: (PluginDevProject) -> Unit
) {
    val t = LocalUIDensityTokens.current
    val languageTag = currentLanguageTag()
    var pendingDelete by remember { mutableStateOf<PluginDevProject?>(null) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = t.xs,
                bottom = t.pluginTabBarReserve
            )
            // 不设 spacedBy：与已安装列表一致，LazyGroupItem 首尾行圆角、行间无缝拼成一张卡。
        ) {
            if (projects.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    SettingsEmptyState(
                        icon = FeatherIcons.Code,
                        title = stringResource(R.string.plugins_dev_empty_title),
                        hint = stringResource(R.string.plugins_dev_empty_hint, devRootPath),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = t.scale(48))
                    )
                }
            } else {
                itemsIndexed(
                    items = projects,
                    key = { _, project -> project.dirName },
                    contentType = { _, _ -> "dev-row" }
                ) { index, project ->
                    val installed = plugins.firstOrNull { it.id == project.pluginId }
                    LazyGroupItem(index = index, lastIndex = projects.lastIndex) {
                        DevPluginRow(
                            project = project,
                            installed = installed,
                            languageTag = languageTag,
                            onClick = { onOpenDetail(project) },
                            onSetEnabled = { onSetEnabled(project, it) },
                            onDelete = { pendingDelete = project }
                        )
                    }
                }
            }
        }
    }

    // 删除开发项目二次确认：只删开发目录源码，已安装插件不受影响。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.plugins_dev_delete_title)) },
            text = { Text(stringResource(R.string.plugins_dev_delete_message, target.displayName(languageTag))) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteProject(target)
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * 单个开发者项目行：图标 + 名称 + 版本/状态徽章 + 启用开关，支持左滑删除。
 *
 * TODO: 原 AiCode 用 `installed.status == PluginStatus.OK` 判断可用；AiVerse 领域层无
 * PluginStatus，改用 [InstalledPlugin.isActive]（enabled && manifest && content 齐全）。
 */
@Composable
private fun DevPluginRow(
    project: PluginDevProject,
    installed: InstalledPlugin?,
    languageTag: String,
    onClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.lg,
                    end = Spacing.sm,
                    top = t.listCellPaddingVertical,
                    bottom = t.listCellPaddingVertical
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(t.scale(40))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.sm)
                    ),
                contentAlignment = Alignment.Center
            ) {
                PluginIconContent(
                    icon = installed?.content?.icon,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = t.scale(22),
                    shape = RoundedCornerShape(Radius.sm)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.displayName(languageTag),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(t.scale(6)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(t.scale(6)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (project.version.isNotBlank()) {
                        McpPill(
                            text = "v${project.version}",
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    McpPill(
                        text = projectStatusText(project, installed),
                        textColor = if (project.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            AppSwitch(
                checked = installed?.record?.enabled == true,
                onCheckedChange = onSetEnabled,
                enabled = project.error == null && (installed == null || installed.isActive)
            )

            Spacer(modifier = Modifier.width(Spacing.xs))

            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(t.scale(18))
            )
        }
    }

    SwipeToDeleteRow(onDelete = onDelete, onClick = onClick) {
        rowContent()
    }
}

@Composable
private fun projectStatusText(project: PluginDevProject, installed: InstalledPlugin?): String = when {
    project.error != null -> stringResource(R.string.plugins_dev_invalid)
    installed != null -> stringResource(R.string.plugins_dev_installed, installed.record.version)
    else -> stringResource(R.string.plugins_dev_not_installed)
}

/**
 * 开发者项目详情页：头部卡片 + 信息分组 + 安装/重载按钮。
 *
 * [PluginDevProject] 由同包 PluginsViewModel.kt 提供；`displayName` / `descriptionText` 为同文件的扩展函数。
 */
@Composable
internal fun PluginDevDetailSection(
    project: PluginDevProject,
    plugins: List<InstalledPlugin>,
    devRootPath: String,
    onInstall: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    val languageTag = currentLanguageTag()
    val installed = plugins.firstOrNull { it.id == project.pluginId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.md, bottom = Spacing.xl)
    ) {
        DevHeaderCard(project = project, installed = installed, languageTag = languageTag)

        SettingsGroupHeader(text = stringResource(R.string.plugins_section_info))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Folder,
                title = stringResource(R.string.plugins_dev_detail_dir),
                trailing = { DevInfoValue(project.dirName) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Code,
                title = stringResource(R.string.plugins_dev_detail_id),
                trailing = { DevInfoValue(project.pluginId.ifBlank { "—" }) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Tag,
                title = stringResource(R.string.plugins_dev_detail_version),
                trailing = { DevInfoValue(project.version.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "—") }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Package,
                title = stringResource(R.string.plugins_dev_detail_installed),
                trailing = {
                    DevInfoValue(
                        installed?.record?.version?.let { "v$it" }
                            ?: stringResource(R.string.plugins_dev_not_installed)
                    )
                }
            )
            if (installed != null) {
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Power,
                    title = stringResource(R.string.plugins_dev_detail_status),
                    trailing = {
                        DevInfoValue(
                            stringResource(
                                if (installed.record.enabled) R.string.plugins_status_active
                                else R.string.plugins_status_disabled
                            )
                        )
                    }
                )
            }
        }

        if (project.error != null) {
            Text(
                text = stringResource(R.string.plugins_dev_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs, end = Spacing.xs)
            )
        }

        Button(
            onClick = onInstall,
            enabled = project.error == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg)
        ) {
            Text(
                stringResource(
                    if (installed != null) R.string.plugins_dev_reload
                    else R.string.plugins_dev_install
                )
            )
        }
        Text(
            text = stringResource(R.string.plugins_dev_detail_hint, devRootPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs, end = Spacing.xs)
        )
    }
}

/**
 * 开发者项目头部卡片。
 *
 * TODO: 圆角沿用 AiVerse [Radius.lg]（= 原 14.dp），表面用语义色 cardSurface；
 * AiVerse 磨砂玻璃卡片（glassOr）待统一风格时再对齐。
 */
@Composable
private fun DevHeaderCard(
    project: PluginDevProject,
    installed: InstalledPlugin?,
    languageTag: String
) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.semanticColors.cardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(t.scale(48))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.mdLarge)
                    ),
                contentAlignment = Alignment.Center
            ) {
                PluginIconContent(
                    icon = installed?.content?.icon,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = t.scale(26),
                    shape = RoundedCornerShape(Radius.mdLarge)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.displayName(languageTag),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                project.descriptionText(languageTag)?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                project.publisher?.takeIf { it.isNotBlank() }?.let { publisher ->
                    Text(
                        text = publisher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DevInfoValue(text: String) {
    val t = LocalUIDensityTokens.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.semanticColors.subtleText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = t.scale(190))
    )
}

internal fun PluginDevProject.displayName(languageTag: String): String =
    name?.resolve(languageTag) ?: dirName

internal fun PluginDevProject.descriptionText(languageTag: String): String? =
    description?.resolve(languageTag)