package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.ProviderGroup
import com.github.livingwithhippos.unchained.search.builtin.providers.BitSearchProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.EztvProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.KnabenProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.NekoBtProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.NoNameClubProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.RutorProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TheRarBgProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the media/general providers ported from prajwalch/TorrentSearch.
 *
 * The upstream sites are unreachable from CI (their domains resolve to RFC-5737/3849 blackhole
 * addresses), so every fixture below is a minimal, synthetic body matching the layout the upstream
 * selectors document. Robolectric supplies a real `org.json` implementation for the Knaben test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrentMediaProvidersTest {

    // Knaben: official JSON API. Synthetic body; real endpoint is POST https://api.knaben.org/v1.
    private val knabenBody = """
        {
          "hits": [
            {
              "title": "Ubuntu 24.04 Desktop amd64",
              "hash": "abcdef0123456789abcdef0123456789abcdef01",
              "magnetUrl": "magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&dn=Ubuntu",
              "bytes": 1288490188,
              "seeders": 150,
              "peers": 10,
              "date": "2025-06-11T06:13:57+00:00",
              "details": "https://knaben.org/search/abcdef"
            }
          ]
        }
    """.trimIndent()

    private val bitSearchBody = """
        <div class="space-y-4"><div><div class="item">
          <div>
            <h3><a href="/torrent/abc">Ubuntu 24.04 Desktop</a></h3>
            <div class="meta">
              <span><span>Apps</span></span>
              <span><span>1.2 GB</span></span>
              <span><span>2/3/2024</span></span>
            </div>
            <div class="swarm">
              <span><span>x</span><span>25</span></span>
              <span><span>y</span><span>4</span></span>
            </div>
          </div>
          <div class="dl">
            <a href="/download/torrent/abc">dl</a>
            <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01">m</a>
          </div>
        </div></div></div>
    """.trimIndent()

    private val nekoBtBody = """
        <table class="table"><tbody>
          <tr><th>h</th></tr>
          <tr>
            <td>1</td><td>cat</td>
            <td><div><div><a href="/torrent/xyz"><span><span>Neko Anime 01</span></span></a></div></div></td>
            <td><div>
              <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef02">m</a>
              <a href="/dl/x.torrent">dl</a>
            </div></td>
            <td><span>1.4 GB</span></td>
            <td><span>2024-05-01 12:00:00</span></td>
            <td><span>30</span></td>
            <td><span>5</span></td>
          </tr>
        </tbody></table>
    """.trimIndent()

    private val theRarBgBody = """
        <table><tbody>
          <tr class="list-entry">
            <td>1</td>
            <td class="cellName"><div><a href="/post/xyz">Ubuntu 24.04</a></div></td>
            <td><a href="/sub/1">Apps</a></td>
            <td data-order="1717200000">Jun 1, 2024</td>
            <td class="sizeCell" data-order="1288490188">1.2 GB</td>
            <td>x</td>
            <td>42</td>
            <td>7</td>
          </tr>
        </tbody></table>
    """.trimIndent()

    private val noNameClubBody = """
        <table class="forumline tablesorter"><tbody>
          <tr class="row1"><th>h</th></tr>
          <tr>
            <td>1</td><td>f</td><td><a href="tracker.php?f=48">Other</a></td>
            <td>d</td><td>e</td>
            <td><u>1.4 GB</u></td><td>g</td><td>h</td>
            <td class="seedmed"><b>12</b></td>
            <td class="leechmed"><b>3</b></td>
            <td>
              <a href="viewtopic.php?t=123"><b>Ubuntu 24.04</b></a>
              <a href="download.php?id=123">dl</a>
            </td>
            <td><u>1717200000</u></td>
          </tr>
        </tbody></table>
    """.trimIndent()

    private val rutorBody = """
        <div id="index"><table><tbody>
          <tr><td>hdr</td></tr>
          <tr>
            <td>12 Май 24</td>
            <td>
              <a href="/download/xyz.torrent">dl</a>
              <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef03">m</a>
              <a href="/torrent/12345">Ubuntu 24.04</a>
            </td>
            <td>cat</td><td>z</td><td>1.40 GB</td>
            <td><span>42</span><span>-</span><span>7</span></td>
          </tr>
        </tbody></table></div>
    """.trimIndent()

    private val eztvBody = """
        <table id="a"><tbody><tr><td>other</td></tr></tbody></table>
        <table><tbody>
          <tr><td>bar</td></tr>
          <tr><td>headers</td></tr>
          <tr>
            <td>1</td>
            <td><a href="/ep/123" class="epinfo">Show S01E01 1080p</a></td>
            <td>
              <a href="magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef04" class="magnet">m</a>
              <a href="/ep/123.torrent">dl</a>
            </td>
            <td>1.2 GB</td><td> </td><td>88</td>
          </tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun `knaben maps the json api hits`() {
        val items = KnabenProvider.parse(knabenBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Ubuntu 24.04 Desktop amd64", first.name)
        assertEquals("150", first.seeders)
        assertEquals("10", first.leechers)
        assertEquals("Knaben", first.provider)
        assertEquals(1288490188.0, first.parsedSize!!, 0.0)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertEquals("https://knaben.org/search/abcdef", first.link)
        assertTrue(first.addedDate!!.startsWith("2025-06-11T06:13:57"))
    }

    @Test
    fun `bitsearch parses the html result cards`() {
        val items = BitSearchProvider.parse(bitSearchBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Ubuntu 24.04 Desktop", first.name)
        assertEquals("25", first.seeders)
        assertEquals("4", first.leechers)
        assertEquals("1.2 GB", first.size)
        assertEquals("https://bitsearch.to/torrent/abc", first.link)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef01"))
        assertTrue(first.addedDate!!.startsWith("2024-02-03"))
    }

    @Test
    fun `nekobt parses the html result table`() {
        val items = NekoBtProvider.parse(nekoBtBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Neko Anime 01", first.name)
        assertEquals("30", first.seeders)
        assertEquals("5", first.leechers)
        assertEquals("1.4 GB", first.size)
        assertEquals("https://nekobt.to/torrent/xyz", first.link)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef02"))
        assertTrue(first.addedDate!!.startsWith("2024-05-01"))
    }

    @Test
    fun `therarbg parses the list rows and keeps the details link`() {
        val items = TheRarBgProvider.parse(theRarBgBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Ubuntu 24.04", first.name)
        assertEquals("42", first.seeders)
        assertEquals("7", first.leechers)
        assertEquals("1.20 GB", first.size)
        assertEquals("https://therarbg.com/post/xyz", first.link)
        // The list page exposes no magnet, so the magnet list is legitimately empty.
        assertTrue(first.magnets.isEmpty())
        assertEquals("2024-06-01T00:00:00Z", first.addedDate)
    }

    @Test
    fun `nonameclub parses the posted form result table`() {
        val items = NoNameClubProvider.parse(noNameClubBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Ubuntu 24.04", first.name)
        assertEquals("12", first.seeders)
        assertEquals("3", first.leechers)
        assertEquals("1.4 GB", first.size)
        assertEquals("https://nnmclub.to/viewtopic.php?t=123", first.link)
        assertTrue(first.magnets.isEmpty())
        assertEquals("2024-06-01T00:00:00Z", first.addedDate)
    }

    @Test
    fun `rutor skips the header row and reads the magnet and russian date`() {
        val items = RutorProvider.parse(rutorBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Ubuntu 24.04", first.name)
        assertEquals("42", first.seeders)
        assertEquals("7", first.leechers)
        assertEquals("1.40 GB", first.size)
        assertEquals("https://rutor.info/torrent/12345", first.link)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef03"))
        assertTrue(first.addedDate!!.startsWith("2024-05-12"))
    }

    @Test
    fun `eztv parses series rows and keeps the id stable`() {
        val items = EztvProvider.parse(eztvBody)

        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("Show S01E01 1080p", first.name)
        assertEquals("88", first.seeders)
        assertEquals("1.2 GB", first.size)
        assertEquals("https://eztvx.to/ep/123", first.link)
        assertTrue(first.magnets.first().contains("abcdef0123456789abcdef0123456789abcdef04"))
        assertNull(first.leechers)

        assertEquals("eztv", EztvProvider.id)
        assertEquals(ProviderGroup.MEDIA, EztvProvider.group)
        assertTrue(EztvProvider.enabledByDefault)
    }
}
