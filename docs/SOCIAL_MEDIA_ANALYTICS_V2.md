# ZEROCHILL social attachments, DM reactions and Analytics v2

Candidate: `feature/social-media-reactions-analytics-v2`, based on verified GitHub
main `4454d965ea3285800b98592d2dcca15c071fa304` (released 5.6.2).
The candidate version is **5.7.0-beta.1**; a stable 5.7.0 release is not authorized.
`RELEASE_VERSION`, stable signing, application ID and updater behavior are unchanged.
The signed-candidate workflow uses `.dev` for the side-by-side Beta and records the
source branch and SHA in its filename. Its analytics endpoints are intentionally empty.

## Current backend status

The social production migration (attachment columns, DM reactions, private photo/GIF
buckets and quota/reservations) has already been deployed manually. The
`social-media-upload` v1 Edge Function is ACTIVE with JWT verification enabled.
Social media physical testing is possible now. This follow-up does not redeploy or
replace it. Analytics v2 remains **undeployed**; no cutoff is activated.

## Android social behavior

`SocialMedia` shares a single-selection system Photo Picker, bounded preparation,
preview/removal and private inline rendering between the existing comment composer
and DM thread. No camera, videos, multiple selection or broad library permission.
JPEG, PNG and WebP source files are limited to 20 MiB; static output is JPEG at
quality 90, 84 or 78, at most 5 MiB and a longest edge of 2560 px. No upscaling.
ImageDecoder applies EXIF orientation (API 28+); API 26/27 reuse Glide's bounded
orientation-aware decoder. GIF bytes pass through unchanged, at most 10 MiB.
The fixture includes multiple animation frames and an EXIF-rotated JPEG.

Composers retain the in-memory draft on failed upload/send, disable sending while
preparing/uploading, and allow retry/removal. They post only after upload succeeds.
A generated row UUID ties an immutable upload to its eventual comment or DM;
uncertain POST retries reconcile an existing row with that UUID instead of creating
another message. Changing the draft creates a fresh UUID. Attachment-only rows keep
`Photo`/`GIF` as body text for old clients and existing notification preview code.
No Storage path, filename, URL or signed URL enters notification text or analytics.

Existing reply/like/moderation/count/profile and DM read/unread/clear/block/report
paths remain in use. Deleted comments hide media publicly and remain tombstones.
Media renders through Glide, including GIF animation; rebinding the same object does
not restart its animation. Private media bypasses disk and shared memory caches.
Tap opens a larger dialog. Selection drafts are process-local, like the existing
programmatic social composers; process death discards unsent media. A recreated
picker cancels its old callback rather than delivering to a destroyed composer.

Long press a confirmed bubble or its media for ❤️ 😂 🔥 👍 😮 👎. A floating strip
anchors beside the highlighted bubble with light platform haptic feedback. The
context header offers Copy for actual text and Report for incoming messages. Grouped counts show a dot beside the current
user's reaction. The unique `(message_id,user_id)` key allows one reaction each;
the RPC serializes toggles, replaces a different emoji and removes a repeated one.
Reactions use the existing foreground thread refresh cadence. No reaction push or
reaction activity analytics is added.

## Storage and database boundaries

Additive migration `20261101194912_social_media_reactions.sql` adds attachment
bucket/path columns with empty defaults; old nonempty bodies and existing columns
retain their contracts. `comment_feed` preserves its old column order, appending
new fields. The visible-DM RPC still returns the direct_messages row type.

Two **private** buckets keep independent hard limits:

| Bucket | Max uploaded bytes | MIME types |
| --- | ---: | --- |
| social-photos | 5,242,880 | image/jpeg, image/png, image/webp |
| social-gifs | 10,485,760 | image/gif |

Objects are `<verified-owner-UUID>/<generated-row-UUID>`. The avatar bucket is untouched.
Clients have no INSERT/UPDATE policy on these buckets. `social-media-upload` verifies
Auth, streams a bounded body, validates signature, MIME and complete container/frame
structure, and reserves a slot through the caller's live-session/ban/quota RPC. The
server-only Storage client then writes an immutable object with `upsert:false`.
This rejects truncated headers, SVG, HTML, executables, arbitrary bytes and common
MIME spoofing. Container parsing is bounded structural validation, not a full pixel
decoder or antivirus scanner; intentionally malformed compressed pixels may still
fail to render. Static container dimensions are also bounded to 2560 px.

