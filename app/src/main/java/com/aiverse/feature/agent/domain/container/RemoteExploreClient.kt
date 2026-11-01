package com.aiverse.feature.agent.domain.container

import com.aiverse.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aiverse.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aiverse.feature.workspace.domain.model.RemoteProtocol
import com.aiverse.feature.workspace.domain.repository.RemoteRepository
import net.schmizz.sshj.sftp.SFTPClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程探索客户端：任意对话中直接访问已配置的 SSH/SFTP 服务器，行为类似一个内置的「远程 MCP 工具」。
 *
 * 不依赖当前激活镜像与共享连接：从已配置连接里按 `server` 参数选一个（缺省取排序最前的第一个），
 * 用独立 SSH transport 执行一次 SFTP 操作后关闭，不干扰当前执行环境与其它会话。
 *
 * 认证凭据统一走 [RemoteRepository.resolveAuth]，与「测试连通」/挂载/远程镜像完全同一套解析，
 * 保证「连接与同步里测试连通成功」的服务器在 sshList/sshRead 里同样能认证。
 *
 * 仅支持 SSH/SFTP 协议（FTP 不含在内）；未配置任何服务器或指定 server 不存在时报错，
 * 调用方（工具层）用 [friendlySshError] 翻译成可读提示。
 */
@Singleton
class RemoteExploreClient @Inject constructor(
    private val dao: RemoteConnectionDao,
    private val connection: RemoteSshConnection,
    private val remoteRepository: RemoteRepository
) {
    /** 可用 SFTP 服务器列表（排除 FTP），按用户排序。 */
    suspend fun availableServers(): List<RemoteConnectionEntity> =
        dao.getAllConnectionsOnce()
            .filter { it.protocol == RemoteProtocol.SFTP }

    /** 用独立连接执行一次 SFTP 操作。 */
    suspend fun <T> withSftp(server: String?, block: suspend (SFTPClient, String?) -> T): T {
        val entity = resolve(server)
        return connection.withIsolatedSftp(configOf(entity), block)
    }

    /** 用独立连接执行一条远程命令（sudo 通道用，SFTP 无法提权）。 */
    suspend fun exec(server: String?, command: String): ExecResult {
        val entity = resolve(server)
        return connection.withIsolatedExec(configOf(entity), command)
    }

    /** 按名称或 id 解析目标服务器；缺省取第一个。 */
    private suspend fun resolve(server: String?): RemoteConnectionEntity {
        val servers = availableServers()
        if (servers.isEmpty()) {
            throw IllegalStateException("未配置任何 SSH 服务器，请先在「设置 → 连接与同步」添加")
        }
        val key = server?.trim().orEmpty()
        if (key.isEmpty()) return servers.first()
        return servers.firstOrNull { it.id == key || it.name == key }
            ?: throw IllegalStateException("未找到 SSH 服务器「$key」，可用：${servers.joinToString("、") { it.name }}")
    }

    /** 解析出连接配置：认证凭据复用 [RemoteRepository.resolveAuth]，与测试连通/挂载完全一致。 */
    private fun configOf(entity: RemoteConnectionEntity): RemoteConnectionConfig =
        RemoteConnectionConfig(
            host = entity.host,
            port = entity.port,
            username = entity.username,
            auth = remoteRepository.resolveAuth(entity),
            // 远程探索用 AI 传入的远程绝对路径，不依赖 workspace 映射，占位即可
            remoteWorkspacePath = "/"
        )
}
