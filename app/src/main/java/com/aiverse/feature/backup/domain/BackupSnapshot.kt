package com.aiverse.feature.backup.domain

import com.aiverse.feature.agent.domain.mcp.McpServerConfig
import com.aiverse.feature.agent.domain.permission.PermissionRule
import com.aiverse.feature.settings.data.repository.SyncSettingsSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份快照：一次导出/导入的完整数据集。各 DTO 与 Room Entity 同构但解耦，避免序列化框架绑定到 Room。
 *
 * [schemaVersion] 记录导出时的 AgentDatabase 版本，导入时据此判断兼容性：
 * 备份版本 > 当前 App 版本则拒绝（字段可能缺失）；< 当前则允许（新字段取默认值）。
 */
@Serializable
data class BackupSnapshot(
    val schemaVersion: Int,
    val appVersion: String = "",
    val createdAt: Long,
    val providers: List<ProviderDto> = emptyList(),
    val remoteConnections: List<RemoteConnectionDto> = emptyList(),
    val remoteMounts: List<RemoteMountDto> = emptyList(),
    val chatSessions: List<ChatSessionDto> = emptyList(),
    val agentMessages: List<AgentMessageDto> = emptyList(),
    val todoItems: List<TodoItemDto> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val globalPermissionRules: List<PermissionRule> = emptyList(),
    val roles: List<RoleDto> = emptyList(),
    val themeMode: String? = null,
    val themePresetId: String? = null,
    val dynamicColorEnabled: Boolean = false,
    val codeFontSizeSp: Int = 13,
    val codeWrapEnabled: Boolean = false,
    val codeAutoFoldEnabled: Boolean = true,
    val codeShowLineNumber: Boolean = true,
    val userBubbleOpacity: Int = 100,
    val assistantBubbleOpacity: Int = 100,
    val keepaliveEnabled: Boolean = false,
    val liveNotificationEnabled: Boolean = false,
    val screenOnEnabled: Boolean = false,
    val agentSoundEnabled: Boolean = false,
    val approvalNotificationEnabled: Boolean = false,
    val autoRemoveStaleModels: Boolean = true,
    val startupSessionMode: String? = null,
    val firstByteTimeoutSec: Int = 300,
    val streamIdleTimeoutSec: Int = 300,
    val maxNetworkRetries: Int = 6,
    val subAgentMaxRunning: Int = 6,
    val enterToSend: Boolean = false,
    val compactionThresholdPercent: Int = 90,
    val sendFileMaxSizeMb: Int = 100,
    val deleteExternalWorkspaceSessions: Boolean = false,
    /** 「MCP 工具按需加载（实验）」开关；旧备份无此字段时回退 false（关闭）。 */
    val mcpLazyTools: Boolean = false,
    val logLevel: String? = null,
    val visionProviderId: String = "",
    val visionModel: String = "",
    val compactionProviderId: String = "",
    val compactionModel: String = "",
    val optimizeProviderId: String = "",
    val optimizeModel: String = "",
    val syncSettings: SyncSettingsSnapshot? = null,
    /** 自定义快捷入口（欢迎卡片）的 JSON 编码列表；null 表示旧备份无此字段或未备份应用设置，导入时忽略。 */
    val welcomeEntriesJson: String? = null,
    /** 新会话默认模型（providerId）；空表示未设置，导入时回退全局 active provider。 */
    val defaultModelProviderId: String = "",
    /** 新会话默认模型（model）；空表示未设置。 */
    val defaultModel: String = "",
    /** 语言 tag（如 zh-CN / en）；null 表示跟随系统。 */
    val languageTag: String? = null,
    /** 输入框快捷栏偏好（JSON 编码 [com.aiverse.feature.settings.data.repository.InputBarPrefs] + webSearchEnabled）。 */
    val inputBarPrefsJson: String? = null,
    /** 聊天背景图文件绝对路径（导出设备）；null 表示未设置背景。 */
    val backgroundImagePath: String? = null,
    val memories: List<MemoryDto> = emptyList(),
    val favorites: List<FavoriteItemDto> = emptyList(),
    val skills: List<SkillBackupMeta> = emptyList()
)

/**
 * 流式备份的元数据分片（tar 内 metadata.json）：不含聊天大表，大表走各 *.jsonl。
 * 与 [BackupSnapshot] 的元数据字段一一对应，导入时合并 jsonl 还原完整数据。
 */
