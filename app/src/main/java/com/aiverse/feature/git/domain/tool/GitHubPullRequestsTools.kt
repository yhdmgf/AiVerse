package com.aiverse.feature.git.domain.tool

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.feature.git.domain.GitHubIssuesRepository
import com.aiverse.feature.git.domain.GitHubPullRequestsRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "GitHubPullRequestsTools"

/** 通用失败映射：取消传播，其余包成 ToolResult.Error（带 code）。 */
private fun prFailure(e: Exception): ToolResult {
    if (e is CancellationException) throw e
    FileLogger.e(TAG, "GitHub PR 工具失败", e)
    val message = e.message.orEmpty()
    if (message.contains("token is missing", ignoreCase = true)) {
        return ToolResult.Error(
            "未配置 GitHub token：请让用户打开 Git 页右上角钥匙 → 凭据 → +，添加 github.com 的 token，然后重试。不要读取或猜测 token。",
            "GITHUB_TOKEN_MISSING"
        )
    }
    return ToolResult.Error(message.ifBlank { "GitHub PR 操作失败" }, "GITHUB_PR_ERROR")
}

/**
 * 列出当前仓库的 Pull Request（只读，OPEN，自动放行）。
 * 参数：state = open|closed|all（默认 open）。
 */
class ListGitHubPullRequestsTool @Inject constructor(
    private val repository: GitHubPullRequestsRepository
) : AgentTool() {
    override val name = "listPullRequests"
    override val description = "列出当前 GitHub 仓库的 Pull Request（只读）。返回编号、标题、分支、作者、合并状态与评论数。仅 GitHub 仓库且已配置 token 时可用；CI 状态需用 getPullRequest 查看。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "state" to ToolParameter(
            name = "state",
            type = ParameterType.STRING,
            description = "PR 状态：open（默认）/ closed / all。",
            required = false,
            enum = listOf("open", "closed", "all")
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val state = args["state"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "open"
        val result = repository.listPullRequests(state)
        return if (result.isSuccess) {
            val pulls = result.getOrThrow()
            ToolResult.Success(
                JsonObject(
                    mapOf(
                        "state" to JsonPrimitive(state),
                        "count" to JsonPrimitive(pulls.size),
                        "pullRequests" to Json.parseToJsonElement(Gson().toJson(pulls))
                    )
                )
            )
        } else {
            prFailure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}

/**
 * 获取单条 Pull Request 详情（只读，OPEN，自动放行）。
 * 参数：number = PR 编号（必填）。返回 PR 元数据、CI 检查汇总与全部评论。
 */
class GetGitHubPullRequestTool @Inject constructor(
    private val repository: GitHubPullRequestsRepository,
    private val issuesRepository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "getPullRequest"
    override val description = "获取当前 GitHub 仓库单条 Pull Request 的详情（只读）：元数据、分支、增删行统计、CI 检查汇总（含失败检查名与摘要）、提交记录、变更文件列表和评论。审查 PR 前可先用它确认变更范围与 CI 失败原因，再用 reviewPr 拉取 diff。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "number" to ToolParameter(
            name = "number",
            type = ParameterType.INTEGER,
            description = "Pull Request 编号（必填）。",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return ToolResult.Error("缺少必需参数：number", "MISSING_NUMBER")
        val result = repository.getPullRequest(number)
        if (result.isFailure) {
            return prFailure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
        val pr = result.getOrThrow()
        val checks = repository.getChecks(pr.head.sha).getOrNull()
        // PR 评论与 issue 评论共用 /issues/{number}/comments 接口。
        val comments = issuesRepository.getComments(number.toString()).getOrDefault(emptyList())
        val commits = repository.listPullRequestCommits(number).getOrDefault(emptyList())
        val files = repository.listPullRequestFiles(number).getOrDefault(emptyList())
        val gson = Gson()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "pullRequest" to Json.parseToJsonElement(gson.toJson(pr)),
                    "checks" to Json.parseToJsonElement(gson.toJson(checks)),
                    "commits" to Json.parseToJsonElement(gson.toJson(commits)),
                    "files" to Json.parseToJsonElement(gson.toJson(files)),
                    "commentCount" to JsonPrimitive(comments.size),
                    "comments" to Json.parseToJsonElement(gson.toJson(comments))
                )
            )
        )
    }
}

/**
 * 创建 Pull Request（写操作，ASK）。
 * 参数：title（必填）、head（必填，已推送的源分支）、base（必填，目标分支）、body（可选）、draft（可选）。
 */
