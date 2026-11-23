package com.aiverse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.core.ui.openFileExternal
import com.aiverse.core.util.extToMimeType
import com.aiverse.R
import com.aiverse.feature.agent.presentation.AIAgentViewModel
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import com.aiverse.feature.workspace.domain.isValidFileEntryName
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Download
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Send
import compose.icons.feathericons.Share2
import compose.icons.feathericons.Trash2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * 全屏查看器里针对「单个文件」的操作菜单（issue #205）。
 *
 * 由 [com.aiverse.core.ui] 的三个查看器宿主通过 `actions` slot 在文件树来源（[com.aiverse.core.ui.ViewerOrigin.FILE_TREE]）
 * 下弹出；本组件落在 feature 层，因此可以直接复用 [AIAgentViewModel] 已有的文件操作回调
 * （分享 / 复制 / 剪切 / 重命名 / 删除 / 导出 / 发送至聊天 / 外部打开），与侧边栏文件树长按菜单同一套语义。
 *
 * 动作语义与文件树菜单保持一致：
 * - 除删除外的动作只关掉本 sheet，**不**退出查看器；
 * - 删除走二次确认（复用文件树单文件删除文案），确认后先删、成功再退出预览回文件树；
 *   删除请求进行中锁住按钮防竞态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerFileActionSheet(
    path: String,
    fileName: String,
    agentViewModel: AIAgentViewModel,
    scope: CoroutineScope,
    onDismissViewer: () -> Unit
) {
    val context = LocalContext.current
    // sheet 与各确认弹窗的可见态。sheet 关闭后确认弹窗仍要能单独弹出（先关 sheet 再弹确认，视觉不跳）。
    var sheetVisible by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var renameValue by remember(fileName) { mutableStateOf(fileName) }

    // 导出：与文件树同一套 SAF「另存为 zip」，回调后交给 VM 导出。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            agentViewModel.exportEntries(setOf(path), uri) { ok ->
                if (!ok) toastViewerFileOpFailed(context, R.string.file_browser_export_failed)
            }
        }
    }

    if (sheetVisible) {
        AdaptiveModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.md)
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Send,
                    label = stringResource(R.string.file_browser_send_to_chat),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        sendViewerPathToChat(path, agentViewModel)
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Share2,
                    label = stringResource(R.string.file_browser_share),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        agentViewModel.shareEntries(setOf(path))
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Download,
                    label = stringResource(R.string.file_browser_export),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        exportLauncher.launch("export_$fileName.zip")
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.ExternalLink,
                    label = stringResource(R.string.attachment_open_external),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        openViewerFileExternal(path, context, agentViewModel, scope)
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Copy,
                    label = stringResource(R.string.common_copy),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        agentViewModel.copyBrowseEntry(path, fileName)
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Scissors,
                    label = stringResource(R.string.common_cut),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        sheetVisible = false
                        agentViewModel.cutBrowseEntry(path, fileName)
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Edit2,
                    label = stringResource(R.string.common_rename),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        renameValue = fileName
                        showRenameDialog = true
                    }
                )
                ViewerSheetActionRow(
                    icon = FeatherIcons.Trash2,
                    label = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        sheetVisible = false
                        showDeleteConfirm = true
                    }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.file_browser_delete_file_confirm, fileName)) },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        agentViewModel.deleteBrowseEntry(path) { ok ->
                            deleting = false
                            if (ok) {
                                // 先删、成功再退出预览回文件树（文件树在 Dialog 之下，天然回退，VM 已刷新 browseState）。
                                onDismissViewer()
                            } else {
                                toastViewerFileOpFailed(context, R.string.file_browser_delete_failed)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteConfirm = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.common_rename)) },
            text = {
                AppTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = stringResource(R.string.file_browser_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        agentViewModel.renameBrowseEntry(path, renameValue.trim()) { ok ->
                            if (!ok) toastViewerFileOpFailed(context, R.string.file_browser_rename_failed)
                        }
                        showRenameDialog = false
                    },
                    enabled = isValidFileEntryName(renameValue) && renameValue.trim() != fileName
                ) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/**
 * 查看器菜单里的一行动作，样式与侧边栏文件树长按菜单的 `SheetActionRow` 保持一致
 * （整行可点 + 图标 20dp + 文案 bodyLarge），只服务本文件，不进 core。
 */
@Composable
private fun ViewerSheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
            Spacer(Modifier.width(Spacing.lg))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}

/** 把单个容器路径转成 @引用 文本追加进当前会话输入框（与文件树「发送至聊天」同一套映射）。 */
private fun sendViewerPathToChat(path: String, agentViewModel: AIAgentViewModel) {
    // 本机文件夹等不可映射为工作区相对路径的条目跳过（与文件树批量发送一致）。
    if (WorkspacePathMapper.isLocalFolderContainerPath(path)) return
    val relative = path.removePrefix("${WorkspacePathMapper.CONTAINER_ROOT}/")
        .takeIf { it != path } ?: return // 不在工作区根下（无法引用）则跳过
    agentViewModel.appendInputDraft("@$relative ")
}

/** 外部打开：远程先整份下载到宿主，再交给系统应用（与文件树 onOpenWith 同一链路）。 */
private fun openViewerFileExternal(
    path: String,
    context: android.content.Context,
    agentViewModel: AIAgentViewModel,
    scope: CoroutineScope
) {
    scope.launch {
        val file = withContext(Dispatchers.IO) {
            runCatching { agentViewModel.fileAccess.copyToLocal(path) }.getOrNull()
        }
        if (file == null || !file.isFile) {
            toastViewerFileOpFailed(context, R.string.chat_open_file_missing)
        } else {
            openFileExternal(context, file, extToMimeType(path) ?: "")
        }
    }
}

/** 查看器文件操作失败提示（导出 / 重命名 / 删除 / 外部打开共用）。 */
private fun toastViewerFileOpFailed(context: android.content.Context, messageRes: Int) {
    android.widget.Toast.makeText(context, context.getString(messageRes), android.widget.Toast.LENGTH_SHORT).show()
}
