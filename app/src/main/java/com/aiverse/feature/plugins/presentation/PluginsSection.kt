package com.aiverse.feature.plugins.presentation

import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.AppSwitch
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.FloatingTabBar
import com.aiverse.core.ui.FloatingTabItem
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.core.ui.SegmentedTabs
import com.aiverse.core.ui.SelectionActionBar
import com.aiverse.core.ui.SelectionActionButton
import com.aiverse.core.ui.rememberListSelectionState
import com.aiverse.core.ui.selectionListBottomContentPadding
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.feature.agent.domain.shizuku.ShizukuState
import com.aiverse.feature.plugins.domain.InstalledPlugin
import com.aiverse.feature.plugins.domain.MEMORY_TOGGLE_PLUGIN_ID
import com.aiverse.feature.plugins.domain.newmodel.PluginContent
import com.aiverse.feature.plugins.domain.newmodel.PluginIcon
import com.aiverse.feature.plugins.domain.newmodel.PluginMcpServer
import com.aiverse.feature.plugins.domain.newmodel.PluginPage
// P3c / P3d 相关类型（PluginStoreSection/PluginStoreViewModel/PluginStoreItem/PluginStorePreview/
// PluginWebBridge/PluginWebViewFactory/PluginWebViewCache/PluginWebBridgeInstaller/PluginWebPanelScreen）
// 与 DevPluginsPage 均已由并行子代理落地到本包，同包直接引用，无需 import。
import com.aiverse.feature.settings.presentation.component.CollapsibleGroupHeader
import com.aiverse.feature.settings.presentation.component.LazyGroupItem
import com.aiverse.feature.settings.presentation.component.McpPill
import com.aiverse.feature.settings.presentation.component.ModelSearchField
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsEmptyState
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import com.aiverse.feature.settings.presentation.component.SkillMenuRow
import com.aiverse.feature.settings.presentation.component.openUrl
import com.aiverse.feature.settings.presentation.component.settingsPageBackground
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.Book
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Cloud
import compose.icons.feathericons.Code
import compose.icons.feathericons.Download
import compose.icons.feathericons.Github
import compose.icons.feathericons.Monitor
import compose.icons.feathericons.Package
import compose.icons.feathericons.Search
import compose.icons.feathericons.Server
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Share2
import compose.icons.feathericons.ShoppingBag
import compose.icons.feathericons.Tag
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Tool
import compose.icons.feathericons.User
import compose.icons.feathericons.Users
import compose.icons.feathericons.Zap

/**
 * 插件设置主界面（AiCode 移植版）。
 *
 * 移植适配说明（AiCode → AiVerse）：
 * - 密度：全部间距/圆角/尺寸统一走 [Spacing] / [Radius] / [LocalUIDensityTokens.scale]，不引入硬编码 dp；
 *   列表行内垂直内边距用 [LocalUIDensityTokens.listCellPaddingVertical]（11dp 基准），对齐 AiVerse 设置页 SettingsRow。
 * - `plugin.status == PluginStatus.OK` 在 AiVerse 无对应字段，改用 `manifest != null && content != null`
 *   （清单与贡献可读 = AiCode 的 OK 语义）；[InstalledPlugin.content] 可空，逐字段用安全访问。
 * - `plugin.record.enabled` 改用 AiVerse 顶层 [InstalledPlugin.enabled]。
 * - 跨 feature 复用 AiVerse 设置页组件（SettingsGroup/SettingsRow/SettingsDivider/SettingsGroupHeader/
 *   LazyGroupItem/ModelSearchField/SettingsEmptyState/McpPill/openUrl/settingsPageBackground），
 *   均为同模块 internal，依赖铁律允许（settings.presentation.component）。
 *
 * TODO(P2): 仅剩 data 层类型（permission/开发者/商店设置仓库、PluginDevPackageBuilder）与
 * [PluginManager.devDir] / [PluginManager.prepareDependencies] 待 AiVerse 新插件 data/domain 层补齐；
 * P3a（data.index）/ P3c（PluginStore*）/ P3d（PluginWeb*、PluginWebPanelScreen）/ DevPluginsPage
 * 均已由并行子代理落地为同包/同源类型，可直接编译。
 */

/** 当前界面语言标签，用于挑选插件自带的多语言文本。 */
@Composable
internal fun currentLanguageTag(): String = LocalConfiguration.current.locales[0].toLanguageTag()

/** 展示用名称：清单可读时取本地化名称，否则（文件损坏等）退回插件 ID。 */
internal fun InstalledPlugin.displayName(languageTag: String): String =
    manifest?.name?.resolve(languageTag) ?: id

@Composable
internal fun mcpDescription(server: PluginMcpServer): String {
    val transport = stringResource(
        if (server.config.isStdio) R.string.plugins_mcp_transport_stdio else R.string.plugins_mcp_transport_http
    )
    val target = if (server.config.isStdio) {
        (listOfNotNull(server.config.command) + server.config.args).joinToString(" ")
    } else {
        server.config.url.orEmpty()
    }
    return if (target.isBlank()) transport else "$transport · $target"
}

