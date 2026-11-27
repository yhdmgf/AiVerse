package com.aiverse.feature.git.domain

import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** GitHub Actions 日志请求失败（网络、HTTP 状态码、目标不合法）。 */
class GitHubActionsException(message: String) : Exception(message)

/**
 * 拉取 GitHub Actions 失败 job 的运行日志尾部，供 Agent 直接定位 CI 报错。
 *
 * 现有 [GitHubPullRequestsRepository.getChecks] 只给检查名与摘要，拿不到编译器输出的完整报错；
 * 这里补上 `actions/runs` / `actions/jobs` / `/logs` 三个接口，并按行截断，避免把
 * 几十 MB 的构建日志塞进模型上下文。
 */
@Singleton
class GitHubActionsLogsRepository @Inject constructor(
    private val client: OkHttpClient,
    private val credentialRepository: CredentialRepository,
    private val gitRepository: GitRepository,
    private val pullRequestsRepository: GitHubPullRequestsRepository
) {
    private companion object {
        const val API_BASE = "https://api.github.com/repos"
        const val MAX_RUNS = 2
        const val MAX_JOBS_PER_RUN = 5
        val FAILED_CONCLUSIONS = setOf("failure", "timed_out", "startup_failure")
    }

    private val gson = Gson()

    /** 单个失败 job 的日志尾部与失败步骤。 */
    data class FailedJob(
        val id: Long,
        val name: String?,
        val conclusion: String?,
        val htmlUrl: String?,
        val failedSteps: List<String>,
        val logTail: String?,
        val logError: String?
    )

    /** 一次失败的 workflow run。 */
    data class FailedRun(
        val id: Long,
        val name: String?,
        val conclusion: String?,
        val htmlUrl: String?,
        val jobs: List<FailedJob>
    )

    /**
     * 查询某个提交（或 PR 的 head 提交）上失败的 Actions 运行及日志尾部。
     *
     * @param pullNumber PR 编号；与 [sha] 至少给一个，同时给时以 [sha] 为准。
     * @param sha 目标提交 SHA。
     * @param maxLines 每个失败 job 保留的日志行数（已做上限保护）。
     */
    suspend fun failedLogs(
        pullNumber: Long? = null,
        sha: String? = null,
        maxLines: Int = 200
    ): Result<List<FailedRun>> = withContext(Dispatchers.IO) {
        try {
            val target = resolveGitHubTarget(gitRepository, credentialRepository)
                ?: return@withContext Result.failure(
                    GitHubActionsException("仅支持 github.com 仓库，且需要已配置 github.com 的 token")
                )
            val commit = sha?.trim()?.takeIf { it.isNotBlank() }
                ?: pullNumber?.let { number ->
                    pullRequestsRepository.getPullRequest(number).getOrElse { throw it }.head.sha
                        .takeIf { it.isNotBlank() }
                        ?: throw GitHubActionsException("PR #$number 的 head 提交为空")
                }
                ?: return@withContext Result.failure(
                    GitHubActionsException("需要提供 PR 编号或提交 SHA")
                )
            val lineLimit = maxLines.coerceIn(20, 500)

            val runsUrl = "$API_BASE/${target.owner}/${target.repo}/actions/runs?head_sha=$commit&per_page=20"
            val runs = gson.fromJson(get(runsUrl, target.token), RunsResponse::class.java)
                ?.workflowRuns.orEmpty()
                .filter { it.conclusion.orEmpty() in FAILED_CONCLUSIONS }
                .take(MAX_RUNS)

            val failedRuns = runs.map { run ->
                val jobsUrl = "$API_BASE/${target.owner}/${target.repo}/actions/runs/${run.id}/jobs?per_page=100"
                val jobs = gson.fromJson(get(jobsUrl, target.token), JobsResponse::class.java)
                    ?.jobs.orEmpty()
                    .filter { it.conclusion.orEmpty() in FAILED_CONCLUSIONS }
                    .take(MAX_JOBS_PER_RUN)
                FailedRun(
                    id = run.id,
                    name = run.name,
                    conclusion = run.conclusion,
                    htmlUrl = run.htmlUrl,
                    jobs = jobs.map { job ->
                        val failedSteps = job.steps.orEmpty()
                            .filter { it.conclusion.orEmpty() in FAILED_CONCLUSIONS }
                            .mapNotNull { it.name }
                        val logUrl = "$API_BASE/${target.owner}/${target.repo}/actions/jobs/${job.id}/logs"
                        var tail: String? = null
                        var logError: String? = null
                        try {
                            tail = fetchLogTail(logUrl, target.token, lineLimit)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logError = e.message ?: "日志下载失败"
                        }
                        FailedJob(
                            id = job.id,
                            name = job.name,
                            conclusion = job.conclusion,
                            htmlUrl = job.htmlUrl,
                            failedSteps = failedSteps,
                            logTail = tail,
                            logError = logError
                        )
                    }
                )
            }
            Result.success(failedRuns)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(url: String, token: String): String {
        client.newCall(request(url, token)).execute().use { response ->
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull()
                throw GitHubActionsException("HTTP ${response.code}${body?.let { ": ${it.take(300)}" } ?: ""}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /** 只保留日志最后 [maxLines] 行：Android 构建日志常有几十 MB，整段读进内存会 OOM。 */
    private fun fetchLogTail(url: String, token: String, maxLines: Int): String {
        client.newCall(request(url, token)).execute().use { response ->
            if (!response.isSuccessful) {
                throw GitHubActionsException("日志下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw GitHubActionsException("日志下载失败：响应为空")
            return readTail(body.byteStream(), maxLines)
        }
    }

    private fun readTail(stream: InputStream, maxLines: Int): String {
        val deque = java.util.ArrayDeque<String>(maxLines)
        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (deque.size == maxLines) deque.removeFirst()
                deque.addLast(line)
            }
        }
        return deque.joinToString("\n")
    }

    private fun request(url: String, token: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer $token")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .get()
        .build()

    private data class RunsResponse(
        @SerializedName("workflow_runs") val workflowRuns: List<RunDto>? = null
    )

    private data class RunDto(
        val id: Long = 0,
        val name: String? = null,
        val conclusion: String? = null,
        @SerializedName("html_url") val htmlUrl: String? = null
    )

    private data class JobsResponse(
        val jobs: List<JobDto>? = null
    )

    private data class JobDto(
        val id: Long = 0,
        val name: String? = null,
        val conclusion: String? = null,
        val steps: List<StepDto>? = null,
        @SerializedName("html_url") val htmlUrl: String? = null
    )

    private data class StepDto(
        val name: String? = null,
        val conclusion: String? = null
    )

}
