package com.aiverse.feature.plugins.runtime

import com.aiverse.feature.agent.data.remote.anthropic.AnthropicApi
import com.aiverse.feature.agent.data.remote.gemini.GeminiApi
import com.aiverse.feature.agent.data.remote.openai.OpenAIApi
import com.aiverse.feature.agent.domain.model.AgentMessage
import com.aiverse.feature.agent.domain.provider.AIProvider
import com.aiverse.feature.agent.domain.provider.AIStreamChunk
import com.aiverse.feature.agent.domain.provider.AllKeysFailedException
import com.aiverse.feature.agent.domain.provider.AnthropicAdapter
import com.aiverse.feature.agent.domain.provider.GeminiAdapter
import com.aiverse.feature.agent.domain.provider.KeySwitchOutcome
import com.aiverse.feature.agent.domain.provider.OpenAIAdapter
import com.aiverse.feature.agent.domain.provider.isKeySwitchFailure
import com.aiverse.feature.agent.domain.provider.toReasoningCapability
import com.aiverse.feature.agent.domain.provider.fixedTemperature
import com.aiverse.feature.agent.presentation.AgentUIMessage
import com.aiverse.feature.agent.presentation.MessageRole
import com.aiverse.feature.agent.presentation.RetryState
import com.aiverse.feature.agent.presentation.RunningToolOutput
import com.aiverse.feature.agent.presentation.StreamingAssistantMessage
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.session.PluginSessionSummary
import com.aiverse.feature.settings.data.remote.ModelMetadataService
import com.aiverse.feature.settings.domain.repository.AIProviderRepository
import com.aiverse.feature.settings.data.repository.GeneralSettingsRepository
import com.aiverse.feature.settings.data.repository.ProviderKeyRotator
import com.aiverse.feature.settings.domain.model.ProviderType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PluginRemoteRunState(
    val running: Boolean = false,
    val assistant: StreamingAssistantMessage? = null,
    val settled: List<AgentUIMessage> = emptyList(),
    val tools: Map<String, RunningToolOutput> = emptyMap(),
    val retry: RetryState? = null,
    val error: String? = null,
    val renderedIds: Set<String> = emptySet()
)

/**
 * 驱动远程执行（executionMode = "remote"）的插件会话：把用户消息交给插件提供商（OpenAI 兼容流），
 * 实时把正文/思考推给聊天页，远端继续负责模型与工具编排；宿主只订阅与展示，不再本地续轮或重放工具。
 *
 * 适配说明：AiCode 用共享的 `ProviderInstanceFactory` 装配 provider；AiVerse 没有这类工厂
 * （各调用点内联装配，见 `StatefulAgentWorkflow.createStandaloneProvider`），此处复用同一批协作者
 * 内联装配，不新建宿主基础设施。
 */
