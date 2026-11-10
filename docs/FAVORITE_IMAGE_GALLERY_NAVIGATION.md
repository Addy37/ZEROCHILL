# PR 412 / Preview 25: creator navigation from favorited images

Baseline: Preview 24 `79bf8414fcfee883d76c839408bfc6170ba2a72d`.
The user requests Open gallery / Remove from favorites instead of dead item/album
pages when viewing a favorited image, plus tapping the creator name to open the
creator gallery. Keep the approved liquid-glass host, gallery and header layout.

## Cause and behavior

SavedGalleryMedia previously opened a singleton media session with the image URL
also supplied as album URL and discarded FavoriteStore.Item.galleryKey. The
ordinary viewer overflow consequently offered Open item page / Open album page,
either of which could open an unusable media/CDN URL as a webpage. It also treated
the creator labels as non-interactive titles.

The Favorites destination and Library home Favorites preview now pass the complete
saved entry. For saved favorite images, resolve the native creator destination
once at opening and carry it in internal Intent extras. A matching stored gallery
key reuses CreatorGallerySpec, including manual-merge cache identity, member seeds
and the existing cached-session/navigation path. Otherwise reuse retained creator
metadata and exact known profiles (including OnlyHaven), WikiFeet profile metadata,
or a saved single-creator query. Never use an unresolved merged hash or guess a
creator from an opaque image URL. No new source endpoints or parsing rules.

The saved-image viewer shows Open gallery and Remove from favorites. The header
and footer creator labels open the same native creator destination and retain it
on recreation. Remove from favorites uses the existing membership operation and
returns to the previous screen; a separate Watch Later bookmark is preserved.
Library Favorites card names and card overflow also offer the native gallery.
Card-name hit areas use a transparent 48dp minimum without changing tile geometry.

Without usable creator context, Open gallery is disabled and viewer labels are
not clickable. Such records still support removal; no dead webpage is substituted.
Ordinary non-favorite viewers and saved video viewer actions retain their existing
menus. Existing bookmarks, creator metadata and backup formats are unchanged.

## Validation / candidate

FavoriteImageNavigationTest runs on API 35 and 36. It covers actual glass menu
selection, both viewer creator labels after recreation, card title/overflow,
Fapello and OnlyHaven profile routing, merged cache key/member seeds, WikiFeet,
legacy single-creator queries and missing-context handling. Removal preserves
Watch Later and unrelated favorites. Existing metadata, storage/backup, gallery,
header/inset, thumbnails and menu regressions are included in the scoped suite.

All image fixtures are synthetic; no source fetch or physical creator-gallery
availability is claimed by these tests. Physical checks should use an existing
favorite image, Open gallery from menu and name taps, Back to the image, then
removal while checking independent Watch Later membership as applicable.

Preview 25 / code 6000027 uses one signed release APK with original production
package/certificate, approved ancestry and an upgrade version above Preview 24.
See PR 412 for actual test/build and artifact evidence. Do not merge or publish
a release. No backend, Supabase, remote-config, storage migration, player or
thumbnail fitting change is part of this candidate.
