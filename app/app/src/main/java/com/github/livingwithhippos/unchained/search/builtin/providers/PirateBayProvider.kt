package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.json.JSONArray

/**
 * Ported from prajwalch/TorrentSearch `providers/ThePirateBay.kt` (apibay JSON mirror).
 *
 * The upstream provider id is `thepiratebay`; we also recognize the old `piratebay` setting
 * when choosing its enabled state so existing users keep their preference.
 * JSON array: `[{"id","name","info_hash","seeders","leechers","size","added","category"}]`.
 * The site offers no sort parameter, so [sort] is applied to the results by the engine.
 */
object PirateBayProvider : TorrentProvider {
    override val id = "thepiratebay"
    override val name = "The Pirate Bay"
    override val url = "https://apibay.org"
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/q.php?q=${ProviderHttp.encode(query)}&cat=")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val infoHash = item.optString("info_hash").lowercase()
            // apibay answers a no-result search with a single dummy row.
            if (infoHash.length != 40 || item.optString("name") == "No results returned") {
                return@mapNotNull null
            }
            val title = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val bytes = item.optLong("size").takeIf { it > 0 }
            val magnet = Scrape.magnet(infoHash, title)
            val added = item.optLong("added").takeIf { it > 0 }
            ScrapedItem(
                name = title,
                link = magnet,
                seeders = item.optString("seeders").takeIf { it.isNotBlank() },
                leechers = item.optString("leechers").takeIf { it.isNotBlank() },
                size = Scrape.prettySize(bytes),
                addedDate = added?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = bytes?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }
}
