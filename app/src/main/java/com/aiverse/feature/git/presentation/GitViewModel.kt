package com.aiverse.feature.git.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.core.util.LineDiff
import com.aiverse.feature.agent.domain.workflow.AgentWorkflow
import com.aiverse.feature.git.domain.GitCommandFailureException
import com.aiverse.feature.git.domain.GitOutputTooLargeException
import com.aiverse.feature.git.domain.GitErrorMessage
import com.aiverse.feature.git.domain.GitHubIssuesRepository
import com.aiverse.feature.git.domain.GitHubPullRequestsRepository
import com.aiverse.feature.git.domain.GitRepository
import com.aiverse.feature.git.domain.PatchDiffParser
import com.aiverse.feature.git.domain.IssuePlatform
import com.aiverse.feature.git.domain.parseGithubRepoUrl
import com.aiverse.feature.git.domain.parseIssueRepo
import com.aiverse.feature.git.domain.model.GitBranch
import com.aiverse.feature.git.domain.model.GitCommit
import com.aiverse.feature.git.domain.model.GitFileChange
import com.aiverse.feature.git.domain.model.GitGraph
import com.aiverse.feature.git.domain.model.GitHubCheckSummary
import com.aiverse.feature.git.domain.model.GitHubCommitFile
import com.aiverse.feature.git.domain.model.GitHubComment
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.git.domain.model.GitHubPullRequest
import com.aiverse.feature.git.domain.model.GitHubPullRequestCommit
import com.aiverse.feature.git.domain.model.GitHubPullRequestFile
import com.aiverse.feature.git.domain.model.GitStash
import com.aiverse.feature.git.domain.model.GitStatus
import com.aiverse.feature.git.domain.model.GitTab
import com.aiverse.feature.git.domain.model.GitTag
import com.aiverse.feature.git.presentation.component.DiffData
import com.aiverse.feature.git.presentation.component.DiffRow
import com.aiverse.feature.git.presentation.component.highlightCode
import com.aiverse.feature.git.presentation.component.inferSyntaxLanguage
import com.aiverse.feature.settings.data.repository.AppThemeMode
import com.aiverse.feature.settings.data.repository.ThemeSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Git 页的 UI 状态与操作。
 *
 * 进入页面即 [refresh] 并发拉取 status/graph/remote，随后异步拉取全量分支/标签；每个写操作
 * （暂存/提交/拉取/推送）执行后自动刷新并通过 [GitUiState.toast] 反馈。[GitUiState.busy]
 * 守卫防止并发写操作相互踩踏。
 */
private fun GitGraph.toGitCommits() =
    commits.map { GitCommit(it.hash, it.shortHash, it.author, it.date, it.message, coAuthors = it.coAuthors) }

