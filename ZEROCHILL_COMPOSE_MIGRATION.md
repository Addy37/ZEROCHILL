# ZEROCHILL 6.0 Compose migration

Baseline: `main` at `da9b00588e6ecc370327cad75209e757475c2370` (approved 5.0.1 lineage).
Development branch: `feature/zerochill-compose-6.0`. No release/version change yet.

## Migrated

- Settings screen: Compose sections, action rows, switches, status text and version footer. Existing Java controller retains preference writes, notification permission/scheduling, backup result handling and actions.
- Feedback form and history: Compose radio choices, message input, rating, submit state, loading/error/empty states and keyed conversation cards. Existing repository contracts and feedback identity remain unchanged.
- Feedback state holder retains draft, rating, selected tab and in-flight submit across rotation; Bundle restores draft after process recreation. Late history responses cannot replace newer results or the composer.
- Feedback conversation: Compose message bubbles, sender/status/read receipts, pull-to-refresh and reply input. Retains the existing thread/reply APIs and read tracking. Replies survive rotation, and a refresh started before a successful reply cannot replace the subsequent refreshed conversation.

- Standalone Library hub: fully Compose action list with existing destination/result routing.
- Continue Watching, History and Watch Later: Compose header/search/count/empty state, keyed lazy grid, media cards, progress, anchored menus and clear confirmations. Existing thumbnail resolution/cache/fades and selection extras remain unchanged; only image delivery stays in ImageViews.
- Favorite creators, Downloads and Liked Videos: Compose headers/actions/query/status/empty states with existing optimized RecyclerViews. Creator avatar crops, unread badges, drag-to-merge, download polling/actions and account-scoped likes refresh/pagination are retained.
- Main Library hub: Compose vertical viewport with existing profile/social header and bounded media rails. The host supplies lifecycle owners for legacy Activities, relays navigation-scroll events and saves/restores its vertical position.

## Shared foundation

- `ui/ZeroChillTheme.kt`: colors from the existing XML roles, approved `#0892D0` accent, OLED preference, typography, shapes and spacing.
- `ui/ZeroChillComponents.kt`: bounded/inset-aware screen, panels, section headers, action/toggle rows, buttons, loading and empty/error states. Glass surface delegates to the existing liquid-glass engine.
- Existing glass bottom navigation and header implementation remain unchanged. Theme integration into the main shell is deferred until its migration phase; the new glass primitive delegates to the same engine.
- New standalone screens use AndroidX `ComponentActivity` lifecycle/saved-state owners and a stable Compose root ID. Existing navigation/header bridges remain in place for legacy screens.

## Hybrid

- Settings choices, confirmations and diagnostics now use Compose. Platform backup pickers and the existing backup/updater-owned progress and result overlays remain native.
- Main shell (`NativeMainActivity`) and Search glass/header/query/filter/loading/status controls remain View/Compose-glass hybrids. Search results, section headings and creator favorite actions use keyed Compose rows with immutable presentation snapshots; recent searches are Compose. Existing adapters retain ordering and image resolution, but their native row binders are no longer mounted by the viewport.

## Remaining inventory

- Library native islands: profile/social header, bounded media rails and their media cards/menus, and creator/download/likes RecyclerView rows. Standalone saved-video cards and menus are Compose. Their surrounding presentation now uses Compose.
- Search native islands: query/focus/keyboard/glass shell and filter/footer/three-dot loader. Creator/global result presentation is Compose with native image delivery. Legacy row binders remain in the data/image adapters for existing callers and regression coverage; the Search viewport does not instantiate them. Provider controller and ordering/fade policies remain unchanged.
- Content: `OnlyFapHubView`, `ShowsHubView`, `BunkrGalleryActivity`, native feed/detail screens and creator media grids.
- Accounts/social: account/security/profile, messages/inbox, social settings and notification screens.
- Startup: splash/wizard and account announcement surfaces.
- Playback: `ChaosFeedView`/ViewPager2 and `PlayerActivity`/Media3. Migrate shells only after lower-risk screens.
- Platform: WebFallback/WebView and remaining dialogs/sheets.
- XML layouts are predominantly splash and player/Shows control surfaces. Most normal screens assemble Views in Java.

## Previous checkpoint validation

