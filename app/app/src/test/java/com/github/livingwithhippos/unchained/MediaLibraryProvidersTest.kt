package com.github.livingwithhippos.unchained

import com.github.livingwithhippos.unchained.search.builtin.providers.AudioBookBayProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.EpubLibreProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.InternetArchiveProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.LinuxTrackerProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.MikanProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.SubsPleaseProvider
import com.github.livingwithhippos.unchained.search.builtin.providers.TokyoToshokanProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the media-library scrapers ported from prajwalch/TorrentSearch.
 *
 * The bodies in [MIKAN_HTML], [SUBS_JSON], [ABB_HTML], [EPUB_JSON] and [IA_JSON] are live captures
 * (trimmed); the TokyoToshokan and LinuxTracker bodies are synthetic because both hosts answer the
 * capture with an interstitial instead of the listing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaLibraryProvidersTest {

    @Test
    fun `mikan parses a search row`() {
        val items = MikanProvider.parse(MIKAN_HTML)
        assertTrue(items.isNotEmpty())
        assertEquals("Mikan", items.first().provider)
        assertTrue(items.first().magnets.first().startsWith("magnet:?xt=urn:btih:"))
    }

    @Test
    fun `subsplease parses one row per resolution`() {
        val items = SubsPleaseProvider.parse(SUBS_JSON)
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.name.endsWith("[480p]") })
        assertTrue(items.first().magnets.first().contains("xl="))
    }

    @Test
    fun `tokyotoshokan pairs its two rows per torrent`() {
        val items = TokyoToshokanProvider.parse(TOKYO_HTML)
        assertEquals(2, items.size)
        assertTrue(items.first().magnets.first().startsWith("magnet:?xt=urn:btih:"))
    }

    @Test
    fun `audiobookbay parses a result row`() {
        val items = AudioBookBayProvider.parse(ABB_HTML)
        assertTrue(items.isNotEmpty())
        assertEquals("AudioBookBay", items.first().provider)
        assertTrue(items.first().link!!.contains("/abss/"))
    }

    @Test
    fun `epublibre parses the contenido html`() {
        val items = EpubLibreProvider.parse(EPUB_JSON)
        assertTrue(items.isNotEmpty())
        assertTrue(items.first().name.contains(" - Cristina"))
    }

    @Test
    fun `internetarchive parses the docs array`() {
        val items = InternetArchiveProvider.parse(IA_JSON)
        assertEquals(2, items.size)
        assertTrue(items.first().magnets.first().contains("1dc689206d6f6ef6021d558d95350ed09004b40c"))
    }

    @Test
    fun `linuxtracker parses a listing row`() {
        val items = LinuxTrackerProvider.parse(LT_HTML)
        assertTrue(items.isNotEmpty())
        assertEquals("12", items.first().seeders)
        assertEquals("LinuxTracker", items.first().provider)
    }

    private companion object {
        // Live capture, trimmed to one row.
        val MIKAN_HTML = "<table><tbody>\n<tr class=\"js-search-results-row\" data-itemindex=\"0\"\n                    style=\"\">\n                    <td>\n                        <input type=\"checkbox\" class=\"js-episode-select\" data-magnet=\"magnet:?xt=urn:btih:92bc41f1c587a2feca840f78b835efd7c8c65793\"\n                            aria-label=\"选择此行\" />\n                    </td>\n                    <td>\n                        <a href=\"/Home/Episode/92bc41f1c587a2feca840f78b835efd7c8c65793\" target=\"_blank\"\n                            class=\"magnet-link-wrap\">[&#x685C;&#x90FD;&#x5B57;&#x5E55;&#x7EC4;] &#x6B7B;&#x795E; &#x5343;&#x5E74;&#x8840;&#x6218;&#x7BC7;-&#x7978;&#x8FDB;&#x8C2D;- / Bleach&#xFF1A; Sennen Kessen Hen - Kashin Tan [08][1080P][&#x7E41;&#x4F53;&#x5185;&#x5D4C;]</a>\n                        <a data-clipboard-text=\"magnet:?xt=urn:btih:92bc41f1c587a2feca840f78b835efd7c8c65793\" class=\"js-magnet magnet-link\">[复制磁连]</a>\n                    </td>\n                    <td>951.24 MB</td>\n                    <td>2026/09/20 03:29</td>\n                    <td>\n                        <a\n                            href=\"/Download/20260920/92bc41f1c587a2feca840f78b835efd7c8c65793.torrent\">\n                            <img src=\"/images/download_icon_blue.svg\" style=\"margin-left: 2px;width: 20px;height:15px;\">\n                        </a>\n                    </td>\n                    <td>\n                        <a target=\"_blank\"\n                            href=\"https://keepshare.org/rclukaia/magnet%3A%3Fxt%3Durn%3Abtih%3A92bc41f1c587a2feca840f78b835efd7c8c65793\">\n                            <i class=\"fa fa-play-circle\" style=\"color: #47c1c5; margin-left: 4px; font-size: 18px;\"></i>\n                        </a>\n                    </td>\n                </tr>\n</tbody></table>"
        // Live capture, trimmed to the first show with two downloads.
        val SUBS_JSON = "{\"Bleach - Sennen Kessen Hen - 48\": {\"time\": \"09/12/26\", \"release_date\": \"Sat, 12 Sep 2026 17:04:16 +0000\", \"show\": \"Bleach - Sennen Kessen Hen\", \"episode\": \"48\", \"downloads\": [{\"res\": \"480\", \"magnet\": \"magnet:?xt=urn:btih:QVIDYXLJVTJMRNW5JQ6TYFZE7EPQAR47&dn=%5BSubsPlease%5D%20Bleach%20-%20Sennen%20Kessen%20Hen%20-%2048%20%28480p%29%20%5BFE13D8CC%5D.mkv&xl=386277260&tr=http%3A%2F%2Fnyaa.tracker.wf%3A7777%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=http%3A%2F%2Ftracker.mywaifu.best%3A6969%2Fannounce&tr=https%3A%2F%2Ftracker.zhuqiy.com%3A443%2Fannounce&tr=udp%3A%2F%2Ftracker.tryhackx.org%3A6969%2Fannounce&tr=udp%3A%2F%2Fretracker.hotplug.ru%3A2710%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.com%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.beeimg.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ft.overflow.biz%3A6969%2Fannounce&tr=wss%3A%2F%2Ftracker.openwebtorrent.com\"}, {\"res\": \"720\", \"magnet\": \"magnet:?xt=urn:btih:7DNQZORMFRZIHXGQA2S446VYKP7D7PIG&dn=%5BSubsPlease%5D%20Bleach%20-%20Sennen%20Kessen%20Hen%20-%2048%20%28720p%29%20%5B469A8D28%5D.mkv&xl=742324639&tr=http%3A%2F%2Fnyaa.tracker.wf%3A7777%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=http%3A%2F%2Ftracker.mywaifu.best%3A6969%2Fannounce&tr=https%3A%2F%2Ftracker.zhuqiy.com%3A443%2Fannounce&tr=udp%3A%2F%2Ftracker.tryhackx.org%3A6969%2Fannounce&tr=udp%3A%2F%2Fretracker.hotplug.ru%3A2710%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.com%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.beeimg.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ft.overflow.biz%3A6969%2Fannounce&tr=wss%3A%2F%2Ftracker.openwebtorrent.com\"}], \"xdcc\": \"%22%5BSubsPlease%5D+Bleach+-+Sennen+Kessen+Hen+-+48%22\", \"image_url\": \"/wp-content/uploads/2026/09/135431.jpg\", \"page\": \"bleach-sennen-kessen-hen\"}}"
        // Synthetic listing (the host is behind Cloudflare).
        val TOKYO_HTML = "<table class=\"listing\"><tbody>\n<tr><th>Type</th><th>Name</th><th>Size</th><th>Date</th><th>Seed</th><th>Leech</th><th>Web</th></tr>\n<tr>\n<td><a href=\"/?cat=1\">Anime</a></td>\n<td class=\"desc-top\"><a href=\"magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01&amp;dn=Sample\">magnet</a><a href=\"/details.php?id=123\">Some Anime Episode 01 [1080p]</a></td>\n<td class=\"web\"><a href=\"/details.php?id=123\">details</a></td>\n</tr>\n<tr>\n<td class=\"desc-bot\">Anime | Size: 1.4 GB | Date: 2024-05-01 12:00 UTC</td>\n<td class=\"stats\"><span>42</span><span>7</span></td>\n</tr>\n<tr>\n<td><a href=\"/?cat=1\">Anime</a></td>\n<td class=\"desc-top\"><a href=\"magnet:?xt=urn:btih:1111111111111111111111111111111111111111&amp;dn=Second\">magnet</a><a href=\"/details.php?id=456\">Another Anime 02 [720p]</a></td>\n<td class=\"web\"><a href=\"/details.php?id=456\">details</a></td>\n</tr>\n<tr>\n<td class=\"desc-bot\">Anime | Size: 700 MB | Date: 2024-06-02 08:00 UTC</td>\n<td class=\"stats\"><span>10</span><span>1</span></td>\n</tr>\n</tbody></table>\n"
        // Live capture, trimmed to the first post.
        val ABB_HTML = "<div class=\"post\"><div class=\"postTitle\"><h2><a href=\"/abss/a-short-history-of-irish-literature-seamus-deane/\" rel=\"bookmark\">A Short History of Irish Literature - Seamus Deane</a></h2></div><div class=\"postInfo\">Category: Misc. Non-fiction&nbsp; <br />Language: English<span style=\"margin-left:100px;\">Keywords: Literary History&nbsp </span><br /></div><div class=\"postContent\"><div class=\"center\">\n<p class=\"center\">Shared by:<a href=\"/member/users/index?&amp;mode=userinfo&amp;username=a900\">a900</a></p>\n<p class=\"center\"><a href=\"https://audiobookbay.lu/abss/a-short-history-of-irish-literature-seamus-deane/\"><img src=\"https://m.media-amazon.com/images/I/816mKzHlsmL._SL1500_.jpg\" alt=\"Seamus Deane A Short History of Irish Literature\" width=\"250\" /></a></p>\n</div>\n<p style=\"center;\">\n<p style='text-align:center;'>Posted: 6 Oct 2026<br />Format: <span style='color:#a00;'>M4B</span> / Bitrate: <span style='color:#a00;'>128 Kbps</span><br />File Size: <span style='color:#00f;'>712.46</span> MBs</p>\n</div>\n</div>"
        // Live capture, trimmed to the first book card.
        val EPUB_JSON = "{\"contenido\": \"<div class=\\\"row\\\"><div class=\\\"span2 pad_t_20 ali_centro txt_blanco\\\" style=\\\"height:201px;width:138px\\\"><div class=\\\"borde-catalogo portada-catalogo txt_pequena\\\"><a id=\\\"stk\\\" href=\\\"https://www.epublibre.org/libro/detalle/28357\\\" class=\\\"negrita popover-libro\\\" rel=\\\"popover\\\" title=\\\"Arendt. Estar (políticamente) en el mundo\\\" data-content='<div class=\\\"txt_pequena\\\"><div class=\\\"pull-right sprite idioma_1\\\"></div><img class=\\\"sprite user_silhouette\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> Cristina Sánchez Muñoz<br/><img class=\\\"sprite node-tree\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> Descubrir la filosofía [23]<br/><div class=\\\"row-fluid\\\"><div class=\\\"span3\\\"><img class=\\\"sprite icon-revision\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> 1.3</div><div class=\\\"span3\\\"><img class=\\\"sprite icon-paginas\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> 145</div><div class=\\\"pull-left pop-puntuacion\\\"><img class=\\\"sprite icon-star\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> 9 (10)</div><div class=\\\"pull-left\\\"><img class=\\\"sprite icon-thumb-up\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\" /> 187</div></div>'><div class=\\\"texto-portada\\\"><h2>Cristina Sánchez Muñoz</h2><h1>Arendt. Estar (políticamente) en el mundo</h1></div><div class=\\\"portada-catalogo\\\"><img id=\\\"catalog\\\" src=\\\"https://images.epublibre.org/libros/28357.jpg\\\" alt=\\\"\\\" height=\\\"201\\\" width=\\\"132\\\"/></div><div class=\\\"etiqueta6\\\"><img class=\\\"sprite estado-2\\\" src=\\\"https://www.epublibre.org/img/vacio.png\\\"/></div></a></div>\"}"
        // Live capture, trimmed to two docs that carry a btih.
        val IA_JSON = "{\"responseHeader\": {\"status\": 0, \"QTime\": 146, \"params\": {\"query\": \"title:ubuntu\", \"qin\": \"title:ubuntu\", \"fields\": \"title,item_size,publicdate,mediatype,identifier,btih\", \"wt\": \"json\", \"rows\": 5, \"start\": 0}}, \"response\": {\"numFound\": 2940, \"start\": 0, \"docs\": [{\"btih\": \"1dc689206d6f6ef6021d558d95350ed09004b40c\", \"identifier\": \"ubuntu-20.04.3-desktop-amd64_202205\", \"item_size\": 3071986961, \"mediatype\": \"software\", \"publicdate\": \"2022-05-05T06:23:49Z\", \"title\": \"ubuntu-20.04.3-desktop-amd64\"}, {\"btih\": \"b9bf6c1880cfe7f683933f99c4efc7e37e6cc74f\", \"identifier\": \"youtube-15hB1cXmHRE\", \"item_size\": 38525712, \"mediatype\": \"movies\", \"publicdate\": \"2025-06-24T05:08:33Z\", \"title\": \"Ubuntu Edge Design Blog: Sketches\"}]}}"
        // Synthetic listing (the host answers the capture with an interstitial).
        val LT_HTML = "<table class=\"torrent-table\"><tbody>\n<tr><th>Name</th><th>Comments</th><th>Date</th><th>Size</th><th>Seeds</th><th>Leeches</th></tr>\n<tr>\n<td class=\"torrent-name-cell\"><a class=\"torrent-name\" href=\"/torrents/5b1e0d988fc7a0c9e99bd852071681a59974b39f/\">Ubuntu 24.04 Desktop amd64</a></td>\n<td>0</td>\n<td>May 1, 2024</td>\n<td>2.50 GB</td>\n<td class=\"seeds\"><strong>12</strong></td>\n<td class=\"leeches\"><strong>3</strong></td>\n</tr>\n</tbody></table>\n"
    }
}
