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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshListTool"

/**
 * 列出已配置 SSH/SFTP 服务器上的远程目录——只读远程探索工具。
 *
 * 任意对话中可用（不依赖当前激活镜像，类似内置「远程 MCP」工具）：按 `server` 选一个已配置的
 * SFTP 服务器（缺省第一个），用独立连接列出 `path` 目录内容，执行完关闭，不干扰当前执行环境。
 */
class SshListTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshList"
    override val description =
        "列出已配置 SSH 服务器上的远程目录。server 可选（服务器名称或 id，缺省第一个已配置的）；path 为远程目录绝对路径（如 /root/workspace）。只读探索，不切换当前执行环境。"
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
            "远程目录绝对路径，如 /root/workspace",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        return try {
            withContext(Dispatchers.IO) {
                remote.withSftp(server) { sftp, _ ->
                    val info = sftp.statExistence(path)
                        ?: return@withSftp ToolResult.Error("远程路径不存在：$path", "FILE_NOT_FOUND")
                    if (!info.isDirectory) {
                        return@withSftp ToolResult.Error("不是目录：$path", "NOT_DIRECTORY")
                    }
                    val children = sftp.ls(path)
                        .filter { it.name != "." && it.name != ".." }
                        .sortedWith(compareBy({ if (it.isDirectory) 0 else 1 }, { it.name }))
                        .take(MAX_ENTRIES)
                    val sb = StringBuilder()
                    children.forEach { sb.append(it.name).append(if (it.isDirectory) "/" else "").append('\n') }
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "path" to JsonPrimitive(path),
                                "content" to JsonPrimitive(sb.toString()),
                                "total" to JsonPrimitive(children.size),
                                "truncated" to JsonPrimitive(children.size >= MAX_ENTRIES)
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshList 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_LIST_ERROR")
        }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
