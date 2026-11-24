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
 * 三级兜底：[RepoDataFetcher] 网络（含其内置磁盘 TTL 缓存与断网回退）→ APK 内置 assets 快照。
 * 解析统一走 domain 的 [parseContributors]（含 JSON null "null" 字面量坑修复），此处不重复实现。
 */
@Singleton
class ContributorWallService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val repoFetcher = RepoDataFetcher(context, branch = CONTRIBUTORS_BRANCH)

    /**
     * 拉取贡献者快照：优先网络（缓存未过期则命中本地），失败再读 APK 内置快照。
     * 任何一步失败都降级而不是抛出，保证入口始终有内容可展示或安静收起。
     */
    suspend fun fetch(): List<Contributor> = withContext(Dispatchers.IO) {
        when (val result = runCatching { repoFetcher.fetch(REPO_PATH) }.getOrNull()) {
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
