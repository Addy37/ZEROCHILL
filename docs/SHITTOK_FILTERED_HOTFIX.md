# Filtered ShitTok hotfix candidate

Baseline: GitHub `main` verified at `1fa925f4806d21a1cfefc6d98158ca2cb7f14a51`.
Branch: `hotfix/5.6.1-filtered-shittok`. No stable version bump, publication or merge.
Physical approval on the OnePlus CPH2655 / Android 16 / 120 Hz is still required.

## Confirmed code causes

The 5.6.0 filtered path used one OnlyHaven source slot, admitted two raw clips per
acquisition, fetched the same first creator page and creator media page repeatedly,
and cleared that slot whenever the preference changed. Its normal/rapid refill
thresholds were four/six despite the narrower inventory. Four dry refills could
also prevent an otherwise ready filtered batch from reaching admission. Existing
source diversity could not distinguish different sources containing the same person.
These are code findings, not claims about measured physical-device latency.

## Authority and media

`ShitTokFilteredInventory` owns five independent session inventories and reuses the
existing bounded `ShitTokSourcePool` acquisition/backoff/single-flight machinery.
OnlyHaven category responses create creator cursors and persist membership through
`CreatorCategoryIndex`. Cold sessions can seed up to 12 canonical OnlyHaven profiles
from existing `CreatorCatalog` metadata only when that exact profile has persisted
membership in the selected category. This scan runs on the source worker.

Fapello searches the verified creator name using the existing repository. It accepts
one unique canonical model URL whose entire normalized name equals the verified
name after `CreatorIdentity` reviewed aliases. Multiple distinct exact profile URLs
are ambiguous and rejected. Prefixes, substrings, edit distance and compacted token
boundaries do not qualify. Search is performed once per cursor; media pagination
reuses the matched model and honors its existing `hasNext` response.

Bunkr uses the existing album discovery, `fetchAlbum` and `PlayableSourceRouter`
resolution. One unique album URL must have an entire gallery identity equal under
the same normalized/reviewed rules. Compilations, mentions, partial names and multiple
exact gallery URLs are rejected. Album identity only links media to an already
verified creator; it never establishes a category. The broader creator gallery
aggregator's permissive search results are not treated as category evidence.
Bunkr clips retain the original media URL and carry the OnlyHaven profile/name for
existing blocking and creator navigation. No Bunkr network configuration changes.

ShitShow's existing Activity-backed harvester and playable resolver contribute
directly only in Women. Women first drains up to 12 already-buffered ShitShow
metadata entries from the broad source cache, avoiding stranding valid unplayed
clips when the active mode changes; no other broad source buffer is opened.
Returning to All hands unused raw Women ShitShow metadata back within the same
12-entry source cap, retaining its original All Content acquisition behavior. The other four filtered inventories never request ShitShow.
Only URLs actually harvested into that category session receive evidence. Labels,
media titles, descriptions and thumbnails cannot grant eligibility.

All raw candidates pass the existing liked/history/block/duplicate/playable/portrait
admission gates. Worker and final UI publication recheck preference eligibility;
remote source availability is also rechecked when using evidence. There is no
unverified fallback and no new All Content provider.

## Bounds, pagination and cancellation

Each category retains at most 48 creator records, 48 cursors per creator source,
36 overflow items per source, 12 raw items per source slot, and 4096 media evidence
entries. OnlyHaven returns a small batch spanning up to three creators, interleaved
before ranking. Cross-source work remains one acquisition per source; all category
work shares the existing six-thread, bounded-queue source executor. The admission
executor remains three lanes. Source requests do not run in swipe dispatch.

The category offset advances by 12 after each successful category page. A category
request occurs only when fresh Haven creator work runs out and runway demand is
open; pagination stops at the provider's `hasMore=false`. No entire catalog fetch.
Offsets, profile matches, media page cursors, overflow and source buffers survive
switches. Repeated identical media pages terminate their cursor. A successful empty
page with more cursor work resumes on the normal recovery tick; real request
failures retain source backoff and bounded cursor retries. Category-page failures
back off independently while previously verified creator media cursors remain usable.

Switches pause/invalidate source and admission generations without sharing pools
between categories. Old socket completions cannot publish into the new source
pool. The existing pager guard, current lease exception and future-queue pruning
remain. Already admitted future clips and resolved streams have an additional
10-item cache per filtered category with a ten-minute session TTL. Returning
restores eligible clips through `appendUnique` synchronously, rechecking exclusions.
No players are cached and no persistent media database is added.

## Sequencing and runway

Only filtered future queues are resequenced. Canonical/reviewed creator identity
counts across sources, including Bunkr. A different ready creator takes precedence
over a back-to-back repeat; creators in the last five positions receive a bounded
four-point penalty on top of the existing SmartRanker score. Single-creator tails
remain usable. Targeted future-item moves preserve the current item/player, refresh
holder positions, and let the existing playback window reconcile neighbor leases.
All Content ordering and its four/six resume thresholds stay unchanged.

Filtered targets remain six normally and ten rapidly; refill resumes at five/eight.
Discovery and admission use the existing demand gate. Full runway pauses new work
while bounded in-flight work can finish. The filtered path no longer stops admitting
ready inventory merely because four earlier refills were dry.

Existing bounded provider diagnostics add the active filtered mode, creator count,
offset/hasMore, evidence size, accepted raw count, and per-source health/ready/
verified/contribution counts. No media URLs, cookies, tokens or creator names are
added to these aggregate fields.

## Compatibility and validation

No dependency, database/settings format, application ID, signing identity, backend,
Supabase, source configuration, resolver header, timeout, route, selector or CDN
change. Release metadata remains 5.6.0. Existing CI builds preview candidates using
runtime version overrides and the production signing bundle; the signed Beta stays
side-by-side under `.dev`, and the release-package candidate exercises in-place
upgrade from the latest stable. Neither workflow publishes a stable release.

The candidate workflow runs focused eligibility/source/playback tests first, then
full debug and release suites and both APK builds. Existing scroll/long-session
performance and paired short-scroll workflows are enabled for this branch, against
the verified 5.6.0 baseline. Actual command/run outcomes belong in the PR and final
report. Emulator results cannot establish physical-device performance.

Conservative identity rules intentionally leave unmatched/ambiguous catalogs out.
Provider challenges, sparse video/portrait inventory, source availability and the
provider's attribution accuracy remain external limitations. Bounded eviction can
require rediscovery in a later app session; no large catalog is retained on disk.
