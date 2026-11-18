package com.aiverse.feature.plugins.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.core.util.FileLogger
// P3a（feature/plugins/data/index）已提供：PluginIndexClient / PluginIndexSourceRepository /
// PluginIndexPlugin / PluginIndexSource / PluginOfficialRepositories / NoMatchingReleaseException。
import com.aiverse.feature.plugins.data.index.NoMatchingReleaseException
import com.aiverse.feature.plugins.data.index.PluginIndexClient
import com.aiverse.feature.plugins.data.index.PluginIndexException
import com.aiverse.feature.plugins.data.index.PluginIndexPlugin
import com.aiverse.feature.plugins.data.index.PluginIndexSource
import com.aiverse.feature.plugins.data.index.PluginOfficialRepositories
import com.aiverse.feature.plugins.data.index.PluginIndexSourceRepository
import com.aiverse.feature.plugins.domain.PluginError
import com.aiverse.feature.plugins.domain.PluginErrorCode
import com.aiverse.feature.plugins.domain.PluginInstallOrigin
import com.aiverse.feature.plugins.domain.PluginInstallResult
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.PluginPreview
import com.aiverse.feature.plugins.domain.PluginPreviewResult
import com.aiverse.feature.plugins.domain.PluginUpdateLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 商店列表条目：一条索引记录 + 该插件在本机的安装状态。
 *
 * [plugin] / [source] 为 P3a 的索引模型；字段（id / name / description / author /
 * repository / tags / version / minAppVersion / license / homepage）已核对一致。
 */
data class PluginStoreItem(
    val plugin: PluginIndexPlugin,
    val source: PluginIndexSource,
    val installedVersion: String?,
    val updateAvailable: Boolean,
    val compatible: Boolean,
    /** 插件源码仓库是否归属 AiVerse 官方账号。 */
    val official: Boolean
)

/**
 * 一条待确认的在线安装：预览结果 + 在线来源 + 需要使用者核对的源码仓库。
 *
 * TODO(P3a): [preview] 的 [com.aiverse.feature.plugins.domain.PluginPreview] 字段结构
 * （token / manifest / content / installedVersion / installedSourceId / installedRepository /
 * installedOfficial）沿用 AiVerse 领域层。
 */
data class PluginStorePreview(
    val preview: PluginPreview,
    val origin: PluginInstallOrigin,
    val expectedRepository: String?,
    val sourceLabel: String,
    val officialSource: Boolean
)

enum class PluginStoreErrorCode {
    NETWORK,
    INVALID_INDEX,
    NO_COMPATIBLE_RELEASE,
    DOWNLOAD_FAILED,
    PREVIEW_FAILED
}

data class PluginStoreError(
    val code: PluginStoreErrorCode,
    val detail: String? = null,
    val pluginError: PluginError? = null,
    /** 安装/下载错误所属的插件；索引错误为 null。 */
    val pluginId: String? = null
)

data class PluginStoreUiState(
    /** 首次读取来源前就显示加载态，避免先闪一下空列表。 */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sources: List<PluginIndexSource> = emptyList(),
    val items: List<PluginStoreItem> = emptyList(),
    /** 每个已安装插件可用的最高版本更新条目；供已安装页展示「可更新」。 */
    val updatesByPluginId: Map<String, PluginStoreItem> = emptyMap(),
    val error: PluginStoreError? = null,
    val installingPluginId: String? = null,
    /** 正在为分享而下载/准备插件包的插件 id；非空时分享按钮转圈并禁用，防重复点击。 */
    val sharingPluginId: String? = null,
    val preview: PluginStorePreview? = null,
    /** 一键更新全部：本轮待更新总数；> 0 表示批量更新进行中，供界面显示「更新中 x/N」。 */
    val updatingTotal: Int = 0,
    /** 一键更新全部：剩余未处理数（含正在处理的那一个）。 */
    val updatingRemaining: Int = 0,
    /** 一键更新全部：本轮最终失败的插件数，批量结束后由界面提示。 */
    val updateAllFailures: Int = 0
)

