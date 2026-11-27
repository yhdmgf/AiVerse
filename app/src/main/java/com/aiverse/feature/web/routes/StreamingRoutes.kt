package com.aiverse.feature.web.routes

import com.aiverse.feature.agent.presentation.AgentUIState
import com.aiverse.feature.agent.presentation.StreamingAssistantMessage
import com.aiverse.feature.agent.presentation.run.AgentRunManager
import com.aiverse.feature.web.StreamingEventDto
import io.ktor.server.routing.Route
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private val streamingJson = Json { encodeDefaults = true }

/**
 * SSE 流式路由：订阅 [AgentRunManager.streamingAssistants] 与 [AgentRunManager.agentStates]，
 * 按 sessionId 过滤，把正文/推理变化以 `type=streaming` 推送；本轮结束推 `type=completed`；
 * 本轮失败推 `type=error`。
 *
 * 网页端只解析 `data:` 行里的 JSON，并按 `payload.type` 分支，所以类型必须写在 data 里，
 * 不能只依赖 SSE 的 `event:` 字段。心跳每 15 秒一次，避免移动网络/代理把空闲连接掐掉。
 */
fun Route.streamingRoutes(agentRunManager: AgentRunManager) {
    sse("/api/sessions/{id}/stream") {
        heartbeat {
            period = 15.seconds
        }

        val sessionId = call.parameters["id"]
        if (sessionId == null) {
            send(
                event = "error",
                data = streamingJson.encodeToString(
                    StreamingEventDto(type = "error", settled = true, message = "missing session id")
                )
            )
            return@sse
        }

        var completedSent = false
        var errorSent = false
        var sawRun = false

        fun streamingPayload(msg: StreamingAssistantMessage): String =
            streamingJson.encodeToString(
                StreamingEventDto(
                    type = "streaming",
                    content = msg.content,
                    reasoning = msg.reasoning,
                    settled = msg.settled
                )
            )

        fun completedPayload(): String =
            streamingJson.encodeToString(StreamingEventDto(type = "completed", settled = true))

        // 先推当前快照，让「迟连接」的客户端立即拿到状态；已结束则补 completed。
        val snapshot = agentRunManager.streamingAssistants.value[sessionId]
        val initialState = agentRunManager.agentStates.value[sessionId]
        if (initialState is AgentUIState.Loading || initialState is AgentUIState.Streaming) {
            sawRun = true
        }
        if (snapshot != null) {
            sawRun = true
            send(event = "streaming", data = streamingPayload(snapshot))
            if (snapshot.settled) {
                completedSent = true
                send(event = "completed", data = completedPayload())
            }
        }
        if (initialState is AgentUIState.Error) {
            errorSent = true
            send(
                event = "error",
                data = streamingJson.encodeToString(
                    StreamingEventDto(type = "error", settled = true, message = initialState.message)
                )
            )
        }

        // 同时监听流式正文与运行状态。正文为 null 时不一定结束（可能正在准备工具），
        // 必须等运行状态落到 Result/Idle，或收到 Error，才能给网页端一个明确收尾。
        combine(
            agentRunManager.streamingAssistants.map { it[sessionId] }.distinctUntilChanged(),
            agentRunManager.agentStates.map { it[sessionId] }.distinctUntilChanged()
        ) { msg, state -> msg to state }
            .distinctUntilChanged()
            .collect { (msg, state) ->
                if (state is AgentUIState.Loading || state is AgentUIState.Streaming) {
                    // 新一轮开始：旧一轮的快照/错误标记不能继续压住这一轮的收尾事件。
                    sawRun = true
                    completedSent = false
                    errorSent = false
                }
                if (state is AgentUIState.Error && !errorSent) {
                    errorSent = true
                    send(
                        event = "error",
                        data = streamingJson.encodeToString(
                            StreamingEventDto(type = "error", settled = true, message = state.message)
                        )
                    )
                    return@collect
                }

                if (msg != null) {
                    send(event = "streaming", data = streamingPayload(msg))
                    if (msg.settled && !completedSent) {
                        completedSent = true
                        send(event = "completed", data = completedPayload())
                    }
                }

                if (!completedSent && !errorSent && sawRun && msg == null &&
                    (state is AgentUIState.Result || state is AgentUIState.Idle)
                ) {
                    completedSent = true
                    send(event = "completed", data = completedPayload())
                }
            }
    }
}
