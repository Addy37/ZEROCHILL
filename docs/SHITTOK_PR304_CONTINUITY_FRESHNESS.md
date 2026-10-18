# PR #304: sustained inventory and cross-session freshness

Continue `feature/shittok-deep-source-exploration`, base `rebrand/zerochill`.
The starting head `1a94848f` has exactly the `f95bb681` rollback tree. Do not restore
the reverted Bunkr/TheYNC/ItemFix expansion. No merge is authorized.

## Code-traced failure paths

- PR #297's 6/10 targets and 4/6 thresholds remain. They measure contiguous
  admitted clips with playable URLs, independently of the three-player pool.
- Recent URLs were resolved/probed and counted against a fixed batch admission
  budget before the UI discarded them. Three dry batches deliberately admitted
  those recent items again. Likes were fetched for visible holders only and
  never excluded candidates. The old recent list held only 500 raw URLs.
- A one-item deficit allowed only one active admission task. A slow URL could
  occupy the entire effective pipeline even though three workers existed.
  A fixed batch deficit also ignored clips consumed/rapid mode entered mid-batch.
- Existing unresolved adapter entries blocked new source harvest altogether.
  A 2.2-second source deadline cancelled multi-page regular harvests before their
  accumulated results could publish. These weaknesses predate PR #304.
- PR #304 reduced the first handoff to six items, retained fair early provider
  admission, and gated deeper ordinary pagination/catalog work on a selected
  holder's rendered-frame callback. A missing holder callback or a starter pool
  exhausted by recent filtering could leave all ordinary listings on page 1.
  This is a code-proven dependency, not proof that it caused every gap in the
  supplied 180-second recording. The recording has no inventory trace.

## Repair

Keep starter racing, fair early handoff, asynchronous splash work and eFukt
startup limits. Ordinary post-starter listing pages no longer require a first
frame. Nonrepeating shuffled decks visit up to 32 existing CrazyShit/Kaotic/
Fapello pages; BaddieHub retains its three-page limit. Decks survive Refresh.
OnlyHaven remains on its known-good page-1 path. Expensive catalog/WebView work
still waits for rendering, now also signalled by the player owner.

Source work retains three protected workers, prioritizes independent listings,
publishes each completed regular page immediately, and gets a 5.5-second outer
batch window inside the existing six-second admission budget. Source request
timeouts/headers/remote configuration do not change. Interrupted source work
cannot start a second copy for the same provider while the original still runs.

Admission races up to three candidates even for a one-item deficit. Its bounded
budget grows with forward consumption and increased runway pressure.
While providers are still producing candidates, at most two active admissions
may belong to one provider, leaving a lane for another provider's later result.
Once production completes, a single provider can use all three lanes again. Rejected seen/liked/duplicate aliases do not spend the playable admission budget. Legacy
unresolved pages no longer prohibit independent source fetching. Adapter/player
mutations still wait for paging idle. No new player or far-ahead media buffering.

## Freshness and compatibility

`shittok_discovery` adds up to 8,192 seen identity entries and 32,768 durable liked
exclusions. A clip can use both a page identity and a media alias, so these are
identity counts, not guaranteed clip counts. Legacy `chaos_feed.recent_urls` is
imported and remains maintained for rollback/backup compatibility. Seen entries
age out by insertion order; likes use a larger independent bound and no dry-batch
fallback. Unlike does not erase the device's historical anti-repeat signal.

Identities reuse CrazyShit/Fapello post-ID shapes. Other URLs retain their host
and case-sensitive path; unknown page routes retain meaningful query parameters.
Resolved media aliases strip only explicit transport/tracking query parameters.
Hosts/filenames are not globally merged. Previously unknown CDN host aliases or
historical server hashes with no known URL mapping cannot be inferred safely.

Existing `video_likes` reads support one small own-account key lookup per
fresh provider result using its existing public-read policy, with 900 ms connect/read
timeouts and no startup auth refresh. Known likes are persisted locally and rechecked before
resolution, after resolution and before insertion. Lookup failure permits
discovery using local exclusions and increments `likedLookupFailures`; a remote
like never learned on this device may pass during that explicitly reported
offline/unavailable condition. No backend schema, policy, Supabase deployment,
source endpoint, or remote-config publication is required. Signed-in cold-start
latency includes this extra bounded key read and requires device verification.

## Device handoff

After testing, open Settings > Performance details > Copy diagnostics. The
retained pre-pause snapshot includes inventory, selected playback state, source
timings/active fetches, queue/resolver pressure, rejection totals, dry refills,
tail hits, refill durations and startup timings. Copied content URLs are removed.
No account/session credentials enter this report. Counters survive ring rotation
for the retained feed; they reset with a new feed/process. Minimum usable runway
includes the initial fill; observed stress samples also report a separate minimum.

`tools/stress_shittok_runway.sh` sends 180 aggressive flicks in roughly 90 seconds,
collects bounded aggregate logcat counters and screenshots, and reports missing
evidence/crashes. It measures an emulator, not physical smoothness or an account's
historical-like migration. `tools/measure_shittok_cold_start.sh` retains three
bounded cold-process samples. Exact run results belong in the PR after CI.
