-- New Zerochill: low-cost retention for the private social abuse-throttling ledger.
-- The application only consults events from the last 5 minutes; keep 48 hours
-- to allow conservative troubleshooting while bounding long-term table growth.
-- One local SQL job each day. No HTTP calls, analytics events, or client traffic.
create extension if not exists pg_cron with schema pg_catalog;

create index if not exists social_rate_events_created_at_idx
  on private.social_rate_events (created_at);

select cron.schedule(
  'zerochill-social-rate-ledger-retention',
  '17 04 * * *',
  $job$
    delete from private.social_rate_events
    where created_at < now() - interval '48 hours';
  $job$
);
