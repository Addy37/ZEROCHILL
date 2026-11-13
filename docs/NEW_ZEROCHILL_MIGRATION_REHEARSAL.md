# ZEROCHILL 7.0: isolated migration rehearsal

Status: **rehearsal preparation only**. No real account data transferred; no hosted project created or modified. Owner confirmed OG ZEROCHILL accounts/history have priority; the three New-project identities are test accounts and are not release-critical.

## Goal

Prove a complete, reversible copy of an OG account (with sign-in and linked data) in a **disposable environment** before a production transfer. Do not test by importing OG users directly into the already-populated New project. User consent to the plan is **not** permission to move production records, wipe the three test accounts, or incur staging costs.

## Current checkpoint

- Current production Android code: `main` after PR #427. Old installed clients still use the Old Supabase project until a separately approved 7.0 release.
- Old project: `fketutffusxgjxjlckci`; intended future target: `aavjpykssueavbseqdlf`.
- OG: 768 Auth email identities with existing password hashes, 768 profiles, 6,600 creator favorites, 5,255 video likes, 152 comments, 851 DMs, and 68 media objects. Re-run the snapshot before every meaningful rehearsal or live transfer. These counts will change while OG remains in use.
- New: 3 Auth test identities, 2 profiles, 3 DMs and 1 media object. All 3 emails collide with OG accounts but their Auth UUIDs differ. One New username also belongs to a different OG email address. The New account without a profile is test state.
- Old/new Auth and social schema preflight: 113 checked column definitions match, 20 checked social FKs exist in each; `auth.users` insertion triggers auto-create profiles. All existing New test records must remain intact until a separate authorized reset.

## Rehearsal sequence

### Phase 0: select a private, disposable environment

1. Prefer a **local, isolated Supabase test stack** when it matches required Auth, Storage, and database behavior. Check the installed Supabase CLI version and supported commands using `supabase --help` and its subcommand help. Supabase CLI v3 defaults to local operations and asks for authorization before hosted changes.
2. If hosted staging is required to test actual Supabase-managed Auth/Storage, **get the price and user approval before creating a new project**. Never assume a new project is free. Do not pay for a hosted branch or project until approved.
3. Keep the database entirely private, use temporary credentials, disable outgoing email/push/webhooks for imported account data, and prohibit analytics ingestion and logging of personal data.
4. Use an encrypted private workspace or secret manager under the operator's control. Do not upload backups, Auth rows, password hashes, emails, media bytes, user-ID mapping, message content, or database connection URLs to GitHub, ChatGPT, CI, or shared artifacts.

### Phase 1: backup and synthesize

1. Create separate, verified **encrypted** restore points for Old and New databases and copy storage bytes as well as storage metadata. Document restore verification and retention. A database dump alone does not include private Storage object contents.
2. Recreate the New project's schema, RLS/grants, triggers, functions and bucket permissions in the isolated environment. Reconcile existing hosted migration versions before replaying any migrations, as the New project was provisioned with renamed historical migration versions.
3. First use **synthetic identities and messages only**: one old-style account; a test account sharing an email but different UUID; another account with a matching username and different email; and accounts with replies, likes, DMs, and favorite records. Never assert that mock data proves real password migration.
4. Test the full import twice to detect duplicates and confirm restoration or deliberate replacement of disposable test accounts. For this exercise, **OG IDs/usernames win**, but take a private backup and do not touch existing hosted New accounts. A username collision from an unrelated email must not silently merge two people's history.

### Phase 2: one OG account test, with separate approval

1. Obtain explicit permission and choose a **known, consenting OG account** for the first real-data rehearsal. Only then export its Auth and profile data through reviewed tooling into the encrypted private environment. Never select an unrelated user account simply because it is convenient.
2. Verify supported transfer of `auth.users` and `auth.identities`, including stable old Auth UUID, confirmation state, identity metadata and original password hash. **Do not import old JWT sessions, refresh tokens, or old Supabase API keys**.
3. Plan for `on_zerochill_auth_user_created` to create a placeholder profile. In the isolated environment only, stage the Auth/profile import and handle the trigger safely so profile creation does not conflict with original username, timestamps, biography or avatar. Restore normal triggers and permissions afterward.
4. Restore that account's associated favorites, likes, comments/replies, sent/received DMs, reactions, blocks and settings in FK-safe order, with correct references to other participating accounts. Do not expose messages to a new account merely because its email matches. Private attachments require matching stored bytes, paths and Storage authorization.
5. Using the isolated Auth API, verify sign-in with the **original password**, new-project Auth session, account profile, RLS isolation, conversation participants, attachment permissions and account deletion. Test bad-password rejection too. This test needs functional Auth and may not be meaningful on a plain Postgres-only clone.
6. Run the read-only SQL files `new_zerochill_migration_readonly_inventory.sql` and `new_zerochill_migration_integrity_checks.sql` on the isolated environment and reconcile expected source/target deltas. Do not rely on total counts alone: check each migrated row's ownership and private-media bytes without exposing identifying information in logs.

