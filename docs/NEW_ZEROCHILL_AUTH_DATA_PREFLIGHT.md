# ZEROCHILL 7.0 Auth and social migration preflight

Status: **read-only inventory completed; production import NOT authorized or performed**.
Snapshot: 2026-11-13 UTC. Based on current approved `main` at `7ff953aa330a2891ea954134550287f09895153b` (merged PR #427). This branch adds no Android behavior, schema migration, Edge Function, deployment, or live user-data changes.

## Projects and scope

- Source: Old Supabase / Crazyshit Feedback, `fketutffusxgjxjlckci`.
- Destination: New Supabase / New Zerochill, `aavjpykssueavbseqdlf`.
- Preserve old installed clients while planning a separate 7.0 cutover.
- Preserve existing destination test users, test messages, media, and the active low-traffic social backend.
- Do **not** migrate abandoned old feedback, legacy analytics history, analytics ingestion, or old installation-bound push tokens.
- The 13 existing `source_config_versions` entries are already present on the destination. Do not insert them twice.

## Read-only measurements

The same SELECT-only inventory in `supabase/tests/new_zerochill_migration_readonly_inventory.sql` was executed against each project successfully; no records were changed.

| Metric | Old | New |
| --- | ---: | ---: |
| Auth users / email identities | 768 / 768 | 3 / 3 |
| Users with password hashes | 768 | 2 |
| Confirmed emails / unconfirmed | 705 / 63 | 2 / 1 |
| Profiles | 768 | 2 |
| Auth users without profiles | 0 | 1 |
| Creator favorites | 6,600 | 0 |
| Comments / replies | 152 / 11 | 3 / 1 |
| Comment likes / video likes | 11 / 5,255 | 2 / 1 |
| Direct messages / reactions | 851 / 8 | 3 / 0 |
| Notification preferences / conversation clear states | 21 / 27 | 0 / 0 |
| Installation push tokens | 343 | 1 |
| Storage objects | 68 | 1 |
| Source-config versions | 13 | 13 |

Other checked relationships: zero orphaned Auth identities, profiles, comment owners, DM senders, or DM recipients in either project. Neither project has duplicate canonical email addresses or duplicate case-folded profile usernames internally. All 768 old Auth users use the email identity provider and have username, adult-confirmation, and terms-acceptance metadata.

A separate read-only catalog comparison found **113 matching columns** across Auth users/identities and 12 public social tables, with no type/nullability differences. The same **20 social foreign keys** exist in each project. Both projects have `on_zerochill_auth_user_created` on `auth.users`. That trigger creates profiles and must be accounted for during import: an unreviewed Auth import could create duplicate profile rows or run into username uniqueness checks.

### Cross-project account conflicts

A private, temporary salted-hash comparison of normalized emails, Auth UUIDs and profile usernames was executed across the two connected projects. **No plaintext account identifiers, hashes, emails, passwords, UUID mappings, or message contents were committed to the repository or included in this report.**

- **3 email collisions**: all three existing New project email addresses also exist in the old project.
- **0 Auth UUID collisions**: those matching emails have *different* Auth user IDs. Directly importing the old users fails or duplicates ownership.
- **1 username collision**, on a **different email identity**. None of the destination username/email pairs matches a source username/email pair exactly.
- The new project has **1 Auth account without a public profile**. Treat it as existing test state, not permission to delete it.
- Those three source accounts with matching emails are associated with **125 creator favorites, 314 video likes, 105 comments, and 248 DMs** (messages where one is sender or recipient; not necessarily all unique ownership). Simply skipping the colliding users would discard substantial history.

**Blocker: the real import needs an explicitly reviewed three-user identity mapping and a policy for the cross-email username collision.** Do not overwrite New project's existing identities or silently rename a real user's handle. Verify same-email ownership with actual login before mapping historical records to destination accounts.

## Required migration approach

1. **Secure backup and staging.** Capture verified, encrypted backups and restoration checkpoints for *both* databases. Perform the first import against an isolated disposable destination that mirrors New project's schema and test users. No backups, plaintext identifiers, message bodies, password hashes, refresh tokens or service credentials belong in GitHub, chat, CI logs, or artifacts. Keep the backups outside repository history and set a bounded retention policy. No complete restore into the current live New project, because it would overwrite test accounts and deployed backend state.
2. **Resolve the 3 identity collisions.** Preserve New project test identities as the default. For matching verified email owners, create a controlled mapping `old_user_id -> new_user_id` and remap all account-owned foreign keys and message sender/recipient references before import. Decide the one unrelated-email username collision with owner approval; do not silently steal, rename, merge, or replace either profile. Preserve historical identity and access boundaries.
3. **Import non-colliding Auth identities.** The remaining 765 source email identities are candidates to retain original UUID, password hash, confirmation status, and Auth provider identity data. Confirm managed-schema compatibility and the supported import method in a secured environment; do not rely on `auth.admin.createUser` to preserve original password hashes. The New project JWT key stays distinct; users sign in again to receive new sessions. Never copy old refresh sessions.
4. **Handle the Auth insertion trigger and profiles.** The target's `on_zerochill_auth_user_created` trigger creates profiles from metadata. Design the staged import to avoid default-created profiles blocking original profile restoration; test uniqueness, usernames, terms, avatars, and RLS. Then restore all supported profile fields and use the reviewed collision mapping consistently.
5. **Restore owner-scoped history.** Plan referentially safe ordering for profiles, creator favorites, video likes, comments and parent replies, comment likes, user blocks/reports, DMs and reactions, notification preferences and conversation-clear state. Use idempotent keys and preserve existing New project test data. Do not change raw message bodies or attachment ownership without verified mappings.
6. **Restore media bytes, not only storage rows.** Old has 49 avatar objects, 9 private social photos and 10 private GIFs. New already has 1 private photo. Copy encrypted/authenticated media through supported Storage APIs or vetted migration tooling, compare object counts and byte hashes, then test the public/private bucket access policies.
7. **Re-register push tokens.** Never transplant the 343 old installation tokens to the new project. Existing New tokens stay; each 7.0 client should register its own token after login, with duplicates bounded by the low-traffic guard.
8. **Repair missing New backend capabilities before cutover.** The destination currently lacks deployed `account-delete` and `dm-moderation` functions that exist on Old. Review how Android and moderators use them, deploy the required functions under existing backend security patterns, and run real authenticated tests. Old `notification-test-bot-bootstrap` is not required for consumer migration unless a separate admin/test workflow calls it. Keep legacy analytics disabled.
9. **Operational checks.** Verify Auth email provider, custom SMTP/email confirmation, email-password login, and Firebase push. The New project's security advisor warns that leaked-password protection is disabled. Four RLS-without-policy notices correspond to backend-only tables and should remain intentionally protected, not publicly opened. Reconcile hosted migration history before running `supabase db push` against the new project.
10. **Cutoff and rollback.** Snapshot the source again at an approved write freeze. Reconcile changed rows and object bytes since the first export. Validate imported row counts, stable old and destination-owned data, account ownership, RLS, sign-in, DMs, favorites, media and push. Old clients continue using Old; reversing the new client endpoint alone cannot safely recover messages written after cutover. Document how divergent writes will be reconciled before authorizing launch.

## Acceptance gates before a production copy

- [x] Separate old and new project identities verified
- [x] Source/target aggregate table inventory, Auth metadata and FK/catalog checks
- [x] Cross-project email, UUID, and username conflict counts without exposing user identifiers
- [x] Existing backend function and security-advisor inventory
- [x] Read-only SQL file exercised on both actual databases
- [ ] Owner-approved identity and username-conflict policy
- [ ] Disposable staging environment with securely backed-up test users and original schema
- [ ] Auth import trial with UUID/password-hash/sign-in parity and no live target mutations
- [ ] Media transfer trial with private access and file-byte verification
- [ ] Existing real test accounts and old-account identity mapping proven safe
- [ ] Backend account-delete/moderation, SMTP and push acceptance checks
- [ ] Planned write freeze, delta import, post-import reconciliation and rollback rehearsal
- [ ] Separate explicit authorization to transfer production user records
- [ ] Separate 7.0 production release approval

## Documentation checked

- [Supabase: Migrating Auth Users Between Projects](https://supabase.com/kb/troubleshooting/migrating-auth-users-between-projects), updated November 3, 2026. Password hashes can move at the database layer; project-specific sessions do not carry over.
- [Supabase: Backup and Restore using CLI](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore). Full backup restore is not an acceptable shortcut for this populated destination.
- [Supabase CLI v3 breaking change](https://supabase.com/changelog/supabase-cli-v3), November 4, 2026. Explicit `--remote` is required for hosted commands and explicit consent for hosted mutation. Inspect installed CLI version before writing any command.
- Existing staged PRs #418 through #421 and #424, including `docs/NEW_ZEROCHILL_SUPABASE_MIGRATION.md`, `docs/NEW_ZEROCHILL_SOCIAL_STAGING.md`, and `docs/NEW_ZEROCHILL_TRAFFIC_PLAN.md` on their draft branches.

**Validation actually performed:** read-only SQL on both databases, schema/FK metadata comparison, salted-hash collision comparison, Edge Function inventory and destination security advisor review. **No production import, staging copy, write transactions, Android/Gradle build, email test or live password-auth sign-in was performed.**
