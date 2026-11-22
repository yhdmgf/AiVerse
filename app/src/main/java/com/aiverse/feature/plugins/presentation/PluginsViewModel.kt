package com.aiverse.feature.plugins.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.core.watch.FileChangeHub
import com.aiverse.core.watch.WatchFilter
import com.aiverse.feature.agent.domain.container.CommandEngine
import com.aiverse.feature.agent.domain.session.AgentRunStateStore
import com.aiverse.feature.agent.domain.shizuku.ShizukuManager
import com.aiverse.feature.agent.domain.shizuku.ShizukuState
// TODO(P2): 以下 data 层 permission/开发者/商店仓库由 AiCode 的 feature.plugin.data 提供；
// AiVerse 新插件宿主的 data 层（P2）尚未全部落地，落地后可直接编译。
import com.aiverse.feature.plugins.data.PluginBackupPermissionRepository
import com.aiverse.feature.plugins.data.PluginDevPackageBuilder
import com.aiverse.feature.plugins.data.PluginDevSettingsRepository
import com.aiverse.feature.plugins.data.PluginOverlayPermissionRepository
import com.aiverse.feature.plugins.data.PluginShizukuPermissionRepository
import com.aiverse.feature.plugins.data.PluginSshPermissionRepository
import com.aiverse.feature.plugins.data.PluginStoreSettingsRepository
// TODO(P3a): PluginIndexSourceRepository 由 AiCode 的 data.index 提供；
// AiVerse 已由 P3a 落地（data.index 目录已就绪），import 可直接解析。
import com.aiverse.feature.plugins.data.index.PluginIndexSourceRepository
import com.aiverse.feature.plugins.domain.PluginManifestParser
import com.aiverse.feature.plugins.domain.ManifestParseResult
import com.aiverse.feature.plugins.domain.InstalledPlugin
import com.aiverse.feature.plugins.domain.LocalizedText
import com.aiverse.feature.plugins.domain.PluginError
import com.aiverse.feature.plugins.domain.PluginErrorCode
import com.aiverse.feature.plugins.domain.PluginInstallOrigin
import com.aiverse.feature.plugins.domain.PluginInstallResult
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.PluginPreview
import com.aiverse.feature.plugins.domain.PluginPreviewResult
import com.aiverse.feature.plugins.domain.memoryTogglePlugin
import com.aiverse.feature.plugins.domain.newmodel.PluginPage
import com.aiverse.feature.plugins.domain.newmodel.PluginUiEntry
import com.aiverse.feature.plugins.overlay.PluginOverlayManager
import com.aiverse.feature.plugins.overlay.AppForegroundMonitor
import com.aiverse.feature.plugins.runtime.PluginDependencyResult
import com.aiverse.feature.plugins.runtime.PluginChatService
import com.aiverse.feature.plugins.runtime.PluginHttpService
import com.aiverse.feature.plugins.runtime.PluginIntrospectionService
import com.aiverse.feature.plugins.runtime.PluginModelService
import com.aiverse.feature.plugins.runtime.PluginSecretStore
import com.aiverse.feature.plugins.runtime.PluginSystemService
import com.aiverse.feature.plugins.runtime.PluginEventBus
import com.aiverse.feature.plugins.runtime.PluginHookDispatcher
import com.aiverse.feature.plugins.runtime.PluginProviderRegistrar
import com.aiverse.feature.plugins.runtime.PluginRuntimeManager
import com.aiverse.feature.plugins.runtime.PluginShizukuRunner
import com.aiverse.feature.plugins.runtime.PluginSshRunner
import com.aiverse.feature.plugins.runtime.PluginTerminalRunner
import com.aiverse.feature.plugins.runtime.PluginTipManager
import com.aiverse.feature.plugins.runtime.PluginToolService
import com.aiverse.feature.settings.data.repository.MemoryToggleRepository
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.PathHomeResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** 导入插件包的界面状态。 */
sealed interface PluginImportState {
    data object Idle : PluginImportState

    data object Reading : PluginImportState

    /**
     * 包已通过校验，等待使用者确认。
     */
    data class Preview(
        val preview: PluginPreview,
        val installing: Boolean = false,
        val error: PluginError? = null,
        /** 开发者侧载来源目录名；普通 zip 导入为 null。 */
        val sourceDirName: String? = null,
        /** 在线安装的来源；本地导入保持默认 local。 */
        val origin: PluginInstallOrigin = PluginInstallOrigin(),
        /** 非官方在线安装需要使用者手动输入该插件的源码仓库地址。 */
        val expectedRepository: String? = null,
        /** 展示用来源名称，例如「AiVerse Plugins」。 */
        val sourceLabel: String? = null,
        /** 来源是否在宿主官方白名单内；只有它为 true 时跳过手输仓库。 */
        val officialSource: Boolean = false
    ) : PluginImportState