### Phase 3: full rehearsal, then separate production authorization

1. Repeat a controlled, private import of all 768 OG accounts into disposable staging using a reviewed process that handles the 3 overlapping New test identities. Existing test accounts are replaceable **later** but OG accounts/IDs must remain intact; never drop destination accounts without a verified backup and approval.
2. Validate username uniqueness, all identity rows and password hashes, 63 unconfirmed emails, profile and comment/DM relationships, storage bytes, social blocking and report permissions, and reliable notification behavior.
3. Measure a new snapshot and plan an explicit write-freeze or bounded delta replay so ongoing comments/DMs/likes are not lost between backup and release.
4. Rehearse rollback including messages created **after** switchover. Changing the app URL back is not a complete rollback of new-project writes.
5. Only after all gates pass, obtain **separate approval** for live production import and then for 7.0 public release and old-project retirement.

## Validation gates and expected outputs

The checked-in SQL returns only counts. It requires an authorized database role to bypass RLS invisibility.

- `supabase/tests/new_zerochill_migration_readonly_inventory.sql`: original source and target account/social inventory, with no message bodies, emails or password hashes.
- `supabase/tests/new_zerochill_migration_integrity_checks.sql`: 22 owner/reference and uniqueness checks. Expected zero violations after a clean import. On the currently populated New **test** project, only `auth_user_without_profile = 1` is expected before an authorized test-account cleanup.

Both SQL files were verified on the live projects using **read-only queries**: OG 22/22 zero-violation checks; New 21/22 zeros, one known test Auth user without profile. SQL testing is not an import, password-auth or app-device test.

## Known blockers

- No isolated staging environment or encrypted restore points have been created.
- No private source data has been exported.
- Auth/Storage functional test needs real isolated services.
- The New project lacks the deployed `account-delete` and `dm-moderation` Edge Functions, which must be reviewed before production cutover.
- Security advisor on New flags leaked-password protection disabled. Evaluate Auth settings before launch.
- No confirmation yet of acceptable staging cost if local staging proves insufficient.

## Sources

