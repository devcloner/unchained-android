# Unchained multi-debrid overhaul

This fork keeps Unchained's mature Real-Debrid implementation while extracting provider-specific
code and adding Debrid-Link first. The goal is one Android/Android TV app that can receive a magnet,
`.torrent` file, or hoster URL and send it to the selected debrid cloud.

## Fork audit

The upstream fork network was enumerated and provider strings were checked in every fork:

- 70 forks checked.
- 65 were Real-Debrid-only copies.
- 5 added one provider: TorBox, AllDebrid, or Premiumize.
- 0 implemented Debrid-Link.

No existing Debrid-Link fork could be used as a base, so this work starts from current upstream
`LivingWithHippos/unchained-android` and preserves its history.

## Architecture found in upstream

- Retrofit APIs live in `data/remote`; Hilt wiring lives in `di/ApiFactory.kt`.
- Repositories obtain the Real-Debrid token from protobuf DataStore through `BaseRepository`.
- `TorrentsRepository` already implements magnet and `.torrent` flows, but its DTOs and endpoints are
  Real-Debrid-specific.
- The UI/viewmodels inject concrete repositories. Provider choice therefore has to be introduced at
  those boundaries rather than hidden behind endpoint conditionals.
- Search plugins are independent of Real-Debrid. Their magnet result can be routed through the
  provider layer without rewriting search.

## Implemented in the first slice

- `DebridProvider` model and provider preference keys.
- Typed Debrid-Link API v2 interface and response envelope.
- Account, seedbox torrent/file/activity, and downloader models.
- API-key store and repository.
- API-key bearer auth for:
  - `GET /account/infos`
  - `POST /seedbox/add` (magnet, URL, hash)
  - `POST /seedbox/add` (`.torrent` multipart upload)
  - `GET /seedbox/list`
  - `GET /seedbox/activity`
  - torrent removal
  - `POST /downloader/add`
  - `GET /downloader/list`
- Hilt/Retrofit wiring with a distinct base URL.
- Unit tests for envelope success/failure and seedbox JSON parsing.

## TODO

### Provider foundation

- [x] Audit all upstream forks for Debrid-Link or multi-provider support.
- [x] Fork upstream on GitHub and mirror its branches/tags into GitLab.
- [x] Model Real-Debrid and Debrid-Link as explicit providers.
- [x] Add the initial Debrid-Link API client, DTOs, repository, and tests.
- [ ] Introduce provider-neutral torrent/account/downloader interfaces used by viewmodels.
- [ ] Move both providers' credentials to one provider-aware, encrypted persistence layer.

### Debrid-Link account and magnets

- [ ] Add provider selection to onboarding and settings.
- [ ] Add Debrid-Link API-key setup with a link to the official key page and a live account check.
- [ ] Route the existing magnet input screen through the selected provider.
- [ ] Route Android `ACTION_SEND`/`ACTION_VIEW` magnet links directly into Debrid-Link.
- [ ] Add clipboard magnet detection.
- [ ] Add `.torrent` document selection and multipart upload.
- [ ] Add hoster URLs through Debrid-Link's `/downloader/add`.

### Cloud library

- [ ] Show the Debrid-Link seedbox list and live activity.
- [ ] Add file selection for torrents created with `wait=true`.
- [ ] Browse files and stream/download their signed URLs.
- [ ] Refresh expired links and show quota/limits.
- [ ] Delete torrents and downloader links.

### Search and UX

- [x] Integrate TorrentSearch providers (TorrentsCSV and The Pirate Bay JSON APIs) as built-in search.
- [x] Add direct download chooser on search items (Debrid-Link vs Real-Debrid vs Copy).
- [x] Expose Debrid-Link account info screen (premium status, days remaining, points).
- [x] Add seedbox multi-file folder browser for Debrid-Link torrents.
- [x] Post automated AI architecture & code review on GitLab MR !1 and GitHub PR #1.
- [ ] Keep Unchained's plugin search and add a provider-aware “Send magnet” action.
- [ ] Add Torznab/Jackett/Prowlarr search configuration validation.
- [ ] Rename/rebrand the fork without breaking package upgrades during development.
- [ ] Add Compose/provider UI incrementally; do not rewrite working screens wholesale.

## Quality, CI, release

- [x] GitLab repository, Duo auto-review toggle, CI configuration and review policy pushed.
- [x] CI debug APK built for commit `dc8aa489` (job `17004427541`; artifact verified as a valid ZIP containing `debug-1.8.1-dev.apk`).
- [x] CI lint passed for commit `dc8aa489` (job `17004427540`).
- [x] New Debrid-Link unit tests compiled and ran on CI (9 of 10 total tests passed; the other was an upstream Robolectric SDK mismatch).
- [x] Re-run unit tests after pinning `StringResourceTest` to Robolectric-supported SDK 35 and Java 21 test JVM; pipeline 2923000257 passed all jobs (unit tests, lint, debug APK).
- [x] Download latest debug APK artifact to `/home/hoffe/Downloads/apks/unchained-multidebrid-debug-1.8.1-dev-2cdd9f3f.apk`.
- [x] Triggered Duo review on MR !1; reviewer session 9144390 completed.
- [ ] Add MockWebServer contract tests from sanitized Debrid-Link fixtures.
- [ ] Add instrumented tests for share intents and provider switching.
- [ ] Complete privacy/security review before exposing the API-key UI to production.
- [ ] Document a reproducible signed release process; debug builds are already signed with the Android debug key.

## Next development slices

- [ ] Provider selection and credential storage that does not affect Real-Debrid logins.
- [ ] Verify Debrid-Link API behavior against sanitized contract responses; no live key in tests/logs.
- [ ] Upload shared `.torrent` documents to Debrid-Link, route hoster links, and add clipboard detection.
- [ ] Expose Debrid-Link seedbox list, file browser, streaming and signed-link expiry refresh.
- [ ] Route plugin search magnets through the selected provider without breaking Real-Debrid.

## Security rules

- Never log or commit Debrid tokens, API keys, magnet contents, or signed download URLs.
- Authentication uses `Authorization: Bearer …`; never append keys to URLs.
- Validate incoming shared URLs and reject non-magnet values from the torrent path.
- Keep HTTP logging at body level only in explicit debug builds and redact `Authorization`.
- Do not infer undocumented API enum values. Preserve raw values until the provider documents them.
