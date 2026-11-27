package com.aiverse.feature.web.routes

import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.domain.model.ReasoningEffort
import com.aiverse.feature.agent.domain.session.SessionUseCase
import com.aiverse.feature.web.ApiResponse
import com.aiverse.feature.web.CreateSessionRequest
import com.aiverse.feature.web.RenameSessionRequest
import com.aiverse.feature.web.SessionDto
import com.aiverse.feature.web.SetReasoningEffortRequest
import com.aiverse.feature.web.SetSessionModelRequest
import com.aiverse.feature.web.SetSessionRoleRequest
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * 会话管理路由：新建 / 删除 / 重命名 / 切换模型 / 思考强度 / 绑定角色
 * （与 App 同一数据库，App 端实时同步）。
 */
fun Route.sessionManageRoutes(
    sessionUseCase: SessionUseCase,
    workspaceRepository: WorkspaceRepository,
    roleDao: RoleDao
) {
    route("/api/sessions") {
        // 新建会话：workspaceName 绑定指定项目；也兼容旧的 workspacePath。
        post {
            val req = runCatching { call.receive<CreateSessionRequest>() }.getOrNull()
                ?: CreateSessionRequest()
            val requestedName = req.workspaceName?.trim()?.takeIf { it.isNotBlank() }
            val requestedPath = req.workspacePath?.trim()?.takeIf { it.isNotBlank() }
            val requestedWorkspace = when {
                requestedName != null -> workspaceRepository.workspaces.value
                    .firstOrNull { it.name == requestedName && it.available }
                requestedPath != null -> workspaceRepository.workspaces.value
                    .firstOrNull { it.path == requestedPath && it.available }
                else -> null
            }
            if (requestedName != null && requestedWorkspace == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "workspace not found or unavailable"))
                return@post
            }
            val workspace = requestedWorkspace?.path
                ?: requestedPath
                ?: workspaceRepository.currentPath()
            // 网页端切项目后，App 的全局工作区同步切换：文件工具、Bash、文件树都跟会话一致。
            if (requestedWorkspace != null) {
                workspaceRepository.selectWorkspace(requestedWorkspace.name)
            }
            val effort = req.reasoningEffort?.let { resolveReasoningEffort(it) }
            if (req.reasoningEffort != null && effort == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "invalid reasoningEffort"))
                return@post
            }
            val roleId = req.roleId?.trim()?.takeIf { it.isNotBlank() }
            if (roleId != null && roleDao.getById(roleId) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "role not found"))
                return@post
            }
            // 与 App 端保持一致（#309 弱绑定）：新建会话只跟请求指定的工作区，
            // 角色绑定工作区不再覆盖落点。
            val entity = sessionUseCase.newSessionEntity(
                workspacePath = workspace,
                providerId = req.providerId,
                model = req.model,
                reasoningEffort = effort ?: ReasoningEffort.MEDIUM.name,
                roleId = roleId
            )
            sessionUseCase.upsertSession(entity)
            call.respond(SessionDto.from(entity))
        }

        // 删除会话（含子会话 + 消息级联）。
        delete("/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@delete
            }
            sessionUseCase.deleteSession(id)
            call.respond(ApiResponse(ok = true))
        }

        // 重命名会话标题。
        put("/{id}/title") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@put
            }
            val req = runCatching { call.receive<RenameSessionRequest>() }.getOrNull()
            val title = req?.title?.trim().orEmpty()
            if (title.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "empty title"))
                return@put
            }
            if (sessionUseCase.getSessionById(id) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@put
            }
            sessionUseCase.updateTitle(id, title)
            call.respond(ApiResponse(ok = true))
        }

        // 切换会话模型（providerId + model，两者都可为 null 表示跟随默认）。
        put("/{id}/model") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@put
            }
            val req = runCatching { call.receive<SetSessionModelRequest>() }.getOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "invalid body"))
                    return@put
                }
            if (sessionUseCase.getSessionById(id) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@put
            }
            sessionUseCase.updateProviderModel(id, req.providerId, req.model)
            call.respond(ApiResponse(ok = true))
        }

        // 设置思考强度：接受枚举名（MEDIUM）或 api 值（medium），统一归一化到枚举名。
        put("/{id}/reasoning-effort") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@put
            }
            val req = runCatching { call.receive<SetReasoningEffortRequest>() }.getOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "invalid body"))
                    return@put
                }
            val effort = resolveReasoningEffort(req.effort)
            if (effort == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "invalid reasoningEffort"))
                return@put
            }
            if (sessionUseCase.getSessionById(id) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@put
            }
            sessionUseCase.updateReasoningEffort(id, effort)
            call.respond(ApiResponse(ok = true))
        }

        // 绑定会话角色；roleId 为 null/空白表示切回默认角色。
        put("/{id}/role") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "missing id"))
                return@put
            }
            val req = runCatching { call.receive<SetSessionRoleRequest>() }.getOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "invalid body"))
                    return@put
                }
            if (sessionUseCase.getSessionById(id) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "session not found"))
                return@put
            }
            val roleId = req.roleId?.takeIf { it.isNotBlank() }
            if (roleId != null && roleDao.getById(roleId) == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "role not found"))
                return@put
            }
            sessionUseCase.updateRoleId(id, roleId)
            call.respond(ApiResponse(ok = true))
        }
    }
}

/** 把前端传入的思考强度（枚举名或 api 值，忽略大小写）归一化为枚举名；无法识别返回 null。 */
private fun resolveReasoningEffort(input: String): String? {
    val trimmed = input.trim()
    return ReasoningEffort.entries.firstOrNull {
        it.name.equals(trimmed, ignoreCase = true) || it.apiValue.equals(trimmed, ignoreCase = true)
    }?.name
}