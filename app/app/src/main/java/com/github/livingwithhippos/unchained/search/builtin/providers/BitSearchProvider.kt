package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/BitSearch.kt` (HTML result list).
 *
 * The site only documents `sortBy=seeders`, which is always requested; the requested [sort] is
 * therefore applied to the results locally by the engine (server sort is not claimed).
 */
object BitSearchProvider : TorrentProvider {
    override val id = "bitsearch"
    override val name = "BitSearch"
    override val url = "https://bitsearch.to"
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}&page=1&sortBy=seeders")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val nameElement = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = nameElement.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_LINK)?.attr("href")
                ?.takeIf { it.startsWith("magnet:") } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, row.selectFirst(DETAILS_PAGE_URL)?.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = dateFrom(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** BitSearch dates are `M/d/yyyy`; a few cards show a relative "x ago" string instead. */
    private fun dateFrom(text: String?): String? {
        val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = raw.split('/')
        if (parts.size == 3) {
            val month = parts[0].toIntOrNull()
            val day = parts[1].toIntOrNull()
            val year = parts[2].toIntOrNull()
            if (month != null && day != null && year != null) {
                return Scrape.parseDate(
                    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                )
            }
        }
        return Scrape.parseDate(raw)
    }

    private const val LIST_ITEM = "div.space-y-4 > div > div:nth-child(1)"
    private const val TORRENT_INFO = "> div:nth-child(1)"
    private const val DOWNLOAD_LINKS = "> div:nth-child(2)"
    private const val CATEGORY_AND_METADATA = "$TORRENT_INFO > div:nth-last-child(2)"
    private const val SWARM_STATS = "$TORRENT_INFO > div:nth-last-child(1)"
    private const val TORRENT_NAME = "$TORRENT_INFO h3"
    private const val SIZE = "$CATEGORY_AND_METADATA > span:nth-child(2) > span"
    private const val SEEDERS = "$SWARM_STATS > span:nth-child(1) > span:nth-child(2)"
    private const val PEERS = "$SWARM_STATS > span:nth-child(2) > span:nth-child(2)"
    private const val UPLOAD_DATE = "$CATEGORY_AND_METADATA > span:nth-child(3) > span"
    private const val MAGNET_LINK = "$DOWNLOAD_LINKS > a:nth-child(2)"
    private const val DETAILS_PAGE_URL = "$TORRENT_NAME > a"
}
