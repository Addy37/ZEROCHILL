# PR #401: filtered hero pool candidate

The OnePlus beta rejection concerns the size/quality of Female, Male and Trans
rotations, not merely first-artwork latency. Physical acceptance remains pending.
The earlier startup/atomic-handoff work did not extend filtered candidate discovery
or validate source resolution, so it could not repair this starvation path.

## Confirmed implementation limits

* Category loading publishes three 16-creator shelves. Filtered hero selection
  only inspected that union. When it exhausted the union, requestMoreHeroDiscovery
  returned immediately for any mode other than All. One usable original among
  those rows therefore produced one permanent slot, even with later category pages.
* A selection task captured category rows and shelvesFinished before asynchronous
  shelf publication. New shelf/completion requests were skipped while selection
  was busy. An old empty result with finished=false did not request another pass,
  so even later valid published creators could be stranded behind 1-2 slots.
* Filtered resolution fetched only page 1 / 12 media items for an exact OnlyHaven
  profile. A first page of videos/landscapes/tiny images ended that creator's search.
* Qualification tested only aspect ratio (height >= width * 1.18). Portrait avatars
  or tiny direct images could pass; startup thumbnails were visually indistinguishable
  from a stalled original search. Glide can upscale during decoding, so checking
  resolution in a transform without preventing decoder upscaling is insufficient.
* Hero request deduplication used favorite/display-name keys, despite category rows
  having service/provider identities. Renamed duplicates could consume attempts;
  distinct provider profiles sharing a name could be skipped. Artwork was only
  deduplicated within one creator, allowing repeated pixels to fill multiple slots.

A category revision now changes for every shelf publication, completion and hero
backfill page. An old selection result restarts against the latest inputs after
clearing its busy flag. A controlled late-shelf/empty-selection regression exercises
that lost-wakeup ordering without replacing the selector with a helper.

The existing repository parser already prefers an original variant when present
and builds direct media URLs separately from preview URLs. It remains unchanged.
No live-source timing or source inventory claim is inferred from the phone video.

## Targeted change

Filtered heroes consume the existing strict category endpoint independently of
visible shelf count. After shelf candidates finish, they request offsets 48..160
in 16-row pages (at most 8 additional requests / 128 raw rows). At most one category page and one
creator resolution are outstanding. Stop at 8 unique qualified creators, provider
exhaustion, a failed category page, or the finite page budget. A duplicate-only or
unusable page advances the raw offset rather than rewinding or completing a slot.
There is no unfiltered/name-search fallback in category modes.

For each exact filtered profile, sample at most 4 images per media page across at
most 2 pages. Stop after its first accepted original and proceed to another creator,
rather than waiting for alternate artwork for the same creator. All retains its
existing Fapello discovery, exclusions, ranking and OnlyHaven alternate sampling.

Qualification and display continue to share a Glide-owned resource. The hero-only
DownsampleStrategy caps decode scale at 1, before the pooled center crop. The
transform requires the existing portrait aspect and enough decoded pixels for the
viewport, capped at 720x1080 qualification demand. This rejects tiny portraits
without decoding every source at full resolution. A changed transform disk key
revalidates previous transformed results; source/data disk caching remains intact.
Known OnlyHaven preview/avatar paths are not promoted as gallery originals.
Temporary startup thumbnails still appear quickly and never take a rotation slot;
a qualified original replaces them through the existing owned targets and reveal.

Filtered identity keys use canonical provider URLs. Duplicate artwork across
creators does not take another slot. Small, separate per-mode pools cache only
qualified URL/referer/creator metadata (at most 8 candidates per filtered mode),
never Drawable/Bitmap objects. Cached entries are restored only after current
same-mode category results confirm membership, and remain subject to Glide's
quality check. Current-generation checks guard both posted and deferred work.
A filtered periodic refresh keeps qualified slots and does not restart an exhausted
crawl. A new load/gender generation has a fresh bounded budget.

## Regression evidence and physical acceptance

OnlyFapFilteredHeroTest exercises the actual asynchronous hub selector/resolver,
controlled category/media responses and real Glide decode/display:

* Female, Male and Trans: tiny first gallery artwork, a blocked later category page,
  then enough valid originals to build 8 unique slots; no tiny image gets a slot.
* Duplicate creator and cross-creator duplicate artwork do not fill extra slots.
* A thumbnail remains while a second media page is blocked, then upgrades to its
  original with one qualified slot.
* All -> Female -> Male -> Trans while an old category request ignores cancellation;
  the stale result cannot join the latest pool or become selected.
* Repeating duplicate source pages stop after the page budget, without retry loops. Restoring the old filtered early return makes the Female
  asynchronous pool regression fail (no later page requested).
* A mode-specific qualified cache restores only after same-category confirmation.

Existing All/startup, normal rotation, LiquidRift ownership, ShitTok selection and
merged DM tests remain required. Native rendering probes use realistic-size fixture
originals so qualification cannot pass merely because a fixture was upscaled.
Final executed counts/build/signing evidence are in the PR handoff.

Capture `adb logcat -v threadtime -s ZeroChillHero:I AndroidRuntime:E` from before
opening OnlyFap, through each selector and several rotations. In addition to the
existing correlated hero phases, capture:

    pool mode=women|men|trans_women|trans_men offset=... candidates=... qualified=... exhausted=...
    pool mode=... qualified=... requested=...

Acceptance is a healthy, sharp, strictly filtered rotation on the physical device.
The bounded sample may still exhaust before eight slots when the provider has
insufficient usable originals; no cross-gender filler or infinite crawl is used.
A transient source failure stops that pass; user reload starts a new pass. This
candidate is not physically approved based on startup timing or unit tests.

No application/signing identity, persistence format, database migration, Supabase,
backend, source configuration or selector/design/animation timing changes. Main
f3d4b1a5 (#402) is integrated; both original #401 crash fixes remain intact.
