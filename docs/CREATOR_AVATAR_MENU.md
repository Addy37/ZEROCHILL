# PR 412 / Preview 23: Favorite Creators avatar options

Baseline: user-approved Preview 22 `b4de0fccde70683beb0bf97d3196bd15cfc8ae7f`.
The user supplied a recording and clarified that the per-creator avatar's three-dot
menu does not open. Their clarification scopes this fix to Favorite Creators;
no Favorites media-card or gallery change is part of this candidate.

## Reproduction and cause

The real CreatorsActivity adapter binds each avatar's more button to showMenu.
Unlike FavoritesActivity and DownloadedActivity, showMenu still called
ZeroChillMenu.show(), selecting the Compose PopupWindow host. A new test seeds a
creator, attaches the actual grid, dispatches DOWN/UP through the real item at the
more button's coordinates and drains the delayed click/layout work. On both
Robolectric API 35 and 36 the old code throws:

    java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from
    android.widget.PopupWindow$PopupDecorView

The stack runs through WindowRecomposer and AbstractComposeView attachment. The
Compose content's local owner does not satisfy the popup-window recomposer's
root lifecycle lookup on this standard Activity path. This is a reproduced host
failure; the physical recording itself contains no crash stack.

## Fix

CreatorsActivity.showMenu now calls ZeroChillMenu.showNative(), the existing
native, theme-tinted anchored Library popup. Keep the global Compose popup
implementation and other callers unchanged. Existing MenuItem callbacks still
own Change avatar, Use default avatar, merged members/primary/unmerge and remove.
The header Fetch missing artwork dialog is a separate action and is unchanged.

No avatar editor, image crop, thumbnail/header/gallery change; no favorites,
merge, storage format, navigation, backdrop, appearance settings, performance,
Supabase or remote-config change.

## Verification and candidate

FavoriteCreatorsScrollTest adds API 35/36 coverage for real touch dispatch, native
popup opening, Change avatar routing to the existing picker, dismiss without
navigation/data changes, merged option availability, and Use default avatar
without deleting favorites or unmerging the group. Run existing creator grid,
avatar override, merge, artwork-fetch and focused Library regressions.

The scoped candidate workflow builds one signed release APK, Preview 23 / code
6000025, with the original production application ID and signing identity. Gates
verify lineage, package, stable certificate and a version above Preview 22.
The PR's latest candidate record links actual results and artifact evidence.

Physical test: Favorite Creators -> any avatar's dots -> menu opens and stays on
that screen; Change avatar opens the picker; cancel returns correctly. For a
custom avatar, Use default avatar resets only artwork. For merged creators,
member/primary options remain available. Do not merge or publish a release before
physical acceptance; no global Compose/menu rewrite is included.