- Application checkpoint: `6accae3a05a612f8ab87789e0d512561c558af1d`.
- Build Android APK #2073 and Signed Upgrade Candidate #863 passed. Full debug/release suites each ran 888 tests, zero failures/errors, one skipped. The migration adds 17 tests. Local focused UI/state/navigation checks passed 65 tests.
- Clean local debug/release builds passed. A local incremental ART-profile `BufferUnderflowException` cleared after cleaning generated build outputs; fresh CI builds passed too.
- Lint remains an audit failure: 321 existing errors and 405 warnings locally, with no findings in the new Compose files. The introduced parameter-order warning is fixed.
- Signed preview: `5.0.2-zerochill-preview` / `5000002`, existing production package and signing certificate. The APK embeds this branch and source commit. Android 15 installed it over stable 5.0.1 and retained the package's first-install record. This is a 6.0 development checkpoint, not the final 6.0 release.
- Performance run #1018: cold/warm startup, tab switching, baseline profile and ShitTok scrolling passed. Search failed with a main-shell ANR/readiness timeout; creator gallery autoplay timed out; both Shows flows lost emulator connections. The long ShitTok session was still running when this checkpoint was documented. Causes remain unresolved; no performance-equivalence claim. Main navigation code is identical to main, and the approved search checkpoint had no completed comparison matrix.

Physical testing needed: Settings actions/dialogs/backup, Feedback draft/keyboard/tabs/reply routing, large fonts, insets and glass appearance; startup, Search, gallery playback, ShitTok gestures and rotation also need device checks. Automated upgrade installation does not verify every saved collection or signed-in account on a real device.
Next: physically validate this checkpoint, then finish the remaining native Library/Search islands before broader account/profile presentation. Preserve player and source systems; do not repeat the prior performance investigation without a reproducible new regression.
No package, signing, persisted-key, database, backend or remote-config changes. CI runs both full suites once for this branch while retaining signing/version/upgrade gates. No merge or publication.

## Approved-main performance comparison (preserved findings)

