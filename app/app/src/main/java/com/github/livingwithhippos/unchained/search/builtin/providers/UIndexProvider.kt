package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/UIndex.kt` (HTML table, Cloudflare fronted).
 *
 * The result rows expose both a details link and a direct magnet link, so a magnet is available
 * without a second request.
 */
object UIndexProvider : TorrentProvider {
    override val id = "uindex"
    override val name = "UIndex"
    override val url = "https://uindex.org"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search.php?search=${ProviderHttp.encode(query)}&c=0")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val container = Jsoup.parse(body).selectFirst(LIST_ITEM_CONTAINER) ?: return emptyList()
        container.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(NAME) ?: return@mapNotNull null
            val title = anchor.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_URI)?.attr("href")?.trim()?.takeIf { it.isNotBlank() }
            val infoHash = Scrape.infoHashFrom(magnet)
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.filter { it.isDigit() },
                leechers = row.selectFirst(PEERS)?.ownText()?.filter { it.isDigit() },
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(
                    magnet ?: infoHash?.let { Scrape.magnet(it, title) },
                ),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM_CONTAINER = "table.sr-table, table.top-table"
    private const val LIST_ITEM = "tbody > tr"
    private const val NAME = "td.sr-col-name > a.sr-torrent-link"
    private const val SIZE = "td.sr-col-size"
    private const val SEEDERS = "td.sr-col-seeders > span.sr-seed"
    private const val PEERS = "td.sr-col-leechers > span.sr-leech"
    private const val UPLOAD_DATE = "td.sr-col-uploaded"
    private const val MAGNET_URI = "td.sr-col-name > a.sr-magnet"
}
