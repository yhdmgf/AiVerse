package com.aiverse.feature.settings.presentation

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.feature.agent.data.local.dao.AgentMessageDao
import com.aiverse.feature.agent.data.local.dao.ChatSessionDao
import com.aiverse.feature.agent.data.local.entity.AgentMessageEntity
import com.aiverse.feature.agent.domain.checkpoint.CheckpointManager
import com.aiverse.feature.agent.domain.model.ChatSession
import com.aiverse.feature.agent.domain.session.SessionUseCase
import com.aiverse.feature.agent.presentation.AgentUIMessage
import com.aiverse.feature.backup.domain.BackupManager
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.model.Workspace
import com.aiverse.feature.workspace.domain.model.WorkspaceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/** 会话列表的作用域筛选：全部 / 仅当前环境 / 仅远程与离线。 */
enum class SessionScopeFilter { ALL, CURRENT_ENV, REMOTE_OR_OFFLINE }

/**
 * 会话分桶的来源分类。
 * [isCurrentBackend] 表示属于当前 backend 的已知工作区；[isRemoteLike] 表示远程或离线（非当前本地在线）桶。
 */
enum class WorkspaceBucketSource {
    CURRENT_INTERNAL,
    CURRENT_EXTERNAL,
    CURRENT_REMOTE,
    LOCAL_OFFLINE,
    EXTERNAL_OFFLINE,
    REMOTE_OR_OTHER;

    /** 是否属于当前 backend 的已知工作区（前三个枚举）。 */
    val isCurrentBackend: Boolean
        get() = this == CURRENT_INTERNAL || this == CURRENT_EXTERNAL || this == CURRENT_REMOTE

    /** 是否远程类（当前远程 + 各类离线/其它），用于作用域筛选。 */
    val isRemoteLike: Boolean
        get() = this == CURRENT_REMOTE || this == LOCAL_OFFLINE || this == EXTERNAL_OFFLINE || this == REMOTE_OR_OTHER
}

/** 会话管理页的一个工作区分桶：同属一个工作区路径的根会话集合。 */
@Immutable
data class WorkspaceBucket(
    val path: String,
    val name: String,
    val source: WorkspaceBucketSource,
    val workspace: Workspace?,
    val isCurrent: Boolean,
    val isAvailable: Boolean,
    val sessions: List<ChatSession>
)

/** 全局聊天记录搜索的一条命中。 */
@Immutable
data class SessionMessageSearchHit(
    val messageId: String,
    val sessionId: String,
    val sessionTitle: String,
    val workspacePath: String,
    val workspaceName: String,
    val snippet: String,
    val timestamp: Long
)

/** 会话详情面板的状态：会话本体 + 所属分桶 + 消息 + 子会话 + 加载标记。 */
@Immutable
data class SessionDetailState(
    val session: ChatSession?,
    val bucket: WorkspaceBucket?,
    val messages: List<AgentUIMessage>,
    val subSessions: List<ChatSession>,
    val loading: Boolean
)

/**
 * 会话管理二级页的 ViewModel。
 *
 * 只负责数据编排（分桶、筛选、搜索、详情、增删改迁移导出），所有用户可见文案由 UI 层用
 * stringResource 生成，本类只通过回调返回操作结果（成功/失败），不持有任何文案。
 */
