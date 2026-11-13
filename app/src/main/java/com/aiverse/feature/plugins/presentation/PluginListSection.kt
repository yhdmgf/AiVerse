package com.aiverse.feature.plugins.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.HelpCircle
import compose.icons.feathericons.Share2
import com.aiverse.R
import com.aiverse.core.ui.AppSwitch
import com.aiverse.core.ui.FileMenuRow
import com.aiverse.core.ui.rememberFileShareExport
import com.aiverse.feature.plugins.data.InstalledPlugin
import com.aiverse.feature.plugins.domain.model.PluginPermissions
import com.aiverse.feature.plugins.domain.model.PluginRepoEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 脚本插件使用文档链接（安装脚本正上方图标跳转）。 */
private const val SCRIPTS_DOCS_URL = "https://aivapp.cn/guide/scripts"

/** 脚本插件管理页：列表（启用/禁用/卸载）+ 安装入口（仓库页/本地 zip）。 */
@Composable
fun PluginListSection(
    viewModel: PluginListViewModel,
    onOpenRepo: () -> Unit
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmUninstall by remember { mutableStateOf<InstalledPlugin?>(null) }
    var showInstallMenu by remember { mutableStateOf(false) }

    // 分享/导出公共入口（每项「更多」菜单共用同一份，避免各自创建 launcher）。
    val fileShareExport = rememberFileShareExport()
    val context = LocalContext.current

    // 分享插件：打包单插件 zip 后走系统分享（FileProvider 授权）。
    fun sharePlugin(plugin: InstalledPlugin) {
        viewModel.buildPluginExportZip(plugin.manifest.id) { bytes ->
            if (bytes == null) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.plugins_share_failed)) }
            } else {
                fileShareExport.shareZip(
                    bytes,
                    "${plugin.manifest.name}.zip",
                    context.getString(R.string.plugins_share_chooser)
                ) { ok ->
                    if (!ok) {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.plugins_share_failed)) }
                    }
                }
            }
        }
    }

    // 导出插件：打包单插件 zip 后用 SAF 存到本地。
    fun exportPlugin(plugin: InstalledPlugin) {
        viewModel.buildPluginExportZip(plugin.manifest.id) { bytes ->
            if (bytes == null) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.plugins_export_failed)) }
            } else {
                fileShareExport.exportZip(bytes, "${plugin.manifest.name}.zip") { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(
                                if (ok) R.string.plugins_export_success else R.string.plugins_export_failed
                            )
                        )
                    }
                }
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.install(uri) }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error ?: return@LaunchedEffect)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message ?: return@LaunchedEffect)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 顶栏已展示「脚本插件」标题，页内不再重复；帮助图标与「安装插件」按钮同一行基线靠右。
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SCRIPTS_DOCS_URL)))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = FeatherIcons.HelpCircle,
                        contentDescription = stringResource(R.string.plugins_scripts_docs),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    Button(
                        onClick = { showInstallMenu = true },
                        enabled = !installing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (installing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.plugins_install))
                    }
                    DropdownMenu(
                        expanded = showInstallMenu,
                        onDismissRequest = { showInstallMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plugins_install_from_repo)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showInstallMenu = false
                                onOpenRepo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plugins_install_from_local)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showInstallMenu = false
                                zipPicker.launch("application/zip")
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.size(8.dp))

            if (plugins.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plugins_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(plugins, key = { it.manifest.id }) { plugin ->
                        val entry = updates[plugin.manifest.id]
                        PluginCard(
                            plugin = plugin,
                            hasUpdate = entry != null,
                            onUpdate = { entry?.let { viewModel.installFromRepo(it) } },
                            onToggleEnabled = { enabled ->
                                viewModel.setEnabled(plugin.manifest.id, enabled)
                            },
                            onUninstall = { confirmUninstall = plugin },
                            onShare = { sharePlugin(plugin) },
                            onExport = { exportPlugin(plugin) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    confirmUninstall?.let { plugin ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text(stringResource(R.string.plugins_confirm_uninstall_title)) },
            text = {
                Text(stringResource(R.string.plugins_confirm_uninstall_message, plugin.manifest.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(plugin.manifest.id)
                    confirmUninstall = null
                }) {
                    Text(
                        text = stringResource(R.string.plugins_uninstall),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) {
                    Text(stringResource(R.string.plugins_cancel))
                }
            }
        )
    }
}

/** 插件仓库页：拉取 index.json 列表，安装/更新。返回由 SettingsScreen 顶栏统一处理。 */
@Composable
fun PluginRepoSection(
    viewModel: PluginListViewModel
) {
    val entries by viewModel.repoEntries.collectAsStateWithLifecycle()
    val loading by viewModel.repoLoading.collectAsStateWithLifecycle()
    val repoError by viewModel.repoError.collectAsStateWithLifecycle()
    val installing by viewModel.repoInstalling.collectAsStateWithLifecycle()
    val installed by viewModel.repoInstalled.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val pendingNotify by viewModel.pendingNotifyInstall.collectAsStateWithLifecycle()
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.notifyPermissionGranted() else viewModel.clearPendingNotifyInstall()
    }

    LaunchedEffect(Unit) {
        if (entries.isEmpty() && !loading) viewModel.loadRepo()
    }

    // 安装需要通知权限的插件：先弹系统权限申请，授权后自动重试安装。
    LaunchedEffect(pendingNotify) {
        if (pendingNotify != null) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        when {
            loading && entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            repoError != null && entries.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = repoError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { viewModel.loadRepo() }) {
                        Text(stringResource(R.string.plugins_repo_retry))
                    }
                }
            }
            entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plugins_repo_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.name }) { entry ->
                        val isInstalled = installed[entry.name] == true
                        val updatable = updates.values.any { it.name == entry.name }
                        RepoCard(
                            entry = entry,
                            busy = entry.name in installing,
                            action = when {
                                updatable -> RepoAction.UPDATE
                                isInstalled -> RepoAction.LATEST
                                else -> RepoAction.INSTALL
                            },
                            onAction = { viewModel.installFromRepo(entry) }
                        )
                    }
                }
            }
        }
    }
}

