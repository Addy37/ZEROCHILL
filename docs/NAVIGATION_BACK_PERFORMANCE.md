# Navigation and Back responsiveness candidate

## Baseline and evidence

Started from PR #408 head `de2117512531bea2f384d8faaf231993caef95e1`.
PRs #407 and #408 were both open, draft and unmerged when verified.
The performance PR targets `feature/zerochill-7-shared-chrome-phase4`.

Focused before-change Robolectric checks reproduced the targeted defects:

| Observation | Before | After (executed) |
| --- | --- | --- |
| Tab handler returns with pager state | SETTLING (2) | IDLE (0) |
| Header title writes for one Library selection | 2 | 1 |
| Search close callback complete in Back dispatch | false | true |
| Scoped Shows player finishes in Back dispatch (motion on) | false | true |

These are deterministic behavior/work counts, not measured frame-time gains.
Search's original exit explicitly used `260 ms * remaining morph fraction` and
waited for layout if no frame had occurred. A fully open Search therefore added
260 ms of app-owned animation before removal and underlying work resumption.
Pager smooth-scroll duration depended on RecyclerView distance and velocity;
no fixed physical-device latency is claimed.

## Root causes and changes

- `NativeMainActivity.showPrimaryPage` used smooth scrolling for taps and Back.
  `MainPagerAdapter` withheld hub activation until pager idle. A selected tab
  therefore appeared selected before its destination was settled/active.
  Taps and Back now use immediate selection; the redundant forced unsettled
  state is removed. Actual navbar drags retain fake-drag and settling behavior.
- `setCurrentItem` invokes `onPageSelected`, which applies chrome. The caller
  applied it again, including title writes and unconditional layout parameters.
  Chrome now skips an already rendered visible destination. Layout parameters
  change only when necessary to restore the pager from legacy secondary content.
- Search removal and primary-work resumption waited for the reverse morph.
  Closing now hides the keyboard and completes synchronously, including before
  the opening layout. Opening visuals remain; IME-first Back behavior remains.

- The Shows player Back path also gated `finish()` on a reverse artwork morph
  (400 ms from a settled frame) or a 140 ms fallback fade. A motion-enabled
  scoped-host regression failed on the original path. Back now cancels that
  motion and finishes immediately after the existing save/pause/cancel steps.
  The opening handoff, completion/history save guard, player ownership and
  non-Shows fullscreen/related-history/minimize routing remain unchanged.

## Investigated and retained

- `MainPagerAdapter` is a retained View adapter, not a fragment navigator. Its
  existing Shows/OnlyFap/Library/ShitTok objects survive tab selection. Four
  attached primary pages cost memory and initial layout, but avoid reinflation
  and preserve scroll/player state. Offscreen limit remains three. No evidence
  justifies replacing retention or navigation architecture in this change.
- Loaded Shows and ShitTok content is not reloaded on tab selection. OnlyFap's
  existing 45-minute staleness policy and content-preference refresh remain.
  Library snapshots run off the main thread and skip unchanged data. Explicit
  refresh and genuinely stale/changed content can still request data.
- `SquareNavigationBridge` installs one ComposeView. State equality suppresses
  identical updates; glass configuration is remembered by appearance revision.
  The tab selector's spring launches independently of selection. The backdrop
  revision is read during drawing; navbar springs do not record page content.
  No GPU evidence implicates blur/refraction, so glass and custom accents remain.
- Activity return reuses the pager; a changed appearance color signature still
  intentionally recreates the Activity. Host resume refreshes badges and resumes
  playback through existing owners. These paths are preserved.
- The Android 33+ Back dispatcher and legacy Back entry both still call the same
  handler. Search, ShitTok fullscreen, legacy content, return-to-ShitTok, then
  Activity finish retain their priority. The Shows player's finish timing is
  changed as above; other player/overlay handlers remain unchanged. Gesture
  fidelity requires physical testing.

## Validation

