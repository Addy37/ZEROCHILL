# ZEROCHILL 7.0 Admin app

This is a private installable admin APK for the **New Zerochill** project `aavjpykssueavbseqdlf`. The user explicitly retired the old cloud backend and chose fresh-start social accounts.

## Identity and upgrade

- Existing admin application ID stays `com.addy37.crazyshitadmin`; Java/Kotlin package paths remain unchanged.
- Admin version `7.0.0` uses Android versionCode `7000000`, above current admin versionCode `12`.
- The APK must use the repository's existing production signing bundle. Signing credentials are read only in GitHub Actions and never included in source or APK.
- Admin token storage uses a new Android Keystore alias and preference namespace for New Zerochill. Old admin tokens are ignored, not migrated and not deleted from the user's local storage.
- Notifications start from a new local notification cursor, so old-project feedback does not cause false notifications. Background feedback checks remain the existing 15-minute periodic WorkManager job.

## New backend contract

- Feedback and community API: `https://aavjpykssueavbseqdlf.supabase.co/functions/v1/feedback-admin`.
- Source config administration: `https://aavjpykssueavbseqdlf.supabase.co/functions/v1/app-config-admin`.
- Both are already deployed on New Zerochill with `verify_jwt=false` and server-side token hash validation. Neither service-role keys nor the private admin token are in the APK.
- The owner must pair with the **new** private admin token, not an old token. The matching token is held in New Zerochill Vault; it should be retrieved only through the owner's private Supabase admin console, never committed, pasted into chat, or placed in a build variable.
- The backend social and community views are currently **read-only**. Account searching/details, sanitized social activity, report review, feedback inbox/replies/status/delete, source-config validation/publish/rollback use existing API actions. Direct-message text is intentionally not exposed.
- Historical cloud user accounts, comments, DMs and feedback are not migrated.
- New 7.0 does not collect old device analytics. The former Analytics tab is now **Activity** and shows aggregate account, likes, comments, DMs, blocks, and reports counts from the existing `community_overview` admin action. No analytics RPC or minute-by-minute activity poll is used.
- This does not add broad account-ban, account-delete, broadcast-message, or moderation-write actions.

## Private backend read access

Hosted migration `20261113232554_zerochill7_private_admin_social_read_grants` was applied **only** to New Zerochill and recorded in `supabase/migrations/20261113232554_zerochill7_private_admin_social_read_grants.sql`. It grants specific selected columns from community tables to the server-side `service_role` so the existing private admin function can load community overviews, account details, reports and activity. The admin app still authenticates with the existing server-verified private token. Direct-message bodies and attachments are **not** granted to this read path. A read-only permission check confirmed DM metadata is readable and DM bodies are not.

This migration did not change RLS for app users, grant anon/authenticated admin access, add writes, alter existing data or touch the old Supabase project.

## Verification and build

`.github/workflows/zerochill-7-admin-apk.yml` builds a private signed release APK and checks:

1. Focused admin unit tests for endpoint routing, identity, repository data contracts, and UI state logic.
2. Admin release APK compilation.
3. App ID, versionCode, signing certificate, old Supabase marker exclusion, and New project's presence inside the APK.
4. Upload an installation artifact named `ZeroChill-Admin-7.0.0-<shortSHA>.apk` without publishing a public release.

A passed workflow does **not** establish private admin token provisioning to the device, actual sign-in, account search, inbox send/reply, source-config publish/rollback, notification delivery, or upgrade installation on a phone. Perform those checks with the owner before public distribution. Never issue a source-config write solely as an unapproved smoke test.

## Compatibility

The Android application ID, signing identity, existing source-config format, and private admin token authentication model are retained. Only admin token and notification cursors move to new local namespaces. No app user data, legacy cloud projects or New Zerochill data rows change. The New project receives only the documented service-role read grants.
