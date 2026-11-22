package com.aiverse.feature.plugins.runtime

import com.aiverse.feature.agent.domain.container.ExecResult
import com.aiverse.feature.agent.domain.container.RemoteConnectionConfig
import com.aiverse.feature.agent.domain.container.RemoteSshConnection
import com.aiverse.feature.agent.domain.container.SshLoginKey
import com.aiverse.feature.agent.domain.container.SshLoginKeyStore
import com.aiverse.feature.plugins.data.PluginSshPermissionRepository
import com.aiverse.feature.plugins.domain.InstalledPlugin
import com.aiverse.feature.plugins.domain.PluginInstallOrigin
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.PluginManifest
import com.aiverse.feature.plugins.data.PluginRecord
import com.aiverse.feature.plugins.domain.newmodel.PluginContent
import com.aiverse.feature.plugins.domain.newmodel.PluginSshDefinition
import com.aiverse.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aiverse.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aiverse.feature.workspace.domain.model.RemoteProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 插件 SSH 桥的门禁与参数校验。聚焦 [PluginSshRunner] 在调用真实 SSH 通道前的裁决：
 * 插件启用、清单声明、使用者授权三重门禁，以及 serverId 存在性与协议校验；
 * 底层 [RemoteSshConnection.withIsolatedExec] 由 mock 注入，不发起真实连接。
 */
class PluginSshRunnerTest {

    private val pluginId = "demo.ssh"

    private val pluginManager: PluginManager = mockk()
    private val permissionRepository: PluginSshPermissionRepository = mockk(relaxed = true)
    private val connectionDao: RemoteConnectionDao = mockk()
    private val sshConnection: RemoteSshConnection = mockk()
    private val loginKeyStore: SshLoginKeyStore = mockk()

    private val runner = PluginSshRunner(
        pluginManager = pluginManager,
        permissionRepository = permissionRepository,
        connectionDao = connectionDao,
        sshConnection = sshConnection,
        loginKeyStore = loginKeyStore
    )

    /** 构造一个「启用 + 清单可读 + 声明 ssh」的活跃插件，[sshDeclared] 控制是否声明 ssh。 */
    private fun activePlugin(sshDeclared: Boolean): InstalledPlugin {
        val manifest = mockk<PluginManifest> { every { id } returns pluginId }
        val content = if (sshDeclared) {
            PluginContent(ssh = PluginSshDefinition(pluginId = pluginId, purpose = "run commands"))
        } else {
            PluginContent()
        }
        return InstalledPlugin(
            manifest = manifest,
            installDir = File("/tmp/demo.ssh"),
            enabled = true,
            content = content,
            source = PluginInstallOrigin(kind = PluginInstallOrigin.KIND_LOCAL),
            record = PluginRecord(
                id = pluginId,
                version = "1.0.0",
                enabled = true,
                installedAt = 0L,
                origin = "local",
                archiveSha256 = "",
                manifestSha256 = ""
            )
        )
    }

    /** 让门禁的前两关（启用、声明）通过，授权关由各用例自行设置。 */
    private fun givenPlugin(sshDeclared: Boolean = true) {
        val plugin = activePlugin(sshDeclared)
        every { pluginManager.plugins } returns MutableStateFlow(listOf(plugin))
        every { pluginManager.ensureLoaded() } returns Unit
    }

