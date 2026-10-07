package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/FileMood.kt` (HTML table).
 *
 * The info hash is the trailing hex token of the details URL (`...-<hash>.html`), so a magnet is
 * available directly from the result list.
 */
object FileMoodProvider : TorrentProvider {
    override val id = "filemood"
    override val name = "FileMood"
    override val url = "https://filemood.com"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/result?q=${ProviderHttp.encode(query)}+in%3Atitle")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val title = row.selectFirst(TORRENT_NAME)?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, row.selectFirst(DETAILS_PAGE_URL)?.attr("href"))
                ?: return@mapNotNull null
            val infoHash = detailsUrl
                .removeSuffix(".html")
                .takeLastWhile { it != '-' }
                .trim()
                .lowercase()
                .takeIf { it.length == 40 }
                ?: return@mapNotNull null
            val seedersPeers = row.selectFirst(SEEDERS_PEERS)?.text()?.trim()?.split('/')
            val size = row.selectFirst(SIZE)?.text()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = seedersPeers?.getOrNull(0)?.filter { it.isDigit() },
                leechers = seedersPeers?.getOrNull(1)?.filter { it.isDigit() },
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = null,
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(Scrape.magnet(infoHash, title)),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table > tbody > tr:has(a.btn-success)"
    private const val TORRENT_NAME = "td.dn-title"
    private const val SIZE = "td.dn-size"
    private const val SEEDERS_PEERS = "td.dn-status"
    private const val DETAILS_PAGE_URL = "td.dn-btn > div > a"
}
