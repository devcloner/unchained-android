package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.data.model.DebridLinkError
import com.github.livingwithhippos.unchained.data.model.DebridLinkFile
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import com.github.livingwithhippos.unchained.data.model.toDirectDownloadItems
import com.github.livingwithhippos.unchained.data.model.toTorrentItem
import com.github.livingwithhippos.unchained.data.remote.debridlink.DebridLinkApi
import com.github.livingwithhippos.unchained.data.repository.unwrap
import com.github.livingwithhippos.unchained.utilities.DebridProvider
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.extension.isMagnet
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.lang.reflect.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Debrid-Link answers with `{"success":..,"value":..}` and reports failures on a 200 response, so
 * both the JSON shapes and the unwrapping rule get a check here.
 */
class DebridLinkTest {

    private val moshi: Moshi = Moshi.Builder().build()

    private fun <T> adapter(type: Type) = moshi.adapter<T>(type)

    private val torrentListType: Type =
        Types.newParameterizedType(DebridLinkResponse::class.java, Types.newParameterizedType(List::class.java, DebridLinkTorrent::class.java))

    private val torrentType: Type =
        Types.newParameterizedType(DebridLinkResponse::class.java, DebridLinkTorrent::class.java)

    @Test
    fun `diagnostics filter excludes other levels`() {
        com.github.livingwithhippos.unchained.utilities.DebridDiagnostics.setEnabled(true)
        try {
            com.github.livingwithhippos.unchained.utilities.DebridDiagnostics.record("INFO", "Seedbox started")
            com.github.livingwithhippos.unchained.utilities.DebridDiagnostics.record("ERROR", "Server failed")
            val errors = com.github.livingwithhippos.unchained.utilities.DebridDiagnostics.snapshot("ERROR")
            assertTrue(errors.contains("Server failed"))
            assertTrue(!errors.contains("Seedbox started"))
        } finally {
            com.github.livingwithhippos.unchained.utilities.DebridDiagnostics.setEnabled(false)
        }
    }

    @Test
    fun `seedbox list response parses files`() {
        val json =
            """
            {"success":true,"value":[{"id":"2115ca3cf4356d24510","name":"The name of torrent",
            "created":1767234625,"hashString":"cdb1d2bf6764cb175f68839","downloadPercent":100,
            "totalSize":12345,"status":4,"files":[{"id":"695","name":"movie.mkv","size":12000,
            "downloadUrl":"https://debrid-link.com/dl/1"}]}]}
            """
                .trimIndent()

        val parsed: DebridLinkResponse<List<DebridLinkTorrent>>? = adapter<DebridLinkResponse<List<DebridLinkTorrent>>>(torrentListType).fromJson(json)

        val result = unwrap(parsed)
        assertTrue(result is EitherResult.Success)
        val torrent = (result as EitherResult.Success).success.single()
        assertEquals("The name of torrent", torrent.name)
        assertEquals(100, torrent.downloadPercent)
        assertEquals(1, torrent.files.size)
        assertEquals("movie.mkv", torrent.files.single().name)
        assertEquals("https://debrid-link.com/dl/1", torrent.files.single().downloadUrl)
    }

