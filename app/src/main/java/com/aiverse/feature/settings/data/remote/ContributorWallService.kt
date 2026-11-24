package com.aiverse.feature.settings.data.remote

import android.content.Context
import com.aiverse.core.net.RepoDataFetcher
import com.aiverse.feature.settings.domain.model.Contributor
import com.aiverse.feature.settings.domain.model.parseContributors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 贡献者墙快照数据源：从本仓库 `data` 分支拉取 CI 生成的 `contributors.json`。
 *
 * 采用 stale-while-revalidate：[fetchCached] 立即返回本地已有数据（磁盘缓存优先，其次 APK 内置
 * assets）供展开时即时展示；[fetch] 随后在后台强制条件请求对账（ETag / If-None-Match，没变就
 * 304 不重复下载），拉到新数据由调用方更新界面与缓存。解析统一走 domain 的 [parseContributors]。
 */
@Singleton
class ContributorWallService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val repoFetcher = RepoDataFetcher(context, branch = CONTRIBUTORS_BRANCH)

    /**
     * 立即返回本地已有的快照（磁盘缓存优先，其次 APK 内置），不发网络请求。
     *
     * 用于展开贡献者墙时的即时展示：先给一个不空白的内容，真正的最新数据由 [fetch] 在后台
     * 对账后补上。磁盘缓存里往往已经是上一次拉到的数据（可能比内置新），所以优先于 assets。
     */
    fun fetchCached(): List<Contributor> {
        val cached = runCatching { repoFetcher.readLocalCache(REPO_PATH) }.getOrNull()
        return cached?.let(::parseContributors)?.ifEmpty { initial() } ?: initial()
    }

    /**
     * 后台对账最新快照：跳过本地缓存有效期（`maxAgeMs = 0L`）强制发起条件请求。
     *
     * RepoDataFetcher 自带 ETag / If-None-Match：远端没变返回 304（不重复下载、只刷新缓存
     * 时间戳），变了才下载并写回缓存，断网则回退磁盘缓存。因此每次展开墙都调用它是安全的 —
     * 没有新数据时几乎零成本，有新数据（比如 CI 刚发布、或手动加了的贡献者）时这一次就能拉到。
     */
    suspend fun fetch(): List<Contributor> = withContext(Dispatchers.IO) {
        when (val result = runCatching { repoFetcher.fetch(REPO_PATH, maxAgeMs = 0L) }.getOrNull()) {
            is RepoDataFetcher.FetchResult.Success ->
                parseContributors(result.content).ifEmpty { initial() }
            is RepoDataFetcher.FetchResult.FallbackDiskCache ->
                parseContributors(result.content).ifEmpty { initial() }
            else -> initial()
        }
    }

    /**
     * 同步读取 APK 内置快照（`assets/contributors.json`），作为无网络且无磁盘缓存时的最终兜底。
     */
    fun initial(): List<Contributor> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
    }.getOrNull()?.let(::parseContributors).orEmpty()

    private companion object {
        /** 快照发布在 `data` 分支的 `data/` 目录，与 `sync-contributors.yml` 的发布位置一致。 */
        private const val CONTRIBUTORS_BRANCH = "data"
        private const val REPO_PATH = "data/contributors.json"
        private const val ASSET_PATH = "contributors.json"
    }
}