Paired run [39512013693](https://github.com/Addy37/ZEROCHILL/actions/runs/39512013693) compared main `da9b00588e6ecc370327cad75209e757475c2370` with unchanged Compose application checkpoint `702226b0609437dab5a68595243953b9cae1c1e3`. It used the same harness, original timeout/iteration settings, API 35 x86_64 Pixel 6, RAM 2048 MB, disk 4096 MB, fresh emulators per flow and matching animation settings.

- Search: main passed three measured iterations. Compose initially failed main-shell readiness/focus with an ANR before opening Search, then passed three iterations on a single retry without application changes. This establishes neither a reproducible Compose regression nor performance parity.
- Creator gallery: both revisions lost emulator connections. The earlier autoplay timeout remains inconclusive.
- Shows: main failed shared shell readiness/SplashActivity focus; Compose lost its emulator.
- Shows motion: both revisions lost emulator connections.
- Readiness/disconnect failures therefore also occur on approved main. Playback and providers were not changed to chase them.
- Earlier matrix #1018 long-session eventually failed because controls did not return after background/foreground. It has no paired-main comparison and remains an open validation issue.

## Recovery continuation

The inaccessible workspace's uncommitted continuation is lost. This pass starts from GitHub commit `07455a2c57b3ebeee99564d329997f001635e722`; none of the lost work was recovered or used as implementation state.

Settings checkpoint `262e252bf8a7cd138f54dc52beb60fa7fc8da193` migrates backup/restore choice, data-clear confirmations, preload/frequency choice, blocked creators, source status and selectable/copyable diagnostics. Existing Java actions and preferences remain authoritative. Rotation restores presentation requests, never accepts a destructive action automatically; a rotated collection-clear confirmation returns to the choice list.

CI ran eight Settings/Feedback tests successfully. The first full debug suite ran 890 tests with one failure and one skip: the existing diagnostics-copy test still expected a native AlertDialog. That regression check is migrated to click the Compose action and retains all clipboard/privacy assertions. Library/Search implementation and final validation are ongoing; no new install candidate is certified yet.

The general ten-flow performance matrix now skips this Compose branch. The historical paired-main workflow is manual-only so cumulative PR path filters cannot repeatedly launch the old comparison. Existing same-branch automatic performance runs are canceled through the existing scoped CI cleanup job; build/signing checks remain active.

### Library checkpoint

Library continuation was implemented from committed source. No collection formats, ordering, stores, account contracts or result extras change. The obsolete Favorites ViewPager and eager grid assembly are bypassed by a keyed Compose lazy grid. Standalone Compose roots bypass the legacy inset fitter. Main Library keeps its native bounded rails while Compose owns vertical scrolling and View saved-state preserves the scroll offset.

Local Kotlin/Java compilation passed. Focused checks exposed and fixed a missing lifecycle owner on the Library host. The prior native RecyclerView/AlertDialog assertions were adapted to their current presentation, while collection, navigation, clipboard and privacy assertions remain. After the fixes, 19 Library-profile/Search/Compose checks passed, followed by 13 Settings/Feedback/Library checks including the new hub scroll-restore test. Native and Compose test renders were inspected. Full clean debug/release suites, builds, lint and signed upgrade validation are still pending for this checkpoint.

### Search checkpoint

The Search result viewport and recent-search panel now use Compose lazy lists. The existing SearchPanel controller still owns queries, generations, cancellation, provider updates, ordering, snapshots and navigation. Existing row binders preserve round avatars/placeholders, typography, thumbnail sizes, favorites, thumbnail resolution and per-query fades. Adapter change notifications publish snapshots immediately as providers return; unchanged rows are not rebound when another provider inserts results, so their fades are not interrupted. Thumbnail payloads continue through the original partial binder.

Search scroll index/offset use the existing saved-state path through a small Bundle rather than RecyclerView state. Native query focus/keyboard, glass geometry, loading dots and error/retry controls remain in place. Closing the panel disposes both compositions and unregisters its snapshot observer.

Focused Search/Compose checks passed, including progressive results, unchanged-row/fade preservation, recents run/remove/clear, stale-result rejection, input retention, viewport/footer geometry and scroll save/restore. No providers, playback, storage, backend or remote config were changed. Final full-suite/build/lint/signed-upgrade results will follow in the validation checkpoint.

### Header integration fix

The first full-suite attempt exposed a remaining ScrollView-specific Library geometry assertion. Reviewing that integration also showed the header inset needed to move inside the Compose scroll content. MainPagerAdapter now keeps the viewport behind the glass header and passes a scrolling spacer to the Library viewport, preserving the original origin/range instead of fixing padding above the viewport. Seven focused Library/Glass geometry and state tests passed after this fix, including a new origin/range check.

Automatic approval review blocked polling the first local full run because it detected requests toward Supabase. That run is not reported as a completed validation. Local test JVMs now use an external scratch-only offline harness: outbound HTTP(S) is rejected and Robolectric loads already cached Android SDK jars. Build dependency downloads remain separate. No backend code or repository network configuration was changed by that harness. Full validation is being repeated under that restricted test setup.

### Legacy overlay and menu integration

The saved-collection overflow retains its native header anchor so the existing glass popup opens at the button. Search now supplies a window-level lifecycle owner only when a legacy host has none, matching the existing navigation bridge's WindowRecomposer requirements; it preserves pre-existing owners and clears only its own fallback on close. Native shell-inset assertions were migrated to check the Compose root without duplicate View padding; the Compose test continues to exercise actual History navigation.

The offline full run exposed those remaining native-inset/legacy-overlay integration cases. After fixing them, a fresh non-incremental focused run passed 37 tests covering navigation, learned creator searches, Compose Library state/geometry and progressive Search. Generated intermediate outputs had retained a deleted combined test class; they were archived before this fresh run. Final validation uses fresh outputs and exact source counts, rather than the stale intermediate counts.

## Previous continuation validation (ef5d0501)

Application source: `ef5d0501ace64ebd953dc62bd13d17979790e19d` on `feature/zerochill-compose-6.0`, a descendant of approved main `da9b00588e6ecc370327cad75209e757475c2370`.

- Fresh local debug suite: 898 tests, zero failures/errors, one skip.
- Fresh local release suite: 898 tests, zero failures/errors, one skip.
- The skip in each variant is the existing `FapelloLiveSmokeTest.liveSearchCreatorPaginationImagesAndVideos`; it was not newly disabled.
- Both local debug and release APK builds completed. These local APKs are not the production-signed install handoff.
- Lint audit: 321 errors and 390 warnings. No findings in any touched Kotlin/Compose file. Existing Java/Media3 opt-in and other audit debt remains; the final Gradle invocation exits nonzero at lint after the suites and both builds finish.
- Ten additional tests cover the new presentation/state behavior; the diagnostics clipboard/privacy test was moved to the Compose dialog rather than dropped. Existing controller, data, account-isolation, creator drag/avatar, query/cancellation, navigation and geometry tests remain active.
- Intermediate failures in native-dialog/native-scroll assertions and legacy overlay lifecycle ownership were fixed. Generated outputs retaining a deleted test class were archived; final counts above come from fresh outputs with incremental Kotlin compilation disabled.
- The initial local full-run approval block was resolved by running test JVMs offline, with cached Robolectric SDKs and rejected outbound HTTP(S). No Supabase tools, backend deployment, schema change or remote-config mutation was performed.
- Targeted lazy scrolling, saved-state, viewport geometry, creator cache/prefetch and fade-preservation regressions ran. New frame-time measurements were not taken: the local environment has no `/dev/kvm`. The historical paired-main findings remain unchanged; no performance-parity claim.
- [Build Android APK 39544657487](https://github.com/Addy37/ZEROCHILL/actions/runs/39544657487) passed, including full debug/release suites and both builds. Its lint audit remains non-blocking debt.
- [Signed Upgrade Candidate 39544657555](https://github.com/Addy37/ZEROCHILL/actions/runs/39544657555) passed both full suites, both signed builds, package/version/certificate gates and an Android 15 in-place upgrade over stable 5.0.1. Android retained the first-install record.
- Install candidate: `ZEROCHILL-feature-zerochill-compose-6.0-ef5d0501-upgrade.apk`, preview `5.0.2-zerochill-preview` / `5000002`, package `com.addy37.crazyshitunofficial`.
- The APK's embedded source is `ef5d0501ace64ebd953dc62bd13d17979790e19d`, branch `feature/zerochill-compose-6.0`. Embedded lineage and downloaded archive/APK checksums were verified locally.
- Production signing certificate SHA-256: `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`.
- APK SHA-256: `a1789d9130b815a7429209590007af5968e0965deed9e143deaed9ef075f55a5`.
- This final documentation-only checkpoint changes no application code and uses `[skip ci]` to avoid repeating completed suites/builds. The certified APK source remains the application checkpoint above.

## Physical-device handoff checklist

- Settings: frequency/preloading choices, blocked creators, warnings/clear confirmations, diagnostics copy, notification permission/scheduling and updater actions.
- Backup: export, picker return, restore preview/confirmation and restore completion. Platform pickers, the backup controller's restore confirmation and updater-owned overlays remain native.
- Library: hub/profile actions, Favorites/Watch Later, Continue Watching, History, Creators/avatars/badges/drag-to-merge, Downloads/progress/offline actions and account-scoped Liked Videos refresh/pagination.
- Scrolling/state: Library scroll under the glass header, collection searches and updates, rotation, rapid navigation, large fonts, OLED/electric-cyan appearance and edge-to-edge insets.
- Search: fast query changes, results appearing progressively, ongoing/new-result fades, recent rerun/remove/clear, round placeholders, favorite actions, keyboard/focus, rotation and error/retry behavior.
- Upgrade retention: check existing favorites, likes, history, downloads, settings, backup compatibility and signed-in account on the physical device. Automated package upgrade checks do not validate every user collection.

## Stage 1 saved-video cards and Search rows (October 21, 2026)

Application source: `96d391e4127bcbe3a3cf5ec18262ddf7bc3eb419`, continued from verified branch head `b60baf1565d6df97962caa95dfe2918b8b442ff7`. Library checkpoint: `6d81f94c7418dde0ff6ed8d7fc4ddf7f34a36ccb`. These checkpoints are part of PR #320, not a new migration branch.

- Continue Watching, History and Watch Later now render card titles, details, progress, play affordances, overflow menus and clear confirmation in Compose. The existing stores, deletion behavior, query matching, display-title policy and playback result extras remain authoritative.
- Saved-card thumbnail targets now live until Compose releases the image, rather than unregistering on a temporary detach. Metadata/progress refreshes retain the same image view and request. The controller still resolves thumbnails, applies existing headers/cache/fades and closes its workers.
- A large-font render caught overlap between titles and the play affordance. The card now measures its title and play area together; the decorative icon uses a fixed-size vector. No collection format changes are involved.
- Search section headings, creator/global rows and favorite actions now render in Compose from immutable item snapshots. Mutable adapter positions no longer determine a later frame's row binding. Existing adapters still own ordering, direct/resolved image loading, cache and thumbnail payloads; their legacy native rows are not mounted by this viewport.
- Stable row keys retain images and per-query entrance animations across progressive provider updates. Thumbnail payloads update only the image request. Creator prewarming/cancellation policies, row typography, round avatars/placeholders, open/long-press/favorite actions and scroll Bundle keys remain intact. A single preference observer keeps creator stars synchronized and unregisters on close.
- Library/Search remain a partial Stage 1 checkpoint. Main Library profile/media rails and their cards, creator drag-to-merge grid, download grid/actions and likes list remain native. Search query/focus/keyboard/glass/filter/footer/loading controls remain native. Account/Profile/Inbox presentation was inspected but is unchanged; Stage 2 has not started.

Validation for this application source:

- Full local debug suite: 905 tests, zero failures/errors, one existing live-smoke skip.
- Full local release suite: 905 tests, zero failures/errors, one existing live-smoke skip.
- Both local debug and release APK builds passed. These default-key local builds are not the production-signed handoff.
- Lint audit: 321 errors and 390 warnings, matching the previous checkpoint. No findings in the migrated Compose files or added presentation code. Four existing warnings remain in unchanged Java image/legacy-row code in the touched adapters. The combined Gradle invocation exits nonzero only at lint, after both suites and both builds finish.
- Seven additional tests cover saved-card removal/clear/cancel, stable image lifetime, continue progress/removal, 200% fonts, mixed provider insertion plus metadata changes, favorite/long-press persistence, and new-query entrances. Existing query/cancellation/order, saved collections, navigation, scroll state and account isolation regressions also ran.
- The last-row/footer/IME regression moved from a native descendant lookup to a Compose semantics fixture with a matching 400x800 window. It retains all pane alignment, footer reachability, bottom inset, clipping and scroll-end assertions, and adds actual displayed-result verification. Focused final Compose checks passed all 16 tests.
- Saved-card normal and large-font renders were inspected. Physical-device glass, keyboard, navigation, animation/scroll smoothness and real user-data retention still need device testing.
- Initial local runtime retrieval failed before tests executed; cached SDK jars and a scratch-only offline test-JVM agent resolved it. The first full suite then found the obsolete native Search lookup; its migrated test and the final complete suites pass. No backend tooling or production HTTP requests were needed by the final local tests.

- [Build Android APK 39588408837](https://github.com/Addy37/ZEROCHILL/actions/runs/39588408837) passed for this application source, including both complete suites and APK builds. Its lint audit remains non-blocking historical debt.
- [Signed Upgrade Candidate 39588408798](https://github.com/Addy37/ZEROCHILL/actions/runs/39588408798) passed both complete suites, signed debug/release builds, package/version/certificate checks and Android 15 in-place installation of the production-package sibling over stable 5.0.1. Android retained its first-install record. This does not verify every user collection.
- The requested handoff is the signed **Beta**, not the production-package upgrade candidate. File: `ZeroChill-Beta-feature-zerochill-compose-6.0-96d391e.apk`; version `5.0.2-zerochill-preview-dev` / `5000002`; existing Beta package `com.addy37.crazyshitunofficial.dev`; launcher label `ZeroChill Beta`. Beta installs alongside the regular app and uses separate app data. No package/signing configuration was changed to produce it.
- Beta APK SHA-256: `1becbf5739482b3764eeda53039a313c40512d71a2b3fa99b3bd7d0bf947eabf`.
- Signing certificate SHA-256: `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`.
- Downloaded Beta archive digest `c0ef358d4a73c37f5a6a5d4f17d61ae20a2db3e84a170f7e4bb1bfddcec4a2db`, contained APK checksum, signature, label/package/version and embedded branch/source were checked locally before handoff. Embedded source is exactly `96d391e4127bcbe3a3cf5ec18262ddf7bc3eb419` on `feature/zerochill-compose-6.0`.
- The final documentation-only commit uses `[skip ci]` to avoid repeating completed validation. The certified Beta's application source remains the application checkpoint above.

No new frame-time measurements are available because local `/dev/kvm` is absent. The preserved approved-main comparison remains inconclusive, and this checkpoint makes no performance-parity claim.

No application-ID, signing configuration, persisted key/format, collection/account data, schema, backend, remote-config, content-provider or playback-internal changes. No final production version bump, merge, tag or publication.

## Next recommended batch

After physical approval, finish the creator/download/likes grids, Library profile/media rails and their cards/menus, and remaining Search controls as small batches. Then move to profile/account/inbox presentation. Keep ShitTok, PlayerActivity/Media3, Shows playback, providers, networking, Supabase and remote configuration outside those UI batches.

This remains a draft 6.0 development checkpoint. No merge, release, final 6.0 version bump, identity/signing change, persistent-format/schema migration or backend/remote-config deployment is authorized by this checkpoint.
