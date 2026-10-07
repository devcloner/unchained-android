package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.json.JSONArray

/**
 * Ported from prajwalch/TorrentSearch `providers/AniLibria.kt` (public AniLibria JSON API).
 *
 * Two endpoints: the search returns matching releases, and each release's torrents are fetched from
 * `anime/torrents/release/<id>`. [parse] covers the torrents payload (one row per torrent); the
 * release ids are extracted by [releaseIds].
 */
object AniLibriaProvider : TorrentProvider {
    override val id = "anilibria"
    override val name = "AniLibria"
    override val url = "https://www.anilibria.top"
    override val group = ProviderGroup.ANIME
    override val enabledByDefault = true

    private const val API = "https://anilibria.top/api/v1"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$API/app/search/releases?query=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return runCatching {
            releaseIds(body).flatMap { id ->
                http.getText("$API/anime/torrents/release/$id")?.let { parse(it) } ?: emptyList()
            }
        }.getOrElse { emptyList() }
    }

    /** Release ids of the search payload, capped at [MAX_RELEASES]. */
    internal fun releaseIds(body: String): List<Long> = runCatching {
        val array = JSONArray(body)
        (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.optLong("id")?.takeIf { id -> id > 0 } }
            .take(MAX_RELEASES)
    }.getOrElse { emptyList() }

    /**
     * Torrents payload of `anime/torrents/release/<id>`:
     * `[{"id","hash","label","size","magnet","seeders","leechers","created_at","release"}]`.
     */
    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val release = item.optJSONObject("release")
            val title = item.optString("label").takeIf { it.isNotBlank() }
                ?: release?.optJSONObject("name")?.let { names ->
                    names.optString("english").takeIf { it.isNotBlank() }
                        ?: names.optString("main").takeIf { it.isNotBlank() }
                }
                ?: return@mapNotNull null
            val infoHash = item.optString("hash").takeIf { it.isNotBlank() }
            val bytes = item.optLong("size").takeIf { it > 0 }
            val magnet = item.optString("magnet").takeIf { it.startsWith("magnet:") }
                ?: infoHash?.let { Scrape.magnet(it, title) }
            val alias = release?.optString("alias")?.takeIf { it.isNotBlank() }
            ScrapedItem(
                name = title,
                link = alias?.let { Scrape.resolve(url, "/anime/releases/release/$it") },
                seeders = item.optInt("seeders").takeIf { item.has("seeders") }?.toString(),
                leechers = item.optInt("leechers").takeIf { item.has("leechers") }?.toString(),
                size = Scrape.prettySize(bytes),
                addedDate = Scrape.parseDate(item.optString("created_at")),
                parsedSize = bytes?.toDouble(),
                magnets = listOfNotNull(magnet),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }

    private const val MAX_RELEASES = 15
}
