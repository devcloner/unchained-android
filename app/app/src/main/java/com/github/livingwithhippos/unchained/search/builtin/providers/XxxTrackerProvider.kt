package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/XXXTracker.kt` (adult torrent tracker, xxxtor).
 *
 * Search: `/b.php?search=<query>`. The results table's first row is a header and is dropped; every
 * data row exposes its magnet inline, so the row carries `link` = details page and the magnet.
 * The site offers no sort parameter, so [sort] is applied by the engine.
 */
object XxxTrackerProvider : TorrentProvider {
    override val id = "xxxtracker"
    override val name = "XXXTracker"
    override val url = "https://xxxtor.com"
    override val group = ProviderGroup.NSFW
    override val nsfw = true
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/b.php?search=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body)
            .select(LIST_ITEM)
            // The first row is a header.
            .drop(1)
            .mapNotNull { row ->
                val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
                val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val hash = Scrape.infoHashFrom(row.selectFirst(MAGNET_URI)?.attr("href"))
                val magnet = hash?.let { Scrape.magnet(it, title) }
                val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
                ScrapedItem(
                    name = title,
                    link = Scrape.resolve(url, row.selectFirst(DETAILS_PAGE_URL)?.attr("href"))
                        ?: magnet,
                    seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                    leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
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

    private const val LIST_ITEM = "table > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(2) > a:nth-child(3)"
    private const val DETAILS_PAGE_URL = TORRENT_NAME
    private const val SIZE = "td:nth-last-child(2)"
    private const val SEEDERS = "td:nth-last-child(1) > span.green"
    private const val PEERS = "td:nth-last-child(1) > span.red"
    private const val UPLOAD_DATE = "td:nth-child(1)"
    private const val MAGNET_URI = "td:nth-child(2) > a:nth-child(1)"
}
