# ZEROCHILL 5.1.0 native beta

Based only on approved native 5.0.1 main `da9b00588e6ecc370327cad75209e757475c2370`.
Branch: `feature/zerochill-5.1.0`. Draft PR: #321. The abandoned Compose experiment
and PR #320 are excluded. Existing baseline Compose dependencies are unchanged;
this work adds native Java views only.

## Content preference

One optional string in `app_prefs`, default `all`, shared by Settings and the
OnlyFap header. No login required. Unknown values safely read as All content.
Existing backup whitelists/formats are unchanged; this new device preference is
not added to backup exports. Favorites, history, downloads, blocks, creator groups,
accounts and notification settings keep their existing persistence formats.

| Selection | OnlyFap discovery | OnlyHaven category |
|---|---|---|
| All content | Existing Fapello Discover/New/Hot/Popular and existing hero loaders | Unchanged |
| Women | OnlyHaven creators with content | `woman` |
| Men | OnlyHaven creators with content | `man` |
| Trans women | OnlyHaven creators with content | `trans_woman` |
| Trans men | OnlyHaven creators with content | `trans_man` |
| Non-binary | OnlyHaven creators with content | `nonbinary` |
| Groups | OnlyHaven creators with content | `group` |

Filtered discovery requests up to 50 creators in the provider's default ordering.
There is no unfiltered fallback in a filtered discovery surface. Search, favorites,
Library and manually opened creator galleries retain their normal access.

OnlyHaven's public frontend was inspected in CI on 2026-10-21. Its creator request
builder uses `gender` and `content=imported`; its category enum supplies the tokens
above. The bundled configuration adds optional `creatorCategoryApi` and
`categoryValues` data. Older valid remote documents retain the same compiled
fallback contract. No live configuration is published and no backend is changed.
The live endpoint returned config v10 without the expanded source objects; the
existing Android validation/fallback behavior remains unchanged.
`tools/verify_onlyhaven_categories.py` audits the current public category contract
and real creator portrait responses for all six categories without modifying the source.
Creator avatars use the configured image CDN's creator service/id endpoint;
`avatarThumbhash` is Base64 placeholder metadata, not an image path.

ShitTok All content uses the existing pool and ordering. In other modes, the
OnlyHaven uses the selected category on one process-wide preference worker after
the first playback frame. A two-candidate buffer survives completion/cancellation
of a faster ordinary harvest. Late results get a bounded admission turn even if
the ordinary runway is full or an orientation reservoir remains. Normal providers
retain their existing harvest path; All content never starts this preference worker. Once clips pass the existing block,
seen/liked, identity, playable-media and portrait checks, the future tail orders
one matching clip followed by two ordinary clips while both are available.
No other provider gets an inferred gender label. Scarcity does not remove content
or stall playback. Category catalog failures have a one-minute retry backoff;
empty preference batches back off for 30 seconds. Navigation/pause/close cancels
preference work, stale completions cannot cross mode changes, and successful raw
candidates can be retained for resume. Every candidate still passes normal admission.

Changing the preference cancels the discovery batch and discards the future queue
after the current and immediately prepared next clip. It keeps playback generation,
player callbacks, played history and blocked-creator data. Unplayed discarded
entries can pass through normal deduplication/admission again. Preference ordering
also leaves those first two clips intact.

Each actual selection increments a local change revision. OnlyFap and ShitTok
acknowledge that revision separately before displaying an approximately 1.26-second
fade/scale overlay. Initial installs, unchanged selections and recreation do not
replay it. OnlyFap presents over its refreshed category surface; ShitTok's copy
explicitly says matching content is prioritized. Playback is not paused. All uses
an ALL treatment; women/men use the standard symbols; trans modes use the trans
symbol with explicit labels; non-binary and groups use local vector icons. Every
mode retains visible text.

## Favorite artwork repair

Favorite Creators keeps its grid, Find Creators and Change avatar flows. Its new
header menu starts Fetch missing artwork. `FavoriteCreatorArtworkHydrator` scans
the full favorite-group snapshot, skips usable remote artwork and custom overrides,
deduplicates equivalent/reviewed identities, and uses the existing confirmed
creator search. Only exact normalized or already reviewed identity matches qualify.
Successful metadata goes through `CreatorCatalog.remember`, including relevant raw
favorite identities. No favorite key or custom override is rewritten.

One search runs at a time on one process-wide worker, including across Activity recreation. Each saved result refreshes the visible
grid. The dialog reports completed/total counts and allows cancellation. Leaving
the Activity also cancels future work, including on recreation; an in-flight network
request can take its existing timeout to return, but a separate run token prevents
its stale UI callbacks from changing a later operation. Saved successes survive
cancellation/failure. Retry is manual and skips previously restored artwork.
There is no launch-triggered artwork hydration.

## Settings switches

Settings alone opts into 44dp-wide, 24dp-high tracks with 18dp near-white thumbs,
the shared electric cyan on color and dark off color. The native Switch keeps its
48dp minimum touch target, checked-change listener, row click and accessibility
behavior. Other screens retain existing switch resources.

## Build and validation

Development defaults identify 5.1.0-beta.1, version code 5001000. The signed upgrade
workflow uses this beta version on this branch, verifies stable release upgrade
compatibility, and uploads the signed debug Beta as a CI artifact with source branch
and SHA in the filename. Stable application/signing identifiers are unchanged;
Beta retains the existing `.dev` application ID and installs beside stable.
`BETA_VERSION` and the legacy automatic publication workflow remain unchanged.
No production version, release, tag or merge is authorized.

CI runs focused regression tests before the full debug/release unit suites and APK
builds, lint as an audit, the existing performance workflow, and signed/upgrade checks.
The PR/check results are the authoritative validation record. Physical device testing
is still required before any product approval. Source availability and category
coverage depend on OnlyHaven; no fallback fabricates category metadata.
