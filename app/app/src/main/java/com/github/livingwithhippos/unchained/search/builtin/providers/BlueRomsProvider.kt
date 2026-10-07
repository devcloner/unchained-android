package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/BlueRoms.kt` (ROM download index).
 *
 * Search: `/search?g=0&p=0&q=<query>`. Each result is a bootstrap card; its footer link points at
 * the `/download/<base64>` page that carries the magnet, so the row is emitted with an empty
 * magnet list and the details/download page as its link. The site offers no sort parameter.
 */
object BlueRomsProvider : TorrentProvider {
    override val id = "blueroms"
    override val name = "BlueRoms"
    override val url = "https://www.blueroms.ws"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?g=0&p=0&q=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val document = Jsoup.parse(body)
        document.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(GAME_NAME) ?: return@mapNotNull null
            val gameName = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val platform = row.selectFirst(PLATFORM)
                ?.nextElementSibling()
                ?.ownText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val size = row.selectFirst(SIZE)
                ?.nextSibling()
                ?.outerHtml()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = platform?.let { "$gameName - $it" } ?: gameName,
                link = Scrape.resolve(url, row.selectFirst(DOWNLOAD_PAGE_URL)?.attr("href"))
                    ?: Scrape.resolve(url, anchor.attr("href")),
                size = size,
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "div.row > div.col-xs-12 > div.card"
    private const val GAME_NAME = "h4.card-title > a"
    private const val SIZE = "strong:containsOwn(Size:)"
    private const val PLATFORM = "strong:containsOwn(Platform:)"
    private const val DOWNLOAD_PAGE_URL = "div.card-footer > a"
}