    @Test
    fun `seedbox add parses object and sends encoded magnet`() = runBlocking {
        var recordedPath = ""
        var recordedBody = ""
        var recordedAuth = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            recordedPath = request.url.encodedPath
            recordedAuth = request.header("Authorization").orEmpty()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            recordedBody = buffer.readUtf8()
            okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"success":true,"value":{"id":"seedbox-123","name":"Example"}}"""
                    .toByteArray().toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://debrid-link.com/api/v2/")
            .client(client).addConverterFactory(MoshiConverterFactory.create())
            .build().create(DebridLinkApi::class.java)
        val magnet = "magnet:?xt=urn:btih:0123456789012345678901234567890123456789&dn=Example"
        val response = api.addMagnet("Bearer sample", magnet)
        assertEquals("/api/v2/seedbox/add", recordedPath)
        assertEquals("Bearer sample", recordedAuth)
        assertTrue(recordedBody.contains("url=magnet%3A%3Fxt%3Durn%3Abtih%3A"))
        assertTrue(recordedBody.contains("%26dn%3DExample"))
        assertEquals("seedbox-123", response.body()?.value?.id)
    }

    @Test
    fun `add magnet response parses the torrent id`() {
        val parsed: DebridLinkResponse<DebridLinkTorrent>? = adapter<DebridLinkResponse<DebridLinkTorrent>>(torrentType)
            .fromJson("""{"success":true,"value":{"id":"abcd","name":"Example"}}""")

        val result = unwrap(parsed)
        assertTrue(result is EitherResult.Success)
        assertEquals("abcd", (result as EitherResult.Success).success.id)
    }

    @Test
    fun `failure envelope on a 200 becomes a failure with the api message`() {
        val json = """{"success":false,"error":"badToken","error_id":3}"""

        val parsed: DebridLinkResponse<DebridLinkTorrent>? = adapter<DebridLinkResponse<DebridLinkTorrent>>(torrentType).fromJson(json)

        val result = unwrap(parsed)
        assertTrue(result is EitherResult.Failure)
        val error = (result as EitherResult.Failure).failure
        assertTrue(error is DebridLinkError)
        assertEquals("badToken", (error as DebridLinkError).error)
        assertEquals(3, error.errorId)
    }

    @Test
    fun `null envelope is a failure, not a crash`() {
        val result = unwrap<DebridLinkTorrent>(null)
        assertTrue(result is EitherResult.Failure)
    }

    @Test
    fun `unknown json fields are ignored`() {
        val json = """{"success":true,"value":{"id":"x","unknownField":42},"extra":true}"""

        val parsed: DebridLinkResponse<DebridLinkTorrent>? = adapter<DebridLinkResponse<DebridLinkTorrent>>(torrentType).fromJson(json)

        val result = unwrap(parsed)
        assertTrue(result is EitherResult.Success)
        assertEquals("x", (result as EitherResult.Success).success.id)
    }

    @Test
    fun `file download url is optional`() {
        val file = moshi.adapter(DebridLinkFile::class.java).fromJson("""{"id":"1","name":"a.txt"}""")
        assertEquals("1", file?.id)
        assertEquals(null, file?.downloadUrl)
    }

    @Test
    fun `toTorrentItem maps DebridLinkTorrent correctly`() {
        val dlTorrent =
            DebridLinkTorrent(
                id = "dl123",
                name = "Big.Buck.Bunny.1080p.mkv",
                hashString = "abcdef123456",
                created = 1767234625L,
                totalSize = 5000000L,
                downloadPercent = 100,
                status = 100,
                files =
                    listOf(
                        DebridLinkFile(
                            id = "f1",
                            name = "Big.Buck.Bunny.1080p.mkv",
                            size = 5000000L,
                            downloadUrl = "https://dl.com/1",
                        )
                    ),
                downloadSpeed = 2500.0,
                peersConnected = 42,
            )

        val item = dlTorrent.toTorrentItem()
        assertEquals("dl123", item.id)
        assertEquals("Big.Buck.Bunny.1080p.mkv", item.filename)
        assertEquals("abcdef123456", item.hash)
        assertEquals(5000000L, item.bytes)
        assertEquals(100f, item.progress)
        assertEquals("downloaded", item.status)
        assertEquals(1, item.files?.size)
        assertEquals("https://dl.com/1", item.links.single())
        assertEquals(2500, item.speed)
        assertEquals(42, item.seeders)
    }

    @Test
    fun `debrid provider resolution defaults safely`() {
        assertEquals(DebridProvider.REAL_DEBRID, DebridProvider.fromId("real_debrid"))
        assertEquals(DebridProvider.DEBRID_LINK, DebridProvider.fromId("debrid_link"))
        assertEquals(DebridProvider.REAL_DEBRID, DebridProvider.fromId(null))
        assertEquals(DebridProvider.REAL_DEBRID, DebridProvider.fromId("unknown_provider"))
    }
}
