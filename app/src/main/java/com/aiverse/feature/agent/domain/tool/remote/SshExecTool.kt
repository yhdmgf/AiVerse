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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshExecTool"

/**
 * 在已配置 SSH 服务器上执行一条 shell 命令——远程终端能力。
 *
 * 不依赖当前激活镜像/工作目录：只要配置了服务器（设置 → 连接与同步），任意对话中都能执行。
 * 走独立 SSH 连接 exec 通道，用完即关；命令可含管道、重定向、cd 等任意 shell 语义。
 * `sudo=true` 时在命令前加 `sudo -n`（需服务器已给登录用户配免密 sudo），可操作 /root 等受限路径。
 * 任意命令属敏感操作，执行前需用户确认（ASK）。
 */
class SshExecTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshExec"
    override val description =
        "在已配置 SSH 服务器上执行一条 shell 命令并返回输出（远程终端能力，支持管道/重定向/cd 等任意命令）。" +
            "server 可选（服务器名称或 id，缺省第一个已配置的）；command 为要执行的命令；sudo=true 时命令前加 sudo -n（需服务器免密 sudo）。" +
            "执行前需用户确认；只读探索用 sshList/sshRead，不要用它做只读操作。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.NETWORK_WRITE)

    override val parameters = mapOf(
        "server" to ToolParameter(
            "server",
            ParameterType.STRING,
            "SSH 服务器名称或 id（可选，缺省取第一个已配置的）",
            required = false
        ),
        "command" to ToolParameter(
            "command",
            ParameterType.STRING,
            "要执行的 shell 命令（可含管道、重定向、cd 等）",
            required = true
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo -n 前缀执行（需服务器免密 sudo，访问 /root 等受限路径用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val command = args["command"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (command.isEmpty()) return ToolResult.Error("缺少必需参数：command", "MISSING_COMMAND")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        return try {
            withContext(Dispatchers.IO) {
                val result = remote.exec(server, if (sudo) "sudo -n $command" else command)
                if (result.exitCode != 0) {
                    ToolResult.Error(
                        "命令执行失败（退出码 ${result.exitCode}）：" +
                            (result.stderr.ifBlank { result.stdout }).trim().take(MAX_ERROR_CHARS),
                        "SSH_EXEC_FAILED"
                    )
                } else {
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "command" to JsonPrimitive(command),
                                "exit_code" to JsonPrimitive(result.exitCode),
                                "stdout" to JsonPrimitive(result.stdout),
                                "stderr" to JsonPrimitive(result.stderr)
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshExec 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_EXEC_ERROR")
        }
    }

    private companion object {
        const val MAX_ERROR_CHARS = 2000
    }
}
