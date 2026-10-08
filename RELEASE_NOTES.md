# ZeroChill 4.3.6

- Fixed Shows fullscreen controls so the 2.2-second HUD idle timer resets from user interaction instead of unrelated player events.
- Button taps, holds, scrubbing, and double-tap seek now keep or restore fullscreen controls consistently before they auto-hide again.
- Fullscreen exit and player replacement now clear pending HUD timer and interaction state to prevent stale control behavior.
- Added regression coverage across Shows playback routes and the shared fullscreen controller.
- No changes to application ID, signing identity, persisted data, backend, Supabase, remote config, playback quality, buffering, preload, cache, or source routing.
