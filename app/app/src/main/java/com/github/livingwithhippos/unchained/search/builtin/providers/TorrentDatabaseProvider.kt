package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TorrentDatabase.kt` (HTML table, Cloudflare fronted).
 *
 * The magnet link (`/track/magnet/<hash>`) is embedded in the result row, so no details fetch is
 * needed to build a usable magnet.
 */
object TorrentDatabaseProvider : TorrentProvider {
    override val id = "torrentdatabase"
    override val name = "TorrentDatabase"
    override val url = "https://developify.ca"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val nameEl = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = nameEl.ownText().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val infoHash = nameEl
                .attr("href")
                .removePrefix("/track/magnet/")
                .takeWhile { it != '?' }
                .trim()
                .lowercase()
                .takeIf { it.length == 40 }
                ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, row.selectFirst(DESCRIPTION_PAGE_URL)?.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: Scrape.magnet(infoHash, title),
                seeders = row.selectFirst(SEEDERS)?.ownText()?.filter { it.isDigit() },
                leechers = row.selectFirst(PEERS)?.ownText()?.filter { it.isDigit() },
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(Scrape.magnet(infoHash, title)),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table.torrent-table > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(1) > a:nth-child(2)"
    private const val SIZE = "td.size-cell"
    private const val SEEDERS = "td:nth-child(5) > div > span:nth-child(1)"
    private const val PEERS = "td:nth-child(5) > div > span:nth-child(3)"
    private const val UPLOAD_DATE = "td.date-cell"
    private const val DESCRIPTION_PAGE_URL = "td:nth-child(1) > a:nth-child(1)"
}
