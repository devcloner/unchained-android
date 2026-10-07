package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/Yts.kt` (movies JSON API).
 *
 * One movie carries several torrents (720p/1080p/2160p), so a result row is emitted per quality.
 */
object YtsProvider : TorrentProvider {
    override val id = "ytsmx"
    override val name = "Yts"
    override val url = "https://yts.bz"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = true
    override val supportsServerSort = true

    private const val API = "https://movies-api.accel.li/api/v2"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val order = when (sort) {
            TorrentSort.SEEDERS -> "seeds"
            TorrentSort.DATE -> "date_added"
            TorrentSort.NAME, TorrentSort.SIZE -> "title"
            TorrentSort.DEFAULT -> null
        }
        val body = http.getText(
            if (order == null) "$API/list_movies.json?query_term=${ProviderHttp.encode(query)}&limit=50"
            else "$API/list_movies.json?query_term=${ProviderHttp.encode(query)}&limit=50&sort_by=$order"
        ) ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val movies = JSONObject(body).optJSONObject("data")?.optJSONArray("movies")
            ?: return emptyList()
        (0 until movies.length()).flatMap { index ->
            val movie = movies.optJSONObject(index) ?: return@flatMap emptyList()
            val title = movie.optString("title_long").takeIf { it.isNotBlank() }
                ?: return@flatMap emptyList()
            val poster = movie.optString("medium_cover_image").takeIf { it.isNotBlank() }
            val torrents = movie.optJSONArray("torrents") ?: return@flatMap emptyList()
            (0 until torrents.length()).mapNotNull { tIndex ->
                val torrent = torrents.optJSONObject(tIndex) ?: return@mapNotNull null
                val hash = torrent.optString("hash").takeIf { it.length == 40 }
                    ?: return@mapNotNull null
                val quality = torrent.optString("quality").takeIf { it.isNotBlank() }
                val label = if (quality == null) title else "$title [$quality]"
                val bytes = torrent.optLong("size_bytes").takeIf { it > 0 }
                ScrapedItem(
                    name = label,
                    link = poster ?: Scrape.resolve(url, "/movies/${movie.optInt("id")}"),
                    seeders = torrent.optString("seeds").takeIf { it.isNotBlank() },
                    leechers = torrent.optString("peers").takeIf { it.isNotBlank() },
                    size = torrent.optString("size").takeIf { it.isNotBlank() }
                        ?: Scrape.prettySize(bytes),
                    addedDate = Scrape.parseDate(torrent.optString("date_uploaded")),
                    parsedSize = bytes?.toDouble() ?: Scrape.parseSize(torrent.optString("size"))?.toDouble(),
                    magnets = listOf(Scrape.magnet(hash, label)),
                    torrents = emptyList(),
                    hosting = emptyList(),
                    provider = name,
                )
            }
        }
    }.getOrElse { emptyList() }
}
