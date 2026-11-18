package com.aiverse.feature.agent.domain.container

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.tool.explorer.shellQuote
import com.aiverse.feature.workspace.domain.remote.RemoteAuth
import com.aiverse.feature.workspace.domain.RemoteAgentConfigTransport
import com.aiverse.feature.credentials.domain.model.GitCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RenameFlags
import java.util.EnumSet
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.userauth.UserAuthException
import com.hierynomus.sshj.common.KeyDecryptionFailedException
import net.schmizz.sshj.userauth.password.PasswordUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteSshConnection"

/**
 * 共享的 SSH 连接管理器：持有两条独立的 sshj [SSHClient]——exec 通道供 [RemoteSshEngine] 执行命令，
 * SFTP 通道供 [RemoteSftpFileAccess] 读写文件。两者隔离，SFTP 的 Buffer 溢出不会拖垮命令通道。
 *
 * 连接配置（host/port/username/auth）由调用方在 [connect] 时传入。连接断开后下次 [connect]
 * 重新建立；SFTP 通道按需惰性建立（见 [sftp]）。exec 侧操作串行化（[mutex]），SFTP 侧由
 * [RemoteSftpFileAccess] 串行化（sshj 的 SFTPClient 非线程安全）。
 */
@Singleton
class RemoteSshConnection @Inject constructor(
    private val hostKeyStore: SshHostKeyStore,
    private val hostKeyVerifier: SshHostKeyVerifier,
    private val privateKeyStore: SshPrivateKeyStore,
    private val containerInstaller: ContainerInstaller
) {

    @Volatile
    private var sshClient: SSHClient? = null

    /**
     * 独立的 SFTP 通道：与 exec 各用一条 SSH transport。sshj 的 SFTP 有间歇性 Buffer 溢出
     * （hierynomus/sshj#461），共用 transport 时崩溃会一并拖垮 Bash/终端；分开后只影响文件通道，
     * 崩溃由 [invalidateSftp] + 下次 [sftp] 重建兜底。
     */
    @Volatile
    private var sftpSshClient: SSHClient? = null

    @Volatile
    private var sftpClient: SFTPClient? = null

    private val mutex = Mutex()
    private val sftpLock = Mutex()
    private val credentialSyncMutex = Mutex()
    internal val sftpOperationMutex = Mutex()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    /** 连接状态流，供 UI 显示指示器、工作区初始化等待连接就绪。 */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 重连成功后回调，供工作区重新加载。由 [startSupervisor] 注册。 */
    private var onReconnected: (suspend () -> Unit)? = null
    private var supervisorJob: Job? = null

    /** 远程服务器上的真实 home 路径（连接成功后查 $HOME 缓存），供文件工具展开 ~。 */
    @Volatile
    var remoteHome: String? = null
        private set

    /** 当前连接配置快照，供重连与路径映射使用。 */
    @Volatile
    var config: RemoteConnectionConfig? = null
        private set

    suspend fun connect(config: RemoteConnectionConfig) = mutex.withLock {
        if (isConnected() && this.config == config) return@withLock
        disconnectInternal()
        this.config = config
        _connectionState.value = ConnectionState.CONNECTING
        try {
            withContext(Dispatchers.IO) {
                val client = newSshClient(config)
                sshClient = client
                // 查远程真实 home（root 是 /root，普通用户是 /home/xxx），供文件工具展开 ~
                runCatching {
                    val s = client.startSession()
                    val cmd = s.exec("echo \$HOME")
                    remoteHome = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream))
                        .readText().trim().ifEmpty { null }
                    s.close()
                }
                // 注册 transport 断开监听：连接断开时即时推状态，不等 supervisor 轮询
                client.transport.setDisconnectListener { reason, info ->
                    FileLogger.w(TAG, "SSH 连接断开: $reason, $info")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                FileLogger.i(TAG, "SSH 已连接 ${config.host}:${config.port} as ${config.username}")
            }
            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.FAILED
            throw e
        }
    }

    fun removeHostKey(host: String, port: Int) {
        hostKeyStore.remove(host, port)
    }

    fun savedHostKeys(): Map<String, String> = hostKeyStore.entries()
    /** 无参 connect：用上次保存的 config 重连。 */
    suspend fun connect() {
        val cfg = config ?: throw IllegalStateException("未配置 SSH 连接")
        connect(cfg)
    }

    suspend fun disconnect() = mutex.withLock {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        closeSftpInternal()
        runCatching { sshClient?.disconnect() }
        sshClient = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** 关闭并清空独立 SFTP 通道；下次 [sftp] 会按当前 config 重建。 */
    private fun closeSftpInternal() {
        runCatching { sftpClient?.close() }
        runCatching { sftpSshClient?.disconnect() }
        sftpClient = null
        sftpSshClient = null
    }

    /** 建立一条 SSH transport 并完成认证与保活（exec 与 SFTP 各建一条）。 */
    private fun newSshClient(config: RemoteConnectionConfig): SSHClient = SSHClient().apply {
        addHostKeyVerifier(hostKeyVerifier)
        connect(config.host, config.port)
        when (val auth = config.auth) {
            is RemoteAuth.Password -> authPassword(config.username, auth.password)
            is RemoteAuth.PrivateKey -> {
                val pem = privateKeyStore.readPem(auth.privateKeyPath)
                val passwordFinder = auth.passphrase?.let { PasswordUtils.createOneOff(it.toCharArray()) }
                authPublickey(config.username, loadKeys(pem, null, passwordFinder))
            }
        }
        // 启用 SSH 心跳保活，防止空闲超时断连
        runCatching {
            connection.keepAlive?.let {
                it.setKeepAliveInterval(30)
                it.start()
            }
        }
    }

    fun isConnected(): Boolean =
        sshClient?.isConnected == true && sshClient?.isAuthenticated == true

    /** 开一个 exec session 执行命令。调用方负责关闭返回的 Command。 */
    fun startExecSession(command: String): Session.Command {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        val session = client.startSession()
        val cmd = session.exec(command)
        // 立即给远端进程的 stdin 送 EOF：所有调用方只读输出。不关的话，远端按「stdin 是否可读」
        // 决定行为的程序（无路径参数的 rg 等）会把 channel 当待读输入，一直等到命令超时。
        runCatching { cmd.outputStream.close() }
        return cmd
    }

    /**
     * 开 exec 会话但**保留 stdin 可写**，供大内容经 stdin 传输（避免命令行参数超过远端 ARG_MAX）。
     * 调用方负责写入并关闭 [Session.Command.outputStream]、读取输出、关闭 session。
     */
    fun startExecSessionWithStdin(command: String): Session.Command {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        return client.startSession().exec(command)
    }

    /** 开一个新的 Session，供调用方分配 PTY 并启动 shell。调用方负责关闭 Session。 */
    fun startShellSession(): Session {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        return client.startSession()
    }

    /**
     * 获取独立的 SFTP client（惰性建立第二条 SSH 连接，与 exec 隔离）。调用方不应关闭它——
     * 由 [disconnect] 统一管理。连接失效（未连接/未认证）时丢弃重建，覆盖 SFTP 通道崩溃后的自愈。
     */
    suspend fun sftp(): SFTPClient = sftpLock.withLock {
        val cfg = config ?: throw IllegalStateException("SSH 未配置")
        sftpClient
            ?.takeIf { sftpSshClient?.isConnected == true && sftpSshClient?.isAuthenticated == true }
            ?.let { return@withLock it }
        closeSftpInternal()
        val client = withContext(Dispatchers.IO) { newSshClient(cfg) }
        sftpSshClient = client
        val sftp = withContext(Dispatchers.IO) { client.newSFTPClient() }
        sftpClient = sftp
        sftp
    }

    /** 丢弃当前 SFTP 通道（通道异常后调用），下次 [sftp] 会重建。 */
    suspend fun invalidateSftp() = sftpLock.withLock { closeSftpInternal() }

    /**
     * 用**独立** SSH 连接执行一次 SFTP 操作（远程探索工具用）：任何对话中直接访问已配置服务器，
     * 不依赖当前激活镜像与共享连接。独立 transport 用完即关，不干扰共享连接状态与其它会话。
     *
     * [block] 收到可用的 [SFTPClient] 与远程真实 `$HOME`（查不到为 null），执行完由本方法关闭连接。
     * 连接建立或认证失败时抛 [Exception]，调用方可用 [friendlySshError] 翻译。
     */
    suspend fun <T> withIsolatedSftp(
        config: RemoteConnectionConfig,
        block: suspend (SFTPClient, String?) -> T
    ): T {
        val client = withContext(Dispatchers.IO) { newSshClient(config) }
        var home: String? = null
        try {
            runCatching {
                val s = client.startSession()
                val cmd = s.exec("echo \$HOME")
                home = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream))
                    .readText().trim().ifEmpty { null }
                s.close()
            }
            val sftp = withContext(Dispatchers.IO) { client.newSFTPClient() }
            return block(sftp, home)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    /**
     * 用**独立** SSH 连接执行一条远程命令（远程探索工具的 sudo 通道用）：SFTP 无法 sudo，
     * 需要提权（如访问 /root）时改走 exec 通道执行 `sudo -n ...`（前提：服务器已给登录用户配免密 sudo）。
     * 独立 transport 用完即关，不干扰共享连接状态。返回标准输出/错误与退出码。
     */
    suspend fun withIsolatedExec(config: RemoteConnectionConfig, command: String): ExecResult {
        val client = withContext(Dispatchers.IO) { newSshClient(config) }
        try {
            val session = client.startSession()
            val cmd = session.exec(command)
            runCatching { cmd.outputStream.close() }
            val stdout = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream)).readText()
            val stderr = java.io.BufferedReader(java.io.InputStreamReader(cmd.errorStream)).readText()
            cmd.join()
            val exit = cmd.exitStatus ?: 0
            session.close()
            return ExecResult(stdout, stderr, exit)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    /** 若已配置但未连接，立即尝试重连一次。返回是否最终连通。供 App 回到前台时主动触发。 */
    suspend fun tryReconnectIfDisconnected(): Boolean {
        val cfg = config ?: return false
        if (isConnected()) return true
        _connectionState.value = ConnectionState.CONNECTING
        return runCatching { connect(cfg) }
            .onSuccess {
                FileLogger.i(TAG, "SSH 重连成功（前台触发）")
                runCatching { onReconnected?.invoke() }
            }
            .onFailure {
                FileLogger.w(TAG, "SSH 重连失败（前台触发）", it)
                _connectionState.value = ConnectionState.FAILED
            }
            .isSuccess
    }

    /**
     * 启动连接监督协程：远程模式下定期探活，连接断开则自动重连（指数退避），
     * 重连成功后触发 [onReconnected] 回调（供工作区重新加载）并维护 [connectionState]。
     * 幂等：重复调用不会起多个 supervisor。仅在远程模式下生效。 */
    fun startSupervisor(scope: CoroutineScope, onReconnected: suspend () -> Unit) {
        this.onReconnected = onReconnected
        if (supervisorJob?.isActive == true) return
        supervisorJob = scope.launch {
            var backoffMs = 5000L
            val maxBackoffMs = 30000L
            while (isActive) {
                delay(backoffMs)
                val cfg = config ?: continue
                if (isConnected()) {
                    backoffMs = 15000 // 连接正常，拉长探活间隔（仅兜底，断开有 DisconnectListener 即时通知）
                    continue
                }
                // 连接已断，尝试重连
                tryReconnectIfDisconnected()
                // 重连失败后指数退避
                if (!isConnected()) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                } else {
                    backoffMs = 15000
                }
            }
        }
    }

    suspend fun stopSupervisor() {
        supervisorJob?.cancelAndJoin()
        supervisorJob = null
        onReconnected = null
    }

    /**
     * 更新 ~/workspace 符号链接指向当前选中工作区的远程路径，让 AI 用 ~/workspace/... 路径时
     * Bash 命令（pwd 等）能直接访问到正确的工作区目录。应在工作区选中/初始化后调用。
     *
     * `~/workspace` 已是真实目录时跳过（工作区根被配成了 `~/workspace` 的情况）：此时 `ln` 会把
     * 链接建到该目录**内部**，形成自引用死循环，破坏整个工作区；跳过时命令链路会回退到真实路径。
     */
    suspend fun updateWorkspaceSymlink(workspacePath: String) {
        val client = sshClient ?: return
        val ws = workspacePath.trimEnd('/')
        if (ws.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val session = client.startSession()
                val cmd = session.exec(
                    "if [ -d ~/workspace ] && [ ! -L ~/workspace ]; then echo skip; " +
                        "else ln -sfn ${shellQuote(ws)} ~/workspace 2>/dev/null; echo done; fi"
                )
                val out = java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream)).readText().trim()
                session.close()
                if (out == "skip") {
                    FileLogger.w(TAG, "~/workspace 已是真实目录，跳过符号链接（工作区根可能配成了 ~/workspace）")
                }
            }.onFailure { FileLogger.w(TAG, "更新 workspace 符号链接失败: $ws", it) }
        }
    }

    internal suspend fun agentConfigSnapshotSync(
        docs: Map<String, String>,
        config: RemoteConnectionConfig?,
        transport: RemoteAgentConfigTransport?
    ) {
        if (config == null) {
            check(docs.isEmpty())
            return
        }
        if (!isConnected() || config != this.config) throw IllegalStateException("SSH 未连接")
        checkActive()
        val snapshot = BuiltinDocsSync.snapshot(docs)
        uploadDocs(snapshot)
    }

    /** 中止信号：连接已断时让同步/刷新尽快停手，避免在失效连接上继续传输。 */
    internal fun checkActive() {
        check(isConnected() && config != null) { "SSH 未连接" }
    }

    /**
     * 开一条端口映射：通过**专属** SSH transport 建立到远端 `[targetHost]:[targetPort]` 的 direct-tcpip 通道，
     * 并在本机 `127.0.0.1` 上绑定一个临时端口监听（accept 循环跑在守护线程）。返回带真实绑定端口的句柄，
     * 调用方持有并在 [PortForwardHandle.close] 时关闭监听 + 专属 transport。映射与共享连接通道隔离。
     */
    internal suspend fun openPortForward(targetHost: String, targetPort: Int): PortForwardHandle {
        val cfg = config ?: throw IllegalStateException("SSH 未配置")
        val client = withContext(Dispatchers.IO) { newSshClient(cfg) }
        try {
            // 本机先绑一个 127.0.0.1:0 的临时监听，拿到 OS 分配的真实端口后再交给 sshj 建立转发。
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            val localPort = serverSocket.localPort
            val parameters = Parameters("127.0.0.1", localPort, targetHost, targetPort)
            val forwarder = withContext(Dispatchers.IO) { client.newLocalPortForwarder(parameters, serverSocket) }
            Thread({ runCatching { forwarder.listen() } }, "aiverse-port-fwd-$localPort")
                .apply { isDaemon = true; start() }
            return PortForwardHandle(client, forwarder, serverSocket, localPort)
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            throw e
        }
    }

    /**
     * 把内置文档同步到远程 `~/.aiverse/docs/`，让远程模式下 AI 也能像本地模式一样
     * 查阅 `~/.aiverse/docs/` 下的设置说明文档。每次连接/重连后**增量**同步（首次全量，之后只传输差异），
     * 使 App 升级后远程文档随之更新。正文通过 SFTP 写入并原子替换。
     */
    private suspend fun uploadDocs(
        snapshot: BuiltinDocsSync.Snapshot
    ) {
        if (snapshot.documents.isEmpty()) return
        val client = sshClient ?: return
        val home = remoteHome ?: return
        val docs = home.trimEnd('/') + "/.aiverse/docs"
        withContext(Dispatchers.IO) {
            runCatching {
                // 先建全部子目录并清掉旧 .md：版本间调整文档分类会改变路径，只覆盖不清理会让旧文件残留
                val subDirs = snapshot.documents.keys.mapNotNull { key ->
                    key.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
                }.distinct()
                val mkdirArgs = (listOf(docs) + subDirs.map { "$docs/$it" }).joinToString(" ") { "'$it'" }
                val prepSession = client.startSession()
                prepSession.exec("mkdir -p $mkdirArgs; find '$docs' -type f -name '*.md' -delete").join()
                prepSession.close()
                val sync = BuiltinDocsSync(DocsSftpBackend(this@RemoteSshConnection, docs))
                sync.sync(snapshot)
            }.onFailure { FileLogger.w(TAG, "同步文档到远程失败", it) }
                .onSuccess { result -> if (result.changed) FileLogger.i(TAG, "已同步 ${snapshot.documents.size} 个文档到远程 $docs") }
        }
    }

    /**
     * 释放 App 内置资源（子代理定义、面板示例脚本等）到远程 `~/.aiverse/<subdir>/`。
     * 仅补齐服务器上缺失的文件、**不覆盖已存在**（保留用户修改），脚本赋可执行位。
     * 与 [uploadDocs] 一样在连接/重连后调用——远程模式下全局 agents/scripts 以远端为准，
     * 内置默认需在远端存在才能「开箱即用」。二进制安全（base64 传输）。
     */
    suspend fun uploadBuiltinFiles(
        destSubdir: String,
        files: Map<String, ByteArray>,
        executable: Boolean = false
    ) {
        if (files.isEmpty()) return
        val client = sshClient ?: return
        val home = remoteHome ?: return
        val destDir = home.trimEnd('/') + "/.aiverse/" + destSubdir.trim('/')
        withContext(Dispatchers.IO) {
            runCatching {
                sftpOperationMutex.withLock { sftp().mkdirs(destDir) }
                for ((name, bytes) in files) {
                    val dest = "$destDir/$name"
                    val exists = sftpOperationMutex.withLock { sftp().statExistence(dest) != null }
                    if (!exists) writeRemoteFile(dest, bytes, executable, overwrite = false)
                }
                FileLogger.i(TAG, "已释放 ${files.size} 个内置文件到远程 $destDir")
            }.onFailure { FileLogger.w(TAG, "释放内置文件到远程失败: $destSubdir", it) }
        }
    }

    /**
     * 把 App 的 git 凭据注入远程服务器（仅当用户开启「自动注入」时调用）：
     * 写 `~/.aiverse/git-credentials`（store 格式）+ `~/.aiverse/gitconfig`（顶层 credential helper）
     * + `~/.aiverse/gitconfig.credential`（includeIf 分包）。成功返回 true，失败记录日志并返回 false。
     *
     * 生效机制：git 的 `[credential.helper]` 只能在 **顶层** 全局配置生效，`includeIf` 内的 helper 会被忽略。
     * 故先写 `~/.aiverse/gitconfig`：`[include] path=~/.gitconfig` 保留用户自己的全局配置（署名等），
     * `[credential] helper = !…` 顶层 helper 仅指向一个极薄脚本，它再转发到 credential 分包：
     * - `~/.aiverse/git-credentials`（store 格式，chmod 600）。
     * - `[includeIf "gitdir:<工作区根>/"] path=gitconfig.credential`，仅工作区根目录下的仓库才加载 helper。
     * 这样不影响用户在服务器上手动 git，也不影响其它目录的仓库。
     *
     * @param credentials 要注入的凭据列表（每 host 一条）。
     * @param workspaceRoot 远程工作区根路径（AI 的 ~/workspace 映射目录），限定注入范围。
     */
    suspend fun uploadGitCredentialConfig(credentials: List<GitCredential>, workspaceRoot: String): Boolean = credentialSyncMutex.withLock {
        val client = sshClient ?: return@withLock false
        val home = remoteHome ?: return@withLock false
        val aiverseDir = home.trimEnd('/') + "/.aiverse"
        val wsRoot = workspaceRoot.trimEnd('/').ifEmpty { return@withLock false }
        withContext(Dispatchers.IO) {
            runCatching {
                sftpOperationMutex.withLock { sftp().mkdirs(aiverseDir) }

                val creds = credentials.joinToString("\n") { c ->
                    "https://${enc(c.username)}:${enc(c.token)}@${c.host}"
                }.let { if (it.isNotEmpty()) "$it\n" else "" }

                // 上传 credential helper 脚本（本地由 ContainerInstaller 提取），配置为唯一顶层 helper；
                // 服务器上没有 App，用 AIVERSE_CRED_NO_PROMPT 让未命中时直接空退出而非等待弹窗。
                containerInstaller.extractCredentialHelper()
                val helperScript = java.io.File(containerInstaller.aiverseDir, "git-credential-aiverse").readText()
                check(helperScript.isNotBlank()) { "Git credential helper is empty" }
                val remoteHelperScript = helperScript.replaceFirst(
                    "#!/bin/sh\n",
                    "#!/bin/sh\nexport AIVERSE_CRED_NO_PROMPT=1\n"
                )
                writeRemoteFile("$aiverseDir/git-credential-aiverse", remoteHelperScript.toByteArray(Charsets.UTF_8), executable = true)
                    ?: return@runCatching false
                // store 文件 chmod 600（SFTP 不支持 setfacl）；chmod 失败则回滚，不留下明文风险。
                val credWritten = writeRemoteFile("$aiverseDir/git-credentials", encodeCreds(creds).toByteArray(Charsets.UTF_8))
                if (credWritten == null || !credWritten) {
                    runCatching { sftpOperationMutex.withLock { sftp().rm("$aiverseDir/git-credentials") } }
                    return@runCatching false
                }
                runCatching { sftpOperationMutex.withLock { sftp().chmod("$aiverseDir/git-credentials", 384) } }
                    .onFailure { FileLogger.w(TAG, "设置 git-credentials 权限失败", it) }

                val gitconfig = buildString {
                    append("[include]\n")
                    append("    path = ~/.gitconfig\n")
                    append("[credential]\n")
                    append("    helper = !AIVERSE_CRED_NO_PROMPT=1 $aiverseDir/git-credential-aiverse\n")
                    append("[includeIf \"gitdir:${gitConfigValue(wsRoot)}/\"]\n")
                    append("    path = $aiverseDir/gitconfig.credential\n")
                }
                writeRemoteFile("$aiverseDir/gitconfig", gitconfig.toByteArray(Charsets.UTF_8))
                    ?: return@runCatching false

                val credentialConfig = buildString {
                    append("[credential]\n")
                    append("    helper = !AIVERSE_CRED_NO_PROMPT=1 $aiverseDir/git-credential-aiverse\n")
                }
                writeRemoteFile("$aiverseDir/gitconfig.credential", credentialConfig.toByteArray(Charsets.UTF_8))
                    ?: return@runCatching false
                FileLogger.i(TAG, "已注入 git 凭据到远程 $aiverseDir（限定 $wsRoot/）")
                true
            }.onFailure { FileLogger.w(TAG, "注入 git 凭据到远程失败", it) }.getOrDefault(false)
        }
    }

    /**
     * 撤销远程注入：删除 App 管理的配置/凭据文件。成功返回 true，失败记录日志并返回 false。
     */
    suspend fun removeGitCredentialConfig(): Boolean = credentialSyncMutex.withLock {
        val client = sshClient ?: return@withLock false
        val home = remoteHome ?: return@withLock false
        val aiverseDir = home.trimEnd('/') + "/.aiverse"
        withContext(Dispatchers.IO) {
            runCatching {
                sftpOperationMutex.withLock {
                    val sftp = sftp()
                    listOf("gitconfig", "gitconfig.credential", "git-credentials", "git-credential-aiverse").forEach { name ->
                        val path = "$aiverseDir/$name"
                        if (sftp.statExistence(path) != null) sftp.rm(path)
                    }
                }
                FileLogger.i(TAG, "已撤销远程 git 凭据注入")
                true
            }.onFailure { FileLogger.w(TAG, "撤销远程 git 凭据注入失败", it) }.getOrDefault(false)
        }
    }

    /**
     * 写远程文件。成功返回 true；失败记录日志并返回 false（不抛异常，让调用方决定回滚）。
     */
    private suspend fun writeRemoteFile(
        dest: String,
        bytes: ByteArray,
        executable: Boolean = false,
        overwrite: Boolean = true
    ): Boolean = sftpOperationMutex.withLock {
        val client = sftp()
        val temporary = "$dest.tmp.${UUID.randomUUID()}"
        try {
            client.open(temporary, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).use { file ->
                client.chmod(temporary, if (executable) 448 else 384)
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(32 * 1024, bytes.size - offset)
                    file.write(offset.toLong(), bytes, offset, count)
                    offset += count
                }
            }
            check(client.size(temporary) == bytes.size.toLong()) { "Remote file size mismatch" }
            if (overwrite) {
                client.rename(temporary, dest, EnumSet.of(RenameFlags.ATOMIC, RenameFlags.OVERWRITE))
            } else if (client.statExistence(dest) == null) {
                client.rename(temporary, dest)
            }
            true
        } catch (e: Exception) {
            FileLogger.w(TAG, "写远程文件失败: $dest", e)
            false
        } finally {
            runCatching { client.rm(temporary) }
        }
    }

    /**
     * 读远程文本文件，无文件或读失败返回 null。供 [writeGitExclude]/[removeGitExclude] 按内容精准增删。
     */
    private suspend fun readRemoteFile(path: String): String? = sftpOperationMutex.withLock {
        val client = sftp()
        runCatching {
            if (client.statExistence(path) == null) return@withLock null
            client.open(path, EnumSet.of(OpenMode.READ)).use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(32 * 1024)
                var offset = 0L
                while (true) {
                    val n = input.read(offset, chunk, 0, chunk.size)
                    if (n < 0) break
                    if (n == 0) continue
                    buffer.write(chunk, 0, n)
                    offset += n
                }
                buffer.toString("UTF-8")
            }
        }.onFailure { FileLogger.w(TAG, "读远程文件失败: $path", it) }.getOrNull()
    }

    /**
     * 写 `<workspaceRoot>/.git/info/exclude`：让「仅本工作区」的仓库忽略 App 管理的本地配置文件
     * （如 git 凭据 `[credential]` 分包），并附 hash 备注供下次按内容精准剔除。幂等：已含该行时不重复追加。
     */
    internal suspend fun writeGitExclude(workspaceRoot: String) {
        val root = workspaceRoot.trimEnd('/').ifEmpty { return }
        val target = "$root/.git/info/exclude"
        withContext(Dispatchers.IO) {
            runCatching {
                val hash = BuiltinDocsSync.sha256(GIT_EXCLUDE_LINE.toByteArray(Charsets.UTF_8))
                val existing = readRemoteFile(target)
                if (existing != null && existing.contains(GIT_EXCLUDE_LINE)) {
                    // 已存在时确保 hash 备注与实际内容一致，便于下次按内容精准剔除
                    writeRemoteFile(target, buildExclude(existing, hash).toByteArray(Charsets.UTF_8))
                    return@runCatching
                }
                val content = if (existing != null) {
                    ensureTrailingNewline(existing) + GIT_EXCLUDE_LINE + "\n"
                } else {
                    GIT_EXCLUDE_LINE + "\n"
                }
                writeRemoteFile(target, buildExclude(content, hash).toByteArray(Charsets.UTF_8))
                FileLogger.i(TAG, "已写 $target（限定 $root/.git/info/exclude）")
            }.onFailure { FileLogger.w(TAG, "写 git exclude 失败: $root", it) }
        }
    }

    /**
     * 清理 `<workspaceRoot>/.git/info/exclude` 中由 App 写入的段落（按 hash 备注或已知前缀匹配），
     * 保留用户自己的 exclude 规则。仅动与 [GIT_EXCLUDE_LINE] 相关的行。
     */
    internal suspend fun removeGitExclude(workspaceRoot: String) {
        val root = workspaceRoot.trimEnd('/').ifEmpty { return }
        val target = "$root/.git/info/exclude"
        withContext(Dispatchers.IO) {
            runCatching {
                val content = readRemoteFile(target) ?: return@runCatching
                val hash = readExcludeHash(target)
                if (hash != null) {
                    val cleaned = stripExcludeSection(content, hash)
                    if (cleaned != content) writeRemoteFile(target, cleaned.toByteArray(Charsets.UTF_8))
                    return@runCatching
                }
                // 无 hash 备注的历史遗留：按已知前缀整行剔除
                if (content.contains(GIT_EXCLUDE_LINE)) {
                    writeRemoteFile(target, content.replace(GIT_EXCLUDE_LINE + "\n", "").toByteArray(Charsets.UTF_8))
                }
            }.onFailure { FileLogger.w(TAG, "清理 git exclude 失败: $root", it) }
        }
    }

    /** 读 exclude 里 App 段落的 hash 备注，无则 null。 */
    private suspend fun readExcludeHash(workspaceRoot: String): String? {
        val root = workspaceRoot.trimEnd('/').ifEmpty { return null }
        val content = readRemoteFile("$root/.git/info/exclude") ?: return null
        val marker = "# aiverse:credential-exclude:"
        val line = content.lineSequence().firstOrNull { it.startsWith(marker) } ?: return null
        return line.substringAfter(marker).trim().takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    }

    private fun buildExclude(content: String, hash: String): String {
        val base = content.replace(Regex("# aiverse:credential-exclude:[0-9a-f]{64}[\\t ]*\\n?"), "")
        return ensureTrailingNewline(base) + "# aiverse:credential-exclude:$hash\n"
    }

    private fun stripExcludeSection(content: String, hash: String): String =
        content.replace(GIT_EXCLUDE_LINE + "\n", "")
            .replace(Regex("# aiverse:credential-exclude:${Regex.escape(hash)}[\\t ]*\\n?"), "")

    private fun ensureTrailingNewline(text: String): String =
        if (text.endsWith("\n")) text else "$text\n"

    /** gitconfig 值转义：转义反斜杠与双引号并去换行，避免工作区路径注入结构性配置项。 */
    private fun gitConfigValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")

    private fun enc(part: String): String = java.net.URLEncoder.encode(part, "UTF-8")

    /** 与 app 侧一致的编码：base64 后整体反转（属混淆，非加密）。 */
    private fun encodeCreds(plain: String): String =
        android.util.Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP).reversed()

    internal companion object {
        /** 写进 `<root>/.git/info/exclude` 的那行：把 App 管理的本地 credential 配置文件加入 git 忽略。 */
        internal const val GIT_EXCLUDE_LINE = "/.git/config.aiverse-credential"
    }
}

