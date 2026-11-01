package com.aiverse.feature.agent.domain.tool.remote

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.container.RemoteExploreClient
import com.aiverse.feature.agent.domain.container.friendlySshError
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.feature.agent.domain.tool.explorer.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshMvTool"

/**
 * 移动或重命名远程文件/目录（mv）——远程文件管理。
 *
 * 配置好服务器即可在任意对话中使用（不依赖当前激活镜像/工作目录）。`sudo=true` 可操作 /root 等受限路径
 * （需服务器免密 sudo）。移动/重命名属敏感写操作，执行前需用户确认。
 */
class SshMvTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshMv"
    override val description =
        "移动或重命名远程文件/目录（mv）。server 可选（名称或 id，缺省第一个已配置的）；from 为源绝对路径；to 为目标绝对路径；" +
            "sudo=true 时用 sudo 操作受限路径（如 /root 下，需服务器免密 sudo）。执行前需用户确认。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "server" to ToolParameter(
            "server",
            ParameterType.STRING,
            "SSH 服务器名称或 id（可选，缺省取第一个已配置的）",
            required = false
        ),
        "from" to ToolParameter(
            "from",
            ParameterType.STRING,
            "源文件或目录远程绝对路径",
            required = true
        ),
        "to" to ToolParameter(
            "to",
            ParameterType.STRING,
            "目标文件或目录远程绝对路径",
            required = true
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 操作（需服务器免密 sudo，访问 /root 等受限路径用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val from = args["from"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (from.isEmpty()) return ToolResult.Error("缺少必需参数：from", "MISSING_FROM")
        val to = args["to"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (to.isEmpty()) return ToolResult.Error("缺少必需参数：to", "MISSING_TO")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        val base = if (sudo) "sudo -n" else ""
        return try {
            withContext(Dispatchers.IO) {
                val result = remote.exec(server, "$base mv -- ${shellQuote(from)} ${shellQuote(to)}")
                if (result.exitCode != 0) {
                    ToolResult.Error(
                        "移动失败（退出码 ${result.exitCode}）：" +
                            (result.stderr.ifBlank { result.stdout }).trim().take(MAX_ERROR_CHARS),
                        "SSH_MV_FAILED"
                    )
                } else {
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "from" to JsonPrimitive(from),
                                "to" to JsonPrimitive(to),
                                "moved" to JsonPrimitive(true),
                                "message" to JsonPrimitive("已移动：$from → $to")
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshMv 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_MV_ERROR")
        }
    }

    private companion object {
        const val MAX_ERROR_CHARS = 2000
    }
}
