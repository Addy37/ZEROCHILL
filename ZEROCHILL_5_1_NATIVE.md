# ZEROCHILL 5.1.0 native beta

Based only on approved native 5.0.1 main `da9b00588e6ecc370327cad75209e757475c2370`.
PR #321 was approved and merged as `36cab2b49e6b34fcb030e8ceb16d33d3ad50f330`.
The preference-motion follow-up starts from that native main commit on
`feature/zerochill-5.1.0`; it requires separate review before merge. The abandoned Compose experiment
and PR #320 are excluded. Existing baseline Compose dependencies are unchanged;
this work adds native Java views only.

## Content preference

One optional string in `app_prefs`, default `all`, shared by Settings and the
OnlyFap header. No login required. Unknown values safely read as All content.
Non-binary is no longer selectable. A saved `non_binary` value reads as All content
through the existing unknown-value fallback, without rewriting unrelated data.
The unused provider mapping stays accepted for remote-document compatibility.
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
| Groups | OnlyHaven creators with content | `group` |

Filtered discovery requests up to 50 creators in the provider's default ordering.
There is no unfiltered fallback in a filtered discovery surface. Creator suggestions
and full creator search follow the selected OnlyHaven provider category; All keeps
the broad existing sources. Favorites, Library, history and a specifically opened
creator's complete merged gallery retain their normal access.

OnlyHaven's public frontend was inspected in CI on 2026-10-21. Its creator request
builder uses `gender` and `content=imported`; its category enum supplies the tokens
above. The bundled configuration includes provider category and category-search
routes. Production config v11 contains all seven supported sources, and the
deployed validator and app-config endpoints accept the same schema. Incomplete
older cached documents fail validation and safely fall back to bundled defaults
without deleting persisted app data.
`tools/verify_onlyhaven_categories.py` audits the current public category contract
and real creator portrait responses for all six categories without modifying the source.
Creator avatars use the configured image CDN's creator service/id endpoint;
`avatarThumbhash` is Base64 placeholder metadata, not an image path.

Confirmed live creator matches, including provider-returned search aliases, are
learned into the existing catalog under canonical profile URLs. Successful queries
share a bounded two-minute result cache by query, preference and config version,
so suggestions can reuse results in full search. Incomplete requests are retried.
Mode changes clear visible suggestions immediately and invalidate old search and
discovery snapshots and callbacks. Category membership comes only from OnlyHaven's
returned category lists, never inferred from names.

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
OnlyHaven catalog IO has a dedicated lock; it never holds the ordinary providers'
page-deck monitor during network requests.

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
symbol with explicit labels; groups uses a local vector icon. Every
mode retains visible text.

## Filtered heroes and preference glass follow-up

Filtered modes reuse the existing eight-slot hero set, dots, gallery action,
13-second rotation, portrait qualification and rejected/alternate artwork path.
The category creator pool is the sole membership authority. Selection is deduplicated
and resolves one creator at a time until eight qualify or the pool is exhausted.
The Discover shelf is intentionally not an exclusion for a filtered category.
Filtered artwork resolves the exact OnlyHaven service/id profile without name-based
substitution, catalog backfill or Fapello discovery. One qualifying creator is valid.
All-content discovery, exclusions, deeper Fapello fallback and memory/history retain
their existing behavior. Filtered heroes do not overwrite All-content hero memory.

Changing modes clears the previous hero set and future candidates, cancels tracked
startup/selection/resolution/discovery work and advances the existing generation.
Late callbacks cannot mutate the new generation, including requested-creator keys.
The generation is checked before deferred-queue insertion as well as execution,
so stale work cannot replace a coalesced task for the current category.
An in-flight provider request may still take its existing timeout to stop.

The compact preference selector sits beside View Gallery below the creator description.
Both controls share the gallery button's dark translucent fill, white border and
48dp height. OnlyFap shows the mode symbol/local icon and dropdown arrow; the
accessibility label and tooltip retain the full mode name. The picker and Settings
keep full labels. All content uses ALL, trans modes share their identity symbol,
and Groups uses its existing local vector. The shared button treatment
replaces the previous mode-specific pill highlights and corners at the user's request.
The picker retains its native liquid glass and short forming entrance, detailed below.
Preference persistence, hero loading and rotation are unchanged.

