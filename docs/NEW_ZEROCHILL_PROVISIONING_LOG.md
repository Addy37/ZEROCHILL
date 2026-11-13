# New Zerochill backend provisioning log

Date: 2026-11-11 America/Chicago (2026-11-12 UTC)
Status: **initial setup complete, user-data cutover not started**.

## Target

- New organization: Zerochill
- New Supabase project: New Zerochill, `aavjpykssueavbseqdlf`
- Source project: Crazyshit Feedback, `fketutffusxgjxjlckci`
- Android source of truth: current `main`, not historical `rebrand/zerochill`

## Changes made to the new project

1. Applied the SQL from these existing repository migrations through the connected Supabase migration interface:
   - `202609130001_create_source_config.sql` as `create_source_config`
   - `202609120001_create_feedback.sql` as `create_feedback`
   - `202609290001_feedback_conversations.sql` as `feedback_conversations`
   - `202609290002_feedback_conversation_security.sql` as `feedback_conversation_security`
2. Migrated all **13** versions of `source_config_versions` with original timestamps/version/order and JSON data. Exactly one is active (version 13). Old/new `md5(config::text)` values matched for all 13 rows.
3. Deployed `app-config` Edge Function version 1 (read-only) with `verify_jwt=false`, from the current `main` source `supabase/functions/app-config/index.ts` and `_shared/source-config-validation.ts`. Supabase reports `ACTIVE`.

## Verification

- New custom public tables: `source_config_versions` (13 rows), `app_feedback` (empty), `feedback_messages` (empty).
- All three tables have RLS enabled. Public table access is intentionally denied for remote configuration/feedback; the security advisor flags three informational RLS-without-policy findings expected for backend-only tables.
- Security advisor also flags the existing `set_feedback_updated_at` mutable search path function definition. Review/harden in a tracked migration before release.
- The newly deployed function has not been HTTP-smoke-tested. Browser/server HTTP tools in this session could not reach the project's public function URL; `ACTIVE` is deployment status, not end-to-end verification.
- **Four new hosted migration history entries were generated with 2026-11-12 timestamps.** Before any later `supabase db push`, reconcile them with the repository's older migration filenames using a reviewed migration-history repair process; do not blindly replay migrations.

## Not yet migrated

- 755 Auth users (755 identities and password-backed accounts); 755 profiles.
- Creator favorites, comments, likes, DMs/reactions, notification preferences, push tokens, reports and social bans.
- Existing feedback tickets/messages. The new Edge Functions also need the original server-side feedback salt to preserve anonymous ticket ownership; never put the salt in a public client or this repository.
- 49 avatar objects, 9 private photo objects, and 10 private GIF objects.
- Private admin functions, protected secrets, SMTP/Auth settings, social-media upload/push handlers.
- Analytics: intentionally excluded (especially the legacy deleted endpoint that receives 404s).

## Safe continuation and caveats

1. Stage a secure Postgres backup/restore workflow with credentials in local environment or a trusted secret manager, **not chat, GitHub, or application code**. Consult [Supabase Auth migration guidance](https://supabase.com/kb/troubleshooting/migrating-auth-users-between-projects) and [Backup/Restore guidance](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore). Preserve Auth UUIDs and password hashes via official database tooling. New JWT signing identity means users may need to sign in again.
2. Plan Auth import **before** applying the new project's `on_zerochill_auth_user_created` trigger from the account/social foundation migration, or explicitly handle trigger behavior during an authorized import. Do not import profiles until account IDs are restored; ensure no unplanned duplicates.
3. Stage original owner-scoped rows, media objects, storage policies, API/RPC policies, and server-only secrets. Do a dry-run on isolated data and compare aggregates/ownership/permissions.
4. Build the new Android client without legacy analytics URLs; reduce redundant social polling. Test an in-place update and account login with test users.
5. At a later separately approved cutover, handle live writes between snapshot and launch, then verify new user/data parity and rollback procedure.

The old Supabase project, its existing users and installs, app releases, and repository application code were **not changed** by this provisioning step.
