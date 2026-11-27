package com.aiverse.feature.git.domain

import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.aiverse.feature.git.domain.model.GitHubComment
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.git.domain.model.GitHubMilestone
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** 从 Git remote URL 解析 GitHub 的 owner/repo（保留给 PR 等仅 GitHub 的功能使用）。 */
internal fun parseGithubRepoUrl(url: String): Pair<String, String>? {
    val parsed = parseIssueRepo(url) ?: return null
    if (parsed.first != IssuePlatform.GITHUB) return null
    return parsed.second to parsed.third
}

/** GitHub issue 请求失败（网络、HTTP 状态码、解析、目标不合法）。 */
class GitHubIssuesException(message: String) : Exception(message)

/** 解析后的 GitHub 请求目标：owner/repo + token。 */
internal data class GitHubTarget(val owner: String, val repo: String, val token: String)

/** 解析当前 remote 为 GitHub 目标；非 GitHub 或无 token 时返回 null。 */
internal suspend fun resolveGitHubTarget(
    gitRepository: GitRepository,
    credentialRepository: CredentialRepository
): GitHubTarget? {
    val pair = parseGithubRepoUrl(gitRepository.getRepoUrl()) ?: return null
    val token = credentialRepository.findForHost("github.com")?.token?.takeIf { it.isNotBlank() }
        ?: return null
    return GitHubTarget(pair.first, pair.second, token)
}

/**
 * 执行一次 GitHub API 请求。GET 无 body；POST/PATCH 带 [jsonBody]；PUT/DELETE 无 body（部分返回 204 空体）。
 * 非 2xx 时抛 [GitHubIssuesException]（含状态码与响应体）。
 *
 * 供 PR 仓库使用（issue 仓库已改为多平台，见 [executeIssueRequest]）。
 */
internal fun executeGitHubRequest(
    client: OkHttpClient,
    url: String,
    token: String,
    method: String = "GET",
    jsonBody: String? = null
): String {
    val builder = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer $token")
    when (method) {
        "GET" -> builder.get()
        "DELETE" -> builder.delete()
        else -> {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body: RequestBody = jsonBody?.toRequestBody(mediaType)
                ?: "{}".toRequestBody(mediaType)
            builder.method(method, body)
        }
    }
    client.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) {
            val errBody = runCatching { resp.body?.string() }.getOrNull()
            throw GitHubIssuesException("HTTP ${resp.code}${errBody?.let { ": $it" } ?: ""}")
        }
        return resp.body?.string().orEmpty()
    }
}

/**
 * 从当前仓库读取 issue 列表、详情，并执行 issue 写操作。
 *
 * 平台由 remote URL 决定：GitHub / Gitee / GitCode（含 atomgit.com）。GitHub 用 Bearer，
 * Gitee 与 GitCode 用 access_token；字段差异由 [parseIssue] 等映射函数归一化。
 * issue 编号统一用字符串（Gitee 的 IKXYF8，GitHub/GitCode 的数字也按字符串处理）。
 * 写操作由上层工具层负责 ASK 权限确认，本类不做静默远端写入。
 */
