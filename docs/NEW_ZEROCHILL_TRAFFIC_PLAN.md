# ZEROCHILL 7.0: Supabase traffic failure analysis and low-traffic contract

Date: November 12, 2026. Analysis compares the **OLD** project's last-24-hour gateway/function logs with current Android source. The only database changes made in this work are to **New Zerochill** (`aavjpykssueavbseqdlf`). No existing installed Android app, old-project data, old routes, signing identity, or client behavior was changed.

## Observed OLD project traffic

The old project is `fketutffusxgjxjlckci`. In a moving 24-hour window, the API gateway returned about **38,496 logs** and the Edge Function gateway about **35,694 logs**. The largest Edge Function path was **`/functions/v1/analytics-ingest`: 31,258 requests returning 404** (around 87.6% of function-gateway events). The deleted function is absent from the deployed function list, but installed clients keep calling its URL. Missing-function gateway 404s should **not automatically be treated as executed/billable function invocations**; use Supabase organization usage and the support case to reconcile actual charges.

Other active function paths: `app-config` about **2,713** (1,812 HTTP 200 and 901 HTTP 304), and `analytics-ingest-v2` about **1,601** (HTTP 204). The old `analytics-ingest-v2` function also issues two database RPCs per successful request, seen as ~1,739 `record_analytics_v2_batch` calls and ~1,734 `allow_analytics_v2_request` calls.

Top old REST paths in the same moving window were `video_likes` **10,518** requests, `comment_feed` **9,046**, `comments` **6,694**, `notification_preferences` **1,668**, `source_config_versions` **2,733**. A follow-up UA grouping confirmed the high-count video/comment/preference reads came from Dalvik Android clients, whereas `source_config_versions` reads came from Deno Edge Functions. The two gateway log sources represent **different request layers**; do not add them as though they were unique human actions. The logs lack reliable complete response-byte accounting. These numbers diagnose request frequency, **not exact billed egress**.

## Code-level causes confirmed on current main

- `SocialActivityCoordinator.POLL_MS = 12_000` queries foreground social activity every 12 seconds (up to **300 poll cycles/hour** per active logged-in foreground session). `ZeroChillSocialRepository.loadCommentActivity` can request own comments, incoming comment likes, replies, and actor profiles. `ZeroChillNotificationPreferences.refreshIfNeeded` adds a preferences read at most once per 60 seconds.
- `ChaosFeedView.refreshSelectedLikeStateLater` requests **both** `refreshVideoLikeState` and `refreshCommentCount` ~220ms after the selected ShitTok video settles. `refreshCommentCount` downloads the **full `comment_feed`** solely to calculate a number. `videoLikeState` downloads the list of `user_id` values for that video's likes, then counts them and finds the current user.
- `AnalyticsTracker` currently has a scheduled v2 summary transport when `ANALYTICS_V2_ENDPOINT` is configured. `app/build.gradle.kts` has **old-project fallback URLs** for both `ANALYTICS_ENDPOINT` and `ANALYTICS_V2_ENDPOINT`; current `ACCOUNT_SUPABASE_URL` also defaults to the old project. **A 7.0 build without explicit overrides could still contact old Supabase**.
- The original analytics endpoint was deleted, but that cannot rewrite the endpoint compiled into already-installed APKs. Source config's current background refresh is every two hours with a conditional ETag, which should remain. Legacy builds may have less efficient intervals.

This is excess normal-client polling plus leftover compiled analytics traffic. No evidence here establishes a coordinated external attack or the exact dollar/egress cause. Supabase billing usage and support response must confirm that separately.

## New-project changes already deployed and verified

1. Enabled the existing repository push event triggers `202610250005_direct_message_push_webhook.sql` and `202610250006_social_push_webhooks.sql` under hosted migration versions **20261112200939** and **20261112200941**. They queue one authenticated server-to-server event on a new DM, comment reply, or comment like. The new project's Vault URL points to **New Zerochill**, not the old project, and its existing push webhook key and Firebase service-account secret are present. No old account credentials were moved. The new push-dispatch function is already deployed. **Real Firebase delivery remains untested**.
2. Tested these push triggers with two synthetic Auth users and three synthetic social writes inside one transaction: exactly **three queued requests** targeted New Zerochill's `push-dispatch`; `ROLLBACK` left no test users, messages, or queued notifications. No HTTP push was sent by that test.
3. Created a single **daily in-database cleanup** of `private.social_rate_events`, at 04:17 UTC, deleting entries older than 48 hours, with a timestamp index. Hosted migration **20261112201049**, repository `20261112201049_new_zerochill_social_rate_ledger_retention.sql`. No client request, external network call, or analytics job. Verified active `pg_cron` named job, and a rollback-only test deleted the expired row while keeping the recent row.
4. Added `public.zerochill_video_social_summary(p_content_key text)`, hosted migration **20261112201448**, repository `20261112201448_new_zerochill_video_social_summary.sql`. One security-invoker/RLS-respecting RPC returns `like_count`, `comment_count` (non-deleted comments only), `liked_by_me`; no user IDs or full comment bodies. Available for **future Android client integration**. Rollback-only anon and two-account tests confirmed public counts and correct per-user liked status; no test rows remain.
5. No analytics ingest function or legacy `analytics-ingest` path was deployed to New Zerochill. App-config v13 and old project remain unchanged.

