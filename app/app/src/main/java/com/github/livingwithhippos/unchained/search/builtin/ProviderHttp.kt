package com.github.livingwithhippos.unchained.search.builtin

import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Tiny OkHttp wrapper for the scrapers: every call returns null instead of throwing, so a dead or
 * blocking provider cannot fail a search.
 */
class ProviderHttp(private val client: OkHttpClient) {

    fun getText(url: String, referer: String? = null): String? =
        execute(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply { referer?.let { header("Referer", it) } }
                .build()
        )

    fun postText(
        url: String,
        body: String,
        contentType: String = "application/x-www-form-urlencoded; charset=UTF-8",
        referer: String? = null,
    ): String? =
        execute(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/html,*/*")
                .apply { referer?.let { header("Referer", it) } }
                .post(body.toRequestBody(contentType.toMediaTypeOrNull()))
                .build()
        )

    fun getJsonObject(url: String, referer: String? = null): JSONObject? =
        getText(url, referer)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun getJsonArray(url: String, referer: String? = null): JSONArray? =
        getText(url, referer)?.let { runCatching { JSONArray(it) }.getOrNull() }

    /** Some sites wrap the real JSON in a `cb({...})` callback. */
    fun getJsonpObject(url: String, referer: String? = null): JSONObject? =
        getText(url, referer)
            ?.let { it.substringAfter("(", it).substringBeforeLast(")").ifBlank { it } }
            ?.let { runCatching { JSONObject(it.trim()) }.getOrNull() }

    private fun execute(request: Request): String? =
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.d("search provider %s -> HTTP %d", request.url.host, response.code)
                    null
                } else {
                    response.body.string()
                }
            }
        } catch (t: Throwable) {
            Timber.d(t, "search provider request to %s failed", request.url.host)
            null
        }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
