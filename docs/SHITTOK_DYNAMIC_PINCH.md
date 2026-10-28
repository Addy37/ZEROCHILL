# ShitTok pinch and Focus Mode

Current candidate branch: `feature/shittok-smooth-pinch-focus-mode`.
Base: current approved `main` after PR #359.
Keep unmerged pending CI and physical-device approval.

## Interaction and architecture

Only the active, playing canonical holder with the playback owner's attached surface
can start a feed pinch. The first touch must belong to the video rather than a button
or scrubber. A second finger enters immediately. Root-space pointer IDs and their
distance drive a bounded 0.45–3.5 transform on the existing Media3 content frame.

Live pinch motion is deliberately softened around 1x and the focal point is damped,
so small finger changes do not create the previous harsh 1:1 jump. The fitted
PlayerView, TextureView, player owner, layout size, and playback surface remain
unchanged.

An ordinary pinch release animates back to the resting transform instead of snapping.
A deliberate outward pinch that crosses the Focus Mode threshold promotes the current
video into a persistent portrait Focus Mode after the fingers leave the screen.
Focus Mode keeps the normal ShitTok feed chrome and vertical pager disabled while
reusing the same player and surface.

Focus Mode exposes a minimal player surface at the bottom:

- Exit
- Play / pause
- Progress scrubber
- Playback speed

The user can pinch again while Focus Mode is active. Back exits Focus Mode through the
same existing temporary-pinch reset path. The playback speed that existed before Focus
Mode is restored on exit.

`ShitTokDynamicPinchController.TouchFrame` still observes before child dispatch. It
cancels the original click/creator-swipe target once when the two-finger gesture takes
over, blocks parent interception, and consumes the remainder of the owned gesture.
Controls in the Focus Mode chrome remain normal child views and receive their own
touches.

Cancel, third-finger interruption, holder bind/detach/recycle, outgoing ownership,
page changes, host pause/deactivation, refresh, close, configuration changes, and Back
reset the transform and Focus Mode safely before reuse.

## Compatibility boundaries

This feature must not:

- recreate ExoPlayer
- move or replace the TextureView surface
- change the ShitTok player pool or ownership model
- alter feed admission, preload, source selection, creator handoff, comments, likes,
  favorites, history, downloads, persisted settings, or account data
- change application ID, signing identity, backend, Supabase, or remote config

The existing player/surface continuity is more important than reproducing every TikTok
visual detail.

## Validation scope

Focused Robolectric coverage exercises:

- scale bounds and softened response
- focal-point damping without layout resize
- animated ordinary release
- outward-pinch Focus Mode promotion
- Focus Mode persistence after finger release
- Back/reset behavior
- parent interception and child cancellation
- auto-advance staying blocked until Focus Mode exits
- real-holder player and surface continuity
- holder, page, lifecycle, ownership, refresh, close, and configuration resets

CI remains a regression signal. It cannot prove physical video pixels, hardware decoder
behavior, 120 Hz motion feel, or the final placement of Focus Mode controls on the
OnePlus device.

## Phone checklist

Use the signed Beta/upgrade candidate without uninstalling or clearing app data.

1. Compare a normal small pinch against TikTok. ZEROCHILL should feel resistant rather
   than twitchy and should animate back instead of snapping.
2. Pinch outward enough to enter Focus Mode. Release both fingers. Focus Mode should
   remain active.
3. Confirm normal ShitTok header, action rail, captions, and bottom navigation remain
   hidden while Focus Mode is active.
4. Confirm the Focus Mode controls show Exit, play/pause, scrubber, and playback speed.
5. Confirm playback and audio continue without restarting when Focus Mode begins.
6. Scrub and resume playback from the Focus Mode controls.
7. Change playback speed, exit Focus Mode, and verify the previous baseline speed is
   restored.
8. Pinch again while already in Focus Mode and confirm the transform remains smooth.
9. Press Back while in Focus Mode. It should return to the same ShitTok item with normal
   chrome and vertical paging restored.
10. Use X to exit Focus Mode and compare the return animation with TikTok.
11. Pinch inward and release without crossing the Focus threshold. It should return to
    normal feed mode.
12. Add a third finger or cancel a pinch. It must not leave a transformed frame or stuck
    pager.
13. Verify one-finger vertical swipe still selects clips normally after exiting.
14. Verify creator swipe, comments, like, favorite/save, share, double tap, hold-for-2x,
    mute, and scrubber still work after repeated Focus Mode entries.
15. Let a clip finish while pinching or in Focus Mode. Auto-advance must not move until
    Focus Mode exits.
16. Background/foreground, rotate, switch tabs, refresh, and return from creator gallery.
    Every incoming clip must start at the normal transform and UI state.
17. Listen for overlapping audio or a previous clip continuing after page changes.
18. Compare motion on the 120 Hz physical device directly against the supplied TikTok
    reference recording.

Do not merge until the physical-device behavior is approved.
