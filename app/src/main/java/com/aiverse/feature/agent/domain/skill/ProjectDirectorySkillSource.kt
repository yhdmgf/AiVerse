package com.aiverse.feature.agent.domain.skill

import com.aiverse.feature.workspace.domain.AgentConfigKind
import com.aiverse.feature.workspace.domain.AgentConfigParseCache
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 项目级技能来源：`<projectRoot>/.aiverse/skills/`，随工作区走，可 git 追踪。
 * 以容器路径经 [FileAccessProvider] 访问：本地映射到宿主工作区，远程经 SSH 落到远程工作区。
 *
 * 远程加速：有远程配置快照时一次批量读取全部 SKILL.md/CLAUDE.md，并按快照指纹缓存解析结果；
 * 无快照（本地模式 / 远程后端未就绪）时回退到逐文件扫描。
 */
@Singleton
class ProjectDirectorySkillSource @Inject constructor(
    private val fileAccess: FileAccessProvider
) : SkillSource {

    val skillsRoot: String = "${WorkspacePathMapper.CONTAINER_ROOT}/.aiverse/skills"

    /** 解析缓存：按快照身份+修订号复用已解析的技能列表，避免每次重读重解析。 */
    private val snapshotCache = AgentConfigParseCache<List<Skill>>()

    override fun listSkills(force: Boolean): List<Skill> {
        val snapshot = runCatching { fileAccess.agentConfigSnapshot(skillsRoot, AgentConfigKind.SKILLS, forceRefresh = force) }
            .getOrNull() ?: return SkillDirectoryScanner.scan(fileAccess, skillsRoot)
        return snapshotCache.get(snapshot) { files -> SkillDirectoryScanner.scan(skillsRoot, files) }
    }

    override fun loadInstructions(name: String): String? =
        listSkills().firstOrNull { it.name.equals(name, ignoreCase = true) }?.instructions
}