@Serializable
data class BackupMetadata(
    val schemaVersion: Int,
    val appVersion: String = "",
    val createdAt: Long,
    val providers: List<ProviderDto> = emptyList(),
    val remoteConnections: List<RemoteConnectionDto> = emptyList(),
    val remoteMounts: List<RemoteMountDto> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val globalPermissionRules: List<PermissionRule> = emptyList(),
    val themeMode: String? = null,
    val themePresetId: String? = null,
    val dynamicColorEnabled: Boolean = false,
    /** 备份快照：聊天字号（sp）；null 表示旧备份无此字段或未备份应用设置，导入时清除该键回退默认。 */
    val chatFontSizeSp: Int? = null,
    /** 备份快照：输入框字号（sp）；null 表示旧备份无此字段或未备份应用设置，导入时清除该键回退默认。 */
    val inputFontSizeSp: Int? = null,
    /** 备份快照：界面缩放系数；null 表示旧备份无此字段或未备份应用设置，导入时清除该键回退默认。 */
    val uiFontScale: Float? = null,
    /** 备份快照：界面密度档 id；null 表示旧备份无此字段或未备份应用设置，导入时清除该键回退默认。 */
    val uiDensity: String? = null,
    /** 备份快照：界面字体路径；null 表示旧备份无此字段或未备份应用设置，导入时清除该键回退系统默认。 */
    val uiFontPath: String? = null,
    val codeFontSizeSp: Int = 13,
    val codeWrapEnabled: Boolean = false,
    val codeAutoFoldEnabled: Boolean = true,
    val codeShowLineNumber: Boolean = true,
    val userBubbleOpacity: Int = 100,
    val assistantBubbleOpacity: Int = 100,
    val keepaliveEnabled: Boolean = false,
    val liveNotificationEnabled: Boolean = false,
    val screenOnEnabled: Boolean = false,
    val agentSoundEnabled: Boolean = false,
    val approvalNotificationEnabled: Boolean = false,
    val autoRemoveStaleModels: Boolean = true,
    val startupSessionMode: String? = null,
    val firstByteTimeoutSec: Int = 300,
    val streamIdleTimeoutSec: Int = 300,
    val maxNetworkRetries: Int = 6,
    val subAgentMaxRunning: Int = 6,
    val enterToSend: Boolean = false,
    val compactionThresholdPercent: Int = 90,
    val sendFileMaxSizeMb: Int = 100,
    val deleteExternalWorkspaceSessions: Boolean = false,
    /** 「MCP 工具按需加载（实验）」开关；旧备份无此字段时回退 false（关闭）。 */
    val mcpLazyTools: Boolean = false,
    val logLevel: String? = null,
    val visionProviderId: String = "",
    val visionModel: String = "",
    val compactionProviderId: String = "",
    val compactionModel: String = "",
    val optimizeProviderId: String = "",
    val optimizeModel: String = "",
    val syncSettings: SyncSettingsSnapshot? = null,
    /** 自定义快捷入口（欢迎卡片）的 JSON 编码列表；null 表示旧备份无此字段或未备份应用设置，导入时忽略。 */
    val welcomeEntriesJson: String? = null,
    /** 新会话默认模型（providerId）；空表示未设置。 */
    val defaultModelProviderId: String = "",
    /** 新会话默认模型（model）；空表示未设置。 */
    val defaultModel: String = "",
    /** 语言 tag；null 表示跟随系统。 */
    val languageTag: String? = null,
    /** 输入框快捷栏偏好（JSON 编码）。 */
    val inputBarPrefsJson: String? = null,
    /** 聊天背景图文件绝对路径（导出设备）；null 表示未设置背景。 */
    val backgroundImagePath: String? = null,
    val workspaces: List<WorkspaceBackupMeta> = emptyList(),
    val memories: List<MemoryDto> = emptyList(),
    val favorites: List<FavoriteItemDto> = emptyList(),
    val skills: List<SkillBackupMeta> = emptyList(),
    val plugins: List<PluginBackupMeta> = emptyList(),
    /** 自定义提示词目录（prompts.custom）备份文件数；0 表示旧备份无此段或未勾选。 */
    val customPromptFileCount: Int = 0,
    /** 媒体文件（快捷入口图标 + 聊天背景图）备份文件数；0 表示旧备份无此段或未勾选。 */
    val mediaFileCount: Int = 0,
    val includesAppSettings: Boolean = themeMode != null
)

