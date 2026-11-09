# Navigation and Back responsiveness candidate

## Baseline and evidence

Started from PR #408 head `de2117512531bea2f384d8faaf231993caef95e1`.
PRs #407 and #408 were both open, draft and unmerged when verified.
The performance PR targets `feature/zerochill-7-shared-chrome-phase4`.

Three focused before-change Robolectric checks reproduced the targeted defects:

| Observation | Before | After (executed) |
| --- | --- | --- |
| Tab handler returns with pager state | SETTLING (2) | IDLE (0) |
| Header title writes for one Library selection | 2 | 1 |
| Search close callback complete in Back dispatch | false | true |

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
  Activity finish retain their priority. Player/overlay Activity handlers are
  unchanged. Gesture fidelity requires physical testing.

## Validation

Focused checks cover immediate tab/Back selection, one chrome bind, retained
page identity, final Activity finish, Search priority/early close, Library scroll
retention, coalesced primary work, fake-drag/insets, and ShitTok audio/search
ownership. Local scoped run: 47 tests passed, zero failures/errors/skips.
The additional integrated Search-before-Library-Back check then passed separately
(48 focused cases total). After-change probe output: tab state 0, Back state 0,
header title writes 1. The new tests reproduced three failures on the original
application source before the fix. Signed workflow evidence is recorded on the
PR after execution. Full repository suites and emulator build matrices are
intentionally excluded.

Local setup initially hit a missing test import and a read-only Robolectric
home lock; corrected the import and ran the JVM with a workspace user.home.
Neither is an application regression.

## Compatibility and remaining risks

No dependencies, provider routes, persistence formats, preference keys, account
code, backend, auth, downloads, favorites, history, backups, signing setup or
application ID changed. The workflow preserves the Preview 9 publishable key
and verifies it is present in the APK's DEX without logging the value.

The visible behavior change is immediate tap/Back destination changes and Search
exit. Navbar dragging remains animated. Main-thread first-time construction,
image decoding, player resumption, network responses and GPU glass cost can
still affect responsiveness. No physical-device speedup or Android 16 gesture
acceptance is claimed. Test rapid taps, Library/OnlyFap scroll retention, Search
IME-first Back, fullscreen exit, overlay dismissal, playback continuity, and
in-place upgrade on the OnePlus before approval. Do not merge or release.
