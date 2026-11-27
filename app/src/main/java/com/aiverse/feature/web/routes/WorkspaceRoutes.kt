package com.aiverse.feature.web.routes

import com.aiverse.feature.web.ApiResponse
import com.aiverse.feature.web.SelectWorkspaceRequest
import com.aiverse.feature.web.WorkspaceDto
import com.aiverse.feature.web.WorkspaceListDto
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * 网页端工作区路由。
 *
 * 网页端不是独立进程，所有工作区操作最终都落到 App 的 [WorkspaceRepository]：
 * 当前选中项、目录列表、远程/本地模式与手机端完全一致。这样网页端新建会话时
 * 可以真正绑定到某个项目目录，而不是把会话落进 App 上次选中的目录。
 */
fun Route.workspaceRoutes(workspaceRepository: WorkspaceRepository) {
    route("/api/workspaces") {
        get {
            val current = workspaceRepository.current.value
            call.respond(
                WorkspaceListDto(
                    current = current?.name,
                    workspaces = workspaceRepository.workspaces.value.map { ws ->
                        WorkspaceDto(
                            name = ws.name,
                            label = ws.displayLabel,
                            path = ws.path,
                            type = ws.type.name,
                            available = ws.available
                        )
                    }
                )
            )
        }

        post("/select") {
            val req = runCatching { call.receive<SelectWorkspaceRequest>() }.getOrNull()
            val name = req?.name?.trim().orEmpty()
            if (name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "empty workspace name"))
                return@post
            }
            val target = workspaceRepository.workspaces.value
                .firstOrNull { it.name == name && it.available }
            if (target == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse(ok = false, error = "workspace not found or unavailable"))
                return@post
            }
            workspaceRepository.selectWorkspace(target.name)
            call.respond(ApiResponse(ok = true))
        }
    }
}
