package com.aiverse.feature.settings.presentation.component

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.ListSelectionState
import com.aiverse.core.ui.SelectionActionBar
import com.aiverse.core.ui.SelectionActionButton
import com.aiverse.core.ui.SegmentedTabs
import com.aiverse.core.ui.SelectionToggleAction
import com.aiverse.core.ui.SwipeToDeleteRow
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.core.ui.selectionListBottomContentPadding
import com.aiverse.feature.agent.domain.model.ChatSession
import com.aiverse.feature.agent.presentation.AgentUIMessage
import com.aiverse.feature.agent.presentation.MessageRole
import com.aiverse.feature.settings.presentation.SessionManagementViewModel
import com.aiverse.feature.settings.presentation.SessionMessageSearchHit
import com.aiverse.feature.settings.presentation.SessionScopeFilter
import com.aiverse.feature.settings.presentation.WorkspaceBucket
import com.aiverse.feature.settings.presentation.WorkspaceBucketSource
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Download
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Folder
import compose.icons.feathericons.Trash2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin

/**
 * 会话管理设置二级页：按工作区分组列出全部会话，支持搜索 / 作用域筛选 / 多选批量操作 /
 * 单会话操作（查看、在聊天打开、移动、置顶、重命名、导出、删除）与消息全文命中定位。
 *
 * 主页与详情页共用同一个 [SessionManagementViewModel] 实例（由 SettingsScreen 在分区进入时创建）。
 */

/** 会话标题转安全文件名片段：保留中英文数字与常见符号，其余替换为下划线。 */
private fun safeExportFileTitle(title: String): String =
    title.replace(Regex("[^A-Za-z0-9\u4e00-\u9fa5._-]"), "_").take(40).ifBlank { "session" }

// ───────────────────────────── 主页 ─────────────────────────────

