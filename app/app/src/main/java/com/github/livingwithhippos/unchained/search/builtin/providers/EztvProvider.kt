package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Eztv.kt` (HTML result table, series only).
 *
 * Search is `GET /search/<query>`. Upstream sends a `layout=def_wlinks` cookie so magnets are
 * included; the current [ProviderHttp] contract has no cookie parameter, so magnets are taken when
 * the response exposes them and left empty otherwise (the details link is always kept).
 */
object EztvProvider : TorrentProvider {
    override val id = "eztv"
    override val name = "Eztv"
    override val url = "https://eztvx.to"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search/${ProviderHttp.encode(query)}", referer = "$url/")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).drop(2).mapNotNull { row ->
            val nameElement = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = nameElement.ownText().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_URI)?.attr("href")
                ?.takeIf { it.startsWith("magnet:") }
            val detailsUrl = Scrape.resolve(url, nameElement.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = null,
                size = size,
                addedDate = null,
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table:last-of-type > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(2) > a.epinfo"
    private const val SIZE = "td:nth-child(4)"
    private const val SEEDERS = "td:nth-child(6)"
    private const val MAGNET_URI = "td:nth-child(3) > a.magnet"
}
