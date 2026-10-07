package com.github.livingwithhippos.unchained.data.remote.debridlink

import com.github.livingwithhippos.unchained.data.model.DebridLinkAccount
import com.github.livingwithhippos.unchained.data.model.DebridLinkActivity
import com.github.livingwithhippos.unchained.data.model.DebridLinkDownloaderItem
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import okhttp3.MultipartBody
import retrofit2.Response

/** Thin wrapper over [DebridLinkApi], mirroring the helper/api split used for Real-Debrid. */
interface DebridLinkApiHelper {
    suspend fun getAccountInfos(token: String): Response<DebridLinkResponse<DebridLinkAccount>>

    suspend fun addMagnet(
        token: String,
        url: String,
        wait: Boolean? = null,
        structureType: String? = null,
    ): Response<DebridLinkResponse<DebridLinkTorrent>>

    suspend fun addTorrentFile(
        token: String,
        file: MultipartBody.Part,
        wait: Boolean? = null,
        structureType: String? = null,
    ): Response<DebridLinkResponse<DebridLinkTorrent>>

    suspend fun getSeedboxList(
        token: String,
        ids: String? = null,
        page: Int? = null,
        perPage: Int? = null,
    ): Response<DebridLinkResponse<List<DebridLinkTorrent>>>

    suspend fun getSeedboxActivity(
        token: String,
        ids: String? = null,
        page: Int? = null,
        perPage: Int? = null,
    ): Response<DebridLinkResponse<Map<String, DebridLinkActivity>>>

    suspend fun deleteTorrent(token: String, id: String): Response<DebridLinkResponse<List<String>>>

    suspend fun addHostLink(
        token: String,
        url: String,
    ): Response<DebridLinkResponse<DebridLinkDownloaderItem>>

    suspend fun getDownloaderList(
        token: String,
        page: Int? = null,
        perPage: Int? = null,
    ): Response<DebridLinkResponse<List<DebridLinkDownloaderItem>>>
}
