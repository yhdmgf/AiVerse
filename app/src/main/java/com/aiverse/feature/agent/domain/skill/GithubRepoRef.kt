package com.aiverse.feature.agent.domain.skill

import java.net.URI

/**
 * 从 GitHub 仓库地址解析出的引用：owner/repo 与可选的 ref（分支、tag 或提交）。
 * 供「从 GitHub 拉取技能」使用：把仓库源码 zip 下载到本地后交给 [SkillImporter] 导入。
 *
 * [parse] 为纯函数（无网络、无 Android 依赖），便于单测；支持的输入形如：
 * - https://github.com/owner/repo（无协议 / http / www 前缀均可）
 * - https://github.com/owner/repo.git
 * - https://github.com/owner/repo/tree/{branch}（或 /blob/{branch}）
 * - https://github.com/owner/repo/archive/refs/heads/{branch}.zip
 * - git@github.com:owner/repo.git 与 ssh://git@github.com/owner/repo.git
 */
internal data class GithubRepoRef(val owner: String, val repo: String, val ref: String?) {

    /** codeload 源码 zip 下载地址；ref 为空时用 HEAD 拉仓库默认分支。 */
    fun archiveUrl(): String = "https://codeload.github.com/$owner/$repo/zip/${ref ?: "HEAD"}"

    companion object {
        private const val HOST = "github.com"

        /**
         * 解析 GitHub 仓库地址；非法地址（非 GitHub 主机、缺 owner/repo、路径含未知段）返回 null。
         */
        fun parse(rawUrl: String): GithubRepoRef? {
            val url = rawUrl.trim()
            if (url.isEmpty()) return null

            parseSsh(url)?.let { return it }

            val withScheme = if (url.contains("://")) url else "https://$url"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host != HOST && host != "www.$HOST") return null

            val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() } ?: return null
            if (segments.size < 2) return null

            val owner = segments[0]
            val repo = segments[1].removeSuffix(".git")
            if (!isNameValid(owner) || !isNameValid(repo)) return null

            val rest = segments.drop(2)
            val ref = if (rest.isEmpty()) {
                null
            } else when (rest[0]) {
                "tree", "blob" -> rest.getOrNull(1)?.removeSuffix(".zip")?.takeIf { it.isNotBlank() }
                "archive" -> extractArchiveRef(rest)
                // issues / releases / commits / pulls 等页面不是仓库地址，直接判非法。
                else -> return null
            }
            if (ref != null && !isRefValid(ref)) return null
            return GithubRepoRef(owner, repo, ref)
        }

        /** SSH 形式：git@github.com:owner/repo.git 或 ssh://git@github.com/owner/repo.git。 */
        private fun parseSsh(url: String): GithubRepoRef? {
            val colonForm = Regex("""^git@$HOST:(.+?)(?:\.git)?/?$""").matchEntire(url)
            if (colonForm != null) {
                return buildRef(colonForm.groupValues[1].split('/'))
            }
            if (url.startsWith("ssh://")) {
                val uri = runCatching { URI(url) }.getOrNull() ?: return null
                if (uri.host?.lowercase() != HOST) return null
                return buildRef(uri.path?.trim('/')?.split('/')?.filter { it.isNotEmpty() } ?: return null)
            }
            return null
        }

        private fun buildRef(segments: List<String>): GithubRepoRef? {
            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments[1].removeSuffix(".git")
            if (!isNameValid(owner) || !isNameValid(repo)) return null
            return GithubRepoRef(owner, repo, null)
        }

        /** 解析 /archive/.../{ref}.zip：优先 refs/heads、refs/tags，其次最后一段的 {ref}[.zip]。 */
        private fun extractArchiveRef(rest: List<String>): String? {
            val heads = rest.indexOf("heads")
            if (heads >= 0 && heads + 1 < rest.size) return rest[heads + 1].removeSuffix(".zip")
            val tags = rest.indexOf("tags")
            if (tags >= 0 && tags + 1 < rest.size) return rest[tags + 1].removeSuffix(".zip")
            return rest.lastOrNull()
                ?.removeSuffix(".zip")
                ?.takeIf { it.isNotBlank() && it !in setOf("refs", "archive", "heads", "tags") }
        }

        private fun isNameValid(name: String): Boolean =
            name.isNotEmpty() && name.length <= 100 && name.matches(Regex("[A-Za-z0-9._-]+"))

        private fun isRefValid(ref: String): Boolean =
            ref.isNotEmpty() && ref.length <= 200 && !ref.contains("..") && ref.none { it.isWhitespace() }
    }
}
