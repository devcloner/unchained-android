package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/XXXClub.kt` (adult torrent tracker).
 *
 * Search: `/torrents/search/all/<query>`. Rows are list items inside a browse table; the magnet is
 * only on the details page, so rows carry the details link and an empty magnet list. The site
 * offers no sort parameter, so [sort] is applied by the engine.
 */
object XxxClubProvider : TorrentProvider {
    override val id = "xxxclub"
    override val name = "XXXClub"
    override val url = "https://xxxclub.to"
    override val group = ProviderGroup.NSFW
    override val nsfw = true
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/torrents/search/all/${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val container = Jsoup.parse(body).selectFirst(LIST_ITEM_CONTAINER) ?: return emptyList()
        container.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(DETAILS_PAGE_URL) ?: return@mapNotNull null
            val title = anchor.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")),
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

    private const val LIST_ITEM_CONTAINER = "div.browsetableinside, div.divtableinside"
    private const val LIST_ITEM = "ul > li"
    private const val NAME = """span:nth-child(2) > a[href^="/torrents/details"]"""
    private const val DETAILS_PAGE_URL = NAME
    private const val SIZE = "span.siz"
    private const val SEEDERS = "span.see"
    private const val PEERS = "span.lee"
    private const val UPLOAD_DATE = "span.adde"
}
