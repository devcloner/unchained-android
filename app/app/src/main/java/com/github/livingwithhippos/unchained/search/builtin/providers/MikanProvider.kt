package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Mikan.kt` (mikanani.me, Chinese anime HTML table).
 *
 * The results table carries the magnet in a `data-clipboard-text` attribute, so no details request
 * is needed. The site offers no sort parameter: [sort] is applied to the results by the engine.
 */
object MikanProvider : TorrentProvider {
    override val id = "mikanproject"
    override val name = "Mikan"
    override val url = "https://mikanani.me"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/Home/Search?searchstr=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnetUri = row.selectFirst(MAGNET_URI)?.attr("data-clipboard-text")
                ?.takeIf { it.startsWith("magnet:") }
            val magnet = Scrape.infoHashFrom(magnetUri)?.let { Scrape.magnet(it, title) } ?: magnetUri
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")),
                size = size,
                // The site prints "yyyy/MM/dd HH:mm"; Scrape.parseDate understands the dashed form.
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()?.replace('/', '-')),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "tr.js-search-results-row"
    private const val TORRENT_NAME = "td:nth-child(2) > a:nth-child(1)"
    private const val SIZE = "td:nth-child(3)"
    private const val UPLOAD_DATE = "td:nth-child(4)"
    private const val MAGNET_URI = "td:nth-child(2) > a[data-clipboard-text]"
}
