# Supabase invocation optimization

Base: approved released main `87e3378bd53913863a4282bd522c872d2f84ae60` (PR #378,
5.6.1), also confirmed by PR #394's declared base. The older rebrand branch
instruction is historical. This branch excludes PR #394's updater and 5.6.2 metadata.

## Problem and design

Production project `fketutffusxgjxjlckci` (Crazyshit Feedback) reported 39,453
analytics invocations and 4,293 config invocations in the inspected 24-hour logs
on November 1, consistent with the supplied 39,567 / 4,287 evidence. Every response
status counts toward the 500,000/month free allowance. One HTTP call per event,
three quick retry attempts, and a 45-minute config refresh were unsustainable.

`AnalyticsTracker` now builds the same rotating metric/value/UTC-period hashes on
its background worker. `AnalyticsBatchQueue` groups up to 30 events, flushing 60
seconds after the first queued event when smaller. Five startup metrics share the
same timer with navigation/source events. Existing five-second duplicate
suppression stays in place. No new identity, dependency, alarm or worker service.

A batch gets at most one retry after 60 seconds for transport failures, HTTP 429,
or 5xx. Other 4xx responses are not retried. A second transient failure imposes a
five-minute cooldown before draining the next queued events. At most 500 pending
events plus one retry batch are retained in memory. This is best-effort analytics:
process death can lose unsent events, sustained outages can overflow the queue,
and an ambiguous timeout after a committed write can double counts on retry (the
previous single-event client also had this delivery ambiguity). Unique-key counts
remain deduplicated. No claim of exactly-once delivery is made.

`analytics-ingest` continues accepting the original event object and returns 204.
New clients send `{ "events": [ ... ] }`. Every event is validated before any
write; malformed batches are rejected as a whole. Maximum 50 events, maximum
32 KiB of actual streamed request bytes, including requests without Content-Length.
Only current supported source labels are added to the existing allowlist, preventing
current Kaotic/BaddieHub/XMegaDrive events from invalidating unrelated batched
metrics. Unknown source labels are filtered by Android before queueing. No provider
configuration, domains, selectors, routes or headers change.

The new service-role-only `record_analytics_batch(jsonb)` RPC runs one transaction,
calling the existing `record_analytics_metric` inside Postgres for each event.
There is one database network round trip per batch, and a later invalid event
rolls back all its writes. This intentionally reuses the exact existing daily,
weekly and monthly counters and uniqueness implementation. The old RPC, analytics
tables, data, RLS and grants are unchanged. The new RPC is SECURITY INVOKER with an
empty search path, with execution revoked from PUBLIC/anon/authenticated. Service
credentials remain in the Edge Function only. Anonymous endpoint authentication
(`verify_jwt=false`) is preserved and now recorded in repository config.

Remote config uses a two-hour automatic refresh and sends the active version in
`If-None-Match`, supported by the deployed function. A 304 is a successful refresh
and retains the current validated snapshot. Manual refresh bypasses the automatic
interval. Three short failure retries remain; retry counts/deadlines now persist
across process restarts, followed by the normal two-hour cooldown. An unconfigured
endpoint causes no automatic retry loop. Bundled/cache startup and known-good
fallback/validation are unchanged. A 304 saves response bytes, not invocations;
the longer interval is what reduces invocation usage.

## Deployment and validation

The additive migration and compatible analytics endpoint were deployed before
Android batching. Function v5 introduced batches; v6 also accepts current browser
source labels. The original function's `library` acceptance, missing in main, was
preserved. No app-config deployment, source document publication, account change,
analytics reset, signing change or user-data migration occurred.

Local PostgreSQL 17 regression tests passed for the existing device analytics and
new batch RPC, including counters, uniqueness, atomic rollback, old RPC grants and
service_role execution. The batch SQL test also passed against production inside
an explicit transaction that rolled back all test writes.

Node repository source-config/analytics suites and device validation passed.
Deno checked the handler and event validator. Full entrypoint `deno check` was
blocked by network policy for the preexisting esm.sh SDK import; production
function deployment compiled successfully. Fetching the required Supabase
changelog was also blocked by policy; current security/function guidance was read
through the Supabase documentation tool.

The initial local Android test attempt failed because Robolectric's lock file and
Kotlin daemon cache targeted the read-only home. Validation uses an external init
script for a writable test cache and in-process Kotlin compilation, with analytics
and config endpoints empty. No repository build configuration workaround is added.
Final Android and production smoke results are recorded in the PR.

After v5 deployment, the bounded 02:16–02:22 UTC log query returned 52 successful
analytics requests (204) and four successful config reads (200), with no 4xx/5xx.
The security advisor before/after migration reported the same five informational
RLS-without-policy findings on backend-only tables and one preexisting warning
for disabled leaked-password protection. No new finding was introduced.

The PR-only CI production smoke makes four HTTP requests: one old analytics event,
a 30-event batch, one validated config read, and one conditional read. Its
`zerochill-batch-smoke-<run>-<attempt>` creator label should have 31 events and one
unique key in each period. These are labeled synthetic production metrics, not
user data. The job is limited to this feature branch; no secrets are needed.

## Expected savings and release integration

A full normal batch replaces 30 requests with one (96.7% fewer); a 10-event batch
saves 90%. A startup-only five-event session saves 80%, and a very sparse one-event
minute cannot achieve a tenfold reduction without delaying or dropping events.
The queue regression preserves 300 events in ten requests. Two-hour config refresh
reduces steady continuous-process automatic checks by 62.5% versus 45 minutes.

An illustrative fully adopted 10–30x analytics reduction puts the supplied daily
volume at roughly 1,319–3,957 analytics calls, plus ~1,608 automatic config calls,
or ~88k–167k/month for these endpoints. This is an estimate, not a measured fleet
result. Older installs retain their existing traffic until upgraded, session
lengths affect batching, manual refresh/failures add calls, and other functions
consume allowance too. Backend deployment alone cannot lower old-client call rates.

Do not merge automatically. After approval and merge into main, update PR #394 with
the merged baseline, rerun its signed candidate/upgrade checks, and physically
confirm the combined 5.6.2 candidate including Play Protect behavior before final
release approval. No changes to PR #394 were made here.
