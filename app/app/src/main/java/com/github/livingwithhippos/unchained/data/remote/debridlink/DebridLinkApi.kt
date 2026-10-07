package com.github.livingwithhippos.unchained.data.remote.debridlink

import com.github.livingwithhippos.unchained.data.model.DebridLinkAccount
import com.github.livingwithhippos.unchained.data.model.DebridLinkActivity
import com.github.livingwithhippos.unchained.data.model.DebridLinkDownloaderItem
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Debrid-Link API v2 (https://debrid-link.com/api_doc/v2).
 *
 * All endpoints return the [DebridLinkResponse] envelope and are authenticated with the personal
 * API key sent as `Authorization: Bearer <key>`.
 */
interface DebridLinkApi {

    @GET("account/infos")
    suspend fun getAccountInfos(
        @Header("Authorization") token: String
    ): Response<DebridLinkResponse<DebridLinkAccount>>

    /**
     * Add a torrent to the seedbox from a magnet link, a .torrent URL or a bare hash.
     *
     * The same endpoint accepts a `.torrent` upload through `multipart/form-data`; that variant is
     * [addTorrentFile]. Metadata of a torrent added via magnet can be incomplete for the first
     * calls - re-read it with [getSeedboxList] until the file list is populated.
     *
     * @param wait delay the start so files can be selected before downloading
     * @param structureType file listing shape, `list` (default) or `tree`
     */
    @FormUrlEncoded
    @POST("seedbox/add")
    suspend fun addMagnet(
        @Header("Authorization") token: String,
        @Field("url") url: String,
        @Field("wait") wait: Boolean? = null,
        @Field("structureType") structureType: String? = null,
    ): Response<DebridLinkResponse<String>>

    @Multipart
    @POST("seedbox/add")
    suspend fun addTorrentFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Query("wait") wait: Boolean? = null,
        @Query("structureType") structureType: String? = null,
    ): Response<DebridLinkResponse<String>>

    /**
     * @param ids comma separated torrent ids (max 100) - full list when null
     * @param page page number, starts at 0
     * @param perPage items per page, between 20 and 100
     */
    @GET("seedbox/list")
    suspend fun getSeedboxList(
        @Header("Authorization") token: String,
        @Query("ids") ids: String? = null,
        @Query("page") page: Int? = null,
        @Query("perPage") perPage: Int? = null,
    ): Response<DebridLinkResponse<List<DebridLinkTorrent>>>

    /** Faster than [getSeedboxList], keyed by torrent id; also carries per-file progress. */
    @GET("seedbox/activity")
    suspend fun getSeedboxActivity(
        @Header("Authorization") token: String,
        @Query("ids") ids: String? = null,
        @Query("page") page: Int? = null,
        @Query("perPage") perPage: Int? = null,
    ): Response<DebridLinkResponse<Map<String, DebridLinkActivity>>>

    /** Returns the ids that were removed. */
    @DELETE("seedbox/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<DebridLinkResponse<List<String>>>

    /** Send a hoster link (1fichier, uptobox, ...) to the downloader. */
    @FormUrlEncoded
    @POST("downloader/add")
    suspend fun addHostLink(
        @Header("Authorization") token: String,
        @Field("url") url: String,
    ): Response<DebridLinkResponse<DebridLinkDownloaderItem>>

    @GET("downloader/list")
    suspend fun getDownloaderList(
        @Header("Authorization") token: String,
        @Query("page") page: Int? = null,
        @Query("perPage") perPage: Int? = null,
    ): Response<DebridLinkResponse<List<DebridLinkDownloaderItem>>>
}
