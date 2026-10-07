package com.github.livingwithhippos.unchained.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Debrid-Link wraps every answer in the same envelope: `{"success":true,"value":...}` on success
 * and `{"success":false,"error":"...","error_id":N}` on failure. Failures are frequently returned
 * with an HTTP 200, so the [success] flag - not the status code - is what decides.
 */
@JsonClass(generateAdapter = true)
data class DebridLinkResponse<T : Any>(
    @param:Json(name = "success") val success: Boolean = false,
    @param:Json(name = "value") val value: T? = null,
    @param:Json(name = "error") val error: String? = null,
    @param:Json(name = "error_description") val errorDescription: String? = null,
    @param:Json(name = "error_id") val errorId: Int? = null,
)

/** A Debrid-Link failure, mirroring [APIError] for Real-Debrid. */
data class DebridLinkError(val error: String, val errorId: Int?, val description: String? = null) : UnchainedNetworkException

@JsonClass(generateAdapter = true)
data class DebridLinkAccount(
    @param:Json(name = "username") val username: String? = null,
    @param:Json(name = "email") val email: String? = null,
    @param:Json(name = "emailVerified") val emailVerified: Boolean? = null,
    @param:Json(name = "accountType") val accountType: Int? = null,
    @param:Json(name = "premiumLeft") val premiumLeft: Long? = null,
    @param:Json(name = "pts") val points: Long? = null,
    @param:Json(name = "upgradeAccountUrl") val upgradeAccountUrl: String? = null,
    @param:Json(name = "serverId") val serverId: String? = null,
    @param:Json(name = "settings") val settings: DebridLinkSettings? = null,
)

@JsonClass(generateAdapter = true)
data class DebridLinkSettings(
    @param:Json(name = "https") val https: Boolean? = null,
    @param:Json(name = "themeDark") val themeDark: Boolean? = null,
    @param:Json(name = "hideOldLinks") val hideOldLinks: Boolean? = null,
    @param:Json(name = "cdn") val cdn: String? = null,
)

/**
 * A torrent in the user's Debrid-Link seedbox.
 *
 * [status] is the raw API status code, kept as an int on purpose: the API documents the values but
 * upstream has never published a stable enum, so mapping it to names in code would be a guess.
 */
@JsonClass(generateAdapter = true)
data class DebridLinkTorrent(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "hashString") val hashString: String? = null,
    @param:Json(name = "created") val created: Long? = null,
    @param:Json(name = "uploadRatio") val uploadRatio: Double? = null,
    @param:Json(name = "serverId") val serverId: String? = null,
    @param:Json(name = "wait") val wait: Boolean = false,
    @param:Json(name = "peersConnected") val peersConnected: Int? = null,
    @param:Json(name = "status") val status: Int? = null,
    @param:Json(name = "totalSize") val totalSize: Long? = null,
    @param:Json(name = "downloadPercent") val downloadPercent: Int? = null,
    @param:Json(name = "downloadSpeed") val downloadSpeed: Double? = null,
    @param:Json(name = "uploadSpeed") val uploadSpeed: Double? = null,
    @param:Json(name = "isZip") val isZip: Boolean = false,
    @param:Json(name = "srvMaint") val srvMaint: Boolean = false,
    @param:Json(name = "expired") val expired: Boolean? = null,
    @param:Json(name = "files") val files: List<DebridLinkFile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DebridLinkFile(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "size") val size: Long? = null,
    @param:Json(name = "downloadUrl") val downloadUrl: String? = null,
    @param:Json(name = "mimeType") val mimeType: String? = null,
)

/** Fast-updating torrent status, as returned by `GET /seedbox/activity`. */
@JsonClass(generateAdapter = true)
data class DebridLinkActivity(
    @param:Json(name = "status") val status: Int? = null,
    @param:Json(name = "downloadPercent") val downloadPercent: Int? = null,
    @param:Json(name = "peersConnected") val peersConnected: Int? = null,
    @param:Json(name = "downloadSpeed") val downloadSpeed: Double? = null,
    @param:Json(name = "uploadSpeed") val uploadSpeed: Double? = null,
    @param:Json(name = "uploadRatio") val uploadRatio: Double? = null,
    @param:Json(name = "totalSize") val totalSize: Long? = null,
    @param:Json(name = "expired") val expired: Boolean? = null,
    @param:Json(name = "files") val files: List<Int> = emptyList(),
)

/** An entry of the hoster-link downloader (`GET /downloader/list`). */
@JsonClass(generateAdapter = true)
data class DebridLinkDownloaderItem(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "size") val size: Long? = null,
    @param:Json(name = "downloadUrl") val downloadUrl: String? = null,
    @param:Json(name = "uploadDate") val uploadDate: Long? = null,
    @param:Json(name = "expired") val expired: Boolean? = null,
)

/** Map a Debrid-Link seedbox torrent into the unified [TorrentItem] representation. */
fun DebridLinkTorrent.toTorrentItem(): TorrentItem =
    TorrentItem(
        id = id,
        filename = name ?: id,
        originalFilename = name,
        hash = hashString ?: "",
        bytes = totalSize ?: 0L,
        originalBytes = totalSize,
        host = "debrid-link.fr",
        split = 1,
        progress = (downloadPercent ?: 0).toFloat(),
        status = if (status == 100) "downloaded" else "downloading",
        added = (created ?: 0L).toString(),
        files = files.mapIndexed { idx, f ->
            InnerTorrentFile(
                id = idx,
                path = f.name ?: f.id,
                bytes = f.size ?: 0L,
                selected = 1,
            )
        },
        links = files.mapNotNull { it.downloadUrl },
        ended = null,
        speed = downloadSpeed?.toInt(),
        seeders = peersConnected,
    )

/** Convert a TorrentItem with direct links into DownloadItems for immediate playback/download. */
fun TorrentItem.toDirectDownloadItems(): List<DownloadItem> {
    if (files.isNullOrEmpty()) {
        return links.mapIndexed { idx, dlUrl ->
            DownloadItem(
                id = "${id}_$idx",
                filename = filename,
                mimeType = null,
                fileSize = bytes,
                link = dlUrl,
                host = host,
                hostIcon = null,
                chunks = 1,
                crc = null,
                download = dlUrl,
                streamable = 1,
                generated = added,
                type = null,
                alternative = null,
            )
        }
    }
    return files.mapIndexed { idx, file ->
        val dlUrl = links.getOrNull(idx) ?: links.firstOrNull() ?: ""
        DownloadItem(
            id = "${id}_$idx",
            filename = file.path,
            mimeType = null,
            fileSize = file.bytes,
            link = dlUrl,
            host = host,
            hostIcon = null,
            chunks = 1,
            crc = null,
            download = dlUrl,
            streamable = 1,
            generated = added,
            type = null,
            alternative = null,
        )
    }
}
