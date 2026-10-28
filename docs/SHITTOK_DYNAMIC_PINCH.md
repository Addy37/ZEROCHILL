# ShitTok dynamic pinch and focus mode

Current behavior keeps the existing ShitTok player, TextureView, playback owner, and media surface alive throughout the interaction.

## Interaction

Only the active, playing canonical holder can begin a pinch from the video surface. The live pinch uses damped scaling and a smoothed focal point so small finger movements do not create abrupt jumps.

A normal pinch release animates back to the resting frame. A deliberate outward pinch can promote into persistent Focus Mode. Focus Mode keeps the current video playing in the same holder, hides the normal ShitTok feed chrome, and allows additional two-finger pinches without recreating or handing off the player surface.

Focus Mode follows the supplied TikTok reference layout:

- black fullscreen canvas
- media reclaims the old feed viewport and is top-anchored above the compact control zone
- normal ShitTok title, metadata, action rail, and feed chrome disappear instead of shifting with the viewport
- neutral gray hairline scrubber with no visible thumb at rest
- small circular exit control at bottom-left
- compact rounded play/pause and speed pill at bottom-right
- no ZEROCHILL cyan treatment in the fullscreen chrome
- controls hide during another pinch and return after release

One-finger vertical swipes remain owned by the existing ViewPager2 while Focus Mode is active. Adjacent attached holders are prepped with their normal feed chrome hidden and their fullscreen viewport applied before selection, so swiping to the next or previous video stays in Focus Mode without a title jump or flash back to the normal feed layout.

A deliberate inward pinch exits Focus Mode and smoothly returns to normal ShitTok. The bottom-left exit control and Android Back remain fallback exits, but they are not required for the normal TikTok-style gesture flow.

## Lifecycle

Bind, detach, recycle, ownership changes, host pause, deactivation, refresh, close, configuration changes, and Back reset the pinch/focus controller. User page changes migrate Focus Mode to the newly selected holder instead of ending it. Focus Mode restores the original player layout and holder padding before returning control to the normal feed.

Completion-driven auto-scroll stays blocked until Focus Mode ends, while manual vertical paging remains available.

## Validation

Focused Robolectric coverage checks:

- scale limits and damped response
- smooth ordinary release
- Focus Mode persistence
- pinch-in Focus Mode exit
- vertical paging while Focus Mode stays active
- adjacent-holder feed chrome suppression
- Back/reset behavior
- parent interception and touch ownership
- player/surface continuity
- auto-advance blocking
- holder lifecycle resets
- fullscreen viewport reclaim and restoration

Physical-device validation remains required for final motion and visual matching, especially on high-refresh-rate displays.
