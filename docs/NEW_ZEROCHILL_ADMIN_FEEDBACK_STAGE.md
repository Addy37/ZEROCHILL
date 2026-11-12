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
   - `feedback-admin` v1, `verify_jwt=false`, requires the same server-side admin token hash. This combined admin handler includes actions for social/analytics tables **not yet present**; only the feedback list endpoint has been smoke-tested.
   - `dynamic-api` v1, `verify_jwt=true`, from this branch. The scoped patch rejects requests with 503 when `FEEDBACK_ID_SALT` is not present, instead of silently hashing user installation IDs with an empty salt. The user explicitly abandoned historical feedback, so a new salt was created and stored in New Zerochill Edge Secrets.
4. Existing `app-config` v1 (read-only) and the 13 migrated source-config versions remain in place. No new configuration publish or rollback was triggered.
5. Applied new-only hosted migration `new_zerochill_service_role_backend_grants` (`20261112192736`), recorded as `supabase/migrations/20261112192736_new_zerochill_service_role_backend_grants.sql`. The new database originally lacked service-role SELECT/INSERT/UPDATE/DELETE on three backend-only tables even though the old project granted them. This was the direct cause of HTTP 500 on both admin read endpoints. Restored these service-role ACLs without adding anon/authenticated grants.

## Verified

- `service_role` has `publish_source_config` EXECUTE; `anon` and `authenticated` do not.
- `source_hash` exists on both feedback tables, two rate-limit triggers exist, active source config is still v13.
- Transaction-scoped tests: fifth feedback ticket accepted but sixth blocked by `zerochill_feedback_rate_limited`; twentieth user reply accepted but twenty-first blocked by `zerochill_feedback_reply_rate_limited`. Both transactions rolled back; subsequent queries confirmed **zero persisted staging rows**.
- Supabase deployment metadata reports all five new-project functions ACTIVE (`app-config`, `push-dispatch`, `app-config-admin`, `feedback-admin`, `dynamic-api`), with `dynamic-api` JWT verification on.
- Security advisor after changes: three informational RLS-without-policy notices on **backend-only** tables; leaked-password protection disabled warning (defer to Auth settings review before account cutover). Prior mutable `set_feedback_updated_at` search path warning was cleared.
- HTTP smoke tests ran inside the **new project's database** through `pg_net` to its own deployed functions, passing credentials from Vault in a header without returning token/hash values. Initial valid-token calls to `app-config-admin` `current` and `feedback-admin` `list` returned HTTP 500 due to missing service_role table grants; an invalid token returned HTTP 401. After ACL repair, valid-token calls returned **HTTP 200** with the active source config version **13** and **0 feedback tickets**. No source publish/rollback, feedback submit/reply, private admin APK workflow, or Android test was run.
- No local Android/Deno build or unit tests were run. No SDK secrets, salts, users, feedback tickets, or admin tokens were copied into code, chat or GitHub.

## Secret configuration now in place

- The user explicitly chose to **abandon all historical feedback**. No old feedback tickets/messages or old identity salt were moved. The user reports creating a new `FEEDBACK_ID_SALT` in New Zerochill's Edge Function Secrets. The salt value was not read via the connector.
- A fresh 32-byte random admin token and matching SHA-256 hash were generated **inside New Zerochill database**, then stored separately in encrypted Vault as `zerochill_admin_token` and `zerochill_admin_token_sha256`. A boolean-only SQL check confirmed they match. Neither was returned to chat or committed.
- The user placed the new hash into the Edge Function secret `FEEDBACK_ADMIN_TOKEN_HASH`. Accepted/rejected HTTP authentication tests confirmed the configured value matches the Vault token. The private Admin APK still needs the matching token and new endpoints in a later tested build.
- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` stay in the Edge backend (Supabase-provisioned), never in Android or GitHub client build artifacts.

Next: perform controlled end-to-end feedback submit/list/thread/reply tests with disposable data, test private admin APK pointing only at the new project, and stage a reviewed source-config publish/rollback test if needed. **Do not** publish a new source-config version solely for a smoke test without considering client validation and rollback.

## Deferred

- Historical feedback migration is **not planned**, per explicit user direction.
- Updating private `feedbackadmin` APK's endpoints; run its tests/build before field use.
- App `FEEDBACK_ENDPOINT`, `FEEDBACK_ANON_KEY`, new publishable key, source-config URL injections and release signing. Do not point any old APK to this project.
- Social/Auth/analytics migrations. No analytics Edge Function has been deployed in New Zerochill.
- Hosted migration version reconciliation before CLI migration push (earlier staging also used tool-generated hosted versions).

## Reversal and safety

Old project remains the rollback baseline. New functions are staged but not used by production clients. If unexpected traffic appears, review new-only function deployments first, without disabling old services. This documentation PR is separate from the earlier planning PR #418, and no merge/release is authorized.

## Verified synthetic API interaction (2026-11-12)

The new project's private admin token was used **inside the database only** via Vault with `pg_net`, so no token was sent to chat or GitHub.

- Created one disposable feedback ticket and one user message in **New Zerochill** using SQL, tagged `staging-smoke-20261112`. This deliberately tested the admin API from a pre-existing synthetic row; **public dynamic-api submission remains untested**.
- `feedback-admin` `list`: HTTP 200; one ticket found with unread_count=1.
- `feedback-admin` `thread`: HTTP 200; one message returned and user's message marked read.
- `feedback-admin` `reply`: HTTP 200; developer reply recorded, ticket status moved to `reviewing`, now two messages.
- `feedback-admin` `delete`: HTTP 200; response `deleted=true`. Follow-up DB check confirmed **0 test tickets and 0 linked messages remain**.
- `app-config-admin` `validate` on current active version 13: HTTP 200 / valid=true. `history`: HTTP 200 with all 13 versions. Neither request published or changed source config.
- Public `app-config` GET: HTTP 200 with configVersion 13. GET with `If-None-Match: \"13\"`: HTTP 304, as intended for cached versions.
- The public `dynamic-api` JWT-gated submit/list/reply and Android device UI are **not** yet end-to-end verified. No real users or feedback history were migrated.

## Separate, safer Admin staging APK build option

Two additional repository files were updated on this same feature branch:

- `feedbackadmin/build.gradle.kts` accepts `ADMIN_BACKEND_TARGET=new_zerochill`. Only then, feedback/source-admin URLs default to the new project and the admin APK uses the separate application ID `com.addy37.crazyshitadmin.newzerochill` with `-staging` version label. The existing admin package `com.addy37.crazyshitadmin`, signing setup, private token storage, and default backend behavior remain unchanged.
- `.github/workflows/build-feedback-admin.yml` has a **manual-only** `workflow_dispatch` choice `backend_target: existing | new_zerochill` (default `existing`), passes explicit URLs, and labels artifacts with the selected target. It retains the existing build/tests, emulator checks and signing steps. **No GitHub Actions run was triggered.** Workflows on a feature branch may not be manually dispatchable until this change reaches the repository default branch or is run in a separate coding environment.

The test Admin APK will require entering the matching **Vault-only private admin token** after installing. Do not hard-code or paste the token into the build, GitHub, or chat. The test APK uses a separate app ID so it can coexist with the existing admin app.

Current admin analytics/social-management tabs depend on deferred social and analytics tables. They are not ready and were not tested. No physical build, Gradle unit test, emulator, signed upgrade or client-side install has yet run in this turn.
