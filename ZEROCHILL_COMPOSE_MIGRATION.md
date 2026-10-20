# ZEROCHILL 6.0 Compose migration

Baseline: `main` at `da9b00588e6ecc370327cad75209e757475c2370` (approved 5.0.1 lineage).
Development branch: `feature/zerochill-compose-6.0`. No release/version change yet.

## Migrated

- Settings screen: Compose sections, action rows, switches, status text and version footer. Existing Java controller retains preference writes, notification permission/scheduling, backup result handling and actions.
- Feedback form and history: Compose radio choices, message input, rating, submit state, loading/error/empty states and keyed conversation cards. Existing repository contracts and feedback identity remain unchanged.
- Feedback state holder retains draft, rating, selected tab and in-flight submit across rotation; Bundle restores draft after process recreation. Late history responses cannot replace newer results or the composer.
- Feedback conversation: Compose message bubbles, sender/status/read receipts, pull-to-refresh and reply input. Retains the existing thread/reply APIs and read tracking. Replies survive rotation, and a refresh started before a successful reply cannot replace the subsequent refreshed conversation.

## Shared foundation

- `ui/ZeroChillTheme.kt`: colors from the existing XML roles, approved `#0892D0` accent, OLED preference, typography, shapes and spacing.
- `ui/ZeroChillComponents.kt`: bounded/inset-aware screen, panels, section headers, action/toggle rows, buttons, loading and empty/error states. Glass surface delegates to the existing liquid-glass engine.
- Existing glass bottom navigation and header implementation remain unchanged. Theme integration into the main shell is deferred until its migration phase; the new glass primitive delegates to the same engine.
- New standalone screens use AndroidX `ComponentActivity` lifecycle/saved-state owners and a stable Compose root ID. Existing navigation/header bridges remain in place for legacy screens.

## Hybrid

- Settings dialogs, diagnostics, backup picker and updater overlays still use existing implementations.
- Main shell (`NativeMainActivity`), Search (`SearchPanel`, `SearchGlassLayout`) and headers retain View controls with Compose glass.

## Remaining inventory

- Library: `LibraryHubView`, `FavoritesActivity`, `CreatorsActivity`, `DownloadedActivity`, `LikedVideosActivity`, `LibraryHubActivity`.
- Search: `SearchPanel`, creator/global result adapters, recent-search presentation. Preserve approved progressive results and fade behavior.
- Content: `OnlyFapHubView`, `ShowsHubView`, `BunkrGalleryActivity`, native feed/detail screens and creator media grids.
- Accounts/social: account/security/profile, messages/inbox, social settings and notification screens.
- Startup: splash/wizard and account announcement surfaces.
- Playback: `ChaosFeedView`/ViewPager2 and `PlayerActivity`/Media3. Migrate shells only after lower-risk screens.
- Platform: WebFallback/WebView and remaining dialogs/sheets.
- XML layouts are predominantly splash and player/Shows control surfaces. Most normal screens assemble Views in Java.

## Validation and next batch

- Application checkpoint: `6accae3a05a612f8ab87789e0d512561c558af1d`.
- Build Android APK #2073 and Signed Upgrade Candidate #863 passed. Full debug/release suites each ran 888 tests, zero failures/errors, one skipped. The migration adds 17 tests. Local focused UI/state/navigation checks passed 65 tests.
- Clean local debug/release builds passed. A local incremental ART-profile `BufferUnderflowException` cleared after cleaning generated build outputs; fresh CI builds passed too.
- Lint remains an audit failure: 321 existing errors and 405 warnings locally, with no findings in the new Compose files. The introduced parameter-order warning is fixed.
- Signed preview: `5.0.2-zerochill-preview` / `5000002`, existing production package and signing certificate. The APK embeds this branch and source commit. Android 15 installed it over stable 5.0.1 and retained the package's first-install record. This is a 6.0 development checkpoint, not the final 6.0 release.
- Performance run #1018: cold/warm startup, tab switching, baseline profile and ShitTok scrolling passed. Search failed with a main-shell ANR/readiness timeout; creator gallery autoplay timed out; both Shows flows lost emulator connections. The long ShitTok session was still running when this checkpoint was documented. Causes remain unresolved; no performance-equivalence claim. Main navigation code is identical to main, and the approved search checkpoint had no completed comparison matrix.

Physical testing needed: Settings actions/dialogs/backup, Feedback draft/keyboard/tabs/reply routing, large fonts, insets and glass appearance; startup, Search, gallery playback, ShitTok gestures and rotation also need device checks. Automated upgrade installation does not verify every saved collection or signed-in account on a real device.
Next: compare the failed performance flows against approved main and diagnose the timeouts, then migrate Settings dialogs and Library/Search. Preserve player and source systems.
No package, signing, persisted-key, database, backend or remote-config changes. CI runs both full suites once for this branch while retaining signing/version/upgrade gates. No merge or publication.
