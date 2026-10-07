package com.github.livingwithhippos.unchained.utilities

/**
 * The debrid services the app can talk to.
 *
 * Unchained used to be Real-Debrid only: the API layer, the credentials and most of the UI assumed
 * a single provider. Everything provider specific is now keyed off this enum so a second service
 * can be added without copying the whole app.
 */
enum class DebridProvider(val id: String) {
    REAL_DEBRID("real_debrid"),
    DEBRID_LINK("debrid_link");

    companion object {
        fun fromId(id: String?): DebridProvider = entries.firstOrNull { it.id == id } ?: DEBRID_LINK
    }
}

/** SharedPreferences key holding the [DebridProvider.id] the user selected. */
const val DEBRID_PROVIDER_PREF_KEY = "debrid_provider"

/**
 * Debrid-Link API v2. Authentication is a personal API key used as bearer token, generated at
 * [DEBRID_LINK_API_KEY_URL] - there is no client id/secret dance needed unlike Real-Debrid.
 */
const val DEBRID_LINK_BASE_URL = "https://debrid-link.com/api/v2/"

const val DEBRID_LINK_API_KEY_URL = "https://debrid-link.com/webapp/apikey"

/** Preference key holding the Debrid-Link API key. */
const val DEBRID_LINK_API_KEY_PREF_KEY = "debrid_link_api_key"
