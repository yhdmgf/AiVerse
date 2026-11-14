package com.aiverse.feature.agent.domain.mention

import com.aiverse.feature.agent.domain.container.CommandEngine
import javax.inject.Inject

/**
 * @ 文件引用的文件清单来源：调用容器内 ripgrep 列出工作区文件，并提供内存模糊匹配。
 *
 * 清单不跨「@ 会话」缓存——每次打开菜单由调用方拉取最新列表，关闭即丢弃；
 * 一次会话内的按键只在内存里对已拉取的列表做匹配，不再触发命令。
 */
class FileMentionRepository @Inject constructor(
    private val commandEngine: CommandEngine
) {

    /**
     * 拉取工作区文件清单。走 `rg --files`，与侧边栏文件树保持一致：
     * 始终搜索隐藏文件并忽略 .gitignore / .ignore（`--hidden --no-ignore-vcs`），
     * 否则 `.env`、`.github/` 这类既点开头又被忽略的文件搜不到；
     * 生成物与依赖目录改用 [EXCLUDED_GLOBS] 手动排除，避免大仓库全量遍历。
     * [showHiddenFiles] 关闭时（与侧边栏「显示隐藏文件」偏好一致）额外排除点文件 / 点目录。
     * 容器未就绪（本地未初始化 / 远程未装 rg）时返回 [LoadResult.Unavailable]。
     */
    suspend fun loadFiles(
        projectPath: String,
        showHiddenFiles: Boolean = true
    ): LoadResult {
        if (projectPath.isBlank()) return LoadResult.Unavailable
        val result = commandEngine.runCommandSyncIfReady(
            command = buildFilesCommand(showHiddenFiles),
            projectPath = projectPath,
            timeoutMs = TIMEOUT_MS
        ) ?: return LoadResult.Unavailable
        // rg 无匹配时退出码为 1（这里即空目录），仅 >1 视为执行错误。
        if (isRgMissing(result.output) || result.exitCode == 127) return LoadResult.Unavailable
        if (result.exitCode != null && result.exitCode > 1) {
            return LoadResult.Failed(result.output.ifBlank { "rg 执行失败" })
        }
        val files = result.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_FILES)
            .toList()
        return LoadResult.Success(files)
    }

    /**
     * 模糊匹配：空 [query] 返回前 [limit] 条；否则按子序列匹配质量排序后截断。
     * 匹配不区分大小写，越连续、层级越浅、路径越短排得越前。
     */
    fun match(files: List<String>, query: String, limit: Int = MAX_RESULTS): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return files.take(limit)
        return files.asSequence()
            .mapNotNull { path -> score(path, q)?.let { path to it } }
            .sortedWith(compareBy({ it.second }, { it.first.length }, { it.first }))
            .map { it.first }
            .take(limit)
            .toList()
    }

    /** 返回匹配质量（越小越好），不匹配返回 null。同一评分下由调用方按路径长度/字典序排。 */
    private fun score(path: String, query: String): Int? {
        val lower = path.lowercase()
        val q = query.lowercase()
        var qi = 0
        var gap = 0
        var lastIndex = -1
        for (i in lower.indices) {
            if (qi < q.length && lower[i] == q[qi]) {
                if (lastIndex >= 0) gap += i - lastIndex - 1
                lastIndex = i
                qi++
            }
        }
        return if (qi == q.length) gap else null
    }

    /** 清单拉取结果。 */
    sealed interface LoadResult {
        /** 成功拉到清单（可能为空）。 */
        data class Success(val files: List<String>) : LoadResult

        /** 容器未就绪或未安装 rg，无法搜索。 */
        data object Unavailable : LoadResult

        /** rg 执行出错。 */
        data class Failed(val message: String) : LoadResult
    }

    private companion object {
        const val RG_FILES_BASE_COMMAND = "rg --files --color never --hidden --no-ignore-vcs"
        const val TIMEOUT_MS = 10_000L
        const val MAX_FILES = 20_000

        /** 始终排除的生成物 / 依赖 / VCS 目录；rg 遵守 gitignore 时不出现，故手动排除。 */
        private val EXCLUDED_GLOBS = listOf(
            "**/.git/**",
            "**/.svn/**",
            "**/.hg/**",
            "**/node_modules/**",
            "**/build/**",
            "**/target/**",
            "**/dist/**",
            "**/out/**",
            "**/.gradle/**",
            "**/.idea/**",
            "**/.vscode/**",
            "**/.cache/**",
            "**/.venv/**",
            "**/venv/**",
            "**/__pycache__/**",
            "**/.mypy_cache/**",
            "**/.pytest_cache/**",
            "**/Pods/**",
            "**/.dart_tool/**",
            "**/.expo/**",
            "**/DerivedData/**"
        )

        /**
         * 恒定排除的敏感文件（不受 [showHiddenFiles] 影响）。@ 选中即作为附件/上下文
         * 送入模型，而 `--hidden --no-ignore-vcs` 会把 `.env` / `local.properties` /
         * 密钥与证书等原本被 gitignore 藏起的凭据文件曝进候选，故在此兕底。
         */
        private val SENSITIVE_GLOBS = listOf(
            "**/.env",
            "**/.env.*",
            "**/local.properties",
            "**/*.jks",
            "**/*.keystore",
            "**/*.pem",
            "**/*.key",
            "**/*.pfx",
            "**/*.p12",
            "**/id_rsa",
            "**/id_rsa.*",
            "**/id_ed25519",
            "**/id_ed25519.*",
            "**/.git-credentials",
            "**/.netrc",
            "**/.npmrc",
            "**/.pypirc",
            "**/.htpasswd",
            "**/.aws/**",
            "**/*serviceAccountKey*.json"
        )

        /** 按隐藏文件偏好拼装 rg 命令。生成物与敏感文件排除项恒定生效；关闭隐藏文件时
         * 追加排除所有点开头的文件与目录的 glob，与侧边栏关闭「显示隐藏文件」时一致。
         * glob 参数含感叹号与星号，用单引号包裹避免被 shell 展开。 */
        fun buildFilesCommand(showHiddenFiles: Boolean): String = buildString {
            append(RG_FILES_BASE_COMMAND)
            for (glob in EXCLUDED_GLOBS + SENSITIVE_GLOBS) {
                append(" --glob '")
                append("!")
                append(glob)
                append("'")
            }
            if (!showHiddenFiles) append(" --glob '!**/.*'")
        }

        /** 菜单一次最多展示的匹配条数。 */
        const val MAX_RESULTS = 50

        fun isRgMissing(output: String): Boolean =
            output.contains("command not found", ignoreCase = true) ||
                output.contains("rg: not found", ignoreCase = true)
    }
}
