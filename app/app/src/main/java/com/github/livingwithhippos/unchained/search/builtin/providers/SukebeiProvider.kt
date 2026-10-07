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
 * Ported from prajwalch/TorrentSearch `providers/Sukebei.kt` (HTML table, all categories `0_0`).
 *
 * Adult sibling of Nyaa: only searched when the global NSFW switch is on. The site accepts `s`/`o`
 * sort parameters, so [sort] is mapped server side for the criteria it supports.
 */
object SukebeiProvider : TorrentProvider {
    override val id = "sukebeinyaa"
    override val name = "Sukebei"
    override val url = "https://sukebei.nyaa.si"
    override val group = ProviderGroup.ANIME
    override val nsfw = true
    override val enabledByDefault = false
    override val supportsServerSort = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val order = when (sort) {
            TorrentSort.SEEDERS -> "&s=seeders&o=desc"
            TorrentSort.SIZE -> "&s=size&o=desc"
            TorrentSort.DATE -> "&s=id&o=desc"
            else -> ""
        }
        val body = http.getText("$url/?f=0&c=0_0&q=${ProviderHttp.encode(query)}$order")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_URI)
                ?.attr("href")
                ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            val added = row.selectFirst(UPLOAD_DATE)?.attr("data-timestamp")?.toLongOrNull()
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

    private const val LIST_ITEM = "table.torrent-list > tbody > tr"
    private const val NAME = "td:nth-child(2) > a:not(.comments)"
    private const val SIZE = "td:nth-child(4)"
    private const val SEEDERS = "td:nth-child(6)"
    private const val PEERS = "td:nth-child(7)"
    private const val UPLOAD_DATE = "td:nth-child(5)"
    private const val MAGNET_URI = "td:nth-child(3) > a:nth-child(2)"
}
