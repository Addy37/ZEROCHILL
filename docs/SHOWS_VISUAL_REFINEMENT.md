# Shows visual refinement

## Baseline and scope

The user selected the newer OG lineage after inspecting the requested historical
rebrand branch. This feature starts at current main `52d9c212` (a descendant of
local starting checkout `5f24b294`) and targets main. All merged work in that
baseline, including ShitTok PR415, remains intact. V2 main was verified at
`9ce121f9`. The earlier rebrand-based implementation remains a checkpoint only;
its player host, pagination implementation and preference adapter are not copied.

The existing ShowDetailsHeader and its artwork, title, gradient, metadata,
Play/Resume action and behavior remain unchanged. OG's existing source/page
requests, ten-episode ranges, range picker/search, prefetch/retry behavior,
full watch-session snapshot, saved range and scroll restoration are retained.
The player UI, controls, engine, orientation, gestures and history remain OG.

## Presentation

- Homepage uses V2 proportions, compact two-row rails, 160dp Continue Watching
  cards, selectable accent-pill dots and six-second rotation. Hero corners stay
  square. Artwork reveals circularly after loading; the prior image stays visible
  during the load. Bounded snapshots and image caches are reused. Full remote
  hero artwork has bundled fallback. Existing category mosaics, provider/order
  selection, frame caching and source-view callbacks remain in place.
- Episodes use compact horizontal rows, rounded dark 18dp backgrounds, fixed
  132x80dp thumbnail slots (narrower only in small windows), 13dp image corners,
  accent EPISODE 01 labels and three-line titles. Cards grow for larger text while
  thumbnails retain their dimensions. Missing images keep the same slot. Entire
  rows remain tappable, and long-press keeps the existing episode actions.
- Numbering uses the existing absolute range offset. The original source title
  is not edited. Paging mechanics and cached-page request behavior are unchanged.
- Inline pagination controls use the saved accent and restrained glass-like
  surfaces derived from the existing transparency/edge/off preferences. No
  backdrop recording is added to the scrolling list. OLED stays black. All
  styling uses the existing AppearanceTheme and writes no new preferences.

## Motion

OG already has a thumbnail-origin player opening and a retained browsing screen.
The existing host now uses V2's 320ms opening and 280ms reverse timing/easing.
A 70ms captured-poster bridge precedes contraction when video is already visible.
Back's existing cleanup, request invalidation and saved-on-exit guard run before
that visual return. The baseline's immediate Shows close is intentionally changed
to the requested reverse transition. Missing/recycled/scrolled/changed origins
use a short fade; reduced motion skips decoration. Existing configuration-change
cleanup handles interrupted returns. API26 retains OG's opaque compatibility
entry; API27/28 use public matrix operations instead of an API29-only View helper.
No player gestures, decoder setup, media resolution or playback queue changes.

## Validation and limitations

Run the Shows, responsive layout and existing playback/page/range tests first,
then the debug/release unit suites and APK builds. Lint remains the repository's
audit. Actual results belong in the PR and completion report.

No device or emulator was attached; `/dev/kvm` is absent. Host tests and builds
cannot establish physical motion parity, GPU frame pacing, source playback or
OnePlus rotation behavior. Captured player artwork is bounded to 512px, so brief
scaling softness is possible. API26 and unavailable origins use the fallback.

No application ID, signing configuration, persisted data schema, favorites,
downloads, backups, source/provider/parser, backend, Supabase or remote-config
changes. No dependency or migration. Local development APKs use the existing
`.dev` package and development signing and are not production-signed upgrades.

Device acceptance: compare homepage against V2 and verify sharp hero edges;
exercise dots, rotation, every rail and cached artwork; keep OG episode hero;
check pages 1–10, 11–20 and partial tail with missing/portrait art; open/close
from hero, episodes, This Week and Continue Watching, including partial cards;
check Back during entry, changed origins, scroll restore, playback cleanup,
portrait/wide video, fullscreen, rotation lock, accents, glass off, reduced motion
and background/recreation. Physical approval remains pending.
