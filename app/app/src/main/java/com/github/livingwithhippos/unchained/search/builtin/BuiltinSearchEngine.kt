package com.github.livingwithhippos.unchained.search.builtin

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
import timber.log.Timber

/**
 * Built-in search engine based on TorrentSearch providers (TorrentsCSV and The Pirate Bay JSON API).
 * Enables instant searching out-of-the-box without requiring custom .unchained plugin files or Jackett/Prowlarr.
 */
@Singleton
class BuiltinSearchEngine @Inject constructor(
    @param:ClassicClient private val client: OkHttpClient,
) {
    suspend fun search(query: String): List<ScrapedItem> = coroutineScope {
        val tcsvDeferred = async(Dispatchers.IO) { searchTorrentsCsv(query) }
        val apibayDeferred = async(Dispatchers.IO) { searchApibay(query) }

        val tcsv = try { tcsvDeferred.await() } catch (e: Exception) { emptyList() }
        val apibay = try { apibayDeferred.await() } catch (e: Exception) { emptyList() }

        tcsv + apibay
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

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