    data class Failed(val errors: List<PluginError>) : PluginImportState
}

/** 依赖准备的界面状态；按插件 ID 隔离。 */
data class PluginDependencyUiState(
    val running: Boolean = false,
    val ready: Boolean? = null,
    val message: String? = null
)

/** 开发者目录里的一个插件项目；安装/重载按目录重新打包。 */
data class PluginDevProject(
    val dirName: String,
    val pluginId: String,
    val version: String,
    val installedVersion: String?,
    val lastModified: Long,
    val error: String? = null,
    val name: LocalizedText? = null,
    val description: LocalizedText? = null,
    val publisher: String? = null
)

/**
 * 同 ID 替换时来源是否变化：索引来源、源码仓库或官方标记任一不同，
 * 以及本地导入与在线来源互换，都算换了来源。
 */
internal fun installSourceChanged(preview: PluginPreview, origin: PluginInstallOrigin): Boolean {
    if (preview.installedVersion == null) return false
    val installedFromIndex = preview.installedSourceId != null
    val installFromIndex = origin.kind == PluginInstallOrigin.KIND_INDEX
    return installedFromIndex != installFromIndex ||
        preview.installedSourceId != origin.sourceId ||
        preview.installedRepository != origin.repository ||
        (preview.installedOfficial ?: false) != origin.official
}

