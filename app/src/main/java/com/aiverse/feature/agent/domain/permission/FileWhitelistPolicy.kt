package com.aiverse.feature.agent.domain.permission

import com.aiverse.feature.agent.domain.skill.SkillRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件写入白名单策略：聚合所有启用技能 SKILL.md frontmatter 的 `file_whitelist`，
 * 供授权引擎判定 writeFile/editFile 的目标路径是否允许写入（配置强制）。
 *
 * 语义：白名单为空 = 未启用白名单限制（保留「写操作需确认」的默认行为）；
 * 白名单非空 = 仅允许命中白名单条目（路径本身或其子路径），越界一律拒绝，任何模式都不例外。
 */
@Singleton
class FileWhitelistPolicy @Inject constructor(
    private val skillRepository: SkillRepository
) {
    /** 全部启用技能的 file_whitelist 并集（归一化、去重、保序）。 */
    fun whitelist(): List<String> =
        skillRepository.listSkills()
            .flatMap { it.fileWhitelist }
            .map { normalize(it) }
            .distinct()

    /** 目标路径是否允许写入：白名单为空放行；否则命中某一条目（本身或子路径）放行。 */
    fun isWriteAllowed(path: String): Boolean {
        val patterns = whitelist()
        if (patterns.isEmpty()) return true
        val target = normalize(path)
        return patterns.any { target == it || target.startsWith("$it/") }
    }

    /**
     * 归一化：去掉首尾空白与尾部 `/`，展开 `~` 为容器家目录（与
     * [ToolPermissionPolicyEngine] 的 HOME_DIR 一致，使 `~/workspace/...` 与 `/root/workspace/...` 等价）。
     */
    private fun normalize(p: String): String {
        var s = p.trim()
        if (s == "~") return "/root"
        if (s.startsWith("~/")) s = "/root" + s.drop(1)
        return s.trimEnd('/')
    }
}
