package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Torrent9.kt` (French HTML table).
 *
 * Sizes are written in French units ("1.4 Go"), so `o` is turned into `B` before parsing.
 * The infohash lives on the details page only, so magnets stay empty.
 */
object Torrent9Provider : TorrentProvider {
    override val id = "torrent9"
    override val name = "Torrent9"
    override val url = "https://www6.torrent9.to"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search_torrent/${ProviderHttp.encode(query)}.html")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.replace('o', 'B')?.let(::normalizeSize)
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

    private const val LIST_ITEM = "table > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(1) > a"
    private const val SIZE = "td:nth-child(3)"
    private const val SEEDERS = "td:nth-child(4) > span.seed_ok"
    private const val PEERS = "td:nth-child(5)"
    private const val UPLOAD_DATE = "td:nth-child(2)"
}

/** "1.4GB" -> "1.4 GB" so that [Scrape.parseSize] can read the unit. */
private fun normalizeSize(size: String): String = buildString {
    for (index in size.indices) {
        append(size[index])
        if (index < size.length - 1 && size[index].isDigit() && size[index + 1].isLetter()) {
            append(' ')
        }
    }
}
