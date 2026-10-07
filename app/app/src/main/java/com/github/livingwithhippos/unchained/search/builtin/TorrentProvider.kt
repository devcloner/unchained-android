package com.github.livingwithhippos.unchained.search.builtin

import com.github.livingwithhippos.unchained.plugins.model.ScrapedItem

/** Result ordering the user picked for a provider. */
enum class TorrentSort(val id: String) {
    DEFAULT("default"),
    SEEDERS("seeders"),
    SIZE("size"),
    DATE("date"),
    NAME("name");

    companion object {
        fun fromId(id: String?): TorrentSort = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Settings group a provider is listed under. */
enum class ProviderGroup(val id: String) {
    GENERAL("general"),
    ANIME("anime"),
    MEDIA("media"),
    NSFW("nsfw");

    companion object {
        fun fromId(id: String?): ProviderGroup = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}

/**
 * One torrent search source.
 *
 * Rules for implementations:
 * - never throw: return an empty list when the site is down, rate limited or changed layout;
 * - [id] is the suffix of the settings keys (`search_provider_<id>` / `search_sort_<id>`), so keep
 *   it stable or the user's choice resets;
 * - keep the parsing in a pure `internal fun parse(body: String, ...)` so a fixture test can cover
 *   it without network access.
 */
interface TorrentProvider {
    val id: String
    val name: String
    val url: String
    val group: ProviderGroup get() = ProviderGroup.GENERAL
    /** Adult content: only searched when the global NSFW switch is on. */
    val nsfw: Boolean get() = false
    val enabledByDefault: Boolean get() = false
    /** true when [sort] is sent to the site instead of only being applied to its results locally. */
    val supportsServerSort: Boolean get() = false

    suspend fun search(http: ProviderHttp, query: String, sort: TorrentSort): List<ScrapedItem>
}
