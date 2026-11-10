# PR 412 / Preview 24: Library liquid-glass submenu lifecycle

Baseline: Preview 23 `08588a9c72f8161f03444aeb1e7e7a0ecc9c1d30`.
The user's new recording opens a saved photo normally, then tapping the viewer's
header overflow returns to Favorites. The accompanying screenshot shows the
interim native creator menu; the user explicitly requests the same liquid glass
as other app menus and asks to cover other Library submenus.

## Cause and fix

BunkrGalleryActivity's header uses ZeroChillMenu.show -> ZeroChillComposeMenu.
Previous real creator-grid touch tests reproduced ViewTreeLifecycleOwner not
found from PopupWindow.PopupDecorView on both API 35 and 36. The popup assigned a
local owner only to its ComposeView. WindowRecomposer resolves the popup window
root, which is outside that ComposeView subtree, so the child tag is insufficient.

ZeroChillComposeMenu now installs lifecycle and saved-state owners on the glass
container and, as that parent attaches, on the popup decor root. Android attaches
the parent before its Compose child, so the window owners exist before Compose
creates its composition. Keep existing owner close/unregister, anchor detach,
dismiss, item model/callback and BadToken fallback behavior.

Restore show() for creator avatar actions and saved-collection/Downloads menus,
using the original ComposeMenu plus PreferenceGlassPanel. No recreation or
approximation of the liquid-glass shader/style. Convert the creator header's
single Fetch missing artwork menu from an AlertDialog list to the same anchored
glass menu; its existing artwork action and progress/result dialogs are unchanged.

The viewer benefits from the shared host repair without viewer/player/gallery
logic changes. Normal media opening and full-screen controls are preserved.

## Library submenu inventory

| Entry | Coverage / behavior |
| --- | --- |
| Saved-photo viewer header overflow | Actual viewer opens, composes, dismisses without returning or losing its item |
| Favorite Creators avatar overflow | Actual grid touch, live glass composition, avatar picker route |
| Merged creator overflow | Members, primary, unmerge, avatar, default and remove actions retained |
| Favorite Creators header | Fetch missing artwork uses the shared glass host |
| History / Continue item actions | Existing delete/remove labels in a live glass host |
| Watch Later / Favorites item actions | Existing remove/unfavorite labels in a live glass host |
| Saved collection header menus | Existing clear actions in a live glass host; confirmation remains separate |
| Downloads | Play / Retry / Resume / Pause and Remove retained for each download state |
| Liked Videos | Refresh / Load more; no overflow menu exists on this screen |
| Viewer media context / merged-member sheets | Separate existing native overlay/sheet paths; existing action tests included |

The shared popup repair also applies to existing non-Library callers. It changes
only window ownership, not their menu actions or styling. No broader navigation,
layout, data, source, remote-config, Supabase or preference changes.

## Verification and candidate

Test live Compose attachment and semantic actions, not just native view callbacks
or MenuBuilder contents. On API 35/36 check window lifecycle/saved-state owner
identity, active composition, hidden items, repeat opens, callback isolation,
dismissal and anchor-detach destruction. Actual creator tests retain picker and
default-avatar behavior. Actual saved viewer regression preserves the Activity
and current item. LibrarySubmenuTest covers real menu builders across saved
routes and Downloads states. Existing glass, creator, context menu, header inset,
thumbnail, mixed-grid and Library regressions are included.

Robolectric cannot establish physical hardware lens appearance or OxygenOS
acceptance. Inspect the signed APK on the user's OnePlus: viewer header overflow
stays open, avatar options use liquid glass, other Library overflows open/dismiss,
Back works and destructive actions still follow existing confirmation behavior.

Preview 24 / code 6000026 builds one signed release APK, preserving production
application ID/signing identity. CI gates lineage, package, certificate and upgrade
version. The PR records actual results and artifact links. Do not merge or publish
a release; stop at the APK for physical testing.
