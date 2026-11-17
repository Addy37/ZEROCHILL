# Five fixes physical-test candidate

Branch: `feature/zerochill-five-fixes`. Baseline: fetched `main`
`8964653bb5a25948d587d477077e8a13162548b0`, including released 7.1.0.
Historical `rebrand/zerochill` (`2f9a489a`) is an ancestor of this main.
The artifact's `VALIDATION.txt` records the exact source SHA.

## Changes

- DM text: HTTP/HTTPS spans use the active accent; original message bodies,
  attachments and video-card actions are retained. Other URL schemes are excluded.
- Copy: long press shows a labeled Copy message button beside reactions, in
  addition to the selection toolbar. Both copy the complete stored text.
- Comments: one IME inset owner with `ADJUST_NOTHING`, instead of combining
  resized-window inference and keyboard padding. Previous sheet expansion is retained.
- Shows: register Android USER orientation with the shared lifecycle policy so
  activity callbacks cannot restore the portrait browsing lock. Android observes
  Auto Rotate; explicit fullscreen still requests landscape with rotation locked.
  The existing custom sensor remains scoped to other playback surfaces.
- Bunkr albums: reuse the Shows hero, numbered rows, ten-episode ranges and
  incremental paging. Keep mixed files in the existing album session; only videos
  become episodes. Pictures opens the filtered existing image viewer. Album next
  playback uses source order, without interpreting numbers in filenames or looping
  at the end. Watch-page paging extends that session, including beyond the compact
  Intent snapshot limit. Ordinary OnlyFap creator galleries retain their UI.

The pictures viewer advances its album cursor using all source files, so a
video-only source page cannot prematurely finish the shared album. Watch-page
prefetch pauses after three consecutive pages without another video and offers
Load more album videos; it does not crawl an entire image-heavy album unattended.

## Focused validation

Local `:app:testDebugUnitTest` passed these 85 tests (11 classes):

| Class | Tests |
| --- | ---: |
| ZeroChillMessageThreadUiTest | 18 |
| InlineCommentsSheetTest (API 35) | 5 |
| InlineCommentsUiTest (API 35) | 9 |
| PhoneOrientationPolicyTest | 7 |
| ShowsWatchPageTest | 11 |
| ShowsWatchSessionTest | 3 |
| ShowsEpisodeRangesTest | 3 |
| ShowsEpisodeListTest | 19 |
| BunkrAlbumShowsTest | 4 |
| BunkrGalleryMediaGuardTest | 2 |
| ShitTokOrientationTest | 4 |

The first comments attempt was blocked before execution by the Android 16
Robolectric jar redirecting to a mirror denied by this environment. Retrying an
allowed Maven entry point reached the same blocked redirect. Local comments tests
then passed with cached API 35. The single candidate Actions workflow runs the
two comments classes on APIs 35 and 36, followed by one release APK build; its
XML results are included in the artifact. A new paging fixture was corrected to
drive pager posts explicitly because its viewer is not attached to a window.

No full suite, debug APK assembly, lint audit, or live source/backend test was run.
`git diff --check` passed. The referenced recordings were not present in the
workspace; physical keyboard, sensor and source playback acceptance remains pending.

## Identity and scope

Candidate version: `7.1.1-test.1-five-fixes`, versionCode `7001001` (workflow only).
The signed workflow checks the production package and certificate against the
published `v7.1.0/ZeroChill.apk`, and requires a higher versionCode than 7001000.
The application ID remains `com.addy37.crazyshitunofficial`. Signing uses only the
existing encrypted Actions bundle. No signing secret, preference/database schema,
authentication, backend deployment, source networking/configuration, migration,
public updater metadata, tag, merge, or GitHub Release change is part of this work.
The existing New Zerochill endpoints and disabled analytics remain unchanged.

One branch-scoped workflow is triggered by the final push containing its workflow
file. Subsequent app-only pushes do not trigger it. No PR is opened during this
candidate build, to avoid unrelated automatic PR workflows. Phone approval is
required before any later merge or release.
