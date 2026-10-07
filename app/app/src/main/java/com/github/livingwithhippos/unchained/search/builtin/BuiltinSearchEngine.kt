package com.github.livingwithhippos.unchained.search.builtin

import android.content.SharedPreferences
import com.github.livingwithhippos.unchained.di.ClassicClient
import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import timber.log.Timber

/**
 * Built-in search engine based on TorrentSearch providers.
 * Supports toggleable providers via SharedPreferences:
 * - TorrentsCSV
 * - The Pirate Bay (apibay.org)
 * - LimeTorrents (HTML parsing)
 * - EZTV (JSON API)
 */
@Singleton
class BuiltinSearchEngine @Inject constructor(
    @param:ClassicClient private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    suspend fun search(query: String): List<ScrapedItem> = coroutineScope {
        val tcsvEnabled = preferences.getBoolean("search_provider_torrentscsv", true)
        val tpbEnabled = preferences.getBoolean("search_provider_piratebay", true)
        val limeEnabled = preferences.getBoolean("search_provider_limetorrents", true)
        val eztvEnabled = preferences.getBoolean("search_provider_eztv", true)

        val tcsvDeferred = if (tcsvEnabled) async(Dispatchers.IO) { searchTorrentsCsv(query) } else null
        val apibayDeferred = if (tpbEnabled) async(Dispatchers.IO) { searchApibay(query) } else null
        val limeDeferred = if (limeEnabled) async(Dispatchers.IO) { searchLimeTorrents(query) } else null
        val eztvDeferred = if (eztvEnabled) async(Dispatchers.IO) { searchEztv(query) } else null

        val results = mutableListOf<ScrapedItem>()
        tcsvDeferred?.let { try { results.addAll(it.await()) } catch (e: Exception) { Timber.e(e) } }
        apibayDeferred?.let { try { results.addAll(it.await()) } catch (e: Exception) { Timber.e(e) } }
        limeDeferred?.let { try { results.addAll(it.await()) } catch (e: Exception) { Timber.e(e) } }
        eztvDeferred?.let { try { results.addAll(it.await()) } catch (e: Exception) { Timber.e(e) } }

        results
    }

    private fun searchTorrentsCsv(query: String): List<ScrapedItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://torrents-csv.com/service/search?q=$encoded")
                .header("User-Agent", "Unchained/1.8.1")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            val json = JSONObject(body)
            val torrents = json.optJSONArray("torrents") ?: return emptyList()

            val items = mutableListOf<ScrapedItem>()
            for (i in 0 until torrents.length()) {
                val t = torrents.getJSONObject(i)
                val name = t.optString("name", "Unknown")
                val infohash = t.optString("infohash", "")
                if (infohash.isBlank()) continue
                val sizeBytes = t.optLong("size_bytes", 0L)
                val seeders = t.optInt("seeders", 0).toString()
                val leechers = t.optInt("leechers", 0).toString()
                val magnet = "magnet:?xt=urn:btih:$infohash&dn=${URLEncoder.encode(name, "UTF-8")}"

                items.add(
                    ScrapedItem(
                        name = name,
                        link = null,
                        seeders = seeders,
                        leechers = leechers,
                        size = formatSize(sizeBytes),
                        addedDate = null,
                        parsedSize = sizeBytes.toDouble(),
                        magnets = listOf(magnet),
                        torrents = emptyList(),
                        hosting = emptyList(),
                    )
                )
            }
            items
        } catch (e: Exception) {
            Timber.e(e, "Error searching TorrentsCSV")
            emptyList()
        }
    }

    private fun searchApibay(query: String): List<ScrapedItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://apibay.org/q.php?q=$encoded&cat=")
                .header("User-Agent", "Unchained/1.8.1")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            val array = JSONArray(body)

            val items = mutableListOf<ScrapedItem>()
            for (i in 0 until array.length()) {
                val t = array.getJSONObject(i)
                val name = t.optString("name", "")
                val infohash = t.optString("info_hash", "")
                if (infohash.isBlank() || name == "No results returned") continue
                val sizeBytes = t.optLong("size", 0L)
                val seeders = t.optString("seeders", "0")
                val leechers = t.optString("leechers", "0")
                val id = t.optString("id", "")
                val magnet = "magnet:?xt=urn:btih:$infohash&dn=${URLEncoder.encode(name, "UTF-8")}"

                items.add(
                    ScrapedItem(
                        name = name,
                        link = if (id.isNotBlank()) "https://thepiratebay.org/description.php?id=$id" else null,
                        seeders = seeders,
                        leechers = leechers,
                        size = formatSize(sizeBytes),
                        addedDate = null,
                        parsedSize = sizeBytes.toDouble(),
                        magnets = listOf(magnet),
                        torrents = emptyList(),
                        hosting = emptyList(),
                    )
                )
            }
            items
        } catch (e: Exception) {
            Timber.e(e, "Error searching Apibay")
            emptyList()
        }
    }

    private fun searchLimeTorrents(query: String): List<ScrapedItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://limetorrents.lol/search/all/$encoded/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val html = response.body.string()
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.table2 tr")

            val items = mutableListOf<ScrapedItem>()
            for (row in rows) {
                val titleElem = row.selectFirst("div.tt-name a:nth-of-type(2)") ?: continue
                val name = titleElem.text()
                val magnetElem = row.selectFirst("a[href^=magnet:]")
                val magnet = magnetElem?.attr("href") ?: continue
                val size = row.selectFirst("td.tdnormal")?.text() ?: "0 B"
                val seeders = row.selectFirst("td.tdseed")?.text() ?: "0"
                val leechers = row.selectFirst("td.tdleech")?.text() ?: "0"

                items.add(
                    ScrapedItem(
                        name = name,
                        link = null,
                        seeders = seeders,
                        leechers = leechers,
                        size = size,
                        addedDate = null,
                        parsedSize = 0.0,
                        magnets = listOf(magnet),
                        torrents = emptyList(),
                        hosting = emptyList(),
                    )
                )
            }
            items
        } catch (e: Exception) {
            Timber.e(e, "Error searching LimeTorrents")
            emptyList()
        }
    }

    private fun searchEztv(query: String): List<ScrapedItem> {
        return try {
            val request = Request.Builder()
                .url("https://eztv.re/api/get-torrents?limit=30&page=1")
                .header("User-Agent", "Unchained/1.8.1")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            val json = JSONObject(body)
            val torrents = json.optJSONArray("torrents") ?: return emptyList()

            val items = mutableListOf<ScrapedItem>()
            val lowerQuery = query.lowercase()
            for (i in 0 until torrents.length()) {
                val t = torrents.getJSONObject(i)
                val title = t.optString("title", "")
                if (!title.lowercase().contains(lowerQuery)) continue
                val magnet = t.optString("magnet_url", "")
                if (magnet.isBlank()) continue
                val sizeBytes = t.optLong("size_bytes", 0L)
                val seeders = t.optInt("seeds", 0).toString()

                items.add(
                    ScrapedItem(
                        name = title,
                        link = t.optString("episode_url").takeIf { it.isNotBlank() },
                        seeders = seeders,
                        leechers = "0",
                        size = formatSize(sizeBytes),
                        addedDate = null,
                        parsedSize = sizeBytes.toDouble(),
                        magnets = listOf(magnet),
                        torrents = emptyList(),
                        hosting = emptyList(),
                    )
                )
            }
            items
        } catch (e: Exception) {
            Timber.e(e, "Error searching EZTV")
            emptyList()
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
