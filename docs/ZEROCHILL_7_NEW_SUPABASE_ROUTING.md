# ZEROCHILL 7 New Zerochill build routing

This branch adds an explicit 7.x build gate and a manually triggered signed Beta workflow. It does not change the default 6.x backend, existing installed clients, databases, schema, accounts or user data.

## Backend selection

- Supabase project: **New Zerochill**, `aavjpykssueavbseqdlf`.
- Auth, profiles, social, DMs, storage and push registration use `ACCOUNT_SUPABASE_URL` and its public publishable key.
- Feedback calls `/functions/v1/dynamic-api` on New Zerochill.
- Remote source configuration calls `/functions/v1/app-config` on New Zerochill, preserving the bundled local fallback.
- Legacy analytics endpoints are empty in 7.0 traffic mode.

Any build with `APP_VERSION_NAME` starting with `7.` now fails its Gradle configuration if low-traffic mode is disabled, the Supabase URL/key are wrong or missing, either remote endpoint is absent/points outside New Zerochill, or the feedback key differs from the New publishable key. This intentionally prevents a 7.0 APK from silently retaining the legacy Supabase defaults. Version 6.x behavior is unchanged.

## Manual test APK

Run `.github/workflows/zerochill-7-new-supabase-beta.yml` manually from its feature branch or, after review and merge, from `main`. It uses the New project's active public key, dedicated endpoints, zero analytics, a side-by-side `.dev` application ID and the existing signing identity. It runs focused Auth/message/push unit tests, builds an APK, then uses `tools/verify_traffic_candidate.py` to validate package, version, signing, included source SHA, disabled analytics and absence of the old Supabase marker in APK contents. An invalid build fails without delivering a test artifact. No workflow is triggered automatically.

This PR has **not** run an Android build, device test or GitHub Actions workflow; those checks only occur when somebody manually starts the workflow.

## Fresh-start policy and release gates

The user chose fresh social signups instead of migrating old cloud accounts. Do not import legacy Auth users/password hashes, bulk email users, or silently merge histories. Existing on-device downloads, history, favorites and settings must not be deleted or reset.

New Zerochill currently has `app-config`, `dynamic-api`, `social-media-upload` and `push-dispatch`. The previously used `account-delete` and `dm-moderation` functions are not deployed there. Audit and restore any required backend capability before shipping 7.0. Also test signup, login, email verification, password reset, account switching, comments, DMs, push delivery, RLS and in-place 6.x-to-7.x upgrade on device. No release, merge or old-project shutdown is authorized by this routing change.
