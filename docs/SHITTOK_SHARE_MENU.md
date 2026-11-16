# ShitTok central share menu beta

Baseline: `main` at `605e85c7c58a95bb8fd91c3921040f10f472ae50` (7.0.5).
`Addy37/CrazyShitAndroid` now resolves to `Addy37/ZEROCHILL`. The historical
`rebrand/zerochill` branch is an ancestor of this complete approved lineage.
Feature branch: `feature/shittok-share-menu`. No merge or public release is authorized.

## Android behavior

The portrait and landscape Share buttons and their long-press actions open the same
OLED-black Material bottom sheet. It uses the selected accent, rounded upper corners,
native drag/dismiss animation, a nested vertical scroller and horizontally scrolling
action rows. System-bar and cutout insets pad the panel.
Opening it does not resize or replace the media viewport or prepare/restart a player.

The menu contains exactly seven actions: Share Video, Copy Link, Auto Scroll,
Not Interested, Block Creator, Report and Details. Download, Favorites (Watch Later)
and Comments retain their existing controls outside this menu. Creator blocking
requires confirmation and preserves saved data. Settings retains the existing unblock
screen. Auto Scroll saves its ON/OFF setting in the existing feed preference file; menu,
confirmation/report and comments overlays suppress automatic paging. A finishing
video loops while an overlay is open instead of moving the item under the user.

## Actual external video sharing

Share Video uses Android's native chooser for WhatsApp, Messages and other installed
receivers. Apps may impose their own format/size limits.

Preparation runs only on an explicit file-share request. It first reuses a prior
prepared file, then a completed download, otherwise the selected resolved stream
or the existing `PlayableSourceRouter`. The same Media3 data source and playback
disk-cache keys/headers are used for progressive media. Cached playback spans are
read locally and missing bytes are requested on demand; no whole-feed downloading.

Validated MP4 and WebM files containing a video track are supported, with a 100 MiB
per-video limit and a 300 MiB share-directory budget. Manifests (HLS/DASH/SmoothStreaming),
HTML, unsupported containers, failed/truncated transfers, detected DRM and unavailable
sources fail visibly. There is no remuxing, restriction bypass or webpage fallback.
The source must already allow access through the ordinary playback/download pipeline.

The receiver gets `ACTION_SEND`, `video/mp4` or `video/webm`, `EXTRA_STREAM`, ClipData
and `FLAG_GRANT_READ_URI_PERMISSION`. The FileProvider is not exported and exposes
only `cache/shittok_share/`; it does not expose favorites, downloads or playback-cache
directories. Copying a completed download creates only a bounded temporary export.
The original is never changed. Cancellation closes the active input and removes the
partial export. Preparation has a three-minute budget checked during transfers.

Completed exports are reused by page identity and retained for 48 hours after their
most recent preparation/share, so dismissing the sheet does not delete files that
receivers may still be reading. Old exports are removed lazily on the next share.
Recent files are not evicted to make room; a full budget asks the user to retry later.
Android may independently reclaim cache storage. Long-term archiving belongs to
Download, not the temporary share directory.

Copy Link is explicitly a separate source-link action. The only current app deep
link is an authentication callback; no video route/scheme is invented.

## Final compact menu (beta 4)

SHARE contains Share Video and Copy Link side by side. VIDEO ACTIONS contains
Auto Scroll, Not Interested, Block Creator, Report and Details in one horizontal
row. There are no WhatsApp, Messages, More Apps, Download, Favorite or Comments
menu controls. The sheet sizes to its content, capped at 65% of screen height;
short screens scroll vertically. Rows scroll horizontally at narrow widths and
large font scales, with labels allowed to grow vertically. The five actions fit
at normal font scale on a 384dp phone. OLED black, accent colors, rounded styling,
insets, native animations and file-preparation feedback remain.

Auto Scroll toggles in place with visible ON/OFF and an accent-filled enabled
icon. Creator blocking checks both original metadata and the resolved identity
used by the avatar. A newly resolved identity updates the open menu. Blocking
uses the same persisted name keys and targeted queue removal; unidentified clips
remain disabled. Existing saved creators, downloads and history are untouched.

## Auto Scroll replay ownership

Previously a retained ended player reached `prepareMeasured` and was treated as
a new completion before the detached, delayed replay helper could seek it. The
feed now rewinds a completed lease synchronously at settled selection, before
playback permission and UI completion checks. Retired completed leases remember
position zero, while partial watches retain their positions. Player engines,
surfaces, pool limits and preparation scheduling are retained. The obsolete
90ms view-tree replay helper is removed.

Completion UI work captures its page visit and rejects work queued before a
manual drag or selection change, including A → B → A. A completion must still be
STATE_ENDED on the active holder. Existing generation cancellation and one-issued
advance gates reject stale transitions and duplicate endings. Manual navigation
keeps Auto Scroll enabled. Focus chrome and swipe handling remain on their
existing paths. Regression tests cover retained and retired replay, partial
position restoration, post-replay advance, duplicate/stale callbacks, rapid
navigation, manual cancellation, toggles, saved setting and Focus behavior.

## Validation and delivery

The scoped candidate workflow uses the existing encrypted `ANDROID_SIGNING_BUNDLE`,
preserves `com.addy37.crazyshitunofficial` and the production certificate, and builds
`7.0.6-beta.4-replay-share` / `7000008` to upgrade Beta 3 and 7.0.5. The workflow checks out the
exact feature source, runs Debug/Release tests and builds, verifies lineage/signing,
current backend routing and APK contents, and uploads a branch/SHA-named APK plus
checksum and validation report. It never creates a release/tag or merges a PR.

Beta 2 also fixes the ordinary feed controls resurfacing after a Focus Mode swipe.
The settled entrance animation now respects the same chrome-suppression predicate
as the normal control-show paths, including incoming holders prepared for Focus.
Forward/reverse swipe and late-animation regressions verify hidden chrome until
explicit Focus exit, unchanged media surface/ownership and normal entrance after exit.

Completed test/build counts and artifact links are recorded in the PR. Automated
media fixtures validate export bytes, provider access and caching but use Robolectric's
registered-track MediaExtractor shadow; they do not establish real Android demuxing,
WhatsApp/MMS acceptance, DRM handling on a physical device or live provider permissions.

Physical acceptance still needs an Android device with compatible receiving apps:
open/dismiss/swipe sheet repeatedly, share supported videos and play the received
files, cancel/retry, test downloaded/cached/offline cases and unsupported streams,
verify existing DM access, verify Auto Scroll and creator blocking across restart,
check favorites/downloads/history, inspect small/landscape layouts and system insets,
and measure live playback/swipe/memory behavior. Report only completed checks.
