#!/usr/bin/env python3
"""Generate the torrent search provider registry + settings UI from one table.

Single source of truth for: which providers exist, their settings keys, group, NSFW flag,
default state, and display name. Run from the repo root:

    python3 tools/gen_search_providers.py

It rewrites:
  app/app/src/main/java/com/github/livingwithhippos/unchained/search/builtin/TorrentProviderRegistry.kt
  app/app/src/main/res/xml/settings.xml          (block between the SEARCH_PROVIDERS markers)
  app/app/src/main/res/values/strings.xml        (block between the SEARCH_PROVIDERS markers)

Ids are persistent SharedPreferences keys (search_provider_<id> / search_sort_<id>): never
change one that has shipped, or the user's choice resets.
"""

import re
import sys
from pathlib import Path

# id, display name, group, nsfw, enabled by default, Kotlin object
PROVIDERS = [
    ("torrentscsv", "TorrentsCSV", "general", False, True, "TorrentsCsvProvider"),
    ("thepiratebay", "The Pirate Bay", "general", False, True, "PirateBayProvider"),
    ("limetorrents", "LimeTorrents", "general", False, True, "LimeTorrentsProvider"),
    ("1337x", "1337x", "general", False, True, "ThirteenThirtySevenXProvider"),
    ("knaben", "Knaben", "general", False, True, "KnabenProvider"),
    ("bitsearch", "BitSearch", "general", False, True, "BitSearchProvider"),
    ("torrentz", "Torrentz", "general", False, False, "TorrentzProvider"),
    ("torrent9", "Torrent9", "general", False, False, "Torrent9Provider"),
    ("oxtorrent", "OxTorrent", "general", False, False, "OxTorrentProvider"),
    ("bt4g", "BT4G", "general", False, False, "Bt4gProvider"),
    ("torrentkitty", "TorrentKitty", "general", False, False, "TorrentKittyProvider"),
    ("torrentdownloads", "TorrentDownloads", "general", False, False, "TorrentDownloadsProvider"),
    ("torrentdownloadinfo", "TorrentDownload", "general", False, False, "TorrentDownloadProvider"),
    ("torrentdatabase", "TorrentDatabase", "general", False, False, "TorrentDatabaseProvider"),
    ("btdigg", "BTDigg", "general", False, False, "BtDiggProvider"),
    ("btsow", "Btsow", "general", False, False, "BtsowProvider"),
    ("filemood", "FileMood", "general", False, False, "FileMoodProvider"),
    ("megapeer", "MegaPeer", "general", False, False, "MegaPeerProvider"),
    ("uindex", "UIndex", "general", False, False, "UIndexProvider"),
    ("0magnet", "0Magnet", "general", False, False, "ZeroMagnetProvider"),
    ("nekobt", "NekoBT", "general", False, False, "NekoBtProvider"),
    ("therarbag", "TheRarBg", "general", False, False, "TheRarBgProvider"),
    ("nonameclub", "NoNameClub", "general", False, False, "NoNameClubProvider"),
    ("rutorinfo", "Rutor", "general", False, False, "RutorProvider"),
    ("eztv", "Eztv", "media", False, True, "EztvProvider"),
    ("ytsmx", "Yts", "media", False, True, "YtsProvider"),
    ("subsplease", "SubsPlease", "media", False, True, "SubsPleaseProvider"),
    ("internetarchive", "InternetArchive", "media", False, True, "InternetArchiveProvider"),
    ("mikanproject", "Mikan", "media", False, False, "MikanProvider"),
    ("tokyotoshokan", "TokyoToshokan", "media", False, False, "TokyoToshokanProvider"),
    ("audiobookbay", "AudioBookBay", "media", False, False, "AudioBookBayProvider"),
    ("epublibre", "EpubLibre", "media", False, False, "EpubLibreProvider"),
    ("linuxtracker", "LinuxTracker", "media", False, False, "LinuxTrackerProvider"),
    ("fitgirlrepacks", "FitGirl Repacks", "media", False, False, "FitGirlRepacksProvider"),
    ("blueroms", "BlueRoms", "media", False, False, "BlueRomsProvider"),
    ("nyaasi", "Nyaa", "anime", False, True, "NyaaProvider"),
    ("animetosho", "AnimeTosho", "anime", False, True, "AnimeToshoProvider"),
    ("anilibria", "AniLibria", "anime", False, True, "AniLibriaProvider"),
    ("anirena", "AniRena", "anime", False, False, "AniRenaProvider"),
    ("bangumimoe", "BangumiMoe", "anime", False, False, "BangumiMoeProvider"),
    ("dmhy", "Dmhy", "anime", False, False, "DmhyProvider"),
    ("sukebeinyaa", "Sukebei", "anime", True, False, "SukebeiProvider"),
    ("mypornclub", "MyPornClub", "nsfw", True, False, "MyPornClubProvider"),
    ("xxxclub", "XXXClub", "nsfw", True, False, "XxxClubProvider"),
    ("xxxtracker", "XXXTracker", "nsfw", True, False, "XxxTrackerProvider"),
]

