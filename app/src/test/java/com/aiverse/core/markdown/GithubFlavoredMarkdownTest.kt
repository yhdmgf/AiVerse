package com.aiverse.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 GithubFlavoredMarkdown.autolink 的 issue 号自动链接逻辑：
 * 括号包裹的 `(#173)` / `（#173）` 应能生成链接（本次修复目标），
 * 同时保证 `##` 标题、URL 片段、已有链接目标、行内代码、代码围栏不被误伤。
 */
class GithubFlavoredMarkdownTest {

    private val repo = GithubFlavoredMarkdown.RepoRef("yhdmgf", "AiVerse")

    // ------------------------------------------------------------ 正例：应转链接

    @Test
    fun bareIssue_inParentheses_becomesLink() {
        // release-notes 规范写法 `(#173)`，括号包裹不应阻止链接生成
        val input = "修复远端图片 (#173)"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 issues 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf/AiVerse/issues/")
        )
    }

    @Test
    fun bareIssue_inChineseParentheses_becomesLink() {
        val input = "（#173）"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 issues 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf/AiVerse/issues/")
        )
    }

    @Test
    fun bareIssue_atLineStart_becomesLink() {
        val input = "#173 修复远端图片"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 issues 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf/AiVerse/issues/")
        )
    }

    // ------------------------------------------------------------ 反例：不转链接，原样保留

    @Test
    fun hashHeading_isNotLinked() {
        val input = "## 标题"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun hashHeadingLevel3_isNotLinked() {
        val input = "### 标题"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun urlFragment_isNotLinked() {
        val input = "https://x.com/#/path"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun existingMarkdownLinkTarget_isNotReWrapped() {
        // `[文字](#123)`：`#123` 已是链接目标，不应重复包裹
        val input = "[文字](#123)"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun inlineCode_isNotLinked() {
        val input = "看 `#123` 说明"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun codeFence_isNotLinked() {
        val input = "```\n#123\n```"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    @Test
    fun dotBeforeHash_isNotLinked() {
        // `v1.0#123`：`#` 前是 `.`，属版本号，不转链接
        val input = "v1.0#123"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertEquals(input, output)
    }

    // ------------------------------------------------------------ repo = null：裸 # 原样保留

    @Test
    fun bareIssue_repoNull_keptAsIs() {
        val input = "看 #173 说明"
        val output = GithubFlavoredMarkdown.autolink(input, null)
        assertEquals(input, output)
    }

    // ------------------------------------------------------------ 其他引用类型：确认本次改动未破坏

    @Test
    fun ghIssue_becomesLink() {
        val input = "看 GH-123 说明"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 GH-123 issues 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf/AiVerse/issues/")
        )
    }

    @Test
    fun crossRepo_becomesLink() {
        val input = "看 yhdmgf/AiVerse#123 说明"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 cross-repo issues 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf/AiVerse/issues/")
        )
    }

    @Test
    fun mention_becomesLink() {
        val input = "感谢 @yhdmgf"
        val output = GithubFlavoredMarkdown.autolink(input, repo)
        assertTrue(
            "期望输出包含 user 链接，实际：$output",
            output.contains("](https://github.com/yhdmgf)")
        )
    }
}
