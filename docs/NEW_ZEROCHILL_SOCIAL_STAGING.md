# New Zerochill: social backend staging

Date: 2026-11-12. **Only NEW Supabase project `aavjpykssueavbseqdlf` was changed.**
Old project `fketutffusxgjxjlckci`, installed Android app, current Admin APK, signing, local data, source configuration, and legacy user accounts were not changed or copied.

## Source and dependency

The approved repository source is `main`, containing the former `rebrand/zerochill` lineage. Social staging branch `feature/new-zerochill-social-staging` starts from the prior `feature/new-zerochill-admin-feedback-staging` branch (draft PR #419). Merge/rebase in the correct order. Read `AGENTS.md`, `PROFILE_ACCOUNT_2.md`, `PROFILE_SOCIAL_CLEANUP.md`, `docs/SOCIAL_MEDIA_ANALYTICS_V2.md`, and relevant migrations before release.

## Actual New Zerochill database changes

The following **existing repository migrations** were applied to the new project in order, under new hosted migration names `new_zerochill_social_<source migration name>`:

| Source migration | New hosted version |
| --- | --- |
| `202609290003_account_social_foundation` | `20261112195119` |
| `202609300001_dm_inbox_controls` | `20261112195127` |
| `202609300006_comment_owner_controls` | `20261112195129` |
| `20260930212316_profile_account_2` | `20261112195131` |
| `20260930234006_profile_account_rpc_boundaries` | `20261112195133` |
| `20261003171153_conversation_cleanup` | `20261112195134` |
| `202610290002_social_write_rate_limits` | `20261112195212` |
| `202610290003_social_bans` | `20261112195214` |
| `20261101194912_social_media_reactions` | `20261112195216` |

The existing migration `202610290001_social_abuse_hardening` was **not** replayed wholesale: source-hash feedback columns, indexes, and feedback search-path hardening were already installed in this new project; the old `public.rls_auto_enable()` helper does not exist here. Its DM anti-spam function/grants were extracted into the new-project migration `20261112195202_new_zerochill_social_dm_abuse_limits.sql`, applied as hosted version `20261112195202`.

The schema now includes `profiles`, `creator_favorites`, `comments`, `comment_likes`, `video_likes`, `user_blocks`, `direct_messages`, `social_reports`, `notification_preferences`, `conversation_clear_state`, `dm_reactions`, and the `comment_feed` security-invoker view. Private quota/moderation support is in `private.social_rate_events`, `private.social_bans`, and `private.social_media_uploads`. Public social tables use RLS. Private social/DM message boundaries and caller-specific permissions match the current repository patterns.

Storage buckets created: public `avatars` (512 KiB limit) and private `social-photos` (5 MiB) / `social-gifs` (10 MiB) with scoped policies. Social media client upload uses a backend-only service credential; direct client writes to these private buckets are intentionally closed.

The four-file `social-media-upload` Edge Function from current `main` (`index.ts`, `handler.ts`, `validation.ts`, `structure.ts`) was deployed in the **new** project as v1 ACTIVE with `verify_jwt=true`. Initial bundle attempt lacked `structure.ts` and was rejected; the complete four-file deployment succeeded. Authenticated binary upload has not been tested.

## Tests actually performed in the new database

All synthetic account and social data tests ran inside explicit `BEGIN ... ROLLBACK` transactions. Follow-up queries confirmed no synthetic users, comments, favorites, messages, bans, reports, or rate records persisted.

- Creating two synthetic Auth users with `adult_confirmed`, `terms_accepted`, and valid `username` metadata generated their profile rows through the expected trigger, then rolled back.
- With `SET LOCAL ROLE authenticated` and synthetic JWT subject claims: self-owned favorites/comment/message inserts succeeded; inserting another account's favorite was rejected; a third user saw no private DMs or another account's favorites.
- A blocking record prevented a newly blocked sender from sending additional DMs.
- A sixth comment within 30 seconds was denied; a ninth DM to the same recipient within 30 seconds was denied; a fourth report within five minutes was denied.
- A sixteenth comment like in 30 seconds was denied by the private accepted-write ledger guard; the ledger was empty after rollback.
- A moderation-banned user could not send DMs or post comments.

These were database-level policy/rate-limit checks. They do not substitute for real Auth sessions, client HTTP, live signed-in social/Storage requests, or Android device tests. The live social uploader was not physically exercised with a signed-in account.

## Compatibility and outstanding work

- Zero existing account/profile/DM/favorite/comment data was imported from the old project. The fresh project's one pre-existing Auth user currently has no profile from these newly added tables; this stage does not migrate or retroactively create account records.
- The new Auth insert trigger requires valid username, adult confirmation, and terms acceptance metadata. Review/reconcile pre-existing test/admin Auth users and future Auth migration before allowing real sign-ups.
- Existing source configuration remains v13. The old production backend, existing application IDs, and signing identities are unaffected.
- **No new analytics ingestion, analytics tables, or old `analytics-ingest` routes were installed.** No Android app was pointed to this backend.
- Rate-limit event records in `private.social_rate_events` accumulate as comment likes arrive. A low-cost, bounded retention/cleanup plan is needed before real traffic; do not introduce per-action network calls or an analytics-style event pipeline.
- Supabase security advisor still reports three informational backend-only RLS-without-policy notices and the previously known leaked-password protection setting. Revisit the latter before the Auth cutover.
- The admin app's account/social/analytics sections are not usable against the fresh backend until accounts/moderation clients are prepared.
- **No Gradle build, Android test, real signed-in HTTP social test, signed APK, or physical device verification has been run in this staging task.** No source-config publish/rollback was performed.
- Reconcile Supabase hosted migration history to repository files before invoking a CLI database migration push. Many source SQL files already exist in `main` but were applied under new hosted versions.

Next recommended step: create disposable accounts through the actual Supabase Auth API, test social/Storage/RPC behavior over authenticated HTTP, review retention and Auth settings, then plan the controlled Android backend cutover. Keep existing accounts and app traffic on the old project until separately approved.
