# 6.0.1 OnlyFap bitmap lifecycle hotfix candidate

Baseline: released main `4144b9e312b90822d121af0cd396127e39525a4c` (6.0.0).
No release, tag or merge is authorized before physical approval.

## Ownership trace

`LiquidRiftImageView.onDraw()` delegates to `ImageView.onDraw()`, either directly
or inside its clipped liquid wave. It owns no bitmap and `resetRift()` changes
only rendering state, transforms and cached RenderEffects. The unsafe ownership
originates in `OnlyFapHubView.loadStartupHero()` and `loadHeroArtwork()`:

1. Request A owns bitmap A and displays its drawable on `heroImage`.
2. The app reads that drawable and passes it as both placeholder and error to B.
3. Glide 4.16 `RequestBuilder.into()` clears the old view request before starting
   B. `SingleRequest.clear()` first calls target `onLoadCleared(placeholder)`,
   then releases its EngineResource. A drawable reference does not acquire that
   EngineResource. BitmapDrawable.ConstantState/newDrawable would not fix this.
4. B installs the previously captured drawable again as its placeholder. An
   error can keep it there indefinitely. Clearing B can reinstall it again.
5. Once Glide's memory cache/pool evicts or recycles A (particularly under memory
   pressure), the next ImageView draw uses invalid pixels. The startup 350 ms
   TransitionDrawable can also keep A after its owner is gone.
6. Android rejects the bitmap in BaseCanvas.throwIfCannotDraw, through
   BitmapDrawable.draw / ImageView.onDraw / LiquidRiftImageView.onDraw. The
   liquid effect exposes the invalid drawable; RenderEffect is not required.

The regression was run against the released hub implementation with controlled
Glide fetches and real recycling on API 29 and 35. Both fail with
`RuntimeException: Canvas: trying to use a recycled bitmap`, through
`BaseCanvas.throwIfCannotDraw -> BitmapDrawable.draw -> ImageView.onDraw ->
LiquidRiftImageView.onDraw(LiquidRiftImageView.java:51)` (plus the normal
ThumbnailFades wrapper in this fixture). The reports omit their exception
message, but the reproduced exception and draw location establish the concrete
resource-lifecycle defect rather than attributing it to a device or RenderEffect.

## Fix and visual behavior

Use the existing two stable LiquidRiftImageViews as separate live Glide targets
for all hero requests, including cached startup and qualified artwork. Keep the
outgoing request alive while the replacement loads. Swap views after Glide has
installed the new drawable; never detach/reparent a live target or copy pixels.
No old drawable becomes a placeholder, error drawable or request input.

The normal new-image fade still uses ThumbnailFades' 240 ms policy, with the same
cache, visibility and motion eligibility. The startup handoff keeps Glide's original 350 ms TransitionDrawable, backed by both owned targets. The preference effect keeps its 1040 ms clock, clipped
liquid reveal, cyan edge, focus/RenderEffect, scale and text phases unchanged.
Normal swaps preserve the current background roles. During the startup crossfade
the outgoing view is hidden and its bounded reveal wrapper is settled, so the
TransitionDrawable alone draws the two owned frames without stacking artwork.
Outgoing targets clear immediately after their last visible use. Hero targets'
`onLoadCleared` also collapses any active crossfade before Glide releases either
owner, including independent lifecycle clears of the outgoing or incoming target. No global Glide
cache, pool, decoder or recycling setting changes.

Each new artwork request gets an identity and explicitly clears the hidden target
before loading. This prevents Glide's equivalent-request reuse from retaining a
listener for an older hero/generation when URLs and options match. Posted commits
check request identity, live target, completion/clear state and candidate/category
before swapping. Preference callbacks no longer capture resource drawables for
later reassignment. Close invalidates commits and clears both targets; tab exit
and detach settle transitions before clearing outgoing artwork. Activity-bound
Glide lifecycle still pauses pending work in the background and clears on destroy.
Reentry keeps the existing cached-tab behavior and valid current request.

OnlyFapHeroMemory stores remote URLs/creator metadata, not bitmap references.
ColdTabSnapshot owns a separately bounded decorative snapshot, not a borrowed
Glide bitmap; it follows the swapped hero target. No full-resolution copy is
introduced. At most the two existing hero targets hold decoded artwork during
loading/transition, and Glide retains responsibility for both lifetimes.

## Regression and review scope

The new actual-hub/Glide fixture supplies controlled delayed fetches and uses
zero-capacity cache/pool adapters **in tests only**, forcing real recycling on
release. Android API 29 and 35 native graphics tests exercise pending replacement
under memory pressure, A to B release/draw, A to B to C with late cancellation,
error/placeholder behavior, close during a request, preference rift settlement,
startup crossfade/tab exit, and clear between delivery and posted commit.
Existing startup/category/design/rotation/discover/transition tests remain part
of validation. The debug real-Glide GPU fixture is run by the candidate workflow
on API 29, 36 and 37.0 (Android 17), including repeated entry, trim memory and background/foreground.
These emulator/API checks do not establish physical-device approval.

Compatibility: unchanged package, signing process, minSdk 26, storage, accounts,
preferences, favorites, history, downloads, backups, notifications and databases.
No migration, backend, Supabase or remote-config change. RenderEffect remains
API-31 guarded, and bitmap ownership logic has no modern-API requirement.

Validation results and final source/artifact identities are recorded in the PR
and final handoff report; no unexecuted device checks are claimed here.
