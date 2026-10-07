package com.github.livingwithhippos.unchained.search.builtin.providers

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.Scrape
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ported from prajwalch/TorrentSearch `providers/Btsow.kt` (JSON POST API).
 *
 * Request body: `[{"search":"<query>"},30,1]`; response: `{"code":200,"data":[{"hash","name",
 * "size","lastUpdateTime"}]}`.
 */
object BtsowProvider : TorrentProvider {
    override val id = "btsow"
    override val name = "Btsow"
    override val url = "https://btsow.live"
    override val group = ProviderGroup.GENERAL
    override val enabledByDefault = false

    private val api get() = "$url/bts/data/api/search"

    override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem> {
        val payload = JSONArray()
            .put(JSONObject().put("search", query))
            .put(30)
            .put(1)
            .toString()
        val body = http.postText(api, payload, "application/json; charset=UTF-8")
            ?: return emptyList()
        return parse(body)
    }

    internal fun parse(body: String): List<ScrapedItem> = runCatching {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("name")
                .replace("<em>", "")
                .replace("</em>", "")
                .trim()
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val infoHash = item.optString("hash").lowercase().takeIf { it.length == 40 }
                ?: return@mapNotNull null
            val bytes = item.optLong("size").takeIf { it > 0 }
            ScrapedItem(
                name = title,
                link = "$url/magnet/detail/$infoHash",
                seeders = null,
                leechers = null,
                size = Scrape.prettySize(bytes),
                addedDate = item.optLong("lastUpdateTime").takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it).toString() },
                parsedSize = bytes?.toDouble(),
                magnets = listOf(Scrape.magnet(infoHash, title)),
                torrents = emptyList(),
                hosting = emptyList(),
                provider = name,
            )
        }
    }.getOrElse { emptyList() }
}
