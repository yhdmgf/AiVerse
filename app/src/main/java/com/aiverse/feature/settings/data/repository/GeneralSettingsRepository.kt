package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.generalDataStore by preferencesDataStore(
    name = "general_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/** App 启动（含切换工作区）时进入哪个会话。 */
enum class StartupSessionMode {
    /** 新开会话：复用当前工作区里还没发过消息的空会话，否则新建。 */
    NEW_SESSION,

    /** 打开最近会话：直接进入最近更新的会话，该工作区还没有会话时才新建。 */
    RECENT_SESSION
}

/**
 * 「偏好设置」里的用户偏好。
 *
 * 目前九项：拉取模型成功后是否自动移除远端已不存在的本地模型（默认开启）、
 * 启动时进入新会话还是最近会话（默认打开最近会话）、首字 / 数据块间隔超时（秒）、
 * 网络请求的最大重试次数（默认 6）、子代理并发上限（默认 6）、回车发送、自动压缩阈值、
 * sendFile 单个文件大小上限（默认 100MB），
 * 以及移除外部本地工作区时是否一并删除其聊天记录（默认关闭）。
 * DataStore 用法与 [KeepaliveSettingsRepository] 一致。
 */
@Singleton
class GeneralSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    internal companion object {
        val AUTO_REMOVE_STALE_MODELS_KEY = booleanPreferencesKey("auto_remove_stale_models")
        val STARTUP_SESSION_MODE_KEY = stringPreferencesKey("startup_session_mode")
        val FIRST_BYTE_TIMEOUT_SEC_KEY = intPreferencesKey("first_byte_timeout_sec")
        val STREAM_IDLE_TIMEOUT_SEC_KEY = intPreferencesKey("stream_idle_timeout_sec")
        val MAX_NETWORK_RETRIES_KEY = intPreferencesKey("max_network_retries")
        val MAX_SUBAGENT_RUNNING_KEY = intPreferencesKey("max_subagent_running")
        val ENTER_TO_SEND_KEY = booleanPreferencesKey("enter_to_send")
        val AUTO_EXPAND_REASONING_KEY = booleanPreferencesKey("auto_expand_reasoning")
        val COMPACTION_THRESHOLD_PERCENT_KEY = intPreferencesKey("compaction_threshold_percent")
        val SENDFILE_MAX_SIZE_MB_KEY = intPreferencesKey("sendfile_max_size_mb")
        val GOAL_MAX_TURNS_KEY = intPreferencesKey("goal_max_turns")
        val GOAL_MAX_TOKENS_KEY = intPreferencesKey("goal_max_tokens")
        val DELETE_EXTERNAL_WORKSPACE_SESSIONS_KEY = booleanPreferencesKey("delete_external_workspace_sessions")
        /** 「MCP 工具按需加载（实验）」：开启后 MCP 工具不逐个注册，只提供 mcp_search / mcp_call 两个入口。 */
        val MCP_LAZY_TOOLS_KEY = booleanPreferencesKey("mcp_lazy_tools")
        /** 「提示词」页使用说明公告已展示内容的哈希；无值或与当前内容哈希不一致时重新弹出。 */
        val PROMPTS_ANNOUNCEMENT_SHOWN_HASH_KEY = stringPreferencesKey("prompts_announcement_shown_hash")
        /** 应用启动次数：主进程每次冷启动自增一，供 token 统计页概览展示。 */
        val LAUNCH_COUNT_KEY = intPreferencesKey("launch_count")

        /** 累计启动天数：只在本地日期变化后的首次启动自增，同一天多次打开不重复计数。 */
        val LAUNCH_DAY_COUNT_KEY = intPreferencesKey("launch_day_count")

        /** 上一次计入启动天数的本地日期（ISO yyyy-MM-dd），用于去重。 */
        val LAST_LAUNCH_DAY_KEY = stringPreferencesKey("last_launch_day")

        /** 首字超时默认 5 分钟，与原硬编码值一致。 */
        const val DEFAULT_FIRST_BYTE_TIMEOUT_SEC = 300

        /** 数据块间隔超时默认 5 分钟：服务端静默时能自行断开并重试，避免本轮永久停在「执行中」。 */
        const val DEFAULT_STREAM_IDLE_TIMEOUT_SEC = 300

        /** 网络重试次数默认 6，与原硬编码值一致。 */
        const val DEFAULT_MAX_NETWORK_RETRIES = 6

        /** 子代理并发上限默认 6，取代原先写死的 5。 */
        const val DEFAULT_MAX_SUBAGENT_RUNNING = 6

        /** 自动压缩阈值默认 85%。 */
        const val DEFAULT_COMPACTION_THRESHOLD_PERCENT = 85

        /** sendFile 单个文件大小上限默认 100MB，与原硬编码值一致。 */
        const val DEFAULT_SENDFILE_MAX_SIZE_MB = 100

        /** goal 自主循环最大续跑轮数默认 50。 */
        const val DEFAULT_GOAL_MAX_TURNS = 50

        /** goal 自主循环 token 预算默认 500k（按输入+输出累计）。 */
        const val DEFAULT_GOAL_MAX_TOKENS = 500_000
    }

    /** 拉取模型后自动对齐本地列表的开关流；未设置时回退到 true（默认开启）。 */
    val autoRemoveStaleModelsFlow: Flow<Boolean> =
        context.generalDataStore.data.map { it[AUTO_REMOVE_STALE_MODELS_KEY] ?: true }

    suspend fun setAutoRemoveStaleModels(enabled: Boolean) {
        context.generalDataStore.edit { it[AUTO_REMOVE_STALE_MODELS_KEY] = enabled }
    }

    /** 启动时会话偏好流；未设置或值无法识别时回退到 [StartupSessionMode.RECENT_SESSION]。 */
    val startupSessionModeFlow: Flow<StartupSessionMode> = context.generalDataStore.data.map { prefs ->
        when (prefs[STARTUP_SESSION_MODE_KEY]) {
            StartupSessionMode.NEW_SESSION.name -> StartupSessionMode.NEW_SESSION
            else -> StartupSessionMode.RECENT_SESSION
        }
    }

    suspend fun setStartupSessionMode(mode: StartupSessionMode) {
        context.generalDataStore.edit { it[STARTUP_SESSION_MODE_KEY] = mode.name }
    }

    /** 切换到工作区前读一次偏好，避免在会话初始化路径上多开一条收集流。 */
    suspend fun startupSessionMode(): StartupSessionMode = startupSessionModeFlow.first()

    /** 备份快照：当前自动对齐开关。 */
    suspend fun autoRemoveStaleModelsSnapshot(): Boolean = autoRemoveStaleModelsFlow.first()

    /** 备份快照：当前启动时会话偏好名。 */
    suspend fun startupSessionModeSnapshot(): String = startupSessionModeFlow.first().name

    /** 从备份还原自动对齐开关。 */
    suspend fun restoreAutoRemoveStaleModels(enabled: Boolean) = setAutoRemoveStaleModels(enabled)

    /** 从备份还原启动时会话偏好；旧备份无此字段（null）或值无法识别时回退打开最近会话。 */
    suspend fun restoreStartupSessionMode(mode: String?) {
        setStartupSessionMode(
            StartupSessionMode.entries.firstOrNull { it.name == mode } ?: StartupSessionMode.RECENT_SESSION
        )
    }

    /** 首字超时（秒）；0 表示不限制，未设置时回退到 300 秒。 */
    val firstByteTimeoutSecFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[FIRST_BYTE_TIMEOUT_SEC_KEY] ?: DEFAULT_FIRST_BYTE_TIMEOUT_SEC).coerceAtLeast(0)
    }

    /** 流式响应相邻数据块间隔超时（秒）；0 表示不限制，未设置时回退到 300 秒。 */
    val streamIdleTimeoutSecFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[STREAM_IDLE_TIMEOUT_SEC_KEY] ?: DEFAULT_STREAM_IDLE_TIMEOUT_SEC).coerceAtLeast(0)
    }

    suspend fun setFirstByteTimeoutSec(sec: Int) {
        context.generalDataStore.edit { it[FIRST_BYTE_TIMEOUT_SEC_KEY] = sec.coerceAtLeast(0) }
    }

    suspend fun setStreamIdleTimeoutSec(sec: Int) {
        context.generalDataStore.edit { it[STREAM_IDLE_TIMEOUT_SEC_KEY] = sec.coerceAtLeast(0) }
    }

    /** 装配 provider 前读取一次首字超时（毫秒）；0 表示不限制。 */
    suspend fun firstByteTimeoutMs(): Long = firstByteTimeoutSecFlow.first() * 1000L

    /** 装配 provider 前读取一次数据块间隔超时（毫秒）；0 表示不限制。 */
    suspend fun streamIdleTimeoutMs(): Long = streamIdleTimeoutSecFlow.first() * 1000L

    /** 备份快照：两个超时设置（秒）。 */
    suspend fun firstByteTimeoutSecSnapshot(): Int = firstByteTimeoutSecFlow.first()

    suspend fun streamIdleTimeoutSecSnapshot(): Int = streamIdleTimeoutSecFlow.first()

    suspend fun restoreFirstByteTimeoutSec(sec: Int) = setFirstByteTimeoutSec(sec)

    suspend fun restoreStreamIdleTimeoutSec(sec: Int) = setStreamIdleTimeoutSec(sec)

    /** 网络请求最大重试次数（不含首次请求）；0 表示不重试，未设置时回退到 6。 */
    val maxNetworkRetriesFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[MAX_NETWORK_RETRIES_KEY] ?: DEFAULT_MAX_NETWORK_RETRIES).coerceAtLeast(0)
    }

    suspend fun setMaxNetworkRetries(count: Int) {
        context.generalDataStore.edit { it[MAX_NETWORK_RETRIES_KEY] = count.coerceAtLeast(0) }
    }

    /** 装配 provider 前读取一次最大重试次数。 */
    suspend fun maxNetworkRetries(): Int = maxNetworkRetriesFlow.first()

    /** 备份快照：最大重试次数。 */
    suspend fun maxNetworkRetriesSnapshot(): Int = maxNetworkRetriesFlow.first()

    suspend fun restoreMaxNetworkRetries(count: Int) = setMaxNetworkRetries(count)

    /** 同时运行的子代理上限；未设置时回退到 6，下限 1（设成 0 会让子代理彻底不可用）。 */
    val maxSubagentRunningFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[MAX_SUBAGENT_RUNNING_KEY] ?: DEFAULT_MAX_SUBAGENT_RUNNING).coerceAtLeast(1)
    }

    suspend fun setMaxSubagentRunning(count: Int) {
        context.generalDataStore.edit { it[MAX_SUBAGENT_RUNNING_KEY] = count.coerceAtLeast(1) }
    }

    /** 冷读一次子代理并发上限。 */
    suspend fun maxSubagentRunning(): Int = maxSubagentRunningFlow.first()

    /** 备份快照：子代理并发上限。 */
    suspend fun maxSubagentRunningSnapshot(): Int = maxSubagentRunningFlow.first()

    suspend fun restoreSubAgentMaxRunning(count: Int) = setMaxSubagentRunning(count)

    /** 回车键是否直接发送消息；默认关闭（回车换行，由发送按钮发送）。 */
    val enterToSendFlow: Flow<Boolean> =
        context.generalDataStore.data.map { it[ENTER_TO_SEND_KEY] ?: false }

    suspend fun setEnterToSend(enabled: Boolean) {
        context.generalDataStore.edit { it[ENTER_TO_SEND_KEY] = enabled }
    }

    /** 思考卡片是否默认展开正文；默认收起。 */
    val autoExpandReasoningFlow: Flow<Boolean> =
        context.generalDataStore.data.map { it[AUTO_EXPAND_REASONING_KEY] ?: false }

    suspend fun setAutoExpandReasoning(enabled: Boolean) {
        context.generalDataStore.edit { it[AUTO_EXPAND_REASONING_KEY] = enabled }
    }

    /** 自动压缩触发阈值（上下文窗口的百分比）；默认 90，限定 1..100。 */
    val compactionThresholdPercentFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[COMPACTION_THRESHOLD_PERCENT_KEY] ?: DEFAULT_COMPACTION_THRESHOLD_PERCENT).coerceIn(1, 100)
    }

    suspend fun setCompactionThresholdPercent(percent: Int) {
        context.generalDataStore.edit { it[COMPACTION_THRESHOLD_PERCENT_KEY] = percent.coerceIn(1, 100) }
    }

    /** 压缩前读取一次触发阈值百分比。 */
    suspend fun compactionThresholdPercent(): Int = compactionThresholdPercentFlow.first()

    /** 备份快照：回车发送开关与压缩阈值。 */
    suspend fun enterToSendSnapshot(): Boolean = enterToSendFlow.first()

    suspend fun compactionThresholdPercentSnapshot(): Int = compactionThresholdPercentFlow.first()

    suspend fun restoreEnterToSend(enabled: Boolean) = setEnterToSend(enabled)

    suspend fun restoreCompactionThresholdPercent(percent: Int) = setCompactionThresholdPercent(percent)

    /** sendFile 单个文件大小上限（MB）；默认 100，下限 1，不设上限。 */
    val sendFileMaxSizeMbFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[SENDFILE_MAX_SIZE_MB_KEY] ?: DEFAULT_SENDFILE_MAX_SIZE_MB).coerceAtLeast(1)
    }

    suspend fun setSendFileMaxSizeMb(mb: Int) {
        context.generalDataStore.edit { it[SENDFILE_MAX_SIZE_MB_KEY] = mb.coerceAtLeast(1) }
    }

    /** goal 自主循环最大续跑轮数；默认 50，下限 1。 */
    val goalMaxTurnsFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[GOAL_MAX_TURNS_KEY] ?: DEFAULT_GOAL_MAX_TURNS).coerceAtLeast(1)
    }

    suspend fun setGoalMaxTurns(turns: Int) {
        context.generalDataStore.edit { it[GOAL_MAX_TURNS_KEY] = turns.coerceAtLeast(1) }
    }

    /** 启动 goal 循环前读取一次轮次上限。 */
    suspend fun goalMaxTurns(): Int = goalMaxTurnsFlow.first()

    /** goal 自主循环 token 预算（输入+输出累计）；默认 500000，下限 1000。 */
    val goalMaxTokensFlow: Flow<Int> = context.generalDataStore.data.map {
        (it[GOAL_MAX_TOKENS_KEY] ?: DEFAULT_GOAL_MAX_TOKENS).coerceAtLeast(1000)
    }

    suspend fun setGoalMaxTokens(tokens: Int) {
        context.generalDataStore.edit { it[GOAL_MAX_TOKENS_KEY] = tokens.coerceAtLeast(1000) }
    }

    /** 启动 goal 循环前读取一次 token 预算。 */
    suspend fun goalMaxTokens(): Int = goalMaxTokensFlow.first()

    /** 执行 sendFile 前读取一次单个文件大小上限（MB）。 */
    suspend fun sendFileMaxSizeMb(): Int = sendFileMaxSizeMbFlow.first()

    /** 备份快照：sendFile 单个文件大小上限。 */
    suspend fun sendFileMaxSizeMbSnapshot(): Int = sendFileMaxSizeMbFlow.first()

    suspend fun restoreSendFileMaxSizeMb(mb: Int) = setSendFileMaxSizeMb(mb)

    /** 移除外部本地工作区时是否一并删除其聊天记录；默认关闭（仅解除关联、保留聊天记录）。 */
    val deleteExternalWorkspaceSessionsFlow: Flow<Boolean> =
        context.generalDataStore.data.map { it[DELETE_EXTERNAL_WORKSPACE_SESSIONS_KEY] ?: false }

    suspend fun setDeleteExternalWorkspaceSessions(enabled: Boolean) {
        context.generalDataStore.edit { it[DELETE_EXTERNAL_WORKSPACE_SESSIONS_KEY] = enabled }
    }

    /** 移除外部工作区前读取一次该偏好。 */
    suspend fun deleteExternalWorkspaceSessions(): Boolean = deleteExternalWorkspaceSessionsFlow.first()

    /** 「MCP 工具按需加载（实验）」；默认关闭（关闭时行为与逐个注册完全一致）。 */
    val mcpLazyToolsFlow: Flow<Boolean> =
        context.generalDataStore.data.map { it[MCP_LAZY_TOOLS_KEY] ?: false }

    suspend fun setMcpLazyTools(enabled: Boolean) {
        context.generalDataStore.edit { it[MCP_LAZY_TOOLS_KEY] = enabled }
    }

    /** 备份快照：MCP 工具按需加载开关。 */
    suspend fun mcpLazyToolsSnapshot(): Boolean = mcpLazyToolsFlow.first()

    suspend fun restoreMcpLazyTools(enabled: Boolean) = setMcpLazyTools(enabled)

    /** 「提示词」页公告已展示内容的哈希；无值表示从未展示过。 */
    val promptsAnnouncementShownHashFlow: Flow<String?> = context.generalDataStore.data.map { prefs ->
        prefs[PROMPTS_ANNOUNCEMENT_SHOWN_HASH_KEY]
    }

    suspend fun markPromptsAnnouncementShown(hash: String) {
        context.generalDataStore.edit { it[PROMPTS_ANNOUNCEMENT_SHOWN_HASH_KEY] = hash }
    }

    /** 备份快照：外部工作区聊天记录处理偏好。 */
    suspend fun deleteExternalWorkspaceSessionsSnapshot(): Boolean = deleteExternalWorkspaceSessionsFlow.first()

    suspend fun restoreDeleteExternalWorkspaceSessions(enabled: Boolean) = setDeleteExternalWorkspaceSessions(enabled)

    /** 启动次数流；未设置时回退到 0。 */
    val launchCountFlow: Flow<Int> = context.generalDataStore.data.map { it[LAUNCH_COUNT_KEY] ?: 0 }

    /** 启动次数与启动天数：天数按本地自然日去重，同一天多次打开只算一天。 */
    val launchStatsFlow: Flow<LaunchStats> = context.generalDataStore.data.map {
        LaunchStats(
            count = it[LAUNCH_COUNT_KEY] ?: 0,
            days = it[LAUNCH_DAY_COUNT_KEY] ?: 0
        )
    }

    /**
     * 记录一次启动：次数每次自增；天数仅在本地日期变化后自增。
     * 读取旧值、比较日期与写回都在同一个 DataStore 事务内完成，避免并发重复计数。
     */
    suspend fun recordLaunch() {
        val today = java.time.LocalDate.now().toString()
        context.generalDataStore.edit {
            it[LAUNCH_COUNT_KEY] = (it[LAUNCH_COUNT_KEY] ?: 0) + 1
            if (it[LAST_LAUNCH_DAY_KEY] != today) {
                it[LAUNCH_DAY_COUNT_KEY] = (it[LAUNCH_DAY_COUNT_KEY] ?: 0) + 1
                it[LAST_LAUNCH_DAY_KEY] = today
            }
        }
    }

    /** 启动次数自增一：保留给旧调用点，内部走 [recordLaunch] 同步维护启动天数。 */
    suspend fun incrementLaunchCount() = recordLaunch()
}

/** 启动概览：总打开次数与去重后的自然日天数。 */
data class LaunchStats(
    val count: Int = 0,
    val days: Int = 0
)