private enum class RepoAction { INSTALL, UPDATE, LATEST }

@Composable
private fun RepoCard(
    entry: PluginRepoEntry,
    busy: Boolean,
    action: RepoAction,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    when (action) {
                        RepoAction.INSTALL -> TextButton(onClick = onAction) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.plugins_install))
                        }
                        RepoAction.UPDATE -> TextButton(onClick = onAction) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.plugins_update),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        RepoAction.LATEST -> TextButton(enabled = false, onClick = {}) {
                            Text(stringResource(R.string.plugins_installed_latest))
                        }
                    }
                }
            }

            val meta = listOfNotNull(
                entry.version,
                entry.creator.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.lastUpdate > 0) {
                Text(
                    text = stringResource(R.string.plugins_repo_last_update, formatRepoDate(entry.lastUpdate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry.permission.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.permission.forEach { perm ->
                        val label = PluginPermissions.LABELS[perm] ?: perm
                        val dangerous = perm in PluginPermissions.DANGEROUS
                        val color = if (dangerous) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier
                                .background(
                                    color = color.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRepoDate(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))

@Composable
private fun PluginCard(
    plugin: InstalledPlugin,
    hasUpdate: Boolean,
    onUpdate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.manifest.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasUpdate) {
                            Spacer(Modifier.width(6.dp))
                            // 有可用更新：红色角标（白字数字 1）
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    val meta = listOfNotNull(
                        plugin.manifest.version,
                        plugin.manifest.author.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AppSwitch(
                    checked = plugin.enabled,
                    onCheckedChange = onToggleEnabled
                )
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.plugins_more_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (hasUpdate) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plugins_update)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Update,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onUpdate()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { FileMenuRow(FeatherIcons.Share2, stringResource(R.string.plugins_share)) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { FileMenuRow(FeatherIcons.Download, stringResource(R.string.plugins_export)) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plugins_uninstall)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onUninstall()
                            }
                        )
                    }
                }
            }

            if (plugin.manifest.description.isNotBlank()) {
                Text(
                    text = plugin.manifest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (plugin.manifest.permissions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plugin.manifest.permissions.forEach { perm ->
                        val label = PluginPermissions.LABELS[perm] ?: perm
                        val dangerous = perm in PluginPermissions.DANGEROUS
                        val color = if (dangerous) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier
                                .background(
                                    color = color.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
