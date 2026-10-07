package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/InternetArchive.kt` (advancedsearch JSON API).
 *
 * Response shape: `{"response":{"docs":[{"title","item_size","publicdate","identifier","btih"}]}}`.
 * Docs without a `btih` have no torrent and are skipped. The API accepts a `sort[]` parameter but
 * upstream does not send one.
 */
object InternetArchiveProvider : TorrentProvider {
    override val id = "internetarchive"
    override val name = "InternetArchive"
    override val url = "https://archive.org"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/advancedsearch.php?q=title:${ProviderHttp.encode(query)}" +
                "&fl%5B%5D=title,item_size,publicdate,mediatype,identifier,btih" +
                "&rows=100&page=1&output=json"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs")
            ?: return emptyList()
        (0 until docs.length()).mapNotNull { index ->
            val doc = docs.optJSONObject(index) ?: return@mapNotNull null
            val title = doc.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val infoHash = doc.optString("btih").lowercase().trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val magnet = Scrape.magnet(infoHash, title)
            val identifier = doc.optString("identifier").takeIf { it.isNotBlank() }
            val bytes = doc.optLong("item_size").takeIf { it > 0 }
            ScrapedItem(
                name = title,
                link = identifier?.let { "$url/details/$it" } ?: magnet,
                size = Scrape.prettySize(bytes),
                addedDate = Scrape.parseDate(doc.optString("publicdate")),
                parsedSize = bytes?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }
}
