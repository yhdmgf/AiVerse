package com.aiverse.feature.agent.domain.skill

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.workspace.domain.FileAccessProvider

/**
 * 目录型技能源的共享扫描逻辑：递归查找 SKILL.md / CLAUDE.md，
 * 每个含指令文件的目录解析为一个 Skill。
 *
 * 目录经 [FileAccessProvider] 以容器路径访问，本地与远程（SSH）同一套逻辑。
 */
object SkillDirectoryScanner {
    private const val TAG = "SkillDirectoryScanner"

    /** 允许一定的嵌套深度（比如 repo/skills/my-skill/SKILL.md）。 */
    private const val MAX_DEPTH = 4
    private const val SKILL_FILE = "SKILL.md"
    private const val CLAUDE_FILE = "CLAUDE.md"

    /**
     * 扫描 [root] 目录下所有合法技能，按名称排序。
     * 目录不存在时返回空列表；扫描过程异常（IO 失败、远程工作区未连接等）时降级为空列表并记日志，
     * 不向上抛——技能扫描失败不该让调用方（工作区切换、AI 请求）崩溃。
     */
    fun scan(provider: FileAccessProvider, root: String): List<Skill> = runCatching {
        val dirs = provider.listFilesRecursive(root, MAX_DEPTH)
            .filter { relative ->
                val name = relative.substringAfterLast('/')
                name.equals(SKILL_FILE, ignoreCase = true) || name.equals(CLAUDE_FILE, ignoreCase = true)
            }
            .map { it.substringBeforeLast('/', "") }
            .distinct()

        val base = root.trimEnd('/')
        dirs.mapNotNull { relative ->
            val dirPath = if (relative.isEmpty()) base else "$base/$relative"
            SkillParser.parse(provider, dirPath)
        }.sortedBy { it.name.lowercase() }
    }.getOrElse { e ->
        FileLogger.w(TAG, "扫描技能目录失败: $root", e)
        emptyList()
    }

    /**
     * 从一整套远程配置快照的文件表解析技能（远程加速路径）：[files] 是「容器相对路径 → 文件全文」，
     * 一次批量读取的结果，无需逐文件往返。按 SKILL.md/CLAUDE.md 所在目录分组，每个目录解析为一个 Skill，
     * 与 [scan] 同序（按名称）。目录不存在/无技能时返回空列表；解析异常降级为空列表并记日志。
     */
    fun scan(root: String, files: Map<String, String>): List<Skill> = runCatching {
        val base = root.trimEnd('/')
        files.keys
            .filter { rel ->
                val name = rel.substringAfterLast('/')
                name.equals(SKILL_FILE, ignoreCase = true) || name.equals(CLAUDE_FILE, ignoreCase = true)
            }
            .map { it.substringBeforeLast('/', "") }
            .distinct()
            .mapNotNull { rel -> parseDirFromSnapshot(base, rel, files) }
            .sortedBy { it.name.lowercase() }
    }.getOrElse { e ->
        FileLogger.w(TAG, "解析技能快照失败: $root", e)
        emptyList()
    }

    /** 从快照中取某技能目录的指令文件（SKILL.md 优先，退回 CLAUDE.md），解析为一个 Skill。 */
    private fun parseDirFromSnapshot(base: String, rel: String, files: Map<String, String>): Skill? {
        val inDir = files.filterKeys { key -> key.substringBeforeLast('/', "") == rel }
        val entry = inDir.entries.firstOrNull { it.key.substringAfterLast('/').equals(SKILL_FILE, ignoreCase = true) }
            ?: inDir.entries.firstOrNull { it.key.substringAfterLast('/').equals(CLAUDE_FILE, ignoreCase = true) }
            ?: return null
        val dirPath = if (rel.isEmpty()) base else "$base/$rel"
        val fallbackName = (if (rel.isEmpty()) base.trimEnd('/').substringAfterLast('/') else rel.substringAfterLast('/'))
            .ifEmpty { SKILL_FILE.removeSuffix(".md") }
        return SkillParser.parseText(entry.value, fallbackName).copy(dirPath = dirPath)
    }
}