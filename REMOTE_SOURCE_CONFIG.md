# Remote source configuration

ZeroChill keeps source logic in the APK. Supabase can publish validated data values that change
domains, routes, approved headers, selectors, bounded timeout/retry values, CDN hosts, fallbacks,
and per-source availability.

## Current schema

Schema 1 supports exactly seven configurable sources: Fapello, Bunkr, WikiFeet,
WikiFeet X, Kaotic, OnlyHaven and BaddieHub. CrazyShit and EFukt retain their
existing compiled integrations. Source parsing/control flow remains in Android.
The bundled document is the known-good fallback. The shared backend validator,
Android parser and private Admin editor use the same supported source set.

## Runtime order

1. Parse `app/src/main/assets/source_config_defaults.json`.
2. Replace it with a newer validated local cache when one exists.
3. Start the normal application flow.
4. Check the `app-config` Edge Function on a background thread, at most once every 45 minutes while
   the process is running.
5. Activate and cache only a newer config that passes Android validation.

The previous valid snapshot remains in local storage for rollback. A failed refresh never deletes
the active cache.

## Backend

- `source_config_versions` stores immutable history and one active version.
- `publish_source_config` serializes publishes with a database lock and requires increasing versions.
- `app-config` accepts a Supabase publishable key and returns only the active document.
- `app-config-admin` uses the existing private admin token hash and a backend service credential.
- Rollback copies an older document into a new higher version so phones with a newer cache accept it.
- Direct table access remains closed to `anon` and `authenticated` roles.

## Remote fields

| Source | Remote values |
|---|---|
| Global | Source kill switches on/off, fallback-domain use on/off |
| Fapello | Enabled, base/fallback domains, search/creator/listing/popular-video routes, User-Agent, Referer override, approved headers, request/ajax timeouts, retry count, creator/media/video/image/next/playable selectors, bounded parser regex, CDN hosts |
| Bunkr | Enabled, Balbums index, page/fallback origins, legacy API endpoints, signing URL, download root, User-Agent, Referer override, approved headers, request/sign timeouts, retry count, album/video/image selectors, CDN hosts |
| WikiFeet | Enabled, base/fallback domains, picture/thumbnail hosts, search route/selector, User-Agent, Referer override, approved headers, request/ajax timeouts, retry count |
| WikiFeet X | Same supported values as WikiFeet, with an independent configuration |
| Kaotic | Enabled, base/fallback domains, feed routes, User-Agent, Referer override, approved headers, request timeout/retry count, card/playable selectors, bounded page/media regex |
| OnlyHaven (cum.st) | Enabled, base/fallback domains, media/image CDN bases, creator-search UI/API, category search/list routes and provider category tokens, creator-page/posts routes, User-Agent, Referer override, approved headers, request timeout/retry count, creator/media/playable selectors, bounded creator/media regex |
| BaddieHub | Enabled, base/fallback domains, latest/categories/category pagination routes, User-Agent, Referer override, approved headers, request timeout/retry count, category/card/player selectors |

## Values that remain compiled

- Repository control flow and parsing algorithms
- WebView challenge fallback behavior
- Creator/gallery state and pagination state machines
- Database and SharedPreferences structure
- Player, thumbnail, download, and file behavior
- Android components, permissions, classes, and local paths
- Admin authentication and credential storage behavior
- Header allowlist, URL protocol rules, size limits, schema support, and validation limits

Remote Java, Kotlin, DEX, JavaScript, commands, class names, permissions, and local file paths are not
accepted by the schema.

## One-time deployment

The commands require the Supabase CLI and access to project `fketutffusxgjxjlckci`.

```bash
supabase link --project-ref fketutffusxgjxjlckci
supabase db push
supabase functions deploy app-config
supabase functions deploy app-config-admin
```