/**
 * 设置 → 插件：底部 tab 切换「已安装」「开发中」（开启开发者侧载时）与「插件商店」。
 * 已安装列表保留搜索；条目显示名称、图标、版本/作者标签与启停开关，卸载入口在详情页右上角。
 */
@Composable
internal fun PluginsSection(
    plugins: List<InstalledPlugin>,
    loaded: Boolean,
    devMode: Boolean,
    devProjects: List<PluginDevProject>,
    devRootPath: String,
    refreshing: Boolean,
    devRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRefreshDevProjects: () -> Unit,
    onOpenDevDetail: (PluginDevProject) -> Unit,
    onSetDevEnabled: (PluginDevProject, Boolean) -> Unit,
    onOpenDetail: (InstalledPlugin) -> Unit,
    onToggleEnabled: (InstalledPlugin, Boolean) -> Unit,
    onMemoryToggleEnabled: (Boolean) -> Unit,
    onShowStorePreview: (PluginStorePreview) -> Unit,
    onOpenStoreDetail: (PluginStoreItem) -> Unit,
    onUninstall: (InstalledPlugin) -> Unit = {},
    onBatchUninstall: (List<InstalledPlugin>) -> Unit = {},
    onBatchSetEnabled: (List<InstalledPlugin>, Boolean) -> Unit = { _, _ -> },
    onBatchExport: (List<InstalledPlugin>) -> Unit = {},
    onBatchShare: (List<InstalledPlugin>) -> Unit = {},
    initialTabPage: Int = 0,
    onTabPageChange: (Int) -> Unit = {}
) {
    if (!loaded) return

    val languageTag = currentLanguageTag()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val storeViewModel: PluginStoreViewModel = hiltViewModel()
    val storeState by storeViewModel.state.collectAsStateWithLifecycle()
    // 首次进入插件页时补一次静默加载：否则索引还没回来时 updatesByPluginId 是空的，
    // 已安装列表里不会出现「全部更新」，只能靠用户手动下拉刷新置商店。
    LaunchedEffect(Unit) { storeViewModel.loadIfEmpty() }
    val pageCount = if (devMode) 3 else 2
    val installedListState = rememberLazyListState()
    val devListState = rememberLazyListState()
    val selection = rememberListSelectionState()

    // 多选集合随数据刷新剔除失效 id；
    // 真实插件全被卸载时退出多选。
    // 记忆「伪插件」不参与多选：从 key 集合里剔除，避免它被当成可勾选/可卸载条目。
    LaunchedEffect(plugins, selection.isActive) {
        selection.retainOnly(plugins.map { it.id }.filterNot { it == MEMORY_TOGGLE_PLUGIN_ID }.toSet())
        if (selection.isActive && plugins.none { it.id != MEMORY_TOGGLE_PLUGIN_ID }) selection.exit()
    }

    // 搜索过滤提到此处，供列表页与多选栏的「全选」共用同一可见集合。
    val filteredPlugins = remember(plugins, searchQuery, languageTag) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            plugins
        } else {
            plugins.filter { plugin ->
                plugin.displayName(languageTag).lowercase().contains(query) ||
                    plugin.id.lowercase().contains(query) ||
                    plugin.manifest?.publisher?.lowercase()?.contains(query) == true ||
                    plugin.manifest?.description?.resolve(languageTag)?.lowercase()?.contains(query) == true
            }
        }
    }
    val filteredKeys = remember(filteredPlugins) {
        filteredPlugins.map { it.id }.filterNot { it == MEMORY_TOGGLE_PLUGIN_ID }
    }
    // 批量卸载对象。
    val selectedPlugins = selection.selectedKeys.mapNotNull { id -> plugins.firstOrNull { it.id == id } }
    var showBatchUninstallConfirm by remember { mutableStateOf(false) }

    // 一键更新全部结束后提示失败数；为 0 时成功不额外打扰。
    val updateAllContext = LocalContext.current
    LaunchedEffect(storeState.updateAllFailures) {
        val failed = storeState.updateAllFailures
        if (failed > 0) {
            Toast.makeText(
                updateAllContext,
                updateAllContext.getString(R.string.plugins_update_all_failed, failed),
                Toast.LENGTH_SHORT
            ).show()
            storeViewModel.consumeUpdateAllFailures()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 开发者侧载开关会改变 tab 数量；连同底栏一起重建，避免底栏内部缓存的 tab 位置残留。
        key(pageCount) {
            // initialPage 仅首次组合生效：从详情页返回时本组件重新组合，
            // 用宿主 rememberSaveable 记住的 tab 恢复到离开前的位置（商店详情返回 → 商店 tab）。
            val pagerState = rememberPagerState(
                initialPage = initialTabPage.coerceIn(0, pageCount - 1)
            ) { pageCount }
            // 当前 tab 回写宿主，作为下次返回的恢复目标。
            LaunchedEffect(pagerState.currentPage) { onTabPageChange(pagerState.currentPage) }
            val listScrolling by remember {
                derivedStateOf {
                    when {
                        pagerState.currentPage == 0 -> installedListState.isScrollInProgress
                        pageCount == 3 && pagerState.currentPage == 1 -> devListState.isScrollInProgress
                        else -> false
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when {
                    page == 0 -> InstalledPluginsPage(
                        plugins = plugins,
                        languageTag = languageTag,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        listState = installedListState,
                        updates = storeState.updatesByPluginId,
                        updatingTotal = storeState.updatingTotal,
                        updatingRemaining = storeState.updatingRemaining,
                        onUpdateAll = storeViewModel::updateAll,
                        refreshing = refreshing,
                        onRefresh = {
                            onRefresh()
                            storeViewModel.refresh()
                        },
                        onOpenDetail = onOpenDetail,
                        onToggleEnabled = onToggleEnabled,
                        onMemoryToggleEnabled = onMemoryToggleEnabled,
                        onUninstall = onUninstall,
                        selection = selection,
                        filteredPlugins = filteredPlugins
                    )
                    pageCount == 3 && page == 1 -> DevPluginsPage(
                        projects = devProjects,
                        plugins = plugins,
                        devRootPath = devRootPath,
                        listState = devListState,
                        refreshing = devRefreshing,
                        onRefresh = onRefreshDevProjects,
                        onOpenDetail = onOpenDevDetail,
                        onSetEnabled = onSetDevEnabled
                    )
                    else -> PluginStorePage(
                        onShowPreview = onShowStorePreview,
                        onOpenDetail = onOpenStoreDetail,
                        viewModel = storeViewModel
                    )
                }
            }

            if (!selection.isActive) {
                FloatingTabBar(
                    pagerState = pagerState,
                    items = buildList {
                        add(FloatingTabItem(FeatherIcons.Package, stringResource(R.string.plugins_tab_installed)))
                        if (devMode) {
                            add(FloatingTabItem(FeatherIcons.Code, stringResource(R.string.plugins_tab_dev)))
                        }
                        add(FloatingTabItem(FeatherIcons.ShoppingBag, stringResource(R.string.plugins_tab_store)))
                    },
                    maskColor = settingsPageBackground(),
                    isScrolling = listScrolling,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 多选模式：底栏换成批量操作栏（全选 / 已选 N 项 / 启用停用 / 更多：分享导出 / 卸载），
        // 与 FloatingTabBar 互斥避免重叠。启用/停用/分享/导出仅对自定义插件（非官方渠道）有效。
        if (selection.isActive) {
            val enabled = selection.selectedCount > 0
            val allSelected = filteredKeys.isNotEmpty() && filteredKeys.all { selection.isSelected(it) }
            // 「仅自定义有效」：选中的都是真实插件（记忆伪插件不参与多选，无需排除；
            // 官方索引源插件也算自定义，同样可启用/停用/导出/分享）。
            val customOnly = selectedPlugins.isNotEmpty() && selectedPlugins.all { !it.isMemoryToggle }
            SelectionActionBar(
                selectedCount = selection.selectedCount,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) selection.clear() else selection.replaceAll(filteredKeys)
                },
                onExit = { selection.exit() },
                modifier = Modifier.align(Alignment.BottomCenter),
                moreActions = {
                    DropdownMenuItem(
                        text = { SkillMenuRow(FeatherIcons.Share2, stringResource(R.string.plugins_share)) },
                        enabled = customOnly,
                        onClick = { onBatchShare(selectedPlugins) }
                    )
                    DropdownMenuItem(
                        text = { SkillMenuRow(FeatherIcons.Download, stringResource(R.string.plugins_export)) },
                        enabled = customOnly,
                        onClick = { onBatchExport(selectedPlugins) }
                    )
                },
                moreActionsAvailable = true
            ) {
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_enable),
                    enabled = customOnly,
                    onClick = { onBatchSetEnabled(selectedPlugins, true) }
                )
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_disable),
                    enabled = customOnly,
                    onClick = { onBatchSetEnabled(selectedPlugins, false) }
                )
                SelectionActionButton(
                    text = stringResource(R.string.plugins_uninstall),
                    enabled = enabled,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showBatchUninstallConfirm = true }
                )
            }
        }
    }

    if (showBatchUninstallConfirm) {
        PluginUninstallConfirmDialog(
            message = stringResource(R.string.plugins_batch_uninstall_confirm, selectedPlugins.size),
            onConfirm = {
                showBatchUninstallConfirm = false
                onBatchUninstall(selectedPlugins)
                selection.exit()
            },
            onDismiss = { showBatchUninstallConfirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledPluginsPage(
    plugins: List<InstalledPlugin>,
    filteredPlugins: List<InstalledPlugin>,
    languageTag: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    listState: LazyListState,
    updates: Map<String, PluginStoreItem>,
    updatingTotal: Int,
    updatingRemaining: Int,
    onUpdateAll: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDetail: (InstalledPlugin) -> Unit,
    onToggleEnabled: (InstalledPlugin, Boolean) -> Unit,
    onMemoryToggleEnabled: (Boolean) -> Unit,
    onUninstall: (InstalledPlugin) -> Unit,
    selection: ListSelectionState
) {
    val t = LocalUIDensityTokens.current
    // 内置/自定义两组展开状态：默认都展开，收起后不丢（rememberSaveable）。
    var builtinExpanded by rememberSaveable { mutableStateOf(true) }
    var customExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingUninstall by remember { mutableStateOf<InstalledPlugin?>(null) }
    // 内置组 = 记忆「内置能力」伪插件（仅设置开关，非真实插件）；真实插件（含商店/本地安装，
    // 无论官方索引源与否）都算自定义——商店安装的官方插件是用户主动安装，不应算内置。
    val builtinPlugins = remember(filteredPlugins) {
        filteredPlugins.filter { it.isMemoryToggle }
    }
    val customPlugins = remember(filteredPlugins) {
        filteredPlugins.filter { !it.isMemoryToggle }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // 与角色/子代理等列表页一致：左右 md、顶部 xs，分组标题与卡片左边缘对齐。
                start = Spacing.md,
                end = Spacing.md,
                top = t.xs,
                bottom = if (selection.isActive) selectionListBottomContentPadding(true) else t.pluginTabBarReserve
            )
            // 不设 spacedBy：LazyGroupItem 只在首尾行做圆角，行间必须无缝拼成一张卡；
            // 组与组之间、卡片之外的间距由下面各项自己 padding 控制。
        ) {
            // 一键更新全部：仅当存在可升级插件时显示；更新中禁用防重复点击。
            if (updates.isNotEmpty()) {
                item(key = "update-all", contentType = "update-all") {
                    val updating = updatingTotal > 0
                    val processed = (updatingTotal - updatingRemaining).coerceIn(0, updatingTotal)
                    Button(
                        onClick = onUpdateAll,
                        enabled = !updating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.sm)
                    ) {
                        Text(
                            text = if (updating) {
                                stringResource(
                                    R.string.plugins_update_all_running,
                                    processed.coerceAtLeast(1),
                                    updatingTotal
                                )
                            } else {
                                stringResource(R.string.plugins_update_all_count, updates.size)
                            }
                        )
                    }
                }
            }
            // 空状态：只有真的没有任何已安装插件时才显示（之前条件写反，装了插件也误报空）。
            if (plugins.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    SettingsEmptyState(
                        icon = FeatherIcons.Package,
                        title = stringResource(R.string.plugins_empty_title),
                        hint = stringResource(R.string.plugins_empty_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = t.scale(48))
                    )
                }
            }
            if (plugins.size >= 3) {
                item(key = "search", contentType = "search") {
                    ModelSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.plugins_search_placeholder),
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                }
            }

            if (filteredPlugins.isEmpty() && plugins.isNotEmpty()) {
                item(key = "search-empty", contentType = "empty") {
                    PluginSearchEmptyState()
                }
            } else if (plugins.isNotEmpty()) {
                // 单个插件行渲染，内置/自定义两组共用。
                val pluginRow: @Composable (InstalledPlugin) -> Unit = { plugin ->
                    PluginRow(
                        plugin = plugin,
                        languageTag = languageTag,
                        update = updates[plugin.id],
                        onToggleEnabled = { onToggleEnabled(plugin, it) },
                        onMemoryToggleEnabled = onMemoryToggleEnabled,
                        // 记忆「伪插件」不进普通插件详情（最小侵入），点击不响应；
                        // 其启停开关就是记忆的开关，设置页的记忆管理入口随开关显隐。
                        onClick = { onOpenDetail(plugin) },
                        onUninstall = { pendingUninstall = plugin },
                        selectionMode = selection.isActive,
                        selected = selection.isSelected(plugin.id),
                        onToggleSelect = { selection.toggle(plugin.id) },
                        onLongPress = {
                            // 长按进入多选并选中当前行；已处于多选时由勾选框接管，不再响应长按。
                            if (!selection.isActive) {
                                selection.enter()
                                selection.toggle(plugin.id)
                            }
                        }
                    )
                }

                item(key = "builtin-header", contentType = "header") {
                    CollapsibleGroupHeader(
                        text = stringResource(R.string.plugins_group_builtin),
                        expanded = builtinExpanded,
                        onToggle = { builtinExpanded = !builtinExpanded }
                    )
                }
                if (builtinExpanded) {
                    itemsIndexed(
                        items = builtinPlugins,
                        key = { _, plugin -> plugin.id },
                        contentType = { _, _ -> "plugin-row" }
                    ) { index, plugin ->
                        LazyGroupItem(index = index, lastIndex = builtinPlugins.lastIndex) {
                            pluginRow(plugin)
                        }
                    }
                }

                item(key = "custom-header", contentType = "header") {
                    CollapsibleGroupHeader(
                        text = stringResource(R.string.plugins_group_custom),
                        expanded = customExpanded,
                        onToggle = { customExpanded = !customExpanded }
                    )
                }
                if (customExpanded) {
                    itemsIndexed(
                        items = customPlugins,
                        key = { _, plugin -> plugin.id },
                        contentType = { _, _ -> "plugin-row" }
                    ) { index, plugin ->
                        LazyGroupItem(index = index, lastIndex = customPlugins.lastIndex) {
                            pluginRow(plugin)
                        }
                    }
                }
            }
        }
    }

    // 单个卸载（左滑）二次确认：卸载会删除插件文件，不可恢复。
    pendingUninstall?.let { target ->
        PluginUninstallConfirmDialog(
            message = stringResource(R.string.plugins_confirm_uninstall_message, target.displayName(languageTag)),
            onConfirm = {
                pendingUninstall = null
                onUninstall(target)
            },
            onDismiss = { pendingUninstall = null }
        )
    }
}

