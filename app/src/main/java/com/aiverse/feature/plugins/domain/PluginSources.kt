package com.aiverse.feature.plugins.domain

import com.aiverse.feature.agent.domain.skill.Skill
import com.aiverse.feature.agent.domain.skill.SkillSource
import com.aiverse.feature.agent.domain.subagent.AgentDefinition
import com.aiverse.feature.agent.domain.subagent.AgentDefinitionSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件贡献的技能来源。内容来自手机端插件包，不经 FileAccessProvider，
 * 因此不会写入远程服务器的技能目录，SSH 模式下同样可用。
 *
 * TODO(适配): [PluginManager] 在此引用的是 AiVerse 同包下的同名类。现有旧 PluginManager（QuickJS
 * 脚本插件）没有 activeSkills()/allSkills()/activeAgents()/allAgents()/revision 这些成员，需要由 P2
 * 提供基于贡献清单的新 PluginManager（或扩展旧类补齐这些 API）才能编译；本文件按 aicode 原结构写好
 * 类型引用，方法缺失的编译问题待 P2 落地后自然消除。
 */
@Singleton
class PluginSkillSource @Inject constructor(
    private val manager: PluginManager
) : SkillSource {

    override fun listSkills(force: Boolean): List<Skill> = manager.activeSkills()

    /** 全部插件技能，不论插件是否启用，供设置页全量展示。 */
    fun listAllSkills(): List<Skill> = manager.allSkills()

    override fun loadInstructions(name: String): String? =
        listSkills().firstOrNull { it.name.equals(name, ignoreCase = true) }?.instructions
}

/** 插件贡献的子代理来源。 */
@Singleton
class PluginAgentSource @Inject constructor(
    private val manager: PluginManager
) : AgentDefinitionSource {

    override fun listDefinitions(): List<AgentDefinition> = manager.activeAgents()

    /** 含已停用插件的定义，供还原在途子会话使用。 */
    fun listAllDefinitions(): List<AgentDefinition> = manager.allAgents()

    /** 插件集合的变更序号，并入子代理清单的缓存版本。 */
    val revision: Long get() = manager.revision
}
