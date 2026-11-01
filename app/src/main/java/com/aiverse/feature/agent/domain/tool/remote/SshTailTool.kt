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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshTailTool"

/**
 * 查看远程文件末尾 N 行（如服务日志）——远程终端能力的只读侧。
 *
 * 配置好服务器即可在任意对话中使用。`lines` 默认 200（1..1000）；`sudo=true` 可读取 /root 等受限路径的日志
 * （需服务器免密 sudo）。只读，自动放行。
 */
class SshTailTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshTail"
    override val description =
        "查看远程文件末尾 N 行（如服务日志，排查进程崩溃/启动失败）。path 为远程文件路径；lines 可选（默认 200，最大 1000）；" +
            "sudo=true 时用 sudo 读取受限路径（需服务器免密 sudo）；server 可选（名称或 id，缺省第一个已配置的）。只读，自动放行。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ)

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
            "远程文件路径（如 /var/log/xxx.log、/home/ubuntu/app/app.log）",
            required = true
        ),
        "lines" to ToolParameter(
            "lines",
            ParameterType.INTEGER,
            "读取末尾行数（默认 200，最大 1000）",
            required = false
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 读取（需服务器免密 sudo，/root 等受限路径用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val lines = (args["lines"]?.jsonPrimitive?.intOrNull ?: DEFAULT_LINES).coerceIn(1, MAX_LINES)
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        val base = if (sudo) "sudo -n" else ""
        return try {
            withContext(Dispatchers.IO) {
                val result = remote.exec(server, "$base tail -n $lines ${shellQuote(path)}")
                if (result.exitCode != 0) {
                    ToolResult.Error(checkError(result.stderr, result.exitCode, path), "SSH_TAIL_FAILED")
                } else {
                    val output = result.stdout.take(MAX_OUTPUT_CHARS)
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "path" to JsonPrimitive(path),
                                "lines" to JsonPrimitive(lines),
                                "truncated" to JsonPrimitive(result.stdout.length > MAX_OUTPUT_CHARS),
                                "output" to JsonPrimitive(output)
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshTail 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_TAIL_ERROR")
        }
    }

    private fun checkError(stderr: String, exitCode: Int, path: String): String {
        val msg = stderr.trim()
        val low = msg.lowercase()
        return when {
            "a password is required" in low || "no password was provided" in low ->
                "需要免密 sudo：当前服务器用户未配置 NOPASSWD。请在服务器执行 sudo visudo，加入 `<user> ALL=(ALL) NOPASSWD:ALL` 后重试"
            "no such file" in low -> "远程文件不存在：$path"
            "permission denied" in low -> "远程文件无访问权限（Permission denied）：sudo 下仍被拒绝，请检查路径与 sudo 配置"
            else -> msg.ifEmpty { "读取失败（退出码 $exitCode）：$path" }
        }
    }

    private companion object {
        const val DEFAULT_LINES = 200
        const val MAX_LINES = 1000
        const val MAX_OUTPUT_CHARS = 60000
    }
}