@Composable
internal fun SessionManagementSectionHost(
    viewModel: SessionManagementViewModel,
    selection: ListSelectionState,
    onOpenDetail: () -> Unit,
    onOpenInChat: (String) -> Unit,
    onSessionsMutated: () -> Unit
) {
    val context = LocalContext.current
    val buckets by viewModel.allBuckets.collectAsStateWithLifecycle()
    val filtered by viewModel.filteredBuckets.collectAsStateWithLifecycle()
    val messageCounts by viewModel.messageCounts.collectAsStateWithLifecycle()
    val searchHits by viewModel.messageSearchHits.collectAsStateWithLifecycle()
    val scopeFilter by viewModel.scopeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // 弹窗状态机：批量移动 / 批量删除 / 单会话操作表 / 重命名 / 单个删除 / 导出 / 跨环境二次确认。
    var pendingMoveIds by remember { mutableStateOf<Set<String>?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var pendingRenameSession by remember { mutableStateOf<ChatSession?>(null) }
    var menuSession by remember { mutableStateOf<ChatSession?>(null) }
    var pendingExportSessionId by remember { mutableStateOf<String?>(null) }
    var pendingConfirmMove by remember { mutableStateOf<Pair<Set<String>, WorkspaceBucket>?>(null) }
    // 各工作区分组的展开/收起状态：收起的 bucket path 集合。
    var collapsedBuckets by remember { mutableStateOf<Set<String>>(emptySet()) }

    val t = LocalUIDensityTokens.current
    val searchHint = stringResource(R.string.session_mgmt_search_hint)
    val filterAll = stringResource(R.string.session_mgmt_filter_all)
    val filterCurrentEnv = stringResource(R.string.session_mgmt_filter_current_env)
    val filterRemoteOffline = stringResource(R.string.session_mgmt_filter_remote_offline)
    val copiedText = stringResource(R.string.chat_copied)
    val exportDoneText = stringResource(R.string.chat_export_session_done)
    val exportFailedText = stringResource(R.string.chat_export_session_failed)

    // 数据刷新后剔除已失效的选中项；离开分区时的清理由 SettingsScreen 统一处理。
    LaunchedEffect(buckets) {
        selection.retainOnly(buckets.flatMap { it.sessions.map { s -> sessionKey(s) } }.toSet())
    }

    // 导出：系统文档创建器，文件名 aiverse-session-<安全标题>-<时间戳>.tar.gz。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val sessionId = pendingExportSessionId
        pendingExportSessionId = null
        if (uri == null || sessionId == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { output ->
            viewModel.exportSession(sessionId, output) { ok ->
                Toast.makeText(
                    context,
                    if (ok) exportDoneText else exportFailedText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } ?: run {
            Toast.makeText(context, exportFailedText, Toast.LENGTH_SHORT).show()
        }
    }

    fun safeFileTitle(title: String): String = safeExportFileTitle(title)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg)
                // 多选时底部浮动操作栏会遮住内容，额外留出避让空间。
                .padding(bottom = selectionListBottomContentPadding(selection.isActive)),
            verticalArrangement = Arrangement.spacedBy(t.listCellGap)
        ) {
            item(key = "search") {
                ModelSearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    placeholder = searchHint
                )
            }
            item(key = "scope") {
                SegmentedTabs(
                    selected = when (scopeFilter) {
                        SessionScopeFilter.ALL -> 0
                        SessionScopeFilter.CURRENT_ENV -> 1
                        SessionScopeFilter.REMOTE_OR_OFFLINE -> 2
                    },
                    labels = listOf(filterAll, filterCurrentEnv, filterRemoteOffline),
                    onSelect = { index ->
                        viewModel.setScopeFilter(
                            when (index) {
                                1 -> SessionScopeFilter.CURRENT_ENV
                                2 -> SessionScopeFilter.REMOTE_OR_OFFLINE
                                else -> SessionScopeFilter.ALL
                            }
                        )
                    }
                )
            }

            if (filtered.isEmpty() && searchHits.isEmpty()) {
                item(key = "empty") {
                    SessionEmptyRow(text = stringResource(R.string.session_mgmt_empty))
                }
            }

            // 按工作区分组的会话卡片；bucket header 可折叠。
            filtered.forEachIndexed { bucketIndex, bucket ->
                val expanded = bucket.path !in collapsedBuckets
                item(key = "bucket_header_${bucket.path}") {
                    CollapsibleBucketHeader(
                        bucket = bucket,
                        expanded = expanded,
                        onToggleExpanded = {
                            collapsedBuckets = if (expanded) {
                                collapsedBuckets + bucket.path
                            } else {
                                collapsedBuckets - bucket.path
                            }
                        }
                    )
                }
                if (!expanded) return@forEachIndexed
                // 会话卡片逐项虚拟化；用 key 稳定复用，删除/移动后自动刷新。
                items(
                    items = bucket.sessions,
                    key = { sessionKey(it) },
                    contentType = { "session" }
                ) { session ->
                    SwipeToDeleteRow(
                        onDelete = { pendingDeleteIds = setOf(session.id) },
                        onClick = {
                            if (selection.isActive) selection.toggle(sessionKey(session))
                            else {
                                // 先在 ViewModel 记录选中的会话 id，详情页据此渲染 detailState。
                                viewModel.openSessionDetail(session.id)
                                onOpenDetail()
                            }
                        },
                        onLongClick = { menuSession = session },
                        deleteEnabled = !session.isRunningLike(),
                        selectionMode = selection.isActive,
                        selected = selection.isSelected(sessionKey(session)),
                        onToggleSelect = { selection.toggle(sessionKey(session)) }
                    ) {
                        ManagedSessionCard(
                            session = session,
                            messageCount = messageCounts[session.id] ?: 0,
                            isPinned = session.isPinned,
                            selectionMode = selection.isActive,
                            selected = selection.isSelected(sessionKey(session)),
                            sourceTag = workspaceSourceTag(bucket.source)
                        )
                    }
                }
            }

            // 消息全文命中结果（搜索关键字命中消息正文时展示）。
            if (searchHits.isNotEmpty()) {
                item(key = "hits_header") {
                    SettingsGroupHeader(text = stringResource(R.string.session_mgmt_message_hits_header))
                }
                items(
                    items = searchHits,
                    key = { it.messageId },
                    contentType = { "hit" }
                ) { hit ->
                    SessionMessageHitRow(
                        hit = hit,
                        onClick = {
                            // 命中行先定位到对应会话，再打开详情页。
                            viewModel.openSessionDetail(hit.sessionId)
                            onOpenDetail()
                        }
                    )
                }
            }
        }

        // 多选模式：底部浮动操作栏（全选 / 批量移动 / 批量删除）。
        if (selection.isActive) {
            val allSessions = buckets.flatMap { it.sessions }
            val allSelected = allSessions.isNotEmpty() && selection.selectedCount == allSessions.size
            SelectionActionBar(
                selectedCount = selection.selectedCount,
                allSelected = allSelected,
                onToggleSelectAll = {
                    if (allSelected) selection.clear()
                    else selection.replaceAll(allSessions.map { sessionKey(it) })
                },
                onExit = { selection.exit() },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SelectionActionButton(
                    text = stringResource(R.string.chat_batch_move),
                    icon = FeatherIcons.Folder,
                    enabled = selection.selectedCount > 0,
                    onClick = { pendingMoveIds = selection.selectedKeys.toSet() }
                )
                SelectionActionButton(
                    text = stringResource(R.string.common_batch_delete),
                    icon = FeatherIcons.Trash2,
                    enabled = selection.selectedCount > 0,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { pendingDeleteIds = selection.selectedKeys.toSet() }
                )
            }
        }
    }

    // 单会话操作表（长按触发）。
    menuSession?.let { session ->
        val bucketOf = buckets.firstOrNull { b -> b.sessions.any { it.id == session.id } }
        ManagedSessionActionSheet(
            session = session,
            bucket = bucketOf,
            canMove = buckets.size > 1,
            onDismiss = { menuSession = null },
            onViewContent = {
                menuSession = null
                // 查看会话内容前先设选中 id，避免详情页因 selectedSessionId 为空而显示「会话不存在」。
                viewModel.openSessionDetail(session.id)
                onOpenDetail()
            },
            onOpenInChat = {
                menuSession = null
                onOpenInChat(session.id)
            },
            onMove = {
                menuSession = null
                pendingMoveIds = setOf(session.id)
            },
            onTogglePin = {
                menuSession = null
                viewModel.togglePinSession(session)
                onSessionsMutated()
            },
            onRename = {
                menuSession = null
                pendingRenameSession = session
            },
            onExport = {
                menuSession = null
                pendingExportSessionId = session.id
                exportLauncher.launch("aiverse-session-${safeFileTitle(session.title)}-${System.currentTimeMillis()}.tar.gz")
            },
            onBatchSelect = {
                menuSession = null
                selection.enter()
                selection.toggle(sessionKey(session))
            },
            onDelete = {
                menuSession = null
                pendingDeleteIds = setOf(session.id)
            }
        )
    }

    // 重命名弹窗。
    pendingRenameSession?.let { session ->
        SessionRenameDialog(
            initialTitle = session.title,
            onDismiss = { pendingRenameSession = null },
            onConfirm = { newTitle ->
                viewModel.renameSession(session.id, newTitle)
                pendingRenameSession = null
                onSessionsMutated()
            }
        )
    }

    // 单个删除确认。
    pendingDeleteIds?.takeIf { it.size == 1 }?.let { ids ->
        val target = buckets.flatMap { it.sessions }.firstOrNull { it.id == ids.first() }
        if (target != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteIds = null },
                title = { Text(stringResource(R.string.chat_delete_session_confirm)) },
                text = { Text(stringResource(R.string.chat_delete_session_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        val deleting = ids
                        pendingDeleteIds = null
                        viewModel.deleteSessions(deleting.toList()) {
                            selection.retainOnly(
                                (selection.selectedKeys - deleting).toSet()
                            )
                            onSessionsMutated()
                        }
                    }) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIds = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    // 批量删除确认。
    pendingDeleteIds?.takeIf { it.size > 1 }?.let { ids ->
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.chat_batch_delete_title),
            message = stringResource(R.string.chat_batch_delete_confirm, ids.size),
            onConfirm = {
                val deleting = ids
                pendingDeleteIds = null
                viewModel.deleteSessions(deleting.toList()) {
                    selection.retainOnly((selection.selectedKeys - deleting).toSet())
                    selection.exit()
                    onSessionsMutated()
                }
            },
            onDismiss = { pendingDeleteIds = null }
        )
    }

    // 批量 / 单个移动到工作区（选择目标工作区）。
    pendingMoveIds?.let { ids ->
        TargetWorkspacePickerSheet(
            buckets = buckets,
            isCrossEnvironmentMove = { target -> viewModel.isCrossEnvironmentMove(ids, target) },
            onDismiss = { pendingMoveIds = null },
            onPick = { target ->
                pendingMoveIds = null
                if (viewModel.isCrossEnvironmentMove(ids, target)) {
                    pendingConfirmMove = ids to target
                } else {
                    viewModel.moveSessions(ids.toList(), target) { onSessionsMutated() }
                }
            }
        )
    }

    // 跨环境移动二次确认。
    pendingConfirmMove?.let { (ids, target) ->
        AlertDialog(
            onDismissRequest = { pendingConfirmMove = null },
            title = { Text(stringResource(R.string.session_mgmt_cross_env_title)) },
            text = { Text(stringResource(R.string.session_mgmt_cross_env_confirm, ids.size, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val moving = pendingConfirmMove
                    pendingConfirmMove = null
                    moving?.let { (moveIds, moveTarget) ->
                        viewModel.moveSessions(moveIds.toList(), moveTarget) { onSessionsMutated() }
                    }
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmMove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ───────────────────────────── 详情页 ─────────────────────────────

@Composable
internal fun SessionDetailSectionHost(
    viewModel: SessionManagementViewModel,
    onOpenInChat: (String) -> Unit,
    onSessionsMutated: () -> Unit,
    onDeletedNavigateBack: () -> Unit
) {
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val allBuckets by viewModel.allBuckets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val t = LocalUIDensityTokens.current
    // 详情页重命名 / 导出 / 移动弹窗状态。
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingExportSessionId by remember { mutableStateOf<String?>(null) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var pendingConfirmMoveTarget by remember { mutableStateOf<WorkspaceBucket?>(null) }
    // 详情页删除二次确认：与主页单删同款 AlertDialog，避免点一下立即删并返回。
    var pendingDetailDelete by remember { mutableStateOf(false) }
    val exportDoneText = stringResource(R.string.chat_export_session_done)
    val exportFailedText = stringResource(R.string.chat_export_session_failed)

    // 导出：系统文档创建器，与主页同一套文件名与回调约定。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val sessionId = pendingExportSessionId
        pendingExportSessionId = null
        if (uri == null || sessionId == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { output ->
            viewModel.exportSession(sessionId, output) { ok ->
                Toast.makeText(
                    context,
                    if (ok) exportDoneText else exportFailedText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } ?: run {
            Toast.makeText(context, exportFailedText, Toast.LENGTH_SHORT).show()
        }
    }

    if (detail.session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.session_mgmt_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 绑定为非空值：上方空态分支已 return，此处再用 ?: return 恢复智能转换，避免后续 session.xxx 报可空。
    val session = detail.session ?: return
    if (showRenameDialog) {
        SessionRenameDialog(
            initialTitle = session.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.renameSession(session.id, newTitle)
                showRenameDialog = false
                onSessionsMutated()
            }
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(t.listCellGap),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xl)
    ) {
        item(key = "meta") {
            SessionDetailMetaCard(
                session = session,
                bucket = detail.bucket,
                messageCount = detail.messages.size,
                subAgentCount = detail.subSessions.size,
                onOpenInChat = { onOpenInChat(session.id) },
                onMove = { showMoveSheet = true },
                onRename = { showRenameDialog = true },
                onExport = {
                    pendingExportSessionId = session.id
                    exportLauncher.launch(
                        "aiverse-session-${safeExportFileTitle(session.title)}-${System.currentTimeMillis()}.tar.gz"
                    )
                },
                onDelete = {
                    // 先置待确认标记，弹二次确认框，确认后再真正删除。
                    pendingDetailDelete = true
                },
                deleteEnabled = !session.isRunningLike(),
                loading = detail.loading
            )
        }

        if (detail.messages.isEmpty()) {
            item(key = "empty_msg") {
                SessionEmptyRow(text = stringResource(R.string.session_mgmt_empty_messages))
            }
        }

        items(
            items = detail.messages,
            key = { it.id },
            contentType = { "message" }
        ) { message ->
            SessionDetailMessageCard(message = message)
        }
    }

    // 详情页删除二次确认（与主页单删同款文案与结构）。
    if (pendingDetailDelete) {
        AlertDialog(
            onDismissRequest = { pendingDetailDelete = false },
            title = { Text(stringResource(R.string.chat_delete_session_confirm)) },
            text = { Text(stringResource(R.string.chat_delete_session_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDetailDelete = false
                    viewModel.deleteSessions(listOf(session.id)) {
                        onSessionsMutated()
                        onDeletedNavigateBack()
                    }
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDetailDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 详情页移动到工作区：选择目标工作区，会话当前所在工作区禁用（不可移回自身）。
    if (showMoveSheet) {
        TargetWorkspacePickerSheet(
            buckets = allBuckets,
            disabledPaths = setOf(session.workspacePath),
            isCrossEnvironmentMove = { target ->
                viewModel.isCrossEnvironmentMove(setOf(session.workspacePath), target)
            },
            onDismiss = { showMoveSheet = false },
            onPick = { target ->
                showMoveSheet = false
                if (viewModel.isCrossEnvironmentMove(setOf(session.workspacePath), target)) {
                    pendingConfirmMoveTarget = target
                } else {
                    viewModel.moveSessions(listOf(session.id), target) { onSessionsMutated() }
                }
            }
        )
    }

    // 跨环境移动二次确认（与主页同一套文案）。
    pendingConfirmMoveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConfirmMoveTarget = null },
            title = { Text(stringResource(R.string.session_mgmt_cross_env_title)) },
            text = { Text(stringResource(R.string.session_mgmt_cross_env_confirm, 1, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val moving = pendingConfirmMoveTarget
                    pendingConfirmMoveTarget = null
                    moving?.let {
                        viewModel.moveSessions(listOf(session.id), it) { onSessionsMutated() }
                    }
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmMoveTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ───────────────────────────── 复用子组件 ─────────────────────────────

/** 空态行：整行灰字，左右内边距对齐卡片内容。 */
@Composable
private fun SessionEmptyRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 工作区分组 header：可折叠，展示工作区名 + 会话数 + 来源标签。 */
@Composable
private fun CollapsibleBucketHeader(
    bucket: WorkspaceBucket,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.rSm))
            .clickable { onToggleExpanded() },
        shape = RoundedCornerShape(t.rSm),
        color = MaterialTheme.semanticColors.mutedSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FeatherIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = bucket.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.session_mgmt_msg_count, bucket.sessions.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
            SourceTagChip(source = bucket.source)
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (expanded) 90f else 0f }
            )
        }
    }
}

/** 工作区来源标签：local / external / remote_connected / 各离线态。 */
@Composable
private fun SourceTagChip(source: WorkspaceBucketSource) {
    val t = LocalUIDensityTokens.current
    val text = workspaceSourceTag(source)
    Surface(
        shape = RoundedCornerShape(t.rPill),
        color = MaterialTheme.semanticColors.capsuleSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 7.dp)
        )
    }
}

/** 来源枚举 → 文案 key 映射。 */
@Composable
private fun workspaceSourceTag(source: WorkspaceBucketSource): String = when (source) {
    WorkspaceBucketSource.CURRENT_INTERNAL -> stringResource(R.string.session_mgmt_tag_local)
    WorkspaceBucketSource.CURRENT_EXTERNAL -> stringResource(R.string.session_mgmt_tag_external)
    WorkspaceBucketSource.CURRENT_REMOTE -> stringResource(R.string.session_mgmt_tag_remote_connected)
    WorkspaceBucketSource.LOCAL_OFFLINE -> stringResource(R.string.session_mgmt_tag_local_offline)
    WorkspaceBucketSource.EXTERNAL_OFFLINE -> stringResource(R.string.session_mgmt_tag_external_offline)
    WorkspaceBucketSource.REMOTE_OR_OTHER -> stringResource(R.string.session_mgmt_tag_remote_offline)
}

// ───────────────────────────── 会话卡片 ─────────────────────────────

/**
 * 会话卡片：标题 + meta 行（消息数 · 子代理数 · 时间 · 运行中）+ 来源标签。
 * 前缀图标优先级：多选勾选框 > 运行中圆点 > 置顶图钉。置顶卡片背景用 primaryContainer 半透明。
 */
@Composable
private fun ManagedSessionCard(
    session: ChatSession,
    messageCount: Int,
    isPinned: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    sourceTag: String
) {
    val t = LocalUIDensityTokens.current
    val running = session.isRunningLike()
    val cardColor = if (isPinned) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.semanticColors.cardSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = t.cardShape,
        color = cardColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                selectionMode -> {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                running -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(t.rPill))
                            .background(MaterialTheme.semanticColors.success)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                isPinned -> {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { session.id },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPinned) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = buildMetaLine(session, messageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.semanticColors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            SourceTagChipInline(text = sourceTag)
        }
    }
}

/** 卡片内联来源标签（不换行、不占满）。 */
@Composable
private fun SourceTagChipInline(text: String) {
    val t = LocalUIDensityTokens.current
    Surface(
        shape = RoundedCornerShape(t.rPill),
        color = MaterialTheme.semanticColors.capsuleSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 7.dp)
        )
    }
}

/** meta 行文案：消息数 · 子代理数 · 更新时间 · 运行中。 */
@Composable
private fun buildMetaLine(session: ChatSession, messageCount: Int): String {
    val msgText = stringResource(R.string.session_mgmt_msg_count, messageCount)
    val timeText = remember(session.updatedAt) { formatSessionTime(session.updatedAt) }
    return listOf(msgText, timeText).joinToString(" · ")
}

/** 会话运行态推断：无独立 running 标记，用 goalStatus 判断是否处于自主循环执行中。 */
private fun ChatSession.isRunningLike(): Boolean = goalStatus != null

private fun sessionKey(session: ChatSession): String = session.id

/** 更新时间：MM-dd HH:mm。 */
private fun formatSessionTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

// ───────────────────────────── 单会话操作表 ─────────────────────────────

/** 长按会话卡片弹出的操作表：查看 / 在聊天打开 / 移动 / 置顶 / 重命名 / 导出 / 批量选择 / 删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagedSessionActionSheet(
    session: ChatSession,
    bucket: WorkspaceBucket?,
    canMove: Boolean,
    onDismiss: () -> Unit,
    onViewContent: () -> Unit,
    onOpenInChat: () -> Unit,
    onMove: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onBatchSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val canOpenInChat = bucket?.workspace != null && bucket.isAvailable
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        content = {
            SessionActionSheetItem(
                icon = FeatherIcons.Eye,
                text = stringResource(R.string.session_mgmt_view_content),
                onClick = onViewContent
            )
            if (canOpenInChat) {
                SessionActionSheetItem(
                    icon = FeatherIcons.ExternalLink,
                    text = stringResource(R.string.session_mgmt_open_in_chat),
                    onClick = onOpenInChat
                )
            }
            if (canMove) {
                SessionActionSheetItem(
                    icon = FeatherIcons.Folder,
                    text = stringResource(R.string.chat_move_to_workspace),
                    onClick = onMove
                )
            }
            SessionActionSheetItem(
                icon = Icons.Outlined.PushPin,
                text = stringResource(
                    if (session.isPinned) R.string.chat_unpin_session else R.string.chat_pin_session
                ),
                onClick = onTogglePin
            )
            SessionActionSheetItem(
                icon = FeatherIcons.Edit2,
                text = stringResource(R.string.chat_rename_session),
                onClick = onRename
            )
            SessionActionSheetItem(
                icon = FeatherIcons.Download,
                text = stringResource(R.string.chat_export_session),
                onClick = onExport
            )
            SessionActionSheetItem(
                icon = FeatherIcons.ArrowRight,
                text = stringResource(R.string.common_select_mode),
                onClick = onBatchSelect
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            SessionActionSheetItem(
                icon = FeatherIcons.Trash2,
                text = stringResource(R.string.chat_delete_session),
                danger = true,
                onClick = onDelete
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    )
}

/** 操作表单行：图标 + 文字，danger=true 时文字与图标用 error 色。 */
@Composable
private fun SessionActionSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val t = LocalUIDensityTokens.current
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

// ───────────────────────────── 重命名弹窗 ─────────────────────────────

@Composable
private fun SessionRenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_session)) },
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                colors = dialogTextFieldColors(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.common_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

// ───────────────────────────── 移动到工作区表 ─────────────────────────────

/** 目标工作区选择表：LazyColumn 列出全部工作区 bucket，源桶与 [disabledPaths] 禁用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetWorkspacePickerSheet(
    buckets: List<WorkspaceBucket>,
    disabledPaths: Set<String> = emptySet(),
    isCrossEnvironmentMove: (WorkspaceBucket) -> Boolean,
    onDismiss: () -> Unit,
    onPick: (WorkspaceBucket) -> Unit
) {
    val t = LocalUIDensityTokens.current
    // 会话当前所在工作区（源桶）不允许作为移动目标：取第一个 bucket（当前工作区）作为源，
    // 并额外禁用调用方传入的 [disabledPaths]（如详情页中会话自身所在工作区）。
    val sourcePaths = (buckets.firstOrNull()?.path?.let { setOf(it) } ?: emptySet()) + disabledPaths
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.chat_move_workspace_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(buckets, key = { it.path }) { bucket ->
                    val isSource = bucket.path in sourcePaths
                    val crossEnv = isCrossEnvironmentMove(bucket)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSource) { onPick(bucket) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                            .alpha(if (isSource) 0.5f else 1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bucket.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = bucket.path,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.semanticColors.subtleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (crossEnv) {
                            Text(
                                text = stringResource(R.string.session_mgmt_tag_remote_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.semanticColors.subtleText
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    )
}

// ───────────────────────────── 消息命中行 ─────────────────────────────

/** 消息全文命中结果行：会话标题 + snippet。 */
@Composable
private fun SessionMessageHitRow(
    hit: SessionMessageSearchHit,
    onClick: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(t.cardShape)
            .clickable { onClick() },
        shape = t.cardShape,
        color = MaterialTheme.semanticColors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md)
        ) {
            Text(
                text = hit.sessionTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ───────────────────────────── 详情页元信息卡 ─────────────────────────────

/** 详情页元信息卡：标题 + 来源标签 + 路径 + meta 行 + 操作 pill 行 + 离线只读提示。 */
@Composable
private fun SessionDetailMetaCard(
    session: ChatSession,
    bucket: WorkspaceBucket?,
    messageCount: Int,
    subAgentCount: Int,
    onOpenInChat: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    // 运行中会话禁止删除（与主页滑删同款闸），置灰删除 pill。
    deleteEnabled: Boolean = true,
    loading: Boolean
) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.rLg),
        color = MaterialTheme.semanticColors.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.title.ifBlank { session.id },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (bucket != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    SourceTagChip(source = bucket.source)
                }
            }
            if (!bucket?.path.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = bucket!!.path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.semanticColors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = buildDetailMetaLine(session, messageCount, subAgentCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.semanticColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.md))
            // 操作 pill 行：横向滚动，不换行。
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item {
                    DetailPillButton(
                        text = stringResource(R.string.session_mgmt_open_in_chat),
                        icon = FeatherIcons.ExternalLink,
                        emphasized = true,
                        onClick = onOpenInChat
                    )
                }
                item {
                    DetailPillButton(
                        text = stringResource(R.string.chat_move_to_workspace),
                        icon = FeatherIcons.Folder,
                        onClick = onMove
                    )
                }
                item {
                    DetailPillButton(
                        text = stringResource(R.string.chat_rename_session),
                        icon = FeatherIcons.Edit2,
                        onClick = onRename
                    )
                }
                item {
                    DetailPillButton(
                        text = stringResource(R.string.chat_export_session),
                        icon = FeatherIcons.Download,
                        onClick = onExport
                    )
                }
                item {
                    DetailPillButton(
                        text = stringResource(R.string.common_delete),
                        icon = FeatherIcons.Trash2,
                        danger = true,
                        enabled = deleteEnabled,
                        onClick = { if (deleteEnabled) onDelete() }
                    )
                }
            }
            if (bucket != null && !bucket.isAvailable) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.session_mgmt_offline_readonly_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.semanticColors.subtleText
                )
            }
            if (loading) {
                Spacer(Modifier.height(Spacing.sm))
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/** 详情页元信息行：消息数 · 时间 · model · 运行中。 */
@Composable
private fun buildDetailMetaLine(session: ChatSession, messageCount: Int, subAgentCount: Int): String {
    val msgText = stringResource(R.string.session_mgmt_msg_count, messageCount)
    val timeText = remember(session.updatedAt) { formatDetailTime(session.updatedAt) }
    val modelText = session.model ?: ""
    val parts = buildList {
        add(msgText)
        if (timeText.isNotBlank()) add(timeText)
        if (modelText.isNotBlank()) add(modelText)
        if (session.isRunningLike()) add(stringResource(R.string.session_mgmt_running_tag))
    }
    return parts.joinToString(" · ")
}

/** 详情页时间：yyyy-MM-dd HH:mm。 */
private fun formatDetailTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/** 详情页操作 pill：emphasized 用 primary 填充，danger 用 error 色。 */
@Composable
private fun DetailPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val t = LocalUIDensityTokens.current
    val bg = when {
        emphasized -> MaterialTheme.colorScheme.primary
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> MaterialTheme.semanticColors.capsuleSurface
    }
    val fg = when {
        emphasized -> MaterialTheme.colorScheme.onPrimary
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(t.rPill),
        color = bg,
        modifier = Modifier
            // 置灰时降 alpha 并禁用点击，与全局禁用态一致。
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1
            )
        }
    }
}

