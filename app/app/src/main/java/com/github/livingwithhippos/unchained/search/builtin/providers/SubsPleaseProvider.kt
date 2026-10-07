package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/SubsPlease.kt` (anime JSON API).
 *
 * The API answers `{}`/object of show name -> anime when there are hits and `[]` when there are
 * none, so a non-object answer simply yields no results. One anime carries one download per
 * resolution, so a row is emitted per download.
 */
object SubsPleaseProvider : TorrentProvider {
    override val id = "subsplease"
    override val name = "SubsPlease"
    override val url = "https://subsplease.org"
    override val group = ProviderGroup.MEDIA
    override val enabledByDefault = true

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val body = http.getText("$url/api?f=search&tz=$&s=${ProviderHttp.encode(query)}")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        // Throws for the `[]` empty answer, which the runCatching turns into no results.
        val root = JSONObject(body)
        val items = mutableListOf<ScrapedItem>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val showName = keys.next()
            val anime = root.optJSONObject(showName) ?: continue
            val episode = anime.optString("episode").takeIf { it.isNotBlank() }
            val page = anime.optString("page").takeIf { it.isNotBlank() }
            val addedDate = Scrape.parseDate(anime.optString("release_date"))
            val downloads = anime.optJSONArray("downloads") ?: continue
            for (index in 0 until downloads.length()) {
                val download = downloads.optJSONObject(index) ?: continue
                val magnetUri = download.optString("magnet").takeIf { it.startsWith("magnet:") }
                    ?: continue
                val resolution = download.optString("res")
                val title = if (resolution.isBlank()) showName else "$showName [${resolution}p]"
                val bytes = XL_REGEX.find(magnetUri)?.groupValues?.get(1)?.toLongOrNull()
                val details = page?.let { path ->
                    if (episode != null && resolution.isNotBlank()) {
                        "$url/$path?ep=$episode&res=$resolution"
                    } else {
                        "$url/$path"
                    }
                }
                items += ScrapedItem(
                    name = title,
                    link = details,
                    size = Scrape.prettySize(bytes),
                    addedDate = addedDate,
                    parsedSize = bytes?.toDouble(),
                    magnets = listOf(magnetUri),
                    torrents = emptyList(),
                    hosting = emptyList(),
                    provider = name,
                )
            }
        }
        items
    }.getOrElse { emptyList() }

    private val XL_REGEX = Regex("[?&]xl=(\\d+)")
}
