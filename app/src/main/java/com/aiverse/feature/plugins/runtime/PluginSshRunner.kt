package com.aiverse.feature.plugins.runtime

import com.aiverse.core.security.KeystoreCipher
import com.aiverse.feature.agent.domain.container.BoundedOutput
import com.aiverse.feature.agent.domain.container.RemoteConnectionConfig
import com.aiverse.feature.agent.domain.container.RemoteSshConnection
import com.aiverse.feature.agent.domain.container.SshLoginKeyStore
import com.aiverse.feature.plugins.data.PluginSshPermissionRepository
import com.aiverse.feature.plugins.domain.PluginHostPaths
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.settings.data.repository.normalizeRemoteWorkspacePath
import com.aiverse.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aiverse.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aiverse.feature.workspace.domain.model.RemoteProtocol
import com.aiverse.feature.workspace.domain.remote.RemoteAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 插件调用 SSH 桥的失败原因；调用方转成页面桥/进程错误码。 */
class PluginSshException(val code: String, message: String) : Exception(message)

/**
 * 插件侧的 SSH 桥执行入口：复用 AiVerse 已配置的远程服务器（[RemoteConnectionDao]）与
 * [RemoteSshConnection] 的独立 exec 通道，不复制连接逻辑。
 *
 * 授权模型：插件必须在清单声明用途（[PluginManager] 贡献里的 `ssh`），使用者单独授权
 * （[PluginSshPermissionRepository]）后即可对已配置的 SSH 服务器执行任意命令；每次调用仍检查
 * 插件启用、清单声明与使用者授权。服务器列表只暴露 `id`/`name`，绝不回传 host/账号/凭据。
 */
@Singleton
class PluginSshRunner @Inject constructor(
    private val pluginManager: PluginManager,
    private val permissionRepository: PluginSshPermissionRepository,
    private val connectionDao: RemoteConnectionDao,
    private val sshConnection: RemoteSshConnection,
    private val loginKeyStore: SshLoginKeyStore
) {

    /** 列出使用者已配置的 SSH 服务器，供插件让使用者选择执行目标；只含 id 与 name。 */
    suspend fun listServers(pluginId: String): JsonElement {
        ensureAuthorized(pluginId)
        val servers = connectionDao.getAllConnectionsOnce()
            .filter { it.protocol == RemoteProtocol.SFTP }
            .map { conn ->
                buildJsonObject {
                    put("id", conn.id)
                    put("name", conn.name)
                }
            }
        return buildJsonObject { put("servers", JsonArray(servers)) }
    }

    /** 在指定 SSH 服务器上执行一条命令；独立连接用完即关，超时由调用方 [withTimeout] 兜底。 */
    suspend fun exec(pluginId: String, params: JsonObject): JsonElement {
        ensureAuthorized(pluginId)
        val command = PluginHostPaths.requireString(params, "command").trim()
        if (command.isEmpty()) throw PluginSshException("PARAM_INVALID", "empty command")
        if (command.length > MAX_COMMAND_CHARS) {
            throw PluginSshException("PARAM_INVALID", "command too long")
        }
        val serverId = PluginHostPaths.requireString(params, "serverId")
        val timeoutSeconds = (params["timeout"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
            ?: DEFAULT_TIMEOUT_SECONDS
        val timeoutMs = timeoutSeconds.coerceIn(1L, MAX_TIMEOUT_SECONDS) * 1000L
        val conn = connectionDao.getConnectionById(serverId)
        if (conn == null || conn.protocol != RemoteProtocol.SFTP) {
            throw PluginSshException("SSH_SERVER_NOT_FOUND", "ssh server not found: $serverId")
        }
        val config = toConfig(conn)
        return try {
            val result = withTimeout(timeoutMs) { sshConnection.withIsolatedExec(config, command) }
            val bounded = BoundedOutput().apply {
                append(result.stdout)
                if (result.stderr.isNotEmpty()) append(result.stderr)
            }
            buildJsonObject {
                put("output", bounded.build())
                put("exitCode", result.exitCode)
                put("truncated", bounded.truncated)
            }
        } catch (e: PluginSshException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw PluginSshException("SSH_TIMEOUT", "ssh command timed out after ${timeoutMs}ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PluginSshException("SSH_FAILED", e.message ?: e.javaClass.simpleName)
        }
    }

    /** 门禁：插件启用 → 清单声明 ssh → 使用者授权，与 Shizuku 入口一致。 */
    private suspend fun ensureAuthorized(pluginId: String) {
        pluginManager.ensureLoaded()
        val plugin = pluginManager.plugins.value.firstOrNull { it.id == pluginId && it.isActive }
            ?: throw PluginSshException("PLUGIN_INACTIVE", "plugin is not active: $pluginId")
        plugin.content?.ssh
            ?: throw PluginSshException("SSH_NOT_DECLARED", "plugin did not declare ssh")
        if (!permissionRepository.isGranted(pluginId)) {
            throw PluginSshException("SSH_NOT_AUTHORIZED", "user did not grant ssh for this plugin")
        }
    }

    /** 实体转连接配置：按 authType 解析凭据，工作区根缺省归一到 home，样板同 ActiveRemoteConnectionResolver。 */
    private suspend fun toConfig(conn: RemoteConnectionEntity): RemoteConnectionConfig {
        val auth = if (conn.authType.equals("PASSWORD", ignoreCase = true)) {
            RemoteAuth.Password(KeystoreCipher.decryptString(conn.authData))
        } else {
            val passphrase = loginKeyStore.entries().firstOrNull { it.path == conn.authData }?.passphrase
                ?: conn.passphrase?.let { KeystoreCipher.decryptString(it) }
            RemoteAuth.PrivateKey(
                privateKeyPath = conn.authData,
                passphrase = passphrase
            )
        }
        return RemoteConnectionConfig(
            host = conn.host,
            port = conn.port,
            username = conn.username,
            auth = auth,
            remoteWorkspacePath = normalizeRemoteWorkspacePath(null, conn.username)
        )
    }

    private companion object {
        const val MAX_COMMAND_CHARS = 4_096
        const val MAX_TIMEOUT_SECONDS = 1_800L
        const val DEFAULT_TIMEOUT_SECONDS = 120L
    }
}
