# ShitTok interaction candidate

Base: merged PR #297, `c759e9108a47ece952ae013643b548cf21cc6aaf` on
`rebrand/zerochill`. Branch: `feature/shittok-interaction-polish`.
Keep this candidate draft and unmerged pending device approval.

## Behavior

- Video-only double taps use KidTok's GestureDetector pattern: confirmed single
  tap preserves the existing playback/control action, double tap adds an existing
  social heart and never removes one. Watch Later stays a separate action.
  Single taps now wait for Android's double-tap confirmation timeout.
  Signed-in users see an immediate optimistic heart/count update and one short
  cyan animation. Concurrent/repeated taps cannot submit an unlike. A failed
  request restores the previous state. Signed-out users get a sign-in hint while
  playback continues; the explicit heart button keeps its existing sign-in route.
- Buttons, creator links/avatars and the scrubber remain separate touch targets.
  Video gestures do not intercept their events. Vertical paging, creator swipes
  and pinch keep their existing handling. Page/source/holder/lifecycle transitions
  invalidate delayed taps and animation work.
- At 60 seconds or longer, a subtle track remains visible with a 48dp hit row.
  Touching it reveals the normal thumb and scrub interaction. Short clips retain
  the existing paused/landscape-controls visibility. Only progress stays visible
  when landscape controls hide. The existing viewport/footer inset is retained.
  API 29+ uses 2dp idle / 4dp interactive thickness; API 26-28 retains its themed
  track with the same idle alpha and hidden thumb.

## Optional preview work

