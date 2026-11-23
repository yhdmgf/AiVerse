package com.aiverse.feature.git.domain

import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.aiverse.feature.git.domain.model.GitHubCheckRun
import com.aiverse.feature.git.domain.model.GitHubCheckSummary
import com.aiverse.feature.git.domain.model.GitHubCommitDetail
import com.aiverse.feature.git.domain.model.GitHubMergeResult
import com.aiverse.feature.git.domain.model.GitHubPullRequest
import com.aiverse.feature.git.domain.model.GitHubPullRequestCommit
import com.aiverse.feature.git.domain.model.GitHubPullRequestFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** GitHub PR 请求失败（网络、HTTP 状态码、解析、目标不合法）。 */
class GitHubPullRequestsException(message: String) : Exception(message)

/**
 * 从 GitHub REST API 拉取当前仓库的 Pull Request 列表、详情与 CI 检查状态。
 *
 * 目标解析与 HTTP 执行复用 issue 仓库的 [resolveGitHubTarget] / [executeGitHubRequest]：
 * 非 github.com remote 或未配置 token 时返回 [Result.failure]，不会静默失败。
 */
@Singleton
class GitHubPullRequestsRepository @Inject constructor(
    private val client: OkHttpClient,
    private val credentialRepository: CredentialRepository,
    private val gitRepository: GitRepository
) {
    private companion object {
        const val API_BASE = "https://api.github.com/repos"
        const val PER_PAGE = 100
        /** CI 检查摘要截断长度：避免把整段日志塞进列表/工具结果。 */
        const val MAX_SUMMARY_CHARS = 300
        /** 按 SHA 取文件内容时的字节上限：超过则降级为「文件过大」，不拉全量文本。 */
        const val MAX_RAW_FILE_BYTES = 2L * 1024 * 1024
        val VALID_STATES = setOf("open", "closed", "all")
    }

    private val gson = Gson()

    /** 当前仓库的 PR 列表。[state] 取 open / closed / all，非法值按 open 处理。 */
    suspend fun listPullRequests(state: String = "open"): Result<List<GitHubPullRequest>> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget() ?: return@withContext errorResult()
                val normalized = state.takeIf { it in VALID_STATES } ?: "open"
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls?state=$normalized&per_page=$PER_PAGE"
                val body = execute(url, target.token)
                val type = object : TypeToken<List<GitHubPullRequest>>() {}.type
                Result.success(gson.fromJson<List<GitHubPullRequest>>(body, type) ?: emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 单条 PR 详情。 */
    suspend fun getPullRequest(number: Long): Result<GitHubPullRequest> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls/$number"
                val pull = gson.fromJson(execute(url, target.token), GitHubPullRequest::class.java)
                    ?: throw GitHubPullRequestsException("Empty pull request response")
                Result.success(pull)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 某提交的 CI 汇总：优先读 check-runs；没有 check-runs 时回退旧版 commit status。
     * [sha] 取 PR 的 head.sha。
     */
    suspend fun getChecks(sha: String): Result<GitHubCheckSummary> =
        withContext(Dispatchers.IO) {
            try {
                if (sha.isBlank()) return@withContext Result.success(GitHubCheckSummary.EMPTY)
                val target = resolveTarget() ?: return@withContext errorResult()
                val repoBase = "$API_BASE/${target.owner}/${target.repo}"
                val runsBody = execute("$repoBase/commits/$sha/check-runs?per_page=$PER_PAGE", target.token)
                val runsResponse = gson.fromJson(runsBody, CheckRunsResponse::class.java)
                    ?: CheckRunsResponse()
                val runs = runsResponse.checkRuns.map { dto ->
                    GitHubCheckRun(
                        name = dto.name.ifBlank { dto.output?.title ?: "" },
                        status = dto.status,
                        conclusion = dto.conclusion,
                        htmlUrl = dto.htmlUrl,
                        summary = listOfNotNull(dto.output?.title, dto.output?.summary)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                            .take(MAX_SUMMARY_CHARS)
                            .takeIf { it.isNotBlank() }
                    )
                }
                if (runs.isNotEmpty()) {
                    return@withContext Result.success(summarize(runs))
                }

                val statusBody = execute("$repoBase/commits/$sha/status", target.token)
                val statusResponse = gson.fromJson(statusBody, CombinedStatusResponse::class.java)
                    ?: CombinedStatusResponse()
                val converted = statusResponse.statuses.map { status ->
                    when (status.state) {
                        "success" -> GitHubCheckRun(
                            name = status.context,
                            status = "completed",
                            conclusion = "success",
                            htmlUrl = status.targetUrl,
                            summary = status.description
                        )
                        "failure", "error" -> GitHubCheckRun(
                            name = status.context,
                            status = "completed",
                            conclusion = "failure",
                            htmlUrl = status.targetUrl,
                            summary = status.description
                        )
                        "neutral" -> GitHubCheckRun(
                            name = status.context,
                            status = "completed",
                            conclusion = "success",
                            htmlUrl = status.targetUrl,
                            summary = status.description
                        )
                        else -> GitHubCheckRun(
                            name = status.context,
                            status = "pending",
                            conclusion = null,
                            htmlUrl = status.targetUrl,
                            summary = status.description
                        )
                    }
                }
                Result.success(
                    if (converted.isEmpty()) GitHubCheckSummary.EMPTY
                    else summarize(converted, statusResponse.state.takeIf { it.isNotBlank() })
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 创建 PR。head 为源分支（同仓库可直接写分支名，fork 用 owner:branch），base 为目标分支。 */
    suspend fun createPullRequest(
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false
    ): Result<GitHubPullRequest> =
        withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) throw GitHubPullRequestsException("title is blank")
                if (head.isBlank() || base.isBlank()) throw GitHubPullRequestsException("head/base is blank")
                if (head == base) throw GitHubPullRequestsException("head and base are the same")
                val target = resolveTarget() ?: return@withContext errorResult()
                val payload = HashMap<String, Any>()
                payload["title"] = title
                payload["head"] = head
                payload["base"] = base
                if (!body.isNullOrBlank()) payload["body"] = body
                if (draft) payload["draft"] = true
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls"
                val response = execute(url, target.token, method = "POST", jsonBody = gson.toJson(payload))
                val pull = gson.fromJson(response, GitHubPullRequest::class.java)
                    ?: throw GitHubPullRequestsException("Empty pull request response")
                Result.success(pull)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 关闭（state=closed）或重开（state=open）PR。 */
    suspend fun setPullRequestState(number: Long, state: String): Result<GitHubPullRequest> =
        withContext(Dispatchers.IO) {
            try {
                if (number <= 0) throw GitHubPullRequestsException("invalid pull request number")
                val normalized = state.takeIf { it in setOf("open", "closed") }
                    ?: throw GitHubPullRequestsException("invalid pull request state")
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls/$number"
                val response = execute(
                    url,
                    target.token,
                    method = "PATCH",
                    jsonBody = gson.toJson(mapOf("state" to normalized))
                )
                val pull = gson.fromJson(response, GitHubPullRequest::class.java)
                    ?: throw GitHubPullRequestsException("Empty pull request response")
                Result.success(pull)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 关闭 PR。 */
    suspend fun closePullRequest(number: Long): Result<GitHubPullRequest> =
        setPullRequestState(number, "closed")

    /** 重开 PR。 */
    suspend fun reopenPullRequest(number: Long): Result<GitHubPullRequest> =
        setPullRequestState(number, "open")

    /** 合并 PR。[method] 取 merge / squash / rebase，非法值按 merge。 */
    suspend fun mergePullRequest(number: Long, method: String = "merge"): Result<GitHubMergeResult> =
        withContext(Dispatchers.IO) {
            try {
                if (number <= 0) throw GitHubPullRequestsException("invalid pull request number")
                val normalized = method.takeIf { it in setOf("merge", "squash", "rebase") } ?: "merge"
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls/$number/merge"
                val response = execute(
                    url,
                    target.token,
                    method = "PUT",
                    jsonBody = gson.toJson(mapOf("merge_method" to normalized))
                )
                val result = gson.fromJson(response, GitHubMergeResult::class.java)
                    ?: throw GitHubPullRequestsException("Empty merge response")
                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** PR 的提交记录（最多 100 条）。 */
    suspend fun listPullRequestCommits(number: Long): Result<List<GitHubPullRequestCommit>> =
        withContext(Dispatchers.IO) {
            try {
                if (number <= 0) throw GitHubPullRequestsException("invalid pull request number")
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls/$number/commits?per_page=$PER_PAGE"
                val body = execute(url, target.token)
                val type = object : TypeToken<List<GitHubPullRequestCommit>>() {}.type
                Result.success(gson.fromJson<List<GitHubPullRequestCommit>>(body, type) ?: emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** PR 变更的文件列表（最多 100 条，不含 patch 正文）。 */
    suspend fun listPullRequestFiles(number: Long): Result<List<GitHubPullRequestFile>> =
        withContext(Dispatchers.IO) {
            try {
                if (number <= 0) throw GitHubPullRequestsException("invalid pull request number")
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/pulls/$number/files?per_page=$PER_PAGE"
                val body = execute(url, target.token)
                val type = object : TypeToken<List<GitHubPullRequestFile>>() {}.type
                Result.success(gson.fromJson<List<GitHubPullRequestFile>>(body, type) ?: emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 单个提交的完整详情（含每个文件的 patch 正文），供 PR 提交区点开提交后看改动文件与逐行 diff。
     * [sha] 取 PR 提交列表里的提交 sha。
     */
    suspend fun getCommit(sha: String): Result<GitHubCommitDetail> =
        withContext(Dispatchers.IO) {
            try {
                if (sha.isBlank()) throw GitHubPullRequestsException("invalid commit sha")
                val target = resolveTarget() ?: return@withContext errorResult()
                val url = "$API_BASE/${target.owner}/${target.repo}/commits/$sha"
                val detail = gson.fromJson(execute(url, target.token), GitHubCommitDetail::class.java)
                    ?: throw GitHubPullRequestsException("Empty commit response")
                Result.success(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 取某 SHA 下某文件路径的原始文本内容，作为 PR 文件 diff 的 contentProvider。
     * 用 contents API + `Accept: application/vnd.github.raw` 拿原始文本（避开 execute 的 JSON Accept）。
     * 文件在该 SHA 下不存在（404）时返回空串，供 computeDiff 按全新增/全删除呈现；
     * 超过 [MAX_RAW_FILE_BYTES] 抛 [GitOutputTooLargeException]，走「文件过大」降级。
     */
    suspend fun fetchFileContentAt(sha: String, path: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (sha.isBlank() || path.isBlank()) return@withContext Result.success("")
                val target = resolveTarget() ?: return@withContext errorResult()
                // 用 HttpUrl.Builder 逐段编码 path，保留「/」分隔符；OkHttp 按 RFC 3986 编码，
                // 空格→%20、#→%23、中文→%XX，避开 URLEncoder 的 form 语义（空格→+）导致取不到。
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("api.github.com")
                    .addPathSegment("repos")
                    .addPathSegment(target.owner)
                    .addPathSegment(target.repo)
                    .addPathSegment("contents")
                    .addPathSegment(path)
                    .addQueryParameter("ref", sha)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.raw")
                    .header("Authorization", "Bearer ${target.token}")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 404 -> Result.success("")
                        !resp.isSuccessful -> throw GitHubPullRequestsException(
                            "HTTP ${resp.code} fetching path at $sha"
                        )
                        else -> {
                            // 流式读取并在累计超过上限时立刻中断，不整段读入内存；
                            // 不依赖 content-length（gzip 透传时常为 -1）。
                            val body = resp.body ?: return@use Result.success("")
                            val buffer = okio.Buffer()
                            body.source().use { source ->
                                while (true) {
                                    val read = source.read(buffer, MAX_RAW_FILE_BYTES + 1 - buffer.size)
                                    if (read == -1L) break
                                    if (buffer.size > MAX_RAW_FILE_BYTES) throw GitOutputTooLargeException()
                                }
                            }
                            Result.success(buffer.readUtf8())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 当前仓库是否配置了 GitHub token（与 issue 仓库同源）。 */
    suspend fun hasGitHubToken(): Boolean =
        credentialRepository.findForHost("github.com")?.token?.isNotBlank() == true

    private fun summarize(runs: List<GitHubCheckRun>, combinedState: String? = null): GitHubCheckSummary =
        GitHubCheckSummary(
            total = runs.size,
            success = runs.count { it.outcome == "success" },
            failure = runs.count { it.outcome == "failure" },
            pending = runs.count { it.outcome == "pending" },
            runs = runs,
            combinedState = combinedState
        )

    private suspend fun resolveTarget(): GitHubTarget? =
        resolveGitHubTarget(gitRepository, credentialRepository)

    private fun execute(
        url: String,
        token: String,
        method: String = "GET",
        jsonBody: String? = null
    ): String = executeGitHubRequest(client, url, token, method, jsonBody)

    private fun errorResult(): Result<Nothing> = Result.failure(
        GitHubPullRequestsException("GitHub pull requests unavailable: repository is not GitHub or token is missing")
    )

    private data class CheckRunsResponse(
        @SerializedName("check_runs") val checkRuns: List<CheckRunDto> = emptyList()
    )

    private data class CheckRunDto(
        val name: String = "",
        val status: String = "",
        val conclusion: String? = null,
        @SerializedName("html_url") val htmlUrl: String = "",
        val output: CheckOutputDto? = null
    )

    private data class CheckOutputDto(
        val title: String? = null,
        val summary: String? = null
    )

    private data class CombinedStatusResponse(
        val state: String = "",
        val statuses: List<StatusDto> = emptyList()
    )

    private data class StatusDto(
        val context: String = "",
        val state: String = "",
        val description: String? = null,
        @SerializedName("target_url") val targetUrl: String = ""
    )
}
