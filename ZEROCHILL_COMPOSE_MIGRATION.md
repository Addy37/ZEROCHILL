# ZEROCHILL 6.0 Compose migration

Baseline: `main` at `da9b00588e6ecc370327cad75209e757475c2370` (approved 5.0.1 lineage).
Development branch: `feature/zerochill-compose-6.0`. No release/version change yet.

## Migrated

- Settings screen: Compose sections, action rows, switches, status text and version footer. Existing Java controller retains preference writes, notification permission/scheduling, backup result handling and actions.
- Feedback form and history: Compose radio choices, message input, rating, submit state, loading/error/empty states and keyed conversation cards. Existing repository contracts and feedback identity remain unchanged.
- Feedback state holder retains draft, rating, selected tab and in-flight submit across rotation; Bundle restores draft after process recreation. Late history responses cannot replace newer results or the composer.

## Shared foundation

- `ui/ZeroChillTheme.kt`: colors from the existing XML roles, approved `#0892D0` accent, OLED preference, typography, shapes and spacing.
- `ui/ZeroChillComponents.kt`: bounded/inset-aware screen, panels, section headers, action/toggle rows, buttons, loading and empty/error states. Glass surface delegates to the existing liquid-glass engine.
- Existing glass bottom navigation now consumes the shared theme. Its backdrop, lens, drag/collapse and selection code remain intact.
- New standalone screens use AndroidX `ComponentActivity` lifecycle/saved-state owners and a stable Compose root ID. Existing navigation/header bridges remain in place for legacy screens.

## Hybrid

- Settings dialogs, diagnostics, backup picker and updater overlays still use existing implementations.
- Feedback conversation (`FeedbackThreadActivity`) still uses Views.
- Main shell (`NativeMainActivity`), Search (`SpotlightSearchPanel`, `SearchGlassPane`) and headers retain View controls with Compose glass.

## Remaining inventory

- Library: `LibraryHomeView`, `FavoritesActivity`, `CreatorsActivity`, `DownloadedActivity`, `LikedVideosActivity`, `LibraryHubActivity`.
- Search: `SpotlightSearchPanel`, creator/global result adapters, recent-search presentation. Preserve approved progressive results and fade behavior.
- Content: `OnlyFapHubView`, `SeriesHubView`, `BunkrGalleryActivity`, native feed/detail screens and creator media grids.
- Accounts/social: account/security/profile, messages/inbox, social settings and notification screens.
- Startup: splash/wizard and account announcement surfaces.
- Playback: `ChaosFeedView`/ViewPager2 and `PlayerActivity`/Media3. Migrate shells only after lower-risk screens.
- Platform: WebFallback/WebView and remaining dialogs/sheets.
- XML layouts are predominantly splash and player/Shows control surfaces. Most normal screens assemble Views in Java.

## Validation and next batch

Validation in progress. No physical-device or performance claim yet.
Physical testing needed: Settings toggles/dialogs/backup, Feedback draft/keyboard/tabs/conversation routing, font scaling, insets and navigation glass appearance.
Next batch: Feedback conversation and Settings dialogs, then Library/Search. Preserve player and source systems.
No package, signing, persisted-key, database, backend or remote-config changes.
