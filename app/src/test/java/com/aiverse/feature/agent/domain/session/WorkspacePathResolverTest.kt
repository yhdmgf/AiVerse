package com.aiverse.feature.agent.domain.session

import com.aiverse.feature.agent.data.local.dao.RoleDao
import com.aiverse.feature.agent.data.local.entity.RoleEntity
import com.aiverse.feature.workspace.data.repository.WorkspaceRepository
import com.aiverse.feature.workspace.domain.model.Workspace
import com.aiverse.feature.workspace.domain.model.WorkspaceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 角色绑定工作区 → 会话路径 的解析逻辑（issue #240）。
 *
 * 覆盖四条主分支：绑定命中 / 未绑定回退 / 绑定已失效（悬挂引用）回退 / 绑定不可用回退，
 * 以及工作区列表尚未加载时的补刷新时序处理。
 */
class WorkspacePathResolverTest {

    private val roleDao = mockk<RoleDao>()
    private val workspaceRepository = mockk<WorkspaceRepository>()

    private val resolver = WorkspacePathResolver(roleDao, workspaceRepository)

    private fun workspace(
        name: String,
        path: String = "/ws/$name",
        available: Boolean = true
    ) = Workspace(
        name = name,
        path = path,
        type = WorkspaceType.INTERNAL,
        available = available
    )

    private fun role(id: String, workspaceId: String?) = RoleEntity(
        id = id,
        name = "r-$id",
        description = "",
        identityPrompt = "",
        tags = "",
        avatarKind = "NONE",
        avatarValue = "",
        enabled = true,
        isBuiltin = false,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L,
        workspaceId = workspaceId
    )

    private fun stubWorkspaces(list: List<Workspace>) {
        every { workspaceRepository.workspaces } returns MutableStateFlow(list)
    }

    @Test
    fun `绑定的工作区可用时 返回其路径`() = runTest {
        stubWorkspaces(listOf(workspace("alpha"), workspace("beta")))
        coEvery { roleDao.getById("r1") } returns role("r1", "beta")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/ws/beta", resolved)
    }

    @Test
    fun `未绑定角色 回退传入路径`() = runTest {
        stubWorkspaces(listOf(workspace("alpha")))
        coEvery { roleDao.getById("r1") } returns role("r1", workspaceId = null)

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/fallback", resolved)
    }

    @Test
    fun `roleId为空 回退传入路径 且不查角色`() = runTest {
        val resolved = resolver.resolveForRole(null, "/fallback")

        assertEquals("/fallback", resolved)
        coVerify(exactly = 0) { roleDao.getById(any()) }
    }

    @Test
    fun `绑定的工作区名已不存在 悬挂引用 回退传入路径`() = runTest {
        // 列表里没有 "gone"：角色指向的名字已被删除或改名。
        stubWorkspaces(listOf(workspace("alpha")))
        coEvery { roleDao.getById("r1") } returns role("r1", "gone")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/fallback", resolved)
    }

    @Test
    fun `绑定的工作区当前不可用 回退传入路径`() = runTest {
        stubWorkspaces(listOf(workspace("alpha"), workspace("offline", available = false)))
        coEvery { roleDao.getById("r1") } returns role("r1", "offline")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/fallback", resolved)
    }

    @Test
    fun `工作区列表尚未加载时 先补刷新再解析`() = runTest {
        // 初始为空（冷启动早期未 refresh），刷新后列表填充。
        val flow = MutableStateFlow<List<Workspace>>(emptyList())
        every { workspaceRepository.workspaces } returns flow
        coEvery { workspaceRepository.refreshAvailability() } coAnswers {
            flow.value = listOf(workspace("alpha"))
        }
        coEvery { roleDao.getById("r1") } returns role("r1", "alpha")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/ws/alpha", resolved)
        coVerify(exactly = 1) { workspaceRepository.refreshAvailability() }
    }

    @Test
    fun `列表非空时不触发额外刷新`() = runTest {
        stubWorkspaces(listOf(workspace("alpha")))
        coEvery { roleDao.getById("r1") } returns role("r1", "alpha")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/ws/alpha", resolved)
        coVerify(exactly = 0) { workspaceRepository.refreshAvailability() }
    }

    @Test
    fun `绑定为空白字符串 视为未绑定 回退`() = runTest {
        stubWorkspaces(listOf(workspace("alpha")))
        coEvery { roleDao.getById("r1") } returns role("r1", "   ")

        val resolved = resolver.resolveForRole("r1", "/fallback")

        assertEquals("/fallback", resolved)
    }
}
