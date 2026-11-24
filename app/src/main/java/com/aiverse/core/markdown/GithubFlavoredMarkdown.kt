package com.aiverse.core.markdown

/**
 * GitHub Flavored Markdown 的补充处理。
 *
 * 渲染器（multiplatform-markdown-renderer 0.41.0）基于 commonmark，只认标准语法：
 * 标题（`#`~`######`）、列表、表格、代码块、引用块都能正常渲染；但 GitHub 的两类扩展
 * 需要额外处理：
 *
 * 1. **告警块**（`> [!NOTE]` / `[!TIP]` / `[!IMPORTANT]` / `[!WARNING]` / `[!CAUTION]`）
 *    —— 这里是纯解析：把引用块源码切成「类型 + 正文」，交给 `core/ui/markdown`
 *    的 `GithubAlertCard` 渲染成 GitHub 同款彩色卡片（图标用 Material 矢量图标，
 *    标题固定英文类型名，配色对齐 GitHub 明/暗两套 palette）。
 * 2. **自动链接**（`#1145`、`GH-1145`、`owner/repo#123`、`@user`）—— 预处理成标准
 *    Markdown 链接，点开即跳浏览器。
 *
 * 自动链接严格跳过代码围栏与行内代码，不改动代码内容；也不重复包裹已有链接。
 */
object GithubFlavoredMarkdown {

    /** 仓库坐标：裸 `#123` / `GH-123` 需要它才能拼出完整 URL。 */
    data class RepoRef(val owner: String, val repo: String) {
        val issuesBase: String get() = "https://github.com/$owner/$repo/issues"
        fun issueUrl(number: String): String = "$issuesBase/$number"
    }

    /**
     * GitHub 告警类型。标题、颜色、图标都按 GitHub 官方实现对齐：
     * 标题**固定为英文类型名**（GitHub 不允许自定义标题），图标用 octicons 语义对应的
     * Material 矢量图标，不走 emoji。
     */
    enum class AlertType(val marker: String, val title: String) {
        NOTE("NOTE", "Note"),
        TIP("TIP", "Tip"),
        IMPORTANT("IMPORTANT", "Important"),
        WARNING("WARNING", "Warning"),
        CAUTION("CAUTION", "Caution");

        companion object {
            fun fromMarker(marker: String): AlertType? =
                entries.firstOrNull { it.marker.equals(marker, ignoreCase = true) }
        }
    }

    /** 解析出来的告警块：[type] 决定外观，[body] 是去掉 `>` 前缀与标记行后的正文。 */
    data class Alert(val type: AlertType, val body: String)

