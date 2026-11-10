# OnlyFap gallery artwork and V2 media controls

The user physically approved Preview 29 and explicitly authorized merging PR #413.
It was merged at `8e8c7c55a84328db61e6de8f92667f8444bfecdf` after its signed
candidate passed. This new candidate starts at that exact production main commit
on `feature/zerochill-onlyfap-gallery-polish`. It is a separate draft PR for the
new requests; no further merge, tag or release is authorized.

## Requested surfaces

- Creator gallery artwork begins directly beneath the status bar. Remove its
  redundant OnlyFap title and overflow toolbar; keep an accessible Back overlay
  and favorite access when the hero collapses. Increase the image viewport from
  184dp to 280dp rather than keeping a separate 56dp black title strip.
- Randomly rotate photos from the same creator's already-loaded gallery at the
  existing OnlyFap 13-second cadence. Use listing originals when available, no
  video frames, extra provider crawling or unrelated creators. Keep current pixels
  until the replacement is ready, retain independent Glide owners through any
  bounded fade, and cancel safely on pause, detachment and destruction.
- Adapt actual V2 media controls: photo close/count/favorite/menu pills and a
  compact video dock with thin accent timeline, title/count, playback, favorite,
  fullscreen and menu actions. Preserve native playback, gestures, advanced
  settings, sharing, downloads, saved creator links and favorite actions.

## Reference and implementation

Reference is ZEROCHILL-V2 main `9ce121f`, specifically
`app/.../OnlyFapViewerChrome.kt`, native `GalleryViewerChrome.java`, its
`BunkrGalleryActivity` integration and `FullscreenVideoGestures` hooks. V2 uses
Compose GlassCard and its separate backdrop library. OG adapts the geometry and
controls using existing native Views and static glass optics, honoring accent,
transparency and rim settings without adding dependencies or recording playing
video/Media3 trees. Native advanced controls remain available through the menu. Wide landscape keeps
the thin timeline above the action row rather than V2’s optional inline timeline.
The shared native media viewer also serves Bunkr albums, so the visual dock is
shared there; content routes, player engine and source headers remain unchanged.

Creator header chrome owns its geometry rather than the delayed generic browser
header restyler. Root responsive safe insets and width fitment remain; the generic
landscape toolbar-height override skips this image-led creator surface. Noncreator
browser headers and Shows remain on their existing paths.

## Compatibility, validation and limits

Production application ID, signing identity, preferences, saved items, backups,
creator identity, filtering, media ordering, source pagination, playback recovery,
downloads and backend configuration remain. No migration, Supabase or remote-config
change. Preview 30 uses `6000032`, above approved Preview 29 `6000031`, signed with
the existing production key. One scoped candidate workflow verifies ancestry,
package, stable certificate and upgrade version, and exports a branch/SHA-named
APK. This does not authorize a public release.

Local validation passed: 34 focused classes / 344 tests in each of debug and
release (688 total), plus `:app:assembleDebug` and `git diff --check`. This covers
hero Glide ownership, stale/failure results, random rotation, reduced motion,
status-bar/header geometry, menu lifetime, interrupted seeks, gestures, gallery
returns, preference persistence, appearance, shared Shows controls and playback
recovery. Initial compile/fixture timing failures were fixed before the final
successful run. The signed release build and package/certificate/version gates
are recorded in the candidate workflow and PR handoff. Full unfiltered tests and
lint were not run for this scoped candidate.
Physical OnePlus Android 16 acceptance of crop, rotation, overlay position, seeking,
fullscreen, glass readability and 120Hz smoothness remains pending. Rotation uses
only a bounded set of the creator's loaded photos, not a new scrape of the archive.
Native rendering is intentionally used instead of importing V2's backdrop package.

## Changed files

- `CreatorProfileHeader.java`, new `CreatorGalleryHero.java` and
  `NativeFeedBrowserActivity.java`: image-led header, overlays and rotation.
- `OledImmersiveUiController.java`, `ResponsiveFitmentController.java`: preserve
  creator-owned image geometry while retaining safe insets.
- `BunkrGalleryActivity.java`, `BunkrGalleryPagerAdapter.java`,
  `FullscreenVideoGestures.java` and new `OnlyFapViewerChrome.java`: native V2 dock,
  same-player actions and gestures, artwork readiness and lifecycle cleanup.
- `ZeroChillMenu.java`, `ZeroChillComposeMenu.kt`: optional dismissal callback so
  controls remain reachable while menus are open.
- New `CreatorGalleryHeroTest.java`, `CreatorGalleryHeaderLayoutTest.java`,
  `OnlyFapViewerChromeTest.java`, `OnlyFapViewerGesturesTest.java`, and updated
  `ZeroChillComposeMenuTest.java`: focused regression coverage.
- `.github/workflows/onlyfap-gallery-polish-candidate.yml`, this document and
  `ZEROCHILL_BUILD_LINEAGE.md`: signed test candidate and lineage record.
