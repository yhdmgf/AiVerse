package com.aiverse.feature.git.domain

import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.aiverse.feature.git.domain.model.GitHubComment
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.git.domain.model.GitHubIssueUser
import com.aiverse.feature.git.domain.model.GitHubMilestone
import com.aiverse.feature.git.domain.model.IssueLabel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * issue / PR 所在的托管平台。URL 解析与鉴权方式按平台区分：
 * GitHub 用 `Authorization: Bearer`，Gitee / GitCode 用 `access_token` 查询参数。
 */
internal enum class IssuePlatform(
    val label: String,
    val hosts: List<String>,
    val apiBase: String
) {
    GITHUB("GitHub", listOf("github.com"), "https://api.github.com/repos"),
    GITEE("Gitee", listOf("gitee.com"), "https://gitee.com/api/v5/repos"),
    GITCODE("GitCode", listOf("gitcode.com", "atomgit.com"), "https://api.gitcode.com/api/v5/repos");

    val supportsIssueWrite: Boolean get() = true
}

/** 解析后的 issue 请求目标。 */
internal data class IssueTarget(
    val platform: IssuePlatform,
    val owner: String,
    val repo: String,
    val token: String
) {
    val repoBase: String get() = "${platform.apiBase}/$owner/$repo"
    val issueWebBase: String
        get() = when (platform) {
            IssuePlatform.GITHUB -> "https://github.com/$owner/$repo/issues"
            IssuePlatform.GITEE -> "https://gitee.com/$owner/$repo/issues"
            IssuePlatform.GITCODE -> "https://gitcode.com/$owner/$repo/issues"
        }
}

/**
 * 从 remote URL 解析平台与 owner/repo。支持 https 与 `git@host:owner/repo.git`；
 * 非受支持平台或格式不合法时返回 null。
 */
internal fun parseIssueRepo(url: String): Triple<IssuePlatform, String, String>? {
    val trimmed = url.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null

    val hostAndPath: Pair<String, String> = when {
        trimmed.startsWith("git@") -> {
            val rest = trimmed.removePrefix("git@")
            val idx = rest.indexOf(':')
            if (idx <= 0) return null
            rest.substring(0, idx).lowercase() to rest.substring(idx + 1)
        }
        else -> {
            val withoutScheme = trimmed
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("ssh://")
            val idx = withoutScheme.indexOf('/')
            if (idx <= 0) return null
            withoutScheme.substring(0, idx).lowercase() to withoutScheme.substring(idx + 1)
        }
    }

    val host = hostAndPath.first
    val platform = IssuePlatform.entries.firstOrNull { p -> p.hosts.any { host == it || host.endsWith(".$it") } }
        ?: return null
    val segments = hostAndPath.second.removeSuffix(".git").split("/")
    if (segments.size < 2) return null
    val owner = segments[0]
    val repo = segments[1]
    if (owner.isBlank() || repo.isBlank()) return null
    return Triple(platform, owner, repo)
}

/** 解析当前 remote 与对应平台的 token；非受支持平台或没有 token 时返回 null。 */
internal suspend fun resolveIssueTarget(
    gitRepository: GitRepository,
    credentialRepository: CredentialRepository
): IssueTarget? {
    val (platform, owner, repo) = parseIssueRepo(gitRepository.getRepoUrl()) ?: return null
    val token = platform.hosts.firstNotNullOfOrNull { host ->
        credentialRepository.findForHost(host)?.token?.takeIf { it.isNotBlank() }
    } ?: return null
    return IssueTarget(platform, owner, repo, token)
}

/** 拼出仓库内某个 API 路径的完整 URL，并按平台附加鉴权参数。 */
internal fun issueApiUrl(target: IssueTarget, path: String): String {
    val raw = target.repoBase + path
    if (target.platform == IssuePlatform.GITHUB) return raw
    val sep = if (raw.contains('?')) "&" else "?"
    val encoded = URLEncoder.encode(target.token, "UTF-8")
    return "$raw${sep}access_token=$encoded"
}

/**
 * 执行一次 issue API 请求。GitHub 走 Bearer；Gitee / GitCode 的鉴权已在 [issueApiUrl] 中拼好。
 * 非 2xx 抛 [GitHubIssuesException]（含状态码与响应体）。
 */
internal fun executeIssueRequest(
    client: OkHttpClient,
    target: IssueTarget,
    path: String,
    method: String = "GET",
    jsonBody: String? = null
): String {
    val url = issueApiUrl(target, path)
    val builder = Request.Builder().url(url).header("Accept", "application/json")
    if (target.platform == IssuePlatform.GITHUB) {
        builder.header("Accept", "application/vnd.github+json")
        builder.header("Authorization", "Bearer ${target.token}")
    }
    when (method) {
        "GET" -> builder.get()
        "DELETE" -> builder.delete()
        else -> {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            builder.method(method, (jsonBody ?: "{}").toRequestBody(mediaType))
        }
    }
    client.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) {
            val errBody = runCatching { resp.body?.string() }.getOrNull()
            throw GitHubIssuesException("HTTP ${resp.code}${errBody?.let { ": $it" } ?: ""}")
        }
        return resp.body?.string().orEmpty()
    }
}

