package com.aiverse.feature.agent.domain.tool.remote

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.container.ExecResult
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
import net.schmizz.sshj.sftp.FileMode
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
        "列出已配置 SSH 服务器上的远程目录。server 可选（服务器名称或 id，缺省第一个已配置的）；path 为远程目录绝对路径（如 /root/workspace）；sudo=true 时用 sudo 提权访问受限目录（如 /root，需服务器已给当前用户配免密 sudo，否则会提示配置）。只读探索，不切换当前执行环境。"
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
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 提权访问（默认 false；访问 /root 等受限目录需设为 true，且服务器已配免密 sudo）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        return try {
            withContext(Dispatchers.IO) {
                if (sudo) execList(server, path) else sftpList(server, path)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshList 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_LIST_ERROR")
        }
    }

    /** 普通路径：SFTP 列目录（无 sudo，受登录用户权限限制）。 */
    private suspend fun sftpList(server: String?, path: String): ToolResult =
        remote.withSftp(server) { sftp, _ ->
            val info = sftp.statExistence(path)
                ?: return@withSftp ToolResult.Error("远程路径不存在：$path", "FILE_NOT_FOUND")
            if (info.type != FileMode.Type.DIRECTORY) {
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

    /** sudo 通道：SFTP 无法提权，改用 exec 执行 sudo -n find 列目录（需服务器免密 sudo）。 */
    private suspend fun execList(server: String?, path: String): ToolResult {
        val command = "sudo -n find ${shellQuote(path)} -maxdepth 1 -mindepth 1 -printf '%y|%f\\n'"
        val result = remote.exec(server, command)
        if (result.exitCode != 0 || result.stderr.isNotBlank()) {
            return ToolResult.Error(sudoError(result, path), "SSH_SUDO_FAILED")
        }
        val entries = result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val name = line.substring(sep + 1)
                name to (line[0] == 'd')
            }
            .sortedWith(compareBy({ if (it.second) 0 else 1 }, { it.first }))
            .take(MAX_ENTRIES)
        val sb = StringBuilder()
        entries.forEach { (name, dir) -> sb.append(name).append(if (dir) "/" else "").append('\n') }
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "path" to JsonPrimitive(path),
                    "content" to JsonPrimitive(sb.toString()),
                    "total" to JsonPrimitive(entries.size),
                    "truncated" to JsonPrimitive(entries.size >= MAX_ENTRIES)
                )
            )
        )
    }

    private fun sudoError(r: ExecResult, path: String): String {
        val msg = r.stderr.trim()
        val low = msg.lowercase()
        return when {
            "a password is required" in low || "no password was provided" in low ->
                "需要免密 sudo：当前服务器用户未配置 NOPASSWD，sudo 要求输入密码。" +
                    "请在服务器执行 sudo visudo，加入 `<user> ALL=(ALL) NOPASSWD:ALL` 后重试"
            "no such file" in low -> "远程路径不存在：$path"
            "permission denied" in low ->
                "远程目录无访问权限（Permission denied）：sudo 下仍被拒绝，请检查路径与 sudo 配置"
            else -> msg.ifEmpty { "sudo 命令执行失败（退出码 ${r.exitCode}）：$path" }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