Focused checks cover immediate tab/Back selection, one chrome bind, retained
page identity, final Activity finish, Search priority/early close, Library scroll
retention, coalesced primary work, fake-drag/insets, and ShitTok audio/search
ownership. Local scoped run: 47 tests passed, zero failures/errors/skips.
The additional integrated Search-before-Library-Back check then passed separately
(48 focused cases total). After-change probe output: tab state 0, Back state 0,
header title writes 1. The initial tests reproduced three failures on the original
application source before the fix. The additional scoped Shows Back test also
failed before its targeted fix. All 13 follow-up checks passed: scoped-host
finish, existing related-selection Back behavior, early/duplicate Back, and
opening artwork motion. Total local focused coverage: 61 cases passed, zero
remaining failures/errors/skips. Signed workflow evidence is recorded on the
PR after execution. Full repository suites and emulator build matrices are
intentionally excluded.

Local setup initially hit a missing test import and a read-only Robolectric
home lock; corrected the import and ran the JVM with a workspace user.home.
Neither is an application regression. The Shows follow-up initially exposed a
null-root case in the existing early/duplicate-Back fixture; guarded animation
cancellation before the successful rerun.

## Compatibility and remaining risks

No dependencies, provider routes, persistence formats, preference keys, account
code, backend, auth, downloads, favorites, history, backups, signing setup or
application ID changed. The workflow preserves the Preview 9 publishable key
and verifies it is present in the APK's DEX without logging the value.

The visible behavior change is immediate tap/Back destination changes, Search
exit and Shows player exit. Navbar dragging remains animated. Main-thread first-time construction,
image decoding, player resumption, network responses and GPU glass cost can
still affect responsiveness. No physical-device speedup or Android 16 gesture
acceptance is claimed. Test rapid taps, Library/OnlyFap scroll retention, Search
IME-first Back, fullscreen exit, overlay dismissal, playback continuity, and
in-place upgrade on the OnePlus before approval. Do not merge or release.


## Physical Preview 10 follow-up: notification work on navigation

The supplied 67-second OnePlus recording confirms partial improvement, with
remaining delays. It shows repeated Library/OnlyFap/Shows navigation during
0–30 seconds, Settings and return around 31–42 seconds, Messages around 43–51
seconds, and Notifications showing its loading label around 56–60 seconds.
These are video timeline observations, not exact input latency or CPU traces.
No physical acceptance or merge approval was supplied.

The first pass tested dispatch with empty notification stores and missed a
populated-data main-thread path. `NativeMainActivity` called
`UpdateInboxStore.unreadCount` directly on Library activation and Activity
resume. Counting decoded, pruned and grouped the complete local inbox, including
creator catalog and aliases. That work could occupy the main thread or wait for
the same lock as notification loading. The Notifications loading label precedes
local history publication; its social refresh starts afterward, so this initial
local-history cost is separate from later network freshness.

The populated-data check uses 200 notification entries, 80 favorites and a 1000
creator catalog. Before: 400 ms and 202 favorite preference reads. After: 69 ms
and 3 reads, with the same 80 groups and all 200 children. These are one controlled
local before/after pair, not statistically stable or physical-device timings.
The bounded-work assertion failed before the change and passed afterward.

Follow-up implementation:

- Read Library badges on the existing IO executor, coalescing queued requests;
  apply results only for the latest request and same account on a live Activity.
- Snapshot favorite names once during pruning, index group aliases once, and
  preserve original first-group ordering and reviewed/manual alias matching.
- Reuse compiled name-normalization patterns with identical normalization rules.
- Group the call's decoded snapshot after releasing the inbox lock. Storage
  decoding/pruning/writes remain under the existing lock. Skip catalog grouping
  when the visible inbox has no creator notifications.

Focused protection includes a deliberately blocked badge reader while tabs and
Back execute, stale-account result rejection, populated inbox work bounds,
notification read/delete/history semantics, merged names, Unicode matching,
notification lifecycle/account isolation and Library scroll retention. The local
follow-up selection passed 59 cases with no failures/errors/skips, including the
blocked-read/account guard and populated-store regression. A later warmed run of
the same fixture printed 20 ms, illustrating why these are workload observations
rather than a device latency promise. Final CI results and the Preview 11 signed
candidate are recorded on PR #409.
The signed workflow now uses `6000013`, above Preview 10 (`6000012`), with the
same production identity and Preview 9 client-key safeguards.

No animation or glass changes accompany this follow-up. Initial remote Messages
loads, media preparation, GPU cost and other unprofiled work can still take time.
The recording supplies evidence for a shared populated-inbox bottleneck; only
new physical testing can establish how much of the remaining delay it resolves.