Reservations and recent stored uploads are limited to 40 per owner per five minutes
under an account advisory lock, including parallel requests. Existing comment and
DM write rate limits remain unchanged. A row constraint binds media to that row's
owner/ID; a trigger rejects links to nonexistent objects.

Storage SELECT follows existing active-comment visibility, or DM participant RLS.
A staged object's owner can also read it. Unrelated users cannot read DM objects.
Storage DELETE allows only the owner's unlinked staging object; an uncertain
successful post cannot lose its media during cleanup. Android records account-scoped
cleanup debt before upload and retries old unlinked objects after 24 hours on a
subsequent upload (up to ten attempts). Account deletion removes the owner's files
in both buckets through the Storage API before deleting Auth. Files orphaned by
other accounts' cascaded deletions, lost app data, or a user who never uploads again
need operational orphan cleanup; no SQL directly deletes Storage objects.

## Analytics v2

New path: `/functions/v1/analytics-ingest-v2`. Legacy
`/functions/v1/analytics-ingest` and all existing analytics RPCs/tables remain intact.
New production-enabled builds use `ANALYTICS_V2_ENDPOINT`; CI/Beta explicitly sets
it empty to avoid synthetic production traffic.

The tracker retains existing navigation hooks and extends the existing
`AnalyticsBatchQueue` retry/cooldown transport. Actions increment a finite local
counter map, not a network queue per action. A foreground session emits a compact
summary on final Activity stop (configuration changes do not end it); long sessions
flush every 15 minutes only while foregrounded. The transport batches a retry backlog
of up to five summaries per request, makes one delayed transient retry, and preserves
the existing cooldown. At most 30 unsent summaries are retained in memory.

Each summary contains approved counter totals plus version, Android version and phone
model aggregates. Session count and duration sums produce average session seconds;
selected ShitTok playback starts and session count produce average videos/session.
Buffering counts mean recovery from at least two seconds of buffering, not every
state transition. Provider missing/failure counters are emitted on stream resolution;
provider labels are finite and normal provider popularity is not collected. Search
counts are submitted search actions, creator/gallery and favorites counts are entries,
social counts are successful writes, and update adoption is a locally observed
version transition. On the first v2 report, package first-install/update times distinguish
an existing upgraded installation from a fresh install; no installation identifier is sent.

Only DAU/WAU/MAU store anonymous unique keys. Each day/week/month has an independent
random local seed, replaced when that Eastern calendar period changes. SHA-256 keys
are period-scoped; no permanent analytics ID, Feedback installation ID or account ID
is used. Unique keys have no metric/value dimensions and are never joined to aggregate
rows. All other metrics use sums by Eastern hour, version and a bounded category.
No search terms, creators, viewing history, URLs, attachments or social text are sent.

All client/server period boundaries use `America/New_York`, with Monday-start calendar
weeks and calendar months, including EST/EDT. The dashboard labels these calendar
periods rather than rolling 7/30-day windows. Repeated fall-back hours share an Eastern
hour bucket. Counters spanning a boundary are attributed to the flush's current period;
there is no individual event timeline. A delayed retry crossing a period boundary can
be dropped. Analytics is best-effort: process death loses in-memory counters, outages
can discard summaries, and ambiguous committed retries can double aggregate totals.
Anonymous active keys remain deduplicated. No exactly-once or fleet measurement claim.

`20261101194929_analytics_v2.sql` creates backend-only aggregate, active-key and abuse-rate
tables, service-only recording/dashboard/cleanup RPCs, and no legacy data migration.
A server-only salt produces an hourly rotating source hash solely for abuse control,
in a separate table, never logged or joined to metric/active rows. Rate limit: 120
requests per hourly source token. Counts are bounded per summary. Anonymous reporting
cannot prove a human is unique: modified clients can still fabricate telemetry within
limits. These are installation estimates, not authenticated audience measurements.

`feedback-admin` gains additive `analytics_v2`/`active_counts_v2` actions. Old admin
versions retain old actions. Updated feedbackadmin shows active counts, version and
section totals, device/Android totals, approved activity/error categories and session
averages in Eastern Time. Its foreground counter refresh is one minute instead of five
seconds. Community account administration is separate and is not an analytics identity.

Test-based reduction: 300 search actions become one local counter and one summary
request (99.67% fewer requests than per-action delivery). Relative to 5.6.2's full
30-event batches, the same 300 actions can fall from ten requests to one (90% fewer).
This is a synthetic session test, not measured fleet traffic. Very sparse or short
sessions, retries and background transitions change the savings.

