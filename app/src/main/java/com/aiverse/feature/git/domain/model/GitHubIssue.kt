package com.aiverse.feature.git.domain.model

import com.google.gson.annotations.SerializedName

/** GitHub issue 的标签。 */
data class IssueLabel(
    val name: String,
    /** GitHub 返回的颜色（不带 `#` 前缀的十六进制，如 `d73a4a`）。 */
    val color: String? = null
)

/** GitHub issue 的作者 / 受理人（仅取需要的 login 字段）。 */
data class GitHubIssueUser(
    @SerializedName("login") val login: String = ""
)

/** GitHub issue 的里程碑。 */
data class GitHubMilestone(
    val number: Long,
    val title: String,
    /** "open" / "closed"。 */
    val state: String = "open",
    val description: String? = null,
    @SerializedName("html_url") val htmlUrl: String = ""
)

/** GitHub issue 的评论。 */
data class GitHubComment(
    val id: Long,
    val user: GitHubIssueUser? = null,
    val body: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("html_url") val htmlUrl: String = ""
) {
    val userLogin: String get() = user?.login ?: ""
}

/**
 * GitHub issue 的视图模型。由 [com.aiverse.feature.git.domain.GitHubIssuesRepository] 用 Gson 解析
 * GitHub REST API 的 JSON 得到，纯数据、无 Android 依赖。
 */
data class GitHubIssue(
    /** issue 编号。GitHub/GitCode 多为数字字符串，Gitee 是 IKXYF8 这类业务编号。 */
    val number: String,
    val title: String,
    val body: String? = null,
    /** "open" / "closed"。 */
    val state: String = "open",
    val user: GitHubIssueUser? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("closed_at") val closedAt: String? = null,
    val comments: Int = 0,
    val labels: List<IssueLabel> = emptyList(),
    /** 当前 issue 的受理人列表。 */
    val assignees: List<GitHubIssueUser> = emptyList(),
    /** 当前 issue 的里程碑；无则 null。 */
    val milestone: GitHubMilestone? = null,
    /** 是否被锁定（仅限维护者讨论）。 */
    val locked: Boolean = false,
    @SerializedName("html_url") val htmlUrl: String = "",
    /** 非 null 表示该项其实是 Pull Request，不是 issue。 */
    @SerializedName("pull_request") val pullRequest: Any? = null
) {
    val userLogin: String get() = user?.login ?: ""
    val isPullRequest: Boolean get() = pullRequest != null
    val isOpen: Boolean get() = state == "open"
    val assigneeLogins: List<String> get() = assignees.map { it.login }
}
