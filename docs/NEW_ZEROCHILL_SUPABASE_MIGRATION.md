# ZEROCHILL 7.0: New Zerochill Supabase cutover plan

Status: **planning only**. No backend deploy, data export, credential change, Android endpoint swap, old-project shutdown, or release is authorized by this document.

## Baseline and source of truth

- As checked on 2026-11-11 US Central/2026-11-12 UTC, the historical `rebrand/zerochill` branch is an ancestor of `main`. Use current approved `main` lineage and recheck before implementation.
- Old project: **Crazyshit Feedback**. New isolated account/organization: **Zerochill**; new project: **New Zerochill**. New project was ACTIVE_HEALTHY with no public tables, custom Edge Functions, or migrations when inspected.
- Keep old services live for existing installed clients while cutting over supported 7.0 clients. Do not assume deleting a function, revoking an API key, or changing an APK makes old clients stop calling the old URL.
- Keep application ID `com.addy37.crazyshitunofficial`, production signing identity, device data, favorites/history/downloads/backups, remote-source fallbacks, and working content sources.

## Read-only evidence

The old project currently has approximately 755 Auth users (695 email-confirmed), 755 profiles, 6,419 account-scoped creator favorites, 5,119 video likes, 851 DMs, 152 comments, 64 feedback tickets, and 198 feedback messages. Its legacy analytics tables contain approximately 150,641 counter and 438,121 unique rows. Counts are snapshots, not migration totals.

A trailing 24-hour log query on the old project found:
- `/functions/v1/analytics-ingest`: **31,404 HTTP 404s**. This legacy slug is no longer deployed, but older installations still request it. A gateway 404 may still consume quota/egress.
- Successful `/rest/v1/video_likes` GETs: **10,301**; `comment_feed`: **9,279**; `comments`: **6,324**. Do not conflate successful social polling with the deleted analytics endpoint.
- The current `main` Android build configuration still has old-project default URLs for both `ANALYTICS_ENDPOINT` and `ANALYTICS_V2_ENDPOINT`. `AnalyticsTracker` currently sends v2 foreground summaries when configured; legacy traffic can originate from older installed versions.
- `SocialActivityCoordinator` polls every 12 seconds while eligible activity screens are resumed; profile, comments, likes, DMs, and preferences have additional traffic. Measure per-screen call counts before choosing a new cadence.

Do not redirect the old 404 route to the new project or duplicate old telemetry on the new project.

## Desired v7 design

1. **New backend has its own explicit endpoint/key configuration.** A 7.0 build must never silently fall back to the old project if the new URL or publishable key is missing. Keep admin/service credentials server-side. Build validation should fail closed for required account/social/feedback features.
2. **Analytics are disabled in the initial 7.0 cutover** pending a separate measured design and owner approval. Do not deploy either legacy or v2 analytics tables/functions by default, and do not carry old analytics history into the new live database. Historical old analytics may remain on old infrastructure for review.
3. **Preserve the needed feature set:** Auth/verified identity, profile + avatar, account creator favorites, comments/replies/likes, video likes, DMs/clear/reactions, blocks/reports, notification choices, push registration/dispatch, feedback conversations + private admin, secure social attachments, remote source config and admin publishing, account deletion, and any still-used ShitTok popularity features.
4. **Reduce social and config requests at their source.** Profile and social calls should be batched/cached/conditional or use an incremental cursor as justified by current code. Preserve acceptable foreground notification timeliness and account isolation; test latency before changing poll cadence. Keep source-config ETag/304 and bundled fallback.
5. **Use existing migrations as input, not a blind full-project clone.** Audit the deployed schema, RLS, grants, triggers, storage policies, RPCs and functions against `main/supabase`; build new auditable migrations under `supabase/migrations`. Do not apply analytics migrations automatically. Check Supabase CLI version because CLI v3 behavior has changed.
6. **Migrate users securely.** Cross-project JWTs and refresh sessions are not interchangeable; expect reauthentication in 7.0. Verify a secure Auth user/password-hash migration approach, provider/SMTP settings, identity UUID continuity, email verification, and constraints before moving public profiles. Never export password hashes, service-role credentials, or personal messages into chat, GitHub, CI logs, or documentation. Do not silently force 755 users to re-register or destroy their social history.
7. **Preserve linked content.** Migrate selected live data plus avatars/private attachments with IDs, ownership, referential integrity and storage policies, using a secured, auditable export/import method. Protect anonymous feedback installation-hash identity and its server-only salt. Freeze writes or do a bounded delta sync at a planned cutoff so no final changes vanish.
8. **Separate rollout and rollback.** Provision/test new backend without changing the old. Test an isolated 7.0 APK using the new environment, then release only after approval. Old installs remain on old backend until sunset is separately approved. Rollback must account for new-project writes and divergent user data; switching endpoint URLs alone is not a safe data rollback.

## Proposed implementation sequence and acceptance gates

- [ ] **A. Inventory:** verify current `main` usage for each REST/RPC/function/storage path, deployed-only migrations, authentication and notification flows. Capture 24-hour baseline by endpoint and error status. Do not inspect users' message bodies.
- [ ] **B. Schema branch:** add reviewed migrations for essential tables/functions/RLS/storage. Exclude analytics. Run migrations against the isolated new project, check grants/RLS and migration history, and inspect advisors.
- [ ] **C. Auth/data dry run:** use disposable accounts first; verify sign-in/re-sign-in, UUID continuity, avatars, creator favorites, comments, likes, DMs, reactions, feedback threads, social media, notifications, and account deletion. Establish a secure production import procedure and rollback checkpoints.
- [ ] **D. Android cutover branch:** require explicit new endpoint/key injection for required services. Gate or disable analytics. Reduce redundant social calls with focused regression tests; do not remove functional services.
- [ ] **E. Signed-device test:** run relevant SQL/security tests, unit tests/builds, and signed in-place upgrade on the user's OnePlus Android 16. Validate old and new builds independently with no credentials in logs.
- [ ] **F. Launch authorization:** verify quiet baseline of new project, quota/cost, old-client separation, data parity, monitoring, backups, and deliberate switchover/rollback. Owner approves production data movement and publication separately.

## Risk register

- **Highest:** Auth user migration and preserving ownership/security of imported records. Must validate before any real data copy.
- **High:** User writes during migration, avatar/social storage ownership, anonymous feedback salt/identity, push and private admin credentials, new project egress, old Android session handling.
- **Medium:** 12-second social refresh demand, new-project quotas, remote-config snapshot compatibility, current code's old-project fallback build defaults.
- **Deferred:** rebuilding an analytics dashboard, migrating old analytics metrics, retiring the old project. These require separate decisions.

## Documentation and verification

Relevant repository references: `AGENTS.md`, `ZEROCHILL_BUILD_LINEAGE.md`, `REMOTE_SOURCE_CONFIG.md`, `FEEDBACK_SETUP.md`, `PROFILE_ACCOUNT_2.md`, `docs/ANALYTICS_V2_CUTOFF.md`, `docs/SUPABASE_INVOCATION_OPTIMIZATION.md`, `docs/SOCIAL_MEDIA_ANALYTICS_V2.md`.

This planning change makes no Android, SQL, Edge Function, storage, CI, or security modification. No build, Android test or live migration has been run for this document.
