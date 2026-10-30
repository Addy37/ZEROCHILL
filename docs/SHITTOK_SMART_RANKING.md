# Smart ShitTok v2 candidate

Starting point: approved `main` `dd0fc91fdb7be88587685fd4ea3a618998b8bdea` (PR #370).
PR #371 was inspected as a failed-attempt reference; it is neither merged nor a dependency.
Physical-device approval is required before merge.

## Failure diagnosis

XMegaDrive listings use a browser-backed fetch with an existing 45-second timeout,
and playable-page resolution uses that browser too. The previous mixer cancelled
unfinished listings after 5.5 seconds; incremental inventory admission could cancel
the entire harvest earlier when faster providers filled the runway. Verification
also cancelled unfinished resolution/probes at the end of its six-second window.
Consequently a healthy but slower XMegaDrive could be repeatedly interrupted at
both stages. Exceptions became empty lists, hiding the distinction from diagnostics.

ShitShow has a separate hidden-WebView harvester and cache. It is deliberately
stopped during playback starvation protection. Only a later ordinary mixed batch
could restart and drain it; no session recovery loop guaranteed that opportunity.
Fast-provider runway completion, queued ordinary leftovers, and cancelled acquisition
could keep it out of future admission. PR #371 only restarted the WebView when
protection cleared; it did not repair source acquisition or verification ownership.
These are code-traced causes, not a claim to have reproduced current live provider
responses or the physical device's complete failure history.

## Pipeline and health

Existing starter race -> session source acquisition -> category eligibility ->
blocks/seen/liked/identity checks -> bounded asynchronous playable/orientation
verification -> local score + source opportunity + exploration -> ready runway.

`ShitTokSourcePool` owns at most one acquisition per source and 12 raw candidates
per source (eight providers, at most 96 raw entries). A refill ending never cancels
these requests. Completed slow sources remain available for a later refill. Existing
provider request timeouts remain unchanged. Multi-request source loaders preserve
partial success and distinguish total failure from a legitimate empty response;
empty catalogs can retry rather than becoming permanent session exclusions. Session health distinguishes idle,
fetching, slow (>5.5 seconds), ready, empty, failed, config-unavailable, user-filtered,
and ready-but-starved sources. Diagnostics expose health, buffered counts, successful playable verification and
actual queue contributions separately; raw arrival is not treated as playback success.

Failure backoff starts at five seconds; legitimate empty backoff starts at 15 seconds.
Both double to a two-minute ceiling. Cancellation is not failure. No source is declared
dead. While the feed is active, a two-second main-loop tick only schedules bounded
work: recovery acquisition requires existing playback permission; filtered acquisition
can start without a first frame. Buffered sources do not refetch until consumed.
Paused/hidden/closed/search-covered sessions do not start recovery. Existing
reservoirs reuse their candidates without launching another provider race. Lifecycle/mode
cancellation invalidates source generations and stops the ShitShow WebView.
Healthy raw metadata caches survive pauses and filtered modes without becoming
eligible there. Mode changes clear the OnlyHaven category buffer; returning to
All re-enables broad caches after rechecking remote availability. This avoids
losing ShitShow metadata already consumed by its internal harvest deduplication.
Abandoned unplayed ShitShow recommendations release that metadata dedup key; normal
seen/history exclusions still prevent replaying actually watched clips. A socket
that ignores interruption remains single-flight until it really exits.

Raw selection combines source opportunity age (capped at eight turns) with a bounded
personal/community score (-2..2). Every fourth choice samples the source's original
candidate order. There are no permanent source percentages. Only available candidates
participate; admission still rejects invalid, blocked, repeated or landscape media.
Previously buffered recovered sources are offered before leftover ordinary candidates.

Verification keeps at most three pending results/tasks across ordinary refills. A slow
source can occupy at most two lanes when other sources are available; a single-source
pool can use all three. Lifecycle/mode changes cancel and invalidate pending work.
Admission also uses bounded score plus source opportunity, retaining trusted metadata
as a small speed preference rather than an absolute priority. Completed slow results
are collected by future refills rather than re-resolved from scratch.

## Local learning

The existing optional `shittok_smart_ranker_v1` file and skip keys are preserved.
Two optional v2 reward keys add source/creator taste; there is no database migration.
Source tables are capped at 64 rows, creator tables at 384, feature memory at 4096,
and session action deduplication at 512. Creator reward keys are hashes of the
existing source/creator identity; categories, thumbnails and inferred demographics
are never stored as taste features. The profile is device-local and shared across
accounts on that device, matching the existing local skip-profile behavior.

Only selected-player `isPlaying` intervals count toward watch time. Buffering,
paused/background time, stream failure and no-playback selections do not teach skips.
A fast skip must also leave promptly in elapsed time, so a buffering wait is not
misclassified as disinterest.
On leaving a successfully played clip, <=2.5 seconds produces the existing negative
source/creator signal; >=8 seconds produces a 1.2 reward. A completion with actual
watching >=min(8 seconds, 80% of duration), at least one second, earns 1.8. A measured
replay earns another 0.3 once. Saves earn 2, creator favorites 2.5, confirmed likes
0.5 and creator gallery opens 0.25. Duplicate callbacks/toggles cannot repeatedly
reward the same action in the bounded session memory. Viewing thus outweighs light taps.
All signals have a seven-day half-life and fixed caps. The positive score caps at four;
negative score caps at six. Constants are centralized in the ranker for tuning.

Reads/scoring use memory. Existing lifecycle/seen-store flushes serialize snapshots
on one disk worker with at most one pending snapshot (newer snapshots supersede
older queued writes). Corrupt/non-finite rows are ignored. Existing
favorites, history, downloads, settings, backup formats, application IDs and signing
are unchanged. There is no cloud recommendation requirement or new dependency.
Existing optional anonymous community popularity remains a cached background input;
network failure does not prevent local ranking. No backend/Supabase changes are needed.

## Hard category eligibility and immediate switching

All Content uses all existing providers. Each other mode (Women, Men, Trans Women,
Trans Men, Groups) accepts only membership returned by OnlyHaven's configured selected
category endpoint. Its parser already rejects contradictory explicit provider genders.
No other provider currently has a verified mapping in this pipeline, so those sources
are excluded in filtered modes. No names, titles, keywords or images classify gender.
Scarcity/failed category loading yields a wait/empty state, never an unfiltered fallback.

The preference listener immediately cancels old acquisition, inventory and verification
work. Queue pruning waits only for an already-running pager transition to settle,
then drops every future clip, including the prefetched successor. The current player,
playback generation and lease survive. Targeted removals retire incompatible past pages, including the outgoing current
exception after its first swipe, while retaining the current holder/URL lease.
A page-selection guard rejects incompatible old pages during a racing gesture. Worker admission and
final publication independently re-check the current category. Old callbacks cannot
restore incompatible content. Returning to All reuses the broader local profile.

## Performance and validation boundary

Playback uses the existing normal six / rapid ten ready-clip runway, player leases,
preparation/cleanup staggering, Focus Mode, thumbnails, mute/speed, creator navigation,
history, favorites and downloads. There is no scoring/network wait in swipe dispatch.
The recovery tick never ranks or resolves media on main. Work and queues remain bounded.

New source recovery/category requests and admission starts follow the existing runway
hysteresis: fill to six normally or ten during rapid scrolling, then resume at four
or six respectively. A full runway closes the start gate even when playback permits
speculation and raw candidate queues remain populated. The gate is checked by the
coordinator, again when a queued verification lane starts, and after waiting for a
shared resolution slot. No provider validation is weakened to save work.

Demand is separate from lifecycle/playback permission. Up to three existing verification
tasks may finish; their results stay in the bounded pending map for the next refill.
Raw leftovers and source caches remain available. Full-runway pauses do not cancel a
category generation, reset the selected player, or count as a dry refill. Existing
host/category cancellation still invalidates pending work. An already running bounded
source acquisition/hidden harvest can finish; the recovery tick starts no replacement
acquisition while demand is closed.

Session diagnostics add aggregate `admissionStarts`, `runwayPauses` (coordinator exits
because demand closed), `startDeferrals` (queued lanes deferred before admission), and `verificationActive`
(running verification lanes). The existing `activeAdmissions` counts pending entries,
including completed results awaiting publication, rather than just running tasks.
These counters contain no URLs and use the existing bounded report. They measure
scheduling, not frame improvement; in-flight tasks can still appear at a full runway.

Focused tests cover source recovery/backoff/single-flight/generation safety, diversity,
actual-viewing learning/decay/action caps/exploration, exact category evidence and
queue invalidation, and retained slow verification. Relevant existing regressions
and complete debug/release suites/builds run in CI. The candidate's performance matrix
is limited to ShitTok scrolling and long sessions. Report actual run results in the PR;
this document does not assert successful CI, emulator or physical-device performance.

Provider drift, browser challenges/explicit age access, sparse categories, and media
quality remain external constraints. No source route, domain, selector, header,
request timeout, retry count, CDN handling or remote document/schema changes are made.
The pool's session scheduling backoff is compiled app control flow, not remote code.
