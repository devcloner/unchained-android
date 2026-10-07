package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TheRarBg.kt` (HTML result table).
 *
 * Search is `GET /get-posts/keywords:<query>`. The list page exposes only the details page (the
 * magnet lives behind it), so [ScrapedItem.magnets] stays empty and [ScrapedItem.link] points at
 * the details page. The site offers no documented sort parameter on this endpoint.
 */
object TheRarBgProvider : TorrentProvider {
    override val id = "therarbag"
    override val name = "TheRarBg"
    override val url = "https://therarbg.com"
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/get-posts/keywords:$query") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val nameElement = row.selectFirst(NAME) ?: return@mapNotNull null
            val title = nameElement.ownText().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, nameElement.attr("href")) ?: return@mapNotNull null
            val bytes = row.selectFirst(SIZE)?.attr("data-order")?.toLongOrNull()?.takeIf { it > 0 }
            val size = Scrape.prettySize(bytes) ?: row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = row.selectFirst(UPLOAD_DATE)?.attr("data-order")?.toLongOrNull()
                    ?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = bytes?.toDouble() ?: Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table > tbody > tr.list-entry"
    private const val NAME = "td.cellName > div > a"
    private const val SIZE = "td.sizeCell"
    private const val SEEDERS = "td:nth-child(7)"
    private const val PEERS = "td:nth-child(8)"
    private const val UPLOAD_DATE = "td:nth-child(4)"
}