@Composable
private fun PluginStorePage(
    onShowPreview: (PluginStorePreview) -> Unit,
    onOpenDetail: (PluginStoreItem) -> Unit,
    viewModel: PluginStoreViewModel
) {
    PluginStoreSection(
        onShowPreview = onShowPreview,
        onOpenDetail = onOpenDetail,
        viewModel = viewModel
    )
}

/**
 * 卸载确认弹窗（单个左滑与批量多选共用）：卸载会删除插件文件且不可恢复。
 * 确认按钮文案用「卸载」而非通用「删除」，与插件语义一致。
 */
@Composable
private fun PluginUninstallConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugins_confirm_uninstall_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.plugins_uninstall),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** 列表搜索无结果时的共用占位。 */
@Composable
internal fun PluginSearchEmptyState() {
    val t = LocalUIDensityTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.scale(48)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                FeatherIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(t.scale(28)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.plugins_search_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个插件列表行：名称独占一行；下一行是图标、版本/作者标签、启停开关与详情箭头，支持左滑卸载。
 *
 * 记忆「伪插件」（[InstalledPlugin.isMemoryToggle]）走独立分支：只保留启停开关与一行介绍，
 * 无图标/版本作者标签/详情箭头、不可左滑卸载、点击不进详情，开关直接驱动
 * [com.aiverse.feature.settings.data.repository.MemoryToggleRepository]（回调 [onMemoryToggleEnabled]）。
 */
@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    languageTag: String,
    update: PluginStoreItem?,
    onToggleEnabled: (Boolean) -> Unit,
    onMemoryToggleEnabled: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    onUninstall: () -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    val version = plugin.manifest?.version?.toString() ?: plugin.record.version
    val publisher = plugin.manifest?.publisher
    val switchEnabled = plugin.manifest != null && plugin.content != null

    // 记忆「伪插件」：无清单/贡献，只保留启停开关——
    // 开关始终可点（否则灰态点不动）、不展示图标/版本作者标签/详情箭头、不支持左滑卸载与点击进详情。
    // 行内边距/间距沿用真实插件行的密度基线（Spacing + listCellPaddingVertical + t.scale）。
    if (plugin.isMemoryToggle) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plugin_memory_name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(t.scale(6)))
                Text(
                    text = stringResource(R.string.plugin_memory_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 多选模式下隐藏启停开关，与真实插件行为一致，避免与整行勾选手势冲突。
            if (!selectionMode) {
                AppSwitch(
                    checked = plugin.enabled,
                    onCheckedChange = onMemoryToggleEnabled,
                    enabled = true
                )
            }
        }
        return
    }

    // 行内容（图标 + 名称/标签 + 启停开关）。
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = t.listCellPaddingVertical, bottom = t.listCellPaddingVertical),
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
                    icon = plugin.content?.icon,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = t.scale(22),
                    shape = RoundedCornerShape(Radius.sm)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plugin.displayName(languageTag),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (update != null) {
                        Spacer(modifier = Modifier.width(t.scale(6)))
                        Text(
                            text = stringResource(
                                R.string.plugins_update_version_transition,
                                plugin.record.version,
                                update.plugin.version.orEmpty()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(t.scale(6)))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(t.scale(6)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McpPill(
                        text = "v$version",
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    if (plugin.record.official) {
                        McpPill(
                            text = stringResource(R.string.plugins_badge_official),
                            textColor = MaterialTheme.semanticColors.onInfoContainer,
                            backgroundColor = MaterialTheme.semanticColors.infoContainer
                        )
                    }
                    if (!publisher.isNullOrBlank()) {
                        McpPill(
                            text = publisher,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                    if (update != null) {
                        McpPill(
                            text = stringResource(R.string.plugins_update_available),
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 多选模式下隐藏启停开关与详情箭头，避免与整行勾选手势冲突。
            if (!selectionMode) {
                AppSwitch(
                    checked = plugin.enabled && switchEnabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = switchEnabled
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
    }

    // 行背景与左滑卸载手势由 SwipeToDeleteRow 统一提供；多选模式下不渲染滑动手势与删除按钮，
    // 改为行首勾选框 + 整行点击勾选（onLongClick 仅普通模式生效，用于进入多选）。
    SwipeToDeleteRow(
        onDelete = onUninstall,
        onClick = onClick,
        onLongClick = if (selectionMode) null else onLongPress,
        selectionMode = selectionMode,
        selected = selected,
        onToggleSelect = onToggleSelect,
    ) {
        rowContent()
    }
}

/**
 * 插件详情与配置页面：
 * 采用 SegmentedTabs 分段控件，将【插件管理（AiVerse 固定配置）】与【插件配置（WebView 专属配置页面）】清晰分离，
 * 若插件未声明专属配置页面（configPage）则直接全屏展示原生管理页面，无缝融入 App 主题。
 */
@Composable
internal fun PluginDetailSection(
    plugin: InstalledPlugin,
    onToggleEnabled: (Boolean) -> Unit,
    bridgeFactory: ((page: PluginPage, post: (String) -> Unit, onClosePage: () -> Unit) -> PluginWebBridge)? = null,
    onClosePage: () -> Unit = {},
    dependencyState: PluginDependencyUiState? = null,
    onPrepareDependencies: () -> Unit = {},
    shizukuGranted: Boolean = false,
    shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    onSetShizukuGranted: (Boolean) -> Unit = {},
    sshGranted: Boolean = false,
    onSetSshGranted: (Boolean) -> Unit = {},
    overlayGranted: Boolean = false,
    onSetOverlayGranted: (Boolean) -> Unit = {},
    backupGranted: Boolean = false,
    onSetBackupGranted: (Boolean) -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    val languageTag = currentLanguageTag()
    val configPage = plugin.content?.configPage
    val hasConfigPage = plugin.isActive && configPage != null && bridgeFactory != null
    var selectedTab by remember(plugin.id, plugin.isActive) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasConfigPage) {
            SegmentedTabs(
                selected = selectedTab,
                labels = listOf(
                    stringResource(R.string.plugins_tab_manage),
                    stringResource(R.string.plugins_tab_config)
                ),
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.sm, bottom = Spacing.sm)
            )
        }

        // 内容区必须用 weight(1f) 吃掉「顶栏 + 分段控件」剩下的高度。
        // 之前这里是 Column 的末位子项直接 fillMaxSize：它会按整屏高度测量，把内容顶出屏幕底部，
        // 表现就是「插件管理 / 插件配置要往下滑一段才看全、上方顶栏像不见了」。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!hasConfigPage || selectedTab == 0) {
                // Tab 0: AiVerse 固定配置与管理页面
                PluginNativeConfigContent(
                    plugin = plugin,
                    languageTag = languageTag,
                    onToggleEnabled = onToggleEnabled,
                    dependencyState = dependencyState,
                    onPrepareDependencies = onPrepareDependencies,
                    shizukuGranted = shizukuGranted,
                    shizukuState = shizukuState,
                    onSetShizukuGranted = onSetShizukuGranted,
                    sshGranted = sshGranted,
                    onSetSshGranted = onSetSshGranted,
                    overlayGranted = overlayGranted,
                    onSetOverlayGranted = onSetOverlayGranted,
                    backupGranted = backupGranted,
                    onSetBackupGranted = onSetBackupGranted
                )
            } else {
                // Tab 1: 插件自己的专属 WebView 配置页面
                // TODO(P3d): PluginWebPanelScreen 待 P3d 落地。
                PluginWebPanelScreen(
                    page = configPage,
                    onClosePage = onClosePage,
                    bridgeFactory = { post, onClose ->
                        bridgeFactory(configPage, post, onClose)
                    },
                    modifier = Modifier.fillMaxSize(),
                    applySystemBarInsets = false
                )
            }
        }
    }
}

/** 插件原生的固定配置列表。 */
@Composable
private fun PluginNativeConfigContent(
    plugin: InstalledPlugin,
    languageTag: String,
    onToggleEnabled: (Boolean) -> Unit,
    dependencyState: PluginDependencyUiState? = null,
    onPrepareDependencies: () -> Unit = {},
    shizukuGranted: Boolean = false,
    shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    onSetShizukuGranted: (Boolean) -> Unit = {},
    sshGranted: Boolean = false,
    onSetSshGranted: (Boolean) -> Unit = {},
    overlayGranted: Boolean = false,
    onSetOverlayGranted: (Boolean) -> Unit = {},
    backupGranted: Boolean = false,
    onSetBackupGranted: (Boolean) -> Unit = {}
) {
    val t = LocalUIDensityTokens.current
    val manifest = plugin.manifest
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Spacer(Modifier.height(Spacing.xs))

        if (manifest != null) {
            PluginHeaderCard(
                name = plugin.displayName(languageTag),
                description = manifest.description.resolve(languageTag),
                icon = plugin.content?.icon
            )
        }

        // 启停开关
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.plugins_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                AppSwitch(
                    checked = plugin.enabled && plugin.manifest != null && plugin.content != null,
                    onCheckedChange = onToggleEnabled,
                    enabled = plugin.manifest != null && plugin.content != null
                )
            }
        }

        if (plugin.content?.shizuku != null || plugin.content?.ssh != null || plugin.content?.overlay != null || plugin.content?.backup != null) {
            SettingsGroupHeader(text = stringResource(R.string.plugins_permissions_section))
            SettingsGroup {
                plugin.content?.shizuku?.let { declaration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.plugins_shizuku_section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = declaration.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (shizukuState != ShizukuState.READY) {
                                Text(
                                    text = stringResource(
                                        R.string.plugins_shizuku_system_state,
                                        stringResource(shizukuState.statusLabelRes())
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        AppSwitch(
                            checked = shizukuGranted,
                            onCheckedChange = onSetShizukuGranted,
                            enabled = plugin.isActive
                        )
                    }
                }

                if (plugin.content?.shizuku != null && (plugin.content?.ssh != null || plugin.content?.overlay != null || plugin.content?.backup != null)) {
                    SettingsDivider()
                }

                plugin.content?.ssh?.let { declaration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.plugins_ssh_section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = declaration.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = sshGranted,
                            onCheckedChange = onSetSshGranted,
                            enabled = plugin.isActive
                        )
                    }
                }

                if (plugin.content?.ssh != null && (plugin.content?.overlay != null || plugin.content?.backup != null)) {
                    SettingsDivider()
                }

                plugin.content?.overlay?.let { declaration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.plugins_overlay_section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = declaration.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!canDrawOverlays) {
                                Text(
                                    text = stringResource(R.string.plugins_overlay_system_missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        AppSwitch(
                            checked = overlayGranted,
                            onCheckedChange = onSetOverlayGranted,
                            enabled = plugin.isActive && canDrawOverlays
                        )
                    }
                }

                if (plugin.content?.overlay != null && plugin.content?.backup != null) {
                    SettingsDivider()
                }

                plugin.content?.backup?.let { declaration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.plugins_backup_section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = declaration.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = backupGranted,
                            onCheckedChange = onSetBackupGranted,
                            enabled = plugin.isActive
                        )
                    }
                }
            }
        }

        if (manifest != null) {
            SettingsGroupHeader(text = stringResource(R.string.plugins_section_info))
            SettingsGroup {
                SettingsRow(
                    icon = FeatherIcons.Tag,
                    title = stringResource(R.string.plugins_info_version),
                    trailing = { PluginInfoValue("v${manifest.version}") }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.User,
                    title = stringResource(R.string.plugins_info_publisher),
                    trailing = { PluginInfoValue(manifest.publisher) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Book,
                    title = stringResource(R.string.plugins_info_license),
                    trailing = { PluginInfoValue(manifest.license) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Github,
                    title = stringResource(R.string.plugins_info_repository),
                    onClick = { openUrl(context, manifest.repository) }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Package,
                    title = stringResource(R.string.plugins_info_origin),
                    trailing = { PluginInfoValue(stringResource(R.string.plugins_origin_local)) }
                )
            }

            SettingsGroupHeader(text = stringResource(R.string.plugins_section_contents))
            SettingsGroup {
                plugin.content?.let { content ->
                    PluginContentRows(
                        content = content,
                        languageTag = languageTag,
                        active = plugin.isActive
                    )
                }
            }
        }

        val dependency = plugin.content?.runtime?.dependencies
        if (dependency != null) {
            SettingsGroupHeader(text = stringResource(R.string.plugins_section_dependencies))
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val ready = dependencyState?.ready ?: plugin.record.dependenciesReady
                        Text(
                            text = stringResource(
                                if (ready) R.string.plugins_dependencies_ready
                                else R.string.plugins_dependencies_missing
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dependency.file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dependencyState?.message?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = onPrepareDependencies,
                        enabled = dependencyState?.running != true
                    ) {
                        Text(
                            stringResource(
                                if (dependencyState?.running == true) R.string.plugins_dependencies_preparing
                                else R.string.plugins_dependencies_prepare
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginHeaderCard(name: String, description: String, icon: PluginIcon?) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.semanticColors.cardSurface,
        shadowElevation = t.scale(0)
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
                    icon = icon,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = t.scale(26),
                    shape = RoundedCornerShape(Radius.mdLarge)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginInfoValue(text: String) {
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

internal data class PluginContentItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

/** 插件贡献逐项：(图标, 标题, 描述)。详情页与安装预览共用同一套条目。 */
@Composable
internal fun pluginContentItems(content: PluginContent, languageTag: String): List<PluginContentItem> {
    val items = mutableListOf<PluginContentItem>()
    content.skills.forEach { entry ->
        items += PluginContentItem(FeatherIcons.BookOpen, entry.localId, entry.skill.description)
    }
    content.agents.forEach { entry ->
        items += PluginContentItem(FeatherIcons.Users, entry.localId, entry.definition.description)
    }
    content.workspaceTools.forEach { entry ->
        items += PluginContentItem(
            FeatherIcons.Tool,
            entry.title.resolve(languageTag),
            entry.description.resolve(languageTag)
        )
    }
    content.uiEntries.forEach { entry ->
        items += PluginContentItem(FeatherIcons.Settings, entry.title.resolve(languageTag), entry.slot.orEmpty())
    }
    content.tools.forEach { entry ->
        items += PluginContentItem(
            FeatherIcons.Zap,
            entry.title.resolve(languageTag),
            entry.description.resolve(languageTag)
        )
    }
    content.hooks.forEach { entry ->
        items += PluginContentItem(FeatherIcons.Activity, entry.event, entry.handler)
    }
    content.mcp.forEach { entry ->
        items += PluginContentItem(FeatherIcons.Server, entry.originalName, mcpDescription(entry))
    }
    content.providers.forEach { entry ->
        items += PluginContentItem(FeatherIcons.Cloud, entry.name.resolve(languageTag), entry.baseUrl)
    }
    content.modelAccess?.let { declaration ->
        items += PluginContentItem(
            FeatherIcons.Server,
            stringResource(R.string.plugins_model_access_section),
            "${declaration.purpose} · ${stringResource(R.string.plugins_model_access_desc)}"
        )
    }
    content.shizuku?.let { declaration ->
        items += PluginContentItem(
            FeatherIcons.Terminal,
            stringResource(R.string.plugins_shizuku_section),
            declaration.purpose
        )
    }
    content.overlay?.let { declaration ->
        items += PluginContentItem(
            FeatherIcons.Monitor,
            stringResource(R.string.plugins_overlay_section),
            declaration.purpose
        )
    }
    return items
}

/** 在 [SettingsGroup] 内绘制插件贡献列表；空列表时显示无内容提示。 */
@Composable
internal fun PluginContentRows(content: PluginContent, languageTag: String, active: Boolean) {
    val t = LocalUIDensityTokens.current
    val items = pluginContentItems(content, languageTag)
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.plugins_no_contents),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical)
        )
    } else {
        items.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            ContentRow(
                icon = item.icon,
                title = item.title,
                description = item.description,
                active = active
            )
        }
    }
}

@Composable
internal fun ContentRow(icon: ImageVector, title: String, description: String, active: Boolean) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (active) 1f else 0.5f)
            .padding(horizontal = Spacing.lg, vertical = t.scale(10)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(t.scale(32))
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(Radius.sm)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(t.scale(17))
            )
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSettingsSheet(
    devMode: Boolean,
    devRootPath: String,
    indexSourceText: String,
    downloadMirror: String,
    onSetIndexSourceText: (String) -> Unit,
    onSetDownloadMirror: (String) -> Unit,
    onSetDevMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
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
                .imePadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.plugins_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = Spacing.md)
            )
            PluginIndexSettingsSection(
                indexSourceText = indexSourceText,
                downloadMirror = downloadMirror,
                onSetIndexSourceText = onSetIndexSourceText,
                onSetDownloadMirror = onSetDownloadMirror
            )
            // 两张设置卡之间留出更明显的间隙（原 md 偏小，视觉上贴在一起）。
            Spacer(modifier = Modifier.height(Spacing.lg))
            PluginDeveloperSection(
                devMode = devMode,
                devRootPath = devRootPath,
                onSetDevMode = onSetDevMode
            )
        }
    }
}

