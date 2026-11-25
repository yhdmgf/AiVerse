package com.aiverse.feature.backup.data

import android.content.Context
import com.aiverse.core.security.KeystoreCipher
import com.aiverse.core.util.FileLogger
import com.aiverse.core.util.GitIgnoreMatcher
import com.aiverse.feature.agent.data.local.dao.AgentMessageDao
import com.aiverse.feature.agent.data.local.dao.ChatSessionDao
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.data.local.dao.TodoItemDao
import com.aiverse.feature.agent.data.local.database.AgentDatabase
import com.aiverse.feature.agent.data.repository.FavoriteItem
import com.aiverse.feature.agent.data.repository.FavoriteRepository
import com.aiverse.feature.agent.data.local.entity.AgentMessageEntity
import com.aiverse.feature.agent.data.local.entity.ChatSessionEntity
import com.aiverse.feature.agent.data.local.entity.RoleEntity
import com.aiverse.feature.agent.data.local.entity.TodoItemEntity
import com.aiverse.feature.agent.domain.container.ContainerInstaller
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.domain.mcp.McpConfigRepository
import com.aiverse.feature.agent.domain.mcp.McpManager
import com.aiverse.feature.agent.domain.memory.MemoryRepository
import com.aiverse.feature.agent.domain.memory.MemoryScope
import com.aiverse.feature.agent.domain.permission.PermissionRulesRepository
import com.aiverse.feature.agent.domain.skill.SkillRepository
import com.aiverse.feature.agent.domain.skill.SkillScope
import com.aiverse.feature.backup.domain.AgentMessageDto
import com.aiverse.feature.backup.domain.BackupCrypto
import com.aiverse.feature.backup.domain.BackupDecryptionException
import com.aiverse.feature.backup.domain.BackupManager
import com.aiverse.feature.backup.domain.BackupMetadata
import com.aiverse.feature.backup.domain.BackupOptions
import com.aiverse.feature.backup.domain.BackupSnapshot
import com.aiverse.feature.backup.domain.ChatSessionDto
import com.aiverse.feature.backup.domain.FavoriteItemDto
import com.aiverse.feature.backup.domain.ImportPreview
import com.aiverse.feature.backup.domain.InputBarPrefsDto
import com.aiverse.feature.backup.domain.MemoryDto
import com.aiverse.feature.backup.domain.PluginBackupMeta
import com.aiverse.feature.backup.domain.ProviderDto
import com.aiverse.feature.backup.domain.RemoteConnectionDto
import com.aiverse.feature.backup.domain.RemoteMountDto
import com.aiverse.feature.backup.domain.RestoreStats
import com.aiverse.feature.backup.domain.RoleDto
import com.aiverse.feature.backup.domain.SkillBackupMeta
import com.aiverse.feature.backup.domain.TodoItemDto
import com.aiverse.feature.backup.domain.WorkspaceBackupMeta
import com.aiverse.feature.backup.domain.toMetadata
import com.aiverse.feature.credentials.data.repository.FileCredentialRepository
import com.aiverse.feature.plugins.domain.PluginManager
import java.util.Locale
import com.aiverse.feature.settings.data.local.dao.AIProviderDao
import com.aiverse.feature.settings.data.local.entity.AIProviderEntity
import com.aiverse.feature.settings.data.repository.AppearanceSettingsRepository
import com.aiverse.feature.settings.data.repository.BackgroundSettingsRepository
import com.aiverse.feature.settings.data.repository.CompactionModelSettingsRepository
import com.aiverse.feature.settings.data.repository.AgentSoundSettingsRepository
import com.aiverse.feature.settings.data.repository.DefaultModelSettingsRepository
import com.aiverse.feature.settings.data.repository.GeneralSettingsRepository
import com.aiverse.feature.settings.data.repository.InputBarPrefs
import com.aiverse.feature.settings.data.repository.InputBarSettingsRepository
import com.aiverse.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aiverse.feature.settings.data.repository.LiveNotificationSettingsRepository
import com.aiverse.feature.settings.data.repository.ApprovalNotificationSettingsRepository
import com.aiverse.feature.settings.data.repository.ScreenOnSettingsRepository
import com.aiverse.feature.settings.data.repository.LanguageSettingsRepository
import com.aiverse.feature.settings.data.repository.LogSettingsRepository
import com.aiverse.feature.settings.data.repository.SyncSettingsRepository
import com.aiverse.feature.settings.data.repository.ThemeSettingsRepository
import com.aiverse.feature.settings.data.repository.WelcomeEntriesRepository
import com.aiverse.feature.settings.data.repository.VisionModelSettingsRepository
import com.aiverse.feature.settings.data.repository.OptimizeInputModelSettingsRepository
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aiverse.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aiverse.feature.workspace.data.local.entity.RemoteMountEntity
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.model.RemoteProtocol
import com.aiverse.feature.workspace.domain.model.Workspace
import com.aiverse.feature.workspace.domain.model.WorkspaceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aiProviderDao: AIProviderDao,
    private val remoteConnectionDao: RemoteConnectionDao,
    private val chatSessionDao: ChatSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val todoItemDao: TodoItemDao,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val appearanceSettingsRepository: AppearanceSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val liveNotificationSettingsRepository: LiveNotificationSettingsRepository,
    private val approvalNotificationSettingsRepository: ApprovalNotificationSettingsRepository,
    private val screenOnSettingsRepository: ScreenOnSettingsRepository,
    private val agentSoundSettingsRepository: AgentSoundSettingsRepository,
    private val generalSettingsRepository: GeneralSettingsRepository,
    private val logSettingsRepository: LogSettingsRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
    private val optimizeInputModelSettingsRepository: OptimizeInputModelSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val credentialRepository: FileCredentialRepository,
    private val memoryRepository: MemoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val skillRepository: SkillRepository,
    private val pluginManager: PluginManager,
    private val welcomeEntriesRepository: WelcomeEntriesRepository,
    private val roleDao: RoleDao,
    private val defaultModelSettingsRepository: DefaultModelSettingsRepository,
    private val languageSettingsRepository: LanguageSettingsRepository,
    private val inputBarSettingsRepository: InputBarSettingsRepository,
    private val backgroundSettingsRepository: BackgroundSettingsRepository,
    private val containerInstaller: ContainerInstaller
) : BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun currentSchemaVersion(): Int = AgentDatabase.SCHEMA_VERSION

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    override suspend fun export(password: CharArray?, options: BackupOptions, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val pw = password?.takeIf { it.isNotEmpty() }
            if (pw == null) {
                writeTarGz(output, options)
                return@withContext
            }
            val temp = createTempFile()
            try {
                FileOutputStream(temp).buffered().use { writeTarGz(it, options) }
                FileInputStream(temp).buffered().use { input ->
                    BackupCrypto.encryptStream(input, output, pw)
                }
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun prepareImport(input: InputStream, password: CharArray?): File = withContext(Dispatchers.IO) {
        val temp = createTempFile()
        try {
            FileOutputStream(temp).buffered().use { output ->
                val source = input.buffered()
                val pw = password?.takeIf { it.isNotEmpty() }
                if (pw == null) source.copyTo(output) else BackupCrypto.decryptStream(source, output, pw)
            }
            temp
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    override suspend fun exportSession(sessionId: String, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val session = chatSessionDao.getById(sessionId) ?: error("Session not found: $sessionId")
            val temp = createTempFile()
            try {
                FileOutputStream(temp).use { fos ->
                    GzipCompressorOutputStream(fos).use { gz ->
                        TarArchiveOutputStream(gz).use { tar ->
                            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                            writeMetadataEntry(tar, BackupMetadata(
                                schemaVersion = currentSchemaVersion(),
                                appVersion = appVersionName(),
                                createdAt = System.currentTimeMillis()
                            ))
                            writeJsonlFileEntry(tar, FILE_SESSIONS) { writer ->
                                writer.writeLine(json.encodeToString(ChatSessionDto.serializer(), session.toDto()))
                            }
                            writeJsonlFileEntry(tar, FILE_MESSAGES) { writer ->
                                var lastTs = 0L
                                var lastId = ""
                                while (true) {
                                    val batch = agentMessageDao.getPageBySessionAfter(sessionId, lastTs, lastId, PAGE_SIZE)
                                    if (batch.isEmpty()) break
                                    batch.forEach { writer.writeLine(json.encodeToString(AgentMessageDto.serializer(), it.toDto())) }
                                    lastTs = batch.last().timestamp
                                    lastId = batch.last().id
                                }
                            }
                            writeJsonlFileEntry(tar, FILE_TODOS) { writer ->
                                var lastTs = 0L
                                var lastId = ""
                                while (true) {
                                    val batch = todoItemDao.getBySessionPageAfter(sessionId, lastTs, lastId, PAGE_SIZE)
                                    if (batch.isEmpty()) break
                                    batch.forEach { writer.writeLine(json.encodeToString(TodoItemDto.serializer(), it.toDto())) }
                                    lastTs = batch.last().createdAt
                                    lastId = batch.last().id
                                }
                            }
                        }
                    }
                }
                FileInputStream(temp).use { it.copyTo(output) }
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun import(
        input: InputStream,
        password: CharArray?,
        selectedWorkspaces: Set<String>?
    ): Result<RestoreStats> {
        val pw = password?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            FileLogger.i(TAG, "导入备份开始（${if (pw != null) "加密" else "明文"}${if (selectedWorkspaces != null) "，勾选工作区=${selectedWorkspaces.size}个" else "，全量"}）")
            runCatching {
                openTar(input, pw).use { source ->
                    restoreFromTar(source.tar, selectedWorkspaces)
                }
            }
            .onSuccess { FileLogger.i(TAG, "导入备份完成：$it") }
            .onFailure { FileLogger.e(TAG, "导入备份失败", it) }
            .recoverCatching { e -> mapImportError(e, pw) }
        }
    }

    override suspend fun previewImport(input: InputStream, password: CharArray?): Result<ImportPreview> {
        val pw = password?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            FileLogger.i(TAG, "导入预览开始（${if (pw != null) "加密" else "明文"}）")
            runCatching {
                openTar(input, pw).use { source ->
                    val tar = source.tar
                    var workspaces: List<WorkspaceBackupMeta> = emptyList()
                    var entry = tar.nextEntry
                    while (entry != null) {
                        if (entry.name == FILE_METADATA) {
                            val plain = tar.readBytes()
                            val metadata = json.decodeFromString(BackupMetadata.serializer(), String(plain, Charsets.UTF_8))
                            checkVersion(metadata.schemaVersion)
                            workspaces = metadata.workspaces
                            // 导出时 metadata.json 为首个条目，读到即可停，避免遍历大段 jsonl
                            break
                        }
                        entry = tar.nextEntry
                    }
                    ImportPreview(workspaces)
                }
            }
            .onSuccess { FileLogger.i(TAG, "导入预览完成：${it.workspaces.size} 个工作区") }
            .onFailure { FileLogger.e(TAG, "导入预览失败", it) }
            .recoverCatching { e -> mapImportError(e, pw) }
        }
    }

    /** 把导入异常映射为用户可读的 IllegalArgumentException（解密异常原样抛出）。 */
    private fun mapImportError(e: Throwable, pw: CharArray?): Nothing {
        when (e) {
            is BackupDecryptionException -> throw e
            is IllegalStateException -> throw e
            else -> throw IllegalArgumentException(
                if (pw != null) {
                    "备份文件已损坏，或口令与备份文件不匹配"
                } else {
                    "不是有效的 AiVerse 备份文件；如果这是加密备份，请输入导出口令"
                },
                e
            )
        }
    }

    /** 打开 tar 流：先按需解密到临时文件，再解压；调用方负责 [TarSource.close]。 */
    private fun openTar(input: InputStream, pw: CharArray?): TarSource {
        if (pw == null) {
            FileLogger.i(TAG, "明文备份：直接解压 tar.gz")
            val p = BufferedInputStream(input)
            val gz = GzipCompressorInputStream(p)
            return TarSource(TarArchiveInputStream(gz), null)
        }
        FileLogger.i(TAG, "加密备份：先解密到临时文件")
        val temp = createTempFile()
        try {
            BufferedInputStream(input).use { src ->
                FileOutputStream(temp).buffered().use { dst -> BackupCrypto.decryptStream(src, dst, pw) }
            }
            val p = FileInputStream(temp).buffered()
            val gz = GzipCompressorInputStream(p)
            return TarSource(TarArchiveInputStream(gz), temp)
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    // ── 导出辅助 ──────────────────────────────────────────────

    private suspend fun writeTarGz(output: OutputStream, options: BackupOptions) {
        val stream = object : FilterOutputStream(output) {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                out.write(bytes, offset, length)
            }

            override fun close() {
                flush()
            }
        }
        stream.buffered().use { fos ->
            GzipCompressorOutputStream(fos).use { gz ->
                TarArchiveOutputStream(gz).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                    writeMetadataEntry(tar, buildMetadata(options))
                    if (options.workspaceFiles) {
                        writeWorkspaceEntries(tar)
                    }
                    if (options.skills) {
                        writeSkillEntries(tar)
                    }
                    if (options.plugins) {
                        writePluginEntries(tar)
                    }
                    if (options.appSettings) {
                        writeSettingsEntries(tar)
                    }
                    if (options.roles) {
                        writeJsonlFileEntry(tar, FILE_ROLES) { writer ->
                            roleDao.getAllOnce().forEach { writer.writeLine(json.encodeToString(RoleDto.serializer(), it.toDto())) }
                        }
                    }
                    if (options.customPrompts) {
                        writeCustomPromptEntries(tar)
                    }
                    if (options.mediaFiles) {
                        writeMediaEntries(tar)
                    }
                    if (options.gitCredentials) {
                        // git 凭据以 git-credential-store 原始格式入包（不走 KeystoreCipher），
                        // metadata.json 仍为首个条目，新条目只追加在其后。
                        val raw = credentialRepository.readRawCredentials()
                        if (raw.isNotBlank()) writeTarEntry(tar, FILE_GIT_CREDENTIALS, raw.toByteArray(Charsets.UTF_8))
                    }
                    if (options.chatHistory) {
                        writeJsonlFileEntry(tar, FILE_SESSIONS) { writer ->
                            var lastTs = 0L
                            var lastId = ""
                            while (true) {
                                val batch = chatSessionDao.getPageAfter(lastTs, lastId, PAGE_SIZE)
                                if (batch.isEmpty()) break
                                batch.forEach { writer.writeLine(json.encodeToString(ChatSessionDto.serializer(), it.toDto())) }
                                lastTs = batch.last().updatedAt
                                lastId = batch.last().id
                            }
                        }
                        writeJsonlFileEntry(tar, FILE_MESSAGES) { writer ->
                            var lastTs = 0L
                            var lastId = ""
                            while (true) {
                                val batch = agentMessageDao.getPageAfter(lastTs, lastId, PAGE_SIZE)
                                if (batch.isEmpty()) break
                                batch.forEach { writer.writeLine(json.encodeToString(AgentMessageDto.serializer(), it.toDto())) }
                                lastTs = batch.last().timestamp
                                lastId = batch.last().id
                            }
                        }
                        writeJsonlFileEntry(tar, FILE_TODOS) { writer ->
                            var lastTs = 0L
                            var lastId = ""
                            while (true) {
                                val batch = todoItemDao.getPageAfter(lastTs, lastId, PAGE_SIZE)
                                if (batch.isEmpty()) break
                                batch.forEach { writer.writeLine(json.encodeToString(TodoItemDto.serializer(), it.toDto())) }
                                lastTs = batch.last().createdAt
                                lastId = batch.last().id
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun buildMetadata(options: BackupOptions): BackupMetadata = BackupMetadata(
        schemaVersion = currentSchemaVersion(),
        appVersion = appVersionName(),
        createdAt = System.currentTimeMillis(),
        providers = if (options.providers) aiProviderDao.getAllProvidersOnce().map { it.toDto() } else emptyList(),
        remoteConnections = if (options.remoteConnections) remoteConnectionDao.getAllConnectionsOnce().map { it.toDto() } else emptyList(),
        remoteMounts = if (options.remoteConnections) remoteConnectionDao.getAllMountsOnce().map { it.toDto() } else emptyList(),
        mcpServers = if (options.mcpServers) mcpConfigRepository.getGlobalServers() else emptyList(),
        globalPermissionRules = if (options.permissionRules) permissionRulesRepository.getGlobalRulesOnce() else emptyList(),
        themeMode = if (options.appSettings) themeSettingsRepository.snapshot() else null,
        themePresetId = if (options.appSettings) themeSettingsRepository.presetSnapshot() else null,
        dynamicColorEnabled = if (options.appSettings) themeSettingsRepository.dynamicColorSnapshot() else false,
        chatFontSizeSp = if (options.appSettings) appearanceSettingsRepository.chatFontSizeSnapshot() else null,
        inputFontSizeSp = if (options.appSettings) appearanceSettingsRepository.inputFontSizeSnapshot() else null,
        uiFontScale = if (options.appSettings) appearanceSettingsRepository.uiFontScaleSnapshot() else null,
        uiDensity = if (options.appSettings) appearanceSettingsRepository.uiDensitySnapshot() else null,
        uiFontPath = if (options.appSettings) appearanceSettingsRepository.uiFontPathSnapshot() else null,
        codeFontSizeSp = if (options.appSettings) appearanceSettingsRepository.codeFontSizeSnapshot() else 13,
        codeWrapEnabled = if (options.appSettings) appearanceSettingsRepository.codeWrapSnapshot() else false,
        codeAutoFoldEnabled = if (options.appSettings) appearanceSettingsRepository.codeAutoFoldSnapshot() else true,
        codeShowLineNumber = if (options.appSettings) appearanceSettingsRepository.codeShowLineNumberSnapshot() else true,
        userBubbleOpacity = if (options.appSettings) appearanceSettingsRepository.userBubbleOpacitySnapshot() else 100,
        assistantBubbleOpacity = if (options.appSettings) appearanceSettingsRepository.assistantBubbleOpacitySnapshot() else 100,
        chatPanelOpacity = if (options.appSettings) appearanceSettingsRepository.chatPanelOpacitySnapshot() else 100,
        keepaliveEnabled = if (options.appSettings) keepaliveSettingsRepository.snapshot() else false,
        liveNotificationEnabled = if (options.appSettings) liveNotificationSettingsRepository.snapshot() else false,
        approvalNotificationEnabled = if (options.appSettings) approvalNotificationSettingsRepository.snapshot() else false,
        screenOnEnabled = if (options.appSettings) screenOnSettingsRepository.snapshot() else false,
        agentSoundEnabled = if (options.appSettings) agentSoundSettingsRepository.snapshot() else false,
        autoRemoveStaleModels = if (options.appSettings) generalSettingsRepository.autoRemoveStaleModelsSnapshot() else true,
        startupSessionMode = if (options.appSettings) generalSettingsRepository.startupSessionModeSnapshot() else null,
        firstByteTimeoutSec = if (options.appSettings) generalSettingsRepository.firstByteTimeoutSecSnapshot() else 300,
        streamIdleTimeoutSec = if (options.appSettings) generalSettingsRepository.streamIdleTimeoutSecSnapshot() else 0,
        maxNetworkRetries = if (options.appSettings) generalSettingsRepository.maxNetworkRetriesSnapshot() else 6,
        subAgentMaxRunning = if (options.appSettings) generalSettingsRepository.maxSubagentRunningSnapshot() else 6,
        enterToSend = if (options.appSettings) generalSettingsRepository.enterToSendSnapshot() else false,
        compactionThresholdPercent = if (options.appSettings) generalSettingsRepository.compactionThresholdPercentSnapshot() else 90,
        sendFileMaxSizeMb = if (options.appSettings) generalSettingsRepository.sendFileMaxSizeMbSnapshot() else 100,
        deleteExternalWorkspaceSessions = if (options.appSettings) generalSettingsRepository.deleteExternalWorkspaceSessionsSnapshot() else false,
        mcpLazyTools = if (options.appSettings) generalSettingsRepository.mcpLazyToolsSnapshot() else false,
        logLevel = if (options.appSettings) logSettingsRepository.snapshot() else null,
        visionProviderId = if (options.appSettings) visionModelSettingsRepository.getVisionProviderId() else "",
        visionModel = if (options.appSettings) visionModelSettingsRepository.getVisionModel() else "",
        compactionProviderId = if (options.appSettings) compactionModelSettingsRepository.getCompactionProviderId() else "",
        compactionModel = if (options.appSettings) compactionModelSettingsRepository.getCompactionModel() else "",
        optimizeProviderId = if (options.appSettings) optimizeInputModelSettingsRepository.getOptimizeInputProviderId() else "",
        optimizeModel = if (options.appSettings) optimizeInputModelSettingsRepository.getOptimizeInputModel() else "",
        syncSettings = if (options.appSettings) syncSettingsRepository.snapshot() else null,
        welcomeEntriesJson = if (options.appSettings) welcomeEntriesRepository.entriesJsonSnapshot() else null,
        defaultModelProviderId = if (options.appSettings) defaultModelSettingsRepository.getDefaultProviderId() else "",
        defaultModel = if (options.appSettings) defaultModelSettingsRepository.getDefaultModel() else "",
        languageTag = if (options.appSettings) languageSettingsRepository.snapshot() else null,
        inputBarPrefsJson = if (options.appSettings) encodeInputBarPrefs() else null,
        backgroundImagePath = if (options.appSettings) backgroundSettingsRepository.imagePathFlow.first() else null,
        workspaces = if (options.workspaceFiles) collectWorkspaceMetas() else emptyList(),
        memories = if (options.memories) collectMemories() else emptyList(),
        favorites = if (options.favorites) favoriteRepository.getAllOnce().map { it.toDto() } else emptyList(),
        skills = if (options.skills) collectSkillMetas() else emptyList(),
        plugins = if (options.plugins) collectPluginMetas() else emptyList(),
        customPromptFileCount = if (options.customPrompts) collectCustomPromptFileCount() else 0,
        mediaFileCount = if (options.mediaFiles) collectMediaFileCount() else 0,
        includesAppSettings = options.appSettings
    )

    /** 收集全部记忆：全局 + 各工作区项目级（本地/远程工作区均可，远程经项目键映射到本地目录）。 */
    private fun collectMemories(): List<MemoryDto> {
        val result = mutableListOf<MemoryDto>()
        memoryRepository.listGlobalMemories().forEach { m ->
            result += MemoryDto(m.name, m.description, m.content, MemoryScope.GLOBAL.name)
        }
        val seenRoots = mutableSetOf<String>()
        workspaceRepository.workspaces.value.forEach { ws ->
            if (!seenRoots.add(ws.path)) return@forEach
            memoryRepository.listProjectMemories(ws.path).forEach { m ->
                result += MemoryDto(m.name, m.description, m.content, MemoryScope.PROJECT.name, ws.path)
            }
        }
        return result
    }

    private fun writeMetadataEntry(tar: TarArchiveOutputStream, metadata: BackupMetadata) {
        val content = json.encodeToString(BackupMetadata.serializer(), metadata).toByteArray(Charsets.UTF_8)
        writeTarEntry(tar, FILE_METADATA, content)
    }

    private fun writeTarEntry(tar: TarArchiveOutputStream, name: String, content: ByteArray) {
        val entry = TarArchiveEntry(name).apply { size = content.size.toLong() }
        tar.putArchiveEntry(entry)
        tar.write(content)
        tar.closeArchiveEntry()
    }

    /**
     * 先将 jsonl 写入一个临时文件，获取确切的 [File.length] 设置 TarArchiveEntry.size，
     * 然后流式拷入 TarArchiveOutputStream，避免在 Header 中 size 设为 0 导致写入越界异常。
     */
    private suspend fun writeJsonlFileEntry(
        tar: TarArchiveOutputStream,
        entryName: String,
        block: suspend (JsonlWriter) -> Unit
    ) {
        val tmp = createTempFile()
        try {
            FileOutputStream(tmp).use { fos ->
                val writer = JsonlWriter(fos)
                block(writer)
                writer.flush()
            }
            val entry = TarArchiveEntry(entryName).apply { size = tmp.length() }
            tar.putArchiveEntry(entry)
            FileInputStream(tmp).use { fis -> fis.copyTo(tar) }
            tar.closeArchiveEntry()
        } finally {
            tmp.delete()
        }
    }

    // ── 工作区文件备份 ──────────────────────────────────────────

    /** 仅备份内部本地工作区；外部本地工作区是用户设备上的目录，不属于 App 私有数据，不纳入备份。 */
    private fun backupLocalWorkspaces(): List<Workspace> =
        workspaceRepository.workspaces.value.filter { it.type != WorkspaceType.EXTERNAL_LOCAL }

    /** 第一遍：统计每个本地工作区将备份的文件数（写 metadata 用），并带上显示名别名供还原恢复。 */
    private fun collectWorkspaceMetas(): List<WorkspaceBackupMeta> =
        backupLocalWorkspaces().mapNotNull { ws ->
            var count = 0
            walkWorkspaceFiles(ws) { _, _ -> count++ }
            if (count > 0) WorkspaceBackupMeta(ws.name, count, ws.displayName) else null
        }

    /** 第二遍：把各本地工作区文件写入 tar（`workspaces/<name>/<相对路径>`）。 */
    private fun writeWorkspaceEntries(tar: TarArchiveOutputStream) {
        backupLocalWorkspaces().forEach { ws ->
            walkWorkspaceFiles(ws) { file, parts ->
                writeTarFileEntry(tar, "workspaces/${ws.name}/${parts.joinToString("/")}", file)
            }
        }
    }

    // ── 技能备份 ─────────────────────────────────────────────

    /** 第一遍：统计每个技能的目录文件数（写 metadata 用）。 */
    private fun collectSkillMetas(): List<SkillBackupMeta> =
        skillRepository.exportAllSkills().map { SkillBackupMeta(it.name, it.scope.name, it.files.size) }

    /** 第二遍：把各技能目录文件写入 tar（`skills/<scope>/<name>/<相对路径>`）。 */
    private fun writeSkillEntries(tar: TarArchiveOutputStream) {
        skillRepository.exportAllSkills().forEach { bundle ->
            bundle.files.forEach { (rel, bytes) ->
                writeTarEntry(tar, "${SKILL_PREFIX}${bundle.scope.name}/${bundle.name}/$rel", bytes)
            }
        }
    }

    // ── 脚本插件备份 ─────────────────────────────────────────

    /** 第一遍：统计每个已安装插件的文件数（写 metadata 用）。 */
    private fun collectPluginMetas(): List<PluginBackupMeta> =
        pluginManager.installedPlugins().mapNotNull { plugin ->
            val manifest = plugin.manifest ?: return@mapNotNull null
            val count = pluginManager.collectPluginFiles(plugin.id).size
            if (count > 0) PluginBackupMeta(plugin.id, manifest.name.resolve(Locale.getDefault().toLanguageTag()), count) else null
        }

    /** 第二遍：把各插件目录文件写入 tar（`plugins/<id>/<相对路径>`，zip 内 plugin.json 在插件根）。 */
    private fun writePluginEntries(tar: TarArchiveOutputStream) {
        pluginManager.installedPlugins().forEach { plugin ->
            pluginManager.collectPluginFiles(plugin.id).forEach { (rel, bytes) ->
                writeTarEntry(tar, "${PLUGIN_PREFIX}${plugin.id}/$rel", bytes)
            }
        }
    }

    /** DataStore 文件目录：`filesDir/datastore`。 */
    private val datastoreDir: File
        get() = File(context.filesDir, "datastore")

    /** SharedPreferences 目录：与 filesDir 同级的 `shared_prefs`。 */
    private val sharedPrefsDir: File
        get() = File(context.filesDir.parentFile, "shared_prefs")

    /** 插件自带的数据目录：`filesDir/aiverse/plugins/data`。 */
    private val pluginDataDir: File
        get() = File(File(context.filesDir, "aiverse"), "plugins/data")

    /**
     * 设置类数据整体入包，而不是逐项抄字段：
     * `settings/datastore/<name>`、`settings/shared_prefs/<name>`、`settings/plugin-registry.json`、
     * `settings/plugin-data/<id>/<相对路径>`。以后新增设置项自动被覆盖，不用再补备份范围。
     */
    private fun writeSettingsEntries(tar: TarArchiveOutputStream) {
        fun writeFlat(dir: File, prefix: String) {
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    writeTarEntry(tar, "$prefix${file.name}", file.readBytes())
                }
            }
        }
        writeFlat(datastoreDir, SETTINGS_DATASTORE_PREFIX)
        writeFlat(sharedPrefsDir, SETTINGS_SHARED_PREFS_PREFIX)
        val registry = File(context.filesDir, PLUGIN_REGISTRY_FILE)
        if (registry.isFile && registry.length() > 0) {
            writeTarEntry(tar, SETTINGS_PLUGIN_REGISTRY, registry.readBytes())
        }
        val pluginData = pluginDataDir
        if (pluginData.isDirectory) {
            walkDirectory(pluginData) { file, relParts ->
                writeTarEntry(
                    tar,
                    "$SETTINGS_PLUGIN_DATA_PREFIX${relParts.joinToString("/")}",
                    file.readBytes()
                )
            }
        }
    }

    /** 单个设置文���落回本机；导入完成需提示用户重启，DataStore 实例才会重新读盘。 */
    private fun restoreSettingsEntry(tar: TarArchiveInputStream, name: String): Boolean {
        val base: File
        val rel: String
        when {
            name.startsWith(SETTINGS_DATASTORE_PREFIX) -> {
                base = datastoreDir
                rel = name.removePrefix(SETTINGS_DATASTORE_PREFIX)
            }
            name.startsWith(SETTINGS_SHARED_PREFS_PREFIX) -> {
                base = sharedPrefsDir
                rel = name.removePrefix(SETTINGS_SHARED_PREFS_PREFIX)
            }
            name.startsWith(SETTINGS_PLUGIN_DATA_PREFIX) -> {
                base = pluginDataDir
                rel = name.removePrefix(SETTINGS_PLUGIN_DATA_PREFIX)
            }
            name == SETTINGS_PLUGIN_REGISTRY -> {
                base = context.filesDir
                rel = PLUGIN_REGISTRY_FILE
            }
            else -> return false
        }
        // 备份是外部文件，落盘前确认目标没有跳出既定目录。
        val root = base.canonicalFile
        val target = File(root, rel).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            FileLogger.w(TAG, "跳过越界的设置条目：$name")
            return false
        }
        target.parentFile?.mkdirs()
        target.writeBytes(tar.readBytes())
        return true
    }

    // ── 自定义提示词备份（prompts.custom 整目录） ─────────────────

    private val promptsCustomDir: File
        get() = File(containerInstaller.aiverseDir, "prompts.custom")

    /** 第一遍：统计 prompts.custom 目录下将备份的文件数（写 metadata 用）。 */
    private fun collectCustomPromptFileCount(): Int {
        var count = 0
        walkDirectory(promptsCustomDir) { _, _ -> count++ }
        return count
    }

    /** 第二遍：把 prompts.custom 目录文件写入 tar（`customPrompts/<相对路径>`）。 */
    private fun writeCustomPromptEntries(tar: TarArchiveOutputStream) {
        walkDirectory(promptsCustomDir) { file, parts ->
            writeTarFileEntry(tar, "${CUSTOM_PROMPT_PREFIX}${parts.joinToString("/")}", file)
        }
    }

    // ── 媒体文件备份（快捷入口图标 + 聊天背景图） ──────────────────

    /** 第一遍：统计图标/背景图目录文件数（写 metadata 用）。 */
    private fun collectMediaFileCount(): Int {
        var count = 0
        walkDirectory(welcomeIconsDir) { _, _ -> count++ }
        walkDirectory(backgroundsDir) { _, _ -> count++ }
        return count
    }

    /** 第二遍：把图标/背景图文件写入 tar（`media/welcome_icons/<name>`、`media/backgrounds/<name>`）。 */
    private fun writeMediaEntries(tar: TarArchiveOutputStream) {
        walkDirectory(welcomeIconsDir) { file, parts ->
            writeTarFileEntry(tar, "${MEDIA_PREFIX}${WELCOME_ICON_DIR}/${parts.joinToString("/")}", file)
        }
        walkDirectory(backgroundsDir) { file, parts ->
            writeTarFileEntry(tar, "${MEDIA_PREFIX}${BACKGROUND_DIR}/${parts.joinToString("/")}", file)
        }
    }

    private val welcomeIconsDir: File
        get() = File(context.filesDir, WELCOME_ICON_DIR)

    private val backgroundsDir: File
        get() = File(context.filesDir, BACKGROUND_DIR)

    /** 递归遍历目录文件（符号链接跳过，防循环），按相对路径回调。 */
    private fun walkDirectory(dir: File, onFile: (File, List<String>) -> Unit) {
        if (!dir.isDirectory) return
        fun walk(current: File, relParts: List<String>) {
            current.listFiles()?.forEach { f ->
                if (java.nio.file.Files.isSymbolicLink(f.toPath())) return@forEach
                val parts = relParts + f.name
                if (f.isDirectory) walk(f, parts) else onFile(f, parts)
            }
        }
        walk(dir, emptyList())
    }

    /**
     * 递归遍历本地工作区，按「.gitignore（锚定）+ 同步忽略清单」排除文件；
     * `.git` 目录强制包含（其内部不参与忽略判断）；符号链接跳过（防循环）。
     */
    private fun walkWorkspaceFiles(
        workspace: Workspace,
        onFile: (File, List<String>) -> Unit
    ) {
        val root = File(workspace.path)
        if (!root.isDirectory) return  // 远程工作区或不存在：本地无文件可备份
        val customIgnores = syncSettingsRepository.ignoredPatterns.value
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val gitignorePatterns = parseGitIgnore(File(root, ".gitignore"))

        fun walk(dir: File, relParts: List<String>) {
            dir.listFiles()?.forEach { f ->
                val parts = relParts + f.name
                if (java.nio.file.Files.isSymbolicLink(f.toPath())) return@forEach
                if (parts.first() != ".git" &&
                    (customIgnores.any { it in parts } ||
                        GitIgnoreMatcher.isIgnored(gitignorePatterns, parts, anchored = true))
                ) {
                    return@forEach
                }
                if (f.isDirectory) walk(f, parts) else onFile(f, parts)
            }
        }
        walk(root, emptyList())
    }

    /** 解析工作区根 .gitignore：去空行/注释/结尾斜杠。 */
    private fun parseGitIgnore(file: File): List<String> =
        if (!file.isFile) emptyList()
        else file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().removeSuffix("/") }

    private fun writeTarFileEntry(tar: TarArchiveOutputStream, name: String, file: File) {
        val entry = TarArchiveEntry(name).apply { size = file.length() }
        tar.putArchiveEntry(entry)
        FileInputStream(file).use { it.copyTo(tar) }
        tar.closeArchiveEntry()
    }

    // ── 导入辅助 ──────────────────────────────────────────────

    private suspend fun restoreFromTar(tar: TarArchiveInputStream, selectedWorkspaces: Set<String>?): RestoreStats {
        val restoreMapping = mutableMapOf<String, Workspace>()
        val restoredSkills = mutableSetOf<String>()
        val restoredPlugins = mutableSetOf<String>()
        var metadata: BackupMetadata? = null
        var stats = RestoreStats()
        var entry = tar.nextEntry
        while (entry != null) {
            when (entry.name) {
                FILE_LEGACY_SNAPSHOT -> {
                    val plain = tar.readBytes()
                    val snapshot = json.decodeFromString(BackupSnapshot.serializer(), String(plain, Charsets.UTF_8))
                    checkVersion(snapshot.schemaVersion)
                    return restoreLegacy(snapshot)
                }
                FILE_METADATA -> {
                    val plain = tar.readBytes()
                    metadata = json.decodeFromString(BackupMetadata.serializer(), String(plain, Charsets.UTF_8))
                    checkVersion(metadata.schemaVersion)
                }
                FILE_SESSIONS -> {
                    val restored = restoreJsonl(tar, ChatSessionDto.serializer()) { dtos ->
                        chatSessionDao.upsertAll(
                            dtos.map { dto ->
                                dto.copy(
                                    workspacePath = resolveSessionWorkspace(dto.workspacePath, restoreMapping)
                                ).toEntity()
                            }
                        )
                    }
                    // 会话是关键数据：全都没解析出来说明备份本身坏了，直接失败而不是"成功 0 条"。
                    if (restored.inserted == 0 && restored.skipped > 0) {
                        throw IllegalStateException(
                            "备份里的会话数据全部无法解析（损坏 ${restored.skipped} 条），请重新导出备份后再试"
                        )
                    }
                    FileLogger.i(TAG, "恢复会话 ${restored.inserted} 条，跳过 ${restored.skipped} 条")
                    stats += RestoreStats(
                        chatSessions = restored.inserted,
                        skippedItems = restored.skipped,
                        skippedChatSessions = restored.skipped
                    )
                }
                FILE_MESSAGES -> {
                    val restored = restoreJsonl(tar, AgentMessageDto.serializer()) { dtos ->
                        agentMessageDao.insertAll(dtos.map { it.toEntity() })
                    }
                    FileLogger.i(TAG, "恢复消息 ${restored.inserted} 条，跳过 ${restored.skipped} 条")
                    stats += RestoreStats(
                        agentMessages = restored.inserted,
                        skippedItems = restored.skipped,
                        skippedAgentMessages = restored.skipped
                    )
                }
                FILE_TODOS -> {
                    val restored = restoreJsonl(tar, TodoItemDto.serializer()) { dtos ->
                        todoItemDao.upsertAll(dtos.map { it.toEntity() })
                    }
                    FileLogger.i(TAG, "恢复待办 ${restored.inserted} 条，跳过 ${restored.skipped} 条")
                    stats += RestoreStats(
                        todoItems = restored.inserted,
                        skippedItems = restored.skipped,
                        skippedTodoItems = restored.skipped
                    )
                }
                FILE_ROLES -> {
                    val restored = restoreJsonl(tar, RoleDto.serializer()) { dtos ->
                        roleDao.upsertAll(dtos.map { it.toEntity() })
                    }
                    ensureDefaultRole()
                    FileLogger.i(TAG, "恢复角色 ${restored.inserted} 条，跳过 ${restored.skipped} 条")
                    stats += RestoreStats(
                        roles = restored.inserted,
                        skippedItems = restored.skipped,
                        skippedRoles = restored.skipped
                    )
                }
                FILE_GIT_CREDENTIALS -> {
                    val count = restoreGitCredentialsEntry(tar)
                    FileLogger.i(TAG, "恢复 git 凭据 $count 条")
                    stats += RestoreStats(gitCredentials = count)
                }
                else -> {
                    if (entry.name.startsWith(WORKSPACE_PREFIX)) {
                        stats += restoreWorkspaceEntry(tar, entry.name, selectedWorkspaces, restoreMapping)
                    } else if (entry.name.startsWith(SKILL_PREFIX)) {
                        restoreSkillEntry(tar, entry.name)?.let { restoredSkills += it }
                    } else if (entry.name.startsWith(PLUGIN_PREFIX)) {
                        restorePluginEntry(tar, entry.name)?.let { restoredPlugins += it }
                    } else if (entry.name.startsWith(CUSTOM_PROMPT_PREFIX)) {
                        if (restoreCustomPromptEntry(tar, entry.name)) stats += RestoreStats(customPrompts = 1)
                    } else if (entry.name.startsWith(MEDIA_PREFIX)) {
                        if (restoreMediaEntry(tar, entry.name)) stats += RestoreStats(mediaFiles = 1)
                    } else if (entry.name.startsWith(SETTINGS_PREFIX)) {
                        if (restoreSettingsEntry(tar, entry.name)) {
                            stats += RestoreStats(settingsFiles = 1)
                        }
                    }
                }
            }
            entry = tar.nextEntry
        }
        val meta = metadata ?: run {
            FileLogger.e(TAG, "导入失败：tar 中缺少 metadata.json")
            error("不是有效的 AiVerse 备份文件：缺少 metadata.json")
        }
        FileLogger.i(TAG, "tar 解析完成，开始还原元数据段")
        applyRestoredWorkspaceAliases(meta, restoreMapping)
        return stats + RestoreStats(skills = restoredSkills.size, plugins = restoredPlugins.size) + restoreMeta(meta, restoreMapping)
    }

    /**
     * 还原工作区显示名（别名）：备份 meta 带别名且目标工作区当前无别名时写入。
     * 按**还原后的目录名**（[restoreMapping] 的 value，重名已由 uniqueName 加后缀）写，非备份名；
     * 仅处理内部工作区（外部/远程不纳入备份，不在还原映射里）。
     */
    private suspend fun applyRestoredWorkspaceAliases(
        meta: BackupMetadata,
        restoreMapping: Map<String, Workspace>
    ) {
        val aliasByBackupName = meta.workspaces
            .mapNotNull { m -> m.alias?.takeIf { it.isNotBlank() }?.let { m.name to it } }
            .toMap()
        if (aliasByBackupName.isEmpty()) return
        for ((backupName, resolved) in restoreMapping) {
            if (resolved.type != WorkspaceType.INTERNAL) continue
            val alias = aliasByBackupName[backupName] ?: continue
            val latest = workspaceRepository.workspaces.value.firstOrNull {
                it.name == resolved.name && it.type == WorkspaceType.INTERNAL
            }
            // 目标已有别名（如本机重装前已重命名）时不覆盖，尊重本机现状
            if (latest == null || !latest.displayName.isNullOrBlank()) continue
            runCatching { workspaceRepository.setWorkspaceAlias(latest, alias) }
                .onFailure { FileLogger.w(TAG, "还原工作区别名失败: ${resolved.name} -> $alias", it) }
                .onSuccess { FileLogger.i(TAG, "还原工作区别名: ${resolved.name} -> $alias") }
        }
    }

    /** 逐行解析 jsonl 条目，每 [PAGE_SIZE] 条回调一次批量插入；返回该文件的总条数。 */
    /** 逐行恢复的结果：成功写入多少条、因格式异常跳过多少条。 */
    private data class JsonlRestore(val inserted: Int, val skipped: Int)

    private suspend fun <T> restoreJsonl(tar: TarArchiveInputStream, serializer: KSerializer<T>, insert: suspend (List<T>) -> Unit): JsonlRestore {
        val buffer = ByteArray(64 * 1024)
        val line = ByteArrayOutputStream(16 * 1024)
        val batch = ArrayList<T>(PAGE_SIZE)
        var count = 0
        var skipped = 0
        while (true) {
            val n = tar.read(buffer)
            if (n < 0) break
            var start = 0
            for (i in 0 until n) {
                if (buffer[i] == '\n'.code.toByte()) {
                    line.write(buffer, start, i - start)
                    start = i + 1
                    if (line.size() > 0) {
                        // 注意：ByteArrayOutputStream.toString(Charset) 是 API 33 才有的方法，
                        // 在 Android 13 以下会抛 NoSuchMethodError，必须用 String(byte[], Charset) 构造器。
                        // 单行解析失败不能拖垮整个导入：跳过并计数，最后在结果里如实报告。
                        val decoded = decodeLine(serializer, line.toByteArray())
                        if (decoded == null) skipped++ else batch.add(decoded)
                        line.reset()
                        if (batch.size >= PAGE_SIZE) {
                            count += batch.size
                            insert(batch.toList())
                            batch.clear()
                        }
                    } else {
                        line.reset()
                    }
                }
            }
            line.write(buffer, start, n - start)
        }
        if (line.size() > 0) {
            val decoded = decodeLine(serializer, line.toByteArray())
            if (decoded == null) skipped++ else batch.add(decoded)
        }
        if (batch.isNotEmpty()) {
            count += batch.size
            insert(batch.toList())
        }
        return JsonlRestore(inserted = count, skipped = skipped)
    }

    /** 单行 jsonl 解析；格式不对时返回 null（由调用方计入跳过数），不抛异常。 */
    private fun <T> decodeLine(serializer: KSerializer<T>, bytes: ByteArray): T? =
        runCatching {
            json.decodeFromString(serializer, String(bytes, Charsets.UTF_8))
        }.getOrElse {
            FileLogger.w(TAG, "备份条目解析失败，已跳过", it)
            null
        }

    private fun checkVersion(schemaVersion: Int) {
        if (schemaVersion > currentSchemaVersion()) {
            error("备份的数据库版本 v$schemaVersion 高于本应用 v${currentSchemaVersion()}，请升级应用")
        }
    }

    /** 旧格式（单文件 snapshot.json 完整快照）还原。 */
    private suspend fun restoreLegacy(snapshot: BackupSnapshot): RestoreStats {
        val restoreMapping = mutableMapOf<String, Workspace>()
        var stats = restoreMeta(snapshot.toMetadata(), restoreMapping)
        if (snapshot.chatSessions.isNotEmpty()) {
            // 按备份中的工作区路径逐个解析到目标工作区（不存在则自动创建空工作区），不再全部塞进当前工作区。
            chatSessionDao.upsertAll(
                snapshot.chatSessions.map {
                    it.copy(workspacePath = resolveSessionWorkspace(it.workspacePath, restoreMapping)).toEntity()
                }
            )
        }
        if (snapshot.agentMessages.isNotEmpty()) {
            agentMessageDao.insertAll(snapshot.agentMessages.map { it.toEntity() })
        }
        if (snapshot.todoItems.isNotEmpty()) {
            todoItemDao.upsertAll(snapshot.todoItems.map { it.toEntity() })
        }
        if (snapshot.roles.isNotEmpty()) {
            roleDao.upsertAll(snapshot.roles.map { it.toEntity() })
            ensureDefaultRole()
        }
        applyRestoredWorkspaceAliases(snapshot.toMetadata(), restoreMapping)
        return stats + RestoreStats(
            chatSessions = snapshot.chatSessions.size,
            agentMessages = snapshot.agentMessages.size,
            todoItems = snapshot.todoItems.size,
            roles = snapshot.roles.size
        )
    }

    /** 元数据段还原（小表 + 应用设置 + 记忆/收藏），新旧格式共用。 */
    private suspend fun restoreMeta(meta: BackupMetadata, restoreMapping: MutableMap<String, Workspace>): RestoreStats {
        FileLogger.i(
            TAG,
            "还原元数据：providers=${meta.providers.size} remoteConnections=${meta.remoteConnections.size} remoteMounts=${meta.remoteMounts.size} " +
                "mcpServers=${meta.mcpServers.size} permissionRules=${meta.globalPermissionRules.size} syncSettings=${meta.syncSettings != null}"
        )
        if (meta.providers.isNotEmpty()) {
            aiProviderDao.insertAllProviders(meta.providers.map { it.toEntity() })
        }
        if (meta.remoteConnections.isNotEmpty()) {
            remoteConnectionDao.insertAllConnections(meta.remoteConnections.mapNotNull { it.toEntity() })
        }
        if (meta.remoteMounts.isNotEmpty()) {
            // 指向已移除协议（旧版本的本地通道）的挂载一并跳过，避免外键约束失败。
            val connectionIds = remoteConnectionDao.getAllConnectionsOnce().map { it.id }.toSet()
            remoteConnectionDao.insertAllMounts(
                meta.remoteMounts.map { it.toEntity() }.filter { it.connectionId in connectionIds }
            )
        }
        if (meta.mcpServers.isNotEmpty()) {
            mcpConfigRepository.setGlobalServers(meta.mcpServers)
            mcpManager.reload()
        }
        if (meta.globalPermissionRules.isNotEmpty()) {
            permissionRulesRepository.setGlobalRules(meta.globalPermissionRules)
        }
        if (meta.includesAppSettings) {
            meta.themeMode?.let { themeSettingsRepository.restore(it) }
            themeSettingsRepository.restoreColors(meta.themePresetId, meta.dynamicColorEnabled)
            appearanceSettingsRepository.restoreCodeDisplay(
                meta.codeFontSizeSp,
                meta.codeWrapEnabled,
                meta.codeAutoFoldEnabled,
                meta.codeShowLineNumber,
                meta.userBubbleOpacity,
                meta.assistantBubbleOpacity
            )
            appearanceSettingsRepository.restoreChatPanelOpacity(meta.chatPanelOpacity)
            appearanceSettingsRepository.restoreFontSizes(
                meta.chatFontSizeSp,
                meta.inputFontSizeSp,
                meta.uiFontScale,
                meta.uiDensity
            )
            appearanceSettingsRepository.restoreUiFontPath(meta.uiFontPath)
            keepaliveSettingsRepository.restore(meta.keepaliveEnabled)
            liveNotificationSettingsRepository.restore(meta.liveNotificationEnabled)
            approvalNotificationSettingsRepository.restore(meta.approvalNotificationEnabled)
            screenOnSettingsRepository.restore(meta.screenOnEnabled)
            agentSoundSettingsRepository.restore(meta.agentSoundEnabled)
            generalSettingsRepository.restoreAutoRemoveStaleModels(meta.autoRemoveStaleModels)
            generalSettingsRepository.restoreStartupSessionMode(meta.startupSessionMode)
            generalSettingsRepository.restoreFirstByteTimeoutSec(meta.firstByteTimeoutSec)
            generalSettingsRepository.restoreStreamIdleTimeoutSec(meta.streamIdleTimeoutSec)
            generalSettingsRepository.restoreMaxNetworkRetries(meta.maxNetworkRetries)
            generalSettingsRepository.restoreSubAgentMaxRunning(meta.subAgentMaxRunning)
            generalSettingsRepository.restoreEnterToSend(meta.enterToSend)
            generalSettingsRepository.restoreCompactionThresholdPercent(meta.compactionThresholdPercent)
            generalSettingsRepository.restoreSendFileMaxSizeMb(meta.sendFileMaxSizeMb)
            generalSettingsRepository.restoreDeleteExternalWorkspaceSessions(meta.deleteExternalWorkspaceSessions)
            generalSettingsRepository.restoreMcpLazyTools(meta.mcpLazyTools)
            logSettingsRepository.restore(meta.logLevel)
            if (meta.visionProviderId.isNotBlank() || meta.visionModel.isNotBlank()) {
                visionModelSettingsRepository.setVisionModel(meta.visionProviderId, meta.visionModel)
            }
            if (meta.compactionProviderId.isNotBlank() || meta.compactionModel.isNotBlank()) {
                compactionModelSettingsRepository.setCompactionModel(meta.compactionProviderId, meta.compactionModel)
            }
            if (meta.optimizeProviderId.isNotBlank() || meta.optimizeModel.isNotBlank()) {
                optimizeInputModelSettingsRepository.setOptimizeInputModel(meta.optimizeProviderId, meta.optimizeModel)
            }
            meta.syncSettings?.let { syncSettingsRepository.restore(it) }
            meta.welcomeEntriesJson?.let { welcomeEntriesRepository.restoreFromJson(rewriteWelcomeEntriesIconPaths(it)) }
            if (meta.defaultModelProviderId.isNotBlank() || meta.defaultModel.isNotBlank()) {
                defaultModelSettingsRepository.setDefaultModel(meta.defaultModelProviderId, meta.defaultModel)
            }
            meta.languageTag?.let { languageSettingsRepository.restore(it) }
            meta.inputBarPrefsJson?.let { restoreInputBarPrefs(it) }
            meta.backgroundImagePath?.let { path ->
                // 备份里的路径是导出设备的绝对路径；还原后指向本机 backgrounds 目录下同名文件。
                val fileName = path.substringAfterLast('/')
                if (fileName.isNotBlank()) {
                    backgroundSettingsRepository.restoreImagePath(
                        File(context.filesDir, "${BACKGROUND_DIR}/$fileName").absolutePath
                    )
                }
            }
        }

        if (meta.memories.isNotEmpty()) {
            meta.memories.forEach { dto ->
                val scope = runCatching { MemoryScope.valueOf(dto.scope) }.getOrDefault(MemoryScope.GLOBAL)
                if (scope == MemoryScope.GLOBAL) {
                    memoryRepository.saveMemory(dto.name, dto.description, dto.content, MemoryScope.GLOBAL, null)
                } else {
                    val targetPath = resolveSessionWorkspace(dto.projectRoot, restoreMapping)
                    memoryRepository.saveMemory(dto.name, dto.description, dto.content, MemoryScope.PROJECT, targetPath)
                }
            }
            FileLogger.i(TAG, "恢复记忆 ${meta.memories.size} 条")
        }
        if (meta.favorites.isNotEmpty()) {
            favoriteRepository.mergeAll(meta.favorites.map { it.toEntity() })
            FileLogger.i(TAG, "恢复收藏 ${meta.favorites.size} 条")
        }

        return RestoreStats(
            providers = meta.providers.size,
            remoteConnections = meta.remoteConnections.size,
            remoteMounts = meta.remoteMounts.size,
            mcpServers = meta.mcpServers.size,
            globalPermissionRules = meta.globalPermissionRules.size,
            memories = meta.memories.size,
            favorites = meta.favorites.size
        )
    }

    /**
     * 还原单个技能文件条目：`skills/<scope>/<name>/<相对路径>`。
     * 逐文件写回技能目录（同名技能覆盖）；成功返回 `<scope>/<name>`（供去重计数），失败返回 null。
     */
    private fun restoreSkillEntry(tar: TarArchiveInputStream, name: String): String? {
        val parts = name.removePrefix(SKILL_PREFIX).split('/')
        if (parts.size < 3) return null
        val scope = runCatching { SkillScope.valueOf(parts[0]) }.getOrNull() ?: return null
        val skillName = parts[1]
        val rel = parts.drop(2).joinToString("/")
        if (rel.isBlank()) return null
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return null
        return if (skillRepository.restoreSkillDir(skillName, scope, mapOf(rel to bytes))) {
            "${parts[0]}/$skillName"
        } else {
            null
        }
    }

    /**
     * 还原单个脚本插件文件条目：`plugins/<id>/<相对路径>`。
     * 逐文件写回插件目录（同名插件覆盖，plugin.json 在插件根）；成功返回插件 id（供去重计数），失败返回 null。
     */
    private fun restorePluginEntry(tar: TarArchiveInputStream, name: String): String? {
        val parts = name.removePrefix(PLUGIN_PREFIX).split('/')
        if (parts.size < 2) return null
        val id = parts[0]
        val relParts = parts.drop(1)
        // 防路径穿越：跳过空段与 . / ..（备份内部生成，正常不含，兜底防御）。
        if (id.isBlank() || relParts.any { it.isBlank() || it == "." || it == ".." }) return null
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return null
        val target = File(pluginManager.pluginDir(id), relParts.joinToString("/"))
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return id
    }

    /**
     * 还原单个自定义提示词文件条目：`customPrompts/<相对路径>`。
     * 逐文件写回 prompts.custom 目录（同名覆盖）；成功返回 true，失败返回 false。
     */
    private fun restoreCustomPromptEntry(tar: TarArchiveInputStream, name: String): Boolean {
        val rel = name.removePrefix(CUSTOM_PROMPT_PREFIX)
        if (rel.isBlank() || rel.split('/').any { it.isBlank() || it == "." || it == ".." }) return false
        val target = File(promptsCustomDir, rel)
        target.parentFile?.mkdirs()
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return false
        target.writeBytes(bytes)
        return true
    }

    /**
     * 还原单个媒体文件条目：`media/<kind>/<文件名>`（kind 为 welcome_icons 或 backgrounds）。
     * 逐文件写回对应目录（同名覆盖）；成功返回 true，失败返回 false。
     */
    private fun restoreMediaEntry(tar: TarArchiveInputStream, name: String): Boolean {
        val parts = name.removePrefix(MEDIA_PREFIX).split('/')
        if (parts.size < 2) return false
        val kind = parts[0]
        val fileName = parts.drop(1).joinToString("/")
        if (fileName.isBlank() || fileName.split('/').any { it.isBlank() || it == "." || it == ".." }) return false
        val base = when (kind) {
            WELCOME_ICON_DIR -> welcomeIconsDir
            BACKGROUND_DIR -> backgroundsDir
            else -> return false
        }
        val target = File(base, fileName)
        target.parentFile?.mkdirs()
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return false
        target.writeBytes(bytes)
        return true
    }

    /** 还原单个 git 凭据条目：`credentials/git-credentials`（git-credential-store 原始格式，每行一条）。
     *  写入 filesDir/aiverse/git-credentials（内部做 base64+反转 混淆后原子落盘）；返回还原的凭据条数。 */
    private suspend fun restoreGitCredentialsEntry(tar: TarArchiveInputStream): Int {
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return 0
        val raw = String(bytes, Charsets.UTF_8)
        credentialRepository.writeRawCredentials(raw)
        return raw.lines().count { it.isNotBlank() }
    }

    /** 导入角色后保证内置默认角色存在（缺失则补建空身份默认角色，RoleSeeder 后续会在需要时填充）。 */
    private suspend fun ensureDefaultRole() {
        if (roleDao.getById(Role.DEFAULT_ROLE_ID) != null) return
        val now = System.currentTimeMillis()
        roleDao.upsert(
            RoleEntity(
                id = Role.DEFAULT_ROLE_ID,
                name = "默认角色",
                description = "",
                identityPrompt = "",
                tags = "",
                avatarKind = "NONE",
                avatarValue = "",
                enabled = true,
                isBuiltin = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 输入框快捷栏偏好 → JSON（供备份应用设置）。 */
    private fun encodeInputBarPrefs(): String? {
        val p = inputBarSettingsRepository.load()
        val dto = InputBarPrefsDto(
            showMcp = p.showMcp,
            showThinking = p.showThinking,
            showFolder = p.showFolder,
            showModel = p.showModel,
            showWebSearch = p.showWebSearch,
            showOptimize = p.showOptimize,
            showPlus = p.showPlus,
            webSearchEnabled = inputBarSettingsRepository.webSearchEnabled
        )
        return runCatching { json.encodeToString(InputBarPrefsDto.serializer(), dto) }.getOrNull()
    }

    /** 从备份的 JSON 还原输入框快捷栏偏好（解析失败时静默忽略）。 */
    private fun restoreInputBarPrefs(jsonStr: String) {
        val dto = runCatching { json.decodeFromString(InputBarPrefsDto.serializer(), jsonStr) }.getOrNull() ?: return
        inputBarSettingsRepository.updateInputBarPrefs(
            InputBarPrefs(
                showMcp = dto.showMcp,
                showThinking = dto.showThinking,
                showFolder = dto.showFolder,
                showModel = dto.showModel,
                showWebSearch = dto.showWebSearch,
                showOptimize = dto.showOptimize,
                showPlus = dto.showPlus
            )
        )
        inputBarSettingsRepository.webSearchEnabled = dto.webSearchEnabled
    }

    /**
     * 欢迎卡片 JSON 里的图片图标路径改写：备份里的 iconValue 是导出设备的绝对路径，
     * 导入到新设备后目录不同，需把目录前缀替换为当前 filesDir/welcome_icons（文件名保留，与还原后的文件一致）。
     */
    private fun rewriteWelcomeEntriesIconPaths(jsonStr: String): String {
        val entries = runCatching {
            json.decodeFromString(ListSerializer(WelcomeEntry.serializer()), jsonStr)
        }.getOrNull() ?: return jsonStr
        val newBase = File(context.filesDir, WELCOME_ICON_DIR).absolutePath
        val rewritten = entries.map { entry ->
            if (entry.iconKind == WelcomeIconKind.IMAGE && entry.iconValue.isNotBlank()) {
                entry.copy(iconValue = "$newBase/${entry.iconValue.substringAfterLast('/')}")
            } else {
                entry
            }
        }
        return runCatching {
            json.encodeToString(ListSerializer(WelcomeEntry.serializer()), rewritten)
        }.getOrDefault(jsonStr)
    }

    /**
     * 还原单个工作区文件条目：`workspaces/<name>/<相对路径>`。
     * 仅处理勾选的工作区（[selectedWorkspaces] 非 null 时），且只允许写入内部工作区；本地无同名工作区时自动创建，
     * 避免新设备/重装后导入的工作区文件因找不到目标而丢失。
     */
    private suspend fun restoreWorkspaceEntry(
        tar: TarArchiveInputStream,
        entryName: String,
        selectedWorkspaces: Set<String>?,
        restoreMapping: MutableMap<String, Workspace>
    ): RestoreStats {
        val segments = entryName.split("/", limit = 3)
        if (segments.size != 3) return RestoreStats()
        val wsName = segments[1]
        if (selectedWorkspaces != null && wsName !in selectedWorkspaces) return RestoreStats()

        val ws = resolveWorkspaceForRestore(wsName, restoreMapping) ?: return RestoreStats()

        val wsDir = File(ws.path)
        if (!isPathInsideWorkspace(wsDir, segments[2])) return RestoreStats()
        val target = File(wsDir, segments[2])
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out -> tar.copyTo(out) }
        FileLogger.i(TAG, "恢复工作区文件：${ws.name}/${segments[2]}")
        return RestoreStats(workspaceFiles = 1)
    }

    /**
     * 按备份中的工作区名解析到目标工作区：优先复用本轮导入已解析的映射，其次复用本地同名内部工作区，
     * 都不存在则自动创建一个空内部工作区。工作区文件条目与会话共用同一映射，保证两者落到同一个工作区。
     */
    private suspend fun resolveWorkspaceForRestore(
        wsName: String,
        restoreMapping: MutableMap<String, Workspace>
    ): Workspace? {
        restoreMapping[wsName]?.let { return it }
        val reservedNames = restoreMapping.values.map { it.name }.toSet()
        val existing = workspaceRepository.workspaces.value.firstOrNull {
            it.name == wsName && it.type == WorkspaceType.INTERNAL && it.name !in reservedNames
        }
        val resolved = existing ?: run {
            val occupied = workspaceRepository.workspaces.value.map { it.name }.toSet() + reservedNames
            val targetName = WorkspaceRepository.uniqueName(wsName, occupied)
            workspaceRepository.createWorkspace(targetName) ?: return null
        }
        restoreMapping[wsName] = resolved
        FileLogger.i(TAG, "导入会话目标工作区：${resolved.name} -> ${resolved.path}")
        return resolved
    }

    /**
     * 解析某会话在备份中的原始工作区路径到目标设备的工作区：
     * 1. 现有工作区路径与备份一致（同设备重装场景，内部/外部本地都能对上）直接复用；
     * 2. 否则按路径末段当作工作区名，经 [resolveWorkspaceForRestore] 复用同名内部工作区或自动创建空工作区；
     * 3. 路径为空或解析失败（如无法创建）时兜底回当前工作区。
     */
    private suspend fun resolveSessionWorkspace(
        backupWorkspacePath: String,
        restoreMapping: MutableMap<String, Workspace>
    ): String {
        if (backupWorkspacePath.isBlank()) return workspaceRepository.currentPath()
        workspaceRepository.workspaces.value
            .firstOrNull { it.path == backupWorkspacePath && it.name !in restoreMapping.keys }
            ?.let { return it.path }
        val name = backupWorkspacePath.trimEnd('/').substringAfterLast('/')
        if (name.isEmpty()) return workspaceRepository.currentPath()
        return resolveWorkspaceForRestore(name, restoreMapping)?.path ?: workspaceRepository.currentPath()
    }

    /** 防路径穿越：工作区必须是本地目录，且相对路径规范化后仍位于其内。 */
    private fun isPathInsideWorkspace(wsDir: File, relPath: String): Boolean {
        if (!wsDir.isDirectory) return false
        val root = wsDir.canonicalPath
        val target = File(wsDir, relPath).canonicalPath
        return target == root || target.startsWith(root + File.separator)
    }

    private fun createTempFile(): File = File.createTempFile("backup", ".tmp", context.cacheDir)

    // ── Entity ↔ DTO 转换 ──────────────────────────────────────

    private fun AIProviderEntity.toDto() = ProviderDto(
        id = id,
        name = name,
        type = type,
        apiKey = KeystoreCipher.decryptString(apiKey),
        baseUrl = baseUrl,
        defaultModel = defaultModel,
        models = models,
        selectedModel = selectedModel,
        isEnabled = isEnabled,
        useFullUrl = useFullUrl,
        useResponseApi = useResponseApi,
        anthropicCacheBreakpoints = anthropicCacheBreakpoints,
        openaiChatCacheKey = openaiChatCacheKey,
        dashboardScriptPath = dashboardScriptPath,
        dashboardRefreshInterval = dashboardRefreshInterval,
        sortOrder = sortOrder,
        multiKeyEnabled = multiKeyEnabled,
        apiKeys = KeystoreCipher.decryptString(apiKeys),
        keyRotationStrategy = keyRotationStrategy,
        keyFailoverThreshold = keyFailoverThreshold,
        keyCooldownMinutes = keyCooldownMinutes,
        scriptParams = KeystoreCipher.decryptString(scriptParams),
        keySwitchStatusCodes = keySwitchStatusCodes
    )

    private fun ProviderDto.toEntity() = AIProviderEntity(
        id = id,
        name = name,
        type = type,
        apiKey = KeystoreCipher.encryptString(apiKey),
        baseUrl = baseUrl,
        defaultModel = defaultModel,
        models = models,
        selectedModel = selectedModel,
        isEnabled = isEnabled,
        useFullUrl = useFullUrl,
        useResponseApi = useResponseApi,
        anthropicCacheBreakpoints = anthropicCacheBreakpoints ?: true,
        openaiChatCacheKey = openaiChatCacheKey ?: false,
        dashboardScriptPath = dashboardScriptPath ?: "",
        dashboardRefreshInterval = dashboardRefreshInterval ?: 5,
        sortOrder = sortOrder ?: 0,
        multiKeyEnabled = multiKeyEnabled ?: false,
        apiKeys = KeystoreCipher.encryptString(apiKeys ?: ""),
        keyRotationStrategy = keyRotationStrategy ?: "SEQUENTIAL",
        keyFailoverThreshold = keyFailoverThreshold ?: 2,
        keyCooldownMinutes = keyCooldownMinutes ?: 5,
        scriptParams = KeystoreCipher.encryptString(scriptParams ?: ""),
        keySwitchStatusCodes = keySwitchStatusCodes ?: ""
    )

    private fun RemoteConnectionEntity.toDto() = RemoteConnectionDto(
        id, name, protocol.name, host, port, username, authType,
        KeystoreCipher.decryptString(authData), passphrase?.let { KeystoreCipher.decryptString(it) },
        sortOrder
    )

    /** 旧备份可能含已移除的协议（本地通道），此类记录跳过不恢复。 */
    private fun RemoteConnectionDto.toEntity(): RemoteConnectionEntity? {
        val parsed = runCatching { RemoteProtocol.valueOf(protocol) }.getOrNull() ?: return null
        return RemoteConnectionEntity(
            id, name, parsed, host, port, username, authType,
            if (authType.equals("PASSWORD", ignoreCase = true)) KeystoreCipher.encryptString(authData) else authData,
            passphrase?.let { KeystoreCipher.encryptString(it) },
            sortOrder = sortOrder ?: 0
        )
    }

    private fun RemoteMountEntity.toDto() = RemoteMountDto(id, connectionId, remotePath, localMountPath, isActive, autoConnect)
    private fun RemoteMountDto.toEntity() = RemoteMountEntity(id, connectionId, remotePath, localMountPath, isActive, autoConnect)

    private fun ChatSessionEntity.toDto() = ChatSessionDto(
        id = id, title = title, createdAt = createdAt, updatedAt = updatedAt, workspacePath = workspacePath,
        mode = mode, modeBeforePlan = modeBeforePlan, reasoningEffort = reasoningEffort, providerId = providerId,
        model = model, isPinned = isPinned, parentId = parentId, subagentType = subagentType,
        lastInputTokens = lastInputTokens, roleId = roleId
    )

    private fun ChatSessionDto.toEntity() = ChatSessionEntity(
        id = id, title = title, createdAt = createdAt, updatedAt = updatedAt, workspacePath = workspacePath,
        mode = mode, modeBeforePlan = modeBeforePlan, reasoningEffort = reasoningEffort, providerId = providerId,
        model = model, isPinned = isPinned, parentId = parentId, subagentType = subagentType,
        lastInputTokens = lastInputTokens, roleId = roleId
    )

    private fun RoleEntity.toDto() = RoleDto(
        id = id, name = name, description = description, identityPrompt = identityPrompt, tags = tags,
        avatarKind = avatarKind, avatarValue = avatarValue, enabled = enabled, isBuiltin = isBuiltin,
        sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt, workspaceId = workspaceId
    )

    private fun RoleDto.toEntity() = RoleEntity(
        id = id, name = name, description = description, identityPrompt = identityPrompt, tags = tags,
        avatarKind = avatarKind, avatarValue = avatarValue, enabled = enabled, isBuiltin = isBuiltin,
        sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt, workspaceId = workspaceId
    )

    private fun AgentMessageEntity.toDto() = AgentMessageDto(
        id, sessionId, role, content, timestamp, toolCallsJson, toolCallId, toolName, toolArgs,
        isError, reasoning, signature, attachmentsJson, isCompacted, isContextSummary, isCompactionMarker,
        thinkingBlocksJson, isContextExcluded, compactedBySummaryId
    )

    private fun AgentMessageDto.toEntity(): AgentMessageEntity {
        val summary = isContextSummary || (role == "ASSISTANT" && content.startsWith(
            com.aiverse.feature.agent.domain.model.CONTEXT_SUMMARY_LEGACY_PREFIX
        ))
        val excluded = isContextExcluded || (
            isCompacted && !summary && !isCompactionMarker && role == "ASSISTANT" &&
                toolCallsJson == null && compactedBySummaryId == null && isLegacyUsageTable(content)
        )
        return AgentMessageEntity(
            id, sessionId, role, content, timestamp, toolCallsJson, toolCallId, toolName, toolArgs,
            isError, reasoning, signature, attachmentsJson, isCompacted && !excluded, summary, isCompactionMarker,
            thinkingBlocksJson = thinkingBlocksJson,
            isContextExcluded = excluded,
            compactedBySummaryId = if (excluded) null else compactedBySummaryId
        )
    }

    private fun isLegacyUsageTable(content: String): Boolean {
        val lines = content.split('\n')
        if (lines.size != 7 || lines[0] != "| 项目 | 今日 | 累计 |" || lines[1] != "|---|---|---|") {
            return false
        }
        val labels = listOf("调用次数", "输入 tokens", "输出 tokens", "缓存命中 tokens", "预估费用")
        return labels.indices.all { index ->
            val line = lines[index + 2]
            line.startsWith("| ${labels[index]} | ") && line.endsWith(" |") &&
                line.count { it == '|' } == 4 && line.split('|').let { columns ->
                    columns[2].startsWith(" ") && columns[2].endsWith(" ") &&
                        columns[3].startsWith(" ") && columns[3].endsWith(" ")
                }
        }
    }

    private fun TodoItemEntity.toDto() = TodoItemDto(id, sessionId, subject, description, status, priority, order, createdAt, updatedAt)
    private fun TodoItemDto.toEntity() = TodoItemEntity(id, sessionId, subject, description, status, priority, order, createdAt, updatedAt)

    private fun FavoriteItem.toDto() = FavoriteItemDto(id, content, sessionTitle, createdAt)
    private fun FavoriteItemDto.toEntity() = FavoriteItem(id, content, sessionTitle, createdAt)

    private companion object {
        const val TAG = "BackupManager"
        const val PAGE_SIZE = 500
        const val FILE_METADATA = "metadata.json"
        const val FILE_SESSIONS = "chatSessions.jsonl"
        const val FILE_MESSAGES = "messages.jsonl"
        const val FILE_TODOS = "todoItems.jsonl"
        const val FILE_LEGACY_SNAPSHOT = "snapshot.json"
        const val FILE_ROLES = "roles.jsonl"
        const val WORKSPACE_PREFIX = "workspaces/"
        const val SKILL_PREFIX = "skills/"
        const val PLUGIN_PREFIX = "plugins/"
        const val CUSTOM_PROMPT_PREFIX = "customPrompts/"
        const val MEDIA_PREFIX = "media/"
        const val FILE_GIT_CREDENTIALS = "credentials/git-credentials"
        /** 本地设置文件：DataStore / SharedPreferences / 插件注册表 / 插件数据，整体入包。 */
        const val SETTINGS_PREFIX = "settings/"
        const val SETTINGS_DATASTORE_PREFIX = "settings/datastore/"
        const val SETTINGS_SHARED_PREFS_PREFIX = "settings/shared_prefs/"
        const val SETTINGS_PLUGIN_REGISTRY = "settings/plugin-registry.json"
        const val SETTINGS_PLUGIN_DATA_PREFIX = "settings/plugin-data/"
        /** 插件注册表文件名（与 PluginManager 中的 REGISTRY_FILE 保持一致）。 */
        const val PLUGIN_REGISTRY_FILE = "plugin-registry.json"
        const val WELCOME_ICON_DIR = "welcome_icons"
        const val BACKGROUND_DIR = "backgrounds"
    }
}

/** 带内部缓冲的 jsonl 写入器：行缓冲满 64KB 时刷入 tar 流，避免逐行写系统调用。 */
private class JsonlWriter(private val out: OutputStream) {
    private val buffer = ByteArrayOutputStream(64 * 1024)

    fun writeLine(line: String) {
        buffer.write(line.toByteArray(Charsets.UTF_8))
        buffer.write('\n'.code)
        if (buffer.size() >= 64 * 1024) flush()
    }

    fun flush() {
        buffer.writeTo(out)
        buffer.reset()
    }
}

/** 打开的 tar 流封装：加密导入时附带解密用的临时文件，关闭时一并清理。 */
private class TarSource(
    val tar: TarArchiveInputStream,
    private val temp: File?
) : AutoCloseable {
    override fun close() {
        runCatching { tar.close() }
        temp?.delete()
    }
}
