package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/MyPornClub.kt` (adult torrent index).
 *
 * Search: `/s/<slug>/<order>` where the slug uses `-` instead of spaces and the trailing segment
 * is the site's own sort (`seeders`, `latest`, `hits`, `views`), so [sort] is sent server side.
 * The magnet lives on the details page, so rows carry an empty magnet list and the details link.
 */
object MyPornClubProvider : TorrentProvider {
    override val id = "mypornclub"
    override val name = "MyPornClub"
    override val url = "https://myporn.club"
    override val group = ProviderGroup.NSFW
    override val nsfw = true
    override val enabledByDefault = false
    override val supportsServerSort = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val order = when (sort) {
            TorrentSort.DATE -> "latest"
            else -> "seeders"
        }
        // URLEncoder encodes a space as `+`; the site wants `-` between words.
        val slug = ProviderHttp.encode(query.trim()).replace("+", "-").replace("%20", "-")
        val body = http.getText("$url/s/$slug/$order")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val document = Jsoup.parse(body)
        document.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(DETAILS_PAGE_URL) ?: return@mapNotNull null
            val title = row.selectFirst(NAME)?.ownText()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")),
                seeders = row.selectFirst(SEEDERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.ownText()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.text()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "div.torrents_list > div.torrent_element"
    private const val NAME =
        "div.torrent_element_text_div > a:nth-child(2) > span.torrent_element_text_span"
    private const val SIZE = "div.torrent_element_info > span.teiv:nth-child(4)"
    private const val SEEDERS = "div.torrent_element_info > span.teiv.teiv_seeders"
    private const val PEERS = "div.torrent_element_info > span.teiv.teiv_leechers"
    private const val UPLOAD_DATE = "div.torrent_element_info > span.teiv:nth-child(2)"
    private const val DETAILS_PAGE_URL = "div.torrent_element_text_div > a:nth-child(2)"
}
