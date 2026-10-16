# ShitTok continuity and startup candidate, PR #297

Continue `fix/shittok-continuity-runway` from `431e1e19`. Keep draft and unmerged
until physical-device approval. The prior APK improved playback buffering but
failed aggressive forward scrolling, launch latency, and loading-indicator tests.

## Causes traced in code

- The protected inventory path stopped at two verified/resolved future clips.
  Source harvest waited for the mixed batch and admission/resolution ran serially.
  Paging could suppress admission or refill even while forward consumption grew.
  Four dry batches could require another user interaction to restart work.
- SplashActivity waited for startup-preloader readiness, then the main activity
  covered its shell with a second splash for up to five seconds awaiting player
  readiness. The preloader and feed could race duplicate source/stream work.
  Shit Show WebView warmup could also be queued before the shell's first draw.
- Each holder enabled Media3's built-in circular buffering indicator alongside
  the application's three-dot progress view. Holder loading callbacks did not
  provide an explicit selected-only BUFFERING transition for the dots.

## Inventory policy

Normal target: six contiguous usable future clips. Refill begins at four and
continues to six. Two forward selections within 1.5 seconds raise the target to
ten; refill begins at six and continues to ten. Each sustained forward selection
extends rapid mode for four seconds. Time-based decay returns to the normal
policy on the next scheduling event. Hysteresis avoids starting work for every
single consumed item. No player count depends on this inventory target.

The producer streams candidates from completed providers into a 48-entry queue.
Startup providers stream their first small result immediately, before waiting
for secondary providers. The mixed-source path keeps its existing sources and
final weighting functions, with a limited early EFukt contribution. Protected
harvest concurrency increases from two workers to three. At most three admission
workers resolve URLs/check metadata in parallel; an executor queue of three
bounds work behind interrupted IO. Admission attempts stop at 24 per batch or
the six-second batch budget. Individual metadata probes keep the existing byte
and time budgets, now with three probe slots. No probe creates a player/decoder.

Each completed valid item may publish immediately while the rest are in flight.
Rejected candidates spend no usable-item budget. Remaining candidates stay in
the bounded pending queue. Generation, hidden, blocked, session deduplication,
and orientation checks still apply before queue insertion. UI and player work
wait for pager idle; source/admission work can continue during paging. Four dry
batches enter a five-second retry pause, with cancellation while hidden/paused.

The player owner retains its three-engine capacity, selected audio ownership,
current BUFFERING/IDLE protection, 750 ms immediate-next recovery allowance,
steady-playback guard, and ordinary neighbor buffering targets. Far-future
inventory has URLs and admission metadata only.

## Startup and loading

The branded reveal retains its approximately 960 ms animation. It no longer
polls network readiness, starts a duplicate preloader, or hands off to a
content-dependent splash overlay. Legacy handoff extras are consumed without
covering the shell. ShitTok starts source work after its first UI draw. The
first admitted/resolved starter can publish before the deeper runway completes.
Returning to the retained feed does not repeat this cold-start gate.

The feed owns its empty-pool dots. Once items exist, only the canonical selected
holder can show preparation/rebuffer dots. Visibility follows actual player
state and host visibility. READY/ENDED hide dots; errors use the existing retry
and skip treatment. Media3 buffering UI is disabled. Player/URL/token/holder/
surface checks plus queued feed-generation checks reject stale UI callbacks.

## Diagnostics and limits

Existing opt-in bounded diagnostics record selected position, queued/usable
counts, target, rapid mode, unresolved work, active admission count, pending
queue, dry/rejected counts, refill reason/completions/duration, time below target,
and physical tail drag hits. Admission records resolution time and source
harvest records duration. AppPerformance records process-to-Application entry,
Application initialization, splash duration, main activity creation/first draw,
first candidate, first admitted/resolved clip, player prepare, and rendered frame.
Application and activity startup have trace sections. Reports include these
on-device timings. No per-frame production log output is added.

A finite bounded runway cannot promise uninterrupted scrolling when providers
remain unavailable or consumption exceeds successful admission indefinitely.
Physical-device startup, networking, and aggressive-scroll acceptance remain
Addy's test. Decoder-free tests cover scheduling/state, not real device timing.
No identity, signing configuration, persisted data format, migration, backend,
remote source configuration, or source endpoint changes are part of this pass.
