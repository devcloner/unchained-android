package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/ThirteenThirtySevenX.kt` (HTML table).
 *
 * The list page carries no infohash or magnet link, so [ScrapedItem.link] points at the details
 * page the engine can follow; magnets stay empty. The site exposes no sort parameter in the
 * search URL, so [sort] is applied to the results by the engine.
 */
object ThirteenThirtySevenXProvider : TorrentProvider {
    override val id = "1337x"
    override val name = "1337x"
    override val url = "https://1337x.to"
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search/${ProviderHttp.encode(query)}/1/")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.filter { it != ',' }?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table.table-list > tbody > tr"
    private const val TORRENT_NAME = "td.name > a:nth-child(2)"
    private const val SIZE = "td.size"
    private const val SEEDERS = "td.seeds"
    private const val PEERS = "td.leeches"
    private const val UPLOAD_DATE = "td.coll-date"
}