/**
 * 插件商店：按来源顺序拉取索引、聚合条目，并负责下载与预览。
 *
 * 依赖 [PluginIndexClient] / [PluginIndexSourceRepository]（P3a），已核对
 * `fetchIndex` / `isCompatible` / `resolveRelease` / `download` 与
 * `PluginIndexSourceRepository.sources` / `markFetched` 的签名一致。
 */
@HiltViewModel
class PluginStoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: PluginIndexClient,
    private val sourceRepository: PluginIndexSourceRepository,
    private val manager: PluginManager
) : ViewModel() {

    private val _state = MutableStateFlow(PluginStoreUiState())
    val state: StateFlow<PluginStoreUiState> = _state.asStateFlow()

    /** 一次加载结束后要提示的失败来源数；仅在用户下拉刷新或确实有来源失败时发送。 */
    private val _refreshResult = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val refreshResult: SharedFlow<Int> = _refreshResult.asSharedFlow()

    /** 分享结果：成功携带本地插件包文件（已按商店包原名放好），失败携带原因。界面收到后拉系统分享。 */
    private val _shareResult = MutableSharedFlow<Result<File>>(extraBufferCapacity = 1)
    val shareResult: SharedFlow<Result<File>> = _shareResult.asSharedFlow()

    /** 来源地址列表变化时才重新拉取；markFetched 等只改时间戳的写入不会触发加载。 */
    private var loadedUrls: List<String>? = null
    private var loadToken = 0

    init {
        viewModelScope.launch {
            sourceRepository.sources.collect { sources ->
                _state.update { it.copy(sources = sources) }
                val urls = sources.map { it.indexUrl }
                if (urls != loadedUrls) {
                    loadedUrls = urls
                    loadAll(force = false)
                }
            }
        }
        viewModelScope.launch {
            manager.plugins.collect { installed ->
                val versions = installed.associate { it.id to it.record.version }
                _state.update { current ->
                    val updatedItems = current.items.map { item ->
                        val version = versions[item.plugin.id]
                        item.copy(
                            installedVersion = version,
                            updateAvailable = isNewer(item.plugin.version, version)
                        )
                    }
                    current.copy(
                        items = updatedItems,
                        updatesByPluginId = updatesOf(updatedItems)
                    )
                }
            }
        }
    }

    fun refresh() {
        loadAll(force = true)
    }

    /** 按来源顺序依次拉取，条目也按来源顺序叠加；单个来源失败时保留它上次的条目。 */
    private fun loadAll(force: Boolean) {
        val sources = _state.value.sources
        if (sources.isEmpty()) return
        val token = ++loadToken
        _state.update { it.copy(loading = true, refreshing = it.items.isNotEmpty(), error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val installed = manager.plugins.value.associate { it.id to it.record.version }
            val previous = _state.value.items.groupBy { it.source.id }
            val items = mutableListOf<PluginStoreItem>()
            var firstError: PluginStoreError? = null
            var failureCount = 0
            sources.forEach { source ->
                val result = runCatching { client.fetchIndex(source, forceRefresh = force) }
                if (token != loadToken) return@launch
                result.onSuccess { snapshot ->
                    items += snapshot.index.plugins.map { plugin ->
                        val installedVersion = installed[plugin.id]
                        PluginStoreItem(
                            plugin = plugin,
                            source = source,
                            installedVersion = installedVersion,
                            updateAvailable = isNewer(plugin.version, installedVersion),
                            compatible = client.isCompatible(plugin),
                            official = PluginOfficialRepositories.isOfficialAuthor(plugin.author)
                        )
                    }
                    if (!snapshot.stale) {
                        sourceRepository.markFetched(source.id, snapshot.fetchedAt)
                    }
                }.onFailure { e ->
                    FileLogger.w(TAG, "读取插件索引失败: ${source.id}", e)
                    failureCount++
                    if (firstError == null) {
                        firstError = PluginStoreError(
                            code = if (e is PluginIndexException) {
                                PluginStoreErrorCode.INVALID_INDEX
                            } else {
                                PluginStoreErrorCode.NETWORK
                            },
                            detail = e.message
                        )
                    }
                    items += previous[source.id].orEmpty()
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    items = items,
                    updatesByPluginId = updatesOf(items),
                    error = firstError
                )
            }
            if (force || failureCount > 0) {
                _refreshResult.tryEmit(failureCount)
            }
        }
    }

    /**
     * 下载指定条目的资产并只做预览，不落地安装；确认由界面层（预览弹层）触发。
     *
     * `client.resolveRelease` / `client.download` 返回的资产与包文件结构以 P3a 为准（已核对）。
     */
    fun install(item: PluginStoreItem) {
        if (!item.compatible) return
        if (_state.value.installingPluginId != null) return
        _state.update { it.copy(installingPluginId = item.plugin.id, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val asset = client.resolveRelease(item.plugin)
                val packageFile = client.download(asset)
                val result = packageFile.file.inputStream().use { manager.preview(it) }
                when (result) {
                    is PluginPreviewResult.Ready -> {
                        val preview = result.preview
                        val manifestVersion = preview.manifest.version.toString()
                        if (preview.manifest.id != item.plugin.id ||
                            preview.manifest.publisher != item.plugin.author ||
                            preview.manifest.repository != item.plugin.repository ||
                            asset.version != manifestVersion
                        ) {
                            manager.discard(preview.token)
                            _state.update {
                                it.copy(
                                    installingPluginId = null,
                                    error = PluginStoreError(
                                        code = PluginStoreErrorCode.PREVIEW_FAILED,
                                        pluginError = consistencyError(preview, item, manifestVersion),
                                        pluginId = item.plugin.id
                                    )
                                )
                            }
                            return@launch
                        }
                        val official = item.official
                        _state.update {
                            it.copy(
                                installingPluginId = null,
                                preview = PluginStorePreview(
                                    preview = preview,
                                    origin = PluginInstallOrigin(
                                        kind = PluginInstallOrigin.KIND_INDEX,
                                        sourceId = item.source.id,
                                        sourceName = item.source.name,
                                        indexUrl = item.source.indexUrl,
                                        repository = item.plugin.repository,
                                        official = official
                                    ),
                                    expectedRepository = if (official) null else item.plugin.repository,
                                    sourceLabel = item.source.name,
                                    officialSource = official
                                )
                            )
                        }
                    }
                    is PluginPreviewResult.Failed -> _state.update {
                        it.copy(
                            installingPluginId = null,
                            error = PluginStoreError(
                                code = PluginStoreErrorCode.PREVIEW_FAILED,
                                pluginError = result.errors.firstOrNull(),
                                pluginId = item.plugin.id
                            )
                        )
                    }
                }
            } catch (e: NoMatchingReleaseException) {
                _state.update {
                    it.copy(
                        installingPluginId = null,
                        error = PluginStoreError(
                            code = PluginStoreErrorCode.NO_COMPATIBLE_RELEASE,
                            detail = e.message,
                            pluginId = item.plugin.id
                        )
                    )
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "下载插件失败: ${item.plugin.id}", e)
                _state.update {
                    it.copy(
                        installingPluginId = null,
                        error = PluginStoreError(
                            code = PluginStoreErrorCode.DOWNLOAD_FAILED,
                            detail = e.message,
                            pluginId = item.plugin.id
                        )
                    )
                }
            }
        }
    }

    fun consumePreview() {
        _state.update { it.copy(preview = null) }
    }

    /**
     * 分享指定条目的插件包 zip：先确保包在本地（未缓存就按 install 同一条下载链路下载），
     * 再拷到 cacheDir/share/ 并保留商店包原名（{id}_{version}.zip），然后把文件发给界面层。
     *
     * 文件名用 [DownloadedPluginPackage.asset.name]（索引里的原文件名），不改名；
     * 界面层拿文件经 FileProvider 授权后发 ACTION_SEND，收件方保存时看到的还是原名。
     * 全程走 IO 调度器，不阻塞主线程；结果经 [shareResult] 交界面，成功给文件、失败给原因。
     */
    fun share(item: PluginStoreItem) {
        if (!item.compatible) return
        val pluginId = item.plugin.id
        // 已在分享/安装中就忽略，避免并发下载同一包。
        if (_state.value.sharingPluginId != null || _state.value.installingPluginId != null) return
        _state.update { it.copy(sharingPluginId = pluginId) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val downloaded = client.download(client.resolveRelease(item.plugin))
                // 下载缓存名是哈希（plugin-{sha}.zip），分享要保留商店包原名，所以拷一份到 share 目录。
                val shareDir = File(context.cacheDir, "share")
                shareDir.mkdirs()
                val shareFile = File(shareDir, downloaded.asset.name)
                downloaded.file.copyTo(shareFile, overwrite = true)
                shareFile
            }.onFailure { e ->
                FileLogger.w(TAG, "准备分享插件包失败: ${item.plugin.id}", e)
            }
            _state.update { it.copy(sharingPluginId = null) }
            _shareResult.tryEmit(result)
        }
    }

    /**
     * 一致性校验不通过时，构造可定位到具体字段的 [PluginError]。
     *
     * 判定（比较方式）由调用处的 if 条件决定，这里只负责把已经判定出的不匹配字段翻成
     * 带原因的错误，避免 UI 走笼统的「插件包无法读取」兜底文案。
     */
    private fun consistencyError(
        preview: PluginPreview,
        item: PluginStoreItem,
        assetVersion: String
    ): PluginError {
        val manifest = preview.manifest
        return when {
            manifest.repository != item.plugin.repository -> PluginError(
                PluginErrorCode.REPOSITORY_MISMATCH,
                // detail：包内地址 \n 商店登记地址；repository URL 不会含换行，界面按此拆分展示。
                "${manifest.repository}\n${item.plugin.repository}"
            )
            manifest.id != item.plugin.id -> PluginError(
                PluginErrorCode.FIELD_INVALID,
                "id: ${manifest.id} != ${item.plugin.id}"
            )
            manifest.publisher != item.plugin.author -> PluginError(
                PluginErrorCode.FIELD_INVALID,
                "publisher: ${manifest.publisher} != ${item.plugin.author}"
            )
            else -> PluginError(
                PluginErrorCode.FIELD_INVALID,
                "version: ${manifest.version} != $assetVersion"
            )
        }
    }

    /** 一键更新全部是否正在进行，供界面禁用按钮防重复点击。 */
    val isUpdatingAll: Boolean
        get() = _state.value.updatingTotal > 0

    /**
     * 一键更新全部：串行逐个把 [updatesByPluginId] 里的可升级插件下到最新版。
     *
     * 复用单插件的下载→预览→校验链路（[resolveAndUpdate]），但预览通过后直接提交安装，
     * 不再弹出确认层。严格串行：等前一个完成（无论成败）再处理下一个，不并发装包。
     * 单个失败只记数不阻断后续；全部结束后 [updateAllFailures] 汇报失败数，
     * 已安装列表变化会由 [manager.plugins] 收集重算 updateAvailable，从而刷新可升级集合。
     */
    fun updateAll() {
        val targets = _state.value.updatesByPluginId.values.toList()
        if (targets.isEmpty()) return
        // 已在进行中（或正在单个安装）时忽略，防重复点击。
        if (_state.value.updatingTotal > 0 || _state.value.installingPluginId != null) return
        _state.update {
            it.copy(
                updatingTotal = targets.size,
                updatingRemaining = targets.size,
                updateAllFailures = 0,
                error = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            var failures = 0
            for ((index, item) in targets.withIndex()) {
                val ok = resolveAndUpdate(item)
                if (!ok) failures++
                _state.update { current ->
                    current.copy(updatingRemaining = (targets.size - index - 1).coerceAtLeast(0))
                }
            }
            _state.update {
                it.copy(
                    updatingTotal = 0,
                    updatingRemaining = 0,
                    updateAllFailures = failures
                )
            }
        }
    }

    /** 清除一键更新的失败计数提示（界面展示后调用）。 */
    fun consumeUpdateAllFailures() {
        _state.update { it.copy(updateAllFailures = 0) }
    }

    /**
     * 单个可升级插件的静默更新：下载→预览→校验→提交安装。
     * 返回是否更新成功；预览校验失败或下载异常均视为失败，不抛出。
     */
    private suspend fun resolveAndUpdate(item: PluginStoreItem): Boolean {
        if (!item.compatible) return false
        try {
            val asset = client.resolveRelease(item.plugin)
            val packageFile = client.download(asset)
            val result = packageFile.file.inputStream().use { manager.preview(it) }
            val preview = when (result) {
                is PluginPreviewResult.Ready -> result.preview
                is PluginPreviewResult.Failed -> {
                    FileLogger.w(TAG, "批量更新预览失败: ${item.plugin.id}")
                    return false
                }
            }
            val manifestVersion = preview.manifest.version.toString()
            if (preview.manifest.id != item.plugin.id ||
                preview.manifest.publisher != item.plugin.author ||
                preview.manifest.repository != item.plugin.repository ||
                asset.version != manifestVersion
            ) {
                manager.discard(preview.token)
                // 批量更新也不丢具体原因：透传到 state.error，由已安装页红字展示（pluginId 匹配）。
                _state.update {
                    it.copy(
                        error = PluginStoreError(
                            code = PluginStoreErrorCode.PREVIEW_FAILED,
                            pluginError = consistencyError(preview, item, manifestVersion),
                            pluginId = item.plugin.id
                        )
                    )
                }
                FileLogger.w(TAG, "批量更新校验不通过: ${item.plugin.id}")
                return false
            }
            val official = item.official
            val origin = PluginInstallOrigin(
                kind = PluginInstallOrigin.KIND_INDEX,
                sourceId = item.source.id,
                sourceName = item.source.name,
                indexUrl = item.source.indexUrl,
                repository = item.plugin.repository,
                official = official
            )
            return when (val installed = manager.install(preview.token, origin)) {
                is PluginInstallResult.Installed -> true
                is PluginInstallResult.Failed -> {
                    FileLogger.w(TAG, "批量更新安装失败: ${item.plugin.id} ${installed.error.code}")
                    false
                }
            }
        } catch (e: NoMatchingReleaseException) {
            FileLogger.w(TAG, "批量更新无可用版本: ${item.plugin.id}", e)
            return false
        } catch (e: Exception) {
            FileLogger.w(TAG, "批量更新下载失败: ${item.plugin.id}", e)
            return false
        }
    }

    // 版本比较规则下沉到 domain 的 PluginUpdateLogic，商店与启动静默检查共用同一实现；
    // 此处保留同名 thin 委托，最小改动、行为不变。
    private fun isNewer(candidate: String?, installed: String?): Boolean =
        PluginUpdateLogic.isNewer(candidate, installed)

    /** 每个插件取一个可用的最高版本，同一插件出现在多个来源时只提示一次。 */
    private fun updatesOf(items: List<PluginStoreItem>): Map<String, PluginStoreItem> =
        items.asSequence()
            .filter { it.updateAvailable && it.compatible }
            .groupBy { it.plugin.id }
            .mapValues { (_, candidates) ->
                candidates.maxByOrNull { item ->
                    PluginUpdateLogic.versionOrZero(item.plugin.version)
                }!!
            }

    private companion object {
        const val TAG = "PluginStoreViewModel"
    }
}