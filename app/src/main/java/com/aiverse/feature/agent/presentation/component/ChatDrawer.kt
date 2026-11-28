package com.aiverse.feature.agent.presentation.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.BatchDeleteConfirmDialog
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.core.util.TEXT_EXTS
import com.aiverse.core.util.extToMimeType
import com.aiverse.feature.onboarding.domain.OnboardingStep
import com.aiverse.feature.onboarding.presentation.onboardingTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiverse.core.theme.FileNameDisplay
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.FileTreeDisplayPrefs
import com.aiverse.core.theme.FileTreeIndentDensity
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.SegmentedTabs
import com.aiverse.core.ui.rememberImeBottomInset
import com.aiverse.core.ui.RoleAvatar
import com.aiverse.feature.settings.presentation.component.ModelSearchField
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import com.aiverse.feature.settings.presentation.component.settingsPageBackground
import com.aiverse.core.theme.LocalPluginUiStyle
import com.aiverse.feature.agent.domain.model.ChatSession
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.presentation.AgentUIState
import com.aiverse.feature.agent.presentation.ChatSearchHit
import com.aiverse.feature.agent.presentation.ChatSearchState
import com.aiverse.feature.agent.presentation.BrowseClipboard
import com.aiverse.feature.agent.presentation.BrowseSortMode
import com.aiverse.feature.agent.presentation.FileBrowseState
import com.aiverse.feature.agent.presentation.FileTreeNode
import com.aiverse.feature.plugins.domain.session.PluginSessionGroup
import com.aiverse.feature.plugins.domain.session.PluginSessionSummary
import com.aiverse.feature.plugins.domain.PluginUiSlots
import com.aiverse.feature.plugins.domain.newmodel.PluginPage
import com.aiverse.feature.plugins.domain.newmodel.PluginUiEntry
import com.aiverse.feature.plugins.presentation.PluginActionConfirmDialog
import com.aiverse.feature.plugins.presentation.PluginChatPageHost
import com.aiverse.feature.plugins.presentation.PluginEntryContext
import com.aiverse.feature.plugins.presentation.PluginSessionUiMapper
import com.aiverse.feature.plugins.presentation.PluginsViewModel
import com.aiverse.feature.plugins.presentation.chatSlotEntries
import com.aiverse.feature.plugins.presentation.currentLanguageTag
import com.aiverse.feature.plugins.presentation.pluginPageIcon
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.feature.agent.presentation.FileTreeRoot
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.FileEntry
import com.aiverse.feature.workspace.domain.RemoteServerPaths
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import com.aiverse.feature.workspace.domain.isValidFileEntryName
import com.aiverse.core.ui.ChevronRotationStyle
import com.aiverse.core.ui.ExpandableChevronIcon
import com.aiverse.core.ui.containerOsLogo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Bookmark
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.CheckSquare
import compose.icons.feathericons.Clipboard
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Download
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Eye
import compose.icons.feathericons.FilePlus
import compose.icons.feathericons.Folder
import compose.icons.feathericons.FolderPlus
import compose.icons.feathericons.Globe
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Server
import compose.icons.feathericons.Search
import compose.icons.feathericons.Send
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X
import compose.icons.feathericons.Check
import compose.icons.feathericons.CornerUpRight
import compose.icons.feathericons.Share2
import compose.icons.feathericons.Upload
import compose.icons.feathericons.UploadCloud
import androidx.compose.ui.res.stringResource
import com.aiverse.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
private fun DrawerProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth
) {
    // 侧栏打开时经 [chatAnimationsEnabled] 关掉不确定进度动画，改成静态 0.75 进度环：
    // 隐藏/后台的侧栏进度条不再逐帧重绘，视觉上仍是「加载中」。
    if (chatAnimationsEnabled()) {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(
            progress = { 0.75f },
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 侧边栏内容：顶部 Tab 切换「会话」/「文件」，底部「设置」入口卡片。
 * Tab0 为根会话列表，带子代理的会话行可就地展开；Tab1 为当前工作区的文件树。
 */
@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    pluginSessionGroups: List<PluginSessionGroup> = emptyList(),
    currentPluginSessionId: String? = null,
    onSelectPluginSession: (PluginSessionSummary) -> Unit = {},
    agentStates: Map<String, AgentUIState>,
    awaitingPermissionSessionIds: Set<String> = emptySet(),
    awaitingQuestionSessionIds: Set<String> = emptySet(),
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onDeleteSessions: ((Set<String>) -> Unit)? = null,
    onRename: (ChatSession, String) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    onExport: (ChatSession) -> Unit,
    /** 批量导出选中会话：回调里由宿主打开一次 SAF，再打包成 zip。 */
    onExportSessions: ((Set<String>) -> Unit)? = null,
    subSessionsByParent: Map<String, List<ChatSession>> = emptyMap(),
    browseState: FileBrowseState,
    fileTreePrefs: FileTreeDisplayPrefs = FileTreeDisplayPrefs.Default,
    browseSort: BrowseSortMode = BrowseSortMode.NAME,
    onBrowseSortChange: (BrowseSortMode) -> Unit = {},
    onCollapseToPath: (String) -> Unit = {},
    onToggleShowHiddenFiles: (Boolean) -> Unit = {},
    expandedPaths: Set<String>,
    expandingPath: String? = null,
    fileOpPaths: Set<String> = emptySet(),
    clipboard: BrowseClipboard? = null,
    pasteConflict: Pair<String, String>? = null,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenWith: (String) -> Unit,
    onRefreshBrowse: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onSearchFiles: (String, String, (List<FileTreeNode>) -> Unit) -> Unit = { _, _, onResult -> onResult(emptyList()) },
    onRenameEntry: (String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCopyEntry: (String, String) -> Unit,
    onCutEntry: (String, String) -> Unit,
    onPasteEntry: (String, (Boolean) -> Unit) -> Unit,
    onPasteOverwrite: () -> Unit,
    onCancelPasteOverwrite: () -> Unit,
    onClearClipboard: () -> Unit,
    workspaceName: String = "",
    /** 当前会话绑定角色（已解析；null 表示默认角色未解析）。 */
    currentRole: Role? = null,
    /** 侧栏角色入口点击：打开角色切换抽屉。 */
    onOpenRoleSwitcher: () -> Unit = {},
    onAddLocalFolder: (Uri) -> Unit = {},
    onRemoveLocalFolder: (String) -> Unit = {},
    onUploadFiles: (String, List<Uri>) -> Unit = { _, _ -> },
    onUploadFolder: (String, Uri) -> Unit = { _, _ -> },
    // 文件管理增强：分享/导入/导出/批量移动/复制
    onShareEntries: (Set<String>) -> Unit,
    onSendSelectionToChat: (Set<String>) -> Unit = {},
    onExportEntries: (Set<String>, Uri) -> Unit,
    onImportFiles: (List<Uri>, String) -> Unit,
    onCopyEntries: (Set<String>) -> Unit,
    onCutEntries: (Set<String>) -> Unit,
    onPasteEntries: (String) -> Unit,
    batchCut: Set<String> = emptySet(),
    onClearBatchCut: () -> Unit,
    onNavigateToBrowser: (() -> Unit)? = null,
    onOpenFavorites: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit,
    searchQuery: String,
    searchState: ChatSearchState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSearchHit: (ChatSearchHit) -> Unit,
    // 文件树「属性」：目录大小由持有数据层的父层在 IO 后台统计后回填（path → 回调结果）。
    onLoadDirectoryInfo: (String, (DirectoryInfo?) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // tab 与展开状态进 saveable：大屏下侧栏收起后整棵子树会离开组合（见 MainActivity 的
    // SaveableStateProvider），用 remember 存会让每次回到聊天页都重置回「会话」页。
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedSessionIds = remember { mutableStateListOf<String>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var pendingRename by remember { mutableStateOf<ChatSession?>(null) }
    var menuSession by remember { mutableStateOf<ChatSession?>(null) }
    val listState = rememberLazyListState()

    // 侧栏插件槽位（drawer.footer / chat.session.menu / files.tree.menu）：复用聊天页同一套入口数据与动作通道。
    val pluginsViewModel: PluginsViewModel = hiltViewModel()
    val installedPlugins by pluginsViewModel.plugins.collectAsStateWithLifecycle()
    val pluginLanguageTag = currentLanguageTag()
    val drawerFooterEntries = remember(installedPlugins) {
        chatSlotEntries(installedPlugins, PluginUiSlots.DRAWER_FOOTER)
    }
    val sessionMenuEntries = remember(installedPlugins) {
        chatSlotEntries(installedPlugins, PluginUiSlots.CHAT_SESSION_MENU)
    }
    val fileTreeMenuEntries = remember(installedPlugins) {
        chatSlotEntries(installedPlugins, PluginUiSlots.FILES_TREE_MENU)
    }
    var activePluginPage by remember { mutableStateOf<PluginPage?>(null) }
    var activePluginPageSessionId by remember { mutableStateOf<String?>(null) }
    var pendingPluginAction by remember { mutableStateOf<PluginUiEntry?>(null) }
    var pendingPluginActionContext by remember { mutableStateOf<PluginEntryContext?>(null) }
    val drawerContext = LocalContext.current
    val executePluginAction: (PluginUiEntry, PluginEntryContext) -> Unit = { entry, entryContext ->
        pluginsViewModel.runUiAction(
            entry = entry,
            entryContext = entryContext,
            onMessage = { message ->
                if (!message.isNullOrBlank()) {
                    Toast.makeText(drawerContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    val openPluginPage: (PluginPage, String?) -> Unit = { page, sessionId ->
        activePluginPage = page
        activePluginPageSessionId = sessionId
    }
    val runPluginAction: (PluginUiEntry, PluginEntryContext) -> Unit = { entry, entryContext ->
        if (entry.confirm) {
            pendingPluginAction = entry
            pendingPluginActionContext = entryContext
        } else {
            executePluginAction(entry, entryContext)
        }
    }
    val runPluginSessionAction: (PluginUiEntry, String) -> Unit = { entry, sessionId ->
        runPluginAction(entry, PluginEntryContext(sessionId = sessionId))
    }
    val runPluginFileTreeAction: (PluginUiEntry, String, Boolean) -> Unit = { entry, path, isDirectory ->
        runPluginAction(entry, PluginEntryContext(path = path, isDirectory = isDirectory))
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedSessionIds.clear()
    }

    // 点击会话/重开侧边栏保持原滚动位置；仅当同一会话的最后回复时间变化（发消息/收到回复）时滚回顶部。
    var lastTouched by remember { mutableStateOf<Pair<String?, Long?>?>(null) }
    val currentUpdatedAt = sessions.firstOrNull { it.id == currentSessionId }?.updatedAt
    LaunchedEffect(currentSessionId, currentUpdatedAt) {
        val cur = currentSessionId to currentUpdatedAt
        val prev = lastTouched
        lastTouched = cur
        if (prev != null && prev.first == cur.first && prev.second != cur.second) {
            listState.scrollToItem(0)
        }
    }

    val imeInset = rememberImeBottomInset()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalPluginUiStyle.current?.drawerColor ?: settingsPageBackground())
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )
            .padding(horizontal = Spacing.lg)
            .padding(bottom = imeInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // 顶部 Tab 切换
        SegmentedTabs(
            selected = selectedTab,
            labels = listOf(
                stringResource(R.string.subagent_tab_sessions),
                stringResource(R.string.drawer_tab_files)
            ),
            onSelect = { selectedTab = it }
        )

        // 聊天记录搜索框放在 Tab 栏正下方，仅「会话」Tab 可见。
        if (selectedTab == 0 && isSelectionMode) {
            Surface(
                color = MaterialTheme.semanticColors.cardSurface,
                shape = RoundedCornerShape(LocalUIDensityTokens.current.cardCorner),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedSessionIds.clear()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.X,
                                contentDescription = stringResource(R.string.common_cancel),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.chat_sessions_selected_count, selectedSessionIds.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        val allSessionIds: List<String> = remember(sessions, subSessionsByParent) {
                            val ids = mutableListOf<String>()
                            sessions.forEach { s ->
                                ids.add(s.id)
                                subSessionsByParent[s.id]?.forEach { sub -> ids.add(sub.id) }
                            }
                            ids
                        }
                        val allSelected = allSessionIds.isNotEmpty() && selectedSessionIds.size == allSessionIds.size
                        TextButton(
                            onClick = {
                                if (allSelected) {
                                    selectedSessionIds.clear()
                                } else {
                                    selectedSessionIds.clear()
                                    selectedSessionIds.addAll(allSessionIds)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = stringResource(if (allSelected) R.string.provider_models_deselect_all else R.string.provider_models_select_all),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        IconButton(
                            onClick = { onExportSessions?.invoke(selectedSessionIds.toSet()) },
                            enabled = selectedSessionIds.isNotEmpty() && onExportSessions != null,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Download,
                                contentDescription = stringResource(R.string.chat_batch_export),
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedSessionIds.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            onClick = { showBatchDeleteConfirm = true },
                            enabled = selectedSessionIds.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Trash2,
                                contentDescription = stringResource(R.string.chat_batch_delete_title),
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedSessionIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        } else if (selectedTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ChatSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = onClearSearch,
                    modifier = Modifier.weight(1f)
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Plus,
                            contentDescription = stringResource(R.string.chat_new_session),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> SessionListTab(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    pluginSessionGroups = pluginSessionGroups,
                    currentPluginSessionId = currentPluginSessionId,
                    onSelectPluginSession = onSelectPluginSession,
                    agentStates = agentStates,
                    awaitingPermissionSessionIds = awaitingPermissionSessionIds,
                    awaitingQuestionSessionIds = awaitingQuestionSessionIds,
                    subSessionsByParent = subSessionsByParent,
                    listState = listState,
                    searchQuery = searchQuery,
                    searchState = searchState,
                    selectionMode = isSelectionMode,
                    selectedSessionIds = selectedSessionIds,
                    onToggleSelectSession = { s ->
                        val subSessions = subSessionsByParent[s.id].orEmpty()
                        if (subSessions.isNotEmpty()) {
                            // 主代理：级联选中/取消其全部子代理。主代理自身 id 不进出选中集合，
                            // 避免批量删除含主代理 id 时级联删掉全部子代理（含用户取消勾选想保留的）。
                            selectedSessionIds.remove(s.id)
                            val allSelected = subSessions.all { it.id in selectedSessionIds }
                            subSessions.forEach { sub ->
                                if (allSelected) selectedSessionIds.remove(sub.id)
                                else selectedSessionIds.add(sub.id)
                            }
                        } else {
                            // 叶子（子代理行 / 无子代理的主代理）：保持单个 toggle。
                            if (s.id in selectedSessionIds) selectedSessionIds.remove(s.id)
                            else selectedSessionIds.add(s.id)
                        }
                    },
                    onSelect = onSelect,
                    onLongClick = { menuSession = it },
                    onOpenSearchHit = onOpenSearchHit
                )
                1 -> FileBrowserTab(
                    state = browseState,
                    fileTreePrefs = fileTreePrefs,
                    browseSort = browseSort,
                    onBrowseSortChange = onBrowseSortChange,
                    onCollapseToPath = onCollapseToPath,
                    onToggleShowHiddenFiles = onToggleShowHiddenFiles,
                    expandedPaths = expandedPaths,
                    expandingPath = expandingPath,
                    fileOpPaths = fileOpPaths,
                    clipboard = clipboard,
                    onToggleExpand = onToggleExpand,
                    onOpenFile = onOpenFile,
                    onOpenWith = onOpenWith,
                    onRefresh = onRefreshBrowse,
                    onCreateFile = onCreateFile,
                    onCreateFolder = onCreateFolder,
                    onSearchFiles = onSearchFiles,
                    onRenameEntry = onRenameEntry,
                    onDeleteEntry = onDeleteEntry,
                    onCopyEntry = onCopyEntry,
                    onCutEntry = onCutEntry,
                    onPasteEntry = onPasteEntry,
                    onClearClipboard = onClearClipboard,
                    workspaceName = workspaceName,
                    onAddLocalFolder = onAddLocalFolder,
                    onRemoveLocalFolder = onRemoveLocalFolder,
                    onUploadFiles = onUploadFiles,
                    onUploadFolder = onUploadFolder,
                    onShareEntries = onShareEntries,
                    onSendSelectionToChat = onSendSelectionToChat,
                    onExportEntries = onExportEntries,
                    onImportFiles = onImportFiles,
                    onCopyEntries = onCopyEntries,
                    onCutEntries = onCutEntries,
                    onPasteEntries = onPasteEntries,
                    batchCut = batchCut,
                    onClearBatchCut = onClearBatchCut,
                    pluginEntries = fileTreeMenuEntries,
                    pluginLanguageTag = pluginLanguageTag,
                    onPluginRunAction = runPluginFileTreeAction,
                    onBatchDelete = onDeleteEntry,
                    onLoadDirectoryInfo = onLoadDirectoryInfo
                )
            }
        }

        if (drawerFooterEntries.isNotEmpty()) {
            SettingsGroup {
                drawerFooterEntries.forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        icon = pluginPageIcon(entry.icon),
                        title = entry.title.resolve(pluginLanguageTag),
                        subtitle = stringResource(R.string.plugins_ext_entry_subtitle, entry.pluginId),
                        onClick = {
                            val page = entry.page
                            if (page != null) openPluginPage(page, currentSessionId)
                            else runPluginAction(entry, PluginEntryContext(sessionId = currentSessionId))
                        }
                    )
                }
            }
        }

        SettingsGroup {
            // 角色入口与收藏同一张卡片、同一种行样式，不再单独占一个分组框。
            DrawerRoleRow(
                role = currentRole,
                onClick = onOpenRoleSwitcher
            )
            SettingsDivider()
            if (onOpenFavorites != null) {
                SettingsRow(
                    icon = FeatherIcons.Bookmark,
                    title = stringResource(R.string.favorites_title),
                    onClick = onOpenFavorites
                )
                SettingsDivider()
            }
            if (onNavigateToBrowser != null) {
                SettingsRow(
                    icon = FeatherIcons.Globe,
                    title = stringResource(R.string.browser_title),
                    onClick = onNavigateToBrowser
                )
                SettingsDivider()
            }
            SettingsRow(
                icon = FeatherIcons.Settings,
                title = stringResource(R.string.chat_settings),
                onClick = onNavigateToSettings,
                modifier = Modifier.onboardingTarget(OnboardingStep.ENTER_SETTINGS)
            )
        }
    }

    pasteConflict?.let { (_, targetPath) ->
        AlertDialog(
            onDismissRequest = onCancelPasteOverwrite,
            title = { Text(stringResource(R.string.file_browser_paste_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.file_browser_paste_conflict_message,
                        targetPath.substringAfterLast('/')
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onPasteOverwrite) { Text(stringResource(R.string.common_overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelPasteOverwrite) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_session)) },
            text = { Text(stringResource(R.string.chat_delete_session_confirm, session.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    menuSession?.let { session ->
        SessionActionSheet(
            session = session,
            pluginEntries = sessionMenuEntries,
            languageTag = pluginLanguageTag,
            onOpenPage = openPluginPage,
            onRunAction = runPluginSessionAction,
            onTogglePin = {
                menuSession = null
                onTogglePin(session)
            },
            onRename = {
                menuSession = null
                pendingRename = session
            },
            onExport = {
                menuSession = null
                onExport(session)
            },
            onBatchSelect = {
                menuSession = null
                isSelectionMode = true
                selectedSessionIds.clear()
                selectedSessionIds.add(session.id)
            },
            onDelete = {
                menuSession = null
                pendingDelete = session
            },
            onDismiss = { menuSession = null }
        )
    }

    activePluginPage?.let { page ->
        Dialog(
            onDismissRequest = { activePluginPage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PluginChatPageHost(
                page = page,
                pluginsViewModel = pluginsViewModel,
                sessionId = activePluginPageSessionId,
                onClose = { activePluginPage = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    pendingPluginAction?.let { entry ->
        PluginActionConfirmDialog(
            entry = entry,
            languageTag = pluginLanguageTag,
            onConfirm = {
                val entryContext = pendingPluginActionContext
                pendingPluginAction = null
                pendingPluginActionContext = null
                if (entryContext != null) executePluginAction(entry, entryContext)
            },
            onDismiss = {
                pendingPluginAction = null
                pendingPluginActionContext = null
            }
        )
    }

    if (showBatchDeleteConfirm) {
        BatchDeleteConfirmDialog(
            title = stringResource(R.string.chat_batch_delete_title),
            message = stringResource(R.string.chat_batch_delete_confirm, selectedSessionIds.size),
            onConfirm = {
                showBatchDeleteConfirm = false
                val toDelete = selectedSessionIds.toSet()
                selectedSessionIds.clear()
                isSelectionMode = false
                if (onDeleteSessions != null) {
                    onDeleteSessions(toDelete)
                } else {
                    toDelete.forEach { sid: String ->
                        sessions.firstOrNull { it.id == sid }?.let { onDelete(it) }
                    }
                }
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }

    pendingRename?.let { session ->
        var renameText by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.chat_rename_session)) },
            text = {
                AppTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = stringResource(R.string.chat_session_name),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(session, renameText)
                        pendingRename = null
                    },
                    enabled = renameText.isNotBlank() && renameText != session.title
                ) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/**
 * Tab0：会话列表。搜索词非空时列表区切换为跨会话搜索结果。
 */
@Composable
private fun SessionListTab(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    pluginSessionGroups: List<PluginSessionGroup>,
    currentPluginSessionId: String?,
    onSelectPluginSession: (PluginSessionSummary) -> Unit,
    agentStates: Map<String, AgentUIState>,
    awaitingPermissionSessionIds: Set<String>,
    awaitingQuestionSessionIds: Set<String>,
    subSessionsByParent: Map<String, List<ChatSession>>,
    listState: LazyListState,
    searchQuery: String,
    searchState: ChatSearchState,
    selectionMode: Boolean = false,
    selectedSessionIds: List<String> = emptyList(),
    onToggleSelectSession: (ChatSession) -> Unit = {},
    onSelect: (ChatSession) -> Unit,
    onLongClick: (ChatSession) -> Unit,
    onOpenSearchHit: (ChatSearchHit) -> Unit
) {
    if (searchQuery.isBlank()) {
        SessionListContent(
            sessions = sessions,
            currentSessionId = currentSessionId,
            pluginSessionGroups = pluginSessionGroups,
            currentPluginSessionId = currentPluginSessionId,
            onSelectPluginSession = onSelectPluginSession,
            agentStates = agentStates,
            awaitingPermissionSessionIds = awaitingPermissionSessionIds,
            awaitingQuestionSessionIds = awaitingQuestionSessionIds,
            subSessionsByParent = subSessionsByParent,
            listState = listState,
            selectionMode = selectionMode,
            selectedSessionIds = selectedSessionIds,
            onToggleSelectSession = onToggleSelectSession,
            onSelect = onSelect,
            onLongClick = onLongClick
        )
    } else {
        ChatSearchResults(state = searchState, onOpenHit = onOpenSearchHit)
    }
}

/** Tab0 的会话列表本体（按最后回复时间分组）。带子代理的会话行尾有展开箭头，展开后在其下方缩进列出子代理。 */
@Composable
private fun SessionListContent(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    pluginSessionGroups: List<PluginSessionGroup>,
    currentPluginSessionId: String?,
    onSelectPluginSession: (PluginSessionSummary) -> Unit,
    agentStates: Map<String, AgentUIState>,
    awaitingPermissionSessionIds: Set<String>,
    awaitingQuestionSessionIds: Set<String>,
    subSessionsByParent: Map<String, List<ChatSession>>,
    listState: LazyListState,
    selectionMode: Boolean = false,
    selectedSessionIds: List<String> = emptyList(),
    onToggleSelectSession: (ChatSession) -> Unit = {},
    onSelect: (ChatSession) -> Unit,
    onLongClick: (ChatSession) -> Unit
) {
    if (sessions.isEmpty() && pluginSessionGroups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.chat_no_sessions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
        }
        return
    }
    var expandedIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet<String>()) }
    val groups = remember(sessions) {
        val now = System.currentTimeMillis()
        val pinned = sessions.filter { it.isPinned }
        val unpinned = sessions.filterNot { it.isPinned }
        buildList {
            if (pinned.isNotEmpty()) add(SessionGroup("pinned", pinned))
            addAll(buildSessionGroups(unpinned, now))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        // 插件会话：不混入时间分组，固定放在本机会话之上；组内顺序保持插件返回顺序。
        pluginSessionGroups.forEach { pluginGroup ->
            item(key = "plugin-group-${pluginGroup.pluginId}", contentType = "group-header") {
                SettingsGroupHeader(text = pluginGroup.pluginName)
            }
            itemsIndexed(
                items = pluginGroup.sessions,
                key = { _, pluginSession -> "plugin-session-${pluginSession.id}" },
                contentType = { _, _ -> "plugin-session" }
            ) { index, pluginSession ->
                Column {
                    if (index > 0) SettingsDivider()
                    ChatSessionRow(
                        session = pluginSession.asChatSession(),
                        selected = !selectionMode && currentPluginSessionId == pluginSession.id,
                        isExecuting = pluginSession.running,
                        onClick = { onSelectPluginSession(pluginSession) },
                        onLongClick = {}
                    )
                }
            }
        }

        // 每个分组占一个 item、组内 forEach 全量渲染会让 LazyColumn 失去惰性：
        // 「最近 30 天」这种分组有上百个会话时，一个 item 就要组合上百行。分组头与会话各自成 item。
        groups.forEach { group ->
            item(key = "group-${group.groupKey}", contentType = "group-header") {
                SettingsGroupHeader(
                    text = group.sessions.firstOrNull()?.let { sessionGroupLabel(group.groupKey, it) } ?: ""
                )
            }
            itemsIndexed(
                items = group.sessions,
                key = { _, session -> session.id },
                contentType = { _, _ -> "session" }
            ) { index, session ->
                Column {
                    if (index > 0) {
                        if (group.groupKey == "pinned") Spacer(Modifier.height(Spacing.sm)) else SettingsDivider()
                    }
                    val state = agentStates[session.id]
                    val isExecuting = state is AgentUIState.Loading || state is AgentUIState.Streaming
                    val subSessions = subSessionsByParent[session.id].orEmpty()
                    val expanded = session.id in expandedIds
                    val subSelectedCount = subSessions.count { it.id in selectedSessionIds }
                    ChatSessionRow(
                        session = session,
                        selected = if (selectionMode) false else session.id == currentSessionId,
                        isExecuting = isExecuting,
                        awaitingPermission = session.id in awaitingPermissionSessionIds,
                        awaitingQuestion = session.id in awaitingQuestionSessionIds,
                        pinned = session.isPinned,
                        selectionMode = selectionMode,
                        checked = session.id in selectedSessionIds,
                        triState = if (subSessions.isEmpty()) null else {
                            when {
                                subSelectedCount == subSessions.size -> ToggleableState.On
                                subSelectedCount > 0 -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            }
                        },
                        onCheckedChange = { onToggleSelectSession(session) },
                        onClick = {
                            if (selectionMode) {
                                onToggleSelectSession(session)
                            } else {
                                onSelect(session)
                            }
                        },
                        onLongClick = {
                            if (selectionMode) {
                                onToggleSelectSession(session)
                            } else {
                                onLongClick(session)
                            }
                        },
                        trailing = if (subSessions.isEmpty()) null else {
                            {
                                SubAgentExpandToggle(
                                    expanded = expanded,
                                    count = subSessions.size,
                                    onToggle = {
                                        expandedIds = if (expanded) {
                                            expandedIds - session.id
                                        } else {
                                            expandedIds + session.id
                                        }
                                    }
                                )
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(expandFrom = androidx.compose.ui.Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top) + fadeOut()
                    ) {
                        Column {
                            subSessions.forEach { sub ->
                                val subState = agentStates[sub.id]
                                Row(modifier = Modifier.padding(start = Spacing.lg)) {
                                    ChatSessionRow(
                                        session = sub,
                                        selected = if (selectionMode) false else sub.id == currentSessionId,
                                        isExecuting = subState is AgentUIState.Loading ||
                                            subState is AgentUIState.Streaming,
                                        awaitingPermission = sub.id in awaitingPermissionSessionIds,
                                        pinned = false,
                                        selectionMode = selectionMode,
                                        checked = sub.id in selectedSessionIds,
                                        onCheckedChange = { onToggleSelectSession(sub) },
                                        onClick = {
                                            if (selectionMode) {
                                                onToggleSelectSession(sub)
                                            } else {
                                                onSelect(sub)
                                            }
                                        },
                                        onLongClick = {
                                            if (selectionMode) {
                                                onToggleSelectSession(sub)
                                            } else {
                                                onLongClick(sub)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tab0 底部搜索框：复用设置页的胶囊搜索框，非空时带清除按钮。 */
@Composable
internal fun ChatSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModelSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.chat_search_hint),
        modifier = modifier.fillMaxWidth(),
        trailing = if (query.isEmpty()) null else {
            {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = FeatherIcons.X,
                        contentDescription = stringResource(R.string.chat_search_clear),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

/** 搜索结果区：加载中 / 无结果 / 命中列表三态。 */
@Composable
internal fun ChatSearchResults(
    state: ChatSearchState,
    onOpenHit: (ChatSearchHit) -> Unit
) {
    when {
        state.hits.isEmpty() && state.loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DrawerProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.hits.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_search_no_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.hits, key = { it.messageId }) { hit ->
                    ChatSearchResultRow(
                        hit = hit,
                        query = state.query,
                        onClick = { onOpenHit(hit) }
                    )
                    SettingsDivider()
                }
            }
        }
    }
}

/** 单条搜索结果行：会话标题 + 命中片段（关键词高亮），点击跳转并定位。 */
@Composable
internal fun ChatSearchResultRow(
    hit: ChatSearchHit,
    query: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Text(
            text = hit.sessionTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = highlightSnippet(
                snippet = hit.snippet,
                query = query,
                highlight = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 把片段中所有命中词标为高亮样式。 */
private fun highlightSnippet(snippet: String, query: String, highlight: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(snippet)
        if (query.isBlank()) return@buildAnnotatedString
        var start = 0
        while (true) {
            val index = snippet.indexOf(query, start, ignoreCase = true)
            if (index < 0) break
            addStyle(highlight, index, index + query.length)
            start = index + query.length
        }
    }

/** 会话行尾的子代理展开开关：显示数量与箭头，自己消费点击，不触发整行选中。 */
@Composable
private fun SubAgentExpandToggle(
    expanded: Boolean,
    count: Int,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExpandableChevronIcon(
            expanded = expanded,
            style = ChevronRotationStyle.RIGHT_DOWN,
            contentDescription = stringResource(
                if (expanded) R.string.drawer_collapse_subagents else R.string.drawer_expand_subagents
            ),
            size = 16.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Tab1：当前工作区的文件树。从工作区根就地展开，缩进表示层级；
 * 仅「单行横向滚动」档每行共享同一横向滚动，路径过深时左右滑动查看完整名称，不再截断成省略号；
 * 其余档位（最多两行 / 单行省略号）行宽取容器宽度，文本按档位换行或省略号截断。
 * 新建文件/文件夹通过长按目录（含工作区根）行的菜单发起。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileBrowserTab(
    state: FileBrowseState,
    fileTreePrefs: FileTreeDisplayPrefs,
    browseSort: BrowseSortMode,
    onBrowseSortChange: (BrowseSortMode) -> Unit,
    onCollapseToPath: (String) -> Unit,
    onToggleShowHiddenFiles: (Boolean) -> Unit,
    expandedPaths: Set<String>,
    expandingPath: String?,
    fileOpPaths: Set<String>,
    clipboard: BrowseClipboard?,
    onToggleExpand: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenWith: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onSearchFiles: (String, String, (List<FileTreeNode>) -> Unit) -> Unit = { _, _, onResult -> onResult(emptyList()) },
    onRenameEntry: (String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCopyEntry: (String, String) -> Unit,
    onCutEntry: (String, String) -> Unit,
    onPasteEntry: (String, (Boolean) -> Unit) -> Unit,
    onClearClipboard: () -> Unit,
    workspaceName: String,
    onAddLocalFolder: (Uri) -> Unit,
    onRemoveLocalFolder: (String) -> Unit,
    onUploadFiles: (String, List<Uri>) -> Unit,
    onUploadFolder: (String, Uri) -> Unit,
    onShareEntries: (Set<String>) -> Unit,
    onSendSelectionToChat: (Set<String>) -> Unit,
    onExportEntries: (Set<String>, Uri) -> Unit,
    onImportFiles: (List<Uri>, String) -> Unit,
    onCopyEntries: (Set<String>) -> Unit,
    onCutEntries: (Set<String>) -> Unit,
    onPasteEntries: (String) -> Unit,
    batchCut: Set<String> = emptySet(),
    onClearBatchCut: () -> Unit,
    pluginEntries: List<PluginUiEntry>,
    pluginLanguageTag: String,
    onPluginRunAction: (PluginUiEntry, String, Boolean) -> Unit,
    onBatchDelete: (String) -> Unit,
    onLoadDirectoryInfo: (String, (DirectoryInfo?) -> Unit) -> Unit
) {
    // 编辑模式：点击编辑按钮进入，行前置复选框可勾选；选中后可批量分享/导出/移动/删除
    var editMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 用户手动取消的后代（从选中文件夹的整体选中中排除），用于文件夹半选态
    var unselectedFromFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val inSelectionMode = editMode
    // 导出待处理路径（单条或批量），SAF 回调后用
    var exportPendingPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 导入目标目录：工作区根
    val workspaceRoot = (state as? FileBrowseState.Success)?.nodes?.firstOrNull()?.path ?: "~/workspace"
    // 根行 ⋯ 菜单可见性
    var showRootMenu by remember { mutableStateOf(false) }
    // 搜索：⋯ 菜单打开后按文件名过滤当前可见节点
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 面包屑焦点目录：默认工作区根，展开任意根/目录时跟随，点上级折回
    var focusPath by remember { mutableStateOf(workspaceRoot) }
    // SSH 根搜索是递归的，结果异步回来；工作区/容器仍用下面本地过滤。
    var remoteSearchHits by remember { mutableStateOf<List<FileTreeNode>>(emptyList()) }
    var remoteSearchLoading by remember { mutableStateOf(false) }
    val remoteSearchScope = focusPath.takeIf { it.startsWith(RemoteServerPaths.PREFIX) }
    val remoteSearchActive = searchActive && searchQuery.isNotBlank() && remoteSearchScope != null
    LaunchedEffect(searchActive, searchQuery, remoteSearchScope) {
        if (!remoteSearchActive || remoteSearchScope == null) {
            remoteSearchHits = emptyList()
            remoteSearchLoading = false
            return@LaunchedEffect
        }
        val requested = searchQuery
        remoteSearchLoading = true
        onSearchFiles(remoteSearchScope, requested) { hits ->
            // 查询已变时丢弃旧结果，避免慢函数返回覆盖新搜索。
            if (searchQuery == requested) {
                remoteSearchHits = hits
                remoteSearchLoading = false
            }
        }
    }
    // 上传时记住长按的目标目录；系统选择器回调时目标目录行可能已被刷新，不能用菜单节点。
    var uploadTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // 「+」添加本机文件夹：系统目录选择器，选中后持久化授权并登记为文件树根。
    val localFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onAddLocalFolder(uri)
        }
    }
    val uploadFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val target = uploadTarget
        uploadTarget = null
        if (target != null && uris.isNotEmpty()) onUploadFiles(target, uris)
    }
    val uploadFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val target = uploadTarget
        uploadTarget = null
        if (target != null && uri != null) onUploadFolder(target, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportFiles(uris, workspaceRoot)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && exportPendingPaths.isNotEmpty()) onExportEntries(exportPendingPaths, uri)
        exportPendingPaths = emptySet()
    }
    var creating by remember { mutableStateOf<CreateTarget?>(null) }
    var menuNode by remember { mutableStateOf<FileTreeNode?>(null) }
    var pendingRename by remember { mutableStateOf<FileTreeNode?>(null) }
    var pendingDelete by remember { mutableStateOf<FileTreeNode?>(null) }
    // 「属性」抽屉待展示节点：菜单先收再弹，复用 onRename/onDelete 的过渡模式。
    var pendingProps by remember { mutableStateOf<FileTreeNode?>(null) }
    // 多选批量删除二次确认弹窗可见性（复用设置页 BatchDeleteConfirmDialog 模式）
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    val browseNodes = (state as? FileBrowseState.Success)?.nodes.orEmpty()
    fun isSelected(path: String): Boolean =
        path in selectedPaths ||
            (selectedPaths.any { f -> f != path && path.startsWith("$f/") } && path !in unselectedFromFolders)
    fun descendantsOf(path: String): Set<String> = browseNodes
        .filter { it.path == path || it.path.startsWith("$path/") }
        .map { it.path }.toSet()
    fun directChildrenOf(path: String, depth: Int): List<FileTreeNode> = browseNodes
        .filter { it.depth == depth + 1 && it.path.startsWith("$path/") }
    // 选择态：0 未选 / 1 全选 / 2 部分（仅文件夹）
    fun selectionStateOf(node: FileTreeNode): Int {
        if (!node.entry.isDirectory) return if (isSelected(node.path)) 1 else 0
        val children = directChildrenOf(node.path, node.depth)
        if (children.isEmpty()) return if (isSelected(node.path)) 1 else 0
        val selCount = children.count { isSelected(it.path) }
        return when {
            selCount == 0 -> 0
            selCount == children.size -> 1
            else -> 2
        }
    }
    fun toggleSelect(node: FileTreeNode) {
        // 根节点不参与多选勾选：避免把整个远程服务器/本机文件夹带入批量删除、分享或复制
        if (node.root is FileTreeRoot.LocalFolder || node.root is FileTreeRoot.RemoteServer) return
        val kids = descendantsOf(node.path)
        if (node.entry.isDirectory) {
            val children = directChildrenOf(node.path, node.depth)
            val allSel = children.isNotEmpty() && children.all { isSelected(it.path) }
            if (allSel) {
                // 全选 → 取消全部（文件夹 + 所有后代）
                selectedPaths = selectedPaths - kids
                unselectedFromFolders = unselectedFromFolders - kids
            } else {
                // 未选 / 部分 → 全选文件夹（后代经祖先推断全部高亮）
                selectedPaths = selectedPaths + node.path
                unselectedFromFolders = unselectedFromFolders - kids
            }
        } else {
            if (isSelected(node.path)) {
                selectedPaths = selectedPaths - node.path
                unselectedFromFolders = unselectedFromFolders + node.path
            } else {
                selectedPaths = selectedPaths + node.path
                unselectedFromFolders = unselectedFromFolders - node.path
            }
        }
    }
    // 搜索过滤后的可见节点：SSH 根递归搜索用异步结果，其余只按名称过滤当前可见节点。
    val visibleNodes = if (remoteSearchActive) {
        remoteSearchHits
    } else {
        browseNodes.filter { node ->
            searchQuery.isBlank() || node.displayName.contains(searchQuery, ignoreCase = true)
        }
    }
    // 多选「全选/取消全选」：对当前可见的所有文件（非目录）切换选择
    fun toggleSelectAll() {
        val filePaths = visibleNodes.filter { !it.entry.isDirectory }.map { it.path }.toSet()
        val allSelected = filePaths.isNotEmpty() && filePaths.all { isSelected(it) }
        selectedPaths = if (allSelected) emptySet() else filePaths
        unselectedFromFolders = emptySet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部操作栏：面包屑（当前路径 + 点上级返回）+ 剪贴板/操作中指示 + ⋯ 菜单。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileTreeBreadcrumb(
                    focusPath = focusPath,
                    workspaceRoot = workspaceRoot,
                    rootLabel = workspaceName.ifBlank { stringResource(R.string.file_browser_title) },
                    onNavigate = { path ->
                        focusPath = path
                        onCollapseToPath(path)
                    },
                    modifier = Modifier.weight(1f)
                )
                clipboard?.let { clip ->
                    ClipboardIndicator(
                        clipboard = clip,
                        onClear = onClearClipboard
                    )
                }
                if (fileOpPaths.isNotEmpty()) {
                    DrawerProgressIndicator(
                        modifier = Modifier.padding(horizontal = Spacing.xs).size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (editMode) {
                    TextButton(
                        onClick = {
                            editMode = false
                            selectedPaths = emptySet()
                            unselectedFromFolders = emptySet()
                        },
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.common_done))
                    }
                }
                // 「+」添加本机文件夹作为独立文件树根。
                IconButton(
                    onClick = { localFolderLauncher.launch(null) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = stringResource(R.string.file_browser_add_folder),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 根行工具收进 ⋯ 菜单：新建/导入/搜索/显示隐藏/编辑/刷新 + 排序方式。
                Box {
                    IconButton(
                        onClick = { showRootMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = FeatherIcons.MoreHorizontal,
                            contentDescription = stringResource(R.string.common_more),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showRootMenu,
                        onDismissRequest = { showRootMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_new_file)) },
                            leadingIcon = { Icon(FeatherIcons.FilePlus, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showRootMenu = false
                                creating = CreateTarget(focusPath, CreateKind.FILE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_new_folder)) },
                            leadingIcon = { Icon(FeatherIcons.FolderPlus, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showRootMenu = false
                                creating = CreateTarget(focusPath, CreateKind.FOLDER)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_import)) },
                            leadingIcon = { Icon(FeatherIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showRootMenu = false
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_search)) },
                            leadingIcon = { Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showRootMenu = false
                                searchActive = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_show_hidden)) },
                            leadingIcon = { Icon(FeatherIcons.Eye, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (fileTreePrefs.showHiddenFiles) {
                                    Icon(
                                        FeatherIcons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                showRootMenu = false
                                onToggleShowHiddenFiles(!fileTreePrefs.showHiddenFiles)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_edit_mode)) },
                            leadingIcon = { Icon(FeatherIcons.Edit2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (editMode) {
                                    Icon(
                                        FeatherIcons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                showRootMenu = false
                                editMode = !editMode
                                if (editMode) selectedPaths = emptySet()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_refresh)) },
                            leadingIcon = { Icon(FeatherIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showRootMenu = false
                                onRefresh()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_sort_name)) },
                            trailingIcon = {
                                if (browseSort == BrowseSortMode.NAME) {
                                    Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                showRootMenu = false
                                onBrowseSortChange(BrowseSortMode.NAME)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_sort_size)) },
                            trailingIcon = {
                                if (browseSort == BrowseSortMode.SIZE) {
                                    Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                showRootMenu = false
                                onBrowseSortChange(BrowseSortMode.SIZE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_browser_sort_modified)) },
                            trailingIcon = {
                                if (browseSort == BrowseSortMode.MODIFIED) {
                                    Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                showRootMenu = false
                                onBrowseSortChange(BrowseSortMode.MODIFIED)
                            }
                        )
                    }
                }
            }
            // 搜索输入：按文件名过滤当前可见节点。
            if (searchActive) {
                ModelSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.file_browser_search_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    trailing = {
                        IconButton(
                            onClick = {
                                searchActive = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.X,
                                contentDescription = stringResource(R.string.file_browser_cancel_selection),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            // 文件树主体
            Box(modifier = Modifier.weight(1f)) {
        when (state) {
            is FileBrowseState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                DrawerProgressIndicator(modifier = Modifier.size(24.dp))
            }

            is FileBrowseState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.detail ?: stringResource(R.string.file_browser_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }

            is FileBrowseState.Success -> {
                val listState = rememberLazyListState()
                val textMeasurer = rememberTextMeasurer()
                val textStyle = MaterialTheme.typography.bodyMedium
                val density = LocalDensity.current
                // 缩进宽度按偏好：标准档 16dp（历史值）/ 紧凑档 10dp。
                val indentWidth = fileTreePrefs.indentDensity.indentDp.dp
                // 文件名显示档位：仅「单行横向滚动」档需要横向滚动与统一宽度预算；
                // 最多两行 / 单行省略号档行宽取容器宽度，让文本按档位换行或截断。
                val nameScrolls = fileTreePrefs.fileNameDisplay == FileNameDisplay.SINGLE_LINE_SCROLL
                // 预算最宽一行的内容宽度（固定开销 + 缩进 + 文本），供所有行取统一宽度，
                // 以实现整棵树统一横向平移（而非每行各自滚动）。
                // 仅滚动档生效；remember key 含 nameScrolls，档位切换时重算（否则切回滚动档会拿到缓存 0）。
                // 逐个 measure 在大目录下会在组合期同步跑上千次文本测量；先按「缩进 + 字符宽度权重」
                // 挑出最宽的那一行，只对它做一次真实测量。估算不精确，但这里只用来给横向滚动留够宽度。
                val maxContentPx = remember(visibleNodes, textStyle, indentWidth, nameScrolls) {
                    if (!nameScrolls) return@remember 0
                    with(density) {
                        val widest = visibleNodes.maxByOrNull { node ->
                            node.depth * 4 + node.displayName.sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 }
                        } ?: return@with 0
                        val textW = textMeasurer.measure(widest.displayName, textStyle).size.width
                        (FILE_TREE_ROW_OVERHEAD.toPx() + (indentWidth * widest.depth).toPx() + textW).toInt()
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val rowWidthPx = if (nameScrolls) maxOf(constraints.maxWidth, maxContentPx) else constraints.maxWidth
                    val rowWidth = with(density) { rowWidthPx.toDp() }
                    LazyColumn(
                        state = listState,
                        modifier = if (nameScrolls) Modifier.fillMaxHeight().horizontalScroll(hScroll)
                        else Modifier.fillMaxHeight()
                    ) {
                        items(visibleNodes, key = { it.path }) { node ->
                            val selState = selectionStateOf(node)
                            FileTreeRow(
                                node = node,
                                rowWidth = rowWidth,
                                fileTreePrefs = fileTreePrefs,
                                expanding = node.entry.isDirectory && node.path == expandingPath,
                                busy = node.path in fileOpPaths,
                                selectionState = selState,
                                inSelectionMode = inSelectionMode,
                                onClick = {
                                    when {
                                        batchCut.isNotEmpty() && node.entry.isDirectory -> onPasteEntries(node.path)
                                        // 编辑模式：文件夹→进入查看，文件→勾选
                                        inSelectionMode && node.entry.isDirectory -> onToggleExpand(node.path)
                                        inSelectionMode -> toggleSelect(node)
                                        node.entry.isDirectory -> {
                                            onToggleExpand(node.path)
                                            // 焦点跟随任意根/目录：顶部「新建」与 SSH 搜索都按当前焦点作用域。
                                            focusPath = node.path
                                        }
                                        else -> {
                                            // 文件：焦点回到所在目录，顶部新建仍落在同一目录。
                                            focusPath = node.path.substringBeforeLast('/').ifEmpty { node.path }
                                            onOpenFile(node.path)
                                        }
                                    }
                                },
                                onLongClick = {
                                    focusPath = if (node.entry.isDirectory) {
                                        node.path
                                    } else {
                                        node.path.substringBeforeLast('/').ifEmpty { node.path }
                                    }
                                    menuNode = node
                                },
                                onToggleSelect = { toggleSelect(node) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                    placementSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                )
                            )
                        }
                    }
                }
            }
        }
        if (state is FileBrowseState.Success && remoteSearchLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                DrawerProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (state is FileBrowseState.Success && searchActive && searchQuery.isNotBlank() && visibleNodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.file_browser_search_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
            }
        }
        // 多选批量操作栏：编辑模式且选中后底部单行显示分享/复制/移动/删除/更多/取消；
        // 低频的导出/全选收进「更多」；容器圆角与其他浮层保持一致。
        if (inSelectionMode && selectedPaths.isNotEmpty()) {
            var showSelectionMore by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(LocalUIDensityTokens.current.cardCorner),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.file_browser_selected_count, selectedPaths.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs)
                    )
                    IconButton(onClick = { onShareEntries(selectedPaths) }, modifier = Modifier.size(36.dp)) {
                        Icon(FeatherIcons.Share2, contentDescription = stringResource(R.string.file_browser_share), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        onCopyEntries(selectedPaths)
                        selectedPaths = emptySet()
                        unselectedFromFolders = emptySet()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(FeatherIcons.Copy, contentDescription = stringResource(R.string.common_copy), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        onCutEntries(selectedPaths)
                        selectedPaths = emptySet()
                        unselectedFromFolders = emptySet()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(FeatherIcons.CornerUpRight, contentDescription = stringResource(R.string.file_browser_move), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    // 删除：先弹二次确认，与技能/子代理/MCP/提供商/提示词页的批量删除确认保持一致。
                    IconButton(onClick = { showBatchDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(FeatherIcons.Trash2, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                    // 更多：导出/全选等低频动作收进这里。
                    Box {
                        IconButton(onClick = { showSelectionMore = true }, modifier = Modifier.size(36.dp)) {
                            Icon(FeatherIcons.MoreHorizontal, contentDescription = stringResource(R.string.common_more), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showSelectionMore, onDismissRequest = { showSelectionMore = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_browser_export)) },
                                leadingIcon = { Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showSelectionMore = false
                                    exportPendingPaths = selectedPaths
                                    exportLauncher.launch("export_${System.currentTimeMillis()}.zip")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_browser_select_all)) },
                                leadingIcon = { Icon(FeatherIcons.CheckSquare, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showSelectionMore = false
                                    toggleSelectAll()
                                }
                            )
                            // 发送至聊天：把选中项按 @引用 追加进当前会话输入框，随后收起侧栏由上层处理。
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_browser_send_to_chat)) },
                                leadingIcon = { Icon(FeatherIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showSelectionMore = false
                                    onSendSelectionToChat(selectedPaths)
                                    selectedPaths = emptySet()
                                    unselectedFromFolders = emptySet()
                                }
                            )
                        }
                    }
                    IconButton(onClick = { selectedPaths = emptySet(); unselectedFromFolders = emptySet(); editMode = false }, modifier = Modifier.size(36.dp)) {
                        Icon(FeatherIcons.X, contentDescription = stringResource(R.string.file_browser_cancel_selection), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        // 多选批量删除二次确认：确认后逐条删除并清空选择（与设置页 BatchDeleteConfirmDialog 同款）。
        if (showBatchDeleteConfirm) {
            BatchDeleteConfirmDialog(
                title = stringResource(R.string.file_browser_batch_delete_title),
                message = stringResource(R.string.file_browser_batch_delete_confirm, selectedPaths.size),
                onConfirm = {
                    showBatchDeleteConfirm = false
                    selectedPaths.forEach { onBatchDelete(it) }
                    selectedPaths = emptySet()
                    unselectedFromFolders = emptySet()
                },
                onDismiss = { showBatchDeleteConfirm = false }
            )
        }
        // 批量移动：有 batchCut 时底部提示点击目标目录粘贴
        if (batchCut.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.file_browser_move_cut_hint, batchCut.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onClearBatchCut() }) {
                        Text(stringResource(R.string.file_browser_cancel_selection))
                    }
                }
            }
        }
    }

    creating?.let { target ->
        FileNameInputDialog(
            title = stringResource(
                if (target.kind == CreateKind.FILE) R.string.file_browser_new_file
                else R.string.file_browser_new_folder
            ),
            confirmLabel = stringResource(R.string.common_create),
            initialName = "",
            onConfirm = { name ->
                if (target.kind == CreateKind.FILE) onCreateFile(target.parent, name)
                else onCreateFolder(target.parent, name)
                creating = null
            },
            onDismiss = { creating = null }
        )
    }

    menuNode?.let { node ->
        val root = node.root
        // 上传是从设备选择文件/文件夹写入当前目录：只对容器内目录（工作区、容器、其子目录）提供，
        // 本机文件夹本来就在设备上，不需要上传。
        val isLocalFolderPath = WorkspacePathMapper.isLocalFolderContainerPath(node.path)
        val uploadFileAction: (() -> Unit)? = if (node.entry.isDirectory && !isLocalFolderPath) {
            {
                menuNode = null
                uploadTarget = node.path
                uploadFileLauncher.launch(arrayOf("*/*"))
            }
        } else null
        val uploadFolderAction: (() -> Unit)? = if (node.entry.isDirectory && !isLocalFolderPath) {
            {
                menuNode = null
                uploadTarget = node.path
                uploadFolderLauncher.launch(null)
            }
        } else null
        val removeFolderAction: (() -> Unit)? = if (root is FileTreeRoot.LocalFolder) {
            {
                menuNode = null
                onRemoveLocalFolder(root.hostPath)
            }
        } else null
        // 应用内不便处理/不适合渲染的文件交给系统应用打开（远程先整份下载到宿主再授权分享）：
        // 对所有文件开放，仅目录不显示这一项（issue #224）。
        val openWithAction: (() -> Unit)? = if (node.entry.isDirectory) {
            null
        } else {
            {
                menuNode = null
                onOpenWith(node.path)
            }
        }
        FileTreeActionSheet(
            node = node,
            clipboard = clipboard,
            pluginEntries = pluginEntries,
            languageTag = pluginLanguageTag,
            onRunAction = onPluginRunAction,
            onUploadFile = uploadFileAction,
            onUploadFolder = uploadFolderAction,
            onOpenWith = openWithAction,
            onRemoveLocalFolder = removeFolderAction,
            onNewFile = {
                menuNode = null
                if (node.root == null && node.path !in expandedPaths) onToggleExpand(node.path)
                creating = CreateTarget(node.path, CreateKind.FILE)
            },
            onNewFolder = {
                menuNode = null
                if (node.root == null && node.path !in expandedPaths) onToggleExpand(node.path)
                creating = CreateTarget(node.path, CreateKind.FOLDER)
            },
            onRename = {
                menuNode = null
                pendingRename = node
            },
            onDelete = {
                menuNode = null
                pendingDelete = node
            },
            onCopy = {
                menuNode = null
                onCopyEntry(node.path, node.entry.name)
            },
            onCut = {
                menuNode = null
                onCutEntry(node.path, node.entry.name)
            },
            onPasteHere = {
                val target = node.path
                menuNode = null
                onPasteEntry(target) { /* 结果由调用方决定是否提示，这里保持静默 */ }
            },
            onShare = {
                menuNode = null
                onShareEntries(setOf(node.path))
            },
            onExport = {
                menuNode = null
                exportPendingPaths = setOf(node.path)
                exportLauncher.launch("export_${node.entry.name}.zip")
            },
            onSendToChat = {
                menuNode = null
                onSendSelectionToChat(setOf(node.path))
            },
            onProps = {
                menuNode = null
                pendingProps = node
            },
            onDismiss = { menuNode = null }
        )
    }

    pendingRename?.let { node ->
        FileNameInputDialog(
            title = stringResource(R.string.common_rename),
            confirmLabel = stringResource(R.string.common_rename),
            initialName = node.entry.name,
            onConfirm = { name ->
                onRenameEntry(node.path, name)
                pendingRename = null
            },
            onDismiss = { pendingRename = null }
        )
    }

    pendingDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = {
                Text(
                    stringResource(
                        if (node.entry.isDirectory) {
                            R.string.file_browser_delete_folder_confirm
                        } else {
                            R.string.file_browser_delete_file_confirm
                        },
                        node.entry.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(node.path)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    pendingProps?.let { node ->
        FilePropertiesSheet(
            entry = node.entry,
            path = node.path,
            displayName = node.displayName,
            onDismiss = { pendingProps = null },
            onLoadDirectoryInfo = onLoadDirectoryInfo
        )
    }
}

/** 新建对象类型，决定确认后调创建文件还是创建文件夹。 */
private enum class CreateKind { FILE, FOLDER }

/** 新建目标：在哪个目录（[parent]）下新建，以及新建文件还是文件夹（[kind]）。 */
private data class CreateTarget(val parent: String, val kind: CreateKind)

/** 新建 / 重命名共用的名称输入弹窗：名称非法或与原名相同时禁用确认。 */
@Composable
private fun FileNameInputDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = stringResource(R.string.file_browser_name_label),
                modifier = Modifier.fillMaxWidth(),
                colors = dialogTextFieldColors()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = isValidFileEntryName(name) && name.trim() != initialName
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 剪切板指示器：右上角刷新按钮旁的主题色图标，显示复制/剪切状态，点击清空。 */
@Composable
private fun ClipboardIndicator(
    clipboard: BrowseClipboard,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClear,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (clipboard.isCut) Icons.Filled.ContentCut else FeatherIcons.Copy,
            contentDescription = stringResource(
                if (clipboard.isCut) R.string.file_browser_clipboard_cut
                else R.string.file_browser_clipboard_copy
            ),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** 文件树顶部面包屑：显示焦点目录的相对路径段（根段为当前工作区名），点非末段「返回上级」（折叠更深层）。 */
@Composable
private fun FileTreeBreadcrumb(
    focusPath: String,
    workspaceRoot: String,
    rootLabel: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = buildBreadcrumbSegments(focusPath, workspaceRoot, rootLabel)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            val isLast = index == segments.lastIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onNavigate(path) }
                    .padding(horizontal = Spacing.xs, vertical = 2.dp)
            )
        }
    }
}

/** 把焦点目录拆成面包屑段：根段用 [rootLabel]（当前工作区名），其余为逐级目录名，携带累积路径供返回。 */
private fun buildBreadcrumbSegments(focusPath: String, workspaceRoot: String, rootLabel: String): List<Pair<String, String>> {
    val base = workspaceRoot.trimEnd('/')
    if (!focusPath.startsWith(base)) {
        // 非工作区根（如容器根）：仅显示末段名，不可导航。
        val label = focusPath.trim('/').substringAfterLast('/').ifEmpty { focusPath }
        return listOf(label to focusPath)
    }
    val rel = focusPath.removePrefix(base).trim('/')
    val parts = if (rel.isEmpty()) emptyList() else rel.split("/")
    val segments = mutableListOf<Pair<String, String>>()
    var acc = base
    segments += rootLabel to base
    for (p in parts) {
        acc = "$acc/$p"
        segments += p to acc
    }
    return segments
}

/** 文件树节点长按弹出的功能菜单：容器内目录（含工作区根）可新建与上传文件/文件夹，
 * 非根节点可复制/剪切/重命名/删除，剪切板非空时目录可「粘贴到此处」；本机文件夹根可移除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTreeActionSheet(
    node: FileTreeNode,
    clipboard: BrowseClipboard?,
    pluginEntries: List<PluginUiEntry>,
    languageTag: String,
    onRunAction: (PluginUiEntry, String, Boolean) -> Unit,
    onUploadFile: (() -> Unit)?,
    onUploadFolder: (() -> Unit)?,
    onOpenWith: (() -> Unit)?,
    onRemoveLocalFolder: (() -> Unit)?,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPasteHere: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onSendToChat: () -> Unit,
    onProps: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = node.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            // 剪切板非空且当前节点是目录 / 根：优先提供「粘贴到此处」。
            if (clipboard != null && node.entry.isDirectory) {
                SheetActionRow(
                    icon = FeatherIcons.Clipboard,
                    label = stringResource(R.string.file_browser_paste_here),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onPasteHere
                )
            }
            if (node.entry.isDirectory) {
                SheetActionRow(
                    icon = FeatherIcons.FilePlus,
                    label = stringResource(R.string.file_browser_new_file),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onNewFile
                )
                SheetActionRow(
                    icon = FeatherIcons.FolderPlus,
                    label = stringResource(R.string.file_browser_new_folder),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onNewFolder
                )
            }
            onUploadFile?.let { action ->
                SheetActionRow(
                    icon = FeatherIcons.Upload,
                    label = stringResource(R.string.file_browser_upload_file),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = action
                )
            }
            onUploadFolder?.let { action ->
                SheetActionRow(
                    icon = FeatherIcons.UploadCloud,
                    label = stringResource(R.string.file_browser_upload_folder),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = action
                )
            }
            onOpenWith?.let { action ->
                SheetActionRow(
                    icon = FeatherIcons.ExternalLink,
                    label = stringResource(R.string.attachment_open_external),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = action
                )
            }
            if (node.root == null) {
                SheetActionRow(
                    icon = FeatherIcons.Share2,
                    label = stringResource(R.string.file_browser_share),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onShare
                )
                SheetActionRow(
                    icon = FeatherIcons.Download,
                    label = stringResource(R.string.file_browser_export),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onExport
                )
                SheetActionRow(
                    icon = FeatherIcons.Send,
                    label = stringResource(R.string.file_browser_send_to_chat),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onSendToChat
                )
                SheetActionRow(
                    icon = FeatherIcons.Copy,
                    label = stringResource(R.string.common_copy),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onCopy
                )
                SheetActionRow(
                    icon = Icons.Filled.ContentCut,
                    label = stringResource(R.string.common_cut),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onCut
                )
                SheetActionRow(
                    icon = FeatherIcons.Edit2,
                    label = stringResource(R.string.common_rename),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onRename
                )
                SheetActionRow(
                    icon = FeatherIcons.Trash2,
                    label = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
            // 「属性」：常规操作末尾（删除项之后、插件入口之前），根节点也显示。
            SheetActionRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.file_browser_properties),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onProps
            )
            onRemoveLocalFolder?.let { action ->
                SheetActionRow(
                    icon = FeatherIcons.X,
                    label = stringResource(R.string.file_browser_remove_folder),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = action
                )
            }
            // 文件树长按菜单的插件入口（files.tree.menu）：只接受动作入口，把文件路径交给 handler。
            if (pluginEntries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.plugins_slot_welcome_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.md, bottom = Spacing.sm)
                )
                pluginEntries.forEach { entry ->
                    SheetActionRow(
                        icon = pluginPageIcon(entry.icon),
                        label = entry.title.resolve(languageTag),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            onDismiss()
                            onRunAction(entry, node.path, node.entry.isDirectory)
                        }
                    )
                }
            }
        }
    }
}

/** 文件树的一行：按 [FileTreeNode.depth] 缩进，目录带展开箭头。
 * 仅「单行横向滚动」档所有行取统一的 [rowWidth]（与外层 horizontalScroll 配合），横向滑动时整树一起平移，
 * 名称不截断、不换行；其余档位行宽取容器宽度，按档位换行（最多两行）或省略号截断。
 * 着色优先级：读取失败 > .gitignore 命中（橙）> dotfile（弱化）> 常规。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileTreeNode,
    rowWidth: Dp,
    fileTreePrefs: FileTreeDisplayPrefs,
    expanding: Boolean,
    busy: Boolean,
    selectionState: Int = 0, // 0 未选 / 1 全选 / 2 部分（仅文件夹）
    inSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 展开加载态加最小延迟：本地模式列目录极快，避免每次展开都闪一下转圈。
    var showExpanding by remember { mutableStateOf(false) }
    LaunchedEffect(expanding) {
        if (expanding) {
            delay(180)
            showExpanding = true
        } else {
            showExpanding = false
        }
    }
    val isDotfile = node.root == null && node.entry.name.startsWith(".")
    // 弱化着色关闭时：.gitignore / dotfile 均用常规色（读取失败仍标红），图标与文本一致。
    val decorationColor: Color? = when {
        node.hasError -> MaterialTheme.colorScheme.error
        !fileTreePrefs.mutedColoring -> null
        node.ignored -> FileTreeIgnoredColor
        isDotfile -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> null
    }
    val indentWidth = fileTreePrefs.indentDensity.indentDp.dp
    val fileNameDisplay = fileTreePrefs.fileNameDisplay
    Row(
        modifier = modifier
            .width(rowWidth)
            .background(if (selectionState > 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clip(RoundedCornerShape(Radius.md))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = Spacing.xs + 2.dp)
            .padding(start = Spacing.sm, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode && (node.root is FileTreeRoot.LocalFolder || node.root is FileTreeRoot.RemoteServer)) {
            // 挂载根/SSH 根不参与多选：不显示复选框，用等宽占位保持右侧内容对齐
            Spacer(Modifier.width(18.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        if (inSelectionMode && node.root !is FileTreeRoot.LocalFolder && node.root !is FileTreeRoot.RemoteServer) {
            val checked = selectionState == 1
            val partial = selectionState == 2
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (checked || partial) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (checked || partial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleSelect() },
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = FeatherIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                // 部分选中：蓝色实心方块无符号（仅文件夹）
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        Spacer(Modifier.width(indentWidth * node.depth))
        if (node.entry.isDirectory) {
            if (showExpanding) {
                DrawerProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                ExpandableChevronIcon(
                    expanded = node.isExpanded,
                    style = ChevronRotationStyle.RIGHT_DOWN,
                    contentDescription = stringResource(
                        if (node.isExpanded) R.string.file_browser_collapse else R.string.file_browser_expand
                    ),
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.width(Spacing.xs))
        if (busy) {
            DrawerProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            FileTreeIcon(node = node, decorationColor = decorationColor)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = node.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = decorationColor ?: MaterialTheme.colorScheme.onSurface,
            // 文件名显示偏好：单行横向滚动（历史默认）/ 最多两行换行 / 单行省略号。
            maxLines = if (fileNameDisplay == FileNameDisplay.TWO_LINES) 2 else 1,
            softWrap = fileNameDisplay == FileNameDisplay.TWO_LINES,
            overflow = if (fileNameDisplay == FileNameDisplay.SINGLE_LINE_ELLIPSIS) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    }
}

/** 文件树行的类型图标：目录用文件夹，文件按扩展名挑选单色线性图标。容器根用「容器与镜像」列表同款图标，
 * 本机文件夹根用磁盘图标。
 * [decorationColor] 非空时统一染色（.gitignore/dotfile 弱化）。 */
@Composable
private fun FileTreeIcon(node: FileTreeNode, decorationColor: Color?) {
    val containerRoot = node.root as? FileTreeRoot.Container
    if (containerRoot != null) {
        val tint = decorationColor ?: MaterialTheme.colorScheme.primary
        val logo = containerOsLogo(containerRoot.osId)
        if (logo != null) {
            Icon(
                painter = logo,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
        } else {
            Icon(
                imageVector = if (containerRoot.remote) FeatherIcons.Server else FeatherIcons.HardDrive,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
        }
        return
    }
    if (node.root is FileTreeRoot.LocalFolder) {
        Icon(
            imageVector = FeatherIcons.HardDrive,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = decorationColor ?: MaterialTheme.colorScheme.primary
        )
        return
    }
    if (node.root is FileTreeRoot.RemoteServer) {
        Icon(
            imageVector = FeatherIcons.Server,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = decorationColor ?: MaterialTheme.colorScheme.primary
        )
        return
    }
    if (node.entry.isDirectory) {
        FileTreeIconVector(
            vector = FeatherIcons.Folder,
            decorationColor = decorationColor,
            tintFallback = MaterialTheme.colorScheme.primary
        )
        return
    }
    // 本地图片节点用小缩略图占用同一个 20dp 图标位；远程工作区 / 非图片 / 超大图 / 解码失败
    // 一律在 FileTreeThumbnail 内部回退类型图标，不发起网络、不拖慢列表。
    // 被弱化着色的条目（.gitignore 命中 / dotfile）不启用缩略图，保留弱化色语义。
    if (decorationColor == null && shouldThumbnail(node.entry)) {
        FileTreeThumbnail(
            entry = node.entry,
            modifier = Modifier.size(20.dp)
        )
        return
    }
    FileTreeIconVector(
        vector = fileTypeIconFor(node.entry.name),
        decorationColor = decorationColor,
        tintFallback = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 文件树类型图标的统一着色出口：优先弱化色，否则按目录 / 文件语义取色。保持图标位 20dp。 */
@Composable
private fun FileTreeIconVector(vector: ImageVector, decorationColor: Color?, tintFallback: Color) {
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = decorationColor ?: tintFallback
    )
}

/** .gitignore 命中条目的弱化色（橙，类 VSCode）。 */
private val FileTreeIgnoredColor = Color(0xFFCC8844)

/** 文件树一行的显示名：根节点用根标签（工作区根 `workspace` / 当前容器名），其余用条目名。 */
private val FileTreeNode.displayName: String get() = displayNameOverride ?: root?.label ?: entry.name

/** 插件会话合成一个不落库的 [ChatSession]，仅供 [ChatSessionRow] 渲染（标题/时间）。 */
private fun PluginSessionSummary.asChatSession(): ChatSession = PluginSessionUiMapper.toChatSession(this)

/** 文件树一行除缩进与文本外的固定宽度开销（start 8 + 箭头 16 + 4 + 图标 20 + 8 + end 12），供预算整树统一行宽。 */
private val FILE_TREE_ROW_OVERHEAD = 68.dp


/**
 * 会话行长按弹出的功能菜单：置顶 / 重命名 / 导出 / 删除。底部 sheet 样式参照 git 分支的 RefActionSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionSheet(
    session: ChatSession,
    pluginEntries: List<PluginUiEntry>,
    languageTag: String,
    onOpenPage: (PluginPage, String) -> Unit,
    onRunAction: (PluginUiEntry, String) -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onBatchSelect: () -> Unit,
    onDelete: () -> Unit,
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
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            SheetActionRow(
                icon = Icons.Outlined.PushPin,
                label = stringResource(if (session.isPinned) R.string.chat_unpin_session else R.string.chat_pin_session),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onTogglePin()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.Edit2,
                label = stringResource(R.string.common_rename),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.Download,
                label = stringResource(R.string.chat_export_session),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onExport()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.CheckSquare,
                label = stringResource(R.string.chat_batch_select),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onBatchSelect()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.Trash2,
                label = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
            // 会话菜单的插件入口（chat.session.menu）：把当前会话 id 交给插件页 / handler。
            if (pluginEntries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.plugins_slot_welcome_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.md, bottom = Spacing.sm)
                )
                pluginEntries.forEach { entry ->
                    SheetActionRow(
                        icon = pluginPageIcon(entry.icon),
                        label = entry.title.resolve(languageTag),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            onDismiss()
                            val page = entry.page
                            if (page != null) onOpenPage(page, session.id)
                            else onRunAction(entry, session.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
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

/** 文件「属性」抽屉：类型 / 大小 / 位置 / 修改时间 / 权限。文件夹大小由父层在 IO 后台递归统计后回填。
 *  与具体树节点解耦：文件树长按菜单与全屏查看器菜单共用同一套展示。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FilePropertiesSheet(
    entry: FileEntry,
    path: String,
    displayName: String,
    onDismiss: () -> Unit,
    onLoadDirectoryInfo: (String, (DirectoryInfo?) -> Unit) -> Unit
) {
    var dirInfo by remember(path) { mutableStateOf<DirectoryInfo?>(null) }
    var dirLoaded by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (entry.isDirectory) {
            onLoadDirectoryInfo(path) { info ->
                dirInfo = info
                dirLoaded = true
            }
        }
    }
    val sizeText = if (entry.isDirectory) {
        val info = dirInfo
        when {
            info != null -> stringResource(
                R.string.file_properties_folder_size,
                info.fileCount,
                formatFileSize(info.totalSize)
            )
            dirLoaded -> stringResource(R.string.file_properties_unknown)
            else -> stringResource(R.string.file_properties_size_calculating)
        }
    } else {
        formatFileSize(entry.size)
    }
    val typeText = if (entry.isDirectory) {
        stringResource(R.string.file_properties_type_directory)
    } else {
        val name = entry.name
        // 纯文本类扩展名刻意不收进 EXT_MIME_TYPES，这里按属性「类型」语义归成 text/plain。
        if (name.substringAfterLast('.') in TEXT_EXTS) "text/plain"
        else extToMimeType(name) ?: "application/octet-stream"
    }
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            PropertyRow(
                label = stringResource(R.string.file_properties_type),
                value = typeText
            )
            PropertyRow(
                label = stringResource(R.string.file_properties_size),
                value = sizeText
            )
            PropertyRow(
                label = stringResource(R.string.file_properties_location),
                value = path
            )
            val modifiedPattern = stringResource(R.string.file_properties_modified_format)
            // 格式化器只依赖 lastModified 与格式字符串，重组不重复 new SimpleDateFormat。
            val modifiedText = remember(entry.lastModified, modifiedPattern) {
                SimpleDateFormat(modifiedPattern, Locale.getDefault()).format(Date(entry.lastModified))
            }
            PropertyRow(
                label = stringResource(R.string.file_properties_modified),
                value = modifiedText
            )
            if (entry.permissions.isNotBlank()) {
                PropertyRow(
                    label = stringResource(R.string.file_properties_permissions),
                    value = entry.permissions
                )
            }
        }
    }
}

/** 属性抽屉的一行：label 左侧弱化色、value 右侧常规色；label 列宽为密度缩放的基准 96dp（结构性最小宽度）。 */
@Composable
private fun PropertyRow(label: String, value: String) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(t.scale(96))
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 文件大小展示：GB/MB/KB 统一保留 1 位小数，B 原样。属性抽屉专用口径（不与附件/镜像下载的 formatBytes 合并）。 */
private fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/** 目录属性统计：总大小（字节）与文件个数。文件夹「大小」由持有数据层的父层在 IO 后台统计后回填。 */
data class DirectoryInfo(val totalSize: Long, val fileCount: Int)

/** 递归统计目录总大小与文件个数（含全部子目录）。[FileAccessProvider.listFiles] 同步阻塞，调用方须在 IO 线程执行。
 * 深度上限 [MAX_DIRECTORY_INFO_DEPTH] 防符号链接成环导致无限递归；失败返回 null（展示层回退「未知」）。 */
internal fun loadDirectoryInfoSync(fileAccess: FileAccessProvider, path: String): DirectoryInfo? =
    runCatching {
        var totalSize = 0L
        var fileCount = 0
        fun walk(dir: String, depth: Int) {
            if (depth >= MAX_DIRECTORY_INFO_DEPTH) return
            for (entry in fileAccess.listFiles(dir)) {
                val child = if (dir.endsWith("/")) dir + entry.name else "$dir/${entry.name}"
                if (entry.isDirectory) walk(child, depth + 1) else {
                    totalSize += entry.size
                    fileCount++
                }
            }
        }
        walk(path, 0)
        DirectoryInfo(totalSize, fileCount)
    }.getOrNull()

/** 目录属性递归统计的深度上限：足够覆盖实际工程深度，同时防止符号链接成环导致的无限递归。 */
private const val MAX_DIRECTORY_INFO_DEPTH = 64

/** 侧边栏会话分组：同一时间组（今天 / 昨天 / 7天内 / 30天内 / 月份）内的会话，按最后回复时间降序。 */
internal data class SessionGroup(
    val groupKey: String,
    val sessions: List<ChatSession>
)

/**
 * 按最后回复时间（updatedAt）降序的会话列表分组：今天 / 昨天 / 7天内 / 30天内 / 更早按月。
 */
internal fun buildSessionGroups(sessions: List<ChatSession>, now: Long): List<SessionGroup> {
    val groups = mutableListOf<SessionGroup>()
    for (session in sessions) {
        val groupKey = sessionGroupKey(session.updatedAt, now)
        val lastIndex = groups.lastIndex
        if (lastIndex >= 0 && groups[lastIndex].groupKey == groupKey) {
            groups[lastIndex] = groups[lastIndex].copy(sessions = groups[lastIndex].sessions + session)
        } else {
            groups += SessionGroup(groupKey, listOf(session))
        }
    }
    return groups
}

/** 返回会话所属分组 key；月份分组为 ISO 年月（如 2026-05），其余为固定字面量。 */
internal fun sessionGroupKey(updatedAt: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days <= 7L -> "7d"
        days <= 30L -> "30d"
        else -> YearMonth.from(day).toString()
    }
}

@Composable
private fun sessionGroupLabel(groupKey: String, anchorSession: ChatSession): String = when (groupKey) {
    "pinned" -> stringResource(R.string.session_group_pinned)
    "today" -> stringResource(R.string.session_group_today)
    "yesterday" -> stringResource(R.string.session_group_yesterday)
    "7d" -> stringResource(R.string.session_group_last_7_days)
    "30d" -> stringResource(R.string.session_group_last_30_days)
    else -> SimpleDateFormat(
        stringResource(R.string.session_group_month_format),
        Locale.getDefault()
    ).format(Date(anchorSession.updatedAt))
}

/**
 * 侧栏底部角色入口（issue #271）：头像 + 角色名 + 绑定工作区，点击打开角色切换抽屉。
 * 只负责展示与点击，角色解析与切换仍复用顶栏原有的 RoleSheet。
 */
@Composable
private fun DrawerRoleRow(
    role: Role?,
    onClick: () -> Unit
) {
    val workspaceName = role?.workspaceId.orEmpty()
    SettingsRow(
        title = role?.name ?: stringResource(R.string.chat_role_default),
        subtitle = if (workspaceName.isBlank()) {
            stringResource(R.string.chat_role_workspace_follow)
        } else {
            stringResource(R.string.chat_role_workspace_bound, workspaceName)
        },
        onClick = onClick,
        leading = {
            RoleAvatar(role = role, size = 20.dp)
        }
    )
}
