package com.github.livingwithhippos.unchained.data.remote.debridlink

import com.github.livingwithhippos.unchained.data.model.DebridLinkAccount
import com.github.livingwithhippos.unchained.data.model.DebridLinkActivity
import com.github.livingwithhippos.unchained.data.model.DebridLinkDownloaderItem
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import javax.inject.Inject
import okhttp3.MultipartBody
import retrofit2.Response

class DebridLinkApiHelperImpl @Inject constructor(private val debridLinkApi: DebridLinkApi) :
    DebridLinkApiHelper {

    override suspend fun getAccountInfos(
        token: String
    ): Response<DebridLinkResponse<DebridLinkAccount>> = debridLinkApi.getAccountInfos(token)

    override suspend fun addMagnet(
        token: String,
        url: String,
        wait: Boolean?,
        structureType: String?,
    ): Response<DebridLinkResponse<String>> =
        debridLinkApi.addMagnet(token, url, wait, structureType)

    override suspend fun addTorrentFile(
        token: String,
        file: MultipartBody.Part,
        wait: Boolean?,
        structureType: String?,
    ): Response<DebridLinkResponse<String>> =
        debridLinkApi.addTorrentFile(token, file, wait, structureType)

    override suspend fun getSeedboxList(
        token: String,
        ids: String?,
        page: Int?,
        perPage: Int?,
    ): Response<DebridLinkResponse<List<DebridLinkTorrent>>> =
        debridLinkApi.getSeedboxList(token, ids, page, perPage)

    override suspend fun getSeedboxActivity(
        token: String,
        ids: String?,
        page: Int?,
        perPage: Int?,
    ): Response<DebridLinkResponse<Map<String, DebridLinkActivity>>> =
        debridLinkApi.getSeedboxActivity(token, ids, page, perPage)

    override suspend fun deleteTorrent(
        token: String,
        id: String,
    ): Response<DebridLinkResponse<List<String>>> = debridLinkApi.deleteTorrent(token, id)

    override suspend fun addHostLink(
        token: String,
        url: String,
    ): Response<DebridLinkResponse<DebridLinkDownloaderItem>> =
        debridLinkApi.addHostLink(token, url)

    override suspend fun getDownloaderList(
        token: String,
        page: Int?,
        perPage: Int?,
    ): Response<DebridLinkResponse<List<DebridLinkDownloaderItem>>> =
        debridLinkApi.getDownloaderList(token, page, perPage)
}
