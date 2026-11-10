# Phase 6 — OnlyFap V2 motion candidate

Production baseline: `7269d41e37732a4744ef6d23927e223017caa7bb` (merged PR #412).
Reference: ZEROCHILL-V2 main `9ce121f`, its actual imported native hero ripple,
indicators and preference implementation. One branch and draft PR #413.

## Current candidate: Preview 28

The user tested Preview 26 (`f1f3daa4`) and supplied
`Record_2026-11-10-07-25-37_62c688c2cc5660a137780e37d3cb9d1a.mp4`.
Preview 27 (`51c7c2bd`) replaced the opening morph with a reveal of the whole
gallery and added the accent-filled View Gallery action. The user approved that
visual direction, supplied `Record_2026-11-10-07-51-43_62c688c2cc5660a137780e37d3cb9d1a.mp4`,
and requested opening from the actual tap and reversing Back to its origin.

- Ready normal hero changes retain V2's 650ms native circular reveal at 50%/47%
  with white/accent rings. The existing two Glide owners remain alive through
  bounded cleanup. Selection, original quality/cache keys, filtering and the
  13-second timer stay intact. Startup crossfade and category liquid-rift remain.
- Centered 6dp pagination dots use a sliding/pulsing accent pill and a seven-slot
  window, with 18dp spacing and 14dp active width. Index updates follow committed
  image identity; automatic rotation, swipes and dot taps share the ready path.
- The anchored two-column preference grid retains all six options and original
  persistence/filter callbacks, existing glass settings, small-window scrolling
  and lifecycle dismissal. V2 main actually has a vertical list, so the requested
  grid is an intentional difference; icons/accent/glass follow that reference.
- **Gallery opening:** native circular reveal of the complete live toolbar,
  creator header, tabs, loading state and media grid, with the same 650ms timing,
  deceleration and ring overlay as the hero. Gallery center is the actual tap
  position, captured before the child click and converted from screen into gallery
  root coordinates. Keyboard/programmatic entry uses the source view center, then
  a window-center fallback. The scoped translucent
  window exposes the paused live homepage outside the circle. The fully revealed
  root remains OLED black. Existing media-viewer transitions remain separate.
  Gallery requests start normally and can update the tree during the animation.
  No source photo morph, screenshot, image capture, bitmap handoff, new decoding
  or extra image request is used. The old shared-image/header machinery is removed.
- **Back:** reverses the circular reveal and rings toward the same persisted
  origin, with a single completion owner. Rapid Back and Back during opening are
  bounded; reduced motion, API 26 and unsupported native reveals use instant or
  clean-fade fallback. Pause, stop, destruction, detachment, resizing
  and a finite fallback settle the root; recreation does not replay entrance.
  API 26 uses the original opaque window and immediate entrance, avoiding Android
  8.0's translucent/portrait-orientation incompatibility.
- **View Gallery:** fills with the current selected accent and uses the existing
  contrasting on-accent text color, including White. Refreshes when returning to
  the hub. All seven saved accent choices remain intact.

## Styling ownership and compatibility

The existing Shows Watch translucent-window theme is reused only for the scoped
OnlyFap gallery intent. The accent overlay is reapplied after theme selection.
OledThemeController excludes that window so its delayed passes cannot paint an
opaque rectangle behind the circular root. OledImmersiveUiController unwraps the
new reveal host before resolving the original toolbar, preserving its geometry.
Safe insets still belong to the existing responsive pass. Other browser routes,
Library, Shows and full-screen media viewing retain their original behavior.

Production package, signing identity, preferences, backups, source parsers,
gallery data, media playback, downloads and favorites are unchanged. No migration,
backend, Supabase or remote-config change. Preview 28 uses `6000030`, above Preview
27 `6000029`. The scoped workflow builds one signed release APK and compares the
actual package/certificate with stable plus the preview upgrade floor. Filename
includes branch and SHA. No merge, tag, public release or publication is authorized.

## Verification and limitations

Preview 26 passed 192 focused local tests, debug assembly, the same CI suite and
signed release assembly. Its image-morph design was superseded by the user's
physical feedback. Preview 27 passed 202 focused tests and both debug and signed
release assembly; its visual direction was physically accepted. Preview 28 results
are recorded on PR #413 and in the handoff. New tests cover exact touch dispatch,
posted clicks, persisted/clamped origins, reverse easing, partial-open Back, repeated
Back, closing input suppression and completion ownership, plus live-tree updates,
reduced motion, recreation, rapid launches, Back, detach/resize, missing first draw,
delayed OLED styling and all seven button accents on API 35/36. Existing hero
ownership, cache, original quality, filtering, preference and gallery regressions
remain required. Full unfiltered tests, lint, emulator and physical in-place upgrade
are not claimed. OnePlus Android 16 optical rendering, Back gestures and 60/120Hz
frame pacing need physical acceptance. The native reveal is finite; it performs no
continuous screen sampling and holds no animation image buffers. A translucent
window retains the paused source Activity, as the existing Shows transition does.

## Files

Core surfaces: OnlyFapHubView, OnlyFapHeroRippleView, OnlyFapHeroIndicators,
OnlyFapPreferencePicker, ContentPreferenceUi, onlyfap_preference_ids.xml.
Gallery integration: CreatorGalleryTransition, CreatorGalleryReveal,
NativeFeedBrowserActivity, MainPagerAdapter. CreatorProfileHeader and
NativeMainActivity remove the Preview 26 morph-only additions.
Styling integration: OledThemeController, OledImmersiveUiController.
Tests: OnlyFapHeroDesignTest, OnlyFapHeroIndicatorsTest,
OnlyFapHeroBitmapLifecycleTest, OnlyFapPreferencePickerTest,
CreatorGalleryTransitionTest, CreatorGalleryRevealIntegrationTest.
Delivery: .github/workflows/onlyfap-motion-candidate.yml, this document,
ZEROCHILL_BUILD_LINEAGE.md. The PR Files tab is the complete baseline diff.

## Preview 27 local verification

The final offline run passed **202 tests across 20 focused suites** and
`:app:assembleDebug` (2m11s), using Java 17 compilation and Java 21 Robolectric.
The initial expanded run had one stale assertion expecting matching dark gallery
and preference buttons; it now checks the requested accent fill and retains the
original geometry/touch-target checks. Review added the API 26 opaque-window
fallback before final validation. Diff scope and whitespace checks passed.

## Preview 28 local verification

The final offline run passed **224 tests across 20 focused suites** and
`:app:assembleDebug` (2m11s), using Java 17 compilation and Java 21 Robolectric.
The focused origin/reverse run also passed. Tests include actual off-center hub
button dispatch through a posted click, stale/consumed/keyboard fallback origins,
screen offsets and insets, clamping and recreation, reverse easing and partial
opening, repeated Back, close-time input suppression, lifecycle finish ownership
and real NativeFeed Back/pause integration. Original hero, gallery, preference,
quality/cache and accent regressions remain passing. Diff checks passed. No
physical-device or full unfiltered test claim is made; CI/signed artifact evidence
is recorded on PR #413 after completion.
