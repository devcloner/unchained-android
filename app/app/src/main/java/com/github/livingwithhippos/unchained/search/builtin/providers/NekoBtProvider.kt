package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/NekoBT.kt` (HTML result table).
 *
 * Search is `GET /search?query=...`; the site offers no sort parameter on that endpoint, so [sort]
 * is applied to the results locally by the engine.
 */
object NekoBtProvider : TorrentProvider {
    override val id = "nekobt"
    override val name = "NekoBT"
    override val url = "https://nekobt.to"
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?query=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val nameElement = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = nameElement.selectFirst("span > span:nth-child(1)")?.ownText()?.trim()
                ?: nameElement.ownText().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_URI)?.attr("href")
                ?.takeIf { it.startsWith("magnet:") } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, nameElement.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table.table > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(3) > div:nth-child(1) > div > a"
    private const val SIZE = "td:nth-child(5) > span"
    private const val SEEDERS = "td:nth-child(7) > span"
    private const val PEERS = "td:nth-child(8) > span"
    private const val UPLOAD_DATE = "td:nth-child(6) > span"
    private const val MAGNET_URI = "td:nth-child(4) > div > a:nth-child(1)"
}
