# Phase 6 — OnlyFap V2 motion candidate

Baseline: production main `7269d41e37732a4744ef6d23927e223017caa7bb`, merged PR #412.
Reference: ZEROCHILL-V2 main `9ce121f`; inspected its actual imported Java
OnlyFapHubView, OnlyFapHeroRippleView, OnlyFapHeroIndicators,
ContentPreferenceUi, CreatorGalleryTransition and CreatorProfileHeader.
All changes remain one candidate branch/PR. No merge or publication is authorized.

## Behavior

- Ready-to-display normal hero changes use V2's 650ms native circular reveal,
  centered at 50%/47%, and two white/accent rings. Both existing Glide target
  owners remain alive until completion. Selection, strict source qualification,
  original-resolution requests, cache keys, filtering and 13-second dwell stay
  intact. Startup retains its bounded crossfade; category changes retain OG's
  established liquid-rift transition. Hardware reveal unavailability uses the
  existing owned-target crossfade. Motion disabled commits immediately.
- Compact centered pagination uses V2's sliding accent pill, 6dp dots and a
  seven-slot moving window. Spacing is reduced from V2's 24dp to 18dp, and pill
  width from 20dp to 14dp. Active changes pulse even at the moving window edge.
  Position changes only with committed hero identity; indicator taps use the
  same ready-image path as swipes and automatic rotation.
- OnlyFap's anchored two-column preference grid reuses PreferenceGlassPanel and
  current accent, icons, stored values and refresh callbacks. The six original
  options remain. It scrolls within the visible window and dismisses on tab,
  detach, layout/configuration changes or Back. Other surfaces' picker stays
  unchanged. **V2 main and its file history contain a vertical list, not a grid**;
  the requested grid is therefore a deliberate layout difference, not an exact
  copy of a nonexistent reference implementation.
- Hero, shelf cards and favorite-avatar entry points pass the actual image into
  Android's shared image/bounds transform. Gallery loading starts independently.
  A single independent rendered-image buffer (long edge <=1024px, at most 4MiB)
  seeds gallery presentation and return; no extra download or decoding is added.
  This replaces V2's unsafe cross-Activity borrowing of Glide-owned drawables.
  It is an image-only buffer, never a fullscreen screen capture. Actual corners
  interpolate; ripple is additional to V2's gallery bounds/image morph.
  Detached/rebound/offscreen sources or missing/recreated gallery headers fade.
  Repeated launches are bounded; existing data, preloader seeds and actions stay.

## Compatibility and acceptance

The production application ID, signing configuration, persisted keys, backups,
source parsers, gallery data, media playback, download/favorite logic and all seven
accents are unchanged. No backend, Supabase or remote-config change or migration.
Preview 26 (`6000028`) is above Preview 25 (`6000027`). The scoped candidate
workflow builds one signed release APK, compares its package and certificate with
stable, and checks source ancestry and upgrade version. APK filename includes
branch and commit. No release/tag job is used.

Local focused tests cover native ownership/cache/startup/filter regressions,
13-second scheduling, bounded capture lifetime, missing/rebound return source,
preference persistence/rapid taps/small landscape/reduced motion, gallery behavior
and glass/appearance. Actual executed results are recorded in the PR and handoff.
Platform shared-element rendering, predictive Back, hardware ripple/glass and
60/120Hz frame pacing still require OnePlus CPH2655 / Android 16 physical testing.
No physical upgrade, optical equivalence or device-performance claim is made.
The bounded navigation capture adds a one-time UI-thread image draw; it does not
sample scrolling-list backdrops continuously. Process recreation intentionally
uses normal gallery loading and fade rather than persisting animation pixels.

## Files

Native UI: `OnlyFapHubView`, `OnlyFapHeroRippleView`, `OnlyFapHeroIndicators`,
`OnlyFapPreferencePicker`, `ContentPreferenceUi`, `onlyfap_preference_ids.xml`.
Gallery navigation: `CreatorGalleryTransition`, `CreatorProfileHeader`,
`MainPagerAdapter`, `NativeMainActivity`, `NativeFeedBrowserActivity`.
Tests: `OnlyFapHeroIndicatorsTest`, `OnlyFapHeroBitmapLifecycleTest`,
`OnlyFapPreferencePickerTest`, `CreatorGalleryTransitionTest`.
Delivery: `.github/workflows/onlyfap-motion-candidate.yml`, this document and
`ZEROCHILL_BUILD_LINEAGE.md`.

## Local verification

Java 17 compilation passed. The final offline local run passed all **192 tests
across 18 focused suites** and `:app:assembleDebug` (3m31s), using Java 21 for
Robolectric. Coverage includes API 29/35 native lifecycle/capture and API 35/36
existing favorite-gallery navigation. Initial trials found legacy indicator and
fade-boundary assertions, asynchronous dialog-dismiss ownership and missing native
reveal-end callback cleanup; those were corrected before the passing run.
`git diff --check` passed. Full unfiltered unit tests, lint, emulator/device runs
and a physical in-place upgrade were not run. The CI signed release build and
identity report are linked from the PR after completion.
