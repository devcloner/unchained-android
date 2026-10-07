package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/NoNameClub.kt` (POST form -> HTML result table).
 *
 * `POST /forum/tracker.php` with `o` as the sort criteria (`10` seeders, `2` topic title), so
 * [supportsServerSort] is true. The list page exposes the details page and a `.torrent` download
 * link but no magnet, hence [ScrapedItem.magnets] is empty and the link points at the details page.
 */
object NoNameClubProvider : TorrentProvider {
    override val id = "nonameclub"
    override val name = "NoNameClub"
    override val url = "https://nnmclub.to"
    override val enabledByDefault = false
    override val supportsServerSort = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val sortCriteria = if (sort == TorrentSort.SEEDERS) "10" else "2"
        val form = listOf(
            "f[]" to "-1",
            "o" to sortCriteria,
            "s" to "2",
            "tm" to "-1",
            "shf" to "1",
            "ta" to "-1",
            "sns" to "-1",
            "sds" to "4",
            "nm" to query,
            "submit" to "Поиск",
        ).joinToString("&") { (key, value) -> "${ProviderHttp.encode(key)}=${ProviderHttp.encode(value)}" }
        val body = http.postText("$url/forum/tracker.php", form, referer = "$url/forum/tracker.php")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val nameElement = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val detailsElement = row.selectFirst(DETAILS_PAGE_URL) ?: return@mapNotNull null
            val title = nameElement.ownText().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, detailsElement.attr("href")) ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = row.selectFirst(UPLOAD_DATE)?.ownText()?.trim()?.toLongOrNull()
                    ?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM =
        """table.forumline.tablesorter > tbody > tr:has(a[href^="viewtopic.php?t="]):has(a[href^="download.php?id="])"""
    private const val TORRENT_NAME = """a[href^="viewtopic.php?t="] > b"""
    private const val DETAILS_PAGE_URL = """a[href^="viewtopic.php?t="]"""
    private const val SIZE = "td:nth-child(6) > u"
    private const val SEEDERS = "td.seedmed > b"
    private const val PEERS = "td.leechmed > b"
    private const val UPLOAD_DATE = "td:last-child > u"
}