@HiltViewModel
class GitViewModel @Inject constructor(
    private val repository: GitRepository,
    private val issuesRepository: GitHubIssuesRepository,
    private val pullRequestsRepository: GitHubPullRequestsRepository,
    @param:ApplicationContext private val context: Context,
    private val themeSettings: ThemeSettingsRepository,
    private val agentWorkflow: AgentWorkflow
) : ViewModel() {

    private companion object {
        const val TAG = "GitViewModel"
        /** diff 行数上限：超过则跳过 LCS，避免移动端 O(n·m) 内存压力。与 FileTools 对齐。 */
        const val MAX_DIFF_LINES = 2000
        /** 单行长度上限：超长单行（如压缩的 JSON）渲染会撑爆 Compose Constraints，直接降级提示。 */
        const val MAX_DIFF_LINE_LENGTH = 2000
        /** diff 两侧内容的来源标记，只进 [DiffData.oldRef]/[DiffData.newRef] 供排查，不展示给用户。 */
        const val REF_WORKTREE = "worktree"
        const val REF_INDEX = "index"
        const val REF_EMPTY = "empty"
        /** 列表页只为前 N 条 PR 拉 CI 状态，避免一次刷新打满 GitHub API 配额。 */
        const val PR_CHECKS_ON_LIST = 20
        const val PR_CHECKS_CONCURRENCY = 4
        /** 详情页 CI 轮询间隔：有检查仍进行中时按此周期复查，避免 job 结束后界面停在「进行中」。 */
        const val PR_CHECKS_POLL_INTERVAL_MS = 12_000L
        /** 详情页 CI 轮询最大次数：超过即停，防止长期 pending 的 job 无限轮询打满配额。 */
        const val PR_CHECKS_POLL_MAX_ATTEMPTS = 6
    }

    /** 当前工作区是否位于宿主外部存储；该文件系统与 git 写对象不兼容，错误提示需引导迁移。 */
    private fun onExternalStorage(): Boolean =
        repository.workspaceRoot().startsWith("/storage/emulated/")

    data class GitUiState(
        val loading: Boolean = true,
        val notARepo: Boolean = false,
        val status: GitStatus? = null,
        val branches: List<GitBranch> = emptyList(),
        val commits: List<GitCommit> = emptyList(),
        /** 拓扑图视图：提交（含父哈希）+ 引用 + 泳道布局，供 LogTab 绘制彩色分支连线。 */
        val graph: GitGraph = GitGraph.EMPTY,
        val tags: List<GitTag> = emptyList(),
        val tab: GitTab = GitTab.STATUS,
        val busy: Boolean = false,
        val toast: String? = null,
        /** 是否已配置远程仓库，控制拉取/推送按钮可用性。 */
        val hasRemote: Boolean = false,
        /** 是否已配置全局署名 user.name（git config --global），控制提交按钮可用性；无署名提交会成为失败提交。 */
        val hasIdentity: Boolean = false,
        /** 工作区里发现的 git 仓库路径列表（根 + 一层子目录）。 */
        val repos: List<String> = emptyList(),
        /** 当前选中的 git 仓库路径；null 表示未选中（工作区根 + 子目录都非仓库）。 */
        val currentRepo: String? = null,
        /** 工作区根的一层直接子目录（仓库选择弹窗展示扫描范围）。 */
        val subdirs: List<String> = emptyList(),
        /** 当前工作区根路径（仓库选择弹窗的基准目录）。 */
        val workspaceRoot: String = "",
        /** 是否显示仓库选择弹窗。 */
        val repoPickerVisible: Boolean = false,
        /** 当前在详情弹层中查看的提交 hash；null 表示未打开。 */
        val commitDetailHash: String? = null,
        /** 已懒加载的提交文件清单，按 hash 缓存。 */
        val commitFiles: Map<String, List<GitFileChange>> = emptyMap(),
        /** 正在加载文件清单的提交 hash。 */
        val loadingCommit: String? = null,
        /** 正在分页加载更旧的提交（滚动到底触发）。不置 busy，避免阻塞写操作。 */
        val graphLoadingMore: Boolean = false,
        /** 全量分支/标签是否已加载（进入页面即后台拉取，不阻塞首屏；切 BRANCHES tab 时未完成则继续等）。 */
        val branchesLoaded: Boolean = false,
        /** 正在加载全量分支/标签。 */
        val branchesLoading: Boolean = false,
        /** 储藏列表。 */
        val stashes: List<GitStash> = emptyList(),
        /** 正在加载储藏列表。 */
        val stashesLoading: Boolean = false,
        /** 正在切换分支。 */
        val checkoutLoading: String? = null,
        /** 已展开的未跟踪目录 → 其下未跟踪文件清单。git status 把新目录折叠成一行，展开时按需查询。 */
        val untrackedDirFiles: Map<String, List<String>> = emptyMap(),
        /** 正在展开的未跟踪目录路径。 */
        val untrackedDirLoading: String? = null,
        /** 正在生成提交信息。 */
        val generatingCommitMessage: Boolean = false,
        /** 是否显示 diff 全屏页；进入即置 true，diffData 为 null 时页内显示加载中。 */
        val diffVisible: Boolean = false,
        /** 正在查看 diff 的文件路径；加载中（diffData 为 null）时供顶栏显示文件名。 */
        val diffPath: String? = null,
        /** diff 视图数据；非 null 时 diff 页渲染内容。 */
        val diffData: DiffData? = null,
        /** GitHub issue 列表。 */
        val issues: List<GitHubIssue> = emptyList(),
        /** 是否正在加载 issue 列表。 */
        val issuesLoading: Boolean = false,
        /** issue 列表是否已加载（成功或失败都置 true，控制懒加载只触发一次）。 */
        val issuesLoaded: Boolean = false,
        /** 当前 issue 过滤："open"/"closed"。 */
        val issuesFilter: String = "open",
        /** issue 列表加载失败的错误信息。 */
        val issuesError: String? = null,
        /** 当前仓库 remote URL（用于识别 GitHub 与懒加载触发）。 */
        val remoteUrl: String = "",
        /** 当前 remote 是否为 GitHub（PR 相关功能仍只支持 GitHub）。 */
        val isGitHub: Boolean = false,
        /** 当前 remote 是否属于受支持的 issue 平台（GitHub / Gitee / GitCode）。 */
        val isIssuePlatform: Boolean = false,
        /** 是否已配置当前平台（GitHub / Gitee / GitCode）的访问令牌。 */
        val hasGitHubToken: Boolean = false,
        /** 正在查看详情的 issue 编号；null 表示未打开详情。 */
        val issueDetailNumber: String? = null,
        /** 正在查看的 issue 详情。 */
        val issueDetail: GitHubIssue? = null,
        /** 是否正在加载 issue 详情。 */
        val issueDetailLoading: Boolean = false,
        /** 详情页的评论列表。 */
        val issueComments: List<GitHubComment> = emptyList(),
        /** 是否正在加载评论列表。 */
        val issueCommentsLoading: Boolean = false,
        /** 仓库贡献者 login 列表（受理人候选）。 */
        val contributors: List<String> = emptyList(),
        /** 详情页是否正在执行写操作（关闭/重开/评论/标签/受理人/锁定）。 */
        val issueActionBusy: Boolean = false,
        /** 详情页写操作失败提示。 */
        val issueActionError: String? = null,
        /** issue 列表排序方式。 */
        val issuesSort: String = "newest",
        /** 是否处于多选模式。 */
        val issueSelectionMode: Boolean = false,
        /** 已选中的 issue 编号集合。 */
        val selectedIssueNumbers: Set<String> = emptySet(),
        /** 是否正在执行批量操作。 */
        val issueBulkBusy: Boolean = false,
        /** GitHub Pull Request 列表。 */
        val pulls: List<GitHubPullRequest> = emptyList(),
        /** 是否正在加载 PR 列表。 */
        val pullsLoading: Boolean = false,
        /** PR 列表是否已加载（成功或失败都置 true，控制懒加载只触发一次）。 */
        val pullsLoaded: Boolean = false,
        /** 当前 PR 过滤：open/closed/all。 */
        val pullsFilter: String = "open",
        /** PR 列表排序方式。 */
        val pullsSort: String = "newest",
        /** PR 列表加载失败的错误信息。 */
        val pullsError: String? = null,
        /** PR 列表的 CI 摘要，key = PR number；只为前若干条拉取。 */
        val pullChecks: Map<Long, GitHubCheckSummary> = emptyMap(),
        /** 正在查看详情的 PR 编号；null 表示未打开。 */
        val pullDetailNumber: Long? = null,
        /** 正在查看的 PR 详情。 */
        val pullDetail: GitHubPullRequest? = null,
        /** 是否正在加载 PR 详情。 */
        val pullDetailLoading: Boolean = false,
        /** 详情页的 CI 汇总。 */
        val pullDetailChecks: GitHubCheckSummary? = null,
        /** 是否正在加载详情页 CI。 */
        val pullDetailChecksLoading: Boolean = false,
        /** 详情页的评论列表。 */
        val pullComments: List<GitHubComment> = emptyList(),
        /** 是否正在加载详情页评论。 */
        val pullCommentsLoading: Boolean = false,
        /** 详情页是否正在执行写操作（评论/关闭/合并）。 */
        val pullActionBusy: Boolean = false,
        /** 详情页的提交记录。 */
        val pullCommits: List<GitHubPullRequestCommit> = emptyList(),
        /** 是否正在加载详情页提交记录。 */
        val pullCommitsLoading: Boolean = false,
        /** 详情页的文件变更列表。 */
        val pullFiles: List<GitHubPullRequestFile> = emptyList(),
        /** 是否正在加载详情页文件列表。 */
        val pullFilesLoading: Boolean = false,
        /** 详情页里点开查看的提交 sha；null 表示未打开提交弹层。 */
        val prCommitDetailSha: String? = null,
        /** 已懒加载的 PR 提交文件清单（含 patch 正文），按 sha 缓存。 */
        val prCommitFiles: Map<String, List<GitHubCommitFile>> = emptyMap(),
        /** 正在加载文件清单的提交 sha。 */
        val prCommitFilesLoading: String? = null,
        /** 新建 PR 弹层是否可见。 */
        val createPullVisible: Boolean = false,
        /** 新建 PR 是否正在提交。 */
        val createPullBusy: Boolean = false,
        /** 新建 PR 的失败原因（保留在弹层内展示）。 */
        val createPullError: String? = null
    )

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    init { refresh() }

    /** 仓库快照：并发拉取 status/graph/remote（+可选 identity）的结果。不含全量分支/标签——
     * 那些在 [refresh] 成功后异步拉取（[loadBranches]），避免阻塞首屏。
     * graph 的 refs 标注只用本地分支（[repository.localRefsOnly]，亚秒级），远程/标签标注由 [loadBranches] 补全。 */
    private data class RepoSnapshot(
        val status: GitStatus,
        val graph: GitGraph,
        val hasRemote: Boolean,
        val hasIdentity: Boolean = false,
        val untrackedDirFiles: Map<String, List<String>> = emptyMap(),
        val remoteUrl: String = "",
        val hasGitHubToken: Boolean = false,
        val issuePlatform: IssuePlatform? = null
    ) {
        val isGitHub: Boolean get() = parseGithubRepoUrl(remoteUrl) != null
    }

    /** 并发拉取轻量快照：status + graph(本地 refs) + remote + 可选 identity。 */
    private suspend fun loadSnapshot(includeIdentity: Boolean): RepoSnapshot = coroutineScope {
        val s = async { repository.status() }
        val localRefs = async { repository.localRefsOnly() }
        val r = async { repository.hasRemote() }
        val id = async { if (includeIdentity) repository.getUserName().isNotBlank() else false }
        val g = async { repository.graph(refs = localRefs.await()) }
        val remoteUrl = async { repository.getRepoUrl() }
        val hasToken = async { issuesRepository.hasGitHubToken() }
        val status = s.await()
        val remote = remoteUrl.await()
        RepoSnapshot(
            status, g.await(), r.await(), id.await(), reloadExpandedUntrackedDirs(status),
            remote, hasToken.await(), parseIssueRepo(remote)?.first
        )
    }

    /**
     * 刷新后重查已展开的未跟踪目录，保住展开态。只保留仍出现在 [GitStatus.untracked] 里的目录——
     * 目录被暂存或删除后就不该再留着陈旧的文件列表。
     */
    private suspend fun reloadExpandedUntrackedDirs(status: GitStatus): Map<String, List<String>> {
        val dirs = _state.value.untrackedDirFiles.keys.filter { it in status.untracked }
        if (dirs.isEmpty()) return emptyMap()
        return coroutineScope {
            dirs.map { dir -> dir to async { repository.untrackedFilesIn(dir) } }
                .associate { (dir, deferred) -> dir to deferred.await() }
        }
    }

    fun setTab(tab: GitTab) {
        _state.update { it.copy(tab = tab) }
        // 兜底：进入页面即已异步加载；若尚未开始/完成（如加载失败后重进），切 tab 时再补一次。
        if (tab == GitTab.BRANCHES && !_state.value.branchesLoaded && !_state.value.branchesLoading) {
            loadBranches()
        }
    }

    /**
     * 加载全量分支/标签（进入页面 [refresh] 成功后即触发，无需等切 BRANCHES tab）。
     * 一次 [repository.loadAllRefs] 拉取，填充 branches/tags 并用全量 refs 更新 graph 的标注。
     * 不置 busy，只读不阻塞写操作。已加载过则跳过；写操作后需手动调 [refreshBranchesIfLoaded] 同步。
     */
    fun loadBranches() {
        if (_state.value.branchesLoading || _state.value.branchesLoaded) return
        _state.update { it.copy(branchesLoading = true) }
        viewModelScope.launch {
            try {
                val allRefs = repository.loadAllRefs()
                // 用全量 refs 更新 graph 标注（远程分支+标签补全）。
                val graph = repository.graphAppend(_state.value.graph.commits, allRefs.refsByCommit)
                val commits = graph.toGitCommits()
                _state.update {
                    it.copy(branches = allRefs.branches, tags = allRefs.tags, graph = graph, commits = commits, branchesLoading = false, branchesLoaded = true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载分支列表失败", e)
                _state.update { it.copy(branchesLoading = false, toast = context.getString(R.string.git_toast_load_branches_failed, e.message)) }
            }
        }
    }

    /**
     * 写操作后同步刷新已加载的分支/标签（若已加载过）。未加载过则不主动拉，保持延迟加载语义。
     */
    private fun refreshBranchesIfLoaded() {
        if (!_state.value.branchesLoaded) return
        viewModelScope.launch {
            try {
                val allRefs = repository.loadAllRefs()
                _state.update { it.copy(branches = allRefs.branches, tags = allRefs.tags) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "刷新分支列表失败", e)
            }
        }
    }

    fun refresh() {
        if (_state.value.busy) return
        // 容器未就绪时不执行 git 命令，直接提示用户去终端页完成初始化，避免误显示"非 Git 仓库"。
        repository.notReadyHint()?.let { hint ->
            _state.update { it.copy(loading = false, toast = hint) }
            return
        }
        _state.update { it.copy(loading = true, toast = null) }
        viewModelScope.launch {
            try {
                // 发现工作区里的 git 仓库（根 + 一层子目录），选中当前或默认仓库。
                val repos = repository.discoverRepos()
                val subdirs = repository.listSubdirectories()
                val workspaceRoot = repository.workspaceRoot()
                val current = repository.currentRepoPath
                // 当前选中仓库已不在发现列表（如切换了工作区）时回退默认：工作区根优先，否则第一个子目录仓库。
                val effectiveRepo = current?.takeIf { it in repos } ?: repos.firstOrNull()
                repository.setRepoPath(effectiveRepo)
                if (effectiveRepo == null) {
                    _state.update { it.copy(loading = false, notARepo = true, repos = repos, subdirs = subdirs, workspaceRoot = workspaceRoot, currentRepo = null) }
                    return@launch
                }
                val snap = loadSnapshot(includeIdentity = true)
                val commits = snap.graph.toGitCommits()
                _state.update {
                    it.copy(loading = false, notARepo = false, status = snap.status, commits = commits, graph = snap.graph, hasRemote = snap.hasRemote, hasIdentity = snap.hasIdentity, untrackedDirFiles = snap.untrackedDirFiles, branchesLoaded = false, branchesLoading = false, branches = emptyList(), tags = emptyList(), repos = repos, subdirs = subdirs, workspaceRoot = workspaceRoot, currentRepo = effectiveRepo, issues = emptyList(), issuesLoading = false, issuesLoaded = false, issuesError = null, issueDetailNumber = null, issueDetail = null, issueDetailLoading = false, issueComments = emptyList(), issueCommentsLoading = false, contributors = emptyList(), issueActionBusy = false, issueActionError = null, issueSelectionMode = false, selectedIssueNumbers = emptySet(), issueBulkBusy = false, pulls = emptyList(), pullsLoading = false, pullsLoaded = false, pullsError = null, pullChecks = emptyMap(), pullDetailNumber = null, pullDetail = null, pullDetailLoading = false, pullDetailChecks = null, pullDetailChecksLoading = false, pullComments = emptyList(), pullCommentsLoading = false, pullActionBusy = false, pullCommits = emptyList(), pullCommitsLoading = false, pullFiles = emptyList(), pullFilesLoading = false, createPullVisible = false, createPullBusy = false, createPullError = null, remoteUrl = snap.remoteUrl, isGitHub = snap.isGitHub, isIssuePlatform = snap.issuePlatform != null, hasGitHubToken = snap.hasGitHubToken)
                }
                // 页面已打开：后台拉取全量分支/标签，用户切到 BRANCHES tab 时无需再等。
                loadBranches()
                // GitHub 仓库且有 token 时后台拉取 issue / PR 列表；切仓库/手动刷新都会重置已加载标记重新拉取。
                loadIssues()
                loadPulls()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "刷新失败", e)
                _state.update { it.copy(loading = false, toast = context.getString(R.string.git_toast_refresh_failed, e.message)) }
            }
        }
    }

    /**
     * 轻量重查 GitHub token 状态：配完密钥从凭证页返回时立即反映，避免 issue tab 仍误报「需要 token」。
     * [hasGitHubToken] 此前只在 [refresh] 里计算一次，而 refresh 仅在 init/手动刷新触发；
     * 用户切去凭证页配完 token 用返回键回到 Git 页时，ViewModel 实例被保留、init 不重跑，
     * state.hasGitHubToken 仍是进入时的 false。这里只重查 token 并更新状态（不重跑 git 快照），
     * 侵入最小；若 token 从无到有（刚配好密钥）且当前为 GitHub 仓库，顺带补拉 issue 列表。
     */
    fun refreshGitHubTokenState() {
        viewModelScope.launch {
            val hasToken = issuesRepository.hasGitHubToken()
            val wasWithoutToken = !_state.value.hasGitHubToken
            _state.update { it.copy(hasGitHubToken = hasToken) }
            if (hasToken && wasWithoutToken) {
                loadIssues()
                loadPulls()
            }
        }
    }

    /** 执行一个写操作：置 busy → 跑命令 → 刷新 → 反馈。操作间互斥。 */
    private fun runAction(
        @StringRes nameRes: Int,
        action: suspend () -> String,
        onComplete: (suspend () -> Unit)? = null
    ) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, toast = null) }
        viewModelScope.launch {
            val name = context.getString(nameRes)
            val msg = try {
                action()
                context.getString(R.string.git_toast_action_success, name)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "${name}失败", e)
                val reason = (e as? GitCommandFailureException)?.output ?: e.message
                context.getString(R.string.git_toast_action_failed, name, GitErrorMessage.friendly(reason ?: "", onExternalStorage = onExternalStorage()))
            }
            // 刷新以反映新状态；失败也刷新，让 UI 与仓库一致。
            try {
                if (repository.isRepo()) {
                    val snap = loadSnapshot(includeIdentity = false)
                    val commits = snap.graph.toGitCommits()
                    _state.update { it.copy(busy = false, status = snap.status, commits = commits, graph = snap.graph, hasRemote = snap.hasRemote, untrackedDirFiles = snap.untrackedDirFiles, notARepo = false, toast = msg) }
                    refreshBranchesIfLoaded()
                    onComplete?.invoke()
                } else {
                    _state.update { it.copy(busy = false, notARepo = true, toast = msg) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(busy = false, toast = context.getString(R.string.git_toast_action_refresh_failed, msg)) }
            }
        }
    }

    fun stage(path: String) = runAction(R.string.git_stage, { repository.stage(path) })
    fun unstage(path: String) = runAction(R.string.git_unstage, { repository.unstage(path) })
    fun stageAll() = runAction(R.string.git_action_stage_all, { repository.stageAll() })
    fun unstageAll() = runAction(R.string.git_action_unstage_all, { repository.unstageAll() })

    /**
     * 回退单个文件的改动。[staged] 为 true 时连已暂存内容一起还原到上次提交，否则只回退未暂存改动
     * （被删除的文件由此取回）。改动未提交过，回退后无法再找回，故 UI 侧须先二次确认。
     */
    fun revertFile(path: String, staged: Boolean) =
        runAction(R.string.git_action_revert, {
            if (staged) repository.revertToHead(path) else repository.revertWorktree(path)
        })

    /** 回退全部未暂存改动，已暂存内容保持不动。 */
    fun revertAllUnstaged() = runAction(R.string.git_action_revert_all, { repository.revertAllWorktree() })

    /** 删除未跟踪的文件或目录。这些文件从未提交过，删除后 git 无法恢复。 */
    fun deleteUntracked(path: String) =
        runAction(R.string.git_action_delete_file, { repository.deleteUntracked(path) })

    /**
     * 展开/收起一个折叠的未跟踪目录（`git status` 把新目录显示成 `dir/` 一行）。展开时按需查询目录下
     * 未跟踪文件，使其可单独查看差异或删除。不置 busy——只读查询不该阻塞写操作。
     */
    fun toggleUntrackedDir(dir: String) {
        if (dir in _state.value.untrackedDirFiles) {
            _state.update { it.copy(untrackedDirFiles = it.untrackedDirFiles - dir) }
            return
        }
        if (_state.value.untrackedDirLoading != null) return
        _state.update { it.copy(untrackedDirLoading = dir) }
        viewModelScope.launch {
            val files = try {
                repository.untrackedFilesIn(dir)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载未跟踪目录失败: $dir", e)
                emptyList()
            }
            _state.update {
                it.copy(untrackedDirFiles = it.untrackedDirFiles + (dir to files), untrackedDirLoading = null)
            }
        }
    }
    fun commit(message: String, amend: Boolean = false) =
        runAction(R.string.git_action_commit, { repository.commit(message, amend) })

    /**
     * 调用 AI 根据暂存差异生成提交信息。
     */
    fun generateCommitMessage(onResult: (String) -> Unit) {
        if (_state.value.generatingCommitMessage) return
        _state.update { it.copy(generatingCommitMessage = true) }
        viewModelScope.launch {
            try {
                val diff = repository.diffForCommitMessage()
                if (diff.isBlank()) {
                    _state.update { it.copy(generatingCommitMessage = false, toast = context.getString(R.string.git_toast_no_diff_for_commit)) }
                    return@launch
                }
                val message = agentWorkflow.generateCommitMessage(diff)
                _state.update { it.copy(generatingCommitMessage = false) }
                if (!message.isNullOrBlank()) {
                    onResult(message)
                } else {
                    _state.update { it.copy(toast = context.getString(R.string.git_toast_generate_commit_failed)) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "生成提交信息异常", e)
                _state.update { it.copy(generatingCommitMessage = false, toast = context.getString(R.string.git_toast_generate_commit_failed)) }
            }
        }
    }
    /** 在当前工作区执行 `git init` 初始化仓库；成功后 runAction 末尾自动刷新（notARepo 翻 false）。 */
    fun initRepo() = runAction(R.string.git_action_init, { repository.initRepo() })

    /** 显示仓库选择弹窗（列出工作区发现的 git 仓库）。 */
    fun showRepoPicker() = _state.update { it.copy(repoPickerVisible = true) }

    /** 关闭仓库选择弹窗。 */
    fun hideRepoPicker() = _state.update { it.copy(repoPickerVisible = false) }

    /**
     * 切换到指定路径的 git 仓库（手动指定或从发现列表选择）。
     * 先校验目标路径是 git 仓库，通过后设置当前仓库路径并刷新。
     */
    fun switchRepo(path: String) {
        if (_state.value.busy) return
        _state.update { it.copy(repoPickerVisible = false) }
        viewModelScope.launch {
            try {
                if (!repository.isGitRepo(path)) {
                    _state.update { it.copy(toast = context.getString(R.string.git_toast_not_repo)) }
                    return@launch
                }
                repository.setRepoPath(path)
                refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "切换仓库失败", e)
                _state.update { it.copy(toast = context.getString(R.string.git_toast_switch_repo_failed, e.message)) }
            }
        }
    }

    /** 在当前工作区克隆远程仓库；成功后 runAction 末尾自动刷新进仓库态。 */
    fun cloneRepo(url: String) = runAction(R.string.git_action_clone, { repository.cloneRepo(url) })
    fun pull() {
        if (!_state.value.hasRemote) {
            _state.update { it.copy(toast = context.getString(R.string.git_toast_no_remote_pull)) }
            return
        }
        runAction(R.string.git_pull, { repository.pull() })
    }
    fun push(onPromptRenameMaster: () -> Unit = {}) {
        if (!_state.value.hasRemote) {
            _state.update { it.copy(toast = context.getString(R.string.git_toast_no_remote_push)) }
            return
        }
        viewModelScope.launch {
            if (repository.isFirstPushOfMaster()) {
                onPromptRenameMaster()
            } else {
                runAction(R.string.git_push, { repository.push() })
            }
        }
    }

    /** 用户在首次推送 master 时选择重命名为 main 并推送。 */
    fun pushRenameMasterToMain() =
        runAction(R.string.git_push, { repository.renameMasterToMainAndPush() })

    /** 用户在首次推送 master 时选择保留 master 直接推送。 */
    fun pushDirectly() =
        runAction(R.string.git_push, { repository.push() })

    /**
     * 打开某条提交的详情弹层。若文件清单尚未加载则懒加载（不置 [GitUiState.busy]，
     * 因为这是只读查看，不应阻塞 status/branches 的写操作）。
     */
    fun openCommitDetail(hash: String) {
        val current = _state.value
        if (current.commitDetailHash == hash) return
        _state.update { it.copy(commitDetailHash = hash) }
        if (hash in current.commitFiles) return
        _state.update { it.copy(loadingCommit = hash) }
        viewModelScope.launch {
            val files = try {
                repository.commitFiles(hash)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载提交文件失败: $hash", e)
                emptyList()
            }
            _state.update {
                it.copy(commitFiles = it.commitFiles + (hash to files), loadingCommit = null)
            }
        }
    }

    fun closeCommitDetail() = _state.update { it.copy(commitDetailHash = null) }

    /** 正在进行的 issue 列表加载任务；切换过滤/刷新时取消旧的，避免过期结果覆盖。 */
    private var issuesLoadJob: Job? = null

    /**
     * 加载当前过滤下的 GitHub issue 列表。仅 GitHub 仓库且有 token 时请求；否则置已加载（展示相应提示态）。
     * 不置 busy——只读查看不应阻塞 status/branches 的写操作。
     */
    fun loadIssues() {
        val current = _state.value
        if (!current.isIssuePlatform || !current.hasGitHubToken) {
            issuesLoadJob?.cancel()
            _state.update { it.copy(issuesLoaded = true, issuesLoading = false) }
            return
        }
        val filter = current.issuesFilter
        issuesLoadJob?.cancel()
        _state.update { it.copy(issuesLoading = true, issuesError = null) }
        issuesLoadJob = viewModelScope.launch {
            try {
                val list = issuesRepository.listIssues(filter).getOrThrow()
                _state.update { it.copy(issues = list, issuesLoading = false, issuesLoaded = true, issuesError = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载 issue 列表失败", e)
                _state.update { it.copy(issuesLoading = false, issuesLoaded = true, issuesError = e.message) }
            }
        }
    }

    /** 切换 issue 过滤（"open"/"closed"）并重新拉取。 */
    fun setIssuesFilter(filter: String) {
        if (_state.value.issuesFilter == filter) return
        _state.update { it.copy(issuesFilter = filter, issues = emptyList(), issuesLoaded = false, issuesError = null) }
        loadIssues()
    }

    /** 打开某条 issue 的详情页并拉取详情；加载失败时页内显示错误+重试（issueDetail 保持 null）。 */
    fun openIssueDetail(number: String) {
        val current = _state.value
        if (current.issueDetailNumber == number && current.issueDetail != null) return
        _state.update { it.copy(issueDetailNumber = number, issueDetail = null, issueDetailLoading = true) }
        viewModelScope.launch {
            try {
                val issue = issuesRepository.getIssue(number).getOrThrow()
                // 只应用仍为当前查看编号的结果，避免快速切换时过期详情覆盖。
                if (_state.value.issueDetailNumber != number) return@launch
                _state.update { it.copy(issueDetail = issue, issueDetailLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载 issue 详情失败: $number", e)
                if (_state.value.issueDetailNumber != number) return@launch
                _state.update { it.copy(issueDetail = null, issueDetailLoading = false) }
            }
        }
    }

    /** 关闭 issue 详情页。 */
    fun closeIssueDetail() = _state.update { it.copy(issueDetailNumber = null, issueDetail = null, issueDetailLoading = false) }

    /** 切换 issue 排序方式（newest/oldest/updated/comments）。 */
    fun setIssuesSort(sort: String) {
        if (_state.value.issuesSort == sort) return
        _state.update { it.copy(issuesSort = sort) }
    }

    // ── Pull Requests ──

    /** 正在进行的 PR 列表加载任务；切换过滤/刷新时取消旧的，避免过期结果覆盖。 */
    private var pullsLoadJob: Job? = null
    /** PR 列表 CI 批量加载任务。 */
    private var pullChecksJob: Job? = null
    /** PR 详情加载任务（详情、CI、评论都由它的子协程执行）。 */
    private var pullDetailJob: Job? = null
    /** 详情页 CI 轮询任务；页面关闭/切换 PR 时取消，防止泄漏与过期回写。 */
    private var pullDetailChecksPollJob: Job? = null

    /**
     * 加载当前过滤下的 PR 列表。仅 GitHub 仓库且有 token 时请求；否则置已加载（展示提示态）。
     * 列表返回后异步给前 [PR_CHECKS_ON_LIST] 条拉 CI 汇总，不阻塞列表展示。
     */
    fun loadPulls() {
        val current = _state.value
        if (!current.isGitHub || !current.hasGitHubToken) {
            pullsLoadJob?.cancel()
            pullChecksJob?.cancel()
            _state.update { it.copy(pullsLoaded = true, pullsLoading = false) }
            return
        }
        val filter = current.pullsFilter
        pullsLoadJob?.cancel()
        pullChecksJob?.cancel()
        _state.update { it.copy(pullsLoading = true, pullsError = null, pullChecks = emptyMap()) }
        pullsLoadJob = viewModelScope.launch {
            try {
                val list = pullRequestsRepository.listPullRequests(filter).getOrThrow()
                if (_state.value.pullsFilter != filter) return@launch
                _state.update { it.copy(pulls = list, pullsLoading = false, pullsLoaded = true, pullsError = null) }
                loadPullChecks(list, filter)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载 PR 列表失败", e)
                if (_state.value.pullsFilter != filter) return@launch
                _state.update { it.copy(pullsLoading = false, pullsLoaded = true, pullsError = e.message) }
            }
        }
    }

    /** 切换 PR 过滤（open/closed/all）并重新拉取。 */
    fun setPullsFilter(filter: String) {
        if (_state.value.pullsFilter == filter) return
        _state.update {
            it.copy(
                pullsFilter = filter,
                pulls = emptyList(),
                pullsLoaded = false,
                pullsError = null,
                pullChecks = emptyMap()
            )
        }
        loadPulls()
    }

    /** 切换 PR 排序方式（newest/oldest/updated）。 */
    fun setPullsSort(sort: String) {
        if (_state.value.pullsSort == sort) return
        _state.update { it.copy(pullsSort = sort) }
    }

    /**
     * 给列表前若干条 PR 拉 CI 汇总。单条失败不影响其它项；结果回写前再次校验过滤条件，
     * 避免切换过滤后旧请求把徽章写到新列表上。
     */
    private fun loadPullChecks(pulls: List<GitHubPullRequest>, filter: String) {
        val targets = pulls.take(PR_CHECKS_ON_LIST).filter { it.head.sha.isNotBlank() }
        if (targets.isEmpty()) return
        pullChecksJob = viewModelScope.launch {
            val semaphore = Semaphore(PR_CHECKS_CONCURRENCY)
            val results = coroutineScope {
                targets.map { pr ->
                    async {
                        semaphore.withPermit {
                            pr.number to pullRequestsRepository.getChecks(pr.head.sha).getOrNull()
                        }
                    }
                }.awaitAll()
            }
            if (_state.value.pullsFilter != filter) return@launch
            val summaries = results.mapNotNull { (number, summary) -> summary?.let { number to it } }.toMap()
            if (summaries.isEmpty()) return@launch
            _state.update { it.copy(pullChecks = it.pullChecks + summaries) }
        }
    }

    /** 打开某条 PR 的详情页：并行拉详情、CI 与评论；失败时页内显示错误+重试。 */
    fun openPullRequestDetail(number: Long) {
        pullDetailJob?.cancel()
        pullDetailChecksPollJob?.cancel()
        _state.update {
            it.copy(
                pullDetailNumber = number,
                pullDetail = null,
                pullDetailLoading = true,
                pullDetailChecks = null,
                pullDetailChecksLoading = true,
                pullComments = emptyList(),
                pullCommentsLoading = true,
                pullActionBusy = false,
                pullCommits = emptyList(),
                pullCommitsLoading = true,
                pullFiles = emptyList(),
                pullFilesLoading = true
            )
        }
        pullDetailJob = viewModelScope.launch {
            try {
                val pr = pullRequestsRepository.getPullRequest(number).getOrThrow()
                if (_state.value.pullDetailNumber != number) return@launch
                _state.update { it.copy(pullDetail = pr, pullDetailLoading = false) }
                launch { loadPullDetailChecks(number, pr.head.sha) }
                launch { loadPullCommentsFor(number) }
                launch { loadPullCommitsFor(number) }
                launch { loadPullFilesFor(number) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载 PR 详情失败: $number", e)
                if (_state.value.pullDetailNumber != number) return@launch
                _state.update {
                    it.copy(
                        pullDetail = null,
                        pullDetailLoading = false,
                        pullDetailChecksLoading = false,
                        pullCommentsLoading = false,
                        pullCommitsLoading = false,
                        pullFilesLoading = false
                    )
                }
            }
        }
    }

    /** 关闭 PR 详情页。 */
    fun closePullRequestDetail() {
        pullDetailJob?.cancel()
        pullDetailChecksPollJob?.cancel()
        _state.update {
            it.copy(
                pullDetailNumber = null,
                pullDetail = null,
                pullDetailLoading = false,
                pullDetailChecks = null,
                pullDetailChecksLoading = false,
                pullComments = emptyList(),
                pullCommentsLoading = false,
                pullActionBusy = false,
                pullCommits = emptyList(),
                pullCommitsLoading = false,
                pullFiles = emptyList(),
                pullFilesLoading = false,
                prCommitDetailSha = null,
                prCommitFiles = emptyMap(),
                prCommitFilesLoading = null
            )
        }
    }

    /** 详情页重试/刷新入口：重新拉取当前 PR 的详情、CI 与评论。 */
    fun refreshPullRequestDetail() {
        val number = _state.value.pullDetailNumber ?: return
        openPullRequestDetail(number)
    }

    private suspend fun loadPullDetailChecks(number: Long, sha: String) {
        val summary = pullRequestsRepository.getChecks(sha).getOrNull()
        if (_state.value.pullDetailNumber != number) return
        _state.update { it.copy(pullDetailChecks = summary, pullDetailChecksLoading = false) }
        if (summary != null && summary.pending > 0) startPullDetailChecksPolling(number, sha)
    }

    /**
     * 详情页 CI 有限次轮询：仍处 pending 的检查按固定间隔复查，直到全部进入终态、达到次数上限
     * 或离开详情页。job 由 [closePullRequestDetail] / 切换 PR 取消，避免界面停在「进行中」或泄漏。
     */
    private fun startPullDetailChecksPolling(number: Long, sha: String) {
        if (sha.isBlank()) return
        pullDetailChecksPollJob?.cancel()
        pullDetailChecksPollJob = viewModelScope.launch {
            var attempt = 0
            while (attempt < PR_CHECKS_POLL_MAX_ATTEMPTS) {
                delay(PR_CHECKS_POLL_INTERVAL_MS)
                if (_state.value.pullDetailNumber != number) return@launch
                attempt++
                val summary = pullRequestsRepository.getChecks(sha).getOrNull() ?: continue
                if (_state.value.pullDetailNumber != number) return@launch
                _state.update { it.copy(pullDetailChecks = summary, pullDetailChecksLoading = false) }
                if (summary.pending <= 0) return@launch
            }
        }
    }

    private suspend fun loadPullCommentsFor(number: Long) {
        val comments = issuesRepository.getComments(number).getOrDefault(emptyList())
        if (_state.value.pullDetailNumber != number) return
        _state.update { it.copy(pullComments = comments, pullCommentsLoading = false) }
    }

    /** 在详情页发表评论（PR 评论与 issue 评论共用接口）。 */
    fun addPullComment(body: String) {
        val number = _state.value.pullDetailNumber ?: return
        val text = body.trim()
        if (text.isEmpty() || _state.value.pullActionBusy) return
        _state.update { it.copy(pullActionBusy = true) }
        viewModelScope.launch {
            val result = issuesRepository.addComment(number, text)
            if (result.isSuccess) {
                _state.update { it.copy(pullActionBusy = false, toast = context.getString(R.string.git_pr_comment_success)) }
                loadPullCommentsFor(number)
            } else {
                _state.update { it.copy(pullActionBusy = false, toast = context.getString(R.string.git_pr_comment_failed)) }
            }
        }
    }

    /** 删除详情页评论。 */
    fun deletePullComment(commentId: Long) {
        val number = _state.value.pullDetailNumber ?: return
        if (_state.value.pullActionBusy) return
        _state.update { it.copy(pullActionBusy = true) }
        viewModelScope.launch {
            val result = issuesRepository.deleteComment(commentId)
            if (result.isSuccess) {
                _state.update { it.copy(pullActionBusy = false, toast = context.getString(R.string.git_pr_comment_deleted)) }
                loadPullCommentsFor(number)
            } else {
                _state.update { it.copy(pullActionBusy = false, toast = context.getString(R.string.git_pr_comment_failed)) }
            }
        }
    }

    private suspend fun loadPullCommitsFor(number: Long) {
        val commits = pullRequestsRepository.listPullRequestCommits(number).getOrDefault(emptyList())
        if (_state.value.pullDetailNumber != number) return
        _state.update { it.copy(pullCommits = commits, pullCommitsLoading = false) }
    }

    private suspend fun loadPullFilesFor(number: Long) {
        val files = pullRequestsRepository.listPullRequestFiles(number).getOrDefault(emptyList())
        if (_state.value.pullDetailNumber != number) return
        _state.update { it.copy(pullFiles = files, pullFilesLoading = false) }
    }

    /**
     * 打开详情页某条提交的弹层。若文件清单（含 patch）尚未加载则懒加载（不置 busy，
     * 只读查看不应阻塞写操作）。与 [loadPullCommitsFor] 同款竞态守卫：离开详情页后丢弃过期结果。
     */
    fun openPrCommitDetail(sha: String) {
        val current = _state.value
        if (current.prCommitDetailSha == sha) return
        _state.update { it.copy(prCommitDetailSha = sha) }
        if (sha in current.prCommitFiles) return
        val number = current.pullDetailNumber ?: return
        _state.update { it.copy(prCommitFilesLoading = sha) }
        viewModelScope.launch {
            val files = pullRequestsRepository.getCommit(sha).getOrNull()?.files.orEmpty()
            if (_state.value.pullDetailNumber != number) return@launch
            _state.update {
                it.copy(prCommitFiles = it.prCommitFiles + (sha to files), prCommitFilesLoading = null)
            }
        }
    }

    /** 关闭详情页提交弹层（保留已加载的文件清单缓存）。 */
    fun closePrCommitDetail() = _state.update { it.copy(prCommitDetailSha = null) }

    /**
     * 纯函数：把 GitHub patch 正文直接组装成 [DiffData]，供 PR 提交弹层点文件看逐行 diff。
     * 与 [computeDiff] 的区别：不需要取两侧文件内容跑 LCS，patch 里已含行级差异与行号。
     * patch 为 null/blank（二进制或 diff 过大 GitHub 不返回）时降级为「文件过大」提示页。
     * 不做语法高亮（[DiffRow.highlighted] 置 null，DiffViewer 降级纯文本渲染）。
     * 只做数据转换、不碰 openDiff/finishDiff 状态机，返回值交由 UI 覆盖层渲染。
     */
    fun computeDiffFromPatch(filePath: String, patch: String?): DiffData {
        if (patch.isNullOrBlank()) {
            return DiffData(filePath, "", "", emptyList(), 0, 0, isLarge = true)
        }
        val rows = PatchDiffParser.parsePatch(patch).map {
            DiffRow(it.type, it.text, null, it.oldLineNum, it.newLineNum)
        }
        val added = rows.count { it.type == LineDiff.LineType.ADD }
        val removed = rows.count { it.type == LineDiff.LineType.REMOVE }
        return DiffData(filePath, "", "", rows, added, removed)
    }

    /** 关闭/重开当前详情页 PR。 */
    private fun updatePullRequestState(state: String) {
        val number = _state.value.pullDetailNumber ?: return
        if (_state.value.pullActionBusy) return
        _state.update { it.copy(pullActionBusy = true) }
        viewModelScope.launch {
            try {
                val pull = pullRequestsRepository.setPullRequestState(number, state).getOrThrow()
                _state.update {
                    it.copy(
                        pullActionBusy = false,
                        pullDetail = pull,
                        toast = context.getString(
                            if (state == "closed") R.string.git_pr_close_success
                            else R.string.git_pr_reopen_success
                        )
                    )
                }
                loadPulls()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "更新 PR 状态失败: $number -> $state", e)
                _state.update {
                    it.copy(
                        pullActionBusy = false,
                        toast = context.getString(R.string.git_pr_action_failed)
                    )
                }
            }
        }
    }

    /** 关闭当前详情页 PR。 */
    fun closePullRequest() = updatePullRequestState("closed")

    /** 重开当前详情页 PR。 */
    fun reopenPullRequest() = updatePullRequestState("open")

    /** 合并当前详情页 PR。[method] 取 merge / squash / rebase。 */
    fun mergePullRequest(method: String = "merge") {
        val number = _state.value.pullDetailNumber ?: return
        if (_state.value.pullActionBusy) return
        _state.update { it.copy(pullActionBusy = true) }
        viewModelScope.launch {
            try {
                val result = pullRequestsRepository.mergePullRequest(number, method).getOrThrow()
                if (!result.merged) {
                    _state.update {
                        it.copy(
                            pullActionBusy = false,
                            toast = result.message.ifBlank { context.getString(R.string.git_pr_merge_failed) }
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        pullActionBusy = false,
                        toast = context.getString(R.string.git_pr_merge_success)
                    )
                }
                loadPulls()
                refreshPullRequestDetail()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "合并 PR 失败: $number", e)
                _state.update {
                    it.copy(
                        pullActionBusy = false,
                        toast = context.getString(R.string.git_pr_merge_failed)
                    )
                }
            }
        }
    }

    /** 打开新建 PR 弹层。 */
    fun showCreatePull() = _state.update {
        it.copy(createPullVisible = true, createPullBusy = false, createPullError = null)
    }

    /** 关闭新建 PR 弹层。 */
    fun hideCreatePull() = _state.update {
        it.copy(createPullVisible = false, createPullBusy = false, createPullError = null)
    }

    /** 基于已存在的远程分支创建 PR。 */
    fun createPull(
        title: String,
        body: String?,
        head: String,
        base: String,
        draft: Boolean = false
    ) {
        if (_state.value.createPullBusy) return
        val cleanTitle = title.trim()
        val cleanHead = head.trim()
        val cleanBase = base.trim()
        if (cleanTitle.isBlank() || cleanHead.isBlank() || cleanBase.isBlank()) {
            _state.update { it.copy(createPullError = context.getString(R.string.git_pr_create_missing_fields)) }
            return
        }
        if (cleanHead == cleanBase) {
            _state.update { it.copy(createPullError = context.getString(R.string.git_pr_create_same_branch)) }
            return
        }
        _state.update { it.copy(createPullBusy = true, createPullError = null) }
        viewModelScope.launch {
            try {
                val pull = pullRequestsRepository
                    .createPullRequest(cleanTitle, cleanHead, cleanBase, body?.trim()?.ifBlank { null }, draft)
                    .getOrThrow()
                _state.update {
                    it.copy(
                        createPullBusy = false,
                        createPullVisible = false,
                        createPullError = null,
                        toast = context.getString(R.string.git_pr_create_success)
                    )
                }
                loadPulls()
                openPullRequestDetail(pull.number)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "创建 PR 失败", e)
                _state.update {
                    it.copy(
                        createPullBusy = false,
                        createPullError = context.getString(R.string.git_pr_create_failed, e.message.orEmpty())
                    )
                }
            }
        }
    }

    /**
     * 把当前工作区改动作为 PR 提交：新建分支 → 暂存全部 → 提交 → 推送 → 创建 PR。
     * 任一步失败都保留现场（已建分支/提交不会被回滚），错误显示在弹层里。
     */
    fun createPullFromWorktree(
        title: String,
        body: String?,
        newBranch: String,
        base: String,
        commitMessage: String,
        draft: Boolean = false
    ) {
        if (_state.value.createPullBusy) return
        val cleanTitle = title.trim()
        val cleanBase = base.trim()
        val branch = newBranch.trim().ifBlank { "aiverse/pr-${System.currentTimeMillis() / 1000}" }
        val message = commitMessage.trim().ifBlank { cleanTitle }
        if (cleanTitle.isBlank() || cleanBase.isBlank() || message.isBlank()) {
            _state.update { it.copy(createPullError = context.getString(R.string.git_pr_create_missing_fields)) }
            return
        }
        if (!_state.value.hasRemote) {
            _state.update { it.copy(createPullError = context.getString(R.string.git_pr_create_no_remote)) }
            return
        }
        if (!_state.value.hasIdentity) {
            _state.update { it.copy(createPullError = context.getString(R.string.git_pr_create_no_identity)) }
            return
        }
        _state.update { it.copy(createPullBusy = true, createPullError = null) }
        viewModelScope.launch {
            try {
                val status = repository.status()
                if (!status.hasChanges) {
                    throw IllegalStateException(context.getString(R.string.git_pr_create_no_changes))
                }
                if (status.isDetached) {
                    throw IllegalStateException(context.getString(R.string.git_pr_create_detached))
                }
                repository.createBranch(branch, null, true)
                repository.stageAll()
                repository.commit(message)
                repository.push()
                val pull = pullRequestsRepository
                    .createPullRequest(cleanTitle, branch, cleanBase, body?.trim()?.ifBlank { null }, draft)
                    .getOrThrow()
                _state.update {
                    it.copy(
                        createPullBusy = false,
                        createPullVisible = false,
                        createPullError = null,
                        toast = context.getString(R.string.git_pr_create_success)
                    )
                }
                loadPulls()
                openPullRequestDetail(pull.number)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "从当前改动创建 PR 失败", e)
                _state.update {
                    it.copy(
                        createPullBusy = false,
                        createPullError = context.getString(R.string.git_pr_create_failed, e.message.orEmpty())
                    )
                }
            }
        }
    }

    /** 创建 issue；成功后刷新列表，失败只提示。 */
    fun createIssue(
        title: String,
        body: String?,
        labels: List<String>? = null,
        assignees: List<String>? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            val result = issuesRepository.createIssue(
                cleanTitle,
                body?.trim()?.ifBlank { null },
                labels?.filter { it.isNotBlank() }?.ifEmpty { null },
                assignees?.filter { it.isNotBlank() }?.ifEmpty { null }
            )
            if (result.isSuccess) {
                _state.update { it.copy(toast = context.getString(R.string.git_issue_create_success)) }
                loadIssues()
            } else {
                _state.update { it.copy(toast = context.getString(R.string.git_issue_create_failed)) }
            }
        }
    }

    /** 切换多选：进入/退出多选模式，进入时清空已选。 */
    fun toggleIssueSelectionMode() {
        _state.update { it.copy(issueSelectionMode = !it.issueSelectionMode, selectedIssueNumbers = emptySet()) }
    }

    /** 勾选/取消勾选单条 issue。 */
    fun toggleIssueSelection(number: String) {
        _state.update {
            val selected = it.selectedIssueNumbers
            it.copy(selectedIssueNumbers = if (number in selected) selected - number else selected + number)
        }
    }

    /** 清空多选（退出多选并清空选中）。 */
    fun clearIssueSelection() = _state.update { it.copy(issueSelectionMode = false, selectedIssueNumbers = emptySet()) }

    /** 全选当前列表中的 issue。 */
    fun selectAllVisibleIssues() {
        _state.update { it.copy(selectedIssueNumbers = it.issues.map { i -> i.number }.toSet()) }
    }

    /** 批量关闭选中的 issue；成功后刷新列表并清空选择。 */
    fun bulkCloseSelected() {
        val selected = _state.value.selectedIssueNumbers
        if (selected.isEmpty() || _state.value.issueBulkBusy) return
        _state.update { it.copy(issueBulkBusy = true, issueActionError = null) }
        viewModelScope.launch {
            var failed = 0
            try {
                for (number in selected) {
                    val r = issuesRepository.closeIssue(number)
                    if (r.isFailure) failed++
                }
                _state.update {
                    it.copy(
                        issueBulkBusy = false,
                        issueSelectionMode = false,
                        selectedIssueNumbers = emptySet(),
                        toast = context.getString(
                            if (failed == 0) R.string.git_issue_bulk_close_success
                            else R.string.git_issue_bulk_close_partial, selected.size - failed, failed
                        )
                    )
                }
                loadIssues()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "批量关闭 issue 失败", e)
                _state.update { it.copy(issueBulkBusy = false, issueActionError = e.message) }
            }
        }
    }

    /** 批量给选中的 issue 添加标签；成功后刷新列表并清空选择。 */
    fun bulkAddLabelsToSelected(labels: List<String>) {
        val selected = _state.value.selectedIssueNumbers
        if (selected.isEmpty() || labels.isEmpty() || _state.value.issueBulkBusy) return
        _state.update { it.copy(issueBulkBusy = true, issueActionError = null) }
        viewModelScope.launch {
            var failed = 0
            try {
                for (number in selected) {
                    val r = issuesRepository.addLabels(number, labels)
                    if (r.isFailure) failed++
                }
                _state.update {
                    it.copy(
                        issueBulkBusy = false,
                        issueSelectionMode = false,
                        selectedIssueNumbers = emptySet(),
                        toast = context.getString(
                            if (failed == 0) R.string.git_issue_bulk_label_success
                            else R.string.git_issue_bulk_label_partial, selected.size - failed, failed
                        )
                    )
                }
                loadIssues()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "批量加标签失败", e)
                _state.update { it.copy(issueBulkBusy = false, issueActionError = e.message) }
            }
        }
    }

    /** 批量给选中的 issue 分配受理人；成功后刷新列表并清空选择。 */
    fun bulkSetAssigneesToSelected(assignees: List<String>) {
        val selected = _state.value.selectedIssueNumbers
        if (selected.isEmpty() || assignees.isEmpty() || _state.value.issueBulkBusy) return
        _state.update { it.copy(issueBulkBusy = true, issueActionError = null) }
        viewModelScope.launch {
            var failed = 0
            try {
                for (number in selected) {
                    val r = issuesRepository.setAssignees(number, assignees)
                    if (r.isFailure) failed++
                }
                _state.update {
                    it.copy(
                        issueBulkBusy = false,
                        issueSelectionMode = false,
                        selectedIssueNumbers = emptySet(),
                        toast = context.getString(
                            if (failed == 0) R.string.git_issue_bulk_assign_success
                            else R.string.git_issue_bulk_assign_partial, selected.size - failed, failed
                        )
                    )
                }
                loadIssues()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "批量分配受理人失败", e)
                _state.update { it.copy(issueBulkBusy = false, issueActionError = e.message) }
            }
        }
    }

    /** 懒加载当前 issue 的评论列表。 */
    fun loadIssueComments(number: String) {
        if (_state.value.issueCommentsLoading) return
        _state.update { it.copy(issueCommentsLoading = true) }
        viewModelScope.launch {
            try {
                val comments = issuesRepository.getComments(number).getOrThrow()
                if (_state.value.issueDetailNumber != number) return@launch
                _state.update { it.copy(issueComments = comments, issueCommentsLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载评论失败: $number", e)
                if (_state.value.issueDetailNumber != number) return@launch
                _state.update { it.copy(issueCommentsLoading = false) }
            }
        }
    }

    /** 懒加载仓库贡献者（受理人候选）。 */
    fun loadContributors() {
        if (_state.value.contributors.isNotEmpty()) return
        viewModelScope.launch {
            val list = runCatching { issuesRepository.getContributors().getOrThrow() }.getOrDefault(emptyList())
            _state.update { it.copy(contributors = list) }
        }
    }

    /**
     * 详情页通用写操作：置 busy → 执行 → 成功后重新拉取详情（+评论），失败置 [GitUiState.issueActionError]。
     * [nameRes] 用于成功 toast；[refreshComments] 为 true 时操作后一并刷新评论。
     */
    private fun runIssueAction(
        @StringRes nameRes: Int,
        refreshComments: Boolean = false,
        successToast: (() -> String)? = null,
        action: suspend (String) -> Result<*>
    ) {
        if (_state.value.issueActionBusy) return
        val number = _state.value.issueDetailNumber ?: return
        _state.update { it.copy(issueActionBusy = true, issueActionError = null) }
        viewModelScope.launch {
            val name = context.getString(nameRes)
            try {
                action(number).getOrThrow()
                val issue = issuesRepository.getIssue(number).getOrNull()
                _state.update {
                    it.copy(
                        issueDetail = issue ?: it.issueDetail,
                        issueActionBusy = false,
                        toast = successToast?.invoke()
                            ?: context.getString(R.string.git_toast_action_success, name)
                    )
                }
                if (refreshComments) loadIssueComments(number)
                // 详情可能影响列表（关闭/重开/标签/受理人/里程碑），刷新列表保持同步。
                loadIssues()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "issue 详情写操作失败: $number", e)
                _state.update {
                    it.copy(
                        issueActionBusy = false,
                        issueActionError = e.message,
                        toast = context.getString(R.string.git_toast_action_failed, name, e.message ?: "")
                    )
                }
            }
        }
    }

    fun closeIssueInDetail(stateReason: String) = runIssueAction(R.string.git_issue_action_close) { n ->
        issuesRepository.closeIssue(n, stateReason)
    }
    fun reopenIssueInDetail() = runIssueAction(R.string.git_issue_action_reopen) { n -> issuesRepository.reopenIssue(n) }
    fun addIssueComment(body: String) = runIssueAction(R.string.git_issue_action_comment, refreshComments = true) { n ->
        issuesRepository.addComment(n, body)
    }
    fun deleteIssueComment(commentId: Long) = runIssueAction(R.string.git_issue_action_delete_comment, refreshComments = true) { n ->
        issuesRepository.deleteComment(commentId)
    }
    fun addIssueLabels(labels: List<String>) = runIssueAction(R.string.git_issue_action_label) { n ->
        issuesRepository.addLabels(n, labels)
    }
    fun removeIssueLabel(label: String) = runIssueAction(R.string.git_issue_action_label) { n ->
        issuesRepository.removeLabel(n, label)
    }
    fun setIssueAssignees(assignees: List<String>) = runIssueAction(
        nameRes = R.string.git_issue_action_assignee,
        // 成功提示直接带上受理人，省得用户回列表才看到结果。
        successToast = {
            if (assignees.isEmpty()) {
                context.getString(R.string.git_toast_assignees_cleared)
            } else {
                context.getString(
                    R.string.git_toast_assignees_set,
                    assignees.joinToString(", ") { "@$it" }
                )
            }
        }
    ) { n -> issuesRepository.setAssignees(n, assignees) }
    fun toggleIssueLock(locked: Boolean) = runIssueAction(R.string.git_issue_action_lock) { n ->
        issuesRepository.setIssueLock(n, locked)
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    /**
     * 分页加载更旧的提交。UI 滚到底时调用：取已加载提交作为锚点，[repository.graphAppend] 用
     * `--skip` 取下一页并整体重算泳道布局，返回完整图直接替换 state.graph。不置 busy，避免阻塞写操作。
     * 守卫：正在加载或无更多时直接返回。失败 toast 提示且保留 hasMore 允许重试。
     */
    fun loadMoreCommits() {
        val current = _state.value
        if (current.graphLoadingMore || !current.graph.hasMore) return
        _state.update { it.copy(graphLoadingMore = true) }
        viewModelScope.launch {
            try {
                val graph = repository.graphAppend(current.graph.commits, current.graph.refs)
                val commits = graph.toGitCommits()
                _state.update { it.copy(graph = graph, commits = commits, graphLoadingMore = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "加载更多提交失败", e)
                _state.update { it.copy(graphLoadingMore = false, toast = context.getString(R.string.git_toast_load_more_failed, e.message)) }
            }
        }
    }

    /**
     * 切换到指定分支或标签。成功后刷新全量状态。
     */
    fun checkoutBranch(ref: String, isRemote: Boolean = false) {
        if (_state.value.busy || _state.value.checkoutLoading != null) return
        _state.update { it.copy(checkoutLoading = ref, toast = null) }
        viewModelScope.launch {
            val msg = try {
                repository.checkout(ref, isRemote)
                context.getString(R.string.git_toast_checkout_success, ref)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileLogger.e(TAG, "切换分支失败", e)
                val reason = (e as? GitCommandFailureException)?.output ?: e.message
                context.getString(R.string.git_toast_checkout_failed, GitErrorMessage.friendly(reason ?: "", onExternalStorage = onExternalStorage()))
            }
            try {
                if (repository.isRepo()) {
                    val snap = loadSnapshot(includeIdentity = false)
                    val commits = snap.graph.toGitCommits()
                    _state.update { it.copy(checkoutLoading = null, status = snap.status, commits = commits, graph = snap.graph, hasRemote = snap.hasRemote, notARepo = false, toast = msg) }
                    // 切换分支后分支列表可能变化，若已加载过则刷新。
                    refreshBranchesIfLoaded()
                } else {
                    _state.update { it.copy(checkoutLoading = null, notARepo = true, toast = msg) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(checkoutLoading = null, toast = context.getString(R.string.git_toast_action_refresh_failed, msg)) }
            }
        }
    }

    /**
     * 创建新分支。name 为新分支名；startPoint 为基准分支（null/空 → 从当前 HEAD）；
     * checkout=true 则创建并切换。复用 runAction：置 busy → 跑命令 → 刷新 → toast。
     */
    fun createBranch(name: String, startPoint: String?, checkout: Boolean) {
        if (name.isBlank()) return
        runAction(R.string.git_action_create_branch, { repository.createBranch(name, startPoint, checkout) })
    }

    /**
     * 安全删除本地分支（git branch -d）。未合并或为当前分支时 git 报错，经 runAction toast 透出。
     */
    fun deleteBranch(name: String) {
        if (name.isBlank()) return
        runAction(R.string.git_action_delete_branch, { repository.deleteBranch(name) })
    }

    /**
     * 删除远程分支（git push --delete）。会改远端，失败经 runAction toast 透出。
     */
    fun deleteRemoteBranch(ref: String) {
        if (ref.isBlank()) return
        runAction(R.string.git_action_delete_remote_branch, { repository.deleteRemoteBranch(ref) })
    }

    /**
     * 重命名本地分支（git branch -m）。当前分支也可重命名。失败经 runAction toast 透出。
     */
    fun renameBranch(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank()) return
        runAction(R.string.git_action_rename_branch, { repository.renameBranch(oldName, newName) })
    }

    /**
     * 创建轻量标签（git tag <name>），指向当前 HEAD 或指定提交。失败经 runAction toast 透出。
     */
    fun createTag(name: String, commitHash: String? = null) {
        if (name.isBlank()) return
        runAction(R.string.git_action_create_tag, { repository.createTag(name, commitHash) })
    }

    /**
     * 重置当前分支到指定提交。
     */
    fun resetToCommit(commitHash: String, mode: String = "mixed") {
        if (commitHash.isBlank()) return
        runAction(R.string.git_reset_to_commit, { repository.resetToCommit(commitHash, mode) })
    }

    /**
     * 添加规则到 .gitignore。
     */
    fun addToGitignore(pattern: String) {
        if (pattern.isBlank()) return
        runAction(R.string.git_add_to_gitignore, { repository.addToGitignore(pattern) })
    }

    /**
     * 删除本地标签（git tag -d）。失败经 runAction toast 透出。
     */
    fun deleteTag(name: String) {
        if (name.isBlank()) return
        runAction(R.string.git_action_delete_tag, { repository.deleteTag(name) })
    }

    /**
     * 合并分支到当前分支。
     */
    fun mergeBranch(branch: String, noFf: Boolean = false) {
        if (branch.isBlank()) return
        runAction(R.string.git_action_merge, { repository.mergeBranch(branch, noFf) })
    }

    /**
     * 中止当前进行中的合并。
     */
    fun abortMerge() {
        runAction(R.string.git_action_abort_merge, { repository.abortMerge() })
    }

    /**
     * 加载储藏列表。
     */
    fun loadStashes() {
        viewModelScope.launch {
            _state.update { it.copy(stashesLoading = true) }
            val list = runCatching { repository.stashList() }.getOrDefault(emptyList())
            _state.update { it.copy(stashes = list, stashesLoading = false) }
        }
    }

    /**
     * 储藏工作区改动。
     */
    fun stashSave(message: String? = null, includeUntracked: Boolean = false) {
        runAction(R.string.git_action_stash, { repository.stashSave(message, includeUntracked) }) {
            loadStashes()
        }
    }

    /**
     * 弹出指定储藏（应用并删除）。
     */
    fun stashPop(index: String = "stash@{0}") {
        runAction(R.string.git_stash_pop, { repository.stashPop(index) }) {
            loadStashes()
        }
    }

    /**
     * 应用指定储藏（保留记录）。
     */
    fun stashApply(index: String = "stash@{0}") {
        runAction(R.string.git_stash_apply, { repository.stashApply(index) }) {
            loadStashes()
        }
    }

    /**
     * 丢弃指定储藏。
     */
    fun stashDrop(index: String) {
        runAction(R.string.git_stash_drop, { repository.stashDrop(index) }) {
            loadStashes()
        }
    }

    /**
     * 清空全部储藏记录。
     */
    fun stashClear() {
        runAction(R.string.git_stash_clear, { repository.stashClear() }) {
            loadStashes()
        }
    }

    /**
     * 退出 diff 视图，清空 diff 状态。
     */
    fun clearDiff() = _state.update { it.copy(diffVisible = false, diffPath = null, diffData = null) }

    /**
     * 加载某次提交中某文件的差异（改动前 vs 改动后）。
 * hash 为提交 hash，path 为文件路径。后台线程算 diff + 高亮，完成后填 diffData。
 */
    fun loadCommitFileDiff(hash: String, path: String) {
        openDiff(path)
        viewModelScope.launch {
            val data = runCatching { computeDiff(path, "${hash}^", hash, repository::showFileContent) }
                .getOrElse { e ->
                    FileLogger.e(TAG, "加载提交文件 diff 失败: $path", e)
                    null
                }
            finishDiff(data)
        }
    }

    /**
     * 加载 PR 中某文件的差异（base 为旧、head 为新）。
 * baseSha/headSha 取自 PR 的 base.sha / head.sha，path 为文件路径。
 * 用 GitHub contents API 按 SHA 取两侧文本（404 返空串按全增/全删，超大抛 GitOutputTooLargeException 走降级），
 * 走与工作区/提交 diff 相同的 loadDiff 状态机（成功填 diffData，失败关页 toast）。
 */
    fun loadPullRequestFileDiff(headSha: String, baseSha: String, path: String) {
        loadDiff(path, baseSha, headSha) { sha, p ->
            pullRequestsRepository.fetchFileContentAt(sha, p).getOrThrow()
        }
    }

    /**
     * 加载工作区某文件的未暂存差异（HEAD vs 工作区当前内容）。
 * path 为文件路径。用 showFileContent("HEAD", path) 取版本库快照，用 worktreeFileContent(path) 取工作区当前内容。
 */
    fun loadWorktreeDiff(path: String) {
        loadDiff(path, "HEAD", REF_WORKTREE) { ref, p ->
            if (ref == REF_WORKTREE) repository.worktreeFileContent(p)
            else repository.showFileContent(ref, p)
        }
    }

    /** 加载某文件的暂存区差异（HEAD vs index）。 */
    fun loadStagedDiff(path: String) {
        loadDiff(path, "HEAD", REF_INDEX) { ref, p ->
            if (ref == REF_INDEX) repository.indexFileContent(p)
            else repository.showFileContent(ref, p)
        }
    }

    /**
     * 加载未跟踪文件的差异：版本库里没有任何版本，旧侧恒为空，整个文件按全新增呈现。
     */
    fun loadUntrackedDiff(path: String) {
        loadDiff(path, REF_EMPTY, REF_WORKTREE) { ref, p ->
            if (ref == REF_WORKTREE) repository.worktreeFileContent(p) else ""
        }
    }

    private fun loadDiff(
        path: String,
        oldRef: String,
        newRef: String,
        contentProvider: suspend (String, String) -> String
    ) {
        openDiff(path)
        viewModelScope.launch {
            val data = runCatching {
                computeDiff(path, oldRef, newRef, contentProvider)
            }.getOrElse { e ->
                FileLogger.e(TAG, "加载 diff 失败: $path", e)
                null
            }
            finishDiff(data)
        }
    }

    /** 进入 diff 全屏页（页内加载中），已有 diff 页打开时忽略。 */
    private fun openDiff(path: String) {
        if (_state.value.diffVisible) return
        _state.update { it.copy(diffVisible = true, diffPath = path, diffData = null) }
    }

    /** diff 计算完成：成功填数据，失败关闭 diff 页并 toast。 */
    private fun finishDiff(data: DiffData?) {
        if (data == null) {
            _state.update { it.copy(diffVisible = false, diffData = null, toast = context.getString(R.string.git_toast_diff_failed)) }
        } else {
            _state.update { it.copy(diffData = data) }
        }
    }

    /**
     * 取旧/新两份文件内容，用 [LineDiff] 算行级差异，对每行做语法高亮，组装成 [DiffData]。
 * 二进制文件（含 NUL 字节）或超大文件（任一侧超 [MAX_DIFF_LINES]）降级处理。
 * [contentProvider] 负责按 ref 取文件内容，供提交 diff 和工作区 diff 复用。
 */
    private suspend fun computeDiff(
        path: String,
        oldRef: String,
        newRef: String,
        contentProvider: suspend (String, String) -> String
    ): DiffData = withContext(Dispatchers.Default) {
        val oldContent: String
        val newContent: String
        try {
            oldContent = contentProvider(oldRef, path)
            newContent = contentProvider(newRef, path)
        } catch (e: GitOutputTooLargeException) {
            FileLogger.i(TAG, "文件过大，跳过 diff 内容: $path")
            return@withContext DiffData(path, oldRef, newRef, emptyList(), 0, 0, isLarge = true)
        }

        // 二进制检测：git show 对二进制文件返回乱码，直接看是否含 NUL。
        if (oldContent.contains('\u0000') || newContent.contains('\u0000')) {
            return@withContext DiffData(path, oldRef, newRef, emptyList(), 0, 0, isBinary = true)
        }

        val oldLines = oldContent.split('\n')
        val newLines = newContent.split('\n')
        if (maxOf(oldLines.size, newLines.size) > MAX_DIFF_LINES ||
            oldLines.any { it.length > MAX_DIFF_LINE_LENGTH } ||
            newLines.any { it.length > MAX_DIFF_LINE_LENGTH }
        ) {
            return@withContext DiffData(path, oldRef, newRef, emptyList(), 0, 0, isLarge = true)
        }

        val diffLines = LineDiff.diff(oldContent, newContent)
        val language = inferSyntaxLanguage(path)

        // 对旧/新文件整体各跑一次高亮，拿到全文的 token 区间；渲染时按行偏移截取对应 SpanStyle。
        // 语法主题跟随当前 UI 主题，避免深色模式下 light 主题的 token 色（如 = 和 {}）看不清。
        // 两侧并行算（高亮是 diff 耗时主体），整体已在 Default 线程池，不碰主线程。
        val darkMode = currentDarkMode()
        val (oldHighlighted, newHighlighted) = coroutineScope {
            val old = async { highlightCode(oldContent, language, darkMode) }
            val new = async { highlightCode(newContent, language, darkMode) }
            old.await() to new.await()
        }

        // 构建行号 + 高亮截取。oldLineOffsets/newLineOffsets 为每行在全文中的起始偏移。
        val oldOffsets = lineOffsets(oldContent)
        val newOffsets = lineOffsets(newContent)

        var oldNum = 0
        var newNum = 0
        val rows = diffLines.map { line ->
            val (oldN, newN) = when (line.type) {
                LineDiff.LineType.CONTEXT -> { oldNum++; newNum++; oldNum to newNum }
                LineDiff.LineType.REMOVE -> { oldNum++; oldNum to null }
                LineDiff.LineType.ADD -> { newNum++; null to newNum }
            }
            // 截取该行在高亮全文中的 SpanStyle。行索引 = 行号 - 1（0-based）。
            val highlighted = when (line.type) {
                LineDiff.LineType.REMOVE -> sliceLineHighlight(oldHighlighted, oldOffsets, oldN?.let { it - 1 })
                LineDiff.LineType.ADD -> sliceLineHighlight(newHighlighted, newOffsets, newN?.let { it - 1 })
                LineDiff.LineType.CONTEXT -> sliceLineHighlight(oldHighlighted, oldOffsets, oldN?.let { it - 1 })
            }
            DiffRow(line.type, line.text, highlighted, oldN, newN)
        }

        val added = rows.count { it.type == LineDiff.LineType.ADD }
        val removed = rows.count { it.type == LineDiff.LineType.REMOVE }
        return@withContext DiffData(path, oldRef, newRef, rows, added, removed)
    }

    /**
     * 当前实际深色模式：AUTO 跟随系统，与 MainActivity 里 AIEditorTheme 的 darkTheme 判定保持一致。
     */
    private suspend fun currentDarkMode(): Boolean {
        val themeMode = themeSettings.themeModeFlow.first()
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (themeMode) {
            AppThemeMode.AUTO -> systemDark
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
        }
    }

    /**
     * 从全文高亮 AnnotatedString 中截取某行的 SpanStyle，返回只含该行文本+样式的新 AnnotatedString。
 * lineIndex 超出范围或高亮为 null 时返回 null（降级纯文本）。
 */
    private fun sliceLineHighlight(
        highlighted: androidx.compose.ui.text.AnnotatedString?,
        offsets: IntArray,
        lineIndex: Int?
    ): androidx.compose.ui.text.AnnotatedString? {
        if (highlighted == null || lineIndex == null || lineIndex < 0 || lineIndex >= offsets.size) return null
        val start = offsets[lineIndex]
        val end = if (lineIndex + 1 < offsets.size) offsets[lineIndex + 1] - 1 else highlighted.length
        if (start >= highlighted.length || end <= start) return null
        return highlighted.subSequence(start, end)
    }

    /** 计算每行在全文中的起始偏移（含末行后的虚拟偏移 = 全文长度）。 */
    private fun lineOffsets(text: String): IntArray {
        val lines = text.split('\n')
        val offsets = IntArray(lines.size + 1)
        var pos = 0
        for (i in lines.indices) {
            offsets[i] = pos
            pos += lines[i].length + 1 // +1 为换行符
        }
        offsets[lines.size] = pos
        return offsets
    }
}
