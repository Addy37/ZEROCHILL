# Astra UI restoration and Square navbar

Physical acceptance remains open. This correction follows the October 15 product
decision: restore non-navbar visuals to `13a32ab9ed2ab2a4934504493de49e4c6f40d775`
and reuse Square's navbar stack at `5189ba37500b3985d53a1f6f985d01053c536338`.
PR #295 stays draft and unmerged. No release or production publication is authorized.

## Source and scope

The rejected source was `f6655ce7bf2c17f24146700da5aa2d908688f30f`, based on
`rebrand/zerochill` at `91afd759`. Non-navbar production Java/resources modified
since Astra are restored byte-for-byte, except `NativeMainActivity` adds an unread
badge handoff to the navbar. This restores Shows/OnlyFap headers, Library/social
cards, account/profile, inbox/messages, dialogs and floating utility chrome.
`FrostedOverlayLayout`, `FrostedBackdropRenderer` and `ZeroChillUi` return to Astra.
The rejected `LiquidGlassShader`, `LiquidGlassDrawable`, `ZeroChillChrome` and
`ZeroChillFloatingWindow` are removed. Pale selected ink `#8ADEFF` is removed.

## Direct reuse

`glass/` contains Square's actual `LiquidBottomTabs`, `LiquidBottomTab`,
`DampedDragAnimation`, `InteractiveHighlight`, pointer inspector, frame coroutine,
`GlassEffect`, complete non-catalog backdrop stack, and continuous capsule shapes.
The DrawBackdrop nodes, AGSL shaders, RuntimeShader cache, lens, blur, color controls,
highlights, shadows, inner shadows and selected-content replica are reused.
No published backdrop AAR or separately approximated View shader replaces them.
Source headers and packaged third-party notices/licenses are retained.

SquareApp's actual expanded control uses a 61dp row, 16dp side margins, 8dp bottom
clearance above real system insets, 26dp icons, 9sp bold labels/11sp line height,
2dp icon-label gap, 4dp selector inset, white 12% film and black 50% selector wash
in dark mode. These values are used here. Its geometric wrapper captures the shell
as a sibling before selector drawing; that ordering is reproduced in the bridge.
Its music player/search slots and branding are not imported.

## Adaptation and architecture

The Java BottomNavigationView remains only as the existing menu/selection API.
Its Material hierarchy is hidden and replaced by one navbar-local ComposeView.
`SquareNavigationBridge` provides ZEROCHILL icons, labels, #0892D0 selected ink,
Astra #B8C0CC inactive ink, Library unread dot, and lifecycle/saved-state owners.
The existing android.app.Activity, screens, ViewPager2, refresh/warmup pipeline,
player ownership and data remain intact. No Compose screen migration occurs.
The Compose compiler plugin matches Kotlin 2.2.10. Compose BOM 2026.06.01 (the
pinned Square BOM) supplies UI, foundation, animation and Material3. No icons
library, third-party binary renderer, or new media dependency is added.

`FrostedNavigationLayout` records real child View display lists once on page
invalidation, excluding navigation, then composites that recording to the screen.
`ViewPageBackdrop` supplies that same RenderNode to Square's cropped effect layers,
using window coordinates and the same inverse layer transformation as LayerBackdrop.
No PixelCopy, CPU bitmap screenshot, synthetic production backdrop, or recursive
capture of the navbar is used. A changed page advances a draw-only revision.
Navbar springs do not request page recordings. Detach discards the page display
list; foreground return marks it dirty. Hidden or alpha-zero navigation skips
capture, preserving ShitTok pinch's clear display.

Existing media player XML uses TextureView, including ShitTok and Shows fullscreen.
TextureView participates in hardware View display lists. A separately composited
SurfaceView is outside this bridge and would need its own capture integration;
none of the existing primary player surfaces is converted to SurfaceView here.

Square's drag animation remains the optical owner. Optional hooks feed existing
ViewPager fake-drag callbacks after touch slop, and report cancellation. External
fractional pager progress synchronizes the selector without feeding navigation
back into itself. ZEROCHILL's existing four-icon collapse, 50dp compact endpoint,
60dp compact side margins and scroll-driven target ownership are retained.
Only expanded proportions and the old synthetic Material inset are replaced.

API 33+ supports AGSL spatial refraction and selector dispersion; API 31/32 supports
blur/color processing without AGSL; older versions use the non-refractive film.
Square's low-RAM shell guard is retained. Fallback appearance is not full refraction.
No app-owned always-running animation or repeated shader compilation is added.

## Validation tools

Robolectric checks protect retained selection, fake-drag, collapse ownership,
background return, system clearance and branding. Old Material pixel fingerprints
are replaced because they describe a renderer the product explicitly rejected.
They are not evidence of optical parity.

The debug-only probe uses the real navbar over moving high-contrast geometry,
actual production ShowsHubView cards/text/artwork ImageViews, and the existing
Media3 TextureView layout with a local test video. It compares the same shell
with lens enabled and lens disabled, records motion with both navigation modes,
expanded/collapsed states, a held drag/release, and background return. The debug
video and Activity are excluded from release. `capture_glass_probe.py` retains
recordings, frame statistics, memory and shader logs. No emulator result approves
physical parity or production media performance.

Results are recorded after execution. Final acceptance belongs to the maintainer
comparing the signed upgrade candidate and Square on the physical device.

## Compatibility

No application ID, signing identity, persisted settings, storage schema, account,
authentication, favorite/history/download/backup, content source, Supabase, backend,
remote configuration, release metadata or data migration changes.

## Current execution checkpoint

The local focused navigation run passed 28 tests; two remaining assertions still
expected the deliberately replaced 64dp/16dp Material geometry. Those assertions
were updated to Square's 61dp shell and zero navbar-local system gutter, and both
passed in the final focused rerun. `:app:assembleDebug` passed. The bridge's wrapped
Context and window-level lifecycle-owner integration faults found by these tests
were corrected before this checkpoint. Broader CI, release/signing checks and GPU
evidence remain pending; no physical acceptance is claimed.

A broader run reached 621 debug tests with one software-canvas failure and one
skip. RuntimeShader paints now check the actual Canvas hardware capability; the
hardware shader path is unchanged. The native software render regression then
passed after replacing its remaining retired Material-menu geometry assertions.
External pager synchronization also suppresses redundant navigation redispatch,
with a focused regression case. The GPU recorder now starts before its held drag,
and obtains accessibility bounds while the page is stationary.

Local CPU-emulator startup failed before producing usable GPU evidence. This is
not a refraction clearance; the connected CI emulator is the GPU validation path.
