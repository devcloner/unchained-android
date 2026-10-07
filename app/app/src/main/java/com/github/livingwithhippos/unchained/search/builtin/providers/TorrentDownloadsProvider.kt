package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TorrentDownloads.kt` (HTML result list).
 *
 * Results live in the last `div.inner_container`; the first two rows of every container are
 * navigation/header chrome and are dropped. The magnet is only on the details page, so
 * [ScrapedItem.link] is the details URL and magnets stay empty.
 */
object TorrentDownloadsProvider : TorrentProvider {
    override val id = "torrentdownloads"
    override val name = "TorrentDownloads"
    override val url = "https://torrentdownloads.pro"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search/?s_cat=0&search=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val rows = Jsoup.parse(body).select(LIST_ITEM_CONTAINER).lastOrNull()
            ?.select(LIST_ITEM)
            ?.drop(2)
            .orEmpty()
        rows.mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = null,
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM_CONTAINER = "div.inner_container"
    private const val LIST_ITEM = "div.grey_bar3"
    private const val TORRENT_NAME = "p:nth-child(1) > a:nth-child(2)"
    private const val SIZE = "span:nth-child(5)"
    private const val SEEDERS = "span:nth-child(4)"
    private const val PEERS = "span:nth-child(3)"
}
