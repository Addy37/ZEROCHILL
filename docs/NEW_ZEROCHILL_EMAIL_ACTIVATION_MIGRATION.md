# ZEROCHILL 7.0: email-verified account migration

Status: **product direction approved, implementation and import not yet performed**. This plan replaces the old password-hash-preservation requirement in draft PR #428; it does **not** waive identity and history ownership checks.

## Product decision

Existing OG users will **not** keep their old Supabase passwords. During their first ZEROCHILL 7.0 sign-in, they prove access to their registered email using a Supabase email link/code and then set a new password. After activation they retain their existing username, profile, favorites, comments, DMs and eligible media. New-user signup and normal returning-user password login remain available.

The user's previous OG email address is the sole initial recovery identifier. **Do not let a self-service signup or an unverified email claim an OG username or any prior data.** People who cannot access their registered inbox require a separately designed manual recovery policy; do not silently reassign accounts.

## Project boundaries

- Read-only source: OG Supabase `fketutffusxgjxjlckci`.
- Disposable rehearsal: ZEROCHILL Migration Test `xswtgidwhesajavxjbjb`.
- Intended live destination: New Zerochill `aavjpykssueavbseqdlf`. **No live changes or bulk email before separate cutover authorization.**
- App repository: `Addy37/ZEROCHILL`, approved baseline `main` after merged PR #427. Follow root `AGENTS.md` and current lineage; do not change application ID, signing, stable install data or existing source behavior.
- Earlier inventory in PR #428: 768 OG users, 768 profiles, 6,600 creator favorites, 5,255 video likes, 152 comments, 851 DMs, 68 media objects; 3 disposable New test identities have colliding OG emails, and one New username collides with another OG identity. Inventory numbers are snapshots only; refresh before migration.

## Design constraints and decisions

1. **Password hashes are out of scope.** Never export/import OG `encrypted_password` or existing sessions, refresh tokens or JWT signing keys. A newly provisioned target account must not be usable via an OG password. No automatic password-reset blasts.
2. **Account provisioning before email activation.** Rehearse Supabase Admin Auth account creation/invitation with a fake user and **no unsolicited email**. Confirm if the supported API can create an email identity without a password; if not, use a generated, unpredictable, never-reused temporary password only on the server, then require a verified email claim. Do not assume a specific Supabase Admin API behavior without a real staging test. Never put service-role keys in Android.
3. **Stable ownership mapping.** Prefer retaining OG UUIDs only if supported by an audited import route. With Supabase Admin API-created UUIDs, store an immutable, private `old_auth_id -> new_auth_id` mapping and remap every foreign key, sender/recipient, reply owner, block edge, preference, and file ownership consistently. Do not equate matching email with a matching existing New test account. Preserve OG usernames as authoritative; map collisions explicitly before target writes.
4. **Email verification first.** An imported but unclaimed account can only be activated by verifying its registered inbox. Do not allow signup or password entry alone to take over an OG profile. Use Supabase `signInWithOtp` / magic-link semantics with `shouldCreateUser: false` or a verified invite/recovery flow, whichever is proven to work for a pre-created account in staging. Always treat the account as unclaimed until the verified session has set a new password.
5. **User-facing flow.** Add `Activate existing account` on the Android account screen. User enters OG email, receives a generic `Check your email` response (do not reveal whether an account exists), opens link or enters code, sets and confirms new password, and lands in their existing account with username/history. Offer retry/cooldown, expired-link recovery, and a way back to sign-in. The current redirect handler in `ZeroChillAccountRepository.java` and `ZeroChillAccountActivity.java` handles `com.addy37.zerochill://auth/confirmed` and expects access/refresh tokens; extend it without breaking existing signup verification, sign-in, sign-out or session refresh, and cover provider differences in recovery/OTP redirects.
6. **Claim completion is authenticated.** Mark account claimed only after validated password update using a **current session owned by the provisioned Auth UUID**. No client-editable metadata as authority, no unauthenticated public lookup, no arbitrary old-ID submission. Keep any ledger backend-private with tightly scoped privileges.
7. **No email flood.** Only send activation email after the account holder requests it in the app. Verify Brevo SMTP, templates, links, provider throttling/limits and mail deliverability. Use rate limits and generic UI errors to prevent enumeration.
8. **Migration data remains protected.** Before production: two encrypted verified backups, revalidated source and destination inventories, documented conflicting test-account retirement plan, storage byte checks, staged real-user consent test, approved final write freeze/delta plan and rollback. Migrate owner-scoped social rows and media only after Auth mappings are validated, honoring RLS and correct ownership. Existing OG clients keep using OG until explicit release.
9. **Existing 3 New test accounts are disposable only after separate approved backed-up replacement.** Neither this plan nor its implementation grants permission to erase them.
10. **Previously unconfirmed OG emails:** require successful verification of the actual mailbox on the new project, do not inherit unverified authentication as proof of ownership.

