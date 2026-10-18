# ShitTok temporary dynamic pinch candidate

Base: `rebrand/zerochill` at `d46516d8b9319d32a926bff9e13332841e0d094f`.
Candidate branch: `feature/shittok-dynamic-pinch`. Keep unmerged pending phone testing.

## Interaction and architecture

Only an active, playing, canonical holder with the owner's attached surface can
start a pinch. The first touch must belong to the video, rather than a button or
scrubber. A second finger enters immediately. Root-space pointer IDs and their
distance drive a 0.45–3.5 scale, relative to the initial distance. The current
midpoint becomes the focal point. There is no persistent zoom, pan or double-tap mode.

`ShitTokDynamicPinchController.TouchFrame` observes before child dispatch. It
cancels the original click/creator-swipe target once, blocks parent interception
and disables vertical pager input. It consumes the remainder of that touch
sequence, including finger release, so it cannot toggle pause or open a creator.
Pointer up, final up, cancel and a third finger immediately restore scale 1,
original pivot, chrome and pager input. A fresh one-finger gesture works normally.

ShitTok already inflated `view_video_player_texture.xml` before this change.
The candidate preserves that TextureView, Media3 1.11.1, the fitted PlayerView
layout and the three-player owner. Only `exo_content_frame` receives transform
properties. App chrome, subtitles, artwork/loading overlays, header and bottom
navigation hide using alpha without changing viewport dimensions or insets.
There are no per-move allocations, layout parameters, player calls or surface
handoffs. One copied cancel event is allocated at gesture entry.

Bind, detach, recycle, outgoing ownership, page/scroll changes, host pause,
deactivation, refresh, close, configuration and fullscreen/gallery/comments
transitions reset the controller. Touch-origin state also clears before reuse.
Async chrome callbacks cannot reveal controls during pinch. Completion-driven
auto-scroll waits until pinch ends. Automatic creator-gallery warming remains off.

## Validation scope

The focused Robolectric test exercises actual multi-pointer dispatch, scale
limits, midpoint, immediate reset, child cancellation, parent interception,
normal/overlay touches, rapid repetition, no layout request during scaling,
real-holder player/surface continuity, pending auto-scroll and lifecycle/reuse.
It uses the existing offline, decoder-free playback fixture. It cannot validate
actual video pixels, hardware decoding or physical swipe frame timing.

The branch-specific CI comparison runs only the existing short `chaosScroll`
Macrobenchmark: approved source then candidate, three warm iterations each on
one API 35 Pixel 6 emulator, with app data cleared between sources. It preserves
both source SHAs, frame timings, page/holder/pool/surface trace totals, memory and
device diagnostics. The ten-flow performance workflow skips this branch.
Live-source ordering/network variance and fixed run order limit causal claims;
these emulator numbers are regression signals, not proof of phone smoothness.
Actual results and failures belong in the PR delivery report.

## Phone checklist

Use the signed upgrade candidate. Do not uninstall the existing app or clear data.

1. One-finger vertical swipe still selects videos normally.
2. Pinch inward during playback; it shrinks below normal and chrome hides.
3. Pinch outward; video enlarges around the midpoint, with unscaled controls.
4. Repeat rapid pinches, alternating shrink and enlarge.
5. Playback and its audio continue during each pinch.
6. Lift the first finger before the second; scale/UI/paging reset immediately.
7. Lift the second finger first, then both fingers together in another gesture.
8. Cancel a pinch by backgrounding or rotating; return at normal scale/chrome.
9. Move vertically with two fingers during pinch; the page must stay selected.
10. Release and immediately start a fresh one-finger swipe.
11. Pinch then rapidly move forward and backward through other videos.
12. Every incoming video starts at exactly 1.0x.
13. Every incoming video's normal UI remains visible.
14. Listen for audio from a previous or queued video.
15. Check for overlapping audio or duplicate playback starts.
16. Open/close comments after pinch.
17. Like, save/favorite and share after pinch.
18. Test auto-scroll, including holding a pinch when the video finishes.
19. Rotate and enter/exit horizontal fullscreen after pinch.
20. Open/return from a creator gallery, including its horizontal swipe.
21. Background/foreground after using pinch; resume normal playback.
22. Scroll rapidly after many pinches, including immediate reverse swipes.
23. Compare hitching and stray audio with approved 4.3.7 on the same phone.

Also start on a button/scrubber with one finger and add another. That control
must keep its existing behavior rather than activating video pinch. Adding a
third finger should end the pinch without leaving the video transformed.

No application ID, stable signing, persisted settings/data, playback/preload,
content-source, backend or remote-config change is intended. The feature remains
an unapproved candidate until the physical checklist passes.
