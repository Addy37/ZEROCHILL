# Email activation: first disposable rehearsal

Implements the plan in draft PR #429 on `main` after PR #427 (`7ff953aa`).
This is an implementation candidate, not a production migration or release.

## Scope and behavior

Android adds **Activate Existing Account** alongside password login. It requests
an email OTP/magic link with `create_user:false`, verifies a code or the existing
implicit-token redirect, asks for a new password twice, and opens the restored
profile. Generic email responses avoid exposing unknown accounts. A 60-second
UI cooldown applies to resend. Expired/invalid verification asks for another email.
No token/password is saved during verification; recreation deliberately restarts
verification. Completion is accepted locally only while its screen attempt is
current and no other login/signout changed the local session.

Enable only with `ZEROCHILL_MIGRATION_TEST=true`,
`SUPABASE_URL=https://xswtgidwhesajavxjbjb.supabase.co`, and the test project's
public `SUPABASE_PUBLISHABLE_KEY`. This mode disables analytics, feedback and
remote source configuration endpoints and is mutually exclusive with production
7.0 traffic mode. No private credential enters Android. Normal builds retain
signup/login and existing backend configuration; activation stays hidden there.

`private.legacy_account_mapping` stores unique old/new UUID pairs and claim state.
It has no client grants or RLS policies: denial is intentional. A signed-in user
can query only their own pending boolean through an invoker RPC. No email lookup,
old UUID claim parameter, or editable user metadata authorizes access.

The completion function validates the bearer through Auth `/user`, requires recent
OTP AMR and a live Auth session, reserves a five-minute claim lease, updates the
password through the user's authenticated Auth API, then marks the mapping claimed.
Concurrent/repeated completion cannot replace an already claimed password. A
failed Auth update releases the lease. An ambiguous failure keeps the lease for
five minutes, after which the user verifies again; if completion succeeded but the
response was lost, normal password login restores access. The service credential
is used only on the backend. The function itself refuses every project except
Migration Test.

Restrictive RLS gates unclaimed mapped users on the available profile/social/history
tables and storage; existing no-mapping accounts retain existing policy behavior.
The private helper performs only an `auth.uid()` lookup with an empty search path.
Public profiles/comments remain subject to their existing anonymous-read design.

## Supporting migration code

`supabase/migration-tools/mapping.mjs` validates one-to-one mappings and remaps
explicit owner/participant fields for profiles, favorites, comments/likes, DMs,
blocks and notification preferences. Missing mappings, unknown relations, unknown
owner fields and credential-bearing row fields fail closed. Comment/reply IDs are
preserved, so reply references remain intact. This is a limited synthetic importer,
not a complete production migration utility. Media ownership/path rewriting and
full-schema reconciliation are deliberately not implemented or approved.

`rehearse-activation.mjs` has a hardcoded test-project guard and no source-database
connection. It provisions random fake `example.invalid` identities through the
supported Auth Admin create endpoint **without a password or invitation/email**,
registers mappings, and imports mapped synthetic history. It uses Admin
`generate_link` to obtain a token privately without sending mail, then exercises
real Auth verification. That proves the token exchange, **not inbox ownership or
email delivery**. Only fake accounts and fake messages are involved. Credentials,
emails, tokens, mappings and message bodies are never printed. Test identities
remain in the disposable project for private inspection, including failed-run
fixtures. The scripts do not enumerate or migrate OG/live users.

## Validation

Backend/mapping unit tests:

```sh
node --test supabase/tests/activation-handler.test.mjs supabase/tests/activation-mapping.test.mjs
```

12 passed. Includes project isolation, validated-bearer ordering, password-only and
stale OTP rejection, client-selected identity rejection, weak passwords, claim
reservation, password-update failure, ambiguous ledger failure, bijection and
relation remapping failures.

Live synthetic integration:

```sh
NODE_USE_ENV_PROXY=1 node supabase/migration-tools/rehearse-activation.mjs
```

Passed against Migration Test only. Covers password-free Auth provisioning, unique
mapping/no remapping, three favorites, comment/DM import, exact verified target
UUID, unknown email with no automatic account creation, unclaimed history denial,
forged bearer and anonymous status denial, wrong target parameters, unmapped user,
weak password, concurrent claim (one 200/one 409), repeat claim, restored username
and history, outsider DM denial, wrong-password rejection, new-password login,
refresh/signout, consumed/invalid token rejection signed-out OTP session rejection, and no duplicate users.
An all-zero invalid hash is **not** a timed expiration test. Provider rate limiting
and mail delivery are **not** proven by this test.

Applied only in the disposable project: the activation migration, invoker-status
and active-session refinements, plus `supabase/tests/activation-test-compat.sql`.
The compatibility fixture adds `profiles.bio`, favorite-upsert support and fixture
service CRUD grants missing from the older rehearsal schema; it is not a full
production-schema deployment. SQL was created with Supabase CLI 2.90.0 and applied
through the connected Supabase tooling. The final migration SQL also recreated
successfully inside a rollback transaction in Migration Test. Security advisors show the intentional
private deny-all RLS informational finding and a leaked-password-protection warning
in the test project's Auth configuration. See the
[password protection guidance](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

Final focused Android suites passed **24 debug and 24 release tests**, with zero
failures, errors or skips on the final implementation commit. Both variants compiled
as dependencies of these unit test tasks. The command was:

```sh
gradle --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:testReleaseUnitTest \
  --tests '*ZeroChillAccount*' --tests '*ProfileAccountIsolationTest' \
  --tests '*ZeroChillActivationSessionCommitTest'
```

Run with the explicit Migration Test variables above, the provided JDK/Android/Gradle
toolchains, a writable JVM `user.home`, and Robolectric's Maven Central repository.
These six suites cover activation UI, lifecycle/deferred completion, same-value
session mutation/signout, validation, existing signup/login UI/security, and profile
account isolation.
The broader debug/release attempt is blocked: API 36 Robolectric artifact
`android-all-instrumented:16-robolectric-13921718-i7` redirects from Maven Central
to `gcp.mirrors.maven.org`, which the environment network policy denies. Full-suite
results must not be described as passing. No fallback to Actions or APK tasks was used.

`supabase/tests/activation-security.sql` passed against Migration Test, asserting
private-ledger denial, service-only provisioning/claim RPCs, own-state RPC grants,
restrictive table/storage policies, and narrowly scoped live-session column grants. No APK, GitHub
Actions, device install, merge or release is part of this work.

## Gates before full migration

- Approved receiver, SMTP/Brevo credentials/templates, allowed redirect, delivery
  and app reopening tests. Code entry requires the email template to include
  `{{ .Token }}`; the existing redirect requires implicit access/refresh tokens.
  PKCE or custom token-hash redirect routes need a separate implementation if used.
- Timed expiration, provider throttling, resend and delivery failure rehearsal.
- Physical Android link/code/password flow, rotation/back, signup/login and restored
  history acceptance. Compilation and Robolectric do not prove device behavior.
- Full destination-schema/RPC/storage audit. Security-definer RPCs can bypass table
  RLS and must be separately gated before enabling activation against that schema.
  Test schema lacks many live account/security/media endpoints.
- Complete relation and media remapping, reconciliation, ownership/path and byte
  integrity checks, full staged rehearsal. No claim of full OG history migration.
- Verified encrypted backups/restoration; collision retirement approval for the
  three live New test identities; consenting real-user trial; final inventory,
  freeze/delta, rollback, release and cutover authorization.

OG and live New Zerochill were not modified. No OG password hashes, sessions,
refresh tokens or signing secrets were read or imported. Existing live identities
were not retired or replaced.
