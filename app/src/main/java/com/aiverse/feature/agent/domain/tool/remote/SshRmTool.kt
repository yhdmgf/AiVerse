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

private const val TAG = "SshRmTool"

/**
 * 删除远程文件或目录（rm -rf）——远程文件管理。
 *
 * 配置好服务器即可在任意对话中使用（不依赖当前激活镜像/工作目录）。`sudo=true` 可删除 /root 等受限路径
 * （需服务器免密 sudo）。删除不可恢复且属敏感写操作，执行前需用户确认；禁止删除根目录 / 与空路径。
 */
class SshRmTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshRm"
    override val description =
        "删除远程文件或目录（rm -rf，不可恢复）。server 可选（名称或 id，缺省第一个已配置的）；path 为远程绝对路径；" +
            "sudo=true 时用 sudo 删除受限路径（如 /root 下，需服务器免密 sudo）。执行前需用户确认；禁止删除根目录 /。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "server" to ToolParameter(
            "server",
            ParameterType.STRING,
            "SSH 服务器名称或 id（可选，缺省取第一个已配置的）",
            required = false
        ),
        "path" to ToolParameter(
            "path",
            ParameterType.STRING,
            "要删除的远程文件或目录绝对路径",
            required = true
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 删除（需服务器免密 sudo，访问 /root 等受限路径用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val forbidden = path == "/" || path == "." || path == ".." || path == "~"
        if (forbidden) return ToolResult.Error("禁止删除危险路径：$path", "FORBIDDEN_PATH")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        val base = if (sudo) "sudo -n" else ""
        return try {
            withContext(Dispatchers.IO) {
                val result = remote.exec(server, "$base rm -rf -- ${shellQuote(path)}")
                if (result.exitCode != 0) {
                    ToolResult.Error(
                        "删除失败（退出码 ${result.exitCode}）：" +
                            (result.stderr.ifBlank { result.stdout }).trim().take(MAX_ERROR_CHARS),
                        "SSH_RM_FAILED"
                    )
                } else {
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "path" to JsonPrimitive(path),
                                "deleted" to JsonPrimitive(true),
                                "message" to JsonPrimitive("已删除：$path")
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshRm 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_RM_ERROR")
        }
    }

    private companion object {
        const val MAX_ERROR_CHARS = 2000
    }
}
