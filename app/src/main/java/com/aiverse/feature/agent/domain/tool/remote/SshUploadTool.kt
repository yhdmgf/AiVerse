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
import com.aiverse.feature.workspace.domain.LocalFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "SshUploadTool"

/**
 * 上传本地文件或目录到远程服务器指定路径（SFTP put）——远程文件管理，生态闭合的关键一环。
 *
 * AI 开发完前端/后端后，可把本地工作区的源码/产物直接推到服务器任意可写目录（如 /var/www/html、
 * /home/ubuntu/app），无需手动步骤。`localPath` 为本地路径（相对工作区或绝对），`remoteDir` 为远程目标目录；
 * 本地是目录时递归上传（自动排除 .git、node_modules、build、.gradle、.idea 等构建产物）。写远程，执行前需用户确认；
 * 受登录用户权限限制（/root 等受限目录需先改权限，SFTP 无法提权）。
 */
class SshUploadTool @Inject constructor(
    private val remote: RemoteExploreClient,
    private val localFileAccess: LocalFileAccess
) : AgentTool() {
    override val name = "sshUpload"
    override val description =
        "上传本地文件或目录到远程服务器指定路径（SFTP）。localPath 为本地路径（相对工作区或绝对路径）；" +
            "remoteDir 为远程目标目录（如 /var/www/html、/home/ubuntu/app，可任意可写位置）；server 可选（名称或 id，缺省第一个已配置的）。" +
            "本地为目录时递归上传（自动排除 .git/node_modules/build/.gradle/.idea 等构建产物）。写远程，执行前需用户确认；" +
            "受登录用户权限限制，/root 等需 sudo 的目录请先改权限或上传到可写目录再移动。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.NETWORK_WRITE, ToolCapability.WRITE_WORKSPACE)

    override val parameters = mapOf(
        "server" to ToolParameter(
            "server",
            ParameterType.STRING,
            "SSH 服务器名称或 id（可选，缺省取第一个已配置的）",
            required = false
        ),
        "localPath" to ToolParameter(
            "localPath",
            ParameterType.STRING,
            "本地文件或目录路径（相对工作区，如 app/build/outputs/xxx.apk、project/）",
            required = true
        ),
        "remoteDir" to ToolParameter(
            "remoteDir",
            ParameterType.STRING,
            "远程目标目录（如 /var/www/html、/home/ubuntu/app）",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val localPath = args["localPath"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (localPath.isEmpty()) return ToolResult.Error("缺少必需参数：localPath", "MISSING_LOCAL_PATH")
        val remoteDir = args["remoteDir"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (remoteDir.isEmpty()) return ToolResult.Error("缺少必需参数：remoteDir", "MISSING_REMOTE_DIR")
        val server = args["server"]?.jsonPrimitive?.contentOrNull?.trim()
        return try {
            withContext(Dispatchers.IO) {
                if (!localFileAccess.exists(localPath)) {
                    return@withContext ToolResult.Error("本地路径不存在：$localPath", "LOCAL_NOT_FOUND")
                }
                val dir = remoteDir.trimEnd('/')
                if (localFileAccess.isFile(localPath)) {
                    val file = localFileAccess.copyToLocal(localPath)
                    val remotePath = "$dir/${file.name}"
                    remote.upload(server, file, remotePath)
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "files" to JsonPrimitive(1),
                                "bytes" to JsonPrimitive(file.length()),
                                "remote_path" to JsonPrimitive(remotePath),
                                "message" to JsonPrimitive("已上传：$localPath → $remotePath")
                            )
                        )
                    )
                } else {
                    val relFiles = localFileAccess.listFilesRecursive(localPath, MAX_DEPTH)
                        .filterNot { isExcluded(it) }
                    if (relFiles.isEmpty()) {
                        return@withContext ToolResult.Error("目录为空或无可上传文件（排除构建产物后）：$localPath", "EMPTY_DIR")
                    }
                    var count = 0
                    var bytes = 0L
                    var last = ""
                    for (rel in relFiles) {
                        val file = localFileAccess.copyToLocal("$localPath/$rel")
                        val remotePath = "$dir/$rel"
                        remote.upload(server, file, remotePath)
                        count++
                        bytes += file.length()
                        last = remotePath
                    }
                    ToolResult.Success(
                        JsonObject(
                            mapOf(
                                "files" to JsonPrimitive(count),
                                "bytes" to JsonPrimitive(bytes),
                                "remote_root" to JsonPrimitive(dir),
                                "last_file" to JsonPrimitive(last),
                                "message" to JsonPrimitive("已上传 $count 个文件（共 ${bytes} 字节）到 $dir")
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "sshUpload 异常", e)
            ToolResult.Error(friendlySshError(e), "SSH_UPLOAD_ERROR")
        }
    }

    /** 排除常见构建产物与版本控制目录，避免把本地中间产物推上服务器。 */
    private fun isExcluded(rel: String): Boolean = rel.split('/').any { it in EXCLUDED_SEGMENTS }

    private companion object {
        const val MAX_DEPTH = 10
        val EXCLUDED_SEGMENTS = setOf(
            ".git", ".gradle", ".idea", "node_modules", "build", ".DS_Store", "out", "dist"
        )
    }
}
