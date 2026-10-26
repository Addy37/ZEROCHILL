# Shows watch motion, PR #348

Source inspected before editing: `3dd758030f247400d093b6891a87a9171eebcfa6`,
`feature/shows-youtube-motion`. This is an update to the existing PR, not a merge.
The watch layout and provider selection remain on that branch's existing path.

## Reference measurements

`13845.mp4` is the YouTube target; `13852.mp4` is the original ZEROCHILL candidate.
Both supplied recordings are 720 × 1584, nominally 120 fps. Actual presentation
timestamps are variable, so extraction used timestamp resampling at 120 fps,
not a count of compressed video frames. Timing precision is approximately one
recorded display interval (8.3 ms), with additional uncertainty at static endpoints.

The first YouTube open was inspected at 25 ms intervals and then tracked at
8.3 ms intervals using normalized image correlation of the unchanged poster.
Coordinates below are recording pixels. The tracked patch has a fixed 30 px
offset below the poster's upper edge.

| Timestamp | Poster top | Observation |
| --- | ---: | --- |
| 2.433 s | 808 | Browsing card still at rest |
| 2.450 s | 768 | First tracked moving frame |
| 2.500 s | 636 | Browsing remains visible above the poster |
| 2.550 s | 480 | Watch details follow below the poster |
| 2.600 s | 312 | Continuous movement toward the player slot |
| 2.650 s | 174 | Deceleration |
| 2.700 s | 92 | Near destination |
| 2.725 s | 80 | Geometry visually settled |

The selected implementation is **300 ms**, with a native cubic
`PathInterpolator(0.44, 0.37, 0.65, 1)`. A least-squares fit against the normalized
poster movement, allowing the sub-frame start to vary, gives approximately 1.8 px
RMS vertical error over the measured movement. This is a fit to visible output,
not a claim about YouTube's internal animator settings. An earlier overview-only
400 ms estimate was superseded by the timestamp and poster tracking.

Return was inspected independently, particularly 19.650–20.050 s. The first
changed geometry appears around 19.667 s; the small return rectangle is visually
settled around 20.025 s. At 19.708, 19.750, 19.808 and 19.917 s its left edge is
approximately 54, 142, 230 and 330 px, approaching 360 px. A **400 ms** return with
`PathInterpolator(0.15, 0, 0.22, 1)` approximates that normalized trajectory, with
about 1.6% RMS displacement error. Its final low-velocity tail is below the
recording's pixel resolution before the nominal animator endpoint.

YouTube returns to a persistent bottom-right mini-player in this recording.
ZEROCHILL deliberately returns directly to the originating card, as requested.
The return curve uses normalized displacement, not YouTube's mini-player bounds.
The supplied first return around 9.5–9.9 s was also inspected for page reveal.

No bounce, spring, overshoot, separate geometry waypoint, entrance delay, or
player-opacity animation is added. Page details follow the moving player's lower
edge on the same progress clock. Their opacity and the page backdrop use that
progress (the entrance backdrop uses its square to keep the browsing page visible longer) instead of an independent delayed page pop-in. Source card rounding
tapers toward the square player slot. ZEROCHILL's smaller rail cards require
actual size interpolation, unlike the full-width YouTube thumbnail in the clip.

## Implementation and validation scope

The Shows-only host inherits the existing watch Activity and keeps its existing
PlayerView/TextureView attached. A transparent native window allows the retained
browsing Activity to remain visible through the handoff. Source artwork, live
identity, geometry, and orientation are held only in an ephemeral in-process
handoff. Full source bounds and the visible crop are transformed separately,
including partially clipped rail cards. Poster image matrices counter the player
scale so the image keeps its proportions while its crop changes. No image or
Activity is serialized into navigation extras.

Focused host tests cover geometry and motion state. The existing Shows scroll
and animation-enabled scroll benchmark remain intact. The added
`ShowsWatchMotionBenchmark#openAndReturn` opens a real visible Shows card, returns,
and checks that the same card has the same visible bounds. It measures frame
timing and memory with animations enabled; it does not establish Android 16
physical-device visual parity.

The original head's animation-enabled Shows scroll run failed after its emulator
disappeared (`device 'emulator-5554' not found`); its normal Shows scroll job passed.
That failure is preserved as a failed run, not counted as a successful measurement.
Final build and benchmark outcomes belong in the PR validation record.

No application ID, signing identity, persisted data format, migration, provider,
backend, Supabase, or remote-config change is part of this work.
