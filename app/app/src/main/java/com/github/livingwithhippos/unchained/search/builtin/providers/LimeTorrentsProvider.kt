package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/LimeTorrents.kt` (HTML table).
 *
 * The site accepts `date` or `seeds` in the sort slot of the search URL, so [sort] is mapped
 * server side and the engine also re-orders locally for the other criteria.
 */
object LimeTorrentsProvider : TorrentProvider {
    override val id = "limetorrents"
    override val name = "LimeTorrents"
    override val url = "https://limetorrents.fun"
    override val enabledByDefault = true
    override val supportsServerSort = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val order = if (sort == TorrentSort.SEEDERS) "seeds" else "date"
        val body = http.getText("$url/search/all/${ProviderHttp.encode(query)}/$order/1/")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val document = Jsoup.parse(body)
        document.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val hash = Scrape.infoHashFrom(row.selectFirst(FILE_DOWNLOAD_LINK)?.attr("href"))
            val magnet = hash?.let { Scrape.magnet(it, title) }
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE_AND_CATEGORY)?.ownText()?.substringBefore("- in")),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = ".table2 > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(1) > div.tt-name > a:nth-child(2)"
    private const val SIZE = "td:nth-child(3)"
    private const val SEEDERS = "td.tdseed"
    private const val PEERS = "td.tdleech"
    private const val UPLOAD_DATE_AND_CATEGORY = "td:nth-child(2)"
    private const val FILE_DOWNLOAD_LINK = "td:nth-child(1) > div.tt-name > a:nth-child(1)"
}
