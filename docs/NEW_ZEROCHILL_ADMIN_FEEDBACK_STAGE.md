# New Zerochill: Source Admin + Feedback backend staging

Date: 2026-11-12. **Only the NEW Supabase project was modified** (`aavjpykssueavbseqdlf`). Existing installed Android apps and the old Supabase project (`fketutffusxgjxjlckci`) remain unchanged.

## Scope and repository baseline

Current approved source: `main` (historical `rebrand/zerochill` was merged). Read `AGENTS.md`, `FEEDBACK_SETUP.md`, `REMOTE_SOURCE_CONFIG.md` before continuing.

The user authorized preparing source administration and feedback together, with Auth accounts, historical tickets, and release cutover deferred.

## Actual new-project changes

1. Applied migration `new_zerochill_feedback_source_prerequisites` (hosted migration version `20261112184947`), also recorded as `supabase/migrations/20261112184947_new_zerochill_feedback_source_prerequisites.sql`. It creates `private` schema if absent, adds `source_hash` and indexes to existing feedback tables, grants only `service_role` execution of `publish_source_config`, and pins the feedback timestamp function's search path.
2. Applied existing main migration `supabase/migrations/20261029160239_feedback_atomic_rate_limits.sql` through the hosted migration interface as `feedback_atomic_rate_limits` (hosted version `20261112184954`). It creates per-installation/per-source ticket guards and per-thread/per-source reply guards. Do **not** blindly replay it on a later CLI `db push`; reconcile hosted migration history with repository versions first.
3. Deployed the following NEW-project Edge Functions from current main source:
   - `app-config-admin` v1, `verify_jwt=false`, requires server-side `FEEDBACK_ADMIN_TOKEN_HASH`.
   - `feedback-admin` v1, `verify_jwt=false`, requires the same server-side admin token hash. This combined admin handler includes actions for social/analytics tables **not yet present**; only feedback-related actions can be tested after credentials are provisioned.
   - `dynamic-api` v1, `verify_jwt=true`, from this branch. The scoped patch rejects requests with 503 when `FEEDBACK_ID_SALT` is not present, instead of silently hashing user installation IDs with an empty salt. It needs the original salt before historical feedback migration so users can keep access to prior threads.
4. Existing `app-config` v1 (read-only) and the 13 migrated source-config versions remain in place. No new configuration publish or rollback was triggered.

## Verified

- `service_role` has `publish_source_config` EXECUTE; `anon` and `authenticated` do not.
- `source_hash` exists on both feedback tables, two rate-limit triggers exist, active source config is still v13.
- Transaction-scoped tests: fifth feedback ticket accepted but sixth blocked by `zerochill_feedback_rate_limited`; twentieth user reply accepted but twenty-first blocked by `zerochill_feedback_reply_rate_limited`. Both transactions rolled back; subsequent queries confirmed **zero persisted staging rows**.
- Supabase deployment metadata reports all five new-project functions ACTIVE (`app-config`, `push-dispatch`, `app-config-admin`, `feedback-admin`, `dynamic-api`), with `dynamic-api` JWT verification on.
- Security advisor after changes: three informational RLS-without-policy notices on **backend-only** tables; leaked-password protection disabled warning (defer to Auth settings review before account cutover). Prior mutable `set_feedback_updated_at` search path warning was cleared.
- **No HTTP end-to-end check:** web request to new `app-config` URL was not accessible in tool environment. Do not claim Edge runtime, feedback submit/list, admin publishing, Brevo interaction, Android or private admin app workflows were physically exercised.
- No local Android/Deno build or unit tests were run. No SDK secrets, salts, users, feedback tickets, or admin tokens were copied into code, chat or GitHub.

## Required private configuration before functioning

Use **New Zerochill** Supabase Dashboard -> Edge Functions -> Secrets (wording may vary). Enter secrets privately; never paste in chat, APK or GitHub.

- `FEEDBACK_ADMIN_TOKEN_HASH`: SHA-256 hex of the existing private admin token. Reuse existing old-project value through a **secure** transfer or coordinate rotation with the private admin client. This is shared by `app-config-admin` and `feedback-admin`. Without it, both endpoints intentionally return 401.
- `FEEDBACK_ID_SALT`: original old-project feedback installation salt (same value) if preserving ownership of prior 64 feedback tickets/198 messages. This is sensitive; use the provider's secure secret management. Without it, `dynamic-api` intentionally returns 503. Creating a fresh salt without a migration policy would orphan historical ticket identities.
- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` must be available only on the Edge backend (normally Supabase-provisioned). Do not place service keys in client builds.

After secrets: test controlled submit/list/thread/reply and admin list/validate/current/history/publish/rollback using disposable records and a disposable versioned config. **Do not** publish a new source-config version just to smoke-test without comparing current client behavior and rollback plan.

## Deferred

- Historical feedback ticket/message migration and salt ownership verification.
- Updating private `feedbackadmin` APK's endpoints; run its tests/build before field use.
- App `FEEDBACK_ENDPOINT`, `FEEDBACK_ANON_KEY`, new publishable key, source-config URL injections and release signing. Do not point any old APK to this project.
- Social/Auth/analytics migrations. No analytics Edge Function has been deployed in New Zerochill.
- Hosted migration version reconciliation before CLI migration push (earlier staging also used tool-generated hosted versions).

## Reversal and safety

Old project remains the rollback baseline. New functions are staged but not used by production clients. If unexpected traffic appears, review new-only function deployments first, without disabling old services. This documentation PR is separate from the earlier planning PR #418, and no merge/release is authorized.