@Singleton
class PluginRemoteRunManager @Inject constructor(
    private val providerRepository: AIProviderRepository,
    private val pluginManager: PluginManager,
    private val openAIApi: OpenAIApi,
    private val anthropicApi: AnthropicApi,
    private val geminiApi: GeminiApi,
    private val keyRotator: ProviderKeyRotator,
    private val modelMetadataService: ModelMetadataService,
    private val generalSettingsRepository: GeneralSettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Any()
    private val mutableStates = MutableStateFlow<Map<String, PluginRemoteRunState>>(emptyMap())
    internal val states = mutableStates.asStateFlow()

    init {
        scope.launch {
            pluginManager.plugins.collect { plugins ->
                val active = plugins.filter { it.isActive }.mapTo(HashSet()) { it.id }
                synchronized(lock) {
                    jobs.filterKeys { id -> active.none { id.startsWith("$it:") } }
                        .values.forEach { it.cancel() }
                }
            }
        }
    }

    fun start(session: PluginSessionSummary, prompt: String?, onFinished: suspend () -> Unit) {
        synchronized(lock) {
            if (jobs[session.id]?.isActive == true) return
            update(session.id) { it.copy(running = true, error = null, retry = null) }
            jobs[session.id] = scope.launch {
                try {
                    val localProviderId = requireNotNull(session.providerId) { "REMOTE_PROVIDER_REQUIRED" }
                    val providerId = PluginProviderIds.idOf(session.pluginId, localProviderId)
                    val config = providerRepository.getProviderById(providerId)
                        ?: error("REMOTE_PROVIDER_NOT_FOUND")
                    val model = session.model?.takeIf { it.isNotBlank() } ?: config.effectiveModel
                    val provider = buildProvider(config.copy(selectedModel = model), session.id)
                    provider.customHeaders = provider.customHeaders + mapOf(
                        // 网关兼容保留：外部会话完整 ID、幂等键与「仅订阅」标记。
                        "X-AiCode-Session-Id" to session.id,
                        "X-AiCode-Operation-Id" to UUID.randomUUID().toString(),
                        "X-AiCode-Subscribe-Only" to (prompt == null).toString()
                    )
                    provider.useResponseApi = false
                    val messages = prompt?.let { listOf(AgentMessage.UserMessage(content = it)) }.orEmpty()
                    provider.completeStream("", messages, emptyList()).collect { chunk ->
                        when (chunk) {
                            is AIStreamChunk.TextDelta -> {
                                if (chunk.messageId != null) settle(session.id, chunk.messageId)
                                if (chunk.text.isNotEmpty()) append(session.id, chunk.text, reasoning = false)
                            }
                            is AIStreamChunk.ReasoningDelta -> {
                                if (chunk.messageId != null) settle(session.id, chunk.messageId)
                                append(session.id, chunk.text, reasoning = true)
                            }
                            is AIStreamChunk.Retrying -> update(session.id) {
                                it.copy(assistant = null, retry = RetryState(chunk.attempt, chunk.maxRetries, chunk.error))
                            }
                            is AIStreamChunk.Final -> Unit
                            is AIStreamChunk.ToolCallDeclared -> error("REMOTE_TOOL_CALLS_NOT_SUPPORTED")
                            is AIStreamChunk.KeySwitched -> Unit
                        }
                    }
                    settle(session.id, null)
                    onFinished()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    update(session.id) { it.copy(error = e.message ?: e.javaClass.simpleName) }
                } finally {
                    update(session.id) { it.copy(running = false, retry = null, tools = emptyMap()) }
                }
            }
        }
    }

    fun stop(sessionId: String) {
        synchronized(lock) { jobs.remove(sessionId)?.cancel() }
    }

    internal fun updateTool(sessionId: String, callId: String, tool: RunningToolOutput?) {
        update(sessionId) { it.copy(tools = if (tool == null) it.tools - callId else it.tools + (callId to tool)) }
    }

    internal fun retainMessages(sessionId: String, renderedIds: Set<String>) {
        update(sessionId) { it.copy(
            settled = it.settled.filterNot { message -> message.id in renderedIds },
            renderedIds = renderedIds
        ) }
    }

    /**
     * 内联装配一个独立 provider 实例，与 `StatefulAgentWorkflow.createStandaloneProvider` 保持一致，
     * 不占用或修改主对话所用的 Provider 单例。
     */
    private suspend fun buildProvider(config: com.aiverse.feature.settings.domain.model.AIProviderConfig, sessionId: String): AIProvider {
        val provider: AIProvider = when (config.type) {
            ProviderType.ANTHROPIC -> AnthropicAdapter(anthropicApi).also {
                it.cacheBreakpointsEnabled = config.anthropicCacheBreakpoints
            }
            ProviderType.GEMINI -> GeminiAdapter(geminiApi)
            else -> OpenAIAdapter(openAIApi).also {
                it.chatCacheKeyEnabled = config.openaiChatCacheKey
            }
        }
        val activeKeys = config.effectiveApiKeys
        provider.apiKey = keyRotator.activeKey(config, sessionId)
            ?: throw IllegalStateException("provider has no usable key: ${config.id}")
        val keySwitcher: (suspend (Throwable, String, Set<String>) -> KeySwitchOutcome?)? =
            if (activeKeys.size > 1) {
                { error, failedKey, triedKeys ->
                    if (!error.isKeySwitchFailure(config.effectiveKeySwitchStatusCodes)) {
                        null
                    } else {
                        val switched = keyRotator.reportFailure(config.id, sessionId, failedKey, triedKeys)
                        if (switched == null) {
                            throw AllKeysFailedException(
                                "all ${activeKeys.size} keys of ${config.id} failed: " +
                                    (error.message ?: error.javaClass.simpleName),
                                error
                            )
                        }
                        KeySwitchOutcome(switched.newKey, switched.newIndex, switched.total)
                    }
                }
            } else {
                null
            }
        provider.keySwitcher = keySwitcher
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.useFullUrl = config.useFullUrl
        provider.useResponseApi = config.useResponseApi
        provider.providerId = config.id
        provider.logSessionId = sessionId
        provider.customHeaders = config.customHeaders
        val metadata = modelMetadataService.resolve(config.id, config.type, config.effectiveModel)
        provider.maxOutputTokens = metadata.outputTokens
        provider.reasoningCapability = metadata.toReasoningCapability()
        provider.temperature = if (metadata.supportsCustomTemperature) fixedTemperature(config.effectiveModel) else null
        provider.firstByteTimeoutMs = generalSettingsRepository.firstByteTimeoutMs()
        provider.streamIdleTimeoutMs = generalSettingsRepository.streamIdleTimeoutMs()
        provider.maxNetworkRetries = generalSettingsRepository.maxNetworkRetries()
        return provider
    }

    private fun append(sessionId: String, text: String, reasoning: Boolean) {
        if (text.isEmpty()) return
        update(sessionId) { state ->
            val current = state.assistant ?: StreamingAssistantMessage(
                sessionId = sessionId,
                messageId = "remote-stream-${UUID.randomUUID()}"
            )
            state.copy(
                retry = null,
                assistant = if (reasoning) current.copy(reasoning = current.reasoning + text)
                    else current.copy(content = current.content + text)
            )
        }
    }

    private fun settle(sessionId: String, messageId: String?) {
        update(sessionId) { state ->
            val current = state.assistant ?: return@update state
            val message = AgentUIMessage(
                id = messageId ?: current.messageId,
                role = MessageRole.ASSISTANT,
                content = current.content,
                reasoning = current.reasoning.takeIf { it.isNotBlank() }
            )
            state.copy(assistant = null, settled = if (message.id in state.renderedIds) state.settled
                else state.settled.filterNot { it.id == message.id } + message)
        }
    }

    private fun update(sessionId: String, transform: (PluginRemoteRunState) -> PluginRemoteRunState) {
        mutableStates.update { current -> current + (sessionId to transform(current[sessionId] ?: PluginRemoteRunState())) }
    }
}
