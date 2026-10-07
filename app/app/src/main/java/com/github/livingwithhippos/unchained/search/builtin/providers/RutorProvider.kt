package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.util.Locale
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/Rutor.kt` (HTML result table).
 *
 * Search URL is `GET /search/0/<category>/010/<sort>/<query>` with sort `2` (seeders) hardcoded as
 * upstream; [sort] is applied to the results locally by the engine. The list already carries the
 * magnet, so no details fetch is needed.
 */
object RutorProvider : TorrentProvider {
    override val id = "rutorinfo"
    override val name = "Rutor"
    override val url = "https://rutor.info"
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search/0/0/010/2/${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).drop(1).mapNotNull { row ->
            val nameElement = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = nameElement.ownText().trim().takeIf { it.isNotEmpty() }
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
                addedDate = russianDate(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOf(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** Rutor dates look like `12 Май 24`; translate to an ISO token so [Scrape.parseDate] can read it. */
    private fun russianDate(text: String?): String? {
        val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = raw.split(' ').filter { it.isNotBlank() }
        if (parts.size >= 3) {
            val day = parts[0].toIntOrNull()
            val month = RU_MONTHS[parts[1].lowercase().take(3)]
            val yearRaw = parts[2].toIntOrNull()
            if (day != null && month != null && yearRaw != null) {
                val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
                return Scrape.parseDate(String.format(Locale.US, "%04d-%02d-%02d", year, month, day))
            }
        }
        return Scrape.parseDate(raw)
    }

    private val RU_MONTHS = mapOf(
        "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4, "май" to 5, "июн" to 6,
        "июл" to 7, "авг" to 8, "сен" to 9, "окт" to 10, "ноя" to 11, "дек" to 12,
    )

    private const val LIST_ITEM = "div#index > table > tbody > tr"
    private const val TORRENT_NAME = "td:nth-child(2) > a:nth-child(3)"
    private const val SIZE = "td:nth-last-child(2)"
    private const val SEEDERS = "td:nth-last-child(1) > span:nth-child(1)"
    private const val PEERS = "td:nth-last-child(1) > span:nth-child(3)"
    private const val UPLOAD_DATE = "td:nth-child(1)"
    private const val MAGNET_URI = "td:nth-child(2) > a:nth-child(2)"
}
