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
import com.aiverse.feature.workspace.domain.LocalFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshDownloadTool"

/**
 * 下载远程文本文件到本地工作区——远程文件管理。
 *
 * 配置好服务器即可在任意对话中使用（不依赖当前激活镜像/工作目录）。用独立 SSH 连接读远程文件内容，
 * 写入本地工作区（`localPath` 相对工作区，缺省用文件名存到工作区根）。`sudo=true` 可读取 /root 等受限路径
 * （需服务器免密 sudo）。写本地属敏感操作，执行前需用户确认；仅支持文本文件（≤8MB）。
 */
class SshDownloadTool @Inject constructor(
    private val remote: RemoteExploreClient,
    private val localFileAccess: LocalFileAccess
) : AgentTool() {
    override val name = "sshDownload"
    override val description =
        "下载远程文本文件到本地工作区。server 可选（名称或 id，缺省第一个已配置的）；path 为远程文件绝对路径；" +
            "localPath 为本地路径（相对工作区，缺省用文件名存到工作区根）；sudo=true 时用 sudo 读取受限路径（需服务器免密 sudo）。" +
            "写本地，执行前需用户确认；仅支持文本文件（≤8MB）。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.WRITE_WORKSPACE)

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
            "远程文件绝对路径",
            required = true
        ),
        "localPath" to ToolParameter(
            "localPath",
            ParameterType.STRING,
            "本地保存路径（相对工作区，如 notes/readme.md；缺省用文件名存到工作区根）",
            required = false
        ),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 读取（需服务器免密 sudo，访问 /root 等受限路径用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val localPath = args["localPath"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        return try {
            withContext(Dispatchers.IO) {
                // 先查远程文件大小（sudo 下也提权读，避免 wc 因权限误判为空）
                val wc = remote.exec(server, if (sudo) "sudo -n wc -c ${shellQuote(path)}" else "wc -c ${shellQuote(path)}")
                checkExec(wc, path)?.let { return@withContext ToolResult.Error(it, "SSH_DOWNLOAD_FAILED") }
                val size = wc.stdout.trim().substringBefore(' ').trim().toLongOrNull() ?: 0
                if (size > MAX_BYTES) {
                    return@withContext ToolResult.Error(
                        "远程文件超过 ${MAX_BYTES / 1024 / 1024}MB，暂不支持下载（仅文本文件）",
                        "TOO_LARGE"
                    )
                }
                val result = remote.exec(server, if (sudo) "sudo -n cat ${shellQuote(path)}" else "cat ${shellQuote(path)}")
                checkExec(result, path)?.let { return@withContext ToolResult.Error(it, "SSH_DOWNLOAD_FAILED") }
                val local = localPath.ifEmpty { path.substringAfterLast('/') }
                localFileAccess.writeFile(local, result.stdout, overwrite = true, Charsets.UTF_8)
                ToolResult.Success(
                    JsonObject(
                        mapOf(
                            "remote_path" to JsonPrimitive(path),
                            "local_path" to JsonPrimitive(local),
                            "bytes" to JsonPrimitive(size),
                            "message" to JsonPrimitive("已下载到本地工作区：$local")
                        )
                    )
                )
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshDownload 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_DOWNLOAD_ERROR")
        }
    }

    /** 检查命令是否失败（exit 非 0 或 stderr 有输出），翻译成可读错误；成功返回 null。 */
    private fun checkExec(r: com.aiverse.feature.agent.domain.container.ExecResult, path: String): String? {
        if (r.exitCode == 0 && r.stderr.isBlank()) return null
        val msg = r.stderr.trim()
        val low = msg.lowercase()
        return when {
            "a password is required" in low || "no password was provided" in low ->
                "需要免密 sudo：当前服务器用户未配置 NOPASSWD，sudo 要求输入密码。" +
                    "请在服务器执行 sudo visudo，加入 `<user> ALL=(ALL) NOPASSWD:ALL` 后重试"
            "no such file" in low -> "远程路径不存在：$path"
            "permission denied" in low ->
                "远程文件无访问权限（Permission denied）：sudo 下仍被拒绝，请检查路径与 sudo 配置"
            else -> msg.ifEmpty { "命令执行失败（退出码 ${r.exitCode}）：$path" }
        }
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
