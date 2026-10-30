# Feedback atomic rate limits

This hardening closes the remaining feedback race documented after the October 29 abuse incident.

The public feedback Edge Function keeps its existing request validation and fast count checks. PostgreSQL now repeats the limits inside `BEFORE INSERT` triggers while holding transaction-scoped advisory locks. Parallel submissions for the same installation, request-source hash, or feedback thread therefore serialize before the row is accepted.

## Limits

Feedback submissions:

- 5 per installation per hour
- 5 per request-source hash per minute
- 20 per request-source hash per hour

User feedback replies:

- 20 per feedback thread per hour
- 10 per request-source hash per minute
- 60 per request-source hash per hour

Developer replies are not subject to the user reply limiter.

## Privacy and access

The guards use the existing salted `source_hash`; no raw request address is stored. The trigger functions live in the private schema, use a pinned empty search path, and have direct execution revoked from public, anonymous, authenticated, and service-role roles. They execute only through their table triggers.

## Validation

Before production application, the migration was compiled and exercised inside a rollback transaction. Validation covered the installation hourly limit, source rotation limit, and feedback reply source limit.

After production application, the live triggers were verified and the same installation and reply limits were exercised again inside a rollback transaction.

The Supabase security advisor was rerun after deployment. It reports the same expected service-only RLS informational findings plus the pre-existing leaked-password-protection warning. No new database security finding was introduced.
