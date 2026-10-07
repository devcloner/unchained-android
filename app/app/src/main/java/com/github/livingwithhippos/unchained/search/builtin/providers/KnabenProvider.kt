package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/Knaben.kt` (official JSON API).
 *
 * POST `https://api.knaben.org/v1` with `{query,size,order_by,order_direction,hide_unsafe,hide_xxx}`
 * returns `{"hits":[{"title","hash","magnetUrl","bytes","seeders","peers","date","details","id"}]}`.
 * The API accepts `order_by` (`seeders` / `date`), so [supportsServerSort] is true.
 */
object KnabenProvider : TorrentProvider {
    override val id = "knaben"
    override val name = "Knaben"
    override val url = "https://knaben.org"
    override val enabledByDefault = true
    override val supportsServerSort = true

    private const val API = "https://api.knaben.org/v1"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val orderBy = if (sort == TorrentSort.DATE) "date" else "seeders"
        val payload = JSONObject()
            .put("query", query)
            .put("size", 300)
            .put("order_by", orderBy)
            .put("order_direction", "desc")
            .put("hide_unsafe", true)
            .put("hide_xxx", false)
            .toString()
        val body = http.postText(API, payload, "application/json") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
        (0 until hits.length()).mapNotNull { index ->
            val item = hits.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = item.optString("magnetUrl").takeIf { it.isNotBlank() }
                ?: Scrape.infoHashFrom(item.optString("hash"))?.let { Scrape.magnet(it, title) }
                ?: return@mapNotNull null
            val details = item.optString("details").takeIf { it.isNotBlank() }
            val bytes = item.optLong("bytes").takeIf { it > 0 }
            ScrapedItem(
                name = title,
                link = details ?: magnet,
                seeders = item.numberText("seeders"),
                leechers = item.numberText("peers"),
                size = Scrape.prettySize(bytes),
                addedDate = item.optString("date").takeIf { it.isNotBlank() }?.let { raw ->
                    runCatching { Instant.parse(raw).toString() }.getOrNull() ?: Scrape.parseDate(raw)
                },
                parsedSize = bytes?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private fun JSONObject.numberText(key: String): String? =
        if (has(key) && !isNull(key)) optInt(key).toString() else null
}
