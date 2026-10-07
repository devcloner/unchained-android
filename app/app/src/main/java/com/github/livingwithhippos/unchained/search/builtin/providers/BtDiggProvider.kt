package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/BTDigg.kt` (HTML, DHT index).
 *
 * Each result row exposes a full magnet link, so no details request is needed.
 */
object BtDiggProvider : TorrentProvider {
    override val id = "btdigg"
    override val name = "BTDigg"
    override val url = "https://btdig.com"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}") ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnetHref = row.selectFirst(MAGNET_URI)?.attr("href")
            val infoHash = Scrape.infoHashFrom(magnetHref) ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl ?: Scrape.magnet(infoHash, title),
                seeders = null,
                leechers = null,
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.ownText()?.removePrefix("found ")),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(Scrape.magnet(infoHash, title)),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "div.one_result > div"
    private const val TORRENT_NAME = "div.torrent_name > a"
    private const val SIZE = "span.torrent_size"
    private const val UPLOAD_DATE = "span.torrent_age"
    private const val MAGNET_URI = "div.torrent_magnet > div.fa-magnet > a"
}