@HiltViewModel
class SessionManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionUseCase: SessionUseCase,
    private val chatSessionDao: ChatSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val workspaceRepository: WorkspaceRepository,
    private val backupManager: BackupManager,
    private val checkpointManager: CheckpointManager
) : ViewModel() {

    companion object {
        /** 全局消息搜索的上限条数。 */
        private const val SEARCH_LIMIT = 80
        /** 搜索关键词输入的防抖时长（毫秒）。 */
        private const val SEARCH_DEBOUNCE_MS = 220L
        /** 搜索命中片段的上下文字符数（命中位置左右各取的长度）。 */
        private const val SNIPPET_CONTEXT = 32
    }

    // ---- 筛选与搜索输入 ----

    private val _scopeFilter = MutableStateFlow(SessionScopeFilter.ALL)
    val scopeFilter: StateFlow<SessionScopeFilter> = _scopeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 详情面板当前打开的会话 id；null 表示关闭。 */
    private val _selectedSessionId = MutableStateFlow<String?>(null)

    /** 详情内容版本号：会话被编辑（重命名/置顶/删除）后 +1，驱动详情流重算。 */
    private val detailVersion = MutableStateFlow(0)

    // ---- 分桶 ----

    /**
     * 全部分桶 = 当前 backend 的每个工作区一个桶 + 不属于已知工作区的离线桶。
     * 依赖三个流：全部工作区、当前工作区、全部会话（跨工作区）。
     */
    val allBuckets: StateFlow<List<WorkspaceBucket>> =
        combine(
            workspaceRepository.workspaces,
            workspaceRepository.current,
            chatSessionDao.getAllSessionsFlow()
        ) { workspaces, current, allSessions ->
            buildWorkspaceBuckets(
                workspaces = workspaces,
                currentWorkspace = current,
                allSessions = allSessions.map { it.toDomain() },
                projectsRoot = File(context.filesDir, "projects")
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按作用域 + 关键词（会话标题）过滤后的分桶；空桶不展示。 */
    val filteredBuckets: StateFlow<List<WorkspaceBucket>> =
        combine(allBuckets, _scopeFilter, _searchQuery) { buckets, scope, query ->
            val q = query.trim()
            buckets.mapNotNull { bucket ->
                val bucketInScope = when (scope) {
                    SessionScopeFilter.ALL -> true
                    SessionScopeFilter.CURRENT_ENV -> bucket.source.isCurrentBackend
                    SessionScopeFilter.REMOTE_OR_OFFLINE -> bucket.source.isRemoteLike
                }
                if (!bucketInScope) return@mapNotNull null
                val sessions = if (q.isEmpty()) bucket.sessions
                else bucket.sessions.filter { it.title.contains(q, ignoreCase = true) }
                if (sessions.isEmpty()) null
                else bucket.copy(sessions = sessions)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 各会话的消息条数（跨工作区，排除已压缩/摘要行）。 */
    val messageCounts: StateFlow<Map<String, Int>> =
        agentMessageDao.getAllSessionMessageCountsFlow()
            .map { rows -> rows.associate { it.sessionId to it.messageCount } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 全局聊天记录搜索命中：220ms 防抖 + 跳全部工作区检索。 */
    @OptIn(FlowPreview::class)
    val messageSearchHits: StateFlow<List<SessionMessageSearchHit>> =
        combine(_searchQuery, workspaceRepository.workspaces, _scopeFilter) { query, workspaces, scope ->
            Triple(query, workspaces, scope)
        }.debounce(SEARCH_DEBOUNCE_MS).map { (query, workspaces, scope) ->
            val q = query.trim()
            if (q.isEmpty()) return@map emptyList()
            val escaped = escapeLike(q)
            // 先按作用域算出可见的工作区路径集合，命中结果据此过滤（ALL 则不过滤）
            val scopePaths: Set<String>? = when (scope) {
                SessionScopeFilter.ALL -> null
                SessionScopeFilter.CURRENT_ENV -> workspaces.map { it.path }.toSet()
                SessionScopeFilter.REMOTE_OR_OFFLINE -> null // 远程/离线桶路径未知，命中侧再判断
            }
            runCatching { agentMessageDao.searchAllWorkspaces(escaped, SEARCH_LIMIT) }
                .getOrDefault(emptyList())
                .filter { m -> scopePaths == null || m.workspacePath in scopePaths }
                .map { m ->
                    SessionMessageSearchHit(
                        messageId = m.messageId,
                        sessionId = m.sessionId,
                        sessionTitle = m.sessionTitle,
                        workspacePath = m.workspacePath,
                        workspaceName = resolveWorkspaceName(m.workspacePath, workspaces),
                        snippet = buildSnippet(m.content, q),
                        timestamp = m.timestamp
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 会话详情面板状态：由选中会话 id + 详情版本号驱动。 */
    val detailState: StateFlow<SessionDetailState> =
        combine(_selectedSessionId, detailVersion, allBuckets) { id, _, buckets ->
            if (id == null) {
                SessionDetailState(null, null, emptyList(), emptyList(), loading = false)
            } else {
                val entity = sessionUseCase.getSessionById(id)
                val session = entity?.toDomain()
                val bucket = session?.let { s -> buckets.firstOrNull { it.path == s.workspacePath } }
                // 详情只展示有效对话行：排除已压缩、上下文摘要与压缩标记
                val messages = agentMessageDao.getMessagesBySessionOnce(id)
                    .filter { !it.isCompacted && !it.isContextSummary && !it.isCompactionMarker }
                    .map { it.toUIMessage() }
                val subSessions = chatSessionDao.getSubSessionsByParentOnce(id).map { it.toDomain() }
                SessionDetailState(session, bucket, messages, subSessions, loading = false)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionDetailState(null, null, emptyList(), emptyList(), loading = false)
        )

    // ---- 输入动作 ----

    fun setScopeFilter(filter: SessionScopeFilter) {
        _scopeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openSessionDetail(id: String) {
        _selectedSessionId.value = id
        detailVersion.value++
    }

    fun closeDetail() {
        _selectedSessionId.value = null
    }

    // ---- 会话操作 ----

    /** 重命名会话；标题是否合法由 UI 层校验，这里直接落库并刷新详情。 */
    fun renameSession(id: String, newTitle: String) {
        viewModelScope.launch {
            runCatching { sessionUseCase.updateTitle(id, newTitle.trim()) }
            detailVersion.value++
        }
    }

    /** 切换置顶。 */
    fun togglePinSession(session: ChatSession) {
        viewModelScope.launch {
            runCatching { sessionUseCase.updatePinned(session.id, !session.isPinned) }
            detailVersion.value++
        }
    }

    /**
     * 批量删除会话（含各自子会话与消息），删除前清理各会话的代码检查点。
     * @param onDone 删除完成后的回调（UI 层据此提示并收起选中态）
     */
    fun deleteSessions(ids: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            ids.forEach { id ->
                runCatching { checkpointManager.clearSessionCheckpoints(id) }
                runCatching { sessionUseCase.deleteSession(id) }
            }
            if (_selectedSessionId.value in ids) _selectedSessionId.value = null
            detailVersion.value++
            onDone()
        }
    }

    /**
     * 把一批会话迁移到目标工作区分桶。
     * @param onResult 迁移是否成功（目标为当前在线工作区时不迁移并回调 false）
     */
    fun moveSessions(ids: List<String>, targetBucket: WorkspaceBucket, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // 当前 backend 的在线工作区是会话的「运行环境」，跨环境迁移会破坏路径绑定，一律拒绝
            if (targetBucket.source.isCurrentBackend) {
                onResult(false)
                return@launch
            }
            val result = runCatching {
                sessionUseCase.moveSessionsToWorkspace(ids, targetBucket.path)
            }.isSuccess
            detailVersion.value++
            onResult(result)
        }
    }

    /**
     * 导出单个会话为无密码 tar.gz。
     * @param onResult 导出是否成功（流由 UI 层打开与关闭）
     */
    fun exportSession(id: String, output: OutputStream, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { backupManager.exportSession(id, output) }.isSuccess
            onResult(ok)
        }
    }

    /**
     * 判断把 [sourcePaths]（各会话当前工作区路径）迁到 [targetBucket] 是否属于跨环境迁移。
     * 目标是离线/远程/其它桶，或源路径与目标路径不一致，均视为跨环境。
     */
    fun isCrossEnvironmentMove(sourcePaths: Set<String>, targetBucket: WorkspaceBucket): Boolean {
        if (!targetBucket.source.isCurrentBackend) return true
        return sourcePaths.any { it != targetBucket.path }
    }

    // ---- 内部工具 ----

    /** LIKE 通配符转义：与 DAO 侧 ESCAPE '!' 约定配套。 */
    private fun escapeLike(raw: String): String =
        raw.replace("!", "!!").replace("%", "!%").replace("_", "!_")

    /** 由工作区路径解析展示名：优先别名，否则取路径末段。 */
    private fun resolveWorkspaceName(path: String, workspaces: List<Workspace>): String {
        workspaces.firstOrNull { it.path == path }?.let { return it.displayLabel }
        return path.trimEnd('/').substringAfterLast('/').ifBlank { path }
    }

    /**
     * 生成搜索命中片段：定位首个命中位置，向前后各取 [SNIPPET_CONTEXT] 字符，越界加省略号。
     * 找不到命中（理论上不会）时退回首尾截断。
     */
    private fun buildSnippet(content: String, query: String): String {
        val plain = content.replace('\n', ' ').trim()
        if (plain.isEmpty()) return ""
        val idx = plain.indexOf(query, ignoreCase = true)
        if (idx < 0) return plain.take(SNIPPET_CONTEXT * 2)
        val start = (idx - SNIPPET_CONTEXT).coerceAtLeast(0)
        val end = (idx + query.length + SNIPPET_CONTEXT).coerceAtMost(plain.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < plain.length) "…" else ""
        return prefix + plain.substring(start, end) + suffix
    }
}

/**
 * 把全部根会话按工作区分桶。
 *
 * - 当前 backend 的每个已知工作区一个桶：INTERNAL→CURRENT_INTERNAL，EXTERNAL_LOCAL→CURRENT_EXTERNAL，REMOTE→CURRENT_REMOTE；
 * - 不属于任何已知工作区的会话归入离线桶，按路径归为本地/外部/远程离线；
 * - 当前桶排序：isCurrent↓ → isAvailable↓ → name↑；离线桶按最新 updatedAt 降序排在最后。
 *
 * 只统计根会话（parentId == null），子会话随详情面板展示。
 */
internal fun buildWorkspaceBuckets(
    workspaces: List<Workspace>,
    currentWorkspace: Workspace?,
    allSessions: List<ChatSession>,
    projectsRoot: File
): List<WorkspaceBucket> {
    val currentPath = currentWorkspace?.path
    val knownPaths = workspaces.map { it.path }.toSet()
    val roots = allSessions.filter { it.parentId == null }

    fun sourceOfType(type: WorkspaceType): WorkspaceBucketSource = when (type) {
        WorkspaceType.INTERNAL -> WorkspaceBucketSource.CURRENT_INTERNAL
        WorkspaceType.EXTERNAL_LOCAL -> WorkspaceBucketSource.CURRENT_EXTERNAL
        WorkspaceType.REMOTE -> WorkspaceBucketSource.CURRENT_REMOTE
    }

    // 当前 backend 各工作区的桶
    val currentBuckets = workspaces.map { ws ->
        WorkspaceBucket(
            path = ws.path,
            name = ws.displayLabel,
            source = sourceOfType(ws.type),
            workspace = ws,
            isCurrent = ws.path == currentPath,
            isAvailable = ws.available,
            sessions = roots.filter { it.workspacePath == ws.path }
                .sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })
        )
    }.sortedWith(
        compareByDescending<WorkspaceBucket> { it.isCurrent }
            .thenByDescending { it.isAvailable }
            .thenBy { it.name }
    )

    // 离线桶：会话的工作区路径不在已知工作区里
    val offlineGroups = roots.filter { it.workspacePath !in knownPaths }
        .groupBy { it.workspacePath }
    val offlineBuckets = offlineGroups.map { (path, sessions) ->
        val source = classifyOfflineWorkspacePath(path, projectsRoot)
        WorkspaceBucket(
            path = path,
            name = path.trimEnd('/').substringAfterLast('/').ifBlank { path },
            source = source,
            workspace = null,
            isCurrent = false,
            isAvailable = source == WorkspaceBucketSource.LOCAL_OFFLINE &&
                File(projectsRoot, path.trimEnd('/').substringAfterLast('/')).exists(),
            sessions = sessions.sortedByDescending { it.updatedAt }
        )
    }.sortedByDescending { bucket -> bucket.sessions.maxOf { it.updatedAt } }

    return currentBuckets + offlineBuckets
}

/**
 * 把离线工作区路径归类为 LOCAL_OFFLINE / EXTERNAL_OFFLINE / REMOTE_OR_OTHER。
 *
 * - 命中内部项目根（filesDir/projects）→ 本地离线（App 私有目录里的历史工作区）；
 * - 命中外部存储根（/storage/emulated 等）→ 外部本地离线（被移动/删除的用户目录）；
 * - 其余（含 SSH 远程路径）→ 远程或其它。
 */
internal fun classifyOfflineWorkspacePath(path: String, projectsRoot: File): WorkspaceBucketSource {
    val normalized = path.trimEnd('/')
    val projectsRootPath = projectsRoot.absolutePath.trimEnd('/')
    if (normalized == projectsRootPath || normalized.startsWith("$projectsRootPath/")) {
        return WorkspaceBucketSource.LOCAL_OFFLINE
    }
    if (normalized.startsWith("/storage/") || normalized.startsWith("/sdcard/") ||
        normalized.startsWith("/mnt/")
    ) {
        return WorkspaceBucketSource.EXTERNAL_OFFLINE
    }
    return WorkspaceBucketSource.REMOTE_OR_OTHER
}