/** 备份中的一条角色（新角色系统）；与 [com.aiverse.feature.agent.data.local.entity.RoleEntity] 同构。 */
@Serializable
data class RoleDto(
    val id: String,
    val name: String,
    val description: String,
    /** 注入系统提示词最前的身份正文。 */
    val identityPrompt: String,
    /** 逗号分隔的标签。 */
    val tags: String,
    val avatarKind: String,
    val avatarValue: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/** 备份中的输入框快捷栏偏好（JSON 编码用）。 */
@Serializable
data class InputBarPrefsDto(
    val showMcp: Boolean = false,
    val showThinking: Boolean = true,
    val showFolder: Boolean = true,
    val showModel: Boolean = true,
    val showWebSearch: Boolean = false,
    val showOptimize: Boolean = false,
    val showPlus: Boolean = false,
    val webSearchEnabled: Boolean = true
)

/** 备份元数据中的一个技能段：名称 + 作用域 + 文件数（用于导入摘要）。 */
@Serializable
data class SkillBackupMeta(
    val name: String,
    /** "GLOBAL" 或 "PROJECT"。 */
    val scope: String,
    val fileCount: Int
)

/** 备份元数据中的一个脚本插件段：id + 名称 + 文件数（用于导入摘要）。 */
@Serializable
data class PluginBackupMeta(
    val id: String,
    val name: String,
    val fileCount: Int
)

/** 备份元数据中的一个工作区段：名称 + 备份的文件数 + 显示名别名（用于导入摘要与还原后恢复别名）。 */
@Serializable
data class WorkspaceBackupMeta(
    val name: String,
    val fileCount: Int,
    /** 显示名（别名），仅内部工作区还原时使用；旧备份无此字段，默认 null 保持兼容。 */
    val alias: String? = null
)

fun BackupSnapshot.toMetadata() = BackupMetadata(
    schemaVersion = schemaVersion,
    appVersion = appVersion,
    createdAt = createdAt,
    providers = providers,
    remoteConnections = remoteConnections,
    remoteMounts = remoteMounts,
    mcpServers = mcpServers,
    globalPermissionRules = globalPermissionRules,
    themeMode = themeMode,
    themePresetId = themePresetId,
    dynamicColorEnabled = dynamicColorEnabled,
    codeFontSizeSp = codeFontSizeSp,
    codeWrapEnabled = codeWrapEnabled,
    codeAutoFoldEnabled = codeAutoFoldEnabled,
    codeShowLineNumber = codeShowLineNumber,
    userBubbleOpacity = userBubbleOpacity,
    assistantBubbleOpacity = assistantBubbleOpacity,
    keepaliveEnabled = keepaliveEnabled,
    liveNotificationEnabled = liveNotificationEnabled,
    screenOnEnabled = screenOnEnabled,
    agentSoundEnabled = agentSoundEnabled,
    approvalNotificationEnabled = approvalNotificationEnabled,
    autoRemoveStaleModels = autoRemoveStaleModels,
    startupSessionMode = startupSessionMode,
    firstByteTimeoutSec = firstByteTimeoutSec,
    streamIdleTimeoutSec = streamIdleTimeoutSec,
    maxNetworkRetries = maxNetworkRetries,
    subAgentMaxRunning = subAgentMaxRunning,
    enterToSend = enterToSend,
    compactionThresholdPercent = compactionThresholdPercent,
    sendFileMaxSizeMb = sendFileMaxSizeMb,
    deleteExternalWorkspaceSessions = deleteExternalWorkspaceSessions,
    mcpLazyTools = mcpLazyTools,
    logLevel = logLevel,
    visionProviderId = visionProviderId,
    visionModel = visionModel,
    compactionProviderId = compactionProviderId,
    compactionModel = compactionModel,
    optimizeProviderId = optimizeProviderId,
    optimizeModel = optimizeModel,
    syncSettings = syncSettings,
    welcomeEntriesJson = welcomeEntriesJson,
    defaultModelProviderId = defaultModelProviderId,
    defaultModel = defaultModel,
    languageTag = languageTag,
    inputBarPrefsJson = inputBarPrefsJson,
    backgroundImagePath = backgroundImagePath,
    memories = memories,
    favorites = favorites,
    skills = skills,
    // 旧格式快照（snapshot.json）无插件段，转元数据时插件为空。
    plugins = emptyList()
)

@Serializable
data class ProviderDto(
    val id: String,
    val name: String,
    val type: String,
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: String = "",
    val selectedModel: String = "",
    val isEnabled: Boolean = true,
    val useFullUrl: Boolean = false,
    val useResponseApi: Boolean = false,
    /** 提供商级缓存开关；null 表示旧备份无此字段，导入时回退默认值。 */
    val anthropicCacheBreakpoints: Boolean? = null,
    val openaiChatCacheKey: Boolean? = null,
    /** 自定义面板脚本路径；null 表示旧备份无此字段，导入时回退默认值 ""。序列化名沿用历史命名以兼容旧备份。 */
    @SerialName("balanceScriptPath")
    val dashboardScriptPath: String? = null,
    /** 自定义面板刷新间隔（分钟）；null 表示旧备份无此字段，导入时回退默认值 5。序列化名沿用历史命名以兼容旧备份。 */
    @SerialName("balanceRefreshInterval")
    val dashboardRefreshInterval: Int? = null,
    /** 提供商列表排序序号；null 表示旧备份无此字段，导入时回退默认 0。 */
    val sortOrder: Int? = null,
    /** 多 Key 模式相关字段；null 表示旧备份无此字段，导入时回退默认值。 */
    val multiKeyEnabled: Boolean? = null,
    val apiKeys: String? = null,
    val keyRotationStrategy: String? = null,
    val keyFailoverThreshold: Int? = null,
    val keyCooldownMinutes: Int? = null,
    /** 自定义面板 (DIY) 脚本参数（JSON 编码）；null 表示旧备份无此字段，导入时回退为空。 */
    val scriptParams: String? = null,
    /** 多 Key 自动切换状态码（逗号分隔）；null 表示旧备份无此字段，导入时回退空串（即默认码表）。 */
    val keySwitchStatusCodes: String? = null
)

@Serializable
data class RemoteConnectionDto(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String = "password",
    val authData: String,
    val passphrase: String? = null,
    /** 连接列表排序序号；null 表示旧备份无此字段，导入时回退默认 0（由创建时间兜底排序）。 */
    val sortOrder: Int? = null
)

@Serializable
data class RemoteMountDto(
    val id: String,
    val connectionId: String,
    val remotePath: String,
    val localMountPath: String,
    val isActive: Boolean = false,
    val autoConnect: Boolean = true
)

@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val workspacePath: String = "",
    val mode: String = "BUILD",
    val modeBeforePlan: String? = null,
    val reasoningEffort: String = "MEDIUM",
    val providerId: String? = null,
    val model: String? = null,
    val isPinned: Boolean = false,
    /** 子代理会话的父会话 id；丢了它恢复后子会话会变成一堆根会话。 */
    val parentId: String? = null,
    val subagentType: String? = null,
    /** 会话最近一次真实上下文用量（inputTokens），随备份导出/恢复。 */
    val lastInputTokens: Int = 0,
    /** 会话绑定的角色 id；null 表示默认角色。 */
    val roleId: String? = null
)

