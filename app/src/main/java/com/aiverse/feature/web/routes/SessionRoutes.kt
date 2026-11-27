package com.aiverse.feature.web.routes

import com.aiverse.feature.agent.data.local.dao.AgentMessageDao
import com.aiverse.feature.agent.data.local.dao.ChatSessionDao
import com.aiverse.feature.web.ApiResponse
import com.aiverse.feature.web.MessageDto
import com.aiverse.feature.web.SendMessageRequest
import com.aiverse.feature.web.SessionDto
import com.aiverse.feature.web.WebMessageBridge
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** 会话与消息的 REST 路由（只读 + Web 发消息转发）。 */
fun Route.sessionRoutes(
    sessionDao: ChatSessionDao,
    messageDao: AgentMessageDao,
    messageBridge: WebMessageBridge,
    workspaceRepository: WorkspaceRepository
) {
    route("/api") {
        get("/sessions") {
            val workspace = call.request.queryParameters["workspace"]
            val sessions = if (workspace.isNullOrBlank()) {
                sessionDao.getAllOnce()
            } else {
                sessionDao.getAllSessionsByWorkspaceOnce(workspace)
            }
            call.respond(sessions.map(SessionDto::from))
        }
        get("/sessions/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@get
            }
            val session = sessionDao.getById(id)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@get
            }
            call.respond(SessionDto.from(session))
        }
        // 打开会话时把全局工作区切到会话所属目录；网页端的 @ 文件、文件工具、Bash 才能对得上。
        post("/sessions/{id}/activate") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@post
            }
            val session = sessionDao.getById(id)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@post
            }
            if (!activateWorkspaceForSession(workspaceRepository, session.workspacePath)) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse(ok = false, error = "session workspace not found or unavailable")
                )
                return@post
            }
            call.respond(SessionDto.from(session))
        }
        get("/sessions/{id}/messages") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@get
            }
            val messages = messageDao.getMessagesBySessionOnce(id)
            call.respond(messages.map(MessageDto::from))
        }
        // Web 发消息：优先转给 UI 的 Agent 链路；界面不在时回退到进程级运行器，网页端可独立驱动。
        post("/sessions/{id}/messages") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@post
            }
            val session = sessionDao.getById(id)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@post
            }
            val req = runCatching { call.receive<SendMessageRequest>() }.getOrNull()
            if (req == null || req.content.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "empty content"))
                return@post
            }
            // 发消息前再次对齐工作区：网页端可能直接打开一个别的项目的会话 URL。
            if (!activateWorkspaceForSession(workspaceRepository, session.workspacePath)) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse(ok = false, error = "session workspace not found or unavailable")
                )
                return@post
            }
            val accepted = messageBridge.submit(id, req.content.trim())
            if (!accepted) {
                call.respond(HttpStatusCode.ServiceUnavailable, ApiResponse(ok = false, error = "无法投递消息，请稍后重试。"))
                return@post
            }
            call.respond(ApiResponse(ok = true))
        }
    }
}

/**
 * 将会话绑定目录设为 App 当前工作区。路径一致时直接返回，避免无谓切换。
 * 工作区列表由 App 启动时初始化，网页端只读，不在这里重新扫盘（远程断线时扫盘会把当前项清空）。
 */
private suspend fun activateWorkspaceForSession(
    workspaceRepository: WorkspaceRepository,
    workspacePath: String
): Boolean {
    val path = workspacePath.trim()
    if (path.isBlank()) return true
    if (workspaceRepository.currentPath() == path) return true
    val target = workspaceRepository.workspaces.value
        .firstOrNull { it.path == path && it.available }
        ?: return false
    workspaceRepository.selectWorkspace(target.name)
    return true
}
