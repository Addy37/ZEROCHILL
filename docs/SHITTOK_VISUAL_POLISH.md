# ShitTok visual polish / Preview 35

Baseline: fetched approved main `5f24b294a0e2c3155a7b395894c931608be93dd9`,
including merged Library #412, OnlyFap motion #413 and gallery polish #414.
Branch: `feature/zerochill-shittok-visual-polish`.

## Scope and acceptance

Polish the existing native ShitTok UI using the saved theme, motion and glass
settings. Keep white default floating actions with 48dp minimum targets, current
accent for active states and creator ring/favorite badge, readable bottom metadata,
a thin accent scrubber and finite interaction/settled-control motion. Vertical
fade follows pager progress, restores on cancellation and recycled binding, and
accounts for the existing TextureView without replacing or reconnecting players.
Reduced motion disables decorative movement. Menus reuse the existing shared glass
host and settings. Portrait and landscape retain original controls/inset ownership.

Playback engine, player pool, buffering/preload, source selection/ranking, pinch,
double-tap/long-press, creator gallery gestures, persistence, package and signing
identity are protected. No backend, remote-config, preference or data migration.

## Implementation

`ChaosFeedView` remains the UI owner. Existing 24dp white icons now use a narrower
52dp rail with consistent 48dp targets and 2dp vertical margins. A bottom gradient
supports the text and icons, without permanent rail containers. Titles use 18sp
with slightly more line spacing; metadata uses 13sp. Creator ring and favorite badge
refresh from saved accent preferences on attach, resume and active preference changes.
Separate 48dp gallery/favorite targets avoid stealing avatar taps or Like touches.

Pager offset controls at most 4% alpha reduction on the page and its actual
TextureView. Where Android compounds both layers, the video remains above 92%
opacity. Idle, bind, recycle and detach restore full opacity without touching the
player/surface lifecycle. Settled controls use a finite 190ms / 5dp reveal; tap and
orientation interruption reset offsets. Like and favorite pulses, press feedback and
the scrubber visibility transition respect existing motion settings. The existing
double-tap heart now also checks the app motion preference.

The existing scrub interaction, seek/timestamp/preview code and 48dp hit row remain.
The passive track is 2dp with no thumb; interactive controls expose a 4dp track and
thumb. Visibility has one owner so repeated updates do not restart the fade.

More now uses `ZeroChillMenu` and its existing `PreferenceGlassPanel`/Compose host,
including saved glass customization and shared entrance/exit transitions. All Save,
Actions and Feed actions remain. The portrait menu grows above its lower anchor;
the landscape menu opens below the header. Shared sheet components and other callers
are unchanged. No new dependencies, capture loop or explicit rendering layer.

## Candidate delivery

The scoped `shittok-visual-candidate.yml` workflow runs focused regressions and builds
one signed release-package APK. Preview 35 is `7.0-preview.35-shittok` / `6000037`.
It injects existing public client configuration through existing repository secrets,
restores the existing production signing identity, checks ancestry from the approved
baseline and compares APK package/certificate/version against the current stable APK.
The filename includes source branch and commit SHA; checksums and validation metadata
travel with the artifact. No keystore or private credential is committed.

## Validation

Local results are recorded below; the PR records the signed CI artifact and source SHA. Physical OnePlus
Android 16 / 120Hz optical rendering, playback continuity and upgrade acceptance
require device testing. Unit tests cannot establish GPU TextureView composition or
frame pacing. No merge, tag or publication before the user's APK approval.


The initial expanded run exposed a cache-wake test counting independent fixture
maintenance. A fake-player stack identified `runDeferredPrepareMaintenance ->
resolveAhead -> prepareVisible -> source -> syncAudio`; the wrong-URL wake itself
still rejects that URL. The isolated test passes on untouched baseline main.
The cache-wake regression now finishes initial fixture callbacks and cancels only
setup's warm-player maintenance before resetting its fake counter. Its wrong-URL,
matching-URL and drag assertions remain intact; the focused regression passes.
No production playback or preload code changed for this test correction. A new
ring-color assertion uses Robolectric's drawable state rather than unsupported
legacy-canvas pixel output. All 14 new visual cases passed on APIs 35 and 36.


Final local validation passed **158 tests across 21 focused suites**, with no
failures, errors or skips (2m53s). This includes 14 new visual cases on APIs 35/36,
plus existing playback ownership, swipe/settling, pinch, scrub/preview, mute/speed,
auto-scroll, creator handoff, theme and shared-glass regressions. The same final
production source passed `:app:assembleDebug`; subsequent corrections changed
tests only. Java 17 compiled the APK; Java 21 ran Robolectric. Workflow YAML,
embedded shell syntax, ancestry and staged diff/whitespace checks passed.

The initial local runner needed a writable temporary home for Robolectric and
the existing Android 16 test runtime. These environment adjustments remain outside
the repository. Full unfiltered debug/release suites, lint, emulator/device playback,
120Hz profiling and physical in-place installation are not claimed. The signed
candidate uses the repository's established CI signing/identity checks.