GROUPS = [
    ("general", "search_group_general", "General torrent sites"),
    ("media", "search_group_media", "Movies, TV, games, books"),
    ("anime", "search_group_anime", "Anime and Asian media"),
    ("nsfw", "search_group_nsfw", "Adult (needs the switch above)"),
]

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.github.livingwithhippos.unchained.search.builtin"
PKG = "app/app/src/main/java/" + PACKAGE.replace(".", "/")
REGISTRY = ROOT / PKG / "TorrentProviderRegistry.kt"
SETTINGS = ROOT / "app/app/src/main/res/xml/settings.xml"
STRINGS = ROOT / "app/app/src/main/res/values/strings.xml"

MARK_BEGIN = "<!-- SEARCH_PROVIDERS_BEGIN generated by tools/gen_search_providers.py -->"
MARK_END = "<!-- SEARCH_PROVIDERS_END -->"


def xml_escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def registry_kt() -> str:
    imports = "\n".join(f"import {PACKAGE}.providers.{obj}" for _, _, _, _, _, obj in PROVIDERS)
    listing = "\n".join(f"        {obj}," for _, _, _, _, _, obj in PROVIDERS)
    return f"""package com.github.livingwithhippos.unchained.search.builtin

import android.content.SharedPreferences
{imports}

/**
 * Every built-in torrent/magnet search provider, in settings display order.
 *
 * GENERATED by tools/gen_search_providers.py — edit the table there and re-run, do not hand edit.
 */
object TorrentProviderRegistry {{

    val all: List<TorrentProvider> =
        listOf(
{listing}
        )

    val byId: Map<String, TorrentProvider> = all.associateBy {{ it.id }}

    /** The providers the user enabled, honouring the global NSFW switch. */
    fun enabled(prefs: SharedPreferences): List<TorrentProvider> {{
        val nsfw = prefs.getBoolean(KEY_NSFW, false)
        return all.filter {{ provider ->
            prefs.getBoolean(providerKey(provider.id),
                if (provider.id == "thepiratebay")
                    prefs.getBoolean("search_provider_piratebay", provider.enabledByDefault)
                else provider.enabledByDefault) &&
                (!provider.nsfw || nsfw)
        }}
    }}

    fun sortFor(prefs: SharedPreferences, providerId: String): TorrentSort =
        TorrentSort.fromId(prefs.getString(sortKey(providerId), null))

    fun providerKey(id: String) = "search_provider_$id"

    fun sortKey(id: String) = "search_sort_$id"

    const val KEY_NSFW = "search_provider_nsfw"
}}
"""


def settings_block() -> str:
    out = [
        '        <PreferenceCategory android:title="@string/torrent_search_providers">',
        '            <SwitchPreference',
        '                android:defaultValue="false"',
        '                android:summary="@string/search_nsfw_summary"',
        '                android:title="@string/search_nsfw_title"',
        '                app:key="search_provider_nsfw" />',
    ]
    for group_id, title, _ in GROUPS:
        members = [p for p in PROVIDERS if p[2] == group_id]
        if not members:
            continue
        out.append('            <PreferenceCategory android:title="@string/%s">' % title)
        for pid, name, _, _, default, _ in members:
            out.append('                <SwitchPreference')
            out.append('                    android:defaultValue="%s"' % ("true" if default else "false"))
            out.append('                    android:title="%s"' % xml_escape(name))
            out.append('                    app:key="search_provider_%s" />' % pid)
            out.append('                <DropDownPreference')
            out.append('                    android:defaultValue="default"')
            out.append('                    android:entries="@array/search_sort_entries"')
            out.append('                    android:entryValues="@array/search_sort_values"')
            out.append('                    android:title="@string/search_sort_%s"' % pid)
            out.append('                    android:dependency="search_provider_%s"' % pid)
            out.append('                    app:key="search_sort_%s"' % pid)
            out.append('                    app:useSimpleSummaryProvider="true" />')
        out.append('            </PreferenceCategory>')
    out.append('        </PreferenceCategory>')
    return "\n".join(out)


def strings_block() -> str:
    lines = [
        '    <string name="search_nsfw_title" translatable="false">Include adult providers</string>',
        '    <string name="search_nsfw_summary" translatable="false">Also search the NSFW sites you enabled below</string>',
    ]
    for group_id, _, label in GROUPS:
        lines.append('    <string name="%s" translatable="false">%s</string>' % (
            [g[1] for g in GROUPS if g[0] == group_id][0], xml_escape(label)))
    for pid, name, _, _, _, _ in PROVIDERS:
        lines.append('    <string name="search_sort_%s" translatable="false">Sort %s by</string>' % (pid, xml_escape(name)))
    return "\n".join(lines)


def inject(path: Path, block: str) -> None:
    text = path.read_text()
    if MARK_BEGIN not in text or MARK_END not in text:
        sys.exit(f"missing SEARCH_PROVIDERS markers in {path}")
    pattern = re.compile(re.escape(MARK_BEGIN) + r".*?" + re.escape(MARK_END), re.S)
    path.write_text(pattern.sub(MARK_BEGIN + "\n" + block + "\n" + MARK_END, text))
    print(f"updated {path.relative_to(ROOT)}")


def main() -> None:
    ids = [p[0] for p in PROVIDERS]
    assert len(ids) == len(set(ids)), "duplicate provider id"
    REGISTRY.write_text(registry_kt())
    print(f"wrote {REGISTRY.relative_to(ROOT)} ({len(PROVIDERS)} providers)")
    inject(SETTINGS, settings_block())
    inject(STRINGS, strings_block())


if __name__ == "__main__":
    main()
