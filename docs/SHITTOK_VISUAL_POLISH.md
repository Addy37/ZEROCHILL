# ShitTok visual polish / Preview 38

Baseline: fetched approved main `5f24b294a0e2c3155a7b395894c931608be93dd9`,
including merged Library #412, OnlyFap motion #413 and gallery polish #414.
Branch: `feature/zerochill-shittok-visual-polish`.

## Current attachment-viewer correction

The user approved the comments redesign and supplied a recording of a photo opened
inside the old tall glass alert card. The shared attachment viewer now uses a black
full-window canvas with a 48dp Close target protected by system-bar/cutout insets.
FIT_CENTER preserves the whole photo without cropping. The same fix applies to DM
attachments. Authenticated image loading, memory-only caching, GIF animation,
request cancellation and dismissal cleanup retain their existing implementation.
The comments sheet, inline thumbnails, playback and data flows are unchanged.
Local validation passed 97 tests across five focused media/comments/keyboard suites
and `:app:assembleDebug` in 2m, with zero failures, errors or skips. The media lifecycle
suite now also runs on API 36; it covers uncached PNG/JPEG/WebP, repeated opening,
GIF animation, authenticated loading, cancellation and dismissal. New full-window
geometry/Close checks and synthetic portrait/landscape captures verify the borderless
canvas. Workflow syntax and diff checks passed. PR #415 records the signed CI artifact.
Physical OnePlus appearance, insets and upgrade acceptance remain pending; full
unfiltered suites and lint are not claimed.

## Current comments-sheet follow-up

The user supplied 16129.jpg after Preview 36 `45355fe4`, accepted the recommended
comments redesign, and requested a nicer comment heart. The shared native
`InlineCommentsDialog` keeps its existing data/auth/media/posting flows and applies
consistent presentation to ShitTok and its other entry points. Near-black rows,
a compact themed-glass header/composer, grouped username/timestamp, readable body
text and a right-aligned vector heart/count replace the sparse gray presentation.
Unliked hearts use a clean outline; liked hearts fill with the saved accent.
Reply threads collapse behind View replies and focused/deep-linked ancestors expand.
The sheet opens near 60 percent in portrait and drags toward full height; IME
changes preserve the composer and the user's prior expansion state. Existing
resize/close callbacks, moderation, attachment and retry/ownership logic remain.
The approved Preview 36 avatar/rail and prior swipe/text/scrub work stay intact.

Local validation passed 256 tests across 30 focused suites with zero failures,
errors or skips, plus `:app:assembleDebug` (3m25s). This covers comments, owner
moderation models, keyboard/media lifecycle, navigation, social counters and the
existing ShitTok playback/interaction/theme regressions. A final Send-icon centering
correction uses an ImageView; all 26 focused comments cases and the debug build
passed again for that change (1m16s). CI repeats all 30 suites before signing; the PR records
the exact source SHA and signed artifact.

Native captures verified portrait/landscape geometry, grouped identity/time and
heart/count placement. Height assertions caught and corrected a CoordinatorLayout
gravity offset; IME tests cover both window resize and inset-only keyboard layouts.
Successful replies expand their parent, deep links reveal ancestors, and row-level
like updates preserve thread visibility. No full unfiltered suite or lint run is
claimed for this scoped follow-up.
Native test captures use placeholder content; physical OnePlus keyboard, sheet
gestures, GPU video composition and 120Hz acceptance remain device checks.

## Preview 36 physical-feedback follow-up

The user tested Preview 35 `1d6b3fa2` and supplied 16118.jpg plus TikTok reference
16121.jpg. The separate plus badge was rejected, and the old icon shapes did not
provide the requested improvement. Continue the same draft PR #415 with a compact
creator badge overlapping the avatar's lower edge. Keep gallery and favorite touch
routing distinct. New scoped vectors give the rail a visibly bolder white heart,
bookmark, comment bubble, download, speaker and share arrow, with selected-accent
active states and 48dp main action targets. The badge itself is 22dp, with a compact
36dp touch region that preserves avatar-center gallery taps instead of separating
it from the photo. Shared button typography/padding keeps 28dp artwork unclipped.
The landscape header Share icon aligns vertically with Back.

Download replaces the Auto Scroll rail action and reuses the existing downloader.
Share replaces the rail overflow and uses the existing Android share route. Holding
Share retains access to the shared glass action menu, including Auto Scroll; playback
completion behavior and its current session state are preserved. Portrait and
landscape receive the same action/artwork changes. Preview 35's swipe/text/scrub
polish remains. No player, source, ranking, persistent data or backend change.

The follow-up's actual test/build results and signed APK are recorded on PR #415
after validation. Its validation artifact also includes native Robolectric portrait
and landscape captures with synthetic media and placeholder creator artwork.
The download regression covers unresolved-page and already-resolved stream routes;
the production downloader and its source/header handling are unchanged. The historical validation below describes Preview 35 only.

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

## Preview 35 implementation (original candidate)

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
one signed release-package APK. Preview 38 is `7.0-preview.38-image-viewer` / `6000040`.
It injects existing public client configuration through existing repository secrets,
restores the existing production signing identity, checks ancestry from the approved
baseline and compares APK package/certificate/version against the current stable APK.
The filename includes source branch and commit SHA; checksums and validation metadata
travel with the artifact. No keystore or private credential is committed.

## Preview 35 validation

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


## Preview 36 local verification

Final validation passed **168 tests across 22 focused suites**, zero failures,
errors or skips, together with `:app:assembleDebug` (4m43s). APIs 35/36 cover
attached badge/gallery/Like touch routing, default white and active accent states,
28dp icon bounds inside 48dp main buttons, Share/Download in both orientations,
shared-glass menu access, reduced motion and recycled state. Existing playback,
pinch, seeking, mute, Auto Scroll and creator-navigation regressions remain passing.

The first run found an incomplete download-test shadow: rotation can restore a
resolved stream, invoking `downloadKnown` rather than `downloadPage`. The regression
now covers both existing entry points and checks the exact resolved media URL.
No downloader or playback fix was needed. Native Robolectric captures with a bound
placeholder creator were inspected in portrait and landscape, including the attached
badge and aligned header Share/Back icons. These captures and an independent render
of the actual vector paths confirm the visual changes; they do not establish GPU
video composition, OnePlus appearance, 120Hz pacing or physical upgrade acceptance.
Workflow syntax and diff checks passed; CI repeats the suite before signing.
Test-step analytics endpoints are blank, matching local tests; release-build client
configuration, package, signing identity and saved data remain unchanged.
