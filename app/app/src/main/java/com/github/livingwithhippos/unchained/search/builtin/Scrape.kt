package com.github.livingwithhippos.unchained.search.builtin

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Small parsing helpers shared by the scrapers. */
object Scrape {

    /** `magnet:?xt=urn:btih:<hash>&dn=<name>` — the name is only added when it is non blank. */
    fun magnet(infoHash: String, name: String? = null): String {
        val hash = infoHash.trim().lowercase()
        val dn = name?.trim()?.takeIf { it.isNotEmpty() }
        return buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            if (dn != null) append("&dn=").append(ProviderHttp.encode(dn))
        }
    }

    /** A 40 char hex string or a 32 char base32 string, as found in magnets. */
    private val HASH_REGEX = Regex("([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})")

    fun infoHashFrom(text: String?): String? =
        text?.let { HASH_REGEX.find(it)?.groupValues?.get(1) }?.lowercase()

    /** "1.4 GB" / "700 MiB" / "1,4 GB" -> bytes. Returns null when nothing parses. */
    fun parseSize(text: String?): Long? {
        val match = SIZE_REGEX.find(text?.trim().orEmpty()) ?: return null
        val (amount, unit) = match.destructured
        val value = amount.replace(',', '.').toDoubleOrNull() ?: return null
        val factor =
            when (unit.uppercase().first()) {
                'K' -> 1024.0
                'M' -> 1024.0 * 1024
                'G' -> 1024.0 * 1024 * 1024
                'T' -> 1024.0 * 1024 * 1024 * 1024
                'P' -> 1024.0 * 1024 * 1024 * 1024 * 1024
                else -> 1.0
            }
        return (value * factor).toLong()
    }

    private val SIZE_REGEX = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*([KMGP]i?B)", RegexOption.IGNORE_CASE)

    fun prettySize(bytes: Long?): String? {
        val b = bytes?.takeIf { it > 0 } ?: return null
        val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
        var value = b.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "$b B" else String.format(Locale.US, "%.2f %s", value, units[index])
    }

    /** "2 days ago", "yesterday", "2024-05-01 12:00" -> ISO-8601 instant string. */
    fun parseDate(text: String?): String? {
        val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = raw.lowercase()
        val now = Instant.now()

        RELATIVE_REGEX.find(lower)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: 1L
            val seconds =
                when (match.groupValues[2]) {
                    "second", "sec" -> amount
                    "minute", "min" -> amount * 60
                    "hour" -> amount * 3600
                    "day" -> amount * 86_400
                    "week" -> amount * 604_800
                    "month" -> amount * 2_592_000
                    else -> amount * 31_536_000
                }
            return now.minusSeconds(seconds).toString()
        }
        if (lower == "yesterday") return now.minus(1, ChronoUnit.DAYS).toString()
        if (lower == "just now" || lower == "today") return now.toString()

        // Absolute dates: keep the first ISO looking token, otherwise give up (the UI tolerates null).
        ISO_REGEX.find(raw)?.let { return "${it.groupValues[1]}T00:00:00Z" }
        return null
    }

    private val RELATIVE_REGEX = Regex("(\\d+)\\s+(second|sec|minute|min|hour|day|week|month|year)s?\\s+ago")

    private val ISO_REGEX = Regex("(\\d{4}-\\d{2}-\\d{2})")

    fun parseInt(text: String?): Int? = text?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** Bare domain of a URL, used for referers and link attribution. */
    fun host(url: String): String = url.substringAfter("://").substringBefore('/')

    /** jsoup is lenient about `//host/path` links; resolve them against the provider base. */
    fun resolve(baseUrl: String, href: String?): String? {
        val link = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            link.startsWith("http") -> link
            link.startsWith("//") -> "https:$link"
            link.startsWith("/") -> baseUrl.trimEnd('/') + link
            else -> baseUrl.trimEnd('/') + "/" + link
        }
    }
}
