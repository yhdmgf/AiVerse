package com.aiverse.feature.plugins.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.settings.presentation.component.SkillMenuRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Download
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.Share2
import kotlinx.coroutines.launch
import java.io.File

/**
 * 设置 → 插件主入口（AiCode 移植版）：插件列表（已安装 / 开发中 / 商店 tab）、
 * 插件详情页、右上角「＋」导入与「⚙」设置弹层。
 *
 * 详情与导入均在组件内切换，不新增 SettingsSection 路由，最小侵入 AiVerse 设置页。
 *
 * 移植适配说明：
 * - 顶栏标题/返回由 AiVerse SettingsScreen 统一提供，本组件只渲染操作行（导入 + 设置）。
 * - [PluginsViewModel] 通过 Hilt 注入；商店/开发者详情与插件配置页均由并行子代理落地，直接接入。
 *
 * TODO(P2): 仅剩 data 层类型（permission/开发者/商店设置仓库、PluginDevPackageBuilder）与
 * [PluginManager.devDir] / [PluginManager.prepareDependencies] 待 AiVerse 新插件 data/domain 层补齐；
 * P3a（data.index）/ P3c（PluginStore*）/ P3d（PluginWeb*、PluginWebPanelScreen）/ PluginImportHost
 * 均已由并行子代理落地，可直接编译。
 */
