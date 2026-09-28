# ZeroChill 4.1.1

ZeroChill 4.1.1 is a focused ShitTok performance and playback-quality update.

## ShitTok

- Fixed the current video restarting after switching to another tab and returning.
- ShitTok now restores the current clip close to the position where you left it during the same app session.
- Rebalanced the feed to favor Kaotic, Shit Show, and OnlyFap content.
- Reduced EFukt presence in the normal ShitTok mix.
- Removed Bunkr from ShitTok because the source is not stable enough for the swipe feed. Bunkr remains available elsewhere in ZeroChill.
- Keeps a bounded upcoming queue warm so more content is ready before you swipe to it.
- Improved off-tab queue warming without keeping background video players alive.

## Swipe performance

- Significantly reduced the visible hitch that could occur while swiping between ShitTok videos.
- Moved expensive player preparation and cleanup out of the active swipe animation.
- Player cleanup now waits until the pager is idle and is staggered instead of stacking decoder teardown in one frame.
- RecyclerView recycling no longer synchronously destroys video players while the feed is moving.
- Upcoming warm players are prepared one at a time instead of in a burst.
- Preserved the existing selected-plus-next-two preload window and media cache.

## Compatibility

Existing installs keep the same application ID and signing identity. Favorites, creator merges, custom avatars, Library data, watch history, Watch Later, downloads, backups, and persisted settings remain intact.

This release supports an in-place upgrade from ZeroChill 4.1.0.
