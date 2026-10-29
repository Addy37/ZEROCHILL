# ShitTok dynamic pinch and focus mode

Current behavior keeps the existing ShitTok player, TextureView, playback owner, and media surface alive throughout the interaction.

## Interaction

Only the active, playing canonical holder can begin a pinch from the video surface. The live pinch uses damped scaling and a smoothed focal point so small finger movements do not create abrupt jumps.

A normal pinch release animates back to the resting frame. A deliberate outward pinch can promote into persistent Focus Mode. Focus Mode keeps the current video playing in the same holder, hides the normal ShitTok feed chrome, and allows additional two-finger pinches without recreating or handing off the player surface.

Focus Mode uses a stationary control overlay above the existing pager:

- black fullscreen canvas
- media retains its measured resting size and position; Focus entry never clears holder insets or refits/recenters the picture
- normal ShitTok title, metadata, action rail, and feed chrome disappear instead of shifting with the viewport
- neutral gray hairline scrubber with no visible thumb at rest
- scrubber and button row occupy separate vertical zones so their touch areas never overlap
- 40dp circular exit surface at bottom-left inside a 48dp touch target
- compact rounded play/pause and speed pill at bottom-right with 40dp visual height inside 48dp touch targets
- the same live liquid-glass material as navigation, sampling the pager without capturing the controls themselves
- one shared control instance stays pinned while holders swipe behind it; owner and progress follow the selected holder
- play/pause updates the existing holder and playback-owner pause state, including while buffering
- controls hide during another pinch and return after release

One-finger vertical swipes remain owned by the existing ViewPager2 while Focus Mode is active. Adjacent attached holders are prepped with their normal feed chrome hidden and their fullscreen viewport applied before selection, so swiping to the next or previous video stays in Focus Mode without a title jump or flash back to the normal feed layout.

A deliberate inward pinch exits Focus Mode and smoothly returns to normal ShitTok. The bottom-left exit control and Android Back remain fallback exits, but they are not required for the normal TikTok-style gesture flow.

## Lifecycle

Bind, detach, recycle, ownership changes, host pause, deactivation, refresh, close, configuration changes, and Back reset the pinch/focus controller. User page changes migrate Focus Mode to the newly selected holder instead of ending it. Focus Mode leaves player layout and holder padding intact throughout entry and exit.

Completion-driven auto-scroll stays blocked until Focus Mode ends, while manual vertical paging remains available.

Drag-start and settling callbacks preserve Focus Mode before page selection changes. A canceled swipe keeps the current holder focused. Focus callback subscriptions become inactive immediately on cleanup, and removal is posted after ViewPager2 finishes callback dispatch. This avoids the callback-list mutation crash exposed by physical-device swiping with the completed-video replay observer installed.

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
- measured portrait/square picture size and position preserved across entry
- real touch dispatch through the alpha-hidden overlapping navbar
- non-overlapping progress/control geometry and minimum button touch targets

Physical-device validation remains required for final motion and visual matching, especially on high-refresh-rate displays.
