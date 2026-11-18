package com.aiverse.feature.plugins.domain

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.plugins.data.index.PluginIndexClient
import com.aiverse.feature.plugins.data.index.PluginIndexSource
import com.aiverse.feature.plugins.data.index.PluginIndexSourceRepository
import com.aiverse.feature.plugins.data.index.PluginOfficialRepositories
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 一条静默检查发现的「待升级插件」。
 *
 * 同时带上在线来源与源码仓库，供下游 UI 展示与一键更新直接构造
 * [PluginInstallOrigin]（KIND_INDEX），无需再回查索引。
 *
 * [official] 取源码仓库是否归属官方白名单（[PluginOfficialRepositories.isOfficial]），
 * 与商店条目 [com.aiverse.feature.plugins.presentation.PluginStoreItem.official] 语义一致：
 * 非官方仓库的在线更新仍需使用者核对来源。
 */
data class PendingPluginUpdate(
    val pluginId: String,
    val displayName: String,
    /** 索引里声明的云端版本；索引未声明版本时为 null（此时不会被判定为可升级）。 */
    val cloudVersion: String?,
    /** 本机已安装版本；理论上不为 null（未安装的插件不参与升级提示）。 */
    val installedVersion: String?,
    val repository: String,
    val source: PluginIndexSource,
    val official: Boolean
)

/**
 * 插件「启动静默更新检查」：后台遍历全部索引来源，比对本机已安装版本，
 * 收集可升级且与当前宿主版本兼容的插件。
 *
 * 静默语义：单个来源拉取失败只记日志并跳过（不整体失败），全程异常吞掉返回空列表，
 * 绝不向使用者抛错；是否提示由 [PluginUpdateNotifier] 决定。
 *
 * 复用 [PluginIndexClient.fetchIndex]（forceRefresh = false，命中缓存优先，离线可回退缓存）、
 * [PluginIndexClient.isCompatible] 与 [PluginManager.plugins]，不重复实现网络与兼容判定。
 */
@Singleton
class PluginUpdateChecker @Inject constructor(
    private val manager: PluginManager,
    private val client: PluginIndexClient,
    private val sourceRepository: PluginIndexSourceRepository
) {

    /**
     * 遍历全部来源做一次静默检查，返回当前可升级插件列表。
     *
     * 未安装的插件不参与（只关心「已装且能升」）；同一插件出现在多个来源时各来源都会产出，
     * 去重交由消费方按 pluginId 处理（与商店按来源顺序叠加的展示口径一致）。
     */
    suspend fun check(): List<PendingPluginUpdate> = try {
        val installed = manager.plugins.value.associate { it.id to it.record.version }
        val languageTag = Locale.getDefault().toLanguageTag()
        sourceRepository.sources.first().flatMap { source ->
            val snapshot = try {
                client.fetchIndex(source, forceRefresh = false)
            } catch (e: Exception) {
                FileLogger.w(TAG, "静默检查读取插件索引失败: ${source.id}", e)
                return@flatMap emptyList<PendingPluginUpdate>()
            }
            snapshot.index.plugins.mapNotNull { plugin ->
                val installedVersion = installed[plugin.id] ?: return@mapNotNull null
                if (!PluginUpdateLogic.isNewer(plugin.version, installedVersion)) return@mapNotNull null
                if (!client.isCompatible(plugin)) return@mapNotNull null
                PendingPluginUpdate(
                    pluginId = plugin.id,
                    displayName = plugin.name.resolve(languageTag),
                    cloudVersion = plugin.version,
                    installedVersion = installedVersion,
                    repository = plugin.repository,
                    source = source,
                    official = PluginOfficialRepositories.isOfficialAuthor(plugin.author)
                )
            }
        }
    } catch (e: Exception) {
        FileLogger.w(TAG, "插件静默更新检查失败", e)
        emptyList()
    }

    private companion object {
        const val TAG = "PluginUpdateChecker"
    }
}
