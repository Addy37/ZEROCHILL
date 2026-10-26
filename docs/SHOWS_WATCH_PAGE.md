# Shows watch page

## Source and scope

Development starts from verified `main` at
`07287dbce4611b778ab19f65607b010861ec3918`. The root lineage document identifies
`main` as current, and `rebrand/zerochill` is an ancestor of this commit.
The feature branch is `feature/shows-watch-page`. No merge or publication is authorized.

The read-only KidTok reference is its default `main` at
`999e3eac7caf26c869df9136f2d39b09935928f8` (merged PR #59), rather than an older
candidate described in its historical lineage entries. Inspected reference files:

- `HomeWatchScreen.kt`, `HomeWatchModel.kt`, `HomeWatchLayout.kt`
- `HOME_WATCH_LANDSCAPE.md`, `PORTRAIT_WATCH_AND_HOME_PERFORMANCE.md`
- Root `AGENTS.md` and `KIDTOK_BUILD_LINEAGE.md`

KidTok is the visual and interaction reference. Its current completion UI uses
manual next selection. ZEROCHILL deliberately adds the requested five-second
countdown and Cancel action rather than adopting that manual-only behavior.

## Navigation and playback

The existing `VideoDetailActivity` remains the watch destination. Shows shelf,
collection/series, Shows search, and non-OnlyFap Library/history entries attach
bounded ephemeral watch context. The original browsing Activity remains underneath
the watch Activity. Related selection changes media within the watch page rather
than creating another Activity or an internal Back history.

The old delay was traced to multiple fullscreen/related-history Back steps, not
an unconditional provider reload on return. The browser retains its adapter and
scroll position; Shows hub resume refreshes playback badges/Continue Watching.
The watch flow exits directly and avoids related-history frame capture and repeat
exit-time history saves. This is a code-path diagnosis, not a measured Android 16
frame-time claim.

Rotation keeps the player surface and engine. Media selection reuses the Shows
engine with a per-item MediaSource, retaining each resolved source's request
headers. Existing source repositories and resolution remain the source of media.
History, playback position, mute/volume, speed, pause intent, and completion state
continue using the existing local stores and player APIs.

## Presentation

Portrait follows KidTok's top player and separately scrolling metadata/Up Next
page. Related items use large 16:9 images with text below, not compact horizontal
rows. ZEROCHILL's existing social/action controls remain available.

Landscape follows KidTok's fitted primary video and horizontally scrollable
thumbnail rail. Reference geometry comes from `HomeWatchLayout`, whose base
canvas is 1536 by 698, with 304 by 171 cards, 15-unit gaps, and the rail starting
at 499 units. ZEROCHILL retains its own icons, black background, cyan accents,
white text, and cool-gray metadata.

At completion, a valid next item appears in the player area with thumbnail,
title, countdown, immediate selection, and Cancel. Series context prefers an
identifiable following episode, with source display order as the fallback where
episode numbering is unavailable. Missing next content produces a normal ended
state. Backgrounding, Back, and manual selection stop pending automatic selection.

## Compatibility

No application ID, signing setup, release version, provider scraping, remote
configuration, backend, schema, or persisted user-data format changes are part of
this feature. New watch-context and recreation state are additive and ephemeral.
ShitTok and OnlyFap retain their existing player/navigation paths.

## Validation

Validation results and remaining device limitations are recorded in the PR.
Robolectric checks exercise host-side navigation, geometry, media-switch dispatch,
countdown, restoration, and existing persistence behavior. They do not establish
hardware decoding, audible overlap, Android 16 gesture performance, or 120 Hz
frame timing. Physical-device acceptance remains required for those observations.
