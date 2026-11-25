package com.aiverse.feature.agent.domain.session

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.model.Workspace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 集中解析「角色绑定的 workspace → 绝对路径」。
 *
 * 角色的 [com.aiverse.feature.agent.domain.model.Role.workspaceId] 存的是 [Workspace.name]（唯一标识），
 * 新建会话时需把它解析成工作区的绝对路径；未绑定、绑定的名字已失效（工作区被删/改名）或当前不可用时，
 * 一律回退到调用方传入的 fallback 路径（通常是"当前工作区"）。
 *
 * 所有会经过角色的新建会话入口都应复用本类，保证 App 内、Web API 等多端行为一致：
 * - 冷启动 / 新建 / 删除兜底 / ensureSession：见 AIAgentViewModel.createSession
 * - Web API 新建会话：见 SessionManageRoutes
 *
 * 抽成独立类而非散落在各调用点，一是避免逻辑重复，二是让绑定解析（含时序与悬挂引用处理）有单一事实来源、便于单测。
 */
@Singleton
class WorkspacePathResolver @Inject constructor(
    private val roleDao: RoleDao,
    private val workspaceRepository: WorkspaceRepository
) {

    /**
     * 挂起版本：按 roleId 解析绑定工作区路径，解析不了返回 [fallbackPath]。
     *
     * 与纯函数 [resolve] 的区别在于它会处理一个时序边界：工作区列表 [WorkspaceRepository.workspaces]
     * 是初始为空的 StateFlow，只有 refresh 后才有值。若此刻列表为空（例如冷启动早期、应用刚起来就建会话），
     * 直接同步读 .value 会误判为"绑定的工作区不存在"从而错误回退；这里先补一次可用性刷新再解析。
     */
    suspend fun resolveForRole(roleId: String?, fallbackPath: String): String {
        if (roleId.isNullOrBlank()) return fallbackPath
        val boundName = roleDao.getById(roleId)?.workspaceId?.takeIf { it.isNotBlank() } ?: return fallbackPath

        var workspaces = workspaceRepository.workspaces.value
        if (workspaces.isEmpty()) {
            // 列表尚未加载：补一次刷新，避免把"未加载"当成"绑定失效"。
            runCatching { workspaceRepository.refreshAvailability() }
            workspaces = workspaceRepository.workspaces.value
        }
        return resolve(boundName, workspaces, fallbackPath)
    }

    /** 纯解析：在 [workspaces] 里找 name==[boundName] 且当前可用的项，命中返回其路径，否则回退。 */
    private fun resolve(boundName: String, workspaces: List<Workspace>, fallbackPath: String): String {
        val hit = workspaces.find { it.name == boundName }
        return when {
            hit == null -> {
                // 悬挂引用：角色指向的名字在当前工作区列表里已不存在（被删除或重命名过）。
                FileLogger.w(TAG, "角色绑定的工作区「$boundName」不存在（可能已删除或改名），回退当前工作区")
                fallbackPath
            }
            !hit.available -> {
                // 名字还在，但目录被移动/删除导致当前不可用。
                FileLogger.w(TAG, "角色绑定的工作区「$boundName」当前不可用，回退当前工作区")
                fallbackPath
            }
            else -> hit.path
        }
    }

    companion object {
        private const val TAG = "WorkspacePathResolver"
    }
}