/**
 * 插件设置的宿主 ViewModel：安装/启停/卸载、开发者侧载、索引仓库与镜像、依赖准备、
 * 槽位动作分发与页面桥构造。
 *
 * 移植适配说明（AiCode → AiVerse）：
 * - AiCode 的 `InstalledPlugin.status == PluginStatus.OK` 在 AiVerse 没有对应字段：
 *   AiVerse 的 [InstalledPlugin] 以 `manifest != null && content != null` 表达「清单与贡献可读」，
 *   因此 UI 侧改用 [InstalledPlugin.isActive]（含 enabled）与逐字段可空访问（[InstalledPlugin.content] 可空）。
 * - `plugin.record.enabled` 改用 AiVerse 的 [InstalledPlugin.enabled] 顶层字段。
 * - 开发者目录展示路径 `devRootPath` 由 AiCode 的 `~/.aicode/plugin-dev` 改为 `~/.aiverse/plugin-dev`。
 *
 * TODO(P2): [PluginManager.devDir] / [PluginManager.prepareDependencies] 由 AiCode 的 domain 提供，
 * AiVerse 的 PluginManager 尚未落地这两个 API（依赖准备目前由 runtime.PluginDependencyPreparer 独立承担）；
 * 开发者侧载与依赖准备落地后删除 TODO。
 * TODO(P2): 其余 data 层类型（permission/开发者/商店设置仓库、PluginDevPackageBuilder）由 AiVerse 新插件 data 层补齐。
 * 注：P3a（data.index.PluginIndexSourceRepository）与 P3c/P3d（PluginStorePreview / PluginWebBridge / PluginWebViewCache）
 * 已由并行子代理落地为同包/同源类型，可直接编译。
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: PluginManager,
    /**
     * 页面 WebView `<input type=file>` 选中文件的落盘后端：与 `/workspace-file/` 桥
     * 共用同一套 fileAccess / 工作区根，把系统选择器选中的 Uri 复制进工作区 `.aiverse/attachments/`。
     */
    val fileAccess: FileAccessProvider,
    private val pathHomeResolver: PathHomeResolver,
    private val workspaceRepository: WorkspaceRepository,
    private val fileChangeHub: FileChangeHub,
    private val commandEngine: CommandEngine,
    private val eventBus: PluginEventBus,
    private val pluginRuntimeManager: PluginRuntimeManager,
    private val pluginHookDispatcher: PluginHookDispatcher,
    private val pluginTerminalRunner: PluginTerminalRunner,
    private val pluginProviderRegistrar: PluginProviderRegistrar,
    private val shizukuPermissionRepository: PluginShizukuPermissionRepository,
    private val sshPermissionRepository: PluginSshPermissionRepository,
    private val overlayPermissionRepository: PluginOverlayPermissionRepository,
    private val backupPermissionRepository: PluginBackupPermissionRepository,
    private val overlayManager: PluginOverlayManager,
    private val shizukuManager: ShizukuManager,
    private val shizukuRunner: PluginShizukuRunner,
    private val sshRunner: PluginSshRunner,
    private val tipManager: PluginTipManager,
    private val devSettingsRepository: PluginDevSettingsRepository,
    private val indexSourceRepository: PluginIndexSourceRepository,
    private val storeSettingsRepository: PluginStoreSettingsRepository,
    private val pageEnvironment: PluginPageEnvironment,
    private val chatService: PluginChatService,
    private val modelService: PluginModelService,
    private val agentRunStateStore: AgentRunStateStore,
    private val foregroundMonitor: AppForegroundMonitor,
    private val secretStore: PluginSecretStore,
    private val systemService: PluginSystemService,
    private val introspectionService: PluginIntrospectionService,
    private val toolService: PluginToolService,
    private val permissionService: com.aiverse.feature.plugins.runtime.PluginPermissionService,
    private val sessionSourceService: com.aiverse.feature.plugins.runtime.PluginSessionHostService,
    private val httpService: PluginHttpService,
    private val providerService: com.aiverse.feature.plugins.runtime.PluginProviderService,
    private val memoryToggleRepository: MemoryToggleRepository
) : ViewModel() {

    /**
     * 已安装列表：把记忆「伪插件」以开关条目形式插在列表头部（`isMemoryToggle` 标记），
     * 其后是 [PluginManager.plugins] 的真实插件。记忆启停不进注册表，只写
     * [MemoryToggleRepository]（见 [setMemoryEnabled]），UI 据标记跳过图标/版本标签/卸载/详情。
     * `installDir` 与真实插件的安装目录同根（[PluginManager.manifestDir]），仅作占位、不落盘。
     */
    val plugins: StateFlow<List<InstalledPlugin>> =
        combine(manager.plugins, memoryToggleRepository.enabledFlow) { installed, memoryEnabled ->
            listOf(memoryTogglePlugin(enabled = memoryEnabled, pluginsRoot = manager.manifestDir)) + installed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 记忆「伪插件」的启停：只持久化到 DataStore，[plugins] 头部的记忆条目随之刷新。 */
    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { memoryToggleRepository.setEnabled(enabled) }
    }

    val loaded: StateFlow<Boolean> = manager.isLoaded

    /** 页面 WebView `/workspace-file/` 只读工作区来源；与桥共用同一套 fileAccess / 工作区根。 */
    internal val workspaceFiles: PluginWorkspaceFiles = PluginWorkspaceFiles(
        fileAccess = fileAccess,
        homeResolver = pathHomeResolver,
        workspacePath = { workspaceRepository.currentPath() }
    )

    val shizukuState: StateFlow<ShizukuState> = shizukuManager.state

    /** 已授权的插件；停用/卸载后自动从集合中消失。 */
    val shizukuGrantedPluginIds: StateFlow<Set<String>> =
        combine(manager.plugins, shizukuPermissionRepository.grants) { plugins, grants ->
            plugins.filter { plugin ->
                plugin.isActive && plugin.content?.shizuku != null && grants.containsKey(plugin.id)
            }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 已授权使用 SSH 桥的插件；停用、卸载或撤销后自动从集合中消失。 */
    val sshGrantedPluginIds: StateFlow<Set<String>> =
        combine(manager.plugins, sshPermissionRepository.grants) { plugins, grants ->
            plugins.filter { plugin ->
                plugin.isActive && plugin.content?.ssh != null && grants.containsKey(plugin.id)
            }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 已开启悬浮窗授权的插件；停用、卸载或撤销后自动从集合中消失。 */
    val overlayGrantedPluginIds: StateFlow<Set<String>> =
        combine(manager.plugins, overlayPermissionRepository.grants) { plugins, grants ->
            plugins.filter { plugin ->
                plugin.isActive && plugin.content?.overlay != null && grants.containsKey(plugin.id)
            }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 已授权接收备份 / 还原事件的插件；停用、卸载或撤销后自动从集合中消失。 */
    val backupGrantedPluginIds: StateFlow<Set<String>> =
        combine(manager.plugins, backupPermissionRepository.grants) { plugins, grants ->
            plugins.filter { plugin ->
                plugin.isActive && plugin.content?.backup != null && grants.containsKey(plugin.id)
            }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _importState = MutableStateFlow<PluginImportState>(PluginImportState.Idle)
    val importState: StateFlow<PluginImportState> = _importState.asStateFlow()

    /** 已安装插件导出结果：成功给打包好的 zip 文件（供界面层 FileProvider 分享），失败给原因。 */
    private val _exportResult = MutableSharedFlow<Result<File>>(extraBufferCapacity = 1)
    val exportResult: SharedFlow<Result<File>> = _exportResult.asSharedFlow()

    private val _dependencyStates = MutableStateFlow<Map<String, PluginDependencyUiState>>(emptyMap())
    val dependencyStates: StateFlow<Map<String, PluginDependencyUiState>> = _dependencyStates.asStateFlow()

    private val _devMode = MutableStateFlow(false)
    val devMode: StateFlow<Boolean> = _devMode.asStateFlow()

    /** 插件设置里的索引仓库输入框内容，一行一个地址。 */
    private val _indexSourceText = MutableStateFlow("")
    val indexSourceText: StateFlow<String> = _indexSourceText.asStateFlow()

    /** 插件设置里的镜像加速地址；只作用于 GitHub 托管的索引与插件包。 */
    private val _downloadMirror = MutableStateFlow("")
    val downloadMirror: StateFlow<String> = _downloadMirror.asStateFlow()

    private var indexSourceSaveJob: Job? = null
    private var downloadMirrorSaveJob: Job? = null

    /** 开发者目录固定挂在容器 `~/.aiverse/plugin-dev`，每个子目录是一个插件项目。 */
    val devRootPath: String = "~/.aiverse/plugin-dev"

    private val _devProjects = MutableStateFlow<List<PluginDevProject>>(emptyList())
    val devProjects: StateFlow<List<PluginDevProject>> = _devProjects.asStateFlow()

    private val _devRefreshing = MutableStateFlow(false)
    val devRefreshing: StateFlow<Boolean> = _devRefreshing.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var devWatcherJob: Job? = null
    private val devLastLoaded = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch(Dispatchers.IO) { manager.ensureLoaded() }
        viewModelScope.launch {
            devSettingsRepository.enabled.collect { enabled ->
                _devMode.value = enabled
                if (enabled) {
                    refreshDevProjects()
                    startDevWatcher()
                } else {
                    devWatcherJob?.cancel()
                    devWatcherJob = null
                    _devProjects.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            val urls = indexSourceRepository.customIndexUrls.first()
            if (_indexSourceText.value.isEmpty()) _indexSourceText.value = urls.joinToString("\n")
        }
        viewModelScope.launch {
            if (_downloadMirror.value.isEmpty()) {
                _downloadMirror.value = storeSettingsRepository.downloadMirror.first()
            }
        }
    }

    fun import(uri: Uri) {
        if (_importState.value !is PluginImportState.Idle && _importState.value !is PluginImportState.Failed) return
        _importState.value = PluginImportState.Reading
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                context.contentResolver.openInputStream(uri)?.use { manager.preview(it) }
                    ?: PluginPreviewResult.Failed(listOf(PluginError(PluginErrorCode.ARCHIVE_UNREADABLE)))
            } catch (e: IOException) {
                FileLogger.w(TAG, "打开插件包失败", e)
                PluginPreviewResult.Failed(listOf(PluginError(PluginErrorCode.ARCHIVE_UNREADABLE)))
            } catch (e: SecurityException) {
                FileLogger.w(TAG, "没有读取插件包的权限", e)
                PluginPreviewResult.Failed(listOf(PluginError(PluginErrorCode.ARCHIVE_UNREADABLE)))
            }
            _importState.value = when (result) {
                is PluginPreviewResult.Ready -> PluginImportState.Preview(
                    preview = result.preview
                )
                is PluginPreviewResult.Failed -> PluginImportState.Failed(result.errors)
            }
        }
    }

    fun confirmInstall() {
        val state = _importState.value as? PluginImportState.Preview ?: return
        if (state.installing) return
        _importState.value = state.copy(installing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = manager.install(state.preview.token, state.origin)) {
                is PluginInstallResult.Installed -> {
                    if (installSourceChanged(state.preview, state.origin)) {
                        // 来源变了就不沿用旧包拿到的高危授权，启用状态保持原样
                        shizukuPermissionRepository.revoke(result.pluginId)
                        overlayPermissionRepository.revoke(result.pluginId)
                        overlayManager.closePlugin(result.pluginId)
                    }
                    syncProviders(result.pluginId)
                    pluginHookDispatcher.dispatchPluginInstalled(
                        result.pluginId,
                        state.preview.installedVersion
                    )
                    state.sourceDirName?.let { dirName ->
                        devLastLoaded[dirName] = devDirModified(dirName)
                        refreshDevProjects()
                    }
                    _importState.value = PluginImportState.Idle
                }
                is PluginInstallResult.Failed -> _importState.update { current ->
                    (current as? PluginImportState.Preview)?.copy(installing = false, error = result.error) ?: current
                }
            }
        }
    }

    /**
     * 把已安装插件打包为可再导入的 zip（`plugin.json` 在包根）。
     *
     * 抽成 suspend 纯函数是为了让批量导出自己排队：exportResult 是单槽位 SharedFlow，
     * 一次 emit 多个文件会互相覆盖，批量场景必须直接拿文件而不是走事件流。
     */
    suspend fun buildPluginZip(pluginId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val installed = plugins.value.firstOrNull { it.id == pluginId }
                ?: error("插件未安装: $pluginId")
            val dir = manager.pluginDir(pluginId)
            if (!dir.isDirectory) error("插件目录缺失: $pluginId")
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val version = installed.manifest?.version?.toString() ?: "unknown"
            val out = File(shareDir, "${pluginId}_${version}.zip")
            val root = dir.canonicalFile
            ZipOutputStream(out.outputStream().buffered()).use { zos ->
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    // 条目名取相对插件目录的路径，保证 plugin.json 落在 zip 根，能被 import 重新识别
                    val entryName = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            out
        }
    }

    /** 单插件分享/导出：打包后经 [exportResult] 事件流交给 UI 决定走分享还是 SAF 落盘。 */
    fun exportPlugin(pluginId: String) {
        viewModelScope.launch {
            _exportResult.tryEmit(buildPluginZip(pluginId))
        }
    }

    /** 在线商店已完成下载与包校验后，把暂存包交给统一预览/安装弹层。 */
    fun showPreview(storePreview: PluginStorePreview) {
        showPreview(
            preview = storePreview.preview,
            origin = storePreview.origin,
            expectedRepository = storePreview.expectedRepository,
            sourceLabel = storePreview.sourceLabel,
            officialSource = storePreview.officialSource
        )
    }

    fun showPreview(
        preview: PluginPreview,
        origin: PluginInstallOrigin,
        expectedRepository: String?,
        sourceLabel: String?,
        officialSource: Boolean
    ) {
        if (_importState.value !is PluginImportState.Idle && _importState.value !is PluginImportState.Failed) return
        _importState.value = PluginImportState.Preview(
            preview = preview,
            origin = origin,
            expectedRepository = expectedRepository,
            sourceLabel = sourceLabel,
            officialSource = officialSource
        )
    }

    /** 关闭预览 / 错误提示；预览中的暂存包同时丢弃。 */
    fun dismissImport() {
        val state = _importState.value
        if (state is PluginImportState.Preview && state.installing) return
        _importState.value = PluginImportState.Idle
        if (state is PluginImportState.Preview) {
            viewModelScope.launch { manager.discard(state.preview.token) }
        }
    }

    fun setDevMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { devSettingsRepository.setEnabled(enabled) }
    }

    /** 索引仓库按输入行保存；停顿后再落盘，避免每个按键都写一次 DataStore。 */
    fun setIndexSourceText(text: String) {
        _indexSourceText.value = text
        indexSourceSaveJob?.cancel()
        indexSourceSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SETTINGS_SAVE_DEBOUNCE_MS)
            indexSourceRepository.setCustomSources(text.lines())
        }
    }

    fun setDownloadMirror(text: String) {
        _downloadMirror.value = text
        downloadMirrorSaveJob?.cancel()
        downloadMirrorSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SETTINGS_SAVE_DEBOUNCE_MS)
            storeSettingsRepository.setDownloadMirror(text)
        }
    }

    /** 关闭插件设置弹层时立即落盘，避免刚改完就切走而丢掉暂停写入。 */
    fun savePluginSettings() {
        indexSourceSaveJob?.cancel()
        downloadMirrorSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            indexSourceRepository.setCustomSources(_indexSourceText.value.lines())
            storeSettingsRepository.setDownloadMirror(_downloadMirror.value)
        }
    }

    /** 重新扫描已安装插件；不重启运行中的插件进程。 */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { manager.refresh() }
                .onFailure { FileLogger.w(TAG, "刷新插件失败", it) }
            _refreshing.value = false
        }
    }

    fun refreshDevProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _devRefreshing.value = true
            try {
                _devProjects.value = scanDevProjects()
            } finally {
                _devRefreshing.value = false
            }
        }
    }

    /** 从开发目录打 zip 后走现有预览流程，安装仍需使用者在预览里确认。 */
    fun installDevProject(project: PluginDevProject) {
        if (_importState.value !is PluginImportState.Idle && _importState.value !is PluginImportState.Failed) return
        _importState.value = PluginImportState.Reading
        viewModelScope.launch(Dispatchers.IO) {
            val zip = File(context.cacheDir, "plugin-dev-${project.dirName}.zip")
            // TODO(P2): manager.devDir 待 AiVerse PluginManager 落地开发者目录。
            val result = try {
                PluginDevPackageBuilder.zip(File(manager.devDir, project.dirName), zip)
                zip.inputStream().use { manager.preview(it) }
            } catch (e: Exception) {
                FileLogger.w(TAG, "打包开发插件失败: ${project.dirName}", e)
                PluginPreviewResult.Failed(listOf(PluginError(PluginErrorCode.ARCHIVE_UNREADABLE)))
            } finally {
                zip.delete()
            }
            _importState.value = when (result) {
                is PluginPreviewResult.Ready -> PluginImportState.Preview(
                    preview = result.preview,
                    sourceDirName = project.dirName
                )
                is PluginPreviewResult.Failed -> PluginImportState.Failed(result.errors)
            }
        }
    }

    /** 启停入口：真实插件走 [PluginManager.setEnabled]，持久化并回推到 [plugins]。 */
    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!enabled) pluginHookDispatcher.dispatchPluginDisabled(id)
            val persisted = manager.setEnabled(id, enabled)
            if (persisted) {
                if (enabled) {
                    syncProviders(id)
                    pluginHookDispatcher.dispatchPluginEnabled(id)
                } else {
                    removeProviders(id)
                }
            } else {
                FileLogger.w(TAG, "插件启停未持久化: $id -> $enabled")
            }
            // 无论 manager 是否报告成功，都回读一次注册表快照：确保 _plugins 与磁盘一致，
            // 开关状态不会卡在旧值（之前反馈“开关无法关闭”多为此类未刷新/未落盘）。
            runCatching { manager.refresh() }
                .onFailure { FileLogger.w(TAG, "启停后刷新插件失败: $id", it) }
            if (!enabled) {
                pluginTerminalRunner.closePlugin(id)
                tipManager.dismissPlugin(id)
                overlayManager.closePlugin(id)
            }
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            uninstallInternal(id)
        }
    }

    /** 批量卸载：复用单个卸载的清理流程，逐个串行执行（避免并发写注册表/撤销授权竞态）。 */
    fun uninstallAll(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { uninstallInternal(it) }
        }
    }

    /** 单个插件卸载与善后（停运行进程、撤销高危授权、移除提供商）。 */
    private suspend fun uninstallInternal(id: String) {
        pluginHookDispatcher.dispatchPluginUninstalled(id)
        manager.uninstall(id)
        if (manager.plugins.value.none { it.id == id }) {
            removeProviders(id)
            shizukuPermissionRepository.revoke(id)
            overlayPermissionRepository.revoke(id)
            backupPermissionRepository.revoke(id)
        }
        pluginTerminalRunner.closePlugin(id)
        tipManager.dismissPlugin(id)
        overlayManager.closePlugin(id)
    }

    /** 插件级 Shizuku 授权；关闭后该插件无法再通过 Shizuku 执行命令。 */
    fun setShizukuGranted(plugin: InstalledPlugin, granted: Boolean) {
        if (plugin.content?.shizuku == null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (granted) {
                shizukuPermissionRepository.grant(plugin.id)
            } else {
                shizukuPermissionRepository.revoke(plugin.id)
            }
        }
    }

    /** 插件级 SSH 桥授权；关闭后该插件无法再通过 SSH 桥列出服务器或执行命令。 */
    fun setSshGranted(plugin: InstalledPlugin, granted: Boolean) {
        if (plugin.content?.ssh == null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (granted) {
                sshPermissionRepository.grant(plugin.id)
            } else {
                sshPermissionRepository.revoke(plugin.id)
            }
        }
    }

    /** 插件级悬浮窗授权；撤销时立即关闭该插件已经打开的窗口。 */
    fun setOverlayGranted(plugin: InstalledPlugin, granted: Boolean) {
        if (plugin.content?.overlay == null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (granted) {
                overlayPermissionRepository.grant(plugin.id)
            } else {
                overlayPermissionRepository.revoke(plugin.id)
                overlayManager.closePlugin(plugin.id)
            }
        }
    }

    /** 插件级备份事件授权；撤销后宿主不再向该插件下发备份与还原文件。 */
    fun setBackupGranted(plugin: InstalledPlugin, granted: Boolean) {
        if (plugin.content?.backup == null) return
        viewModelScope.launch(Dispatchers.IO) {
            if (granted) {
                backupPermissionRepository.grant(plugin.id)
            } else {
                backupPermissionRepository.revoke(plugin.id)
            }
        }
    }

    /** 提供商注册失败不影响安装/启停本身；插件页重新切换一次即可重试。 */
    private suspend fun syncProviders(pluginId: String) {
        try {
            pluginProviderRegistrar.sync(pluginId)
        } catch (e: Exception) {
            FileLogger.e(TAG, "同步插件提供商失败: $pluginId", e)
        }
    }

    private suspend fun removeProviders(pluginId: String) {
        try {
            pluginProviderRegistrar.removeAll(pluginId)
        } catch (e: Exception) {
            FileLogger.e(TAG, "移除插件提供商失败: $pluginId", e)
        }
    }

    /** 扫描开发目录：每个含 plugin.json 的子目录视为一个项目。 */
    private fun scanDevProjects(): List<PluginDevProject> {
        manager.ensureLoaded()
        val installed = manager.plugins.value.associate { it.id to it.record.version }
        // TODO(P2): manager.devDir 待 AiVerse PluginManager 落地开发者目录。
        return manager.devDir.listFiles().orEmpty()
            .filter { it.isDirectory }
            .map { dir ->
                val manifestFile = File(dir, "plugin.json")
                val parsed = runCatching {
                    if (manifestFile.isFile) PluginManifestParser.parse(manifestFile.readText()) else null
                }.getOrNull()
                val lastModified = dir.walkTopDown().filter { it.isFile }
                    .maxOfOrNull { it.lastModified() } ?: 0L
                when (parsed) {
                    is ManifestParseResult.Success -> PluginDevProject(
                        dirName = dir.name,
                        pluginId = parsed.manifest.id,
                        version = parsed.manifest.version.toString(),
                        installedVersion = installed[parsed.manifest.id],
                        lastModified = lastModified,
                        name = parsed.manifest.name,
                        description = parsed.manifest.description,
                        publisher = parsed.manifest.publisher
                    )
                    else -> PluginDevProject(
                        dirName = dir.name,
                        pluginId = "",
                        version = "",
                        installedVersion = null,
                        lastModified = lastModified,
                        error = "manifest"
                    )
                }
            }
            .sortedBy { it.dirName }
    }

    /** 开发模式开启期间监听开发目录；事件批次合并后重扫，已安装项目变化时自动重新打包替换。 */
    @OptIn(FlowPreview::class)
    private fun startDevWatcher() {
        if (devWatcherJob?.isActive == true) return
        devWatcherJob = viewModelScope.launch(Dispatchers.IO) {
            scanDevChanges()
            // TODO(P2): manager.devDir 待 AiVerse PluginManager 落地开发者目录。
            fileChangeHub.watchHostDir(
                hostDir = manager.devDir,
                recursive = true,
                filter = WatchFilter(ignoredNames = DEV_IGNORED_NAMES, followGitignore = false),
                fallbackPoll = false
            )
                .debounce(DEV_RELOAD_DEBOUNCE_MS)
                .collect { scanDevChanges() }
        }
    }

    /** 重扫开发目录；已安装项目内容或版本有变化时重新打包替换。 */
    private suspend fun scanDevChanges() {
        val projects = scanDevProjects()
        _devProjects.value = projects
        projects.forEach { project ->
            if (project.error != null) return@forEach
            val baseline = devLastLoaded[project.dirName]
            val versionChanged = project.installedVersion != null &&
                project.installedVersion != project.version
            if (baseline == null && !versionChanged) {
                if (project.installedVersion != null) devLastLoaded[project.dirName] = project.lastModified
                return@forEach
            }
            if (project.installedVersion != null &&
                (versionChanged || project.lastModified > (baseline ?: 0L))
            ) {
                if (reloadDevProject(project)) {
                    devLastLoaded[project.dirName] = project.lastModified
                    FileLogger.i(TAG, "开发插件已重载: ${project.dirName}")
                }
            }
        }
    }

    private suspend fun reloadDevProject(project: PluginDevProject): Boolean {
        val zip = File(context.cacheDir, "plugin-dev-${project.dirName}.zip")
        return try {
            // TODO(P2): manager.devDir 待 AiVerse PluginManager 落地开发者目录。
            PluginDevPackageBuilder.zip(File(manager.devDir, project.dirName), zip)
            val preview = zip.inputStream().use { manager.preview(it) }
            if (preview !is PluginPreviewResult.Ready) return false
            if (preview.preview.manifest.id != project.pluginId) return false
            if (preview.preview.installedVersion == null) return false
            when (val result = manager.install(preview.preview.token)) {
                is PluginInstallResult.Installed -> {
                    syncProviders(result.pluginId)
                    pluginHookDispatcher.dispatchPluginInstalled(
                        result.pluginId,
                        preview.preview.installedVersion
                    )
                    true
                }
                is PluginInstallResult.Failed -> false
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "自动重载开发插件失败: ${project.dirName}", e)
            false
        } finally {
            zip.delete()
        }
    }

    private fun devDirModified(dirName: String): Long =
        // TODO(P2): manager.devDir 待 AiVerse PluginManager 落地开发者目录。
        File(manager.devDir, dirName).walkTopDown().filter { it.isFile }
            .maxOfOrNull { it.lastModified() } ?: 0L

    /** 准备依赖：在本地容器、插件安装目录执行；同一插件不并发启动两次。 */
    fun prepareDependencies(id: String) {
        if (_dependencyStates.value[id]?.running == true) return
        _dependencyStates.update { it + (id to PluginDependencyUiState(running = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            // TODO(P2): manager.prepareDependencies 待 AiVerse PluginManager 落地；
            // 依赖准备目前由 com.aiverse.feature.plugins.runtime.PluginDependencyPreparer 独立提供。
            val state = when (val result = manager.prepareDependencies(id)) {
                is PluginDependencyResult.Ready -> PluginDependencyUiState(ready = true)
                is PluginDependencyResult.MissingRuntime ->
                    PluginDependencyUiState(ready = false, message = result.message)
                is PluginDependencyResult.Failed -> PluginDependencyUiState(
                    ready = false,
                    message = if (result.code == "STATE_NOT_SAVED") {
                        context.getString(R.string.plugins_dependencies_state_not_saved)
                    } else result.message
                )
            }
            _dependencyStates.update { it + (id to state) }
        }
    }

    /** 插件专属数据目录；槽位入口与桥必须使用同一落点。 */
    fun dataDirFor(pluginId: String): File = manager.dataDirFor(pluginId)

    /**
     * 槽位动作入口：复核插件仍启用后调用 runtime 的 `ui.invoke`。
     * handler 返回 `{message}` 时交给 UI 显示；失败返回错误文案。
     */
    fun runUiAction(
        entry: PluginUiEntry,
        entryContext: PluginEntryContext,
        onMessage: (String?) -> Unit
    ) {
        val handler = entry.handler ?: return
        viewModelScope.launch {
            val message = try {
                val plugin = manager.plugins.value.firstOrNull { it.id == entry.pluginId && it.isActive }
                    ?: throw IllegalStateException(context.getString(R.string.plugin_tool_not_authorized))
                val runtime = plugin.content?.runtime
                    ?: throw IllegalStateException(context.getString(R.string.plugin_runtime_failed))
                val result = withContext(Dispatchers.IO) {
                    pluginRuntimeManager.invoke(
                        pluginId = plugin.id,
                        version = plugin.record.version,
                        runtime = runtime,
                        handler = handler,
                        args = buildJsonObject {
                            put("entryId", entry.localId)
                            put("slot", entry.slot)
                            entryContext.sessionId?.let { put("sessionId", it) }
                            entryContext.path?.let { put("path", it) }
                            entryContext.isDirectory?.let { put("isDirectory", it) }
                            entryContext.selection?.let { put("selection", it) }
                            entryContext.language?.let { put("language", it) }
                            entryContext.lineStart?.let { put("lineStart", it) }
                            entryContext.lineEnd?.let { put("lineEnd", it) }
                        },
                        method = "ui.invoke",
                        timeoutMs = ACTION_TIMEOUT_MS
                    )
                }
                (result as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.w(TAG, "插件槽位动作失败: ${entry.pluginId}/${entry.localId}", e)
                e.message ?: context.getString(R.string.plugin_runtime_failed)
            }
            onMessage(message)
        }
    }

    /** 为插件设置页构造宿主桥；桥与 WebView 一起缓存，由 [PluginWebViewCache] 统一释放。 */
    fun createWebBridge(
        page: PluginPage,
        pluginVersion: String,
        languageTag: String,
        theme: () -> String,
        onClosePage: () -> Unit,
        post: (String) -> Unit,
        pageContext: () -> PluginEntryContext = { PluginEntryContext() }
    ): PluginWebBridge {
        return PluginWebBridge(
            context = context,
            page = page,
            pluginVersion = pluginVersion,
            languageTag = languageTag,
            fileAccess = fileAccess,
            homeResolver = pathHomeResolver,
            commandEngine = commandEngine,
            workspacePath = { workspaceRepository.currentPath() },
            eventBus = eventBus,
            pluginManager = manager,
            pluginRuntimeManager = pluginRuntimeManager,
            terminalRunner = pluginTerminalRunner,
            shizukuRunner = shizukuRunner,
            sshRunner = sshRunner,
            tipManager = tipManager,
            foregroundMonitor = foregroundMonitor,
            overlayManager = overlayManager,
            chatService = chatService,
            modelService = modelService,
            agentRunStateStore = agentRunStateStore,
            secretStore = secretStore,
            systemService = systemService,
            introspectionService = introspectionService,
            toolService = toolService,
            permissionService = permissionService,
            sessionSourceService = sessionSourceService,
            httpService = httpService,
            providerService = providerService,
            environment = pageEnvironment,
            theme = theme,
            onClosePage = onClosePage,
            post = post,
            pageContext = pageContext
        )
    }

    private companion object {
        const val TAG = "PluginsViewModel"
        const val DEV_RELOAD_DEBOUNCE_MS = 500L
        const val SETTINGS_SAVE_DEBOUNCE_MS = 500L
        const val ACTION_TIMEOUT_MS = 10_000L

        /** 构建产物类目录不参与开发目录监听，避免一次编译触发整批重扫。 */
        val DEV_IGNORED_NAMES = setOf(".git", "node_modules", ".venv", "__pycache__", ".idea")
    }
}
