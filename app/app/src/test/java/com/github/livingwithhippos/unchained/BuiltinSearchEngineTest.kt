package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.BuiltinSearchEngine
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BuiltinSearchEngineTest {

    @Test
    fun `search parses torrents from torrets-csv and apibay responses`() = runBlocking {
        val tcsvJson = """
            {
                "torrents": [
                    {
                        "name": "Ubuntu 24.04 Desktop amd64",
                        "infohash": "abcdef0123456789abcdef0123456789abcdef01",
                        "size_bytes": 5000000000,
                        "seeders": 150,
                        "leechers": 10
                    }
                ]
            }
        """.trimIndent()

        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(tcsvJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(org.robolectric.RuntimeEnvironment.getApplication())
        val engine = BuiltinSearchEngine(client, prefs)
        val results = engine.search("ubuntu")

        assertTrue(results.isNotEmpty())
        val first = results.first()
        assertEquals("Ubuntu 24.04 Desktop amd64", first.name)
        assertEquals("150", first.seeders)
        assertEquals("10", first.leechers)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
    }
}
