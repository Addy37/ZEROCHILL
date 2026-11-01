# Later legacy analytics cutoff — NOT activated

Legacy endpoint: `https://fketutffusxgjxjlckci.supabase.co/functions/v1/analytics-ingest`.
New endpoint: `https://fketutffusxgjxjlckci.supabase.co/functions/v1/analytics-ingest-v2`.
First candidate using v2: **5.7.0-beta.1** when analytics is enabled. CI physical-test
Betas explicitly disable analytics. The first stable release using v2 is planned as
5.7.0 and must be recorded here only after its eventual approval/publication.

5.6.2 and earlier installed consumer versions use the legacy endpoint (5.6.2 batches;
older versions may send individual events). They would lose **analytics only** when
that endpoint is later retired. This PR does not activate that retirement.

## Verification before a separately authorized cutoff

1. Deploy/validate v2 while leaving legacy running. Use bounded Edge logs grouped by
   function name, status and hour over matching periods. Count every response status,
   not only 204. Inspect only metadata; do not log request payloads or period keys.
2. Verify v2 204 traffic from an explicitly analytics-enabled supported build and
   aggregate increments in private v2 tables/admin. Confirm legacy single-event and
   5.6.2 batch traffic still return 204. Test retries with a fixture endpoint, not user
   content. Verify a v2-disabled CI Beta sends neither analytics endpoint.
3. Compare old and v2 invocation totals separately over 24 hours and seven days,
   alongside v2 app-version session totals. Eastern dashboard periods do not equal
   an arbitrary trailing UTC log window. Version totals count reporting sessions,
   not unique installs. Estimate remaining old-client traffic without linking users.
4. Save the deployed legacy function bundle/version and settings for rollback.
   Confirm current app versions use v2 and have sufficient adoption for the owner's
   cutoff decision. Confirm all shared-key services listed below still work.

## Exact later activation steps

1. Obtain explicit owner approval for a dated analytics-only cutoff and its loss of
   old-version reporting. Do not revoke shared keys or disable the Supabase project.
2. Retire **only** `analytics-ingest` at the Edge routing/deployment layer. A separate
   approved operation can delete that function using the documented current CLI
   (`supabase functions delete --help` first, then delete `analytics-ingest` for project
   `fketutffusxgjxjlckci`). A deployed 204 stub still incurs Edge invocations and is
   not a quota-saving cutoff. Do not delete v2 or shared database/social functions.
3. Verify old-path requests no longer invoke a deployed analytics function; some
   gateway 404s may still appear. Verify v2 204s and error rates. Run account sign-in,
   comment, like, favorite, DM, feedback, remote-config and updater smoke checks.
4. Monitor total Edge usage and function-specific errors. Record the exact time,
   deployed version, observations and affected versions in this document.

Rollback: redeploy the saved `analytics-ingest` bundle with its prior
`verify_jwt=false` setting, without changing keys, tables or any other function.
Verify original single-event and batch fixtures return 204 again and legacy traffic
resumes. No historical analytics backfill for the cutoff interval is promised.

Old clients continue using Supabase Auth, profiles/account and creator favorites,
comments/replies/likes, DMs/read state/blocks/reports, avatar Storage, notification
preferences, push-device registration/dispatch, feedback, and remote configuration.
They may also use the community-popularity RPC. These services and their shared
publishable/anon keys remain active. The cutoff must not target them.
