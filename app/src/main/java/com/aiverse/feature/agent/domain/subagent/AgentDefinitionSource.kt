package com.aiverse.feature.agent.domain.subagent

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.workspace.domain.FileAccessProvider

/** 子代理定义来源：一个目录下的 `*.md`，每个文件一个 agent。 */
interface AgentDefinitionSource {
    fun listDefinitions(): List<AgentDefinition>
}

/** 目录扫描：只取顶层 `*.md`，避免把技能目录等无关内容误当 agent 定义。 */
internal object AgentDefinitionDirectoryScanner {
    private const val TAG = "AgentDefinitionScanner"

    /**
     * 扫描 [root] 下顶层 `*.md`，每个文件一个定义。目录不存在时返回空列表；
     * 单个文件解析异常（IO 失败、frontmatter 非法等）只跳过该文件，不拖垮整个目录；
     * 扫描过程整体异常（远程工作区未连接等）时降级为空列表并记日志，不向上抛。
     */
    fun scan(provider: FileAccessProvider, root: String): List<AgentDefinition> = runCatching {
        if (!provider.isDirectory(root)) return@runCatching emptyList()
        val base = root.trimEnd('/')
        provider.listFiles(root)
            .filter { !it.isDirectory && it.name.endsWith(".md", ignoreCase = true) }
            .mapNotNull { entry ->
                runCatching { AgentDefinitionParser.parse(provider, "$base/${entry.name}") }
                    .onFailure { FileLogger.w(TAG, "解析子代理定义失败，已跳过: ${entry.name}", it) }
                    .getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }.getOrElse { e ->
        FileLogger.w(TAG, "扫描子代理定义目录失败: $root", e)
        emptyList()
    }

    /**
     * 从一整套远程配置快照的文件表解析子代理定义（远程加速路径）：[files] 是「容器相对路径 → 文件全文」，
     * 一次批量读取的结果。只取顶层 `*.md`（相对路径不含 `/`），每个文件一个定义，按名称排序；
     * 单文件解析异常逐个跳过并记日志；整体异常降级为空列表，不向上抛。
     */
    fun scan(root: String, files: Map<String, String>): List<AgentDefinition> = runCatching {
        val base = root.trimEnd('/')
        files.filterKeys { rel -> !rel.contains('/') && rel.endsWith(".md", ignoreCase = true) }
            .mapNotNull { (rel, text) ->
                val fileName = rel.substringAfterLast('/')
                runCatching {
                    AgentDefinitionParser.parseText(text, fileName.removeSuffix(".md"), "$base/$fileName")
                }.onFailure { FileLogger.w(TAG, "解析子代理定义失败，已跳过: $fileName", it) }
                    .getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }.getOrElse { e ->
        FileLogger.w(TAG, "解析子代理定义快照失败: $root", e)
        emptyList()
    }
}
