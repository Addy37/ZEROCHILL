# OnlyFap creator gallery media favorites

The existing StaggeredGridLayoutManager and creator pinch controller remain in use.
Column limits, sensitivity, grid morph, release duration, and saved column preferences
are unchanged. Adaptive tiles use source aspect-ratio hints when present, otherwise
the already bounded Glide thumbnail decode supplies dimensions. Ratios stay cached in
the retained adapter. No source probing or full-resolution thumbnail decode was added.
Unknown dimensions initially use the existing square placeholder. Source artwork that
already includes borders cannot be corrected without different source artwork.

Long press opens Media actions: Open, Favorite/Unfavorite, Share, Copy Link, Download.
The menu reuses PreferenceGlassPanel and the navigation lens shader, with its older
Android fallback. It samples only the underlying gallery, excluding the overlay.
An outside tap or Back closes it. A second finger closes it and forwards the start
of the gesture to the page. Haptics respect the existing app preference. The fullscreen
viewer also uses explicit actions; its existing download buttons still download.

FavoriteStore retains its existing watch_later preferences and JSON array. Optional
mediaFavorite, watchLater, and galleryKey fields keep media favorites independent from
legacy Watch Later membership. Missing flags represent the original Watch Later item.
Records retain kind, source URL, artwork, creator context, and aspect hint. Favorites
never initiate downloads. No creator-follow or social-like mutation is involved.
Backup export/restore retains these optional fields, validates image metadata, and
continues to read old backups. Older app versions may reject new image-containing
backups rather than importing them as videos.

Library uses its existing snapshot/rail architecture for a Favorites rail and the
existing saved-items destination in Favorites mode. Saved images open through the
existing gallery viewer, including from Library search. Saved videos use the same
source resolver. Watch Later remains a separate membership in the same store.

Creator favorites form a stable partition ahead of normal source order. Saved media
from the matching gallery key can appear before its source page has loaded. When that
page arrives, its source position replaces the provisional position. Removing a
favorite restores known source order. An item whose source page has not loaded yet
remains after known source rows until its position becomes available. Move updates
retain a visible unaffected anchor and animate existing tiles rather than rebinding
all artwork. Normal pagination that does not change order does not cancel pinch motion.

CI runs focused persistence, backup, sizing, explicit-action, menu-dismissal and gesture
handoff tests, existing motion/viewer/Library regressions, and the full Android suites
and builds. The performance workflow uses the existing creator-gallery playback flow
for this branch. Physical-device validation is still needed for pinch feel, live media,
scroll/move animations, glass rendering, haptics and sharing destinations.
