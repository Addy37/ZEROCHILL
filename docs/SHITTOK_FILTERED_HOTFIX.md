# Filtered ShitTok hotfix candidate

Baseline: GitHub `main` verified at `1fa925f4806d21a1cfefc6d98158ca2cb7f14a51`.
Branch: `hotfix/5.6.1-filtered-shittok`. No stable version bump, publication or merge.
Physical approval on the OnePlus CPH2655 / Android 16 / 120 Hz is still required.

## Physical-test rescue pass and OnlyFap source mode

The rescue pass starts from verified PR #376 head
`89f2563c1457f462858bdcdd4448cd457c053a5b`, continuing the existing branch.
It does not change SmartRanker learning, player ownership, provider configuration,
release metadata, signing, account data or backend state.

Code-traced bottlenecks after the first device tests:

- A filtered refill consumed one raw snapshot. Healthy arrivals during its six-second
  admission window could not join it. A continuing filtered producer now drains fresh
  eligible source results during that same bounded window and replenishes consumed
  source slots. The existing six source workers and three admission workers remain.
- Bunkr could occupy two verification lanes while failing probes. Creator-filtered
  scheduling now permits at most one Bunkr lane and two queued Bunkr candidates.
  Three resolve/probe exceptions trigger 5/10/20/40/60-second source backoff, with later
  retries and a successful admission resetting the penalty. All providers have bounded
  measured cost/yield state; portrait metadata is preferred within the existing rank
  and opportunity rules. Supplemental raw inventory stays in its source pool while
  admission is backed off. Ordinary All + All Content keeps its prior scheduling.
- Two remaining four-dry-refill checks still delayed filtered continuation. They now
  apply only to the broad feed. Targets stay 6/10; filtered resume stays 5/8.
- Creator enrichment was already asynchronous on its own low-priority worker; the
  reported durations do not establish main-thread blocking. Its lookup key was
  URL/title per clip. The cache now shares work by source + exact profile + canonical
  creator (or the existing exact identity), with 256 identities and 512 learned-profile
  keys. Fallback labels/navigation are immediate; enrichment never gates admission.
  The exact Fapello profile URL remains internal and its clean label stays visible.

ShitShow's protected or still-running harvest could return an empty list, which the
source pool interpreted as an exhausted catalog, escalating its retry delay. Those
states now return an explicit deferred result with a one-second scheduling retry,
without increasing catalog-failure backoff. The harvester resolves an Activity through
`ShitShowPlayableResolver`'s existing weak host. The backend retains Application context,
never the first Activity. Protection still stops the hidden WebView, and stop/generation
checks still reject stale JS callbacks. Women requests the real harvester directly
when the broad cache is empty. The regression verifies the actual WebView request to
`/shitshow/`, delivers a JS bridge response, and checks Women receives eligible inventory.
It also tests protected deferral and recovery. This is a controlled request/result test,
not proof of provider reachability or the precise cause on the physical device.

A stationary 172x44 dp `All | OnlyFap` capsule uses the existing navigation glass
material and pager-only backdrop. Selected text is cyan, other text cool gray;
selection uses existing motion settings. Status-bar insets are respected. Pinch,
Focus Mode, clear display and manual fullscreen hide it; normal ShitTok restores it.
The optional `app_prefs.shittok_source_mode` key defaults existing users to `all`.
No account or settings migration occurs.

| Source mode / gender | Eligible sources |
| --- | --- |
| All / All Content | Existing approved broad mixture, including CrazyShit and Kaotic; existing eFukt/XMegaDrive exclusions unchanged |
| All / Women | Verified OnlyHaven, strongly matched Fapello/Bunkr, direct ShitShow |
| All / other exact category | Verified OnlyHaven and strongly matched Fapello/Bunkr; no ShitShow |
| OnlyFap / All Content | BaddieHub, ShitShow, known OnlyHaven creators, strongly matched Fapello/Bunkr for those creators; no gender classification |
| OnlyFap / Women | Verified OnlyHaven, matched Fapello/Bunkr, direct ShitShow, BaddieHub with an existing resolved creator identity uniquely linked to a verified Women creator |
| OnlyFap / other exact category | Verified OnlyHaven, matched Fapello/Bunkr, BaddieHub with that same strong identity plus exact category evidence; no ShitShow |

OnlyFap excludes CrazyShit, Kaotic, eFukt and XMegaDrive. BaddieHub titles do not
classify a category. The existing conservative identity resolver must first produce
a creator; only a unique strong link to an already verified OnlyHaven creator can
transfer membership. Ambiguous matches are rejected. Known linked BaddieHub clips
carry the exact profile and canonical name for navigation and cross-provider diversity.
OnlyFap All Content creator pagination reuses the configured OnlyHaven creator-search
route and its existing offset parameter; it does not invent a category.

One existing cancellation/publication path covers both dimensions. The combined
selection invalidates acquisition/admission; the current lease survives and every
future candidate/publication rechecks eligibility. Warm ready clips and raw inventory
are keyed by source mode x gender. At most eleven creator inventories plus twelve
10-clip warm slots exist, with the existing ten-minute ready-stream TTL and per-session
creator/cursor/evidence/raw caps below. No persistent media catalog is added.
The candidate queue and orientation reservoir remain capped at 48 each; there are
at most three current pending admissions. Per-source candidate admission is capped
at 12, with Bunkr capped at two. A full runway still closes new work.

Reports retain session totals and add current selection, a combined preference epoch,
switch age, current tail hits, below-target time, minimum runway, refill/dry counts,
per-source accepted/probe-error/orientation outcomes, ShitShow request/raw/protected/
harvesting/no-host/failure/empty results, Bunkr backoff time/lane cap, metadata hits/
misses and the existing live queue sizes. Old-epoch completions cannot enter current
outcomes. Reports stay bounded and redact content URLs. Signed candidate diagnostics
use the existing CI opt-in; production diagnostic activation is unchanged.

Physical acceptance is still pending. Provider availability, sparse portrait inventory,
expiring URLs and conservative identity matching remain limitations. Neither unit
fixtures nor emulator benchmarks establish device runway health. Actual validation,
workflow outcomes and the signed artifact are recorded on PR #376.

The original hotfix implementation record follows; the rescue changes above supersede
its per-gender-only keys and snapshot admission scheduling.

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
