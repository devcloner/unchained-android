# Unchained — rules for AI agents

Android phone + Android TV client for debrid services. Real-Debrid is the incumbent provider and must
keep working while the app moves to a provider-neutral architecture (Debrid-Link first).

Keep `GEMINI.md`, `AGENTS.md` and `CLAUDE.md` identical: every agent reads one rulebook. When a rule
changes, change all the copies that exist in the same commit.

## Layout

- Gradle project root is `app/`; the Android module is `app/app`.
- Feature code: `app/app/src/main/java/com/github/livingwithhippos/unchained/<feature>/` (view,
  viewmodel, custom views per screen).
- Shared data layer: `data/remote` (Retrofit APIs + helpers), `data/repository`, `data/local` (Room),
  `data/model`; Koin modules in `di/`; utilities in `utilities/`.
- Unit tests `app/app/src/test/java/...`, instrumented tests `app/app/src/androidTest/java/...`.

## Required checks

From `app/`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The build runs on JDK 21, `compileSdk = 37`, `minSdk = 27`, Kotlin/JVM target 11. Never weaken or
remove an existing check to make a change pass.

## Debrid provider invariants

- Provider API DTOs and Retrofit interfaces stay under their provider-specific package.
- ViewModels and UI must not branch on provider HTTP endpoints.
- Debrid-Link uses `https://debrid-link.com/api/v2/` and bearer authentication; its JSON envelope
  `success` flag decides success, so an HTTP 200 can contain an API failure.
- Never log, print, commit, put in URLs, or add to fixtures: API keys, access tokens, refresh tokens,
  magnet contents, signed download URLs, or user account data.
- Add tests for success envelopes, failure envelopes, empty bodies, malformed input, and unknown JSON.
- Preserve Real-Debrid behaviour and add regression coverage when touching shared code.

## Code review

A review of a pull request must follow `.gemini/styleguide.md`. Prefix every finding with its severity
label — `[BLOCKER]`, `[MAJOR]`, `[MINOR]` or `[QUESTION]` — cite `path:line`, describe the concrete
failure (the input that produces wrong behaviour), and give the minimal fix. Do not comment on
formatting or lint: Android Lint and the Gradle build enforce those. If the change is sound, say so in
one line and stop.

## Working style

Keep changes small and reviewable. Prefer a vertical slice with tests over a broad rewrite. Include
actual build/test output in pull request descriptions. Do not mark work complete if it only compiles
without exercising the relevant behaviour.
