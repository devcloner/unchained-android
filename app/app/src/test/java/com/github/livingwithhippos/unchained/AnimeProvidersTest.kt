package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.providers.AniLibriaProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.AniRenaProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.AnimeToshoProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.BangumiMoeProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.DmhyProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.NyaaProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.SukebeiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the ported anime providers. Nyaa/Sukebei/AnimeTosho/Dmhy/BangumiMoe hosts sit
 * behind a network filter (UniFi content filter returns an interstitial), so their bodies are
 * synthetic but follow the upstream-documented markup/JSON exactly. AniRena and AniLibria bodies
 * were captured live with curl.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimeProvidersTest {

    @Test
    fun `nyaa parses a torrent-list row`() {
        // Synthetic: https://nyaa.si is blocked by the local content filter.
        val body = """
            <table class="torrent-list">
              <tbody>
                <tr>
                  <td class="text-center"><a href="/?c=1_2" title="Anime - English-translated">Anime</a></td>
                  <td colspan="2"><a href="/view/1234567" title="[SubsPlease] One Piece - 1080p">[SubsPlease] One Piece - 1080p</a><a class="comments" href="/view/1234567#comments">1</a></td>
                  <td><a href="/download/1234567.torrent">Torrent</a><a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=One+Piece">Magnet</a></td>
                  <td>1.4 GiB</td>
                  <td data-timestamp="1715000000">2024-05-06</td>
                  <td>10</td>
                  <td>2</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val items = NyaaProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("[SubsPlease] One Piece - 1080p", first.name)
        assertEquals("https://nyaa.si/view/1234567", first.link)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertEquals("1.4 GiB", first.size)
        assertNotNull(first.addedDate)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("Nyaa", first.provider)
    }

    @Test
    fun `sukebei parses a torrent-list row`() {
        // Synthetic: https://sukebei.nyaa.si is blocked by the local content filter.
        val body = """
            <table class="torrent-list">
              <tbody>
                <tr>
                  <td class="text-center"><a href="/?c=1_1" title="Art - Anime">Art</a></td>
                  <td colspan="2"><a href="/view/7654321" title="Sample Release">Sample Release</a><a class="comments" href="/view/7654321#comments">3</a></td>
                  <td><a href="/download/7654321.torrent">Torrent</a><a href="magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&amp;dn=Sample">Magnet</a></td>
                  <td>700 MiB</td>
                  <td data-timestamp="1715000000">2024-05-06</td>
                  <td>5</td>
                  <td>1</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val items = SukebeiProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Sample Release", first.name)
        assertEquals("https://sukebei.nyaa.si/view/7654321", first.link)
        assertEquals("5", first.seeders)
        assertTrue(first.magnets.first().contains("0123456789abcdef0123456789abcdef01234567"))
        assertEquals("Sukebei", first.provider)
    }

    @Test
    fun `anilibria parses a release torrents payload`() {
        // Captured live from https://anilibria.top/api/v1/anime/torrents/release/413 (trimmed).
        val body = """
            [
              {
                "id": 6892,
                "hash": "5bc94db3566c3934bff688fd3b7a861c4ac1c862",
                "size": 52787876553,
                "label": "Naruto- Shippuuden - AniLiberty.TOP [HDTVRip 720p][AVC][370-500]",
                "magnet": "magnet:?xt=urn:btih:5bc94db3566c3934bff688fd3b7a861c4ac1c862&dn=Naruto+Shippuuden",
                "seeders": 10,
                "leechers": 2,
                "created_at": "2019-05-01T17:53:00+00:00",
                "release": {
                  "id": 413,
                  "name": { "main": "Наруто Ураганные хроники", "english": "Naruto: Shippuuden" },
                  "alias": "naruto-shippuuden-naruto-uragannye-khroniki"
                }
              }
            ]
        """.trimIndent()

        val items = AniLibriaProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Naruto- Shippuuden - AniLiberty.TOP [HDTVRip 720p][AVC][370-500]", first.name)
        assertEquals("https://www.anilibria.top/anime/releases/release/naruto-shippuuden-naruto-uragannye-khroniki", first.link)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertNotNull(first.addedDate)
        assertTrue(first.magnets.first().contains("5bc94db3566c3934bff688fd3b7a861c4ac1c862"))
        assertEquals("AniLibria", first.provider)
    }

    @Test
    fun `anirena parses a live results row`() {
        // Captured live from https://anirena.com?q=one+piece&page=1 (trimmed to one row).
        val body = """
            <table class="tl-table">
              <tbody>
                <tr data-created-ts="1790543557" data-torrent-id="01a0e4b6-73e3-705f-a463-adbb35038c4b">
                  <td title="Anime / Subtitle(s) and/or Audio(s)" class="col-cat"></td>
                  <td class="col-name">
                    <div class="tl-name-wrap">
                      <a class="tl-group" href="https://erai-raws.anirena.com" rel="noopener" target="_blank">[Erai-raws]</a>
                      <a class="tl-torrent-name tl-td-trigger" data-torrent-id="01a0e4b6-73e3-705f-a463-adbb35038c4b" href="/torrent/01a0e4b6-73e3-705f-a463-adbb35038c4b">One Piece - 1180 [1080p CR WEBRip HEVC AAC][MultiSub][F01B2367]</a>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val items = AniRenaProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("One Piece - 1180 [1080p CR WEBRip HEVC AAC][MultiSub][F01B2367]", first.name)
        assertEquals("https://anirena.com/torrent/01a0e4b6-73e3-705f-a463-adbb35038c4b", first.link)
        assertNotNull(first.addedDate)
        assertEquals("AniRena", first.provider)
    }

    @Test
    fun `animetosho parses a home-list entry`() {
        // Synthetic: https://animetosho.org is blocked by the local content filter.
        val body = """
            <div class="home_list_entry">
              <div class="link"><a href="/view/naruto-1080p">[SubsPlease] Naruto - 1080p</a></div>
              <div class="links">
                <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=Naruto">Magnet</a>
                <a class="dllink" href="/download/naruto">Torrent</a>
                <span title="Stats">[12↑/3↓]</span>
              </div>
              <div class="size">1.4 GiB</div>
              <div class="date" title="Date/time submitted: 27/7/2024, 01:34">2024-07-27</div>
            </div>
        """.trimIndent()

        val items = AnimeToshoProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("[SubsPlease] Naruto - 1080p", first.name)
        assertEquals("https://animetosho.org/view/naruto-1080p", first.link)
        assertEquals("12", first.seeders)
        assertEquals("3", first.leechers)
        assertEquals("1.4 GiB", first.size)
        assertEquals("2024-07-27T00:00:00Z", first.addedDate)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("AnimeTosho", first.provider)
    }

    @Test
    fun `bangumimoe parses a search payload`() {
        // Synthetic: https://bangumi.moe is blocked by the local content filter.
        val body = """
            {
              "torrents": [
                {
                  "_id": "abc123",
                  "title": "[SubsPlease] Naruto - 1080p",
                  "magnet": "magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&dn=Naruto",
                  "size": "1500000000",
                  "seeders": 12,
                  "leechers": 3,
                  "publish_time": "2024-05-01T12:00:00.000Z"
                }
              ]
            }
        """.trimIndent()

        val items = BangumiMoeProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("[SubsPlease] Naruto - 1080p", first.name)
        assertEquals("https://bangumi.moe/torrent/abc123", first.link)
        assertEquals("12", first.seeders)
        assertEquals("3", first.leechers)
        assertNotNull(first.addedDate)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("BangumiMoe", first.provider)
    }

    @Test
    fun `dmhy parses a topic-list row`() {
        // Synthetic: https://share.dmhy.org is blocked by the local content filter.
        val body = """
            <table id="topic_list">
              <tbody>
                <tr>
                  <td><span>2024/05/01 12:00</span></td>
                  <td><a href="/topics/list/sort_id/2" class="sort-2">动画</a></td>
                  <td class="title"><a href="/topics/view/1">[Naruto] 1080p</a></td>
                  <td><a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=Naruto">磁</a></td>
                  <td>1.4GB</td>
                  <td>10</td>
                  <td>2</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val items = DmhyProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("[Naruto] 1080p", first.name)
        assertEquals("https://share.dmhy.org/topics/view/1", first.link)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertEquals("1.4GB", first.size)
        assertEquals("2024-05-01T00:00:00Z", first.addedDate)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("Dmhy", first.provider)
    }
}
