package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TorrentKitty.kt` (HTML result table).
 *
 * The result table carries the magnet link directly, so the infohash is parsed out of it and a
 * magnet is emitted; [ScrapedItem.link] keeps the details page URL.
 */
object TorrentKittyProvider : TorrentProvider {
    override val id = "torrentkitty"
    override val name = "TorrentKitty"
    override val url = "https://torrentkitty.tv"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search/${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).drop(1).mapNotNull { row ->
            val title = row.selectFirst(TORRENT_NAME)?.ownText()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnetUri = row.selectFirst(MAGNET_URI)?.attr("href")
                ?.takeIf { it.startsWith("magnet:") }
            val magnet = magnetUri?.let { Scrape.infoHashFrom(it) }?.let { Scrape.magnet(it, title) }
                ?: magnetUri
            val detailsUrl = Scrape.resolve(url, row.selectFirst(DETAILS_PAGE_URL)?.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.uppercase()?.takeIf { it.isNotBlank() }
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table#archiveResult > tbody > tr"
    private const val TORRENT_NAME = "td.name"
    private const val SIZE = "td.size"
    private const val UPLOAD_DATE = "td.date"
    private const val MAGNET_URI = "td.action > a:nth-child(2)"
    private const val DETAILS_PAGE_URL = "td.action > a:nth-child(1)"
}
