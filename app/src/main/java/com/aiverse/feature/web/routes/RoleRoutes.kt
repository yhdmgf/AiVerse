package com.aiverse.feature.web.routes

import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.web.RoleDto
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/** 角色路由：返回启用中的角色列表（供网页端绑定会话时选择）。 */
fun Route.roleRoutes(roleDao: RoleDao) {
    route("/api") {
        get("/roles") {
            val roles = roleDao.getEnabledOnce()
            call.respond(roles.map { r ->
                RoleDto(
                    id = r.id,
                    name = r.name,
                    description = r.description,
                    avatarKind = r.avatarKind,
                    avatarValue = r.avatarValue,
                    workspaceId = r.workspaceId
                )
            })
        }
    }
}