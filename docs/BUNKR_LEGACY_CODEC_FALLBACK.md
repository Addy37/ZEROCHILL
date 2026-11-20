# Bunkr legacy video fallback (candidate, not approved for release)

## Scope

- Media3 remains the default player and keeps existing MP4 metadata repair, range/cache behavior, and source recovery.
- Only Bunkr file pages reaching `BunkrGalleryActivity` can launch the internal LibVLC player.
- ASF/WMV extensions launch the fallback directly, instead of guaranteed Media3 error 3003.
- Other non-MP4/non-adaptive sources can launch the fallback only after Media3 error 3003.
- The fallback receives the exact resolved HTTPS media URL and per-video Referer, with no query-string logging.
- The user returns to the same album/episode by closing the fallback. Favorites, history, downloads, app identification, signing and Supabase are unchanged.
- Fallback failure is visible and non-recursive. It does not retry blocked/expired Bunkr CDN responses or silently mark the source healthy.

## Implementation

- `LegacyVideoFormatSupport`: testable Bunkr format/HTTPS routing rules.
- `LegacyVideoPlayerActivity`: isolated LibVLC playback and simple seek/play/pause controls.
- `BunkrGalleryActivity`: optional launch on supported routing conditions.
- `org.videolan.android:libvlc-all:3.7.7` from Maven Central; VLC's Android LibVLC is LGPL-2.1. See https://code.videolan.org/videolan/libvlcjni and https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.

## Release blockers / device validation

1. Resolve dependency and compile debug/release; run `LegacyVideoFormatSupportTest`.
2. Inspect native ABI support, 16 KiB page-size compatibility, dependency licenses and all required redistribution notices. `libvlc-all` AAR contains all ABIs and is about 88 MB before APK packaging; quantify final APK-size increase and upgrade behavior.
3. Confirm the exact reported Bunkr `.wmv` file opens on OnePlus CPH2655 / Android 16 and plays, pauses, seeks, rotates and returns to the same episode.
4. Confirm ordinary Bunkr MP4, seek and resume still use Media3. Repeat a known-good OnlyFap gallery and Shows playback.
5. Confirm blocked, expired and HTML CDN responses show a clear failure without infinite retries or a blank screen. Check return behavior after backgrounding/rotation.
6. Confirm existing package ID/signing, user favorites/history/downloads and update installation.
7. Consider integration with native album chrome only after this standalone fallback has been phone-verified.

This is a candidate implementation, not a claim of successful WMV playback. Do not merge or publish until device and distribution checks are done.