    private val ALERT_MARKER = Regex(
        """^\s*>+\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 判断一个引用块的源码是不是告警块。
     *
     * GitHub 要求指针行必须是引用块的**首行**，这里保持一致：首行不是标记就直接返回 null，
     * 走普通引用块渲染。
     */
    fun parseAlert(rawBlockQuote: String): Alert? {
        val lines = rawBlockQuote.lines()
        var firstIndex = -1
        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) continue
            firstIndex = index
            break
        }
        if (firstIndex < 0) return null
        val marker = ALERT_MARKER.matchEntire(lines[firstIndex]) ?: return null
        val type = AlertType.fromMarker(marker.groupValues[1]) ?: return null
        val body = lines.drop(firstIndex + 1)
            .joinToString("\n") { stripQuotePrefix(it) }
            .trim()
        return Alert(type, body)
    }

    /** 去掉行首的 `>`（含多余空格）；空行保留为空，避免把段落间距吃掉。 */
    private fun stripQuotePrefix(line: String): String =
        line.trimStart().removePrefix(">").removePrefix(" ")

    // ------------------------------------------------------------ 自动生成内容过滤

    /** 入口：先滤掉 GitHub 自动生成的样板段落，再补自动链接。 */
    fun prepare(text: String, repo: RepoRef?): String = autolink(stripBoilerplate(text), repo)

    /**
     * 过滤 GitHub 自动生成、对用户无意义的段落（保留原有行为，别被这次改造弄丢）：
     * - `What's Changed` / `What's New` 标题行
     * - `Full Changelog: <url>` 行
     * - Markdown 表格行（GitHub 自动生成的对比表格）
     *
     * 代码围栏内部不参与过滤。
     */
    fun stripBoilerplate(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var fence: String? = null
        var blankRun = 0
        text.lineSequence().forEach { raw ->
            val trimmed = raw.trim()
            val fenceMatch = FENCE.find(raw.trimStart())
            if (fenceMatch != null) {
                val run = fenceMatch.value.trim()
                fence = when {
                    fence == null -> run
                    fence == run -> null
                    else -> fence
                }
                appendLine(out, raw)
                blankRun = 0
                return@forEach
            }
            if (fence != null) {
                appendLine(out, raw)
                blankRun = 0
                return@forEach
            }
            val drop = trimmed.startsWith("|") ||
                BOILERPLATE_LINE.containsMatchIn(trimmed)
            if (drop) return@forEach
            // 连续空行压成一行
            if (trimmed.isEmpty()) {
                blankRun++
                if (blankRun > 1) return@forEach
            } else {
                blankRun = 0
            }
            appendLine(out, raw.trimEnd())
        }
        return out.toString().trim()
    }

    /** 逐行写入：除首行外每行前加换行，避免行尾被吞。 */
    private fun appendLine(sb: StringBuilder, line: String) {
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(line)
    }

    private val BOILERPLATE_LINE = Regex(
        """^(?:#{1,6}\s*)?(?:What'?s\s+(?:Changed|New)|Full Changelog)\b""",
        RegexOption.IGNORE_CASE
    )

    // ---------------------------------------------------------------- 自动链接

    /** 把 GitHub 写法的引用补成标准链接；代码块与行内代码内部原样保留。 */
    fun autolink(text: String, repo: RepoRef?): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length + 128)
        var fence: String? = null
        text.lineSequence().forEachIndexed { index, raw ->
            if (index > 0) out.append('\n')
            val fenceMatch = FENCE.find(raw.trimStart())
            if (fenceMatch != null) {
                val run = fenceMatch.value.trim()
                fence = when {
                    fence == null -> run
                    fence == run -> null
                    else -> fence
                }
                out.append(raw)
                return@forEachIndexed
            }
            if (fence != null) {
                out.append(raw)
                return@forEachIndexed
            }
            out.append(transformOutsideInlineCode(raw, repo))
        }
        return out.toString()
    }

    /** 逐字符扫描行内代码区间（`` ` `` / ``` `` ```），区间外才交给 [linkRefs]。 */
    private fun transformOutsideInlineCode(line: String, repo: RepoRef?): String {
        val sb = StringBuilder(line.length + 32)
        var i = 0
        var plainStart = 0
        while (i < line.length) {
            if (line[i] != '`') {
                i++
                continue
            }
            val runStart = i
            while (i < line.length && line[i] == '`') i++
            val tickLen = i - runStart
            val close = closingBackticks(line, i, tickLen) ?: continue
            sb.append(linkRefs(line.substring(plainStart, runStart), repo))
            sb.append(line, runStart, close + tickLen)
            i = close + tickLen
            plainStart = i
        }
        sb.append(linkRefs(line.substring(plainStart), repo))
        return sb.toString()
    }

    /** 找到与开头等长的反引号串；找不到说明这里不是行内代码，按普通字符继续。 */
    private fun closingBackticks(line: String, from: Int, tickLen: Int): Int? {
        var i = from
        while (i < line.length) {
            if (line[i] != '`') {
                i++
                continue
            }
            val start = i
            while (i < line.length && line[i] == '`') i++
            if (i - start == tickLen) return start
        }
        return null
    }

    /** 依次处理：跨仓库引用 → GH-issue → 裸 issue → 提及用户。 */
    private fun linkRefs(segment: String, repo: RepoRef?): String {
        var s = segment

        // owner/repo#123
        s = CROSS_REPO.replace(s) { m ->
            val owner = m.groupValues[1]
            val name = m.groupValues[2]
            val number = m.groupValues[3]
            "[${m.value}](https://github.com/$owner/$name/issues/$number)"
        }

        // GH-123
        s = GH_ISSUE.replace(s) { m ->
            val number = m.groupValues[2]
            val url = repo?.issueUrl(number) ?: return@replace m.value
            "[${m.value}]($url)"
        }

        // #123：只有拿到仓库坐标才能拼出完整 URL，否则原样保留
        if (repo != null) {
            s = BARE_ISSUE.replace(s) { m ->
                "[${m.value}](${repo.issueUrl(m.groupValues[1])})"
            }
        }

        // @user：邮箱里的 @ 前面是字母数字，已被 lookbehind 排除
        s = MENTION.replace(s) { m ->
            val user = m.groupValues[1]
            "[@$user](https://github.com/$user)"
        }

        return s
    }

    private val FENCE = Regex("""^(```|~~~)""")

    private val CROSS_REPO = Regex(
        """(?<![\w/\[(.])([A-Za-z0-9][A-Za-z0-9._-]*)/([A-Za-z0-9][A-Za-z0-9._-]*)#([0-9]{1,6})(?![0-9])"""
    )

    private val GH_ISSUE = Regex(
        """(?<![\w/\[(.])(GH|gh)-([0-9]{1,6})(?![0-9])"""
    )

    /** 裸 `#123`：排除 `##` 标题、URL 片段（前为 `/`）、已有链接目标（前为 `](`）；放行 `(#173)`/`（#173）`。 */
    private val BARE_ISSUE = Regex(
        """(?<!]\()(?<![#\w/&\[.])#([0-9]{1,6})(?![0-9])"""
    )

    /** `@user`：排除邮箱、已有链接里的路径、以及 `@@`。 */
    private val MENTION = Regex(
        """(?<![\w@/.\[(])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))(?![\w@])"""
    )
}
