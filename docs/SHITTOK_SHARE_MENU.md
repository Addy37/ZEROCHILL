# ShitTok central share menu beta

Baseline: `main` at `605e85c7c58a95bb8fd91c3921040f10f472ae50` (7.0.5).
`Addy37/CrazyShitAndroid` now resolves to `Addy37/ZEROCHILL`. The historical
`rebrand/zerochill` branch is an ancestor of this complete approved lineage.
Feature branch: `feature/shittok-share-menu`. No merge or public release is authorized.

## Android behavior

The portrait and landscape Share buttons and their long-press actions open the same
OLED-black Material bottom sheet. It uses the selected accent, rounded upper corners,
native drag/dismiss animation, a nested vertical scroller and horizontally scrolling
recipients/external apps. System bars, cutout and keyboard insets pad the panel.
Opening it does not resize or replace the media viewport or prepare/restart a player.

Download and Favorites reuse the current implementations (Favorites is the existing
Watch Later store). Not Interested, Auto Scroll, creator blocking, Comments, Video
details and the existing playback/problem report remain accessible. Creator blocking
requires confirmation and preserves saved data. Settings retains the existing unblock
screen. Auto Scroll keeps its existing preference and completion policy; menu,
confirmation/report and comments overlays suppress automatic paging. A finishing
video loops while an overlay is open instead of moving the item under the user.

## Actual external video sharing

Share Video and More Apps use Android's chooser. WhatsApp targets `com.whatsapp`;
Messages targets the default SMS package. Missing/incompatible apps show an error
and allow another selection. Apps may impose their own format/size limits.

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

## ZEROCHILL DMs: existing backend contract

The reviewed `direct_messages` contract has a 2,000-character body and private
photo/GIF attachments. `SocialMedia.Prepared`, the upload Edge Function and Storage
buckets support images, not video. This change adds no bucket, migration, service,
database column, policy or function deployment. Permanent uploaded video attachments
would need a separate approved size/retention/access-control/backend design.

Instead, this beta sends a versioned video card in the existing authenticated DM body:

```text
ZEROCHILL video · v1
{"page":"https://<supported-provider>/<content-page>","title":"Video title"}
```

The client validates supported HTTPS source identities, rejects credentials/local URLs
and limits the body to the existing contract. It does not send session tokens, cookies
or the resolved CDN media URL. Recent contacts use the existing inbox API once per
opening, bounded to 12 rendered contacts; recipient lookup uses the existing bounded
username search on explicit submission. Sending retains the same UUID on retry, using
the repository's existing idempotency/reconciliation behavior and account checks.

The conversation renders a play card; tapping resolves media using the recipient's
normal source access, then opens the existing native `VideoDetailActivity` player.
An unavailable source produces an error without opening a website. Cards depend on
source availability; they are not uploaded/offline attachments. Both users need this
beta or a later card-aware build. Older apps display the body as ordinary text. Inbox
previews in this beta show the title rather than the serialized body.

## Validation and delivery

The scoped candidate workflow uses the existing encrypted `ANDROID_SIGNING_BUNDLE`,
preserves `com.addy37.crazyshitunofficial` and the production certificate, and builds
`7.0.6-beta.1-share-menu` / `7000006` to upgrade 7.0.5. The workflow checks out the
exact feature source, runs Debug/Release tests and builds, verifies lineage/signing,
current backend routing and APK contents, and uploads a branch/SHA-named APK plus
checksum and validation report. It never creates a release/tag or merges a PR.

Completed test/build counts and artifact links are recorded in the PR. Automated
media fixtures validate export bytes, provider access and caching but use Robolectric's
registered-track MediaExtractor shadow; they do not establish real Android demuxing,
WhatsApp/MMS acceptance, DRM handling on a physical device or live provider permissions.

Physical acceptance still needs an Android device with compatible receiving apps:
open/dismiss/swipe sheet repeatedly, share supported videos and play the received
files, cancel/retry, test downloaded/cached/offline cases and unsupported streams,
send/receive native DM cards, verify Auto Scroll and creator blocking across restart,
check favorites/downloads/history, inspect small/landscape layouts and system insets,
and measure live playback/swipe/memory behavior. Report only completed checks.
