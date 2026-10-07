package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.providers.BtDiggProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.BtsowProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.FileMoodProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.MegaPeerProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentDatabaseProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TorrentDownloadProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.UIndexProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.ZeroMagnetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the ported DHT/index providers. Every body below was captured from the live
 * site (see class comments) unless explicitly marked synthetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrentDigProvidersTest {

    /** Live: https://torrentdownload.info/search?q=ubuntu */
    @Test
    fun `torrentdownload parses rows and builds a magnet from the url hash`() {
        val body = """
            <table class="table2"><tbody>
            <tr><th class="thleft">Fast Links</th></tr><tr bgcolor="#FFFFFF"><td class="tdleft"><div class="tt-name"><a href="/td.php?search=1&amp;keyword=ubuntu"><b>ubuntu</b> Download Anonymously</a></div></td><td class="tdseed">3040 KB/Sec</td><td class="tdnormal">Yesterday</td><td class="tdright">1426</td></tr></table>
            <table class="table2"><tbody>
            <tr><th class="thleft" colspan="5"><h1>1 - 50 of 1,740 for "ubuntu"</h1></th></tr><tr><td class="tdleft"><div class="tt-name"><a href="/A1425E0D6630336CDD9FB320F3FFF1030098975A/Ubuntu-10-04-LTS-x64"><span class="na">Ubuntu</span> 10 04 LTS x64</a> <span class="smallish"> &raquo; Applications</span></div><div class="tt-options"></div></td><td class="tdnormal">1 Year+</td><td class="tdnormal">697.57 MB</td><td class="tdseed">4,341</td><td class="tdleech">2,956</td></tr>
            <tr><td class="tdleft"><div class="tt-name"><a href="/59066769B9AD42DA2E508611C33D7C4480B3857B/ubuntu-17-04-desktop-amd64-iso"><span class="na">ubuntu</span> 17 04 desktop amd64 iso</a> <span class="smallish"> &raquo; Applications</span></div><div class="tt-options"></div></td><td class="tdnormal">1 Year+</td><td class="tdnormal">1.5 GB</td><td class="tdseed">2,408</td><td class="tdleech">124</td></tr>
            </tbody></table>
        """.trimIndent()

        val items = TorrentDownloadProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Ubuntu 10 04 LTS x64", first.name)
        assertEquals("4341", first.seeders)
        assertEquals("2956", first.leechers)
        assertEquals("697.57 MB", first.size)
        assertTrue(first.magnets.first().contains("a1425e0d6630336cdd9fb320f3fff1030098975a"))
        assertEquals("https://torrentdownload.info", TorrentDownloadProvider.url)
    }

    /** Live: https://filemood.com/result?q=ubuntu+in%3Atitle */
    @Test
    fun `filemood parses rows and reads the hash from the details url`() {
        val body = """
            <table><tbody>
            <tr>
              <td class="dn-title"><p class="filedir"><a href="/ubuntu-26.04-desktop-amd64.iso-dafc8c076ca2f3ed376eeae7c76a0d6be2415c45.html" title="Ubuntu 26 04 desktop amd64 iso"><span class="highlated">ubuntu</span><span>-26.04-desktop-amd64.iso</span></a></p></td>
              <td class="dn-status"><p class="text" style="width: 92px;"><b>2019/29</b></p></td>
              <td class="dn-size"><p class="text" style="width: 80px;"><b>6.5 GB</b></p></td>
              <td class="dn-btn"><div style="width: 90px"><a class="btn btn-mini btn-success" href="/ubuntu-26.04-desktop-amd64.iso-dafc8c076ca2f3ed376eeae7c76a0d6be2415c45.html" title="Download now">DOWNLOAD</a></div></td>
            </tr>
            </tbody></table>
        """.trimIndent()

        val items = FileMoodProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("ubuntu-26.04-desktop-amd64.iso", first.name)
        assertEquals("2019", first.seeders)
        assertEquals("29", first.leechers)
        assertEquals("6.5 GB", first.size)
        assertTrue(first.magnets.first().contains("dafc8c076ca2f3ed376eeae7c76a0d6be2415c45"))
        assertEquals("https://filemood.com", FileMoodProvider.url)
    }

    /** Live: https://megapeer.vip/browse.php?search=ubuntu */
    @Test
    fun `megapeer parses rows and converts the russian upload date`() {
        val body = """
            <div id="index"><table width="100%"><tbody>
            <tr class="table_tor"><td width="10px">Добавлен</td><td colspan="2">Название</td><td class="tab1">Размер</td><td class="tab1">Пиры</td></tr>
            <tr class="table_fon">
            <td>10 Ноя 14</td><td colspan="2"><a href="/download/49915"><img src="/pic/d_small.gif" width="13px" height="13px" alt="D"/></a><a href="/torrent/49915/ubuntu-businesspack-1404-i386-amd64-noyabr-2014-pc" class="url">Ubuntu BusinessPack (14.04 [i386 + amd64] [ноябрь]) (2014) PC</a></td>
            <td align="right">4.54 GB</td><td align="center"><img src="/pic/seed.gif" width="5px" height="8px" alt="S"> <font color="#008000">1</font>&nbsp;<img src="/pic/leech.gif" width="5px" height="8px" alt="L"> <font color="#8b0000">0</font></td></tr>
            </tbody></table></div>
        """.trimIndent()

        val items = MegaPeerProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertTrue(first.name.startsWith("Ubuntu BusinessPack"))
        assertEquals("4.54 GB", first.size)
        assertEquals("1", first.seeders)
        assertEquals("0", first.leechers)
        assertEquals("2014-11-10T00:00:00Z", first.addedDate)
        assertTrue(first.link!!.contains("/torrent/49915/"))
        assertEquals("https://megapeer.vip", MegaPeerProvider.url)
    }

    /** Live: https://9mag.net/search?q=ubuntu */
    @Test
    fun `0magnet parses rows and normalizes the compact size`() {
        val body = """
            <table class="table table-hover file-list"><tbody>
            <tr>
                <td class="result-title"><a href="/!lFvU"><mark>ubuntu</mark>-24.04.4-desktop-amd64.iso</a></td>
                <td class="result-meta"><div>6.2GB</div><div class="result-date">2026-08-07</div></td>
            </tr>
            <tr>
                <td class="result-title"><a href="/!k2Z1"><mark>ubuntu</mark>-22.04.5-desktop-amd64.iso</a></td>
                <td class="result-meta"><div>4.44GB</div><div class="result-date">2025-08-08</div></td>
            </tr>
            </tbody></table>
        """.trimIndent()

        val items = ZeroMagnetProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("ubuntu-24.04.4-desktop-amd64.iso", first.name)
        assertEquals("6.2 GB", first.size)
        assertEquals("2026-08-07T00:00:00Z", first.addedDate)
        assertEquals("https://9mag.net/!lFvU", first.link)
        assertEquals("0magnet", ZeroMagnetProvider.id)
    }

    /** Live: POST https://btsow.live/bts/data/api/search */
    @Test
    fun `btsow parses the json api response`() {
        val body = """
            {"code":200,"data":[
              {"hash":"611F70899D4E1D6A9C39CFC925F103DFEF630328","name":"<em>ubuntu</em>-24.04.2-desktop-amd​64.iso","size":6343219200,"lastUpdateTime":1787682423},
              {"hash":"2E8E44068B254814EA1A7D4969A9AF1D78E0F51F","name":"<em>ubuntu</em>-22.04.5-desktop-amd​64.iso","size":4762707968,"lastUpdateTime":1774454693}
            ]}
        """.trimIndent()

        val items = BtsowProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("ubuntu-24.04.2-desktop-amd​64.iso", first.name)
        assertEquals("5.91 GB", first.size)
        assertTrue(first.magnets.first().contains("611f70899d4e1d6a9c39cfc925f103dfef630328"))
        assertTrue(first.link!!.contains("611f70899d4e1d6a9c39cfc925f103dfef630328"))
        assertEquals("https://btsow.live", BtsowProvider.url)
    }

    /** SYNTHETIC: uindex.org is Cloudflare-blocked; body mirrors the upstream documented layout. */
    @Test
    fun `uindex parses rows and keeps the magnet link`() {
        val body = """
            <html><body>
            <table class="sr-table"><tbody>
              <tr>
                <td class="sr-col-name"><a class="sr-torrent-link" href="/torrent/12345">Ubuntu 24.04 ISO</a>
                  <a class="sr-magnet" href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=x">magnet</a></td>
                <td class="sr-col-size">5.0 GB</td>
                <td class="sr-col-uploaded">2 days ago</td>
                <td class="sr-col-seeders"><span class="sr-seed">10</span></td>
                <td class="sr-col-leechers"><span class="sr-leech">2</span></td>
                <td class="sr-col-cat"><a class="sr-cat-badge" href="/c/1">Movies</a></td>
              </tr>
            </tbody></table>
            </body></html>
        """.trimIndent()

        val items = UIndexProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Ubuntu 24.04 ISO", first.name)
        assertEquals("10", first.seeders)
        assertEquals("2", first.leechers)
        assertEquals("5.0 GB", first.size)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("https://uindex.org/torrent/12345", first.link)
    }

    /** SYNTHETIC: developify.ca is Cloudflare-blocked; body mirrors the upstream documented layout. */
    @Test
    fun `torrentdatabase parses rows and builds a magnet from the track link`() {
        val body = """
            <html><body>
            <table class="torrent-table"><tbody>
              <tr>
                <td><a href="/torrent/999">details</a><a href="/track/magnet/abcdef0123456789abcdef0123456789abcdef01?dn=x">Ubuntu ISO</a></td>
                <td><span class="category-bubble">Apps</span></td>
                <td class="size-cell">700 MB</td>
                <td class="date-cell">2024-05-01 12:00:00</td>
                <td><div><span>10</span><span>/</span><span>2</span></div></td>
              </tr>
            </tbody></table>
            </body></html>
        """.trimIndent()

        val items = TorrentDatabaseProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Ubuntu ISO", first.name)
        assertEquals("10", first.seeders)
        assertEquals("700 MB", first.size)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("https://developify.ca/torrent/999", first.link)
    }

    /** SYNTHETIC: btdig.com returned HTTP 429; body mirrors the upstream documented layout. */
    @Test
    fun `btdigg parses rows and keeps the magnet link`() {
        val body = """
            <html><body>
            <div class="one_result"><div>
              <div class="torrent_name"><a href="/search?q=u&amp;p=0">Ubuntu ISO</a></div>
              <div class="torrent_magnet"><div class="fa-magnet"><a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=x">magnet</a></div></div>
              <span class="torrent_size">5.0 GB</span>
              <span class="torrent_age">found 2 days ago</span>
            </div></div>
            </body></html>
        """.trimIndent()

        val items = BtDiggProvider.parse(body)
        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("Ubuntu ISO", first.name)
        assertEquals("5.0 GB", first.size)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("btdigg", BtDiggProvider.id)
    }
}
