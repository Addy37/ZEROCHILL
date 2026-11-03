# OnlyFap gallery layout lifecycle repair

Baseline: latest main `8a46530dd46104f7e9734c48d0a235362107afe1`, including
merged #401 and #402. Branch: `fix/onlyfap-gallery-layout`. This is a physical-test
candidate only; keep the PR draft and unmerged until the exact signed build passes.

## Investigation

The creator All/Pictures/Videos pages use `BunkrGalleryAdapter(true)` and
`StaggeredGridLayoutManager`, with the existing two-column phone default. The
RecyclerView item animator is already null. Adaptive tiles reset their ratio at
every full bind, using a per-URL cached ratio or source hint, otherwise a square
placeholder. Real drawable dimensions arrive through bounded, untransformed Glide
requests. Binding generations guard those callbacks; `applyAspectRatio` also
checks the current adapter position and URL. Ratio changes call `requestLayout`.
There is no separate favorite image-loading or sizing path. Favorite payloads only
change the heart overlay; favorites also stably partition the source order.

The uncovered defect is in animation ownership. Favorite reordering snapshots
visual positions and uses `ViewPropertyAnimator` translations directly on item
views. Pinch morphs also use item transforms. These properties affect drawing,
not the item bounds used for masonry placement. Neither full binding, detachment,
nor recycling previously canceled/reset the root tile's transforms. An interrupted
move can leave a tile shifted over its neighbor; a reused pinch-scaled tile can
look too tall despite correct measured dimensions. Pending favorite pre-draw
snapshots also had no invalidation for replacement, adapter detachment, or delayed
image dimensions changing layout coordinates.

Negative controls against the unchanged production adapter ran 12 cases across
API 29 and 35: eight passed, four failed. Both interrupted-move recycling and
full-rebind translation/scale reset failed on each API. These controls inject an
interrupted transform to isolate ownership from measurement. A second negative-control
run on API 35 used the actual favorite reorder path:
both cases failed, with intersecting visual bounds after a delayed dimension update
and a surviving 1,599 px vertical translation after detach of an interrupted favorite move.
The expanded fixture also exercises the actual favorite reorder code, delayed
ratio arrival during a pending favorite move, payload-only updates, retained tabs,
scrolling and reopen. Device checks use actual Glide decoding and the real adapter.

This proves a cross-device lifecycle defect consistent with the screenshot, not
why a specific Pixel exposed it. The supplied screenshot and “later models” report
do not establish the exact model/API, cancellation timing, or a platform defect.
No manufacturer check or StaggeredGridLayoutManager replacement is justified.

## Repair and compatibility

Only the shared gallery adapter's animation lifecycle changes in production code.
Full bind, detach and recycle normalize tile translations/scales. Favorite moves
have an owned pending listener and generation; replacement, page deactivation,
adapter detachment and genuine dimension changes retire obsolete moves. A new
favorite move still starts from current visual positions and uses the existing
240 ms curve. The ratio calculation, decode bounds, hints, square placeholder,
margin/radius/colors, stable favorite ordering, video/play overlays, touch actions,
pinch density, viewer and zoom behavior remain the existing implementation.

No application ID, signing, version metadata, source API/parser, remote config,
auth, Supabase/backend, persistence, favorite storage, history, download or backup
change. No migration is needed. The same lifecycle protection applies to other
users of this adapter, including the fixed-square Bunkr gallery; their sizing
policy is unchanged. Interrupted transitions may finish immediately when their
coordinates become invalid rather than continuing with a stale transform.

## Validation and physical acceptance

`OnlyFapGalleryLayoutTest` uses real RecyclerView layout on API 29/35 and checks
visual bounds as well as measured heights. Existing favorites, motion and viewer
tests remain intact. `OnlyFapGalleryLayoutProbeActivity` is debug-only and decodes
synthetic portrait, landscape, square, very tall and very wide media through the
real Glide request path, including late deliveries, rapid favorites, scrolling,
tab data changes, retained-adapter reattachment and fresh creator adapters.

The signed-candidate workflow runs the focused tests and the repository's four-task
debug/release command, verifies production signing/upgrade compatibility, names the
release-package candidate with branch/SHA, and runs the probe twice on Pixel-profile
API 35 and 36 emulators. Logs/screenshots are artifacts. Workflow configuration is
not evidence that those jobs have passed; use the exact commit's run results.

Physical acceptance still needs the affected Pixel with its real creator/favorites,
and the healthy OnePlus CPH2655. Check scrolling down/back, rapid favorite/unfavorite,
All/Pictures/Videos, creator reopen, pinch, tap, long press, viewer and zoom. Synthetic
emulator media cannot establish the behavior of every live source image, OEM GPU,
or the user's persisted favorite set. No physical verification is claimed here.