Focused hero/picker regressions run before full debug/release tests and builds.
The existing native-render harness writes hero/picker PNGs for review. This scoped
follow-up skips the unrelated ten-flow performance matrix; no performance improvement
is claimed. Prior unresolved warm-start/long-session benchmark findings remain open.
Validation results for the actual follow-up commit are recorded in draft PR #321.

## Compact liquid preference panel

The physical-device follow-up replaces the opaque stacked row capsules with one
292dp-maximum panel, capped at 80% of the current screen width. Rows keep a 48dp
minimum target, explicit white labels/symbols, local identity icons and a cyan
selected check. Large fonts/small windows retain scrolling and Cancel/back/outside
dismissal. Preference persistence, hero selection and provider behavior are untouched.

PreferenceGlassPanel is a native ViewGroup, not a Compose picker. It samples the
same ViewPageBackdrop used by navigation and directly reuses the existing rounded
refraction AGSL shader and navigation lens tuning. Android 13+ supplies the lens;
Android 12 uses blur; unsupported/software rendering has a readable dark fallback.
A 3dp blur and translucent wash sit below the native controls, keeping ink sharp.
A screen-coordinate bridge aligns the shared page across the dialog window.

A bounded 320ms forming shape expands from a rounded droplet into the final panel
while the labels fade in. Reduced motion shows the completed panel immediately.
There is no repeating animation. Detach removes source observers, cancels motion
and releases owned RenderNodes. The existing upgrade emulator also runs a short
GPU fixture check on two backgrounds and records the entrance, avoiding a separate
performance matrix or emulator. No quantitative performance claim is made.

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

## Coordinated preference transition follow-up

Mode changes retain the outgoing hero pixels and title/source together as a visual
placeholder while immediately invalidating the old generation and future candidates.
The existing loader's second clear retains the same visual placeholder. Gallery
opening is disabled until the new category image is ready, and a small indicator
beside the selector reports the wait. No previous-category creator is admitted to
the new hero pool. Empty/exhausted categories retire the placeholder with an explicit
empty state and retain retry access.

The selected mode's existing startup path remains available during the wait: a
category thumbnail or All-content cached portrait can replace the old creator without
waiting for the slower gallery qualification pass. Startup portraits remain outside
the qualified rotation pool. Both startup and qualified replacements load through
Glide into a second native ImageView. The outgoing
Glide target retains its resource until the 420ms image fade ends. Old copy fades out
before new copy fades in, avoiding overlapping names. Two reserved copy lines,
reserved indicator/dot space and a stable image host avoid geometry changes. Live
Glide targets are reordered inside that host without detaching them;
then its request is cleared. Generation/candidate/URL checks reject stale readiness
and failure callbacks. The cold-tab readiness gate follows the newly displayed target.
Rotation starts after the fade settles. Rapid changes finish the current fade before
starting another wait; tab departure and close settle/cancel transient work. Reduced
motion commits the complete visual immediately. Existing category/artwork admission,
alternate-image rejection, eight-hero cap and 13-second rotation remain.

Preference confirmations now use a finite 500ms translucent native glass bloom with
the mode symbol and name, without a full-screen scrim. OnlyFap triggers a compact top notice at artwork
readiness, clear of the title and gallery controls. ShitTok uses a compact top pill with explicit
prioritization wording, keeping playback and gestures visible. Existing per-surface
revision acknowledgements remain unchanged. Reduced motion uses a short static
notice; detach and superseding selections remove callbacks and animators.

Native regressions include retained pixels/copy, atomic readiness, stale rapid-switch
results, empty categories, reduced motion, tab departure, compact notices, and finite
cleanup. The existing render harness captures loading, midpoint and settled states.
The signed-upgrade emulator additionally exercises the real production hero with
actual delayed Glide delivery and the production tab queue: startup without waiting
for gallery resolution, rapid category supersession, qualified artwork, no simultaneous
visible names, fixed control bounds and finite completion. It records a device video.
Synthetic local portraits isolate UI behavior from provider/network availability.
No backend, provider route, remote configuration, persistence format or dependency
changes. This follow-up does not claim measured performance improvement.