## Staged implementation, in order

### A. Offline design and synthetic rehearsal (no real users)

- Inspect current Account repository, Activity, deep link manifest, Supabase schema, Auth trigger, and tested existing sign-up paths.
- Create an isolated fake pre-provisioned account using supported Auth Admin API in the **disposable staging project** only. Confirm no bulk email is sent during provisioning.
- Test `shouldCreateUser=false` email OTP/magic link or an invite/recovery alternative, capture activation email privately, verify it produces a session for exactly that new target UUID, set a password, then sign in with that password. Prove old/random password cannot sign in after activation.
- Restore a synthetic profile, at least three favorites, a comment and message involving two fake accounts, and confirm participant-only RLS before and after activation. Verify no extra account or username is created by the email-claim flow. Run negative tests for unknown email, mismatched user, expired code, repeated claim and rate limiting.
- If staging access, an allowed email receiver or security approval is missing, stop and report the specific gate. Do not bypass approval or broaden permissions.

### B. Android feature and mapping implementation (separate focused code PR)

- Implement the `Activate existing account` UX and password setup using existing Java/REST/Auth/session patterns and ZEROCHILL OLED/cyan theme. Preserve normal signup, sign-in, and favorites sync.
- Create migration tooling, mapping/reconciliation and secured state tracking in focused code/migrations, not remote source config. First run local fake fixtures and all relevant unit tests. Test real Android deep links on a device before approving release.
- For a **consenting** OG account trial, use only authorized private reads and imports into disposable staging, excluding messages involving unrelated users unless independently authorized. Do not print emails, hashes, UUID mappings, tokens, or message bodies in logs, PRs or chat.

### C. Production cutover (requires separate explicit approval)

- Verify encrypted backups and restoration, collisions, complete auth/social/media counts, integrity, RLS, SMTP, backend functions, push registration, rollout and rollback.
- Pre-provision all accounts safely without sending email; perform approved delta import/write freeze. Do not delete OG or disable older clients as part of a mere rehearsal.
- Release 7.0 only after owner approval and tests; users activate their accounts on first sign-in.

## Acceptance checklist

- [ ] Synthetic pre-provisioned Auth account created without sending unsolicited email
- [ ] Email ownership proof works for an existing but unclaimed account and creates **no duplicate** user
- [ ] Verified account holder chooses new password and signs in successfully, with normal session refresh/signout
- [ ] Imported username, profile, favorites and social history show up only under the correct claimed identity
- [ ] Auth UUID mapping remaps every dependent relation, or preserved original UUIDs are proven with controlled import
- [ ] Incorrect email / invalid link / unknown user / repeated OTP / expired link / retry / missing SMTP have safe UX
- [ ] No legacy password hashes, old sessions, private credentials or other-user messages exposed
- [ ] No app regression in signup, login, deep links, favorites sync, notifications or backups
- [ ] All offline/synthetic tests documented with exact commands and results
- [ ] Private backups, test account trial, full staging rehearsal, and production launch separately approved

## Supabase documentation checked (November 13, 2026)

- [Passwordless email sign-in](https://supabase.com/docs/guides/auth/auth-email-passwordless): magic links/OTP and `shouldCreateUser: false`.
- [JavaScript signInWithOtp](https://supabase.com/docs/reference/javascript/auth-signinwithotp): email flow and token handling.
- [Password reset](https://supabase.com/docs/reference/javascript/auth-resetpasswordforemail): authenticated recovery/password update concepts.
- [Email templates](https://supabase.com/docs/guides/auth/auth-email-templates): configurable email templates and links/codes.
- [Custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp): production delivery restrictions.

## Work done for this plan

- Inspected currently merged Java account repository/UI and the initial Supabase profile trigger.
- Reviewed the earlier read-only inventory and blocked single-account import reports in draft PR #428.
- Verified Supabase docs support email link/OTP without automatic signup. **No code build, unit test, email delivery, live data import, Auth modification, Android APK or production backend change was performed** in this planning step.
