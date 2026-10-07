package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.jsoup.Jsoup
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/EpubLibre.kt` (Spanish ebooks).
 *
 * The catalogue is an AJAX POST returning `{"contenido": "<html>…</html>"}` (upstream sends an
 * `X-Requested-With: XMLHttpRequest` header, which [ProviderHttp] cannot express). The magnet lives
 * on the book page, so it is read from there. The site offers no sort parameter.
 */
object EpubLibreProvider : TorrentProvider {
    override val id = "epublibre"
    override val name = "EpubLibre"
    override val url = "https://epublibre.org"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = false

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.postText(
            "$url/catalogo/index/0/nuevo/todos/sin/todos/${ProviderHttp.encode(query)}/ajax",
            "",
        ) ?: return emptyList()
        return parse(body).map { item ->
            val detailsUrl = item.link ?: return@map item
            val detailsBody = http.getText(detailsUrl) ?: return@map item
            val magnet = magnetFromDetails(detailsBody) ?: return@map item
            item.copy(magnets = listOf(magnet))
        }
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val html = JSONObject(body).optString("contenido").takeIf { it.isNotBlank() }
            ?: return emptyList()
        Jsoup.parse(html).select(LIST_ITEM).mapNotNull { row ->
            val anchor = row.selectFirst(DETAILS_PAGE_URL) ?: return@mapNotNull null
            val bookName = row.selectFirst(BOOK_NAME)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val author = row.selectFirst(BOOK_AUTHOR)?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
            ScrapedItem(
                name = if (author == null) bookName else "$bookName - $author",
                link = Scrape.resolve(url, anchor.attr("href")),
                magnets = emptyList(),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    /** Magnet of a book page, or null when it cannot be read. */
    internal fun magnetFromDetails(body: String): String? = runCatching {
        Jsoup.parse(body).selectFirst(MAGNET_URI)?.attr("href")?.takeIf { it.startsWith("magnet:") }
    }.getOrNull()

    private const val LIST_ITEM = "div.span2"
    private const val BOOK_NAME = "a#stk > div.texto-portada > h1"
    private const val BOOK_AUTHOR = "a#stk > div.texto-portada > h2"
    private const val DETAILS_PAGE_URL = "a#stk"
    private const val MAGNET_URI = "a#en_desc"
}
