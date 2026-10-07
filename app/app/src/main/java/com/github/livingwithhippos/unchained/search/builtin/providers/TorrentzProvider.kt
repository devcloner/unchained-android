package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Torrentz.kt` (HTML description list).
 *
 * Each result is a `div.results > dl` item. The infohash is only available on the details page,
 * so [ScrapedItem.link] is the details URL and magnets stay empty.
 */
object TorrentzProvider : TorrentProvider {
    override val id = "torrentz"
    override val name = "Torrentz"
    override val url = "https://torrentz2.nz"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.attr("title")),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "div.results > dl"
    private const val TORRENT_NAME = "dt > a"
    private const val SIZE = "dd > span.s"
    private const val SEEDERS = "dd > span.u"
    private const val PEERS = "dd > span.d"
    private const val UPLOAD_DATE = "dd > span.a > span"
}
