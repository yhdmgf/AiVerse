package com.aiverse.feature.plugins.data.index

import android.content.Context
import com.aiverse.feature.plugins.domain.SemVer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

data class PluginIndexSnapshot(
    val index: PluginIndexDocument,
    val stale: Boolean,
    val fetchedAt: Long
)

data class PluginReleaseAsset(
    val version: String,
    val name: String,
    val url: String,
    val size: Long
)

data class DownloadedPluginPackage(
    val file: File,
    val asset: PluginReleaseAsset
)

class PluginIndexException(message: String, cause: Throwable? = null) : IOException(message, cause)

class NoMatchingReleaseException(message: String) : IOException(message)

/**
 * 插件索引客户端：拉取 / 缓存 / 校验索引，并解析 Release 资产、下载插件包。
 *
 * 网络客户端复用 `AgentModule` 提供的共享 [OkHttpClient]（覆盖对话、SSE 等），
 * 此处再克隆一份独立超时与关闭缓存，避免互相影响。
 *
 * 注意：AiCode 原版在此注入「下载镜像源设置」，但镜像设置位于 `feature/settings`，
 * 违反「跨 feature 只能依赖 core/」的依赖铁律；本移植版默认直连（与原版镜像为空时的默认行为一致）。
 * 若后续需要镜像加速，应先把镜像读取能力下沉到 `core/`，再在此接入。
 */
