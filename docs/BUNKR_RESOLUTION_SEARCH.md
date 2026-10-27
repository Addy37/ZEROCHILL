# Bunkr playback, artwork and Search repair

Baseline: main `873646b74f0a18d5c4d99491b72970a3856cfeed`, including merged PRs #350 and #352.

## Evidence

Device recording 13893.mp4 shows a stalled OnlyFap selection, then the working website file
`https://bunkr.cr/f/Fuyd9dOX3ZbhP`. Its album is `/a/WlA5Bbyp`.

The workspace and cloud browser returned an environment-level Site Unavailable page.
Read-only GitHub Actions diagnosis runs 40497064518 and 40497259672 reached the actual site.
The file still exposes jsCDN and signUrl. Its JavaScript decodes the CDN pathname, signs
with credentials omitted, and sets token/ex while preserving other query parameters.
Fresh signing from both configured page origins returned HTTP 206 and MP4 `ftyp` bytes,
with both native Origin/Referer and browser-style Referer requests. No full video was downloaded.
No domain, resolver host, CDN, header allowlist, retry or timeout drift was established.

## Confirmed defects

- Current album DOM places `/images/video.svg` before the raster thumbnail. The old parser
  selected the first image. Glide cannot render that SVG through this raster path.
- After a signing failure the script media regex matched the `.mp4` prefix of
  `videoCoverUrl = ...mp4_grid.png`, returning a nonexistent thumbnail-derived media URL.
  It could also return the unsigned jsCDN value even though that page requires signing.
- Gallery session URLs were reused without inspecting the signed URL's expiry. A warm
  session could therefore attempt an expired token before playback recovery.
- Signing dropped unrelated CDN query parameters rather than matching URLSearchParams.set.

The fresh live stream succeeds. These defects explain concrete failing code paths, but the
recording contains no player exception or request log. It does not establish which path caused
that particular stall. Device confirmation remains required. This is not a claim that all
intermittent playback failures have been reproduced or eliminated.

## Changes and bounds

The resolver validates complete media URL tokens, preserves the signing contract and legacy
API/direct-source fallbacks, and the gallery drops expired signed URLs before reuse.
Artwork parsing skips SVG type icons. Failed/missing visible Bunkr tiles can repair from file
social artwork, poster or the site's grid cover. Two workers, 24 pending keys, single-flight
requests and a 256-entry cache bound that work. Positive results cache for one hour; failures
for five minutes. Gallery image loading retains Glide disk caching and preload behavior.

Search adds Bunkr after OnlyFap, using searchAlbums and existing native album/gallery routing.
A separate search worker isolates slow Bunkr requests from the other scopes. Generation checks,
cancellation, snapshots and recent-search handling use the existing Search implementation.
The old `scope=bunkr` creator shortcut alias remains OnlyFap for compatibility; the new album
scope persists as BUNKR_ALBUMS. No new gallery or search design is introduced.

## Impact

No application ID, signing, user-record format, migrations, content membership, backend,
Supabase or remote configuration changes. No source is hidden or removed. No new dependency.
The opt-in BunkrLiveContractTest resolves the recorded file through the Android router and
reads 32 MP4 header bytes, then checks album artwork metadata. This is a source contract,
not physical-device playback testing.
