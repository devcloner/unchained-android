package com.github.livingwithhippos.unchained.search.builtin

import android.content.SharedPreferences
import com.github.livingwithhippos.unchained.di.ClassicClient
import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Built-in torrent/magnet search engine.
 *
 * Runs every provider the user enabled in Settings in parallel, orders each provider's rows the way
 * that provider was configured, then merges everything and drops duplicates. A provider that fails
 * (site down, layout change, timeout) contributes nothing and never fails the whole search.
 */
@Singleton
class BuiltinSearchEngine @Inject constructor(
    @param:ClassicClient private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    private val http = ProviderHttp(client)

    suspend fun search(query: String): List<ScrapedItem> =
        search(query, TorrentProviderRegistry.enabled(preferences))

    suspend fun search(query: String, providers: List<TorrentProvider>): List<ScrapedItem> =
        coroutineScope {
            val perProvider =
                providers.map { provider ->
                    async(Dispatchers.IO) {
                        val sort = TorrentProviderRegistry.sortFor(preferences, provider.id)
                        runCatching { provider.search(http, query, sort) }
                            .onFailure { Timber.w(it, "search provider ${provider.id} failed") }
                            .getOrDefault(emptyList())
                            .let { sortItems(it, sort) }
                    }
                }
            dedupe(perProvider.awaitAll().flatten())
        }

    /** Orders rows locally; providers with a server-side sort already return them ordered. */
    fun sortItems(items: List<ScrapedItem>, sort: TorrentSort): List<ScrapedItem> {
        val comparator: Comparator<ScrapedItem>? =
            when (sort) {
                TorrentSort.DEFAULT -> null
                TorrentSort.SEEDERS -> compareByDescending { it.seeders?.toIntOrNull() ?: -1 }
                TorrentSort.SIZE -> compareByDescending { it.parsedSize ?: 0.0 }
                TorrentSort.DATE -> compareByDescending { it.addedDate.orEmpty() }
                TorrentSort.NAME -> compareBy { it.name.lowercase(Locale.US) }
            }
        return if (comparator == null) items else items.sortedWith(comparator)
    }

    /**
     * The same torrent is often returned by several providers: keep one row per info hash (or per
     * name+size when no hash is known), preferring the copy that reports the most seeders.
     */
    private fun dedupe(items: List<ScrapedItem>): List<ScrapedItem> {
        val best = LinkedHashMap<String, ScrapedItem>()
        for (item in items) {
            val hash = item.magnets.firstNotNullOfOrNull { Scrape.infoHashFrom(it) }
            val key = hash ?: "${item.name}|${item.parsedSize ?: 0.0}"
            val previous = best[key]
            val seeders = item.seeders?.toIntOrNull() ?: -1
            val previousSeeders = previous?.seeders?.toIntOrNull() ?: -1
            if (previous == null || seeders > previousSeeders) {
                best[key] = item
            }
        }
        return best.values.toList()
    }
}
