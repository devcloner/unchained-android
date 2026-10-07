package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TorrentDownload.kt` (HTML table).
 *
 * The result rows carry the info hash inside the details URL path
 * (`/A1425E0D…/Ubuntu-10-04-LTS-x64`), so a magnet is built without a second request.
 */
object TorrentDownloadProvider : TorrentProvider {
    override val id = "torrentdownloadinfo"
    override val name = "TorrentDownload"
    override val url = "https://torrentdownload.info"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val document = Jsoup.parse(body)
        if (document.selectFirst("h3")?.ownText()?.contains("No Results") == true) {
            return emptyList()
        }
        document.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href")) ?: return@mapNotNull null
            // `/A1425E0D…/Ubuntu-10-04-LTS-x64` -> the first path segment is the info hash.
            val infoHash = detailsUrl
                .dropLastWhile { it != '/' }
                .dropLast(1)
                .takeLastWhile { it != '/' }
                .trim()
                .lowercase()
                .takeIf { it.length == 40 }
                ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
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

    private const val LIST_ITEM = "table.table2 > tbody > tr:has(span.smallish)"
    private const val TORRENT_NAME = "td:nth-child(1) > div.tt-name > a"
    private const val SIZE = "td:nth-child(3)"
    private const val SEEDERS = "td:nth-child(4)"
    private const val PEERS = "td:nth-child(5)"
    private const val UPLOAD_DATE = "td:nth-child(2)"
}