@Serializable
data class AgentMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val isError: Boolean = false,
    val reasoning: String? = null,
    val signature: String? = null,
    val attachmentsJson: String? = null,
    val isCompacted: Boolean = false,
    val isContextSummary: Boolean = false,
    val isCompactionMarker: Boolean = false,
    /** Anthropic thinking / redacted_thinking 内容块的原样快照（JSON 数组文本）。 */
    val thinkingBlocksJson: String? = null,
    val isContextExcluded: Boolean = false,
    val compactedBySummaryId: String? = null
)

@Serializable
data class TodoItemDto(
    val id: String,
    val sessionId: String,
    val subject: String,
    val description: String = "",
    val status: String = "PENDING",
    val priority: Int = 0,
    val order: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/** 备份中的一条记忆（全局或项目级）。 */
@Serializable
data class MemoryDto(
    val name: String,
    val description: String = "",
    val content: String,
    /** "GLOBAL" 或 "PROJECT"。 */
    val scope: String,
    /** 项目级记忆归属的工作区路径；全局记忆为空。 */
    val projectRoot: String = ""
)

/** 备份中的一条收藏消息。 */
@Serializable
data class FavoriteItemDto(
    val id: String,
    val content: String,
    val sessionTitle: String = "",
    val createdAt: Long
)
