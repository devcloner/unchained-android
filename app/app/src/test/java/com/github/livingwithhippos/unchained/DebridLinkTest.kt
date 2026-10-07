package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.data.model.DebridLinkError
import com.github.livingwithhippos.unchained.data.model.DebridLinkFile
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import com.github.livingwithhippos.unchained.data.repository.unwrap
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
    fun `seedbox list response parses files`() {
        val json =
            """
            {"success":true,"value":[{"id":"2115ca3cf4356d24510","name":"The name of torrent",
            "created":1767234625,"hashString":"cdb1d2bf6764cb175f68839","downloadPercent":100,
            "totalSize":12345,"status":4,"files":[{"id":"695","name":"movie.mkv","size":12000,
            "downloadUrl":"https://debrid-link.com/dl/1"}]}]}
            """
                .trimIndent()

        val parsed: DebridLinkResponse<List<DebridLinkTorrent>>? = adapter(torrentListType).fromJson(json)

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
    fun `add magnet response parses the torrent id`() {
        val parsed: DebridLinkResponse<String>? = adapter(Types.newParameterizedType(DebridLinkResponse::class.java, String::class.java))
            .fromJson("""{"success":true,"value":"abcd"}""")

        val result = unwrap(parsed)
        assertTrue(result is EitherResult.Success)
        assertEquals("abcd", (result as EitherResult.Success).success)
    }

    @Test
    fun `failure envelope on a 200 becomes a failure with the api message`() {
        val json = """{"success":false,"error":"badToken","error_id":3}"""

        val parsed: DebridLinkResponse<DebridLinkTorrent>? = adapter(torrentType).fromJson(json)

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

        val parsed: DebridLinkResponse<DebridLinkTorrent>? = adapter(torrentType).fromJson(json)

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
}
