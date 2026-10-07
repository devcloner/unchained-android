package com.github.livingwithhippos.unchained.newdownload.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UploadedTorrent
import com.github.livingwithhippos.unchained.data.repository.DebridLinkRepository
import com.github.livingwithhippos.unchained.data.repository.HostsRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.UnrestrictRepository
import com.github.livingwithhippos.unchained.utilities.DEBRID_PROVIDER_PREF_KEY
import com.github.livingwithhippos.unchained.utilities.DebridProvider
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.extension.isMagnet
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/** A [ViewModel] subclass. It offers LiveData to be observed while creating new downloads */
@HiltViewModel
class NewDownloadViewModel
@Inject
constructor(
    private val unrestrictRepository: UnrestrictRepository,
    private val torrentsRepository: TorrentsRepository,
    private val hostsRepository: HostsRepository,
    private val debridLinkRepository: DebridLinkRepository,
    private val preferences: SharedPreferences,
) : ViewModel() {

    // use Event since navigating back to this fragment would trigger this observable again
    val downloadLiveData = MutableLiveData<Event<DownloadItem>>()
    val folderLiveData = MutableLiveData<Event<String>>()
    val networkExceptionLiveData = MutableLiveData<Event<UnchainedNetworkException>>()
    val linkLiveData = MutableLiveData<Event<Link>>()
    val toastLiveData = MutableLiveData<Event<String>>()
    val debridLinkResult = MutableLiveData<Event<DebridLinkAddResult>>()

    fun sendMagnetToDebridLink(magnet: String) {
        if (!magnet.isMagnet()) {
            debridLinkResult.postEvent(DebridLinkAddResult.InvalidMagnet)
            return
        }
        if (!debridLinkRepository.isConfigured()) {
            debridLinkResult.postEvent(DebridLinkAddResult.MissingKey)
            return
        }
        viewModelScope.launch {
            val trimmed = magnet.trim()
            val target = if (trimmed.matches("^[a-fA-F0-9]{40}$".toRegex()) || trimmed.matches("^[a-zA-Z2-7]{32}$".toRegex())) {
                "magnet:?xt=urn:btih:$trimmed"
            } else trimmed

            when (val res = debridLinkRepository.addMagnet(target)) {
                is EitherResult.Success -> debridLinkResult.postEvent(DebridLinkAddResult.Added)
                is EitherResult.Failure -> {
                    val err = (res.failure as? com.github.livingwithhippos.unchained.data.model.DebridLinkError)?.error
                    if (!err.isNullOrBlank()) {
                        debridLinkResult.postEvent(DebridLinkAddResult.FailedWithError(err))
                    } else {
                        debridLinkResult.postEvent(DebridLinkAddResult.Failed)
                    }
                }
            }
        }
    }

    fun sendHostLinkToDebridLink(url: String) {
        if (!debridLinkRepository.isConfigured()) {
            debridLinkResult.postEvent(DebridLinkAddResult.MissingKey)
            return
        }
        viewModelScope.launch {
            when (debridLinkRepository.addHostLink(url)) {
                is EitherResult.Success -> debridLinkResult.postEvent(DebridLinkAddResult.HostAdded)
                is EitherResult.Failure -> debridLinkResult.postEvent(DebridLinkAddResult.Failed)
            }
        }
    }

    fun sendTorrentToDebridLink(binaryTorrent: ByteArray, fileName: String = "upload.torrent") {
        if (!debridLinkRepository.isConfigured()) {
            debridLinkResult.postEvent(DebridLinkAddResult.MissingKey)
            return
        }
        viewModelScope.launch {
            when (debridLinkRepository.addTorrent(binaryTorrent, fileName)) {
                is EitherResult.Success -> debridLinkResult.postEvent(DebridLinkAddResult.TorrentAdded)
                is EitherResult.Failure -> debridLinkResult.postEvent(DebridLinkAddResult.Failed)
            }
        }
    }

    fun getSelectedProvider(): DebridProvider =
        DebridProvider.fromId(preferences.getString(DEBRID_PROVIDER_PREF_KEY, DebridProvider.REAL_DEBRID.id))

    fun isDebridLinkConfigured() = debridLinkRepository.isConfigured()

    fun fetchUnrestrictedLink(link: String, password: String?, remote: Int? = null) {
        viewModelScope.launch {
            // check if it's a folder link
            var isFolder = false
            for (hostRegex in hostsRepository.getFoldersRegex()) {
                val m: Matcher = Pattern.compile(hostRegex.regex).matcher(link)
                if (m.matches()) {
                    isFolder = true
                    folderLiveData.postEvent(link)
                    break
                }
            }
            if (!isFolder) {
                val response =
                    unrestrictRepository.getEitherUnrestrictedLink(link, password, remote)
                when (response) {
                    is EitherResult.Failure -> networkExceptionLiveData.postEvent(response.failure)
                    is EitherResult.Success -> downloadLiveData.postEvent(response.success)
                }
            }
        }
    }

    fun uploadContainer(container: ByteArray) {
        viewModelScope.launch {
            when (val fileList = unrestrictRepository.uploadContainer(container)) {
                is EitherResult.Failure -> {
                    networkExceptionLiveData.postEvent(fileList.failure)
                }
                is EitherResult.Success -> {
                    linkLiveData.postEvent(Link.Container(fileList.success))
                }
            }
        }
    }

    fun unrestrictContainer(link: String) {
        viewModelScope.launch {
            val links = unrestrictRepository.getContainerLinks(link)
            if (links != null) linkLiveData.postEvent(Link.Container(links))
            else linkLiveData.postEvent(Link.RetrievalError)
        }
    }

    fun fetchUploadedTorrent(binaryTorrent: ByteArray) {
        viewModelScope.launch {
            val availableHosts = torrentsRepository.getAvailableHosts()
            if (availableHosts.isNullOrEmpty()) {
                Timber.e("Error fetching available hosts")
            } else {
                val uploadedTorrent =
                    torrentsRepository.addTorrent(binaryTorrent, availableHosts.first().host)
                when (uploadedTorrent) {
                    is EitherResult.Failure -> {
                        networkExceptionLiveData.postEvent(uploadedTorrent.failure)
                    }
                    is EitherResult.Success -> {
                        // todo: add checks for already chosen torrent/magnet (if possible),
                        // otherwise we get
                        // multiple downloads
                        linkLiveData.postEvent(Link.Torrent(uploadedTorrent.success))
                    }
                }
            }
        }
    }

    /**
     * This function is used to manage multiple toast spawning from different parts of the logic to
     * avoid the queue getting too long and a lot of messages being shown, see the collect on the
     * fragment
     */
    fun postMessage(message: String) {
        toastLiveData.postEvent(message)
    }
}

sealed class DebridLinkAddResult {
    data object Added : DebridLinkAddResult()
    data object HostAdded : DebridLinkAddResult()
    data object TorrentAdded : DebridLinkAddResult()
    data object Failed : DebridLinkAddResult()
    data class FailedWithError(val error: String) : DebridLinkAddResult()
    data object MissingKey : DebridLinkAddResult()
    data object InvalidMagnet : DebridLinkAddResult()
}

sealed class Link {
    data class Host(val link: String) : Link()

    data class Torrent(val upload: UploadedTorrent) : Link()

    data class Container(val links: List<String>) : Link()

    data object RetrievalError : Link()
}