@Composable
internal fun PluginSettingsSection(
    onImport: (() -> Unit) -> Unit = {},
    onOpenSettings: (() -> Unit) -> Unit = {}
) {
    val viewModel: PluginsViewModel = hiltViewModel()
    // 商店详情页的分享走商店 ViewModel（下载/缓存插件包由它复用 PluginIndexClient）。
    val storeViewModel: PluginStoreViewModel = hiltViewModel()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val devMode by viewModel.devMode.collectAsStateWithLifecycle()
    val devProjects by viewModel.devProjects.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val devRefreshing by viewModel.devRefreshing.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val shizukuGranted by viewModel.shizukuGrantedPluginIds.collectAsStateWithLifecycle()
    val overlayGranted by viewModel.overlayGrantedPluginIds.collectAsStateWithLifecycle()
    val backupGranted by viewModel.backupGrantedPluginIds.collectAsStateWithLifecycle()
    val dependencyStates by viewModel.dependencyStates.collectAsStateWithLifecycle()
    val indexSourceText by viewModel.indexSourceText.collectAsStateWithLifecycle()
    val downloadMirror by viewModel.downloadMirror.collectAsStateWithLifecycle()

    val languageTag = currentLanguageTag()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var selectedStoreItem by remember { mutableStateOf<PluginStoreItem?>(null) }
    var selectedDevProject by remember { mutableStateOf<PluginDevProject?>(null) }
    // 记住插件列表当前 tab：从商店详情返回时恢复到商店（page 2）而非已安装（page 0）。
    var pluginsTab by rememberSaveable { mutableIntStateOf(0) }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    // 顶栏「＋」导入与「⚙」设置按钮由 SettingsScreen 统一 TopAppBar 提供；
    // 通过上报回调把「拉起系统文件选择器」「打开设置弹层」暴露给父级（路径 B）。
    LaunchedEffect(Unit) {
        onImport {
            zipLauncher.launch(
                arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")
            )
        }
        onOpenSettings { showSettings = true }
    }
    val selectedPlugin = plugins.firstOrNull { it.id == selectedPluginId }

    // 商店详情分享：等插件包在本地就绪（未下载则先下载），再用 FileProvider 授权拉系统分享。
    // 文件名沿用商店包原名（{id}_{version}.zip），不改名。
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    // 分享失败提示：复用商店既有下载失败文案（不在 .kt 硬编码中文）。
    val shareFailedMessage = stringResource(R.string.plugins_store_error_download)
    fun sharePluginPackage(file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, shareFailedMessage, Toast.LENGTH_LONG).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareLauncher.launch(Intent.createChooser(send, context.getString(R.string.plugins_store_detail_share)))
    }
    LaunchedEffect(storeViewModel) {
        storeViewModel.shareResult.collect { result ->
            result.onSuccess(::sharePluginPackage).onFailure { e ->
                FileLogger.w("PluginSettingsSection", "分享插件包失败", e)
                Toast.makeText(context, shareFailedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
    // 已安装插件导出：打包成 zip 后复用同一 FileProvider 分享流程；失败复用商店下载失败文案。
    LaunchedEffect(viewModel) {
        viewModel.exportResult.collect { result ->
            result.onSuccess(::sharePluginPackage).onFailure { e ->
                FileLogger.w("PluginSettingsSection", "导出插件包失败", e)
                Toast.makeText(context, shareFailedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 详情/商店详情/开发详情显示时，系统返回应先回插件列表（清选中状态），
    // 而不是冒泡到 SettingsScreen 直接退出插件分区（之前返回会跳设置主菜单，且状态不清导致再点插件卡详情、需重启）。
    val inDetail = selectedPlugin != null || selectedStoreItem != null || selectedDevProject != null
    BackHandler(enabled = inDetail) {
        selectedPluginId = null
        selectedStoreItem = null
        selectedDevProject = null
    }

    // 导入/预览确认弹层宿主：放在所有提前 return 的子页分支之前，确保商店详情页点「安装」
    // 也能立即弹出预览（之前只渲染在插件列表页，详情页提前 return 导致预览要返回列表才出现）。
    PluginImportHost(viewModel = viewModel)

    if (selectedPlugin != null) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Column(modifier = Modifier.fillMaxSize()) {
            PluginBackBar(
                onBack = { selectedPluginId = null },
                trailing = {
                    // 详情页顶栏「更多」：分享/导出（打包 zip 走系统分享，复用导出结果流）。
                    var detailMoreExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { detailMoreExpanded = true }) {
                            Icon(
                                FeatherIcons.MoreHorizontal,
                                contentDescription = stringResource(R.string.common_more),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = detailMoreExpanded,
                            onDismissRequest = { detailMoreExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { SkillMenuRow(FeatherIcons.Share2, stringResource(R.string.plugins_share)) },
                                onClick = {
                                    detailMoreExpanded = false
                                    viewModel.exportPlugin(selectedPlugin.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { SkillMenuRow(FeatherIcons.Download, stringResource(R.string.plugins_export)) },
                                onClick = {
                                    detailMoreExpanded = false
                                    viewModel.exportPlugin(selectedPlugin.id)
                                }
                            )
                        }
                    }
                }
            )
            PluginDetailSection(
                plugin = selectedPlugin,
                onToggleEnabled = { viewModel.setEnabled(selectedPlugin.id, it) },
                onExport = { viewModel.exportPlugin(selectedPlugin.id) },
                bridgeFactory = { page, post, onClose ->
                    viewModel.createWebBridge(
                        page = page,
                        pluginVersion = page.pluginVersion,
                        languageTag = languageTag,
                        theme = { if (isDark) "dark" else "light" },
                        onClosePage = onClose,
                        post = post
                    )
                },
                onClosePage = { selectedPluginId = null },
                dependencyState = dependencyStates[selectedPlugin.id],
                onPrepareDependencies = { viewModel.prepareDependencies(selectedPlugin.id) },
                shizukuGranted = selectedPlugin.id in shizukuGranted,
                shizukuState = shizukuState,
                onSetShizukuGranted = { viewModel.setShizukuGranted(selectedPlugin, it) },
                overlayGranted = selectedPlugin.id in overlayGranted,
                onSetOverlayGranted = { viewModel.setOverlayGranted(selectedPlugin, it) },
                backupGranted = selectedPlugin.id in backupGranted,
                onSetBackupGranted = { viewModel.setBackupGranted(selectedPlugin, it) }
            )
        }
        return
    }

    val storeItem = selectedStoreItem
    if (storeItem != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            PluginBackBar(onBack = { selectedStoreItem = null })
            PluginStoreDetailSection(
                item = storeItem,
                onShowPreview = viewModel::showPreview
            )
        }
        return
    }

    val devProject = selectedDevProject
    if (devProject != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            PluginBackBar(onBack = { selectedDevProject = null })
            PluginDevDetailSection(
                project = devProject,
                plugins = plugins,
                devRootPath = viewModel.devRootPath,
                onInstall = { viewModel.installDevProject(devProject) }
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PluginsSection(
                plugins = plugins,
                loaded = loaded,
                devMode = devMode,
                devProjects = devProjects,
                devRootPath = viewModel.devRootPath,
                refreshing = refreshing,
                devRefreshing = devRefreshing,
                onRefresh = viewModel::refresh,
                onRefreshDevProjects = viewModel::refreshDevProjects,
                onOpenDevDetail = { selectedDevProject = it },
                onSetDevEnabled = { project, enabled ->
                    val installed = plugins.firstOrNull { it.id == project.pluginId }
                    when {
                        installed != null ->
                            if (installed.manifest != null && installed.content != null) {
                                viewModel.setEnabled(installed.id, enabled)
                            }
                        enabled && project.error == null -> viewModel.installDevProject(project)
                    }
                },
                onOpenDetail = { selectedPluginId = it.id },
                onToggleEnabled = { plugin, enabled -> viewModel.setEnabled(plugin.id, enabled) },
                onMemoryToggleEnabled = viewModel::setMemoryEnabled,
                onUninstall = { plugin -> viewModel.uninstall(plugin.id) },
                onBatchUninstall = { selected -> viewModel.uninstallAll(selected.map { it.id }) },
                onBatchSetEnabled = { selected, enabled ->
                    selected.forEach { viewModel.setEnabled(it.id, enabled) }
                },
                // 分享与导出都复用 exportPlugin：导出结果经 exportResult 流拉起系统分享。
                onBatchExport = { selected -> selected.forEach { viewModel.exportPlugin(it.id) } },
                onBatchShare = { selected -> selected.forEach { viewModel.exportPlugin(it.id) } },
                initialTabPage = pluginsTab,
                onTabPageChange = { pluginsTab = it },
                onShowStorePreview = viewModel::showPreview,
                onOpenStoreDetail = { selectedStoreItem = it }
            )
        }
    }

    if (showSettings) {
        PluginSettingsSheet(
            devMode = devMode,
            devRootPath = viewModel.devRootPath,
            indexSourceText = indexSourceText,
            downloadMirror = downloadMirror,
            onSetIndexSourceText = viewModel::setIndexSourceText,
            onSetDownloadMirror = viewModel::setDownloadMirror,
            onSetDevMode = viewModel::setDevMode,
            onDismiss = {
                showSettings = false
                viewModel.savePluginSettings()
            }
        )
    }
}

/**
 * 详情页顶部的返回条：返回箭头 + 空标题区（标题由 SettingsScreen 统一 TopBar 提供）。
 * 点击返回会清选中状态回到插件列表（而非冒泡到 SettingsScreen 退出插件分区）。
 */
@Composable
internal fun PluginBackBar(
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = FeatherIcons.ArrowLeft,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        // trailing 顶到右侧；默认 null（已装/开发详情页不传，行为不变）。
        Spacer(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 导入/预览确认弹层：读取失败显示错误列表；包校验通过后显示清单信息与确认/取消。
 * 文案复用既有 plugins_* 键；错误详情显示 [PluginError.detail]（语言无关的技术信息，非硬编码中文）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginImportSheet(
    state: PluginImportState,
    languageTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        PluginImportState.Idle, PluginImportState.Reading -> Unit
        is PluginImportState.Failed -> {
            val sheetState = rememberModalBottomSheetState()
            AdaptiveModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.plugins_error_read_zip),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    state.errors.forEach { error ->
                        Text(
                            text = error.detail.ifBlank { error.code.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.plugins_cancel))
                    }
                }
            }
        }
        is PluginImportState.Preview -> {
            val preview = state.preview
            val sheetState = rememberModalBottomSheetState()
            AdaptiveModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = preview.manifest.name.resolve(languageTag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stringResource(R.string.plugins_info_version)}: v${preview.manifest.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preview.manifest.publisher.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.plugins_info_publisher)}: ${preview.manifest.publisher}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.plugins_section_contents)}: ${preview.manifest.contributionCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.error?.let { error ->
                        Text(
                            text = error.detail.ifBlank { error.code.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss, enabled = !state.installing) {
                            Text(stringResource(R.string.plugins_cancel))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = onConfirm, enabled = !state.installing) {
                            Text(
                                stringResource(
                                    if (state.installing) R.string.plugins_installing
                                    else R.string.plugins_install
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