// ── 响应归一化：Gitee / GitCode 与 GitHub 字段不完全一致，统一映射到现有模型 ──

private fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val value = get(key) ?: continue
        if (value.isJsonPrimitive) return value.asString
    }
    return null
}

private fun JsonObject.intOrNull(vararg keys: String): Int? =
    str(*keys)?.toIntOrNull()

private fun JsonObject.boolOrFalse(key: String): Boolean =
    (get(key) as? com.google.gson.JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean ?: false

private fun userOf(element: JsonElement?): GitHubIssueUser? {
    val obj = element as? JsonObject ?: return null
    val login = obj.str("login", "name", "username") ?: return null
    return GitHubIssueUser(login = login)
}

private fun labelsOf(element: JsonElement?): List<IssueLabel> {
    val arr = element as? JsonArray ?: return emptyList()
    return arr.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val name = obj.str("name") ?: return@mapNotNull null
        IssueLabel(name = name, color = obj.str("color"))
    }
}

private fun milestoneOf(element: JsonElement?): GitHubMilestone? {
    val obj = element as? JsonObject ?: return null
    val number = obj.str("number", "id")?.toLongOrNull() ?: return null
    val title = obj.str("title") ?: return null
    return GitHubMilestone(
        number = number,
        title = title,
        state = obj.str("state") ?: "open",
        description = obj.str("description"),
        htmlUrl = obj.str("html_url") ?: ""
    )
}

private fun normalizeIssueState(raw: String?): String = when (raw?.lowercase()) {
    "closed", "rejected", "finished" -> "closed"
    else -> "open"
}

/** 把平台返回的 issue JSON 归一化成 [GitHubIssue]；number 保留平台原始字符串（Gitee 为 IKXYF8 这类）。 */
internal fun parseIssue(element: JsonElement, target: IssueTarget): GitHubIssue {
    val obj = element as JsonObject
    val number = obj.str("number", "id").orEmpty()
    val commentCount = obj.intOrNull("comments") ?: 0
    val assignees = mutableListOf<GitHubIssueUser>()
    (obj.get("assignees") as? JsonArray)?.forEach { item ->
        userOf(item)?.let { assignees += it }
    }
    userOf(obj.get("assignee"))?.let { if (assignees.isEmpty()) assignees += it }
    return GitHubIssue(
        number = number,
        title = obj.str("title").orEmpty(),
        body = obj.str("body", "description"),
        state = normalizeIssueState(obj.str("state")),
        user = userOf(obj.get("user")),
        createdAt = obj.str("created_at") ?: "",
        updatedAt = obj.str("updated_at") ?: "",
        closedAt = obj.str("closed_at", "finished_at"),
        comments = commentCount,
        labels = labelsOf(obj.get("labels")),
        assignees = assignees,
        milestone = milestoneOf(obj.get("milestone")),
        locked = obj.boolOrFalse("locked"),
        htmlUrl = obj.str("html_url") ?: "${target.issueWebBase}/$number",
        pullRequest = obj.get("pull_request")
    )
}

internal fun parseIssues(array: JsonArray, target: IssueTarget): List<GitHubIssue> =
    array.mapNotNull { item -> runCatching { parseIssue(item, target) }.getOrNull() }

internal fun parseComment(element: JsonElement): GitHubComment {
    val obj = element as JsonObject
    val rawId = obj.str("id").orEmpty()
    val id = rawId.toLongOrNull() ?: rawId.hashCode().toLong()
    return GitHubComment(
        id = id,
        user = userOf(obj.get("user")),
        body = obj.str("body"),
        createdAt = obj.str("created_at") ?: "",
        updatedAt = obj.str("updated_at") ?: "",
        htmlUrl = obj.str("html_url") ?: ""
    )
}

internal fun parseComments(array: JsonArray): List<GitHubComment> =
    array.mapNotNull { item -> runCatching { parseComment(item) }.getOrNull() }

internal fun parseMilestones(array: JsonArray): List<GitHubMilestone> =
    array.mapNotNull { item -> runCatching { milestoneOf(item) }.getOrNull() }

internal fun parseJsonElement(gson: Gson, body: String): JsonElement =
    runCatching { gson.fromJson(body, JsonElement::class.java) }.getOrNull()
        ?: throw GitHubIssuesException("响应不是合法 JSON")
