package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import java.util.Locale
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/AnimeTosho.kt` (HTML results list).
 *
 * Seeds/peers live in the `[N↑/M↓]` stats span of the links block and the upload date in the
 * `title` attribute of the date div ("Date/time submitted: ...").
 */
object AnimeToshoProvider : TorrentProvider {
    override val id = "animetosho"
    override val name = "AnimeTosho"
    override val url = "https://animetosho.org"
    override val group = ProviderGroup.ANIME
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/search?q=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        Jsoup.parse(body).select(LIST_ITEM).mapNotNull { entry ->
            val anchor = entry.selectFirst("div.link > a") ?: return@mapNotNull null
            val title = anchor.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val links = entry.selectFirst("div.links") ?: return@mapNotNull null
            val magnet = links.selectFirst("""a[href^="magnet:"]""")
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
            val size = entry.selectFirst("div.size")?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            val stats = links.select("span").firstOrNull { it.hasAttr("title") }?.ownText()
            val match = STATS_REGEX.find(stats.orEmpty())
            ScrapedItem(
                name = title,
                link = Scrape.resolve(url, anchor.attr("href")),
                seeders = match?.groupValues?.get(1),
                leechers = match?.groupValues?.get(2),
                size = size,
                addedDate = parseUploadDate(entry.selectFirst("div.date")?.attr("title")),
                parsedSize = Scrape.parseSize(size)?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** "Date/time submitted: <Today|Yesterday|d/M/yyyy>" -> ISO-8601 (or null when unparseable). */
    private fun parseUploadDate(raw: String?): String? {
        val value = raw?.removePrefix(DATE_PREFIX)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.startsWith("Today") -> Instant.now().toString()
            value.startsWith("Yesterday") -> Instant.now().minusSeconds(86_400).toString()
            else -> DATE_REGEX.find(value)?.let { match ->
                val (day, month, year) = match.destructured
                String.format(Locale.US, "%04d-%02d-%02dT00:00:00Z", year.toInt(), month.toInt(), day.toInt())
            }
        }
    }

    private const val LIST_ITEM = "div.home_list_entry"
    private const val DATE_PREFIX = "Date/time submitted: "
    private val STATS_REGEX = Regex("""\[(\d+)↑/(\d+)↓]""")
    private val DATE_REGEX = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""")
}