class CreateGitHubPullRequestTool @Inject constructor(
    private val repository: GitHubPullRequestsRepository
) : AgentTool() {
    override val name = "createPullRequest"
    override val description = "在当前 GitHub 仓库创建 Pull Request（写操作，需用户确认）。head 为源分支，必须已推送到远程；未推送时先用 Bash 在工作区仓库执行 git push（凭证由容器 helper 自动注入，不要读取 token）。同仓库直接写分支名，fork 写 owner:branch。base 为目标分支。CI 或测试未通过时不要擅自创建。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "title" to ToolParameter(
            name = "title",
            type = ParameterType.STRING,
            description = "PR 标题（必填）。",
            required = true
        ),
        "head" to ToolParameter(
            name = "head",
            type = ParameterType.STRING,
            description = "源分支（必填，需已推送到远程）。",
            required = true
        ),
        "base" to ToolParameter(
            name = "base",
            type = ParameterType.STRING,
            description = "目标分支（必填，如 main）。",
            required = true
        ),
        "body" to ToolParameter(
            name = "body",
            type = ParameterType.STRING,
            description = "PR 描述（可选）。",
            required = false
        ),
        "draft" to ToolParameter(
            name = "draft",
            type = ParameterType.BOOLEAN,
            description = "是否创建为草稿 PR（可选，默认 false）。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val title = args["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val head = args["head"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val base = args["base"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认创建 Pull Request",
            summary = "AI 请求创建 PR：$title",
            details = "将在当前 GitHub 仓库创建 PR。\n标题：$title\n分支：$head → $base",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数：title", "MISSING_TITLE")
        val head = args["head"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数：head", "MISSING_HEAD")
        val base = args["base"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数：base", "MISSING_BASE")
        if (title.isBlank() || head.isBlank() || base.isBlank()) {
            return ToolResult.Error("title / head / base 不能为空", "INVALID_ARGUMENT")
        }
        val body = args["body"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        val draft = args["draft"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val result = repository.createPullRequest(title, head, base, body, draft)
        return if (result.isSuccess) {
            ToolResult.Success(
                JsonObject(mapOf("pullRequest" to Json.parseToJsonElement(Gson().toJson(result.getOrThrow()))))
            )
        } else {
            prFailure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}

/**
 * 更新 Pull Request（写操作，ASK）。action：close / reopen / merge / comment。
 * merge 可选 method（merge / squash / rebase）；comment 需 body。
 */
class UpdateGitHubPullRequestTool @Inject constructor(
    private val repository: GitHubPullRequestsRepository,
    private val issuesRepository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "updatePullRequest"
    override val description = "对当前 GitHub 仓库的 Pull Request 执行写操作（需用户确认）。action：close（关闭）/ reopen（重开）/ merge（合并）/ comment（评论）。merge 可用 method 指定 merge / squash / rebase；comment 需要 body。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "number" to ToolParameter(
            name = "number",
            type = ParameterType.INTEGER,
            description = "PR 编号（必填）。",
            required = true
        ),
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "要执行的操作（必填）。",
            required = true,
            enum = listOf("close", "reopen", "merge", "comment")
        ),
        "method" to ToolParameter(
            name = "method",
            type = ParameterType.STRING,
            description = "merge 时的合并方式：merge（默认）/ squash / rebase（可选）。",
            required = false,
            enum = listOf("merge", "squash", "rebase")
        ),
        "body" to ToolParameter(
            name = "body",
            type = ParameterType.STRING,
            description = "comment 时的评论内容（可选）。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val number = args["number"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val action = args["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val method = args["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val actionText = when (action) {
            "close" -> "关闭"
            "reopen" -> "重开"
            "merge" -> "合并" + if (method.isBlank()) "" else "（$method）"
            "comment" -> "评论"
            else -> action
        }
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认$actionText Pull Request",
            summary = "AI 请求$actionText PR #$number",
            details = "将对 PR #$number 执行：$actionText。写操作会使用你本地配置的 GitHub token。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return ToolResult.Error("缺少必需参数：number", "MISSING_NUMBER")
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：action", "MISSING_ACTION")
        val gson = Gson()
        return when (action) {
            "close", "reopen" -> {
                val state = if (action == "close") "closed" else "open"
                repository.setPullRequestState(number, state).fold(
                    onSuccess = { pull ->
                        ToolResult.Success(
                            JsonObject(mapOf("pullRequest" to Json.parseToJsonElement(gson.toJson(pull))))
                        )
                    },
                    onFailure = { prFailure(it as? Exception ?: Exception("未知错误")) }
                )
            }
            "merge" -> {
                val method = args["method"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                repository.mergePullRequest(number, method.ifBlank { "merge" }).fold(
                    onSuccess = { result ->
                        ToolResult.Success(
                            JsonObject(mapOf("mergeResult" to Json.parseToJsonElement(gson.toJson(result))))
                        )
                    },
                    onFailure = { prFailure(it as? Exception ?: Exception("未知错误")) }
                )
            }
            "comment" -> {
                val body = args["body"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolResult.Error("comment 缺少必需参数：body", "MISSING_BODY")
                if (body.isBlank()) return ToolResult.Error("body 不能为空", "INVALID_BODY")
                issuesRepository.addComment(number.toString(), body).fold(
                    onSuccess = { comment ->
                        ToolResult.Success(
                            JsonObject(mapOf("comment" to Json.parseToJsonElement(gson.toJson(comment))))
                        )
                    },
                    onFailure = { prFailure(it as? Exception ?: Exception("未知错误")) }
                )
            }
            else -> ToolResult.Error("不支持的 action：$action", "INVALID_ACTION")
        }
    }
}
