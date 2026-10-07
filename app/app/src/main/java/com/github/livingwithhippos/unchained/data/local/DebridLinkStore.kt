package com.github.livingwithhippos.unchained.data.local

import android.content.SharedPreferences
import com.github.livingwithhippos.unchained.utilities.DEBRID_LINK_API_KEY_PREF_KEY
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Debrid-Link API key.
 *
 * ponytail: plain SharedPreferences, same place the app already keeps its other settings. Move it
 * into a protobuf DataStore (like [ProtoStoreImpl] does for Real-Debrid credentials) if a token
 * that must survive an app reinstall is ever needed.
 */
@Singleton
class DebridLinkStore @Inject constructor(private val preferences: SharedPreferences) {

    var apiKey: String?
        get() = preferences.getString(DEBRID_LINK_API_KEY_PREF_KEY, null)?.takeIf { it.isNotBlank() }
        private set(value) {
            preferences.edit().putString(DEBRID_LINK_API_KEY_PREF_KEY, value).apply()
        }

    val isLoggedIn: Boolean
        get() = apiKey != null

    fun saveApiKey(value: String) {
        require(value.isNotBlank()) { "Debrid-Link API key cannot be empty" }
        apiKey = value.trim()
    }

    fun clear() {
        preferences.edit().remove(DEBRID_LINK_API_KEY_PREF_KEY).apply()
    }

    /** The bearer token for every Debrid-Link call. */
    fun getToken(): String =
        apiKey ?: throw IllegalArgumentException("Debrid-Link API key is missing")
}
