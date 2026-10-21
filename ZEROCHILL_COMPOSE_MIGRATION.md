# ZEROCHILL 6.0 Compose migration

Baseline: `main` at `da9b00588e6ecc370327cad75209e757475c2370` (approved 5.0.1 lineage).
Development branch: `feature/zerochill-compose-6.0`. No release/version change yet.

## Migrated

- Settings screen: Compose sections, action rows, switches, status text and version footer. Existing Java controller retains preference writes, notification permission/scheduling, backup result handling and actions.
- Feedback form and history: Compose radio choices, message input, rating, submit state, loading/error/empty states and keyed conversation cards. Existing repository contracts and feedback identity remain unchanged.
- Feedback state holder retains draft, rating, selected tab and in-flight submit across rotation; Bundle restores draft after process recreation. Late history responses cannot replace newer results or the composer.
- Feedback conversation: Compose message bubbles, sender/status/read receipts, pull-to-refresh and reply input. Retains the existing thread/reply APIs and read tracking. Replies survive rotation, and a refresh started before a successful reply cannot replace the subsequent refreshed conversation.

- Standalone Library hub: fully Compose action list with existing destination/result routing.
- Continue Watching, History and Watch Later: Compose header/search/count/empty state and keyed lazy grid. Existing native media cards, thumbnail resolution, menus, clear confirmations and selection extras remain embedded.
- Favorite creators, Downloads and Liked Videos: Compose headers/actions/query/status/empty states with existing optimized RecyclerViews. Creator avatar crops, unread badges, drag-to-merge, download polling/actions and account-scoped likes refresh/pagination are retained.
- Main Library hub: Compose vertical viewport with existing profile/social header and bounded media rails. The host supplies lifecycle owners for legacy Activities, relays navigation-scroll events and saves/restores its vertical position.

## Shared foundation

- `ui/ZeroChillTheme.kt`: colors from the existing XML roles, approved `#0892D0` accent, OLED preference, typography, shapes and spacing.
- `ui/ZeroChillComponents.kt`: bounded/inset-aware screen, panels, section headers, action/toggle rows, buttons, loading and empty/error states. Glass surface delegates to the existing liquid-glass engine.
- Existing glass bottom navigation and header implementation remain unchanged. Theme integration into the main shell is deferred until its migration phase; the new glass primitive delegates to the same engine.
- New standalone screens use AndroidX `ComponentActivity` lifecycle/saved-state owners and a stable Compose root ID. Existing navigation/header bridges remain in place for legacy screens.

## Hybrid

- Settings choices, confirmations and diagnostics now use Compose. Platform backup pickers and the existing backup/updater-owned progress and result overlays remain native.
- Main shell (`NativeMainActivity`) and Search glass/header/query/filter/loading/status controls remain View/Compose-glass hybrids. Search results now use a Compose lazy viewport around approved native row binders; recent searches are Compose.

## Remaining inventory

- Library native islands: profile/social header, bounded media rails, media cards/menus and creator/download/likes RecyclerView rows. Their surrounding presentation now uses Compose.
- Search native islands: query/focus/keyboard shell, filter/footer/three-dot loader and creator/global result row binders. Provider controller and ordering/fade policies remain unchanged.
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
Next: complete the Library/Search continuation from committed state and validate the new checkpoint. Preserve player and source systems; do not repeat the prior performance investigation without a reproducible new regression.
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
