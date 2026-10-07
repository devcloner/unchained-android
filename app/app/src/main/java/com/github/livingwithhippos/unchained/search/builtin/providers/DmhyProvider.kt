package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.util.Locale
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Dmhy.kt` (HTML topics table, anime category id 2).
 *
 * The listing already exposes the magnet, so no details page fetch is needed. Upload dates are
 * `yyyy/MM/dd HH:mm` and are normalised to ISO-8601.
 */
object DmhyProvider : TorrentProvider {
    override val id = "dmhy"
    override val name = "Dmhy"
    override val url = "https://share.dmhy.org"
    override val group = ProviderGroup.ANIME
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/topics/list?keyword=${ProviderHttp.encode(query)}&sort_id=2&team_id=0&order=date-desc"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val magnet = row.selectFirst(MAGNET_URI)
                ?.attr("href")
                ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
            val size = row.selectFirst(SIZE)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")) ?: magnet,
                seeders = row.selectFirst(SEEDERS)?.text()?.trim()?.takeIf { it.isNotEmpty() },
                leechers = row.selectFirst(PEERS)?.text()?.trim()?.takeIf { it.isNotEmpty() },
                size = size,
                addedDate = normalizeDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** "yyyy/MM/dd HH:mm" -> ISO-8601 (or null when unparseable). */
    private fun normalizeDate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return DATE_REGEX.find(value)?.let { match ->
            val (year, month, day) = match.destructured
            String.format(Locale.US, "%04d-%02d-%02dT00:00:00Z", year.toInt(), month.toInt(), day.toInt())
        }
    }

    private const val LIST_ITEM = "table#topic_list > tbody > tr"
    private const val TORRENT_NAME = "td.title > a"
    private const val SIZE = "td:nth-child(5)"
    private const val SEEDERS = "td:nth-child(6)"
    private const val PEERS = "td:nth-child(7)"
    private const val UPLOAD_DATE = "td:nth-child(1) > span"
    private const val MAGNET_URI = """td:nth-child(4) > a[href^="magnet:?xt="]"""
    private val DATE_REGEX = Regex("""(\d{4})/(\d{2})/(\d{2})""")
}
