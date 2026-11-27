package com.aiverse.feature.git.domain.tool

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.feature.git.domain.GitHubActionsLogsRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "GitHubActionsTools"

/**
 * 下载 GitHub Actions 失败 job 的日志尾部（只读，自动放行）。
 *
 * 参数二选一：PR 编号（自动取 head SHA）或提交 SHA；`lines` 控制每个失败 job 保留的行数。
 * CI 红灯后用它直接拿编译器报错，不必让用户手工复制日志。
 */
class GetFailedCheckLogsTool @Inject constructor(
    private val repository: GitHubActionsLogsRepository
) : AgentTool() {
    override val name = "getFailedCheckLogs"
    override val description = "下载当前 GitHub 仓库 Actions 失败 job 的日志尾部（只读）：按 PR 编号或提交 SHA 定位失败的 workflow run，返回失败的 job、失败步骤与日志末尾内容。CI 红灯后先用它拿报错，再定位代码；仅 github.com 仓库且已配置 token 时可用。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "number" to ToolParameter(
            name = "number",
            type = ParameterType.INTEGER,
            description = "Pull Request 编号（可选），会自动取其 head 提交的 CI 结果。与 sha 至少提供一个。",
            required = false
        ),
        "sha" to ToolParameter(
            name = "sha",
            type = ParameterType.STRING,
            description = "目标提交 SHA（可选）。与 number 同时提供时以 sha 为准。",
            required = false
        ),
        "lines" to ToolParameter(
            name = "lines",
            type = ParameterType.INTEGER,
            description = "每个失败 job 保留的日志行数，默认 200，范围 20–500。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val sha = args["sha"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (number == null && sha == null) {
            return ToolResult.Error("缺少参数：number（PR 编号）与 sha（提交）至少提供一个", "MISSING_TARGET")
        }
        val lines = args["lines"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 200

        val result = repository.failedLogs(pullNumber = number, sha = sha, maxLines = lines)
        if (result.isFailure) {
            val error = result.exceptionOrNull() as? Exception ?: Exception("未知错误")
            if (error is CancellationException) throw error
            FileLogger.e(TAG, "拉取 GitHub Actions 日志失败", error)
            val message = error.message.orEmpty()
            if (message.contains("token", ignoreCase = true)) {
                return ToolResult.Error(
                    "未配置 GitHub token：请让用户打开 Git 页右上角钥匙 → 凭据 → +，添加 github.com 的 token 后重试。不要读取或猜测 token。",
                    "GITHUB_TOKEN_MISSING"
                )
            }
            return ToolResult.Error(message.ifBlank { "拉取 CI 日志失败" }, "GITHUB_ACTIONS_ERROR")
        }
        val runs = result.getOrThrow()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "count" to JsonPrimitive(runs.size),
                    "runs" to Json.parseToJsonElement(Gson().toJson(runs))
                )
            )
        )
    }
}
