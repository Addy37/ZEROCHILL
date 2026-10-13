# ShitTok PlayerPool and creator updates candidate

Approved ZEROCHILL base: `136d235b9a82c5a449d9983ed8c1e11506cb2c32`.
Incorporates unmerged PR #289 at `5279f41f61223dbee60b481abc575cdaa8b18785`.
Target: `rebrand/zerochill`. Keep draft and unmerged pending device approval.
KidTok reference: verified main `810f60f170f460e131d4e7cd330019d8ddf86b70`.

## Playback ownership

`ShitTokPlaybackOwner` owns the official Media3 1.11.1 `PlayerPool<ExoPlayer>`.
Capacity is three, independent of RecyclerView's retained holders. Priority is
current N, next N+1, previous N-1. Minimal preload settings acquire current only.
A shared DefaultPreloadManager builder gives engines a shared playback looper
and load control. Holders attach/detach surfaces and UI listeners. They never
construct or release an engine. The previous detached-player release queue,
release-maintenance loop, and per-holder ExoPlayer builders were removed.

Settled page IDs determine the window. Scroll-state-only changes update gates,
without redoing the selection plan. Matching leases keep their source, decoder
and buffers when promoted. Out-of-window leases save position, detach listeners,
stop/clear media and yield before replacement acquisition. The engines remain in
the pool until lifecycle release. URL renewal preserves current position.

Audio is revoked before page/source/surface ownership changes and during drag,
settle, hidden/background states. Only the selected attached surface can play.
Manual pause belongs to the item, survives canceled swipes and lifecycle return.
Holder UI callbacks check player, item URL, lease token, canonical holder and
surface ownership. Resolver callbacks capture feed generation and lease token;
stale work may populate a URL cache but cannot mutate a reused lease.

Current playback keeps normal buffers. Silent leases load with 2.5-second low /
6-second high targets that follow their current role on promotion. Current
buffering delays neighbor preparation and suppresses neighbor loading. N+/-2
get lightweight resolved links only, no additional players/decoders or full-file
downloads. The existing 200 MiB disk cache and source-specific User-Agent,
Referer, Origin and cookies remain.

Actual decoder initialization failures reduce the prepared limit from three to
two to one, retiring less urgent silent leases and retrying the retained current
source. The last acquired player cannot be retired. The same error delivered to
two listeners reduces the limit once. Recovery during paging waits for idle.
Other failures retain the existing bounded URL refresh/skip behavior.

No quick-return grace is added. Pool release happens when ShitTok is hidden or
its host pauses, including creator-gallery entry. Positions and manual pause
remain available for return. This keeps background resource ownership simple.

## Swipe/UI work

Background refill results queue until pager idle, including adapter insertion.
Refresh, blocking and Not Interested list mutations share the idle gate. Creator
identity/favorite metadata is cached while batches apply. Async social counter
results wait for idle. The pager retains immediate neighbor views; distant links
do not require full holders. Automatic creator-gallery warming stays disabled.
Explicit creator interactions keep the existing cache-first gallery path.

Candidate/debug diagnostics use bounded events and visible-ShitTok Window
FrameMetrics: pool/lease counts, source start/end, stale result rejection,
prepare/READY/first frame, handoff, cache reads, adapter/refill/bind timing,
decoder reduction, current/neighbor buffer snapshots and audible count. Production
release builds leave the candidate diagnostic flag off. Report problem includes
the diagnostic snapshot. Macrobenchmarks retain actual frame/memory metrics and
add separate pool/bind/handoff/refill traces, beyond page-selection callbacks.

## KidTok principles

Adopted: centralized official pool, bounded bidirectional leases, promotion,
yield-before-acquire, shared looper/allocator, role-based silent buffers,
current priority, generation checks, decoder backoff, idle UI gates and metrics.

Not copied: Compose SlidingWindowEffect/rememberPooledPlayer, YouTube quality /
adaptive extraction, downloads, Home preview/warmup, and quick-return retention.
ZEROCHILL has a Java ViewPager2/RecyclerView UI and its own source routing/cache.
A small Kotlin owner uses Media3's coroutine acquisition API without rewriting
that UI. The existing Kotlin plugin moves to 2.2.10 to match common-ktx metadata.
Far-neighbor compressed-sample preloading is deliberately omitted; resolved links
provide the lighter window while current and two silent decoders share resources.

## Creator notifications

Library updates show one collapsed creator group with newest preview and remaining
unread count. Expanding changes UI state only. Children carry exact media,
thumbnail, source-qualified stable ID and individual read state. Tapping a child
marks only that physical post read and opens a one-item viewer snapshot. Resolution,
image/player failure or a missing restored snapshot falls back to the current
merged creator gallery focused on that item. Favorite Creator badges count
remaining unread children; merely opening a gallery no longer consumes updates.

Grouping follows current merged aliases and chosen primary name/avatar. Stable
Fapello post identities match route and CDN variants, without treating similar
numeric IDs on different sources as equal. Android alerts remain summaries.

Persistence adds children to existing JSON, retains old history and settings,
and reconstructs missing legacy child rows from saved URLs. Earlier versions did
not save every thumbnail, so those cannot be recovered retroactively. Per-profile
polling uses existing repositories/config, four aliases per check and at most 40
first-page media per alias. Large favorite lists rotate through later checks. The
first successful scan establishes a baseline instead of marking an entire old
gallery new. Existing unread/history entries remain readable. No backend changes.

## Validation and remaining physical test

Focused local suite: 141 tests passed before the full suite. Tests use offline
bundled Robolectric runtimes and decoder-free engines with the real Media3 pool.
Live source work is disabled in playback fixtures. Compilation is not physical
playback validation. Final full-suite/build/signing/benchmark results belong in
the draft PR and delivery report.

On OnePlus 13, test fast forward/reverse/A-B-A swipes, canceled swipe after manual
pause, auto-scroll, tab/background/gallery return, and fullscreen entry/exit.
Listen for wrong/overlapping audio and check buffered reverse promotion. Then
expand creator cards, open one child, return, and verify the count and badges
shrink by one while other children stay unread. Check a merged creator and a
missing-media fallback. Physical hitch elimination is not claimed.