@Singleton
class PluginIndexClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    client: OkHttpClient
) {
    private val http = client.newBuilder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .cache(null)
        .build()

    private val cacheRoot = File(context.filesDir, "plugin-index-cache")

    private val hostAppVersion: SemVer? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName?.let(SemVer::parse)
    }.getOrNull()

    suspend fun fetchIndex(source: PluginIndexSource, forceRefresh: Boolean = false): PluginIndexSnapshot =
        withContext(Dispatchers.IO) {
            val cached = cacheFile(source)
            // 索引与元数据是几 KB 的 raw 文件，直连本来就快；走代理反而常被换成 HTML 错误页，
            // 表现为「index.json 解析失败」。因此索引直连，不套镜像（镜像仅用于包下载）。
            val bytes = try {
                getBytes(source.indexUrl, MAX_INDEX_BYTES, noCache = forceRefresh)
            } catch (e: IOException) {
                if (forceRefresh) throw e
                val fallback = readCache(cached) ?: throw e
                return@withContext PluginIndexSnapshot(fallback, stale = true, fetchedAt = cached.lastModified())
            }
            val index = PluginIndexParser.parse(bytes)
            writeCache(cached, bytes)
            PluginIndexSnapshot(index, stale = false, fetchedAt = System.currentTimeMillis())
        }

    fun isCompatible(plugin: PluginIndexPlugin): Boolean {
        val minimum = plugin.minAppVersion?.let { SemVer.parse(it) ?: return false } ?: return true
        val current = hostAppVersion ?: return true
        // 开发 / RC 构建按同一 major.minor.patch 的正式版看待；
        // 最低版本本身是预发布时，仍按完整版本比较。
        val comparable = if (minimum.preRelease.isEmpty()) {
            SemVer(current.major, current.minor, current.patch)
        } else {
            current
        }
        return comparable >= minimum
    }

    /**
     * 解析插件对应的插件包。
     * 插件包统一放在商店仓库 main 分支的 `packages/` 目录，文件名 `{id}_{version}.zip`。
     * version 为空时取 `packages/` 中 `{id}_*.zip` 的最高版本；有 version 时精确查找 `{id}_{version}.zip`。
     * 不再回退 GitHub Release：`packages/` 目录不存在或没有目标文件时直接报错。
     */
    suspend fun resolveRelease(plugin: PluginIndexPlugin): PluginReleaseAsset = withContext(Dispatchers.IO) {
        // 商店直接托管包时按直链下载，跳过 Release 查找（不依赖作者仓库/改名跳转）。
        plugin.packageUrl?.takeIf { it.isNotBlank() }?.let { direct ->
            return@withContext PluginReleaseAsset(
                version = plugin.version.orEmpty(),
                name = "${plugin.id}_${plugin.version ?: "latest"}.zip",
                url = direct,
                // 直链在索引里不带大小，下载时按上限约束
                size = 0L
            )
        }
        val (owner, repo) = parseGitHubRepo(plugin.repository)
            ?: throw NoMatchingReleaseException("不是 GitHub 仓库: ${plugin.repository}")
        val expectedName = plugin.version?.let { assetName(plugin.id, it) }
        val listing = apiGet<List<GitHubContent>>(
            listOf(
                mirrorUrl("$GITHUB_API/repos/$owner/$repo/contents/$PACKAGES_DIR?ref=$DEFAULT_BRANCH"),
                "$GITHUB_API/repos/$owner/$repo/contents/$PACKAGES_DIR?ref=$DEFAULT_BRANCH"
            )
        )
        val candidates = listing.filter { it.type == "file" && it.name.endsWith(".zip") }
        val matched = when {
            expectedName != null -> candidates.firstOrNull { it.name == expectedName }
            else -> candidates
                .mapNotNull { content ->
                    val version = assetVersion(plugin.id, content.name) ?: return@mapNotNull null
                    content to version
                }
                .maxByOrNull { SemVer.parse(it.second) ?: SemVer(0, 0, 0) }
                ?.let { it.first }
        } ?: throw NoMatchingReleaseException("未找到插件包: ${expectedName ?: "${plugin.id}_*.zip"}")
        val version = expectedName?.let { plugin.version!! } ?: assetVersion(plugin.id, matched.name)!!
        PluginReleaseAsset(
            version = version,
            name = matched.name,
            url = rawUrl(owner, repo, matched.path),
            size = matched.size
        )
    }

    suspend fun download(asset: PluginReleaseAsset): DownloadedPluginPackage = withContext(Dispatchers.IO) {
        if (asset.size > MAX_ARCHIVE_BYTES) {
            throw PluginIndexException("资产超过上限: ${asset.size}")
        }
        // 直链包（package_url）不带大小，size=0 时按上限约束；常规包按声明大小精确校验。
        val maxBytes = if (asset.size > 0) asset.size else MAX_ARCHIVE_BYTES
        val bytes = getBytes(
            listOf(mirrorUrl(asset.url), asset.url),
            maxBytes,
            noCache = true,
            expectedSize = asset.size.takeIf { it > 0 }
        )
        val file = File(context.cacheDir, "plugin-${sha256(bytes).take(16)}.zip")
        file.writeBytes(bytes)
        DownloadedPluginPackage(file, asset)
    }

    private fun assetName(id: String, version: String): String = "${id}_$version.zip"

    private fun assetVersion(id: String, name: String): String? {
        val prefix = "${id}_"
        if (!name.startsWith(prefix) || !name.endsWith(".zip")) return null
        val version = name.removePrefix(prefix).removeSuffix(".zip")
        return version.takeIf { !it.startsWith("v") && SemVer.parse(it) != null }
    }

    private fun parseGitHubRepo(repository: String): Pair<String, String>? {
        val uri = runCatching { URI(repository) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com") return null
        val segments = uri.path.trim('/').split('/')
        if (segments.size != 2 || segments.any { it.isBlank() }) return null
        return segments[0] to segments[1].removeSuffix(".git")
    }

    private inline fun <reified T> apiGet(url: String): T = apiGet(listOf(url))

    /** 按顺序尝试多个地址（镜像优先，直连兜底），全部失败抛最后一个地址的异常。 */
    private inline fun <reified T> apiGet(urls: List<String>): T {
        require(urls.isNotEmpty())
        var lastError: IOException? = null
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "aiverse-plugin-index")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw NoMatchingReleaseException("GitHub 请求失败: HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: throw NoMatchingReleaseException("GitHub 响应为空")
                    return try {
                        PluginIndexJson.decodeFromString<T>(body)
                    } catch (e: Exception) {
                        throw NoMatchingReleaseException("GitHub 响应解析失败")
                    }
                }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: NoMatchingReleaseException("所有地址均失败")
    }

    private fun getBytes(
        url: String,
        maxBytes: Long,
        noCache: Boolean,
        expectedSize: Long? = null
    ): ByteArray = getBytes(listOf(url), maxBytes, noCache, expectedSize)

    /**
     * 按顺序尝试 [urls]（首个常为镜像地址，其后为直连 GitHub），第一个成功的返回；
     * 全部失败则抛最后一个地址的异常。镜像返回 HTML 错误页或非 2xx 时状态码不为成功，
     * 自然落到下一个地址，与 [ApkDownloader] 的多地址回退约定一致。
     */
    private fun getBytes(
        urls: List<String>,
        maxBytes: Long,
        noCache: Boolean,
        expectedSize: Long? = null
    ): ByteArray {
        require(urls.isNotEmpty())
        var lastError: IOException? = null
        for (url in urls) {
            try {
                return getBytesSingle(url, maxBytes, noCache, expectedSize)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("所有地址均失败")
    }

    private fun getBytesSingle(
        url: String,
        maxBytes: Long,
        noCache: Boolean,
        expectedSize: Long? = null
    ): ByteArray {
        val builder = Request.Builder().url(url).header("User-Agent", "aiverse-plugin-index")
        if (noCache) builder.cacheControl(CacheControl.Builder().noCache().build())
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            val body = response.body ?: throw IOException("空响应: $url")
            val declared = body.contentLength()
            if (declared > maxBytes) throw PluginIndexException("响应超过大小限制: $url")
            if (expectedSize != null && declared > 0 && declared != expectedSize) {
                throw IOException("响应大小 $declared 与声明 $expectedSize 不一致")
            }
            val stream = body.byteStream()
            val output = java.io.ByteArrayOutputStream(if (declared in 1..maxBytes) declared.toInt() else 8192)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw PluginIndexException("响应超过大小限制: $url")
                output.write(buffer, 0, read)
            }
            if (expectedSize != null && total != expectedSize) {
                throw IOException("响应大小 $total 与声明 $expectedSize 不一致")
            }
            return output.toByteArray()
        }
    }

    private fun cacheFile(source: PluginIndexSource): File = File(File(cacheRoot, source.id), "index.json")

    private fun writeCache(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        temp.renameTo(file)
    }

    private fun readCache(file: File): PluginIndexDocument? {
        if (!file.isFile) return null
        return runCatching { PluginIndexParser.parse(file.readBytes()) }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Serializable
    private data class GitHubContent(
        val name: String,
        val path: String,
        val type: String,
        val size: Long = 0
    )

    private fun rawUrl(owner: String, repo: String, path: String): String =
        "$GITHUB_RAW/$owner/$repo/$DEFAULT_BRANCH/$path"

    /**
     * 把 GitHub 原始地址转成镜像加速地址（固定前缀 `https://gh.aivapp.cn/`，与设置里的镜像源无关）。
     * 镜像站把完整原始 URL 直接拼在域名后，如：
     * `https://gh.aivapp.cn/https://raw.githubusercontent.com/yhdmgf/aiverserules/main/index.json`。
     */
    private fun mirrorUrl(url: String): String = "$GITHUB_MIRROR$url"

    private companion object {
        const val GITHUB_API = "https://api.github.com"
        const val GITHUB_RAW = "https://raw.githubusercontent.com"
        /** GitHub 加速镜像前缀：优先走镜像，失败自动回退直连 GitHub。 */
        const val GITHUB_MIRROR = "https://gh.aivapp.cn/"
        const val DEFAULT_BRANCH = "main"
        const val PACKAGES_DIR = "packages"
        const val MAX_INDEX_BYTES = 2L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024
    }
}
