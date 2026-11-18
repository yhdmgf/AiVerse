package com.aiverse.feature.plugins.runtime

import com.aiverse.feature.agent.domain.container.PortForwardManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * 插件「端口映射」宿主服务：把插件的 `host.ports.*` 请求落到 [PortForwardManager]。
 *
 * 每个插件进程即一个会话（sessionKey = pluginId）。插件请求 `requestedPort`（它想用的端口），
 * 宿主返回本机实际可连端口 `localPort`（`127.0.0.1` 上，OS 分配，可能 != requestedPort）；
 * 插件连本机 `localPort` 即转发到远程服务器的 `targetHost:targetPort`。
 */
@Singleton
class PluginPortService @Inject constructor(
    private val forwardManager: PortForwardManager
) {

    /**
     * 建立一条映射。`params`：`requestedPort`(int, 必填)、`targetHost`(string, 默认 127.0.0.1)、
     * `targetPort`(int, 默认同 requestedPort)。返回本机实际端口。
     */
    suspend fun addPortMapping(pluginId: String, params: JsonObject): Int {
        val requestedPort = (params["requestedPort"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("host.ports.forward requires integer requestedPort")
        val targetHost = (params["targetHost"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TARGET_HOST
        val targetPort = (params["targetPort"] as? JsonPrimitive)?.intOrNull ?: requestedPort
        return forwardManager.forward(pluginId, requestedPort, targetHost, targetPort)
    }

    /** 关闭一条映射。`params`：`port`(int, 必填，requestedPort)。返回是否真的关掉。 */
    suspend fun removePortMapping(pluginId: String, params: JsonObject): Boolean {
        val requestedPort = (params["port"] as? JsonPrimitive)?.intOrNull
            ?: (params["requestedPort"] as? JsonPrimitive)?.intOrNull
            ?: throw IllegalArgumentException("host.ports.close requires integer port")
        return forwardManager.close(pluginId, requestedPort)
    }

    /** 列出该插件的所有活跃映射：[{requestedPort, localPort}]，按 requestedPort 升序。 */
    suspend fun listPorts(pluginId: String): List<Map<String, Int>> =
        forwardManager.mappings(pluginId).map { mapping ->
            mapOf("requestedPort" to mapping.requestedPort, "localPort" to mapping.localPort)
        }

    /** 插件进程退出时回收该插件全部映射，防端口与 SSH transport 泄漏。 */
    suspend fun release(pluginId: String) = forwardManager.closeAll(pluginId)

    private companion object {
        const val DEFAULT_TARGET_HOST = "127.0.0.1"
    }
}
