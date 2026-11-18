package com.aiverse.feature.agent.domain.skill

import com.aiverse.feature.workspace.domain.AgentConfigKind
import com.aiverse.feature.workspace.domain.AgentConfigParseCache
import com.aiverse.feature.workspace.domain.FileAccessProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局技能来源：`~/.aiverse/skills`，跨项目共享。以容器路径经 [FileAccessProvider] 访问，
 * 跟随当前执行环境——本地模式落 App 私有 `filesDir/aiverse/skills`（容器内 `/root/.aiverse/skills`），
 * 远程模式经 SSH 落服务器用户 home 下的 `.aiverse/skills`。远程是独立执行环境，读写均以远端为准。
 *
 * 远程加速：有远程配置快照时一次批量读取全部 SKILL.md/CLAUDE.md，并按快照指纹缓存解析结果，
 * 内容不变不重复解析；无快照（本地模式 / 远程后端未就绪）时回退到逐文件扫描。
 */
@Singleton
class GlobalDirectorySkillSource @Inject constructor(
    private val fileAccess: FileAccessProvider
) : SkillSource {

    val skillsRoot: String = "~/.aiverse/skills"

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
