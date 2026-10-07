package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/BangumiMoe.kt` (JSON API, POST search).
 *
 * `POST /api/v2/torrent/search` with `{"query": "..."}` answers
 * `{"torrents": [{"_id","title","magnet","size","seeders","leechers","publish_time"}]}`.
 */
object BangumiMoeProvider : TorrentProvider {
    override val id = "bangumimoe"
    override val name = "BangumiMoe"
    override val url = "https://bangumi.moe"
    override val group = ProviderGroup.ANIME
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.postText(
            url = "$url/api/v2/torrent/search",
            body = """{"query":${JSONObject.quote(query)}}""",
            contentType = "application/json",
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val torrents = JSONObject(body).optJSONArray("torrents") ?: return emptyList()
        (0 until torrents.length()).mapNotNull { index ->
            val item = torrents.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = item.optString("magnet").takeIf { it.startsWith("magnet:") }
            val infoHash = item.optString("infohash").takeIf { it.isNotBlank() }
                ?: Scrape.infoHashFrom(magnet)
            val rawSize = item.optString("size").takeIf { it.isNotBlank() }
            val bytes = rawSize?.toLongOrNull() ?: Scrape.parseSize(rawSize)
            val id = item.optString("_id").takeIf { it.isNotBlank() }
            val resolvedMagnet = magnet ?: infoHash?.let { Scrape.magnet(it, title) }
            ScrapedItem(
                name = title,
                link = id?.let { Scrape.resolve(url, "/torrent/$it") },
                seeders = item.optInt("seeders").takeIf { item.has("seeders") }?.toString(),
                leechers = item.optInt("leechers").takeIf { item.has("leechers") }?.toString(),
                size = Scrape.prettySize(bytes) ?: rawSize,
                addedDate = Scrape.parseDate(item.optString("publish_time")),
                parsedSize = bytes?.toDouble(),
                magnets = listOfNotNull(resolvedMagnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }
}