## Deployment and rollback

Do not remove or disable the legacy analytics function. The social production
migration and `social-media-upload` v1 are already live; do not reapply or replace
them for this Android follow-up. Analytics v2 remains undeployed and is not authorized
for deployment in this task. The following is reference for a separately approved
backend rollout or a fresh isolated test project:

1. Apply the additive social/analytics migrations appropriate to that rollout;
   check existing migration state first rather than rerunning the live social setup.
2. Preserve `social-media-upload` JWT verification and server-only credentials.
3. Verify the updated `account-delete` Storage cleanup bundle and its existing JWT
   verification when that backend bundle is approved for rollout.
4. Deploy `analytics-ingest-v2` only after approval, with JWT verification disabled
   and a server-only random `ANALYTICS_V2_RATE_SALT`; never put this secret in Android.
5. Deploy updated `feedback-admin` analytics support only as part of that approved
   v2 rollout, preserving its existing private admin-token verification.

During that separately approved rollout, schedule `select public.cleanup_analytics_v2();` hourly through the project's trusted
scheduler. It removes abuse rows older than two hours, anonymous keys older than
65 days and aggregates older than 400 days. No client can invoke maintenance. Also
schedule operational removal through the Storage API of old unreferenced objects
and expiration of old upload reservations; take a bounded, fresh linkage check before
deleting each object. This repository does not silently enable a new Cron extension.

Check RLS advisors before and after applying; read repository SQL regression tests.
Live schema inspection on November 1 confirmed the expected comment-feed column
order, DM participant RLS, bans and existing DM rate limits. The existing security
advisor baseline contains five backend-table RLS/no-policy informational findings and
one disabled leaked-password-protection warning. No credentials were inspected.

Rollback the app rollout first. Keep additive columns, buckets and stored data while
any media-enabled client is installed. Roll back function bundles to their recorded
prior versions only when those clients will no longer invoke the new contract. Do
not drop populated tables/buckets or remove columns to roll back an APK. Restoring
legacy analytics is independent of Auth, social data, keys and remote configuration.

## Social experience and automatic crash follow-up

Comments use AndroidX NestedScrollView inside Material's draggable bottom sheet,
allowing the child to consume scrolls until the appropriate boundary. The pinned
composer integrates media; comments use aspect-aware media capped at 220 × 150dp.
DMs target WhatsApp-like compact grouping, dates/long-gap separators, integrated
media composition and aspect-aware media capped at 250 × 280dp. A selected bubble
is highlighted with a nearby six-reaction popup and supported contextual actions.
No new reaction types, notifications, backend actions or social schema are added.

CrashReporter installs once from Application and always delegates the previous
uncaught handler, including when its own capture fails. That handler does no network
I/O and does not acquire diagnostic locks: it uses a sanitized cached snapshot,
refreshed on resume and every 30 seconds only while foregrounded. Local AtomicFile
storage is private (backup disabled), capped at three reports / 128 KiB. Delivered
payloads are removed, retaining small bounded deduplication receipts. Stable class
and method frames fingerprint repeats for 24 hours; line numbers/messages do not.

After a successful resume, a delayed background worker submits at most one pending
report per launch through existing Feedback submit/thread/reply APIs. A new thread
is `bug_report`, section `Crash`; creation is capped at one per 24 hours. Repeats
reuse the thread with a one-hour update cooldown. At most three diagnostic replies
fit under the API's 2,000-character message limit; server messages reconcile partial
reply delivery and the summary marker reconciles uncertain thread POST responses.
Failures retain local reports for a later launch without UI or startup blocking.
No external SDK, email alerts, Brevo integration or new backend deployment.

Crash-only sanitation runs before persistence and upload, redacting URLs, email,
authorization/cookie headers, recognizable secrets and token-shaped strings.
Free-form exception messages are withheld unless they match restricted platform
forms; arbitrary thread names are withheld. Social bodies/drafts are never read.
ShitTokPoolDiagnostics.userReport already includes AppPerformance; the cached
snapshot reserves bounded space for its recent-event tail and performance section.
Java/Kotlin uncaught exceptions are covered; native crashes, ANRs, forced process
kills, low-memory kills and failures before Application installation are not.
The debug/Beta-only CrashProbeActivity is absent from release manifests/source.
Physical acceptance and real Feedback Admin arrival remain pending Addy's test.
