package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/AniRena.kt` (HTML table results page).
 *
 * The search page only exposes a magnet-redirect URL (`.../magnet`), not the magnet itself, so the
 * result links to the details page and [ScrapedItem.magnets] stays empty until that page is opened.
 */
object AniRenaProvider : TorrentProvider {
    override val id = "anirena"
    override val name = "AniRena"
    override val url = "https://anirena.com"
    override val group = ProviderGroup.ANIME
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url?q=${ProviderHttp.encode(query)}&page=1&cat=anime")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            val added = row.attr("data-created-ts").takeIf { it.isNotBlank() }?.toLongOrNull()
            val magnet = row.selectFirst(MAGNET_URI_SRC_URL)
                ?.attr("href")
                ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")),
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = added?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table.tl-table > tbody > tr"
    private const val TORRENT_NAME = "td.col-name > div.tl-name-wrap > a.tl-torrent-name"
    private const val SIZE = "td.col-size"
    private const val SEEDERS = "td.col-se > span.tl-se"
    private const val PEERS = "td.col-le > span.tl-le"
    private const val MAGNET_URI_SRC_URL = "td.col-actions > div.tl-actions > a:nth-child(1)"
}
