package com.github.livingwithhippos.unchained.lists.model

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.data.model.toTorrentItem
import com.github.livingwithhippos.unchained.data.repository.DebridLinkRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.utilities.DebridProvider
import com.github.livingwithhippos.unchained.utilities.EitherResult
import java.io.IOException
import retrofit2.HttpException

private const val TORRENT_STARTING_PAGE_INDEX = 1

/**
 * Paging Source Using Paging V3. See
 * https://github.com/android/architecture-components-samples/tree/main/PagingWithNetworkSample for
 * a sample
 */
class TorrentPagingSource(
    private val torrentsRepository: TorrentsRepository,
    private val query: String,
    private val provider: DebridProvider = DebridProvider.REAL_DEBRID,
    private val debridLinkRepository: DebridLinkRepository? = null,
) : PagingSource<Int, TorrentItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TorrentItem> {
        val page = params.key ?: TORRENT_STARTING_PAGE_INDEX

        return try {
            val response: List<TorrentItem> =
                if (provider == DebridProvider.DEBRID_LINK && debridLinkRepository != null) {
                    if (page > TORRENT_STARTING_PAGE_INDEX) {
                        emptyList()
                    } else {
                        when (val seedbox = debridLinkRepository.getSeedboxList()) {
                            is EitherResult.Success -> {
                                val items = seedbox.success.map { it.toTorrentItem() }
                                if (query.isBlank()) items
                                else items.filter { it.filename.contains(query, ignoreCase = true) }
                            }
                            is EitherResult.Failure -> emptyList()
                        }
                    }
                } else {
                    if (query.isBlank()) torrentsRepository.getTorrentsList(null, page, params.loadSize)
                    else
                        torrentsRepository.getTorrentsList(null, page, params.loadSize).filter {
                            it.filename.contains(query, ignoreCase = true)
                        }
                }

            LoadResult.Page(
                data = response,
                prevKey = if (page == TORRENT_STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (response.isEmpty() || (provider == DebridProvider.DEBRID_LINK)) null else page + 1,
            )
        } catch (exception: IOException) {
            return LoadResult.Error(exception)
        } catch (exception: HttpException) {
            return LoadResult.Error(exception)
        }
    }

    override val jumpingSupported: Boolean = true

    override fun getRefreshKey(state: PagingState<Int, TorrentItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }
}
