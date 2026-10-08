# Review contract

Prefix every finding with a severity label:

- `[BLOCKER]` — data loss, credential leak, crash, broken build, security hole. Blocks merge.
- `[MAJOR]` — wrong behaviour for a realistic input, a behaviour change with no test, an API contract
  break, blocking work on the main thread.
- `[MINOR]` — correct but suboptimal; readability.
- `[QUESTION]` — intent is unclear; ask instead of assuming.

Every finding cites `path:line`, names the concrete input or sequence that produces the wrong
behaviour, and gives the minimal fix. Skip style and formatting — Android Lint and the Gradle build
enforce those. The rulebook in `GEMINI.md` overrides anything here.

## Focus, in order

1. Secrets and privacy: debrid tokens, magnets, signed download URLs or account data reaching logs,
   error messages, fixtures, telemetry, or URLs.
2. Provider boundaries: provider-specific types leaking into ViewModels/UI, endpoint conditionals
   outside the provider package, Real-Debrid behaviour changed without regression coverage.
3. Android correctness: main-thread I/O, leaked Context/Activity, unscoped coroutines, missing
   cancellation, Room access off `Dispatchers.IO`, exported components without a permission,
   `WebView` on user-supplied URLs.
4. Tests: the diff must cover the success path, the failure envelope, empty/malformed bodies and
   unknown JSON for anything it touches. A behaviour change with no test is `[MAJOR]`.
5. Contracts: parsing `success: false`, HTTP 200 carrying an error body, retries, timeouts,
   backwards compatibility of stored data.

## Behaviour

- Review what the diff touches and the behaviour it breaks; nothing else.
- Do not restate the diff or summarise the pull request.
- Cap at ten findings, worst first. If the change is sound, say so in one line and stop.
