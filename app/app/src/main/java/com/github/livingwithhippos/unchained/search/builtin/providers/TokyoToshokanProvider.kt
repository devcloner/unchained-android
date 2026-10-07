package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/TokyoToshokan.kt` (HTML listing).
 *
 * Every hit is split over two table rows (name/magnet, then size/date/counters), so the rows are
 * paired with `zipWithNext` exactly as upstream does. `type=0` is the "All" category. The site
 * offers no sort parameter.
 */
object TokyoToshokanProvider : TorrentProvider {
    override val id = "tokyotoshokan"
    override val name = "TokyoToshokan"
    override val url = "https://tokyotosho.info"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/search.php?terms=${ProviderHttp.encode(query)}&type=0&searchName=true"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).zipWithNext().mapNotNull { (tr1, tr2) ->
            val anchor = tr1.selectFirst(NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnetUri = tr1.selectFirst(MAGNET_URI)?.attr("href")
                ?.takeIf { it.startsWith("magnet:") }
            val magnet = Scrape.infoHashFrom(magnetUri)?.let { Scrape.magnet(it, title) } ?: magnetUri

            // "Category | Size: 1.4 GB | Date: 2024-05-01 12:00 UTC" — drop the leading label token.
            val info = tr2.selectFirst(SIZE_AND_UPLOAD_DATE)?.ownText()
                ?.split('|')
                ?.drop(1)
                ?.map { it.trim().dropWhile { ch -> !ch.isWhitespace() }.trim() }
                .orEmpty()
            val size = info.getOrNull(0)?.takeIf { it.isNotEmpty() }
            val rawDate = info.getOrNull(1)

            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, tr1.selectFirst(DETAILS_PAGE_URL)?.attr("href")) ?: magnetUri,
                seeders = tr2.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = tr2.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(rawDate),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "table.listing > tbody > tr:nth-child(n+2)"
    private const val NAME = "td.desc-top > a:nth-child(2)"
    private const val SIZE_AND_UPLOAD_DATE = "td.desc-bot"
    private const val SEEDERS = "td.stats > span:nth-child(1)"
    private const val PEERS = "td.stats > span:nth-child(2)"
    private const val MAGNET_URI = "td.desc-top > a:nth-child(1)"
    private const val DETAILS_PAGE_URL = "td.web > a:last-child"
}
