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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val TAG = "SshReadTool"

/**
 * 读取已配置 SSH/SFTP 服务器上的远程文件——只读远程探索工具。
 *
 * 任意对话中可用（不依赖当前激活镜像）：按 `server` 选一个已配置的 SFTP 服务器（缺省第一个），
 * 用独立连接按行窗口读取 `path`，执行完关闭。文件过大提示用 start_line/end_line 分段读取。
 * `sudo=true` 时改用 exec 通道执行 `sudo -n`（SFTP 无法提权），可读取 /root 等受限文件，
 * 前提是服务器已给登录用户配免密 sudo。
 */
class SshReadTool @Inject constructor(
    private val remote: RemoteExploreClient
) : AgentTool() {
    override val name = "sshRead"
    override val description =
        "读取已配置 SSH 服务器上的远程文件。server 可选（服务器名称或 id，缺省第一个已配置的）；path 为远程文件绝对路径；start_line/end_line 分段读取（单次最多 2000 行、约 200KB）；sudo=true 时用 sudo 提权读取受限文件（如 /root 下，需服务器已给当前用户配免密 sudo，否则会提示配置）。只读探索，不切换当前执行环境。"
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
            "远程文件绝对路径，如 /root/workspace/README.md",
            required = true
        ),
        "start_line" to ToolParameter("start_line", ParameterType.INTEGER, "开始行号（从 1 计）", required = false),
        "end_line" to ToolParameter("end_line", ParameterType.INTEGER, "结束行号；与 start_line 跨度最多 2000 行", required = false),
        "sudo" to ToolParameter(
            "sudo",
            ParameterType.BOOLEAN,
            "是否用 sudo 提权读取（默认 false；访问 /root 下受限文件需设为 true，且服务器已配免密 sudo）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (path.isEmpty()) return ToolResult.Error("缺少必需参数：path", "MISSING_PATH")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 1
        val requestedEnd = args["end_line"]?.jsonPrimitive?.intOrNull
        val sudo = args["sudo"]?.jsonPrimitive?.booleanOrNull ?: false
        return try {
            withContext(Dispatchers.IO) {
                if (sudo) execRead(server, path, startLine, requestedEnd)
                else sftpRead(server, path, startLine, requestedEnd)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshRead 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_READ_ERROR")
        }
    }

    /** 普通路径：SFTP 按行窗口读文件（无 sudo，受登录用户权限限制）。 */
    private suspend fun sftpRead(
        server: String?,
        path: String,
        startLine: Int,
        requestedEnd: Int?
    ): ToolResult = remote.withSftp(server) { sftp, _ ->
        val info = sftp.statExistence(path)
            ?: return@withSftp ToolResult.Error("远程路径不存在：$path", "FILE_NOT_FOUND")
        if (info.type == FileMode.Type.DIRECTORY) {
            return@withSftp ToolResult.Error("是目录，无法按文件读取：$path", "IS_DIRECTORY")
        }
        if (info.size > MAX_READ_BYTES) {
            return@withSftp ToolResult.Error(
                "远程文件超过 ${MAX_READ_BYTES / 1024 / 1024}MB，请用 start_line/end_line 分段读取",
                "TOO_LARGE"
            )
        }
        val text = String(readAll(sftp, path), Charsets.UTF_8)
        buildWindowResult(path, text, startLine, requestedEnd)
    }

    /** sudo 通道：SFTP 无法提权，改用 exec 执行 sudo -n（先 wc -l 拿总行数，再 sed 按行窗口读取）。 */
    private suspend fun execRead(
        server: String?,
        path: String,
        startLine: Int,
        requestedEnd: Int?
    ): ToolResult {
        val wc = remote.exec(server, "sudo -n wc -l ${shellQuote(path)}")
        checkSudoError(wc, path)?.let { return ToolResult.Error(it, "SSH_SUDO_FAILED") }
        val total = wc.stdout.trim().substringBefore(' ').trim().toIntOrNull() ?: 0
        val lineCap = startLine + MAX_LINES - 1
        val end = minOf(requestedEnd ?: lineCap, lineCap, maxOf(total, 1))
        if (startLine > total) {
            return buildWindowResult(path, "", startLine, requestedEnd)
        }
        val out = remote.exec(server, "sudo -n sed -n '${startLine},${end}p' ${shellQuote(path)}")
        checkSudoError(out, path)?.let { return ToolResult.Error(it, "SSH_SUDO_FAILED") }
        return buildWindowResult(path, out.stdout, startLine, requestedEnd)
    }

    /** 把全文按 start_line/end_line 窗口切片，并做行数与字节截断。 */
    private fun buildWindowResult(
        path: String,
        text: String,
        startLine: Int,
        requestedEnd: Int?
    ): ToolResult {
        val lines = text.split('\n')
        val total = lines.size
        val lineCap = startLine + MAX_LINES - 1
        val endCap = if (requestedEnd != null) minOf(requestedEnd, lineCap) else lineCap

        val sb = StringBuilder()
        var emitted = 0
        var byteCount = 0
        var truncatedByBytes = false
        for (i in startLine - 1 until minOf(endCap, total)) {
            val line = lines[i]
            val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1
            if (byteCount + lineBytes > MAX_BYTES && emitted > 0) {
                truncatedByBytes = true
                break
            }
            if (emitted > 0) sb.append('\n')
            sb.append(line)
            byteCount += lineBytes
            emitted++
        }
        val lastEmitted = startLine + emitted - 1
        val wantedEnd = if (requestedEnd != null) minOf(requestedEnd, total) else total
        val truncated = emitted > 0 && lastEmitted < wantedEnd
        val note = when {
            !truncated -> null
            truncatedByBytes -> "内容超过 ${MAX_BYTES / 1024}KB 上限已截断，可从第 ${lastEmitted + 1} 行起用 start_line 继续读取。"
            else -> "内容超过 $MAX_LINES 行上限已截断，可从第 ${lastEmitted + 1} 行起用 start_line 继续读取。"
        }
        val result = mutableMapOf<String, JsonElement>(
            "path" to JsonPrimitive(path),
            "content" to JsonPrimitive(sb.toString()),
            "total_lines" to JsonPrimitive(total),
            "start_line" to JsonPrimitive(startLine),
            "end_line" to JsonPrimitive(maxOf(lastEmitted, startLine - 1)),
            "read_lines" to JsonPrimitive(emitted),
            "truncated" to JsonPrimitive(truncated)
        )
        note?.let { result["note"] = JsonPrimitive(it) }
        return ToolResult.Success(JsonObject(result))
    }

    /** 检查 sudo 命令是否失败（退出码非 0 或 stderr 有输出），翻译成可读错误；成功返回 null。 */
    private fun checkSudoError(r: ExecResult, path: String): String? {
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
            else -> msg.ifEmpty { "sudo 命令执行失败（退出码 ${r.exitCode}）：$path" }
        }
    }

    /** 读取远程文件全部字节。调用方已校验大小上限。 */
    private fun readAll(sftp: SFTPClient, path: String): ByteArray =
        sftp.open(path).use { rf ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(IO_CHUNK)
            var offset = 0L
            while (true) {
                val n = rf.read(offset, buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
                offset += n
            }
            out.toByteArray()
        }

    private companion object {
        const val MAX_LINES = 2000
        const val MAX_BYTES = 200 * 1024
        const val MAX_READ_BYTES = 8L * 1024 * 1024
        const val IO_CHUNK = 32 * 1024
    }
}
