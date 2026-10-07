package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.providers.Bt4gProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.OxTorrentProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.ThirteenThirtySevenXProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.Torrent9Provider
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentDownloadsProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentKittyProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentzProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture tests for the torrent-index scrapers. All seven parse HTML with jsoup only, so no
 * Robolectric (and no android runtime) is needed. Fixtures for the sites that were not reachable
 * from the build host are synthetic; they reproduce the exact markup the upstream selectors
 * target. The OxTorrent fixture is a trimmed live response.
 */
class TorrentIndexProvidersTest {

    @Test
    fun `1337x parses a result row`() {
        val items = ThirteenThirtySevenXProvider.parse(FIXTURE_1337X)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04 Desktop", item.name)
        assertEquals("https://1337x.to/torrent/123/ubuntu-2404/", item.link)
        assertEquals("150", item.seeders)
        assertEquals("10", item.leechers)
        assertEquals("5.5 GB", item.size)
        assertTrue(item.parsedSize!! > 0)
        assertEquals("1337x", item.provider)
    }

    @Test
    fun `torrentz parses a result row`() {
        val items = TorrentzProvider.parse(FIXTURE_TORRENTZ)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04 Desktop", item.name)
        assertEquals("https://torrentz2.nz/ubuntu-2404-abcd1234", item.link)
        assertEquals("150", item.seeders)
        assertEquals("10", item.leechers)
        assertEquals("5.5 GB", item.size)
        assertEquals("2025-10-07T00:00:00Z", item.addedDate)
    }

    @Test
    fun `torrent9 parses a result row and converts the french size unit`() {
        val items = Torrent9Provider.parse(FIXTURE_TORRENT9)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04", item.name)
        assertEquals("https://www6.torrent9.to/torrent/123/ubuntu", item.link)
        assertEquals("150", item.seeders)
        assertEquals("10", item.leechers)
        assertEquals("1.4 GB", item.size)
        assertTrue(item.parsedSize!! > 0)
    }

    @Test
    fun `oxtorrent parses a live result row`() {
        val items = OxTorrentProvider.parse(FIXTURE_OXTORRENT)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 10.04 Desktop (32 bits)", item.name)
        assertEquals("https://oxtorrent.co/torrent/48951/ubuntu-10-04-desktop-32-bits", item.link)
        assertEquals("9", item.seeders)
        assertEquals("2", item.leechers)
        assertEquals("700.4 MB", item.size)
    }

    @Test
    fun `bt4g parses a result row and extracts the infohash`() {
        val items = Bt4gProvider.parse(FIXTURE_BT4G)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04 Desktop", item.name)
        assertEquals("150", item.seeders)
        assertEquals("10", item.leechers)
        assertEquals("5.5 GB", item.size)
        assertEquals("2025-10-07T00:00:00Z", item.addedDate)
        assertTrue(item.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
    }

    @Test
    fun `torrentkitty parses a result row and keeps the magnet`() {
        val items = TorrentKittyProvider.parse(FIXTURE_TORRENTKITTY)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04 Desktop", item.name)
        assertEquals("https://torrentkitty.tv/detail/abc", item.link)
        assertEquals("5.5 GB", item.size)
        assertEquals("2025-10-07T00:00:00Z", item.addedDate)
        assertTrue(item.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
    }

    @Test
    fun `torrentdownloads parses a result row`() {
        val items = TorrentDownloadsProvider.parse(FIXTURE_TORRENTDOWNLOADS)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04", item.name)
        assertEquals("https://torrentdownloads.pro/torrent/123/ubuntu", item.link)
        assertEquals("150", item.seeders)
        assertEquals("10", item.leechers)
        assertEquals("5.5 GB", item.size)
    }

    private companion object {
        const val FIXTURE_1337X = """
            <table class="table-list"><tbody>
            <tr>
              <td class="name"><a href="/sub/22/0/">Movies</a><a href="/torrent/123/ubuntu-2404/">Ubuntu 24.04 Desktop</a></td>
              <td class="seeds">150</td>
              <td class="leeches">10</td>
              <td class="size">5.5 GB</td>
              <td class="coll-date">2025-10-07</td>
            </tr>
            </tbody></table>
        """

        const val FIXTURE_TORRENTZ = """
            <div class="results">
            <dl>
              <dt><a href="/ubuntu-2404-abcd1234">Ubuntu 24.04 Desktop</a></dt>
              <dd>
                <span class="s">5.5 GB</span>
                <span class="u">150</span>
                <span class="d">10</span>
                <span class="a"><span title="2025-10-07 12:00:00">7 Oct</span></span>
              </dd>
            </dl>
            </div>
        """

        const val FIXTURE_TORRENT9 = """
            <table><tbody><tr>
              <td><i class="fa fa-desktop"></i><a href="/torrent/123/ubuntu">Ubuntu 24.04</a></td>
              <td>07/10/2025</td>
              <td>1.4 Go</td>
              <td><span class="seed_ok">150</span></td>
              <td>10</td>
            </tr></tbody></table>
        """

        // Trimmed from a live https://oxtorrent.co/recherche/ubuntu response.
        const val FIXTURE_OXTORRENT = """
            <table class="table table-hover">
            <tbody>
            <tr>
              <td width="71%" align="left"><i class="Logiciels"></i>  <a href="/torrent/48951/ubuntu-10-04-desktop-32-bits" title="Ubuntu 10.04 Desktop (32 bits) en Torrent">Ubuntu 10.04 Desktop (32 bits)</a></td>
              <td width="11%" align="left">700.4 MB</td>
              <td width="9%" align="left"><img src="/static/img/uploader.png" alt="seeders">9</td>
              <td width="9%" align="left"><img src="/static/img/downloader.png" alt="leechers">2</td>
            </tr>
            </tbody>
            </table>
        """

        const val FIXTURE_BT4G = """
            <div class="notion-list-item">
              <div class="notion-list-item-title">
                <a href="https://bt4gprx.com/magnet/abcdef0123456789abcdef0123456789abcdef01?dn=Ubuntu">Ubuntu 24.04 Desktop</a>
              </div>
              <div class="notion-list-item-meta">
                <span class="notion-tag">Video</span>
                <span>Creation Time: 2025-10-07</span>
                <span id="seeders">150</span>
                <span id="leechers">10</span>
                <span><b>5.5GB</b></span>
              </div>
            </div>
        """

        const val FIXTURE_TORRENTKITTY = """
            <table id="archiveResult"><tbody>
            <tr><th>Name</th><th>Size</th><th>Date</th><th>Action</th></tr>
            <tr>
              <td class="name">Ubuntu 24.04 Desktop</td>
              <td class="size">5.5 GB</td>
              <td class="date">2025-10-07</td>
              <td class="action">
                <a href="/detail/abc">details</a>
                <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=Ubuntu">magnet</a>
                <a href="https://torrentkitty.tv/download/abc">download</a>
              </td>
            </tr>
            </tbody></table>
        """

        const val FIXTURE_TORRENTDOWNLOADS = """
            <div class="inner_container"><div class="grey_bar3">navigation</div></div>
            <div class="inner_container">
              <div class="grey_bar3">header one</div>
              <div class="grey_bar3">header two</div>
              <div class="grey_bar3">
                <p><a href="/torrent/999/spam">x</a><a href="/torrent/123/ubuntu">Ubuntu 24.04</a></p>
                <span>category</span>
                <span>10</span>
                <span>150</span>
                <span>5.5 GB</span>
              </div>
            </div>
        """
    }
}
