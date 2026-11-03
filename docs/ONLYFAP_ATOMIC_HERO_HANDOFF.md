# PR #401 — normal hero handoff after OnePlus physical rejection

The signed 3cf9c476 candidate failed hero-speed acceptance on OnePlus CPH2655 /
Android16. The reported crash fingerprints were not reproduced on that device;
that does not establish crash absence. This follow-up is a physical-test candidate,
not a claim that the hero speed issue is accepted or fixed on the phone.

## Before: confirmed work and ordering

showHero published heroItem/index/title/hint/accessibility/dots before the display
resource existed. Animated rotation then put loadHeroArtwork behind a 110ms card
fade, at normal animation scale. The old image could therefore carry the next
creator's text for the whole display load. The normal 240ms reveal cleared the
outgoing owner immediately and faded the new image from the dark background.

Qualification used an asBitmap / fitCenter / 360x540 request, while display used
a Drawable / centerCrop request at view size. These are different Glide resource
keys. A controlled comparison against 3cf9c476 (real Glide/native decoding) measured:

| Controlled cold path | API29 | API35 |
| --- | ---: | ---: |
| Qualification, wall time including fixture response/dispatch | 90ms | 68ms |
| Separate display and commit, including dispatch | 68ms | 45ms |
| Actual decode stages | 2 | 2 |
| Actual source fetches | 1 | 1 |

These are test-machine samples, not OnePlus benchmarks. The first-display
comparison did not animate rotation; its numbers do not include the separate
110ms rotation gate. DiskCacheStrategy.ALL already avoided a second download in
this fixture, but did not avoid the second decode/transform. Source-resolution
and network phase durations on the supplied phone recording cannot be recovered
without the correlated trace below. Do not attribute the remaining phone latency
to metadata or qualification without those measurements.

The previous optimization removed startup metadata queue blocking and published
the first qualified candidate before alternates finished. It left early normal
identity publication, the rotation fade gate, and mismatched qualification/display
requests intact. Its startup tests did not cover this physical rotation failure.

## Atomic pending state

showHero stages pendingHero and immediately loads the existing secondary target.
The committed image, copy, index/dots and open action remain on the current creator.
The successful, current image-ready callback swaps owned targets and commits the
identity in one UI runnable. Failed/stale candidates never publish visible identity.
The 110/180ms card motion begins after readiness, without gating loading.

Normal replacements retain both existing Glide owners during a 240ms image
crossfade, avoiding a fade from black. Startup crossfade remains 350ms. The
1040ms LiquidRift, cyan edge, geometry and source/category policies remain. Clear
callbacks collapse transition references before resource release. Leaving the tab
cancels staged/qualification work; reentry can stage known candidates immediately.

## One decode, loader-owned handoff

The first original is requested with the exact display size, Drawable transcode,
options and transform. OnlyFapHeroCrop validates source aspect before delegating
to Glide's existing pooled center crop. The 1.18 portrait rule is unchanged;
landscape originals cannot become eligible merely by being cropped.

The qualification FutureTarget keeps its EngineResource acquired until the UI
target acquires the same resource through Glide. Only then is the FutureTarget
explicitly cleared (cancel alone does not clear completed targets). The controlled
test disables the resource cache/pool and still requires one fetch, one decode and
a MEMORY_CACHE display delivery: ownership, not an app bitmap cache, bridges it.
Two Glide request owners share one prepared resource; no drawable is handed away
from an ended request and no pixels are copied by the app.

Alternates retain bounded qualification sizes. Nonselected first images return to
normal Glide caches after publication; decoded images are not pinned for the whole
carousel. Later eviction or a real size change can require a new display decode.
Normal and preference display paths share the exact builder. No global Glide cache
policy, quality downgrade, new dependency or custom bitmap cache is introduced.

## Next OnePlus capture

Start capture before opening OnlyFap, record startup and at least two normal
rotations, then copy the recent event block from AppPerformance diagnostics or:

```sh
adb logcat -c
adb logcat -v threadtime -s ZeroChillHero:I AndroidRuntime:E
```

Capture all lines for the same `hero=N`:

```text
hero=N phase=candidate_selected elapsedMs=... deltaMs=... source=-
hero=N phase=original_resolved elapsedMs=... deltaMs=... source=-
hero=N phase=qualification_started elapsedMs=... deltaMs=... source=-
hero=N phase=qualification_completed elapsedMs=... deltaMs=... source=REMOTE|DATA_DISK_CACHE|RESOURCE_DISK_CACHE|MEMORY_CACHE
hero=N phase=first_published elapsedMs=... deltaMs=... source=-
hero=N phase=show_requested elapsedMs=... deltaMs=... source=-
hero=N phase=display_started elapsedMs=... deltaMs=... source=-
hero=N phase=resource_ready elapsedMs=... deltaMs=... source=MEMORY_CACHE|...
hero=N phase=commit_completed elapsedMs=... deltaMs=... source=MEMORY_CACHE|...
hero=N phase=image_visible elapsedMs=... deltaMs=... source=-
hero=N phase=transition_finished elapsedMs=... deltaMs=... source=-
```

Subtract paired elapsed times for resolution, qualification, publication queue,
show-to-request gate, display loading, commit queue and first frame. A pooled
candidate may wait before show_requested; do not count carousel dwell as decode.
The hardware path records image_visible at frame commit after the first eligible
visible transition frame. Software/older Android uses the following frame boundary;
neither is an optical panel measurement. Diagnostic storage is bounded to 256 small
events, with no media URLs or creator names. Keep PID/time prefixes across restarts.

## Regression/compatibility scope

Actual hub/Glide tests cover delayed rotation, coherent old action/copy/pixels,
zero built-in request gate, atomic ready commit, owned outgoing transition/release,
single decode/fetch handoff, landscape rejection, stale requests, teardown, startup
and preference rift. The existing device probe includes a delayed normal rotation.
Both original crash suites remain required. Final counts, APK identity and CI results
are in the PR/handoff. No physical acceptance is claimed.

No app ID/signing, persistence format, source eligibility/filtering, backend,
Supabase, remote-config or migration changes. PR remains draft and unmerged.
