# ShitTok dynamic pinch and focus mode

Current behavior keeps the existing ShitTok player, TextureView, playback owner, and media surface alive throughout the interaction.

## Interaction

Only the active, playing canonical holder can begin a pinch from the video surface. The live pinch uses damped scaling and a smoothed focal point so small finger movements do not create abrupt jumps.

A normal pinch release animates back to the resting frame. A deliberate outward pinch can promote into persistent Focus Mode. Focus Mode keeps the current video playing in the same holder, disables vertical paging, hides the normal ShitTok feed chrome, and allows additional two-finger pinches without recreating or handing off the player surface.

Focus Mode follows the supplied TikTok reference layout:

- black fullscreen canvas
- media reclaims the old feed viewport and is top-anchored above the compact control zone
- neutral gray hairline scrubber with no visible thumb at rest
- small circular exit control at bottom-left
- compact rounded play/pause and speed pill at bottom-right
- no ZEROCHILL cyan treatment in the fullscreen chrome
- controls hide during another pinch and return after release

Back or the exit control restores the original feed viewport, pager input, playback speed, and ShitTok chrome. The player and its attached surface remain the same object across entry and exit.

## Lifecycle

Bind, detach, recycle, ownership changes, page changes, host pause, deactivation, refresh, close, configuration changes, and Back reset the pinch/focus controller. Focus Mode restores the original player layout and holder padding before returning control to the feed.

Completion-driven auto-scroll stays blocked until the pinch or Focus Mode ends.

## Validation

Focused Robolectric coverage checks:

- scale limits and damped response
- smooth ordinary release
- Focus Mode persistence
- Back/reset behavior
- parent interception and touch ownership
- player/surface continuity
- auto-advance blocking
- holder lifecycle resets
- fullscreen viewport reclaim and restoration

Physical-device validation remains required for final motion and visual matching, especially on high-refresh-rate displays.
