# ZEROCHILL 7: unresolved 6.x crash prevention

Source: read-only audit of old Supabase `public.app_feedback` and associated `feedback_messages` crash diagnostics, including 6.0.2 reports on 2026-11-13. The legacy database was **not modified**. User excluded early 7.0 preview crashes because those were already addressed.

## Confirmed and addressed

1. **Startup / WorkManager initialization (6.0.2).** Full diagnostic stack ends at `NotificationCoordinator.schedule`, called from `CrazyShitApplication.onCreate` on an Infinix Android 16 device. `WorkManager.getInstance(context)` raised `IllegalStateException`. The scheduler now safely defers notification registration when WorkManager is unavailable and retries when the app next enters the foreground. Manual notification checks report failure instead of claiming a scan began. Existing preferences, unique job names, and normal scheduling frequencies remain unchanged.
2. **OnlyHaven HTTP response read (6.0.2).** Multiple reports show `UncheckedIOException` from jsoup's `Response.body()` in `OnlyHavenRepository.fetchConfiguredText`. With jsoup 1.23.1, `readBody()` throws checked `IOException`, which the existing bounded retry / configured fallback loop already handles. A small regression test checks success and interrupted reads.
3. **Bunkr Gallery async resolution (6.0.2).** Playback/preloading reports include main-thread `NullPointerException` in the gallery's delayed callbacks. `PlayableSourceRouter.resolve` legitimately permits a null result, while gallery paths previously dereferenced the result. Photo, preload-video, and play-video paths now reject missing/blank media URLs on the worker thread, then use the existing failure UI. Async callbacks also check that their gallery instance is still active. Recovery callbacks guard missing result/player state. This prevents identified null paths without changing playable media URLs, source parsing, player format, cache identity, or paging.

## Not changed

- **Android platform FrameLayout NPE (6.0.2):** The trace contains only framework layout frames and no application method naming the affected view. It appears on multiple devices including a Samsung foldable. A targeted fix requires a reproducible screen/transition and device log. Do not turn off animations or rewrite the main layout on this evidence alone.
- **6.0.0 bitmap/LiquidRift crash:** The approved 7.0 implementation already uses a no-bitmap-snapshot rendering path; no speculative change.
- **Early 7.0 Compose lifecycle crashes:** User says already addressed; excluded explicitly.
- Legacy Supabase data, New Supabase auth, source-config values, remote endpoints, social features, API permissions, favorites, history, downloads, and settings were not changed.

## Validation

New tests: `OnlyHavenCheckedResponseTest` and `BunkrGalleryMediaGuardTest`. Run with other existing notification/playback/creator tests and compile a signed 7.0.1 release APK using the one-shot private CI workflow. The workflow verifies the unchanged Android app ID and signing certificate, higher version code, and New Zerochill project isolation. Passing tests does not prove the OEM-specific crash is fully eliminated; live smoke tests should cover background-notification scheduling, OnlyHaven failed/slow requests, Bunkr play/swipe/rotation/exit, and startup on affected devices.

The test build is a private update candidate, not an automatic public release. Preserve 7.0.0 installed data and session.