/** 一条端口映射：底层 SSH transport + 本地监听。[close] 关闭监听并断开专属 transport。 */
internal class PortForwardHandle internal constructor(
    private val client: SSHClient,
    private val forwarder: LocalPortForwarder,
    /** 本机监听用的 server socket，与 forwarder 一并关闭。 */
    private val serverSocket: ServerSocket,
    /** 本机实际绑定的监听端口（127.0.0.1 上，由 OS 分配）。 */
    val localPort: Int
) {
    fun close() {
        runCatching { forwarder.close() }
        runCatching { serverSocket.close() }
        runCatching { client.disconnect() }
    }
}

/**
 * 内置文档同步的远端后端：把 [BuiltinDocsSync] 的动作落到 SFTP，并保证路径安全与原子替换。
 * 所有路径先经合法性校验（白名单前缀 + 无 `..`/绝对路径），再经临时文件替换、旧版备份。
 */
private class DocsSftpBackend(
    private val connection: RemoteSshConnection,
    private val root: String
) : BuiltinDocsSync.Backend {

    private val sftp get() = runBlocking { connection.sftp() }

    /** 把快照内的相对路径（如 `guide/overview.md`）映射为远程文档目录下的绝对路径。 */
    private fun resolve(path: String): String {
        val base = root.trimEnd('/')
        val rel = path.trimStart('/')
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    override fun type(path: String): BuiltinDocsSync.EntryType? {
        val attrs = sftp.statExistence(resolve(path)) ?: return null
        return when (attrs.type) {
            FileMode.Type.REGULAR -> BuiltinDocsSync.EntryType.FILE
            FileMode.Type.DIRECTORY -> BuiltinDocsSync.EntryType.DIRECTORY
            FileMode.Type.SYMLINK -> BuiltinDocsSync.EntryType.SYMLINK
            else -> BuiltinDocsSync.EntryType.OTHER
        }
    }

    override fun readFile(path: String): ByteArray = readAll(resolve(path))

    override fun fileHash(path: String): String = BuiltinDocsSync.sha256(readFile(path))

    override fun createDirectory(path: String) {
        sftp.mkdirs(resolve(path))
    }

    override fun replaceAtomically(path: String, bytes: ByteArray) {
        val remote = resolve(path)
        val tmp = remote + ".tmp." + UUID.randomUUID()
        try {
            sftp.open(tmp, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).use { f ->
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(32 * 1024, bytes.size - offset)
                    f.write(offset.toLong(), bytes, offset, n)
                    offset += n
                }
            }
            sftp.rename(tmp, remote, EnumSet.of(RenameFlags.ATOMIC, RenameFlags.OVERWRITE))
        } finally {
            runCatching { sftp.rm(tmp) }
        }
    }

    override fun backupFile(path: String, backupPath: String, expectedHash: String) {
        val from = resolve(path)
        val to = resolve(backupPath)
        if (sftp.statExistence(from) == null) return
        check(hashOf(from) == expectedHash) { "Built-in docs backup hash mismatch" }
        sftp.rename(from, to, EnumSet.of(RenameFlags.ATOMIC, RenameFlags.OVERWRITE))
    }

    override fun cleanupTemporary(path: String) {
        val remote = resolve(path)
        runCatching { sftp.rm(remote + ".tmp.*") }
    }

    override fun deleteFile(path: String) {
        sftp.rm(resolve(path))
    }

    private fun hashOf(path: String): String = BuiltinDocsSync.sha256(readAll(path))

    /** 读取远程文件全部字节：SFTPClient 无直接 read-to-ByteArray，经 [RemoteFile] 随机读拼接。 */
    private fun readAll(remote: String): ByteArray =
        sftp.open(remote).use { rf ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            var offset = 0L
            while (true) {
                val n = rf.read(offset, buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
                offset += n
            }
            out.toByteArray()
        }
}

/** 远程 SSH 连接状态，供 UI 指示器与工作区初始化时序判断。 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/** 把 SSH 连接异常翻译成友好提示，供工具层回显给 AI/用户。 */
fun friendlySshError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is KeyDecryptionFailedException ->
            "SSH 认证失败：私钥口令不正确或私钥格式不受支持（$msg）"
        e is UserAuthException ->
            "SSH 认证失败：用户名、密码或密钥未被服务器接受（$msg）"
        "ECONNREFUSED" in msg || "Connection refused" in msg ->
            "无法连接到 SSH 服务器（连接被拒绝），请确认远程服务器已启动且端口正确"
        "UnknownHost" in msg || "unknown host" in msg ->
            "无法连接到 SSH 服务器（未知主机），请检查主机地址"
        "Auth fail" in msg || "authentication" in msg ->
            "SSH 认证失败，请检查用户名和密码"
        // SFTP 访问无权限目录（如 root 家目录）也会抛 Permission denied，但这不是认证失败——
        // 认证失败已由上方 UserAuthException / Auth fail 分支覆盖，这里只可能是目录/文件权限问题。
        "Permission denied" in msg ->
            "远程目录无访问权限（Permission denied）：服务器用户对该路径没有读权限。" +
                "常见于访问 root 家目录（/root，普通用户无权访问）；请改用该用户可读的目录（如 /home/ubuntu 或 /）"
        "Network is unreachable" in msg ->
            "网络不可达，请检查网络连接"
        "SocketTimeout" in msg || "timed out" in msg ->
            "连接 SSH 服务器超时，请检查网络或服务器状态"
        "SSH 未连接" in msg ->
            "SSH 未连接，请等待连接恢复或在设置中检查配置"
        else -> "SSH 连接失败: ${e.message ?: "未知错误"}"
    }
}

/** 远程 SSH 连接配置。 */
data class RemoteConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: RemoteAuth,
    /** 远程服务器上的工作区根路径（AI 的 ~/workspace 映射到此路径）。 */
    val remoteWorkspacePath: String
)

/** 远程 exec 命令结果（独立连接）。 */
data class ExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
