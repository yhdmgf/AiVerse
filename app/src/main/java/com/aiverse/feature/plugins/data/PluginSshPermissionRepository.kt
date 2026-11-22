package com.aiverse.feature.plugins.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.pluginSshDataStore by preferencesDataStore(
    name = "plugin_ssh_permissions",
    corruptionHandler = preferencesCorruptionHandler
)

/** 使用者对某个插件的 SSH 桥授权。 */
@Serializable
data class PluginSshGrant(val grantedAt: Long)

/**
 * 插件级 SSH 桥授权。
 *
 * 与“启用即信任”的其他插件能力不同，SSH 桥可以操作使用者配置的远程服务器，必须由使用者
 * 在插件详情里单独授权。授权后该插件可对已配置的 SSH 服务器执行任意命令，不再按命令过滤。
 */
@Singleton
class PluginSshPermissionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    val grants: Flow<Map<String, PluginSshGrant>> =
        context.pluginSshDataStore.data.map { prefs -> decode(prefs[KEY]) }

    suspend fun grant(pluginId: String, now: Long = System.currentTimeMillis()) {
        context.pluginSshDataStore.edit { prefs ->
            val next = decode(prefs[KEY]).toMutableMap()
            next[pluginId] = PluginSshGrant(grantedAt = now)
            prefs[KEY] = json.encodeToString(next)
        }
    }

    suspend fun revoke(pluginId: String) {
        context.pluginSshDataStore.edit { prefs ->
            val next = decode(prefs[KEY]).toMutableMap()
            if (next.remove(pluginId) != null) prefs[KEY] = json.encodeToString(next)
        }
    }

    suspend fun isGranted(pluginId: String): Boolean =
        grants.first().containsKey(pluginId)

    private fun decode(raw: String?): Map<String, PluginSshGrant> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, PluginSshGrant>>(raw)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("grants")
    }
}
