# Hermes / GitLab Duo contribution context

## Project

This is an Android/Android TV fork of Unchained. Real-Debrid must remain functional while the app is
migrated to a provider-neutral architecture. Debrid-Link is the first new provider.

## Required checks

Run from `app/`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Use JDK 21 and Android SDK API 37. Never weaken or remove an existing check to make a change pass.

## Debrid provider invariants

- Provider API DTOs and Retrofit interfaces stay under their provider-specific package.
- Viewmodels and UI must not branch on provider HTTP endpoints.
- Debrid-Link uses `https://debrid-link.com/api/v2/` and bearer authentication.
- Its JSON envelope `success` flag decides success; an HTTP 200 can contain an API failure.
- Never log, print, commit, put in URLs, or add to fixtures: API keys, access tokens, refresh tokens,
  magnet contents, signed download URLs, or user account data.
- Add tests for success envelopes, failure envelopes, empty bodies, malformed input, and unknown JSON.
- Preserve Real-Debrid behavior and add regression coverage when touching shared code.

## Working style

Keep changes small and reviewable. Prefer a vertical slice with tests over a broad rewrite. Include
actual build/test output in merge-request descriptions. Do not mark work complete if it only compiles
without exercising the relevant behavior.
