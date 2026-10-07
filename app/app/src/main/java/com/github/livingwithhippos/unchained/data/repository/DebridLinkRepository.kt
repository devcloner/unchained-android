package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.DebridLinkStore
import com.github.livingwithhippos.unchained.data.model.DebridLinkAccount
import com.github.livingwithhippos.unchained.data.model.DebridLinkActivity
import com.github.livingwithhippos.unchained.data.model.DebridLinkDownloaderItem
import com.github.livingwithhippos.unchained.data.model.DebridLinkError
import com.github.livingwithhippos.unchained.data.model.DebridLinkResponse
import com.github.livingwithhippos.unchained.data.model.DebridLinkTorrent
import com.github.livingwithhippos.unchained.data.model.EmptyBodyError
import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.remote.debridlink.DebridLinkApiHelper
import com.github.livingwithhippos.unchained.utilities.EitherResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import timber.log.Timber

/**
 * Debrid-Link calls. Mirrors [TorrentsRepository] but unwraps the Debrid-Link envelope instead of
 * relying on HTTP status codes: the API answers failures with `{"success":false,"error":...}` on a
 * 200 response.
 */
class DebridLinkRepository
@Inject
constructor(
    protoStore: com.github.livingwithhippos.unchained.data.local.ProtoStore,
    private val debridLinkApiHelper: DebridLinkApiHelper,
    private val debridLinkStore: DebridLinkStore,
) : BaseRepository(protoStore) {

    val isLoggedIn: Boolean
        get() = debridLinkStore.isLoggedIn

    fun setApiKey(apiKey: String) {
        debridLinkStore.saveApiKey(apiKey)
    }

    fun isConfigured(): Boolean = debridLinkStore.isLoggedIn

    fun logout() = debridLinkStore.clear()

    /** Add a torrent to the Debrid-Link seedbox from a magnet link, a .torrent URL or a hash. */
    suspend fun addMagnet(
        url: String,
        wait: Boolean = false,
    ): EitherResult<UnchainedNetworkException, String> =
        apiCall(errorMessage = "Error adding magnet to Debrid-Link") {
            debridLinkApiHelper.addMagnet(token = bearer(), url = url, wait = wait)
        }

    /** Upload a local `.torrent` file to the seedbox. */
    suspend fun addTorrent(
        binaryTorrent: ByteArray,
        fileName: String,
    ): EitherResult<UnchainedNetworkException, String> {
        val body = binaryTorrent.toRequestBody("application/x-bittorrent".toMediaTypeOrNull(), 0, binaryTorrent.size)
        val part = MultipartBody.Part.createFormData("file", fileName, body)

        return apiCall(errorMessage = "Error uploading torrent to Debrid-Link") {
            debridLinkApiHelper.addTorrentFile(token = bearer(), file = part)
        }
    }

    suspend fun getAccountInfos(): EitherResult<UnchainedNetworkException, DebridLinkAccount> =
        apiCall(errorMessage = "Error retrieving the Debrid-Link account") {
            debridLinkApiHelper.getAccountInfos(token = bearer())
        }

    suspend fun getSeedboxList(): EitherResult<UnchainedNetworkException, List<DebridLinkTorrent>> =
        apiCall(errorMessage = "Error retrieving the Debrid-Link seedbox") {
            debridLinkApiHelper.getSeedboxList(token = bearer(), perPage = MAX_PER_PAGE)
        }

    suspend fun getSeedboxActivity(
        ids: List<String>
    ): EitherResult<UnchainedNetworkException, Map<String, DebridLinkActivity>> =
        apiCall(errorMessage = "Error retrieving Debrid-Link activity") {
            debridLinkApiHelper.getSeedboxActivity(token = bearer(), ids = ids.joinToString(","))
        }

    suspend fun deleteTorrent(id: String): EitherResult<UnchainedNetworkException, List<String>> =
        apiCall(errorMessage = "Error deleting the Debrid-Link torrent") {
            debridLinkApiHelper.deleteTorrent(token = bearer(), id = id)
        }

    /** Send a hoster link (1fichier, uptobox, ...) to the Debrid-Link downloader. */
    suspend fun addHostLink(
        url: String
    ): EitherResult<UnchainedNetworkException, DebridLinkDownloaderItem> =
        apiCall(errorMessage = "Error adding host link to Debrid-Link") {
            debridLinkApiHelper.addHostLink(token = bearer(), url = url)
        }

    suspend fun getDownloaderList():
        EitherResult<UnchainedNetworkException, List<DebridLinkDownloaderItem>> =
        apiCall(errorMessage = "Error retrieving the Debrid-Link downloader") {
            debridLinkApiHelper.getDownloaderList(token = bearer(), perPage = MAX_PER_PAGE)
        }

    private fun bearer(): String = "Bearer ${debridLinkStore.getToken()}"

    private suspend fun <T : Any> apiCall(
        errorMessage: String,
        block: suspend () -> Response<DebridLinkResponse<T>>,
    ): EitherResult<UnchainedNetworkException, T> =
        withContext(Dispatchers.IO) {
            val response: Response<DebridLinkResponse<T>> =
                try {
                    block()
                } catch (e: Exception) {
                    Timber.e(e, errorMessage)
                    return@withContext EitherResult.Failure(NetworkError(-1, errorMessage))
                }

            if (!response.isSuccessful) {
                return@withContext EitherResult.Failure(NetworkError(response.code(), errorMessage))
            }

            unwrap(response.body())
        }

    private companion object {
        /** Debrid-Link caps `perPage` between 20 and 100. */
        const val MAX_PER_PAGE = 100
    }
}

/**
 * Turns a Debrid-Link envelope into an [EitherResult]. `success` decides, not the HTTP code.
 *
 * Top level on purpose: parsing rules are worth a unit test without standing up a repository.
 */
internal fun <T : Any> unwrap(
    response: DebridLinkResponse<T>?
): EitherResult<UnchainedNetworkException, T> =
    when {
        response == null -> EitherResult.Failure(EmptyBodyError(-1))
        response.success && response.value != null -> EitherResult.Success(response.value)
        else ->
            EitherResult.Failure(
                DebridLinkError(
                    error = response.error ?: "Unknown Debrid-Link error",
                    errorId = response.errorId,
                )
            )
    }
