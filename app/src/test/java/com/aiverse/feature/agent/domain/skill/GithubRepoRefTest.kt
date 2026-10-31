package com.aiverse.feature.agent.domain.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubRepoRefTest {

    private fun assertRef(
        url: String,
        owner: String,
        repo: String,
        ref: String?,
        archiveUrl: String
    ) {
        val parsed = GithubRepoRef.parse(url)
        assertEquals(owner, parsed?.owner)
        assertEquals(repo, parsed?.repo)
        assertEquals(ref, parsed?.ref)
        assertEquals(archiveUrl, parsed?.archiveUrl())
    }

    @Test
    fun parse_standardHttpsUrl() {
        assertRef(
            "https://github.com/yhdmgf/AiVerse",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
    }

    @Test
    fun parse_acceptsNoSchemeWwwAndHttp() {
        assertRef(
            "github.com/yhdmgf/AiVerse",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
        assertRef(
            "https://www.github.com/yhdmgf/AiVerse",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
        assertRef(
            "http://github.com/yhdmgf/AiVerse",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
    }

    @Test
    fun parse_stripsGitSuffixAndTrailingSlash() {
        assertRef(
            "https://github.com/yhdmgf/AiVerse.git/",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
    }

    @Test
    fun parse_treeBranchUrl() {
        assertRef(
            "https://github.com/yhdmgf/AiVerse/tree/main",
            "yhdmgf", "AiVerse", "main",
            "https://codeload.github.com/yhdmgf/AiVerse/zip/main"
        )
    }

    @Test
    fun parse_archiveRefsHeadsUrl() {
        assertRef(
            "https://github.com/yhdmgf/AiVerse/archive/refs/heads/main.zip",
            "yhdmgf", "AiVerse", "main",
            "https://codeload.github.com/yhdmgf/AiVerse/zip/main"
        )
    }

    @Test
    fun parse_sshForms() {
        assertRef(
            "git@github.com:yhdmgf/AiVerse.git",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
        assertRef(
            "ssh://git@github.com/yhdmgf/AiVerse.git",
            "yhdmgf", "AiVerse", null,
            "https://codeload.github.com/yhdmgf/AiVerse/zip/HEAD"
        )
    }

    @Test
    fun parse_rejectsNonGithubHost() {
        assertNull(GithubRepoRef.parse("https://gitlab.com/yhdmgf/AiVerse"))
        assertNull(GithubRepoRef.parse("https://example.com/owner/repo"))
    }

    @Test
    fun parse_rejectsMissingOwnerOrRepo() {
        assertNull(GithubRepoRef.parse("https://github.com/yhdmgf"))
        assertNull(GithubRepoRef.parse("https://github.com/"))
        assertNull(GithubRepoRef.parse(""))
    }

    @Test
    fun parse_rejectsUnknownPathSegments() {
        assertNull(GithubRepoRef.parse("https://github.com/yhdmgf/AiVerse/issues"))
        assertNull(GithubRepoRef.parse("https://github.com/yhdmgf/AiVerse/releases"))
    }

    @Test
    fun parse_rejectsInvalidOwnerChars() {
        assertNull(GithubRepoRef.parse("https://github.com/jie/api/AiVerse"))
        assertNull(GithubRepoRef.parse("https://github.com/owner with space/repo"))
    }

    @Test
    fun parse_rejectsRefWithDotDotOrWhitespace() {
        assertNull(GithubRepoRef.parse("https://github.com/yhdmgf/AiVerse/tree/..%2F.."))
    }
}
