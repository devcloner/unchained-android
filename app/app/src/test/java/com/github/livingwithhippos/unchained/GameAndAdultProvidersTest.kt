package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.providers.BlueRomsProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.FitGirlRepacksProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.MyPornClubProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.XxxClubProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.XxxTrackerProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the ported game/adult providers. The parsers are jsoup only, but [ScrapedItem]
 * is a Parcelable, so the Robolectric runner is used to provide the Android runtime.
 * BlueRoms uses a body captured from the live site; the others are synthetic because the hosts sit
 * behind a network filter (see task report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameAndAdultProvidersTest {

    @Test
    fun `fitgirlrepacks parses a repack article`() {
        val body = """
            <article id="post-1" class="category-lossless-repack">
              <header>
                <h1 class="entry-title"><a href="https://fitgirl-repacks.site/cyberpunk-2077/">Cyberpunk 2077</a></h1>
                <div class="entry-meta">
                  <span class="entry-date"><a href="https://fitgirl-repacks.site/cyberpunk-2077/"><time class="entry-date" datetime="2024-05-01T10:00:00+00:00">May 1, 2024</time></a></span>
                </div>
              </header>
              <div class="entry-content">
                <p>Repack contents...</p>
                <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=Cyberpunk">Magnet</a>
              </div>
            </article>
        """.trimIndent()

        val items = FitGirlRepacksProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Cyberpunk 2077", first.name)
        assertEquals(
            "https://fitgirl-repacks.site/cyberpunk-2077/",
            first.link,
        )
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("FitGirl Repacks", first.provider)
    }

    @Test
    fun `blueroms parses a result card`() {
        // Captured live from https://www.blueroms.ws/search?g=0&p=0&q=mario (trimmed to one card).
        val body = """
            <div class="row">
              <div class="col-xs-12 col-sm-6 col-md-4 col-lg-3">
                <div class="card">
                  <div class="card-image"></div>
                  <div class="card-body">
                    <h4 class="card-title"><a href="/game/gamecube/dance_dance_revolution_mario_mix">Dance Dance Revolution: Mario Mix</a></h4>
                    <p class="card-text">
                      <strong>Platform:</strong>
                      <a href="/gamecube">Nintendo GameCube</a><br>
                      <strong>Size:</strong> 227.0MiB<br>
                      <strong>Release Date:</strong> 2005-10-24<br>
                      <strong>Score:</strong> 6.8
                    </p>
                  </div>
                  <div class="card-footer"><a href="/download/AAAABBBB" class="btn btn-sm">Download</a></div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val items = BlueRomsProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Dance Dance Revolution: Mario Mix - Nintendo GameCube", first.name)
        assertEquals("227.0MiB", first.size)
        assertEquals(227L * 1024 * 1024, first.parsedSize?.toLong())
        assertEquals("https://www.blueroms.ws/download/AAAABBBB", first.link)
        assertEquals("BlueRoms", first.provider)
    }

    @Test
    fun `mypornclub parses a torrent element`() {
        val body = """
            <div class="torrents_list">
              <div class="torrent_element">
                <div class="torrent_element_text_div">
                  <a class="torrent_element_img" href="/x/1">img</a>
                  <a href="/t/123/anal-video"><span class="torrent_element_text_span">Anal Video Title</span></a>
                </div>
                <div class="torrent_element_info">
                  <span class="teiv">uploader</span>
                  <span class="teiv">3 days ago</span>
                  <span class="teiv">Category</span>
                  <span class="teiv">1.4GB</span>
                  <span class="teiv teiv_seeders">12</span>
                  <span class="teiv teiv_leechers">3</span>
                </div>
              </div>
            </div>
        """.trimIndent()

        val items = MyPornClubProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Anal Video Title", first.name)
        assertEquals("12", first.seeders)
        assertEquals("3", first.leechers)
        assertEquals("1.4GB", first.size)
        assertEquals("https://myporn.club/t/123/anal-video", first.link)
        assertEquals("MyPornClub", first.provider)
    }

    @Test
    fun `xxxclub parses a browse table row`() {
        val body = """
            <div class="divtableinside">
              <ul>
                <li>
                  <span>01</span>
                  <span><a href="/torrents/details/123">Anal Video Title</a></span>
                  <span class="siz">1.4 GB</span>
                  <span class="see">10</span>
                  <span class="lee">2</span>
                  <span class="adde">01 Jan 2024 12:00:00</span>
                </li>
              </ul>
            </div>
        """.trimIndent()

        val items = XxxClubProvider.parse(body)

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Anal Video Title", first.name)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertEquals("1.4 GB", first.size)
        assertEquals("https://xxxclub.to/torrents/details/123", first.link)
        assertEquals("XXXClub", first.provider)
    }

    @Test
    fun `xxxtracker parses a results table and skips the header`() {
        val body = """
            <table>
              <tbody>
                <tr><th>Date</th><th>Name</th><th>Size</th><th>Seeds</th></tr>
                <tr>
                  <td>05 &#1071;&#1085;&#1074; 24</td>
                  <td>
                    <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01">M</a>
                    <a href="/d/123">D</a>
                    <a href="/torrent/123">Anal Video Title</a>
                  </td>
                  <td>1.4 GB</td>
                  <td><span class="green">10</span> / <span class="red">2</span></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val items = XxxTrackerProvider.parse(body)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Anal Video Title", first.name)
        assertEquals("1.4 GB", first.size)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertEquals("https://xxxtor.com/torrent/123", first.link)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("XXXTracker", first.provider)
    }
}
