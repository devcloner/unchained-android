package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup

/**
 * Ported from prajwalch/TorrentSearch `providers/FitGirlRepacks.kt` (games repacks blog).
 *
 * WordPress search: `/?s=<query>`. Every result is an `article.category-lossless-repack` whose
 * entry title links to the repack page; the magnet, when the author exposed one inline, sits in
 * the entry content. The site offers no sort parameter, so [sort] is applied by the engine.
 */
object FitGirlRepacksProvider : TorrentProvider {
    override val id = "fitgirlrepacks"
    override val name = "FitGirl Repacks"
    override val url = "https://fitgirl-repacks.site"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/?s=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val document = Jsoup.parse(body)
        document.select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(GAME_NAME) ?: return@mapNotNull null
            val title = anchor.ownText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detailsUrl = Scrape.resolve(url, anchor.attr("href"))
            val hash = Scrape.infoHashFrom(row.selectFirst(MAGNET_URI)?.attr("href"))
            val magnet = hash?.let { Scrape.magnet(it, title) }
            ScrapedItem(
                name = title,
                link = detailsUrl ?: magnet,
                addedDate = Scrape.parseDate(row.selectFirst(UPLOAD_DATE)?.attr("datetime")),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val LIST_ITEM = "article.category-lossless-repack"
    private const val GAME_NAME = "header > h1.entry-title > a"
    private const val UPLOAD_DATE = "header > div.entry-meta > span.entry-date > a > time"
    private const val MAGNET_URI = """div.entry-content a[href^="magnet:?xt="]"""
}
