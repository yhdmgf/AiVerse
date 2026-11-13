package com.aiverse.feature.git.domain.tool

import com.aiverse.feature.git.domain.GitHubIssuesRepository
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.core.util.FileLogger
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "GitHubIssuesTools"

/** 解析 JSON 数组为字符串列表；非数组或元素非字符串时返回空列表。 */
private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> {
    val arr = element as? JsonArray ?: return emptyList()
    return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
}

/** 通用失败映射：取消传播，其余包成 ToolResult.Error（带 code）。 */
private fun failure(e: Exception): ToolResult {
    if (e is CancellationException) throw e
    FileLogger.e(TAG, "GitHub issue 工具失败", e)
    return ToolResult.Error(e.message ?: "GitHub issue 操作失败", "GITHUB_ISSUE_ERROR")
}

/**
 * 列出当前仓库的 GitHub issue（只读，OPEN，自动放行）。
 * 参数：state = open|closed（默认 open）。
 */
class ListGitHubIssuesTool @Inject constructor(
    private val repository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "listIssues"
    override val description = "列出当前 GitHub 仓库的 issue 列表（只读）。仅 GitHub 仓库且已配置 token 时可用，否则返回失败。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "state" to ToolParameter(
            name = "state",
            type = ParameterType.STRING,
            description = "issue 状态：open（默认）/ closed。",
            required = false,
            enum = listOf("open", "closed")
        )
    )

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        val state = args["state"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "open"
        val result = repository.listIssues(state)
        return if (result.isSuccess) {
            val gson = Gson()
            val issues = result.getOrThrow()
            ToolResult.Success(JsonObject(mapOf(
                "state" to JsonPrimitive(state),
                "count" to JsonPrimitive(issues.size),
                "issues" to Json.parseToJsonElement(gson.toJson(issues))
            )))
        } else {
            failure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}

/**
 * 获取单条 GitHub issue 详情（只读，OPEN，自动放行）。
 * 参数：number = issue 编号（必填）。
 */
class GetGitHubIssueTool @Inject constructor(
    private val repository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "getIssue"
    override val description = "获取当前 GitHub 仓库单条 issue 的详情，含正文与全部评论（只读）。返回 issue、comments（评论列表）、commentCount（评论数）。仅 GitHub 仓库且已配置 token 时可用。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "number" to ToolParameter(
            name = "number",
            type = ParameterType.INTEGER,
            description = "issue 编号（必填）。",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return ToolResult.Error("缺少必需参数：number", "MISSING_NUMBER")
        val result = repository.getIssue(number)
        return if (result.isSuccess) {
            val gson = Gson()
            // 默认同时读取全部评论，避免 AI 需要额外调用才能看到评论区
            val commentsResult = repository.getComments(number)
            val comments = commentsResult.getOrDefault(emptyList())
            ToolResult.Success(JsonObject(mapOf(
                "issue" to Json.parseToJsonElement(gson.toJson(result.getOrThrow())),
                "commentCount" to JsonPrimitive(comments.size),
                "comments" to Json.parseToJsonElement(gson.toJson(comments))
            )))
        } else {
            failure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}

/**
 * 创建 GitHub issue（写操作，ASK）。
 * 参数：title（必填）、body（可选）、labels（可选，名称数组）、assignees（可选，login 数组）。
 */
class CreateGitHubIssueTool @Inject constructor(
    private val repository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "createIssue"
    override val description = "在当前 GitHub 仓库创建一个 issue（写操作，需用户确认）。仅 GitHub 仓库且已配置 token 时可用。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "title" to ToolParameter(
            name = "title",
            type = ParameterType.STRING,
            description = "issue 标题（必填）。",
            required = true
        ),
        "body" to ToolParameter(
            name = "body",
            type = ParameterType.STRING,
            description = "issue 正文（可选）。",
            required = false
        ),
        "labels" to ToolParameter(
            name = "labels",
            type = ParameterType.ARRAY,
            description = "标签名称列表（可选）。",
            required = false
        ),
        "assignees" to ToolParameter(
            name = "assignees",
            type = ParameterType.ARRAY,
            description = "受理人 login 列表（可选）。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val title = args["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认创建 GitHub issue",
            summary = "AI 请求创建 issue：$title",
            details = "将在当前 GitHub 仓库创建 issue。\n标题：$title",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数：title", "MISSING_TITLE")
        if (title.isBlank()) return ToolResult.Error("title 不能为空", "INVALID_TITLE")
        val body = args["body"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        val labels = stringList(args["labels"]).ifEmpty { null }
        val assignees = stringList(args["assignees"]).ifEmpty { null }
        val result = repository.createIssue(title, body, labels, assignees)
        return if (result.isSuccess) {
            val gson = Gson()
            ToolResult.Success(JsonObject(mapOf(
                "issue" to Json.parseToJsonElement(gson.toJson(result.getOrThrow()))
            )))
        } else {
            failure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}

/**
 * 更新 GitHub issue（写操作，ASK）。用 action 区分操作类型：
 * close / reopen / edit / addComment / editComment / deleteComment / addLabels / removeLabel /
 * setAssignees / setMilestone / lock / unlock。
 */
class UpdateGitHubIssueTool @Inject constructor(
    private val repository: GitHubIssuesRepository
) : AgentTool() {
    override val name = "updateIssue"
    override val description =
        "对当前 GitHub 仓库的 issue 执行写操作（需用户确认）。action 取值：close（关闭）/reopen（重开）/" +
            "edit（改标题正文）/addComment（加评论）/editComment（改评论）/deleteComment（删评论）/" +
            "addLabels（加标签）/removeLabel（删标签）/setAssignees（设受理人）/setMilestone（设里程碑，" +
            "milestone=null 清除）/lock（锁定）/unlock（解锁）。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "number" to ToolParameter(
            name = "number",
            type = ParameterType.INTEGER,
            description = "issue 编号（必填）。",
            required = true
        ),
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "要执行的操作（必填）。",
            required = true,
            enum = listOf(
                "close", "reopen", "edit", "addComment", "editComment", "deleteComment",
                "addLabels", "removeLabel", "setAssignees", "setMilestone", "lock", "unlock"
            )
        ),
        "title" to ToolParameter(
            name = "title",
            type = ParameterType.STRING,
            description = "edit 时的新标题（可选）。",
            required = false
        ),
        "body" to ToolParameter(
            name = "body",
            type = ParameterType.STRING,
            description = "edit 时的新正文 / addComment·editComment 时的评论内容（可选）。",
            required = false
        ),
        "labels" to ToolParameter(
            name = "labels",
            type = ParameterType.ARRAY,
            description = "addLabels 时的标签名称列表（可选）。",
            required = false
        ),
        "assignees" to ToolParameter(
            name = "assignees",
            type = ParameterType.ARRAY,
            description = "setAssignees 时的受理人 login 列表（可选）。",
            required = false
        ),
        "milestone" to ToolParameter(
            name = "milestone",
            type = ParameterType.INTEGER,
            description = "setMilestone 时的里程碑编号；缺省表示清除里程碑（可选）。",
            required = false
        ),
        "commentId" to ToolParameter(
            name = "commentId",
            type = ParameterType.INTEGER,
            description = "editComment / deleteComment 时的评论 id（可选）。",
            required = false
        ),
        "lock" to ToolParameter(
            name = "lock",
            type = ParameterType.BOOLEAN,
            description = "lock / unlock 时的目标状态（可选，unlock 时忽略）。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val number = args["number"]?.jsonPrimitive?.contentOrNull ?: "?"
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: "?"
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认更新 GitHub issue",
            summary = "AI 请求对 issue #$number 执行：$action",
            details = "操作：$action（issue #$number）\n将在当前 GitHub 仓库执行该写操作。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return ToolResult.Error("缺少必需参数：number", "MISSING_NUMBER")
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("缺少必需参数：action", "MISSING_ACTION")

        val result: Result<*> = when (action) {
            "close" -> repository.closeIssue(number)
            "reopen" -> repository.reopenIssue(number)
            "edit" -> repository.editIssue(
                number,
                title = args["title"]?.jsonPrimitive?.contentOrNull,
                body = args["body"]?.jsonPrimitive?.contentOrNull
            )
            "addComment" -> {
                val body = args["body"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolResult.Error("addComment 需要 body", "MISSING_BODY")
                repository.addComment(number, body)
            }
            "editComment" -> {
                val commentId = args["commentId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: return ToolResult.Error("editComment 需要 commentId", "MISSING_COMMENT_ID")
                val body = args["body"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolResult.Error("editComment 需要 body", "MISSING_BODY")
                repository.editComment(commentId, body)
            }
            "deleteComment" -> {
                val commentId = args["commentId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: return ToolResult.Error("deleteComment 需要 commentId", "MISSING_COMMENT_ID")
                repository.deleteComment(commentId)
            }
            "addLabels" -> {
                val labels = stringList(args["labels"])
                if (labels.isEmpty()) return ToolResult.Error("addLabels 需要 labels", "MISSING_LABELS")
                repository.addLabels(number, labels)
            }
            "removeLabel" -> {
                val labels = stringList(args["labels"])
                if (labels.isEmpty()) return ToolResult.Error("removeLabel 需要 labels 中的标签名", "MISSING_LABELS")
                // 支持一次移除一个；取第一个
                repository.removeLabel(number, labels.first())
            }
            "setAssignees" -> repository.setAssignees(number, stringList(args["assignees"]))
            "setMilestone" -> {
                val milestone = args["milestone"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                repository.setMilestone(number, milestone)
            }
            "lock" -> repository.setIssueLock(number, true)
            "unlock" -> repository.setIssueLock(number, false)
            else -> return ToolResult.Error("未知 action：$action", "INVALID_ACTION")
        }

        return if (result.isSuccess) {
            val gson = Gson()
            val value = result.getOrNull()
            ToolResult.Success(JsonObject(mapOf(
                "action" to JsonPrimitive(action),
                "result" to (value?.let { Json.parseToJsonElement(gson.toJson(it)) }
                    ?: JsonPrimitive("ok"))
            )))
        } else {
            failure(result.exceptionOrNull() as? Exception ?: Exception("未知错误"))
        }
    }
}