## Android 7.0 work still required before any migration/cutover

**Not implemented in this PR. Use a separate focused Codex Android feature branch and tests; do not alter released 6.0.x installs.**

1. **Disable legacy analytics traffic** in 7.0 default and every signed workflow, including build-time fallback. Prefer `ANALYTICS_ENDPOINT=""` and `ANALYTICS_V2_ENDPOINT=""` for the new project until a separately approved lightweight design exists. Explicitly verify the signed APK cannot call either old analytics URL. Do not build a new analytics ingest service by default.
2. **Change social notifications from 12-second foreground polling to Firebase push plus a bounded recovery check**, e.g., one refresh on foreground resume and at most once per **five minutes** while actively signed in. This changes up to 300 cycles/hour to at most 12, a **96% reduction in poll cycles** for continuous foreground usage. Keep manual pull-to-refresh and reconciliation after missed/disabled push. Do not drop notifications for users with notification permission disabled, expired Firebase registration, or offline reconnect.
3. **Use `zerochill_video_social_summary` for the currently visible video**, with one in-flight request per content key, a short cache TTL (e.g., 120 seconds), account-aware `liked_by_me` caching, and immediate local cache invalidation/optimistic UI on likes/comments. Do not download full comments just to paint the rail count. Continue fetching actual full comments **only when the thread is opened or refreshed by user action**. Avoid queuing requests for every offscreen/preloaded ShitTok item.
4. **Reduce notification preference polling** from every 60 seconds to loading on account change/foreground resume with a longer TTL (e.g., 30 minutes), and cache/invalidate after edits. Continue account-scoped preferences and push choices.
5. Add **error-aware backoff** and a hard stop on repeated 401/403/404, with no background loop; 429/5xx exponential backoff and bounded jitter. Never repeatedly retry a permanent missing route. For push registration, avoid re-upserting identical tokens at each start; use changed-value writes with an occasional refresh, stop retrying when auth expires until a session refresh, preserve account rotation/privacy.
6. Keep remote source configuration's once-per-two-hours maximum automatic check, ETag HTTP 304, persisted cooldown, and known-good cache/fallback. Do not break working content providers, favorites, history, downloads, media playback, or current UI behavior.
7. Update all 7.0-only backend endpoints to the **new** Supabase project intentionally, with new public/publishable key, after real disposable-account/Auth, email, push, media, and admin tests. Reject any 7.0 signing workflow that unintentionally falls back to the old project's URLs. Keep existing APK IDs and signing identity unchanged.

### Acceptance targets and proof

- No 7.0 requests to legacy analytics endpoints or old project after approved cutover.
- No automatic ShitTok like/comment counter GET per swipe after showing a fresh cached result; at most one coalesced summary request for the selected item when cache stale.
- No full comment-feed fetch unless a thread is open or explicitly refreshed.
- No global 12-second social polling. The five-minute fallback plus push preserves functional notifications.
- Verify event push for DM, reply, and comment like on two real test devices; verify disabled permission, foreground transitions, account sign-out/in and lost network.
- Use per-activity/API request-count regression tests, signed build configuration checks, and 24-hour gateway metadata comparisons before/after limited rollout. Inspect request volume and billing/egress metrics **separately**, because counts do not equal bytes.
- Keep prior `app-config`, feedback, favorite, download, social, RLS, and upgrade behavior working.
- On sign-in failure or missing push token, do not add a retry storm; cap retries and offer manual refresh.

## Branch and risk

This traffic-guard branch builds on draft social PR #420, itself built on draft admin/feedback PR #419. No merge, Android release, or project cutover has been authorized. New backend push wiring adds one function dispatch for **each real incoming social event**, not every time a user views a page. Successful Android integration must be measured to verify total costs decrease. All synthetic tests were rolled back. Existing Old Supabase remains the source for existing installed APKs until a separately approved 7.0 rollout.

## Supplemental index and RLS performance follow-up

The new project's performance advisor initially identified five missing foreign-key indexes and four DM reaction RLS per-row Auth-init warnings. Applied the **existing** repository migration `202609290004_account_social_indexes.sql` as hosted migration `20261112201840`, adding the pre-existing repo's comment/user/like/block indexes in the new project. Applied `20261112201854_new_zerochill_social_rls_index_performance.sql` in hosted migration `20261112201854`, adding missing conversation-clear and source-config FK indexes and replacing direct `auth.uid()` in DM reaction policies with cached statement-level `(select auth.uid())`. Original RLS access rules remain unchanged.

After these changes the performance advisor reported **no missing foreign-key indexes or Auth-initplan warnings**, only currently unused indexes on the new, mostly empty database. Do not remove those indexes based on a pre-launch unused-index notice. Rollback-only tests confirmed a DM participant could read a reaction but an unrelated account could not read or forge one. Zero synthetic accounts, reactions, or queued push events remained.
