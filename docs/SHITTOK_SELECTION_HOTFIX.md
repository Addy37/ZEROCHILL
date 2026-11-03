# 6.0.1 ShitTok selection/pruning crash

Continues `hotfix/liquid-rift-bitmap-crash` / PR #401, preserving the LiquidRift
fix. Production fingerprint: `308eee7e75b6`, Pixel 8 Pro / Android 17 / 6.0.0.
No merge, release publication, migration, backend or remote-config change.

## Confirmed sequence

1. The feed has eleven rows, selected page 10, and a playback lease keyed by its URL.
2. A preference/source switch removes future rows and calls
   `pruneIncompatibleHistory()`. It deletes incompatible earlier rows and decrements
   selectedPosition, retaining the current clip. Five retained history rows plus
   the current clip leave six rows, with the current clip now at position 5.
3. Targeted adapter notifications run before RecyclerView finishes the new layout.
   A page callback can still describe the old layout. The existing callback
   accepts position 10, overwrites the corrected selectedPosition, and immediately
   calls settleSelectedPage while idle. There was no range or mutation guard.
4. Reentrant history pruning starts at selectedPosition - 1, so items.get(9) runs
   against six rows and throws. The regression delivers the old-layout callback
   through the real ViewPager callback composite from an AdapterDataObserver;
   the released implementation reproduces the exact exception and production
   method/line pair (`pruneIncompatibleHistory:1005`, `settleSelectedPage:672`).

Refresh and hide also published notifications before updating selection. Creator
block pruning updated the numeric selection but still allowed callbacks to
replace it during notification. Future removals/moves and refill insertion shared
that publication surface. A stale index that still fits the new list can select
another URL, reset resume state, and change audio ownership without throwing.

## Fix

Queue updates preserve a URL anchor and fallback position. They synchronize the
logical selection and holder URL/index bindings before publishing notifications,
ignore reentrant pager selection/scroll callbacks, then reconcile the pager,
playback window, planned position/URL and future maintenance after the update.
Old layout callbacks are also rejected while RecyclerView has pending adapter
updates. Every page selection must be in bounds and agree with the pager.

Retained clips keep their lease/token and resume position despite numeric index
changes. If the selected clip is removed, creator-block pruning retains its
existing nearest-survivor rule; hiding selects the next survivor or previous tail.
An absent URL falls back to the nearest bounded index, never an unconditional
reset to the first clip. Empty queues use selectedPosition/currentItem 0 as a
sentinel, clear playback/planned state, and do not index the list. ViewPager2
ignores setCurrentItem on an empty adapter, so that boundary reattaches the same
adapter when needed to reset its stale currentItem. Normal swiping does not do so.

Removed holders become NO_POSITION and release their lease attachment; holder
lookup proves both URL and logical position, including RecyclerView's pre-layout
interval. Cancelled index-based preload work is rebuilt from the committed queue.
A secondary settle-time repair uses the last selected URL before any index read;
clamping alone cannot reject a stale index that happens to fit a smaller model.
No try/catch suppresses the exception, and no filter/ranking rule is relaxed.

## Mutation audit

| Path | Mutation and synchronization |
| --- | --- |
| Content/gender/source changes | Existing future truncation remains immediate at the idle apply boundary; nested history removal and warm insertion publish under one selection update. Old inventory work is cancelled before deferred UI applies. |
| History pruning | Retains selected URL while removing earlier incompatible rows; notifications cannot replace the shifted selection. Seen/history storage remains unchanged. |
| Continuity pruning | Removes failed/hidden/blocked future rows under the same publication guard; normal refill resumes outside the mutation. |
| Creator blocking | ShitTokQueuePruner mutates the list; its nearest-survivor target is synchronized before callbacks/player cleanup. |
| Hide | Preserves the current URL when hiding another row; updates selection before notification when removing the current row. |
| Refresh | Resets generation, cancellation, ownership and selection before publishing the empty list. |
| Partial/final refill | Only appends on UI, after existing batch identity/cancellation/generation checks; insertion notifications use the common guard. |
| Creator diversity sequencing | Moves only future rows; mapped holders and future plans follow the final order. |
| Orientation/source/creator eligibility | Candidate admission filters before append; no background worker directly mutates items. Invalid future clips go through continuity pruning. |
| Recovery, pause/return, Search | Reuse those same mutation paths. Background work still respects active/resumed/Search gates and stale generation checks. |

Nearby direct selected/neighbor access was reviewed. Playback-window reconciliation,
Search close, cache wake, position remembering and holder ownership now check both
bounds through hasSelectedPage. Existing markSeen/prepare/resolve bounds and
URL/token guards remain. No unrelated source parser, ranking, focus/pinch,
comments, likes or fullscreen implementation changes.

## Regression coverage

Extends existing SourceMode/Continuity fixtures with real feed/adapter/pager
handoffs and decoder-free playback engines: exact eleven-to-six crash (API 29/35),
valid-but-stale callbacks, retained token/resume position, removed-holder binding,
rapid gender/source changes during swipes, switching after settlement, singleton,
empty refresh, hide/nearest survivor, background/foreground and Search, refill
following pruning, and old deferred generation results after refresh.

The existing debug Search/video probe gains an optional deep-pruning mode using
the local cached video, real Media3 decoder and actual ViewPager. It verifies the
same player/token/position after pruning, Search return, and background release
on CI API 29, 36 and 37.0. Decoder output counters avoid the known Android 17
emulator screenshot/texture readback assertion. This is emulator validation;
physical-device acceptance remains pending. Final results and APK source/signing
identity belong in the PR and physical-test report.
