# PR 412 / Preview 21: Library header inset ownership

Baseline: Preview 20 `f61b8578e9a90dfa385d49f914d3c5a7b85b7c6c`, a descendant
of approved main / merged PR 411 `b5020604`. Candidate version code `6000023`.
No merge, tag or release; physical OnePlus Android 16 acceptance is pending.

## Cause

The window/theme comparison found the destinations are ordinary Activity classes,
use the same no-action-bar theme, and explicitly opt into edge-to-edge with
`WindowCompat.setDecorFitsSystemWindows(false)`. No destination sets fullscreen
flags. BrowseUi.screen already owns combined system-bar, cutout and IME padding.

The missed path is CrazyShitApplication.onActivityResumed ->
UiFoundationCoordinator.onActivityResumed -> ResponsiveFitmentController.applySoon
(immediate, 120ms and 360ms posted fitment). For FavoritesActivity only, that pass
called installSafeInsets. It captured the root's **already inset** padding as
baseTop/baseBottom, then replaced the root listener and added system-bar/cutout
insets again. Its default includeIme=false also discarded keyboard padding.
All History, Favorites, Watch Later and Continue routes use FavoritesActivity.
Downloads never matched that fitment branch and retained BrowseUi's listener.
This explains both the working Downloads comparison and why root-only synthetic
inset tests missed the defect. Preview 20's cap was also overwritten later.

CreatorsActivity and LikedVideosActivity do not enter that duplicate-inset path;
their Preview 20 cap was an unnecessary workaround and could under-protect a real
large camera cutout. There is no evidence that their device spacing had the same
cause as History.

## Fix and scope

Keep Favorites' existing single-column width fitment but remove its redundant
inset installation. Use BrowseUi.screen for all four destination Activity classes;
remove compactRoot and its arbitrary 24dp minimum / 42dp maximum. Keep the existing
6dp header margin and edge-to-edge window setup. Downloads' implementation stays
unchanged. Real cutout and status-bar values combine by maximum, not sum; the root
continues to combine navigation and keyboard insets by maximum.

No changes to galleries, thumbnails, playback, data, menus, routing, Back,
appearance settings, remote config, Supabase, package or signing identity.

## Validation

LibraryDestinationInsetsTest attaches the four real Activity types without
starting networking/stores/thumbnail workers, mounts the shared root/header and
runs the delayed resume-time fitment. It measures header coordinates after normal
insets, repeat fitment, IME show/hide, zero top insets and a genuine large cutout.
The regression reproduced on the old fitment implementation on both API 35 and
36: `FavoritesActivity expected:<60> but was:<120>`. Tests use a Java 21 worker
for API 36 while APK compilation remains Java 17. This is a lifecycle/geometry
regression, not a physical OxygenOS or emulator test. Focused chrome tests retain
callback/input/card coverage and check uncapped cutout safety.

The scoped candidate workflow runs Library/menu/mixed-grid/appearance checks and
builds exactly one signed release APK using ANDROID_SIGNING_BUNDLE. Gates compare
the production package and certificate with the current stable APK and require a
version above Preview 20. The APK filename includes branch and source SHA; CI
exports checksum and validation evidence. Consult the successful run for results.

Physical checks: compare History/Favorites/Watch Later/Continue/Downloads/Creators/
Liked Videos header placement, camera clearance, search keyboard show/hide, menu
opening and Back, with populated data. Keep PR 412 draft until physical approval.
