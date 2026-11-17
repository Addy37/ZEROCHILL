# Five fixes plus polish: second test candidate

The user requested these three additions on the existing PR #450 branch after
personally approving the first APK. This candidate descends from approved app
source `8f219b32da76c99bc6955826d3a786b902bf4302` and approval record `cff34b86`.
All five original fixes remain included. The new polish awaits phone testing.

## Changes

- Bunkr episode titles: remove known video extensions and turn filename word
  separators into spaces. Preserve episode numbers, decimal numbers and ordinary
  prose. Use the display title in episode rows, range searches, the watch page,
  related entries and next-video labels. Original filename is available with Copy
  in the album row's action sheet and the watch page's menu. Source models,
  downloads, saved titles, playback identity and album order are unchanged.
- DM links: tapping a recognized HTTP/HTTPS link shows its complete URL with
  Open link, Copy link and Cancel. Opening uses a browsable HTTP/HTTPS intent;
  Copy link copies only that URL. Long-press Copy message, reactions, reporting,
  attachments and ZEROCHILL video cards retain their previous behavior.
- ShitTok: remove the full-width lower gradient. White titles and creator/source
  text have a thin dark outline and a tight shadow, with a transparent overlay.
  Existing controls, accent colors, Focus Mode, gestures and player ownership stay
  on their current paths. Text rendering does not add a software or bitmap layer.

## Focused checks

74 local tests passed across seven classes:

| Class | Tests |
| --- | ---: |
| ZeroChillMessageThreadUiTest | 20 |
| BunkrAlbumShowsTest | 6 |
| ShowsEpisodeListTest | 19 |
| ShowsWatchPageTest | 12 |
| ShitTokVisualPolishTest (API 35) | 9 |
| ShitTokOverlayTextViewTest | 1 |
| ShitTokFocusModeViewportTest | 7 |

The render test checks outlined multiline text over solid bright and dark frames,
checks that pixels outside the text retain the frame color, and writes previews.
Both previews were visually inspected. The first attempt at the API 36 ShitTok
fixture was blocked by the environment's Maven mirror restriction; the signed
Actions workflow runs that class on APIs 35 and 36. Dialog fixtures were corrected
to drain Android's posted button callbacks before asserting their results.

The existing 85-test baseline and successful first signed run remain documented
in `FIVE_FIXES_TEST_APK.md`. No full suite, broad audit or debug APK assembly is
part of this pass. `git diff --check` and workflow YAML/embedded Python parsing
are the remaining lightweight checks before the single signed build.

## Signed candidate and compatibility

Version name `7.1.1-test.2-polish`, versionCode `7001002`, application ID
`com.addy37.crazyshitunofficial`. The existing encrypted Actions signing bundle
is reused unchanged. The workflow checks production package/certificate identity
against published 7.1.0 and requires a code above the first test APK's `7001001`.
The branch and exact source SHA appear in the APK filename and validation report.

No data migration, dependency, source networking/configuration, authentication,
Supabase/backend, updater publication, public release, tag or merge changes.
One candidate build is triggered by the final push of its branch-scoped workflow;
no unrelated manual workflow is dispatched. PR #450 is updated for the combined
candidate and remains pending approval of the new polish.
