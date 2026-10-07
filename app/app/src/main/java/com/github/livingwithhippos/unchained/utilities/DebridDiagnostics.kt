package com.github.livingwithhippos.unchained.utilities

import com.github.livingwithhippos.unchained.data.model.DebridLinkError
import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded, in-memory diagnostics. Never record requests, URLs, credentials, or raw server bodies. */
object DebridDiagnostics {
    private val entries = ArrayDeque<Pair<String, String>>()
    @Volatile var enabled = false
        private set

    @Synchronized fun record(level: String, event: String) {
        if (!enabled) return
        if (entries.size >= 150) entries.removeFirst()
        entries.addLast(level to "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} [$level] $event")
    }

    @Synchronized fun snapshot(filter: String = "ALL"): String =
        entries.filter { filter == "ALL" || it.first == filter }
            .joinToString("\n") { it.second }
            .ifEmpty { "No diagnostic events yet" }

    @Synchronized fun clear() = entries.clear()

    @Synchronized fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) entries.clear()
    }

    fun errorLabel(error: UnchainedNetworkException): String = when (error) {
        is DebridLinkError -> "${error.error.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,60}")) } ?: "API rejected request"}${error.errorId?.let { " (#$it)" }.orEmpty()}"
        is NetworkError -> "HTTP ${error.error.takeIf { it > 0 } ?: "network"}"
        else -> "Unexpected response"
    }
}
