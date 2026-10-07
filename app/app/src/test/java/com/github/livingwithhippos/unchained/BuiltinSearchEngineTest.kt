package com.github.livingwithhippos.unchained

import androidx.preference.PreferenceManager
import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem
import com.github.livingwithhippos.unchained.search.builtin.BuiltinSearchEngine
import com.github.livingwithhippos.unchained.search.builtin.ProviderHttp
import com.github.livingwithhippos.unchained.search.builtin.TorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.TorrentSort
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentsCsvProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BuiltinSearchEngineTest {

    private val tcsvJson =
        """
        {"torrents":[{"name":"Ubuntu 24.04 Desktop amd64",
        "infohash":"$HASH","size_bytes":5000000000,"seeders":150,"leechers":10}]}
        """.trimIndent()

    private val engine =
        BuiltinSearchEngine(
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(tcsvJson.toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    }
                )
                .build(),
            PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()),
        )

    private fun item(
        name: String,
        hash: String,
        seeders: String,
        sizeBytes: Double = 0.0,
        provider: String? = null,
    ) = ScrapedItem(
        name = name,
        link = null,
        seeders = seeders,
        leechers = "0",
        size = null,
        addedDate = null,
        parsedSize = sizeBytes,
        magnets = listOf("magnet:?xt=urn:btih:$hash"),
        torrents = emptyList(),
        hosting = emptyList(),
        provider = provider,
    )

    private fun provider(id: String, items: List<ScrapedItem>) =
        object : TorrentProvider {
            override val id = id

            override val name = id

            override val url = "https://example.invalid"

            override suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort) = items
        }

    @Test
    fun `search parses torrents-csv and tags the provider`() = runBlocking {
        val results = engine.search("ubuntu", listOf(TorrentsCsvProvider))

        assertEquals(1, results.size)
        val first = results.first()
        assertEquals("Ubuntu 24.04 Desktop amd64", first.name)
        assertEquals("150", first.seeders)
        assertEquals("10", first.leechers)
        assertTrue(first.magnets.first().contains(HASH))
        assertEquals("TorrentsCSV", first.provider)
    }

    @Test
    fun `a provider that throws does not fail the search`() = runBlocking {
        val broken =
            object : TorrentProvider {
                override val id = "broken"

                override val name = "Broken"

                override val url = "https://example.invalid"

                override suspend fun search(
                    http: ProviderHttp,
                    query: String,
                    sort: TorrentSort,
                ): List<ScrapedItem> = throw IllegalStateException("site changed layout")
            }

        val results = engine.search("x", listOf(broken, provider("ok", listOf(item("Row", HASH, "3")))))

        assertEquals(listOf("Row"), results.map { it.name })
    }

    @Test
    fun `the same torrent from two providers is merged keeping the better seeded copy`() =
        runBlocking {
            val a = provider("a", listOf(item("Same release", HASH, "3", provider = "A")))
            val b = provider("b", listOf(item("Same release", HASH, "42", provider = "B")))

            val results = engine.search("x", listOf(a, b))

            assertEquals(1, results.size)
            assertEquals("42", results.single().seeders)
            assertEquals("B", results.single().provider)
        }

    @Test
    fun `sortItems orders by the criterion the user picked`() {
        val small = item("small", HASH, "5", sizeBytes = 1e9)
        val big = item("big", HASH_2, "1", sizeBytes = 5e9)

        assertEquals(
            listOf("small", "big"),
            engine.sortItems(listOf(big, small), TorrentSort.SEEDERS).map { it.name },
        )
        assertEquals(
            listOf("big", "small"),
            engine.sortItems(listOf(small, big), TorrentSort.SIZE).map { it.name },
        )
        assertEquals(
            listOf("big", "small"),
            engine.sortItems(listOf(small, big), TorrentSort.NAME).map { it.name },
        )
        assertEquals(
            listOf("small", "big"),
            engine.sortItems(listOf(small, big), TorrentSort.DEFAULT).map { it.name },
        )
    }

    private companion object {
        const val HASH = "abcdef0123456789abcdef0123456789abcdef01"
        const val HASH_2 = "0123456789abcdef0123456789abcdef01234567"
    }
}
