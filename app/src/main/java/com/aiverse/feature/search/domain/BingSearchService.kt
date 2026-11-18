package com.aiverse.feature.search.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

/** Bing 网页搜索（本地抓取 bing.com 结果页，无需 API Key）。 */
object BingSearchService : SearchService<SearchServiceOptions.BingLocalOptions> {
    override val name: String = "Bing"

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            val locale = Locale.getDefault()
            val acceptLanguage = "${locale.language}-${locale.country},${locale.language}"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", acceptLanguage)
                .header("Accept-Encoding", "gzip, deflate, sdch")
                .header("Accept-Charset", "utf-8")
                .header("Connection", "keep-alive")
                .referrer("https://www.bing.com/")
                .cookie("SRCHHPGUSR", "ULSR=1")
                .timeout(5000)
                .get()

            val results = doc.select("li.b_algo").map { element ->
                val title = element.select("h2").text()
                val link = element.select("h2 > a").attr("href")
                val snippet = element.select(".b_caption p").text()
                SearchResult.SearchResultItem(
                    title = title,
                    url = link,
                    text = snippet
                )
            }

            require(results.isNotEmpty()) {
                "Search failed: no results found"
            }

            // Bing 对纯 HTTP 抓取常返回与查询无关的反爬诱饵页（如搜 DeepSeek 却返回 Google/知乎）。
            // 若结果与查询词毫无交集，判定为降级/反爬页，抛错让上层回退到可用的搜索后端。
            val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                val haystack = results.joinToString(" ") { "${it.title} ${it.text}" }.lowercase()
                if (tokens.none { haystack.contains(it) }) {
                    error("Bing returned results unrelated to the query (likely anti-bot page)")
                }
            }

            SearchResult(items = results)
        }
    }
}
