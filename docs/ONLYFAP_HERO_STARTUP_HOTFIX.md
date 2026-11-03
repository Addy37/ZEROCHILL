# 6.0.1 OnlyFap hero startup latency

Continues the same hotfix branch and PR #401, preserving both crash fixes.
This is separate from the recycled-bitmap defect.

## Traced startup waits

1. `requestStartupHero` read small remembered metadata on the serial `metadataIo`
   executor, which also performs favorite grouping, catalog snapshots, avatar
   overrides and persistence. A cached image could not even start its Glide
   request until that earlier work finished. Startup now has a single serial
   executor of its own, cancelled/shut down with the view. Metadata IO remains
   off the main thread; no extra bitmap cache is introduced.
2. Remembered artwork used a cache-only Glide request. A miss forgot the known
   original URL and tried an avatar, leaving full artwork dependent on gallery
   rediscovery. A miss now immediately starts a normal cache/network Glide
   request for the same known artwork. Actual remote failure still forgets that
   optional metadata and tries a distinct available thumbnail, with no retry
   loop. Normal and preference startup share this fallback policy.
3. `resolveHeroAsync` withheld its first qualified portrait until it finished
   seeking a second one: up to two Fapello media pages, original/post resolution
   and image qualification, plus OnlyHaven search/gallery fallback. It now posts
   the first dimension-qualified original immediately through the existing
   generation-checked UI work queue. Alternative discovery continues unchanged;
   its final result updates the existing candidate without restarting the hero.
   Only immutable snapshots cross to the UI; the worker's mutable list is not
   shared. Eligibility, portrait rules, exclusions and source policy remain.

Activation already requests startup before favorites; New Creators already
provides a thumbnail before shelves finish. Those paths remain. Navigation's
settled/frame work scheduler remains, avoiding premature heavy UI work while
swiping tabs. No source routes, timeouts, dependencies or design changes.

## Safety and measurement

All requests use the two live Glide-owned targets from the bitmap fix. The
current hero stays owned and visible while remote replacement is pending;
crossfade and rift timing/compositing remain unchanged. No borrowed previous
drawable, pixel copies, full-resolution duplicates or global cache changes.
One additional lazy executor thread handles small startup metadata, with the
same generation and close guards and explicit shutdown.

Existing AppPerformance diagnostics now record startup-request to metadata-ready
and startup-image-request to visible commit (cache or known remote). These spans
exclude later shelf completion; the image span includes the posted safe handoff.
They are tracing support, not a claimed physical-device benchmark.

Regression coverage extends OnlyFapHeroStartupTest with a blocked favorites
executor and a warmed actual Glide cache: cached artwork must display before the
block is released and without a network fetch. A controlled fresh fetch stays
pending while the previous live request remains complete; releasing it performs
the original crossfade before alternate resolution finishes. Final alternatives
do not replace the selected candidate, and outgoing ownership clears after the
fade. The uncached remembered-original test requires exactly one remote fetch
and retained metadata rather than avatar/gallery rediscovery. Existing recycled
bitmap, stale request, preference/rift, startup, and ShitTok regressions remain.

Final timings, negative-control results, tests and signed source identity are
reported in the PR/handoff. No physical-device speedup is claimed. No migration,
Supabase/backend, remote-config, persisted format or signing identity changes.