    private suspend fun assertRejected(code: String, block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("应抛 PluginSshException($code)，但成功返回")
        } catch (e: PluginSshException) {
            assertEquals(code, e.code)
        }
    }

    // ── 门禁 ──

    @Test
    fun exec_pluginInactive_isRejected() = runTest {
        givenPlugin()
        // 没有任何活跃插件
        every { pluginManager.plugins } returns MutableStateFlow(emptyList())
        assertRejected(code = "PLUGIN_INACTIVE") {
            runner.exec(pluginId, execParams())
        }
    }

    @Test
    fun exec_sshNotDeclared_isRejected() = runTest {
        givenPlugin(sshDeclared = false)
        coEvery { permissionRepository.isGranted(pluginId) } returns true
        assertRejected(code = "SSH_NOT_DECLARED") {
            runner.exec(pluginId, execParams())
        }
    }

    @Test
    fun exec_notAuthorized_isRejected() = runTest {
        givenPlugin()
        coEvery { permissionRepository.isGranted(pluginId) } returns false
        assertRejected(code = "SSH_NOT_AUTHORIZED") {
            runner.exec(pluginId, execParams())
        }
    }

    @Test
    fun listServers_notAuthorized_isRejected() = runTest {
        givenPlugin()
        coEvery { permissionRepository.isGranted(pluginId) } returns false
        assertRejected(code = "SSH_NOT_AUTHORIZED") {
            runner.listServers(pluginId)
        }
    }

    // ── serverId 校验 ──

    @Test
    fun exec_serverNotFound_isRejected() = runTest {
        givenAuthorized()
        coEvery { connectionDao.getConnectionById("missing") } returns null
        assertRejected(code = "SSH_SERVER_NOT_FOUND") {
            runner.exec(pluginId, execParams(serverId = "missing"))
        }
    }

    @Test
    fun exec_serverNotSftp_isRejected() = runTest {
        givenAuthorized()
        coEvery { connectionDao.getConnectionById("srv") } returns connection("srv", RemoteProtocol.FTP)
        assertRejected(code = "SSH_SERVER_NOT_FOUND") {
            runner.exec(pluginId, execParams(serverId = "srv"))
        }
    }

    @Test
    fun exec_emptyCommand_isRejected() = runTest {
        givenAuthorized()
        assertRejected(code = "PARAM_INVALID") {
            runner.exec(pluginId, execParams(command = "   "))
        }
    }

    // ── 成功路径 ──

    @Test
    fun listServers_exposesOnlyIdAndName() = runTest {
        givenAuthorized()
        coEvery { connectionDao.getAllConnectionsOnce() } returns listOf(
            connection("a", RemoteProtocol.SFTP, name = "Alpha"),
            connection("b", RemoteProtocol.FTP, name = "Beta") // 非 SFTP 应被过滤
        )
        val result = runner.listServers(pluginId) as JsonObject
        val servers = result["servers"]!!.jsonArrayOrEmpty()
        assertEquals(1, servers.size)
        val only = servers.first() as JsonObject
        assertEquals("a", (only["id"] as JsonPrimitive).content)
        assertEquals("Alpha", (only["name"] as JsonPrimitive).content)
        // 绝不回传 host / 账号 / 凭据
        assertTrue(!only.containsKey("host") && !only.containsKey("username"))
    }

    @Test
    fun exec_returnsOutputAndExitCode() = runTest {
        givenAuthorized()
        val conn = connection("srv", RemoteProtocol.SFTP)
        coEvery { connectionDao.getConnectionById("srv") } returns conn
        coEvery { loginKeyStore.entries() } returns
            listOf(SshLoginKey(id = "k", name = "key", path = conn.authData, fingerprint = null, passphrase = null))
        coEvery {
            sshConnection.withIsolatedExec(any<RemoteConnectionConfig>(), "echo hi")
        } returns ExecResult(stdout = "hi\n", stderr = "", exitCode = 0)

        val result = runner.exec(pluginId, execParams()) as JsonObject
        assertEquals("hi\n", ((result["output"]!!) as JsonPrimitive).content)
        assertEquals(0, ((result["exitCode"]!!) as JsonPrimitive).content.toInt())
        assertEquals(false, ((result["truncated"]!!) as JsonPrimitive).content.toBoolean())
    }

    // ── 辅助 ──

    /** 门禁全通过 + 已有一个可用的 SFTP 连接。 */
    private suspend fun givenAuthorized() {
        givenPlugin()
        coEvery { permissionRepository.isGranted(pluginId) } returns true
    }

    private fun execParams(serverId: String = "srv", command: String = "echo hi"): JsonObject =
        buildJsonObject {
            put("serverId", JsonPrimitive(serverId))
            put("command", JsonPrimitive(command))
        }

    private fun connection(id: String, protocol: RemoteProtocol, name: String = "Srv") =
        RemoteConnectionEntity(
            id = id,
            name = name,
            protocol = protocol,
            host = "10.0.0.8",
            port = 22,
            username = "deploy",
            authType = "key",
            authData = "/keys/id_rsa"
        )

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrEmpty() =
        this as? kotlinx.serialization.json.JsonArray
            ?: error("expected JsonArray but was $this")
}