The hosted functions receive `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, and publishable-key data
from Supabase. Keep the service-role value in Supabase only. Keep the existing
`FEEDBACK_ADMIN_TOKEN_HASH` function secret because `app-config-admin` uses the same private admin
token as feedback management. Register the public client key that the read-only function accepts:

```bash
supabase secrets set APP_CONFIG_PUBLISHABLE_KEYS='["<the project sb_publishable key>"]'
```

Add these GitHub Actions secrets:

```text
SOURCE_CONFIG_ENDPOINT=https://fketutffusxgjxjlckci.supabase.co/functions/v1/app-config
SUPABASE_PUBLISHABLE_KEY=<the project's sb_publishable key>
ADMIN_SOURCE_CONFIG_ENDPOINT=https://fketutffusxgjxjlckci.supabase.co/functions/v1/app-config-admin
```

Do not add a secret/service-role Supabase key to GitHub Android-build secrets.

Build and install the private admin APK. Deploy both configuration functions with
`verify_jwt = false`, preserving the existing public read endpoint and the private
admin handler's token-hash authentication. Do not change the feedback admin secret.
Deploy the validator and functions before publishing the full Android document.
No database migration is needed: immutable documents remain JSONB.

## Publishing and rollback

1. Open **Source Control** in ZeroChill Admin.
2. Edit supported JSON values.
3. Tap **Validate**. The app assigns the next version and current UTC time.
4. Tap **Publish** only after validation succeeds.
5. Use **History** and **Roll back** to restore an older known-good document as a new version.

The main app normally receives a publish within 45 minutes. Restarting does not force repeated
requests inside the refresh interval.

## Compatibility and rollback

Android requires all seven source objects. An old/incomplete or unknown-source
remote document fails validation as a whole. A failed refresh keeps the current
valid snapshot; invalid persisted caches fall back to bundled defaults on startup.
It does not delete favorites, history, downloads or user data. The public function
also validates before responding, including before returning a conditional 304.
A mismatched version/document or invalid stored document returns 503 so clients
retain their known-good configuration.

New optional OnlyHaven routes `creatorCategoryApi` and `creatorCategorySearchApi`
and `categoryValues` retain compiled fallbacks when absent. Category search accepts
only query/category/limit/offset tokens. Provider category strings are data, not
executable code. The current HTML search uses `creators?cq={query}`.

A complete older compatible document can be restored as a new version. For older
schema-1 snapshots that predate a source or optional field, rollback overlays
historical supported values onto the active full document. It retains new-only
sources and fields, removes unsupported source objects, and validates the result.
It rejects incompatible schemas and unsafe values with 409. Rollback never
reactivates an old version number or overwrites immutable history. If the active
document is incomplete too, publish a complete known-good document before using
legacy rollback. Concurrent publishes/rollbacks can return 409; reload and retry.

Older installed clients with stricter source-key requirements can reject this
seven-source document and keep their own bundled/cached defaults. The server does
not force a data or application upgrade. Application IDs, signing identity and
persisted user-record formats are unchanged.

## Validation and operation

- `node --test supabase/tests/source-config-*.test.mjs` runs schema, handler, auth,
  version and rollback regressions on Node 24 without dependencies.
- `supabase/tests/source-config-cases.json` is shared by backend and Android tests.
- `deno check` validates the shared helpers and both function entrypoints when the
  existing remote Supabase SDK import is available.
- Read the active production version immediately before publication. Publish a
  strictly greater version through the existing serialized `publish_source_config`
  function. Do not derive the next production version from the bundled number.
- Read the public document back, compare all seven source objects, and run it
  through the Android parser/activation tests. Check conditional refresh responses.
- Live contract checks are read-only. A failed probe does not disable sources.

## Production verification, 2026-10-22

The previous active document was v10 with four core sources. The full seven-source
schema-1 document is now production v11. Its four preexisting source sections are
identical to v10; OnlyHaven adds the configured `cq` HTML and category-search routes.
The private validator function is deployment v3. The public reader adds validation
and version/document consistency checks. Both retain their existing auth settings.

Public read-back returned the full v11 document and passed the shared validator.
The query-based conditional refresh returned 304. The hosted gateway weakens ETags
under content encoding, so the public reader also accepts weak/list If-None-Match
headers using normal GET weak comparison. The opt-in Android CI test downloads,
parses and activates the actual public production document.

The real v10/v11 snapshots also passed a simulated legacy rollback to a new complete
version. No production rollback was executed, no immutable history was changed, and
no database migration or user-data edit was made. See PR #323 for final function
versions, CI results and signed Beta lineage.

Read-only source contracts run daily at 07:25 UTC once this workflow is on main,
and on relevant pull requests. They never publish config or disable providers.
