package com.aiverse.feature.plugins.data.index

import java.net.URI

/** 官方插件判定只看插件作者（索引 `author` 字段）是否为 AiVerse 官方署名。 */
object PluginOfficialRepositories {
    /** 历史遗留：旧的按源码仓库 owner 判定，现已改由 [isOfficialAuthor] 按 author 判定。 */
    val GITHUB_OWNERS = setOf("yhdmgf")

    /** AiVerse 官方插件在索引里的作者署名。 */
    const val OFFICIAL_AUTHOR = "aiverse"

    fun ownerOf(repository: String): String? {
        val uri = runCatching { URI(repository) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com") return null
        val segments = uri.path.trim('/').split('/')
        if (segments.size != 2 || segments.any { it.isBlank() }) return null
        return segments[0].lowercase()
    }

    /** 历史遗留：按源码仓库 owner 判定，保留供兼容；现行判定见 [isOfficialAuthor]。 */
    fun isOfficial(repository: String): Boolean = ownerOf(repository) in GITHUB_OWNERS

    /** 现行官方判定：索引 author 署名为 AiVerse 官方（大小写不敏感）。 */
    fun isOfficialAuthor(author: String?): Boolean =
        author?.trim()?.lowercase() == OFFICIAL_AUTHOR
}
