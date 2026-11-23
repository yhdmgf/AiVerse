package com.aiverse.feature.git.domain

import com.aiverse.core.util.LineDiff

/**
 * 一行 patch 的解析结果：差异类型 + 行文本 + 旧/新行号。
 * 行号已按 hunk 头 `@@ -oldStart,oldCount +newStart,newCount @@` 换算成文件里的绝对行号，
 * 直接对应 [com.aiverse.feature.git.presentation.component.DiffRow] 的 oldLineNum/newLineNum。
 */
data class PatchLine(
    val type: LineDiff.LineType,
    val text: String,
    val oldLineNum: Int?,
    val newLineNum: Int?
)

/**
 * 把 GitHub 返回的单文件 unified diff（commits/{sha} 接口里 file.patch 字段）解析成行级差异。
 *
 * GitHub 的 patch 是标准 unified diff 的单个文件片段，不含 `diff --git`/`---`/`+++` 之外的
 * 多文件拼接，但首部可能带这些文件头行；hunk 头给出行号起点，之后按 `+`/`-`/空格 前缀增量推进。
 */
object PatchDiffParser {

    /** hunk 头：捕获旧/新起点行号（count 可省略，省略即 1 行）。 */
    private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@")

    /**
     * 解析 [patch] 为行级差异列表。空串或不含合法 hunk 头时返回空列表。
     * `\ No newline at end of file` 标记行不产生差异行。
     */
    fun parsePatch(patch: String): List<PatchLine> {
        val rows = ArrayList<PatchLine>()
        var oldNum = 0
        var newNum = 0
        var inHunk = false
        for (raw in patch.lineSequence()) {
            if (raw.startsWith("@@")) {
                val match = HUNK_HEADER.find(raw) ?: continue
                oldNum = match.groupValues[1].toInt()
                newNum = match.groupValues[3].toInt()
                inHunk = true
                continue
            }
            if (!inHunk) continue
            when {
                raw.startsWith("+") -> {
                    rows.add(PatchLine(LineDiff.LineType.ADD, raw.substring(1), null, newNum))
                    newNum++
                }
                raw.startsWith("-") -> {
                    rows.add(PatchLine(LineDiff.LineType.REMOVE, raw.substring(1), oldNum, null))
                    oldNum++
                }
                raw.startsWith(" ") -> {
                    rows.add(PatchLine(LineDiff.LineType.CONTEXT, raw.substring(1), oldNum, newNum))
                    oldNum++
                    newNum++
                }
                // 部分生成器会把纯空白上下文行的前缀空格一并裁掉，剩下的空串仍是上下文行。
                raw.isEmpty() -> {
                    rows.add(PatchLine(LineDiff.LineType.CONTEXT, "", oldNum, newNum))
                    oldNum++
                    newNum++
                }
                else -> { /* `\ No newline...` 或无法识别的标记行，跳过 */ }
            }
        }
        return rows
    }
}