@Singleton
class GitHubIssuesRepository @Inject constructor(
    private val client: OkHttpClient,
    private val credentialRepository: CredentialRepository,
    private val gitRepository: GitRepository
) {
    private companion object {
        const val PER_PAGE = 100
    }

    private val gson = Gson()

    /** 当前仓库的 issue 列表（已过滤掉 Pull Request 项）。 */
    suspend fun listIssues(state: String = "open"): Result<List<GitHubIssue>> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val body = execute(target, "/issues?state=$state&per_page=$PER_PAGE&sort=created&direction=desc")
            val array = parseJsonElement(gson, body) as? JsonArray ?: JsonArray()
            Result.success(parseIssues(array, target).filter { !it.isPullRequest })
        }

    /** 单条 issue 详情。 */
    suspend fun getIssue(number: String): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val body = execute(target, "/issues/$number")
            Result.success(parseIssue(parseJsonElement(gson, body), target))
        }

    /** 单条 issue 的评论列表。 */
    suspend fun getComments(number: String): Result<List<GitHubComment>> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val body = execute(target, "/issues/$number/comments?per_page=$PER_PAGE")
            val array = parseJsonElement(gson, body) as? JsonArray ?: JsonArray()
            Result.success(parseComments(array))
        }

    /** 创建 issue；labels/assignees 为名称/login 列表，均可为 null。 */
    suspend fun createIssue(
        title: String,
        body: String? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null
    ): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply {
                addProperty("title", title)
                if (!body.isNullOrBlank()) addProperty("body", body)
                when (target.platform) {
                    IssuePlatform.GITHUB -> {
                        if (!labels.isNullOrEmpty()) add("labels", gson.toJsonTree(labels))
                        if (!assignees.isNullOrEmpty()) add("assignees", gson.toJsonTree(assignees))
                    }
                    else -> {
                        // Gitee / GitCode：labels 逗号分隔，assignee 单值。
                        if (!labels.isNullOrEmpty()) addProperty("labels", labels.joinToString(","))
                        assignees?.firstOrNull()?.let { addProperty("assignee", it) }
                    }
                }
            }
            val resp = execute(target, "/issues", method = "POST", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 编辑 issue 的标题/正文（null 表示不改）。 */
    suspend fun editIssue(
        number: String,
        title: String? = null,
        body: String? = null
    ): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply {
                title?.let { addProperty("title", it) }
                body?.let { addProperty("body", it) }
            }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 设置 issue 状态："open" / "closed"；stateReason 仅 GitHub 生效。 */
    suspend fun setIssueState(number: String, state: String, stateReason: String? = null): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply {
                addProperty("state", state)
                if (stateReason != null && target.platform == IssuePlatform.GITHUB) {
                    addProperty("state_reason", stateReason)
                }
            }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 关闭 issue。 */
    suspend fun closeIssue(number: String, stateReason: String? = null): Result<GitHubIssue> =
        setIssueState(number, "closed", stateReason)

    /** 重开 issue。 */
    suspend fun reopenIssue(number: String): Result<GitHubIssue> = setIssueState(number, "open")

    /** 添加评论，返回创建的评论。 */
    suspend fun addComment(number: String, body: String): Result<GitHubComment> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply { addProperty("body", body) }
            val resp = execute(target, "/issues/$number/comments", method = "POST", jsonBody = gson.toJson(payload))
            Result.success(parseComment(parseJsonElement(gson, resp)))
        }

    /** 编辑评论，返回更新后的评论。 */
    suspend fun editComment(commentId: Long, body: String): Result<GitHubComment> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply { addProperty("body", body) }
            val resp = execute(target, "/issues/comments/$commentId", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseComment(parseJsonElement(gson, resp)))
        }

    /** 删除评论。 */
    suspend fun deleteComment(commentId: Long): Result<Unit> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            execute(target, "/issues/comments/$commentId", method = "DELETE")
            Result.success(Unit)
        }

    /** 为 issue 追加标签（按名称），返回更新后的 issue。 */
    suspend fun addLabels(number: String, labels: List<String>): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            if (target.platform == IssuePlatform.GITHUB) {
                val resp = execute(
                    target,
                    "/issues/$number/labels",
                    method = "POST",
                    jsonBody = gson.toJson(labels)
                )
                return@runCatchingIo Result.success(parseIssue(parseJsonElement(gson, resp), target))
            }
            // Gitee / GitCode 没有标签子资源：读出当前标签后整体 PATCH 合并。
            val current = parseIssue(parseJsonElement(gson, execute(target, "/issues/$number")), target)
            val merged = (current.labels.map { it.name } + labels).distinct()
            val payload = JsonObject().apply { addProperty("labels", merged.joinToString(",")) }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 从 issue 移除单个标签（按名称），返回更新后的 issue。 */
    suspend fun removeLabel(number: String, label: String): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            if (target.platform == IssuePlatform.GITHUB) {
                val encoded = java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")
                val resp = execute(target, "/issues/$number/labels/$encoded", method = "DELETE")
                return@runCatchingIo Result.success(parseIssue(parseJsonElement(gson, resp), target))
            }
            val current = parseIssue(parseJsonElement(gson, execute(target, "/issues/$number")), target)
            val remaining = current.labels.map { it.name }.filter { it != label }
            val payload = JsonObject().apply { addProperty("labels", remaining.joinToString(",")) }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 仓库贡献者 login 列表，供受理人选择；取不到时返回空列表。 */
    suspend fun getContributors(): Result<List<String>> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val body = execute(target, "/contributors?per_page=$PER_PAGE")
            val array = parseJsonElement(gson, body) as? JsonArray ?: JsonArray()
            val logins = array.mapNotNull { item ->
                (item as? JsonObject)?.get("login")?.takeIf { it.isJsonPrimitive }?.asString
            }.filter { it.isNotBlank() }
            Result.success(logins)
        }

    /** 设置 issue 受理人（空列表表示清空），返回更新后的 issue。 */
    suspend fun setAssignees(number: String, assignees: List<String>): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply {
                if (target.platform == IssuePlatform.GITHUB) {
                    add("assignees", gson.toJsonTree(assignees))
                } else {
                    addProperty("assignee", assignees.firstOrNull().orEmpty())
                }
            }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 仓库里程碑列表（open + closed）。 */
    suspend fun getMilestones(): Result<List<GitHubMilestone>> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val body = execute(target, "/milestones?state=all&per_page=$PER_PAGE")
            val array = parseJsonElement(gson, body) as? JsonArray ?: JsonArray()
            Result.success(parseMilestones(array))
        }

    /** 设置 issue 里程碑（null 表示清除）。 */
    suspend fun setMilestone(number: String, milestoneNumber: Long?): Result<GitHubIssue> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            val payload = JsonObject().apply {
                if (milestoneNumber == null) add("milestone", JsonNull.INSTANCE)
                else addProperty("milestone", milestoneNumber)
            }
            val resp = execute(target, "/issues/$number", method = "PATCH", jsonBody = gson.toJson(payload))
            Result.success(parseIssue(parseJsonElement(gson, resp), target))
        }

    /** 锁定/解锁 issue。Gitee / GitCode 没有对应接口，按成功处理，避免打断用户操作。 */
    suspend fun setIssueLock(number: String, locked: Boolean): Result<Unit> =
        runCatchingIo {
            val target = resolveTarget() ?: return@runCatchingIo errorResult()
            if (target.platform == IssuePlatform.GITHUB) {
                execute(target, "/issues/$number/lock", method = if (locked) "PUT" else "DELETE")
            }
            Result.success(Unit)
        }

    /** 当前仓库是否配置了对应平台的访问令牌（GitHub / Gitee / GitCode）。 */
    suspend fun hasGitHubToken(): Boolean {
        val platform = parseIssueRepo(gitRepository.getRepoUrl())?.first ?: return false
        return platform.hosts.any { host ->
            credentialRepository.findForHost(host)?.token?.isNotBlank() == true
        }
    }

    private suspend fun resolveTarget(): IssueTarget? =
        resolveIssueTarget(gitRepository, credentialRepository)

    private fun errorResult(): Result<Nothing> =
        Result.failure(GitHubIssuesException("issue unavailable: unsupported platform or token is missing"))

    private fun execute(
        target: IssueTarget,
        path: String,
        method: String = "GET",
        jsonBody: String? = null
    ): String = executeIssueRequest(client, target, path, method, jsonBody)

    /** 统一 IO 调度与异常包装：取消透传，其余异常转失败结果。 */
    private suspend fun <T> runCatchingIo(block: suspend () -> Result<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
