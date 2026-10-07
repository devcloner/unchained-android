package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Bt4g.kt` (HTML result list).
 *
 * The title link points at the `/magnet/<hash>` page, so the infohash can be read straight from
 * the href; when it is missing the magnet list stays empty rather than failing the row.
 */
object Bt4gProvider : TorrentProvider {
    override val id = "bt4g"
    override val name = "BT4G"
    override val url = "https://bt4gprx.com"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/search?q=${ProviderHttp.encode(query)}&category=all&orderby=seeders&p=1"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val infoHash = Scrape.infoHashFrom(anchor.attr("href"))
            val magnet = infoHash?.let { Scrape.magnet(it, title) }
            val size = row.selectFirst(SIZE)?.ownText()?.let(::normalizeSize)
            val date = row.selectFirst(UPLOAD_DATE)?.ownText()?.removePrefix("Creation Time:")?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(date),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "div.notion-list-item"
    private const val TORRENT_NAME = "div.notion-list-item-title > a"
    private const val SIZE = "div.notion-list-item-meta > span:nth-child(5) > b"
    private const val SEEDERS = "div.notion-list-item-meta span#seeders"
    private const val PEERS = "div.notion-list-item-meta span#leechers"
    private const val UPLOAD_DATE = "div.notion-list-item-meta > span:nth-child(2)"
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