// ───────────────────────────── 详情页消息卡片 ─────────────────────────────

/**
 * 单条消息卡片：角色标签 + 时间 + 复制按钮 + 可选 toolArgs（等宽）+
 * 可折叠思考过程 + 正文（TOOL 等宽最多 12 行）。角色决定卡片底色与角色文字色。
 */
@Composable
private fun SessionDetailMessageCard(message: AgentUIMessage) {
    val t = LocalUIDensityTokens.current
    var showReasoning by remember(message.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedText = stringResource(R.string.chat_copied)

    val (cardColor, roleText, roleColor) = when (message.role) {
        MessageRole.USER -> Triple(
            MaterialTheme.semanticColors.capsuleSurface,
            stringResource(R.string.session_mgmt_role_user),
            MaterialTheme.colorScheme.primary
        )
        MessageRole.ASSISTANT -> Triple(
            MaterialTheme.semanticColors.cardSurface,
            stringResource(R.string.session_mgmt_role_assistant),
            MaterialTheme.colorScheme.onSurface
        )
        MessageRole.TOOL -> Triple(
            MaterialTheme.semanticColors.mutedSurface,
            stringResource(R.string.session_mgmt_role_tool),
            MaterialTheme.semanticColors.subtleText
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = t.cardShape,
        color = cardColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = roleText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = roleColor
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = remember(message.timestamp) { formatMessageTime(message.timestamp) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.semanticColors.subtleText,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        copyToClipboard(context, message.content)
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = FeatherIcons.Copy,
                        contentDescription = stringResource(R.string.common_copy),
                        tint = MaterialTheme.semanticColors.subtleText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // TOOL 消息：执行的指令（等宽）。
            if (!message.toolArgs.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = message.toolArgs!!,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.semanticColors.subtleText,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 思考过程：可折叠。
            if (!message.reasoning.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(
                        if (showReasoning) R.string.session_mgmt_hide_reasoning
                        else R.string.session_mgmt_show_reasoning
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showReasoning = !showReasoning }
                )
                if (showReasoning) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = message.reasoning!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            }

            // 正文：TOOL 等宽最多 12 行，其余正常显示。
            if (message.content.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = message.content,
                    style = if (message.role == MessageRole.TOOL) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (message.role == MessageRole.TOOL) 12 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 消息时间：HH:mm:ss。 */
private fun formatMessageTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

/** 复制文本到剪贴板。 */
private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("session_message", text))
}
