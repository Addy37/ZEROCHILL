# BaddieHub creator navigation repair

Baseline: current main `6c35a930a53f5162a017f96b53bfb40369515704`, including merged #369.
The feature starts from this commit. #365 was inspected as a reference only.

## Diagnosis and replacement

Main never gives a BaddieHub card a creator fallback; the metadata loader previously
returned immediately when that fallback was absent. The swipe and title handlers
also reconstructed a name from OnlyFap media fields. Those fields intentionally
remain BaddieHub fields, so changing the title alone cannot enable navigation.

#365 addressed those gates but contained additional reproducible failure paths:
its exact display-name filter discarded source-confirmed aliases when Fapello's
canonical name differed, and swallowed source exceptions became permanent
per-video misses for the loader session. It also selected the richest local match
rather than rejecting ambiguous local identities. Its live scheduler depended on
AppPerformance events and a static active-loader reference. These are code-level
findings; no physical-device log or reproduction is available to prove which one
caused each historical device failure.

The replacement reuses the useful ideas of exact verification, local-first lookup,
background work, holder-bound navigation, and creator-only publication. It does
not reuse commits, the logging-based selection protocol, permanent failure caching,
first/richest ambiguous selection, or hiding source attribution.

The current parser is unchanged. Matching receives its cleaned title, including
legitimate digits; views and durations are not stripped heuristically by the matcher.
Reviewed bundled aliases resolve to canonical names. Catalog normalization collisions
remain unlinked. Live Fapello results require an exact name or an explicit alias in
a matching canonical profile header. OnlyHaven accepts exact names. Partial search
suggestions are never enough. Successful queries are cached for the loader lifetime
and canonical metadata is learned through the existing CreatorCatalog.

Only the selected card can schedule a live request, after local work completes and
a 750 ms dwell. A separate single low-priority worker isolates source latency from
catalog lookup and player preparation. Selection epochs and feed generations reject
stale publication; URL plus cleaned title separates reused cards. Queued live work
is cancelled on selection, backgrounding, search, clear and close. Temporary source
failures have a 10 second retry cooldown and can retry on a later selection/reconcile.
A completed unmatched/ambiguous lookup stays unlinked for that loader session.

The holder stores the resolved identity. Title, avatar and horizontal swipe consume
that identity and route through the existing createCreatorGallery method into the
current #369 mosaic. Creator-only refresh never calls holder.bind, detachLease,
player.prepare or player.setMediaItem. The original media object, playback source,
history/download identities and BaddieHub attribution remain intact.

## Scope and validation

Production changes: BaddieHubCreatorMatcher, BundledCreatorIndex,
ShitTokCreatorMetadataLoader, ChaosFeedView and one FapelloRepository alias-verification
entry point. Tests add deterministic matching/scheduler/real-holder coverage and
update three existing gesture/handoff fixtures to bind identity as production does.
CI scopes this branch to the focused creator gate, one complete debug/release suite
per workflow, and the ShitTok scroll plus creator-gallery performance flows.
No gallery implementation, data format, application ID, signing, backend, Supabase
or remote source configuration changes are made.

Focused tests exercise parser metadata separation (587, 153K, duration), the two
reported handles, exact normalization, reviewed alias collisions, canonical alias
caching, network retry, stale/cleared selections, creator title/avatar/completed
horizontal swipe, player retention, ordinary creator swipe, and #369 gallery gates.
Use CI and the completion report for actual full-suite/build/signing outcomes.

Physical-device playback, frame timing and live source availability still require
validation. The managed workspace proxy denies fapello.com (CONNECT 403), so the
live examples cannot be claimed as verified here. The failure is environmental;
no source availability switch or remote configuration was changed.
