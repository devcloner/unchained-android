package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/LinuxTracker.kt` (HTML listing, Linux distros).
 *
 * The listing has no magnet, so the magnet is read from each result's details page (upstream does
 * the same lazily via `MagnetUriProvider`). The site offers no sort parameter.
 */
object LinuxTrackerProvider : TorrentProvider {
    override val id = "linuxtracker"
    override val name = "LinuxTracker"
    override val url = "https://linuxtracker.org"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/index.php?page=torrents&search=${ProviderHttp.encode(query)}&category=0&active=0"
        ) ?: return emptyList()
        return parse(body).map { item ->
            val detailsUrl = item.link ?: return@map item
            val detailsBody = http.getText(detailsUrl) ?: return@map item
            val magnet = magnetFromDetails(detailsBody) ?: return@map item
            item.copy(magnets = listOf(magnet))
        }
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
                ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
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

    /** Magnet link of a details page, or null when it cannot be read. */
    internal fun magnetFromDetails(body: String): String? = runCatching {
        Jsoup.parse(body).selectFirst(MAGNET_URI)?.attr("href")?.takeIf { it.startsWith("magnet:") }
    }.getOrNull()

    private const val LIST_ITEM = "table.torrent-table > tbody > tr"
    private const val TORRENT_NAME = "td.torrent-name-cell > a.torrent-name"
    private const val SIZE = "td:nth-child(4)"
    private const val SEEDERS = "td.seeds > strong"
    private const val PEERS = "td.leeches > strong"
    private const val UPLOAD_DATE = "td:nth-child(3)"
    private const val MAGNET_URI = """a[href^="magnet:?"]"""
}
