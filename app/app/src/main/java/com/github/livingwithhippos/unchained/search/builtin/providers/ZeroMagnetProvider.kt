package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/ZeroMagnet.kt` (HTML table).
 *
 * The list page exposes no info hash (the magnet lives behind the details page), so the magnet list
 * stays empty and a details fetch is left to the caller via [ScrapedItem.link].
 */
object ZeroMagnetProvider : TorrentProvider {
    override val id = "0magnet"
    override val name = "0Magnet"
    override val url = "https://9mag.net"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val title = row.selectFirst(TORRENT_NAME)?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, row.selectFirst(DETAILS_PAGE_URL)?.attr("href"))
                ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.let(::normalizeSize)
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = null,
                leechers = null,
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** "6.2GB" -> "6.2 GB" so the shared size parser can read it. */
    private fun normalizeSize(size: String): String = buildString {
        size.forEachIndexed { index, ch ->
            append(ch)
            if (index < size.lastIndex && ch.isDigit() && size[index + 1].isLetter()) {
                append(' ')
            }
        }
    }

    private const val LIST_ITEM = "table.file-list > tbody > tr"
    private const val TORRENT_NAME = "td.result-title > a"
    private const val SIZE = "td.result-meta > div:nth-child(1)"
    private const val UPLOAD_DATE = "td.result-meta > div.result-date"
    private const val DETAILS_PAGE_URL = "td:nth-child(1) > a"
}