@Composable
private fun PluginIndexSettingsSection(
    indexSourceText: String,
    downloadMirror: String,
    onSetIndexSourceText: (String) -> Unit,
    onSetDownloadMirror: (String) -> Unit
) {
    SettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            AppTextField(
                value = indexSourceText,
                onValueChange = onSetIndexSourceText,
                label = stringResource(R.string.plugins_store_sources_label),
                placeholder = "https://example.com/index.json",
                supportingText = { Text(stringResource(R.string.plugins_store_sources_hint)) },
                singleLine = false,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            val mirrorInvalid = downloadMirror.isNotBlank() &&
                !downloadMirror.trim().let { it.startsWith("http://") || it.startsWith("https://") }
            AppTextField(
                value = downloadMirror,
                onValueChange = onSetDownloadMirror,
                label = stringResource(R.string.plugins_store_mirror_label),
                placeholder = "https://gh-proxy.com/",
                supportingText = {
                    Text(
                        stringResource(
                            if (mirrorInvalid) R.string.plugins_store_mirror_invalid
                            else R.string.plugins_store_mirror_hint
                        )
                    )
                },
                isError = mirrorInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }
    }
}

@Composable
private fun PluginDeveloperSection(
    devMode: Boolean,
    devRootPath: String,
    onSetDevMode: (Boolean) -> Unit
) {
    val t = LocalUIDensityTokens.current
    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = t.listCellPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plugins_dev_mode),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.plugins_dev_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppSwitch(checked = devMode, onCheckedChange = onSetDevMode)
        }
        if (devMode) {
            SettingsDivider()
            Text(
                text = stringResource(R.string.plugins_dev_root, devRootPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
        // 文档入口：关于插件（使用手册）与开发插件（编写规则），点击跳官网。
        // 与开发者开关合入同一张卡（行间用 SettingsDivider 分隔），避免两张 SettingsGroup 背靠背贴死。
        // SettingsRow 默认已带右箭头，不再手动传 trailing，修掉每行两个箭头的显示 bug。
        val docsContext = LocalContext.current
        SettingsDivider()
        SettingsRow(
            icon = FeatherIcons.BookOpen,
            title = stringResource(R.string.plugins_docs_about),
            onClick = { openUrl(docsContext, PLUGIN_DOCS_URL) }
        )
        SettingsDivider()
        SettingsRow(
            icon = FeatherIcons.Code,
            title = stringResource(R.string.plugins_docs_dev),
            onClick = { openUrl(docsContext, PLUGIN_DEV_DOCS_URL) }
        )
    }
}

private fun ShizukuState.statusLabelRes(): Int = when (this) {
    ShizukuState.NOT_INSTALLED -> R.string.settings_shizuku_status_not_installed
    ShizukuState.NOT_RUNNING -> R.string.settings_shizuku_status_not_running
    ShizukuState.PERMISSION_DENIED -> R.string.settings_shizuku_status_denied
    ShizukuState.READY -> R.string.settings_shizuku_status_ready
}

/** 插件使用手册（官方文档站「关于插件」页）。 */
private const val PLUGIN_DOCS_URL = "https://www.aivapp.cn/guide/scripts"

/** 插件开发规则（官方文档站「开发插件」页）。 */
private const val PLUGIN_DEV_DOCS_URL = "https://www.aivapp.cn/advanced/plugin-dev"
