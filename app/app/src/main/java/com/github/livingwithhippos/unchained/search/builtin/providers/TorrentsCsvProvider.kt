package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/TorrentsCSV.kt`.
 *
 * JSON API: `{"torrents":[{"name","infohash","size_bytes","seeders","leechers","created_unix"}]}`
 */
object TorrentsCsvProvider : TorrentProvider {
    override val id = "torrentscsv"
    override val name = "TorrentsCSV"
    override val url = "https://torrents-csv.com"
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/service/search?q=${ProviderHttp.encode(query)}&size=100")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val torrents = JSONObject(body).optJSONArray("torrents") ?: return emptyList()
        (0 until torrents.length()).mapNotNull { index ->
            val item = torrents.optJSONObject(index) ?: return@mapNotNull null
            val infoHash = item.optString("infohash").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val title = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val bytes = item.optLong("size_bytes").takeIf { it > 0 }
            val magnet = Scrape.magnet(infoHash, title)
            ScrapedItem(
                name = title,
                link = magnet,
                seeders = item.count("seeders"),
                leechers = item.count("leechers"),
                size = Scrape.prettySize(bytes),
                addedDate = item.optLong("created_unix").takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = bytes?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private fun JSONObject.count(key: String): String? =
        if (has(key)) optInt(key).toString() else null
}
