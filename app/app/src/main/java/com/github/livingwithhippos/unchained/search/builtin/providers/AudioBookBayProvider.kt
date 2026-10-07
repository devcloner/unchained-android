package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/AudioBookBay.kt` (HTML listing, audiobooks).
 *
 * The search page has no magnet/infohash, so the info hash is read from each result's details page
 * (upstream does the same lazily via `MagnetUriProvider`). The site offers no sort parameter.
 */
object AudioBookBayProvider : TorrentProvider {
    override val id = "audiobookbay"
    override val name = "AudioBookBay"
    override val url = "https://audiobookbay.lu"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/?s=${ProviderHttp.encode(query)}") ?: return emptyList()
        return parse(body).map { item ->
            val detailsUrl = item.link ?: return@map item
            val detailsBody = http.getText(detailsUrl) ?: return@map item
            val infoHash = infoHashFromDetails(detailsBody) ?: return@map item
            item.copy(magnets = listOf(Scrape.magnet(infoHash, item.name)))
        }
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val info = row.selectFirst(TORRENT_INFO)?.wholeText()?.lines().orEmpty()
            val size = info.find { it.startsWith("File Size: ") }
                ?.substringAfter("File Size: ")
                ?.trim()
                ?.removeSuffix("s")
                ?.takeIf { it.isNotEmpty() }
            val posted = info.find { it.startsWith("Posted: ") }?.substringAfter("Posted: ")?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                size = size,
                addedDate = Scrape.parseDate(posted),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** Info hash of a details page, or null when it cannot be read. */
    internal fun infoHashFromDetails(body: String): String? = runCatching {
        Scrape.infoHashFrom(
            Jsoup.parse(body).selectFirst(INFO_HASH)?.nextElementSibling()?.ownText()
        )
    }.getOrNull()

    private const val LIST_ITEM = "div.post"
    private const val TORRENT_NAME = "div.postTitle > h2 > a"
    private const val TORRENT_INFO = "div.postContent > p:nth-child(3)"
    private const val INFO_HASH = "td:containsOwn(Info Hash:)"
}