- [Supabase Auth migration guidance](https://supabase.com/kb/troubleshooting/migrating-auth-users-between-projects)
- [Supabase backup/restore guidance](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore)
- [Supabase CLI v3 change notice](https://supabase.com/changelog/supabase-cli-v3)
- Existing repository planning: `docs/NEW_ZEROCHILL_AUTH_DATA_PREFLIGHT.md` and the staged backend PRs #418 through #421 and #424.

## Real isolated staging project established, November 13

The owner created an additional Supabase **Free** project in the existing Zerochill organization:

- Project name: `ZEROCHILL Migration Test`
- Project ref: `xswtgidwhesajavxjbjb`
- Region: `us-west-2`
- Status checked: `ACTIVE_HEALTHY`
- This is **not** the production OG project `fketutffusxgjxjlckci` or the live future New Zerochill target `aavjpykssueavbseqdlf`.

**Changes completed on this test project only:**

1. Verified it initially had 0 Auth users and 0 `public` tables.
2. Applied the original checked-in production migration `supabase/migrations/202609290003_account_social_foundation.sql` as hosted migration `zerochill_migration_test_social_foundation`. No edits to the source migration; the sandbox now has the original profiles, creator favorites, comments, likes, blocks, DMs, signup profile trigger, initial RLS and avatar bucket.
3. Verified 1 active `on_zerochill_auth_user_created` trigger, 7 `public` tables protected by RLS, and 0 `public` tables without RLS. Auth identities, profiles and all social rows remain empty.
4. Attempted to set up an end-to-end test for a **synthetic** email/password account. The connected execution guard **blocked that write before it executed**. Therefore **no test user was created and no password sign-in test passed**.
5. An optional `pg_net` extension was installed while exploring a direct Auth endpoint test. It was promptly removed by `zerochill_remove_unused_http_test_extension` after a public-schema advisor warning. A second advisor check returned **zero security notices**, and a database count confirmed `pg_net` absent.
6. No account data, password hashes, messages or media were extracted from the OG project. No production or live New Zerochill mutation, APK build, CI workflow or public release.

**What remains for the next operator with an authorized secure coding environment:**

- Finish staging schema parity with the current hosted New Zerochill schema before testing. The original social foundation is a **subset**, not full 7.0 schema parity. Respect the actual hosted migration history and existing code; do not replay every old migration blindly.
- Create **one synthetic test account** on the staging project via Supabase's supported Auth Admin API and perform an email/password login through the staging Auth API. Generate temporary credentials outside source control. Check failed-password behavior, Auth session, identity metadata and profile signup trigger.
- For the **actual password-hash import** test, use vetted private database-layer tooling to transfer only a consenting original test account from OG to this isolated project after separate authorization and verified encrypted backup. `auth.admin.createUser` creates new passwords/UUIDs and is not equivalent to a hash-preserving import.
- Exercise session transition, RLS, favorites, replies, DMs, media ownership and cleanup on staging. Do not send real users signup emails, pushes or webhooks as part of a rehearsal.
- After all automated checks and login verification succeed, independently authorize a full migration of real users. **Do not** confuse the staging project with production.

## Actual Supabase schema rehearsal, November 13

Ran `supabase/tests/new_zerochill_auth_collision_rollback.sql` directly against the **New** Supabase project through a single explicit `BEGIN ... ROLLBACK` batch. This was a **transaction-only synthetic test**, not a real user transfer.

- Two invented test identities were inserted with email/username overlaps. The real Auth insert trigger generated their profiles. Removing those synthetic test identities cascaded their synthetic favorite and profile records.
- Two invented OG-like identities were inserted with the original synthetic email/username claims. Their separate user IDs, auto-created profiles, favorite, and parent/reply comment relationships all passed SQL assertions.
- The explicit `ROLLBACK` ran, and final checks returned **0 leftover Auth users, 0 comments, 0 profiles**.
- A follow-up read-only inventory confirmed **3 existing New Auth users, 2 profiles, 0 favorites, 3 comments, 3 DMs, 1 video like**, unchanged from the preflight snapshot.
- No real emails, passwords, usernames, identity mappings, messages, or media were accessed. The test's password field was empty, so this does **not** prove login or password-hash portability.
- This directly checks New project's deployed PostgreSQL schema and profile trigger. It does **not** create isolated Auth services; the next gate remains an actual disposable test project and login.

The connected Zerochill organization is on Supabase's Free plan. Its cost quotation for one additional project was **$0/month** at the time of check. **No project was created** because creating a new hosted resource requires owner awareness and approval. Confirm the organization and quote before creating the disposable project.

## Offline synthetic rehearsal executed

Since the available execution environment has no local PostgreSQL server or Supabase CLI and cannot create a separate hosted test project for free without cost confirmation, the first rehearsal used an **in-memory SQLite model with entirely fake identities**. This test validates transfer orchestration only, not the actual Supabase Auth/Postgres implementation.

Run from repository root:

```sh
python3 supabase/tests/test_zerochill_synthetic_migration.py -v
```

**Actual result:** 5 tests passed, 0 failures (Python 3.13). The synthetic scenario includes three existing test accounts with conflicting OG emails, a username belonging to a different OG identity, preserved OG UUIDs/fake hash placeholders, favorites, likes, nested comment replies, DMs and reactions, per-user preferences, conversation clear state and an attachment manifest.

Covered cases: reject unknown destination account, discard only fake test identities, preserve every OG row and relationship, repeat the rehearsal without duplicates, and roll back an injected failure without changing its initial state. The first test attempt exposed missing `ON DELETE CASCADE` declarations in the synthetic model. Those declarations were corrected against the repository schema; all five tests then passed. A deeper nested reply case was added and passed.

**Limitations:** SQLite does not match all Supabase/PostgreSQL constraints or triggers. The fake password hash is an opaque placeholder, **not a real migrated password**. No JWT/session, email login, Storage API object bytes or RLS enforcement was tested. No live database was modified. This is not a production import script and must never be adapted to run against live credentials without a separate security review.

## Verified execution boundary

- Live Old/New Supabase: **read-only** inspection previously completed; no production account copy.
- Offline temporary SQLite database: synthetic writes followed by process teardown only; no paid service.
- GitHub: synthetic test script and documentation only. No secrets or identifying customer data committed.
- Next technical gate: actual disposable **Supabase/Postgres with Auth** for one consenting OG account, following a private backup and any required pricing approval.

No Gradle, Android, APK, external SMTP, Auth-login, or storage-upload test ran for this preparation.
