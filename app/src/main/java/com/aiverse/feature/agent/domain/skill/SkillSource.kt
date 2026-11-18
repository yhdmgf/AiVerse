package com.aiverse.feature.agent.domain.skill

/**
 * 技能数据源抽象接口，支持多渠道加载（本地目录、远程 URL 等）。
 */
interface SkillSource {
    /**
     * 扫描并返回该数据源下当前所有合法的 Skill。
     * @param force 为 true 时绕过数据源自身的快照缓存短路，强制重读（远程配置内容或连接身份变化后用）。
     */
    fun listSkills(force: Boolean = false): List<Skill>

    /** 读取指定 skill 的完整指令正文；不存在或解析失败时返回 null。 */
    fun loadInstructions(name: String): String?
}
