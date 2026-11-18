package com.aiverse.feature.agent.domain.container

import com.aiverse.core.util.FileLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 远程端口映射管理器：让插件能访问「只能从远程服务器访问」的服务（远端内网 DB、监听 127.0.0.1 的本地服务）。
 *
 * 每条映射由 [RemoteSshConnection.openPortForward] 建一条**专属** SSH transport + 本机临时端口监听；
 * 插件请求 `requestedPort`，实际拿到的是本机 `127.0.0.1:[localPort]`（OS 分配），连接它即转发到远端目标。
 * 按「会话 + 插件」分组，进程退出时 [closeAll] 统一回收，防泄漏。
 *
 * 幂等：同一 `(sessionKey, requestedPort)` 重复 forward 复用已有映射，返回同一 localPort。
 */
@Singleton
class PortForwardManager @Inject constructor(
    private val connection: RemoteSshConnection
) {
    /** 一条活跃映射。 */
    data class PortMapping(
        val requestedPort: Int,
        val localPort: Int,
        val targetHost: String,
        val targetPort: Int
    )

    private val mutex = Mutex()

    // sessionKey -> (requestedPort -> mapping)
    private val forwards = mutableMapOf<String, MutableMap<Int, PortMapping>>()
    private val handles = mutableMapOf<String, MutableMap<Int, PortForwardHandle>>()

    /**
     * 建立/复用一条端口映射。返回本机实际可连的端口（`127.0.0.1:[localPort]`）。
     * 目标 [targetHost] 相对**远程服务器**解析（如 `127.0.0.1` 指服务器本机、内网域名指服务器可达的内网）。
     */
    suspend fun forward(
        sessionKey: String,
        requestedPort: Int,
        targetHost: String,
        targetPort: Int
    ): Int = mutex.withLock {
        forwards[sessionKey]?.get(requestedPort)?.let { return@withLock it.localPort }
        val handle = connection.openPortForward(targetHost, targetPort)
        val mapping = PortMapping(requestedPort, handle.localPort, targetHost, targetPort)
        forwards.getOrPut(sessionKey) { mutableMapOf() }[requestedPort] = mapping
        handles.getOrPut(sessionKey) { mutableMapOf() }[requestedPort] = handle
        FileLogger.i(
            TAG,
            "端口映射 $sessionKey:$requestedPort -> 127.0.0.1:${handle.localPort} (target $targetHost:$targetPort)"
        )
        handle.localPort
    }

    /** 关闭一条映射。返回是否真的关掉了（未找到该映射返回 false）。 */
    suspend fun close(sessionKey: String, requestedPort: Int): Boolean = mutex.withLock {
        val mapping = forwards[sessionKey]?.remove(requestedPort) ?: return@withLock false
        handles[sessionKey]?.remove(requestedPort)?.let { handle ->
            runCatching { handle.close() }.onFailure { FileLogger.w(TAG, "关闭端口映射失败 $sessionKey:$requestedPort", it) }
        }
        if (forwards[sessionKey]?.isEmpty() == true) forwards.remove(sessionKey)
        if (handles[sessionKey]?.isEmpty() == true) handles.remove(sessionKey)
        FileLogger.i(TAG, "已关闭端口映射 $sessionKey:$requestedPort (local ${mapping.localPort})")
        true
    }

    /** 列出某会话的活跃映射（按 requestedPort 升序）。 */
    suspend fun mappings(sessionKey: String): List<PortMapping> = mutex.withLock {
        forwards[sessionKey]?.values?.sortedBy { it.requestedPort }.orEmpty()
    }

    /** 回收某会话的全部映射（进程退出/会话结束调用），防端口与 transport 泄漏。 */
    suspend fun closeAll(sessionKey: String) = mutex.withLock {
        handles.remove(sessionKey)?.values?.forEach { handle -> runCatching { handle.close() } }
        forwards.remove(sessionKey)
        Unit
    }

    private companion object {
        const val TAG = "PortForwardManager"
    }
}
