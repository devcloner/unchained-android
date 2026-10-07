package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/MegaPeer.kt` (HTML table, Russian index).
 *
 * The list page carries no info hash (the magnet lives on the details page), so [ScrapedItem.magnets]
 * stays empty and consumers fall back to [ScrapedItem.link].
 */
object MegaPeerProvider : TorrentProvider {
    override val id = "megapeer"
    override val name = "MegaPeer"
    override val url = "https://megapeer.vip"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText(
            "$url/browse.php?search=${ProviderHttp.encode(query)}&age=&cat=0&stype=0&sort=0&ascdesc=0"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(TORRENT_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val size = row.selectFirst(SIZE)?.ownText()?.trim()
            ScrapedItem(
                name = title,
                link = detailsUrl,
                seeders = row.selectFirst(SEEDERS)?.ownText()?.filter { it.isDigit() },
                leechers = row.selectFirst(PEERS)?.ownText()?.filter { it.isDigit() },
                size = size?.takeIf { it.isNotEmpty() },
                addedDate = russianDateToIso(row.selectFirst(UPLOAD_DATE)?.ownText()),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** "10 Ноя 14" -> "2014-11-10T00:00:00Z"; returns null when the layout changes. */
    private fun russianDateToIso(raw: String?): String? {
        val parts = raw?.trim()?.split(' ')?.filter { it.isNotBlank() } ?: return null
        if (parts.size < 3) return null
        val month = RU_MONTHS[parts[1]] ?: return null
        val day = parts[0].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        val fullYear = if (parts[2].length == 2) 2000 + year else year
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02dT00:00:00Z",
            fullYear,
            month,
            day,
        )
    }

    private val RU_MONTHS = mapOf(
        "Янв" to 1, "Фев" to 2, "Мар" to 3, "Апр" to 4, "Май" to 5, "Мая" to 5,
        "Июн" to 6, "Июл" to 7, "Авг" to 8, "Сен" to 9, "Окт" to 10, "Ноя" to 11, "Дек" to 12,
    )

    private const val LIST_ITEM = "div#index > table > tbody > tr.table_fon"
    private const val TORRENT_NAME = "td:nth-child(2) > a:nth-child(2)"
    private const val SIZE = "td:nth-child(3)"
    private const val SEEDERS = "td:nth-child(4) > font:nth-child(2)"
    private const val PEERS = "td:nth-child(4) > font:nth-child(4)"
    private const val UPLOAD_DATE = "td:nth-child(1)"
}