Media3 stays at 1.11.1. The matching `media3-inspector-frame` module supplies
FrameExtractor. The app does not construct an ExoPlayer or request a player lease.
Media3 internally owns a private extraction player/decoder. Its asynchronous
cleanup is separate from the existing three-player playback pool.
See [Media3 1.11.1 extraction internals](https://github.com/androidx/media/blob/1.11.1/libraries/inspector_frame/src/main/java/androidx/media3/inspector/frame/FrameExtractorInternal.java).
Extraction uses CLOSEST_SYNC and a 180 by 320 pixel Presentation with aspect-preserving
center cropping. This replaces the original 320 by 180 letterboxed output without
changing pixel count, request cadence, cache limits or extractor ownership.
The source is the holder's already resolved StreamInfo, via ShitTokMediaSources:
the same media identity, User-Agent, Referer, Origin, cookies and request metadata.
It reuses the existing compressed-media cache; no preview files or thumbnail disk
cache are created. RenderedThumbnailResolver is not used because its page/WebView
resolution and persisted still-image workflow are unnecessary for active scrubs.

No extractor, bitmap or thumbnail request starts on bind, selection or normal
scrolling. The first active drag creates the controller lazily. Requests use
one-second cache buckets, a 100ms minimum start interval, one in-flight future,
one overwritten latest target and a four-frame in-memory LRU cache. Only the
latest target can display a completed result. The card clamps to the viewport,
follows the scrubber horizontally and shows target / total time immediately.

Drag end, page/source changes, recycle/detach, background/hidden/close and
orientation/fullscreen transitions cancel queued/in-flight work, close the
extractor, clear images/cache and restore paging. Results must match the drag
epoch, item object, StreamInfo, position, feed generation and attached token.
An extraction failure or 1.5-second timeout falls back to time only and suppresses
further extraction attempts for that clip/source in the retained controller.
Preview reads reuse the existing 900ms metadata HTTP budget, disable load retries
and check a cancellation flag before/after reads. Late bytes cannot reach the
extractor after cancellation. Media3 schedules decoder release asynchronously;
an already blocked read may take up to its read timeout to unwind. Seeking never
depends on a thumbnail result.

## Portrait and release-only scrub refinement

Device feedback from the initial candidate identified excessive preview padding
and live main-player seeking during dragging. The thumbnail now has a portrait
112 by 196dp rounded border, a 108 by 192dp image area, 2dp padding and center crop.
The timestamp sits below the thumbnail on a transparent row, with current time
white and the separator/total duration secondary gray. Edge clamping and time-only
fallback remain. Wide source frames crop at their sides rather than stretch.

Scrub start records the current position and playback intent, then pauses the
main player. Drag movement updates a pending destination, time labels and the
existing preview extractor only. Release commits one bounded seek and restores
playback only when it was active beforehand. ACTION_CANCEL discards the pending
destination before SeekBar's default stop callback. The original position remains
unchanged. Ownership refresh cannot restart a player during the drag; lifecycle
cleanup never resumes a detached or outgoing player. User pause preference stays
unchanged. Non-drag keyboard/accessibility progress changes still commit directly.

Refinement validation on October 16, 2026:

- Smallest first run: 12 tests passed across ShitTokScrubInteractionTest and
  ShitTokScrubPreviewTest, zero failures/errors/skips.
- Affected run: 36 tests passed, zero failures/errors/skips, across
  ShitTokScrubInteractionTest (now four tests, including real pointer UP/CANCEL),
  ShitTokInteractionUiTest, ShitTokProgressTest, ShitTokInteractionPolicyTest,
  ShitTokDynamicPinchTest and ShitTokSwipeStartTest.
- `:app:assembleDebug` passed. No broad local suites or lint were run for this
  refinement. Tests used the cached offline toolchain and disabled backend access.
- No new dependency, signing, package, persistence, backend or source configuration
  changes. Physical-device visual/codec/performance acceptance is still required.

## Validation scope

Focused tests cover threshold/time mapping/clamping, idle extraction exclusion,
bounded cache, latest-target coalescing, cadence, cancellation, stale results,
failure/timeout fallback, real-holder gestures, favorite add-only behavior,
optimistic failure recovery, explicit controls, progress visibility and paging
restoration. Fake extractors and players do not prove device codec/network speed.
The original local results below describe the first implementation. The initial
`f3d4623` signed candidate subsequently passed production-certificate/package/version
checks and Android 15 in-place upgrade CI before device testing.

Local validation completed on October 16, 2026:

- Clean focused run: 41 tests passed, zero failures/errors/skips. This includes
  interaction, preview, progress, pinch and real-pager swipe-start tests.
- Full debug suite: 682 tests, zero failures/errors, one skipped live Fapello
  smoke test. Full release suite: the same totals and skipped live test.
- `:app:assembleDebug` and `:app:assembleRelease` passed. Release vital lint
  passed as part of the release build. The broader debug lint audit was not run.
- Package IDs were checked in the built APKs and release APK signature validity
  was checked. These use local validation version/signing defaults. They are not
  production-signed upgrade candidates; no installed-user upgrade test was run.
- Gradle 8.11.1 / JDK 17 ran the repository's full test/build tasks. Test workers
  used cached Robolectric runtimes, empty backend settings and disabled outbound
  connections. A first all-offline build stopped at an uncached lint dependency;
  fetching public build dependencies and rerunning release assembly passed.
- Early test runs found single-tap timing/closed-fixture assertions and an
  animation assertion affected by Robolectric clock advancement. Those fixtures
  were corrected. A clean rebuild discarded stale compiled test output before
  the final runs. Routine chrome refresh retains an active scrub; actual feed
  configuration transitions invalidate it through the existing boundary.
- Final whitespace checks and protected-file diff checks passed. Current
  `rebrand/zerochill` was rechecked at the same merged #297 base.

Changed production scope: ChaosFeedView; ShitTokInteractionPolicy,
ShitTokScrubPreview, ShitTokScrubPreviewView and ShitTokVideoTaps; one matching
Media3 dependency. Five test files, the focused CI step and these notes complete
the diff. No physical video decoding, phone performance, startup timing or
signed-upgrade result is claimed.

Phone acceptance: rapid forward/reverse scrolling; single/double taps; repeated
and already-liked hearts; creator/button/scrubber touches; long/short clips;
fast scrubbing; scrub then immediately swipe; poor network; time-only fallback;
rotation/fullscreen; background/foreground; startup, three-dot loading, buffering
and runway availability compared with approved PR #297.

ShitTokPlaybackOwner, ShitTokRunwayPolicy, source mixing, preload/admission,
orientation policy, startup and loading ownership are unchanged. No application
ID, signing configuration, data format, database migration, backend, Supabase,
remote configuration or endpoint change is required.
