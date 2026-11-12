# New Zerochill: email and Firebase setup

Status: **partially provisioned** on 2026-11-11 (US Central). Do not claim production email or push works until credentials, Auth settings, social tables, and client testing are complete.

## Scope

Target Supabase project: **New Zerochill** (`aavjpykssueavbseqdlf`). Source: existing Crazyshit Feedback project. Work off currently merged `main` history. This is a separate backend for ZEROCHILL 7.0, not a switch for existing Android installs.

## Completed in the NEW project

Applied the following existing `main` SQL migrations, in order, using Supabase's connected migration interface:

1. `202610250001_push_device_tokens.sql` -> hosted migration `push_device_tokens`
2. `202610250002_tighten_push_device_token_grants.sql` -> hosted migration `tighten_push_device_token_grants`
3. `202610250003_use_firebase_installation_ids.sql` -> hosted migration `use_firebase_installation_ids`
4. `202610250004_push_webhook_security.sql` -> hosted migration `push_webhook_security`

The `push_device_tokens` table is empty and has RLS on, with owner-scoped access rules. The webhook verifier and Firebase service-account reader are service-role-only. This stage **does not** add push-enqueuing triggers.

Created these two *new-project-only* Vault entries, with no secret printed or copied into chat, files or GitHub:
- `zerochill_project_url`: new project's Supabase URL.
- `zerochill_push_webhook_key`: fresh cryptographically random 32-byte key generated within the database.

Deployed `push-dispatch` Edge Function version 1 from `main` (`supabase/functions/push-dispatch/index.ts` and `handler.ts`). It uses `verify_jwt=false` with explicit server-only webhook-key validation, matching the old backend's contract.

Verified through SQL:
- 0 push device registrations.
- Both newly created Vault entries exist, with **no** Firebase service-account entry yet.
- `anon` cannot execute `verify_zerochill_push_webhook`; `authenticated` cannot execute `get_zerochill_firebase_service_account`.
- An invalid webhook key fails verification.
- Authenticated own-device INSERT RLS policy exists.
- `push-dispatch` is ACTIVE according to Supabase deployment metadata; an end-to-end FCM push has **not** been sent.

## User steps still required: Brevo SMTP (no credentials in chat)

Previous working setup used Brevo: SMTP host `smtp-relay.brevo.com`, port `587`, sender `ZEROCHILL <noreply@zerochill.online>`. The domain `zerochill.online` was previously configured for authenticated sending; confirm current provider/DNS status before test delivery. Reuse the existing Brevo account, preferably with a separately rotated/issued SMTP key for this new project.

In **New Zerochill** dashboard, open Authentication > SMTP / Email settings and enter:
- Enable custom SMTP; host `smtp-relay.brevo.com`, port 587.
- Use the **Brevo SMTP login and SMTP key in the dashboard only**, never GitHub, chat or the Android APK.
- Sender name `ZEROCHILL`, sender address `noreply@zerochill.online`.
- Confirm email, password recovery, email-change, and security-notification templates in Authentication > Email Templates. Keep link tracking off in the email provider.
- Allow mobile Auth redirect `com.addy37.zerochill://auth/confirmed` in Auth URL Configuration. This is the literal current `ZeroChillAccountRepository.AUTH_REDIRECT_URI`.
- Send test confirmation/recovery messages to a **disposable account** after the Auth foundation is ready. Do not send real user emails before account migration and rollout approval.

Current connected Supabase management tools **do not expose the hosted Auth SMTP/template configuration endpoint**; this needs a dashboard change or separately authorized secured Management API automation. Never assume old SMTP settings carry to a new project.

## User steps still required: Firebase service account

The old project has Vault entry names `zerochill_firebase_service_account`, `zerochill_project_url`, `zerochill_push_webhook_key`. Only names were inspected, not values. The new project uses its own URL and webhook signing key rather than copying old secrets.

Retain the existing Firebase project and Android app registration. In Firebase Console, create a **new appropriately scoped service-account credential** for FCM HTTP v1 if possible, or retrieve a safely stored current credential through the provider's secure UI. In New Zerochill's Supabase Vault, add a secret:
- Name: `zerochill_firebase_service_account`
- Value: the service-account JSON. Keep JSON private. Do not paste it into this chat, an issue, commit, or build log.

The existing `push-dispatch` code reads it through the restricted `get_zerochill_firebase_service_account` RPC. Once provisioned, verify service-account account/project match and FCM permissions with a disposable test user/device.

## Blocked until social/Auth migration

The remaining push migrations create triggers on `public.direct_messages`, `public.comments`, and `public.comment_likes`; these tables have **not** been created on the new project, pending Auth/user migration. Do not apply those triggers yet.

Do not migrate FCM installation IDs/registrations from old production directly. They are tied to old sessions/accounts and should re-register after successful new-project sign-in in the updated app.

## Validation, impact and rollback

- Existing `main` Android code, Firebase project, installed apps, users and old Supabase remain untouched.
- New project now has 8 hosted migrations total, including initial source-config/feedback setup from earlier.
- Push design is staged only: no actual user pushes can be sent without Firebase credential, Auth/social data and webhook triggers.
- Supabase security advisor shows prior backend-only RLS informational findings and one preexisting feedback trigger search-path warning. No new push-specific advisory findings in the latest result.
- Hosted migration history was generated with new timestamps; reconcile with repository filenames before any later `supabase db push`.
- No Android tests/builds or end-to-end SMTP/FCM tests have been run.
- Rollback is to leave this new staged setup unused while the old project stays live; removing these new objects is a separately reviewed operation, not required now.

References: `AGENTS.md`, `REMOTE_SOURCE_CONFIG.md`, `docs/NEW_ZEROCHILL_PROVISIONING_LOG.md`, `supabase/migrations/20261025000{1..6}_*.sql`, `supabase/functions/push-dispatch/`, `ZeroChillPushRegistration.java`, `ZeroChillAccountRepository.java`.

Official documentation: https://supabase.com/docs/guides/auth/auth-smtp and https://supabase.com/docs/guides/database/vault .
