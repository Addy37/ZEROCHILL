-- New Zerochill isolated cutover prerequisites.
-- Supports source admin publishing and feedback abuse controls before accounts/social migrate.
create schema if not exists private;

alter table public.app_feedback add column if not exists source_hash text;
alter table public.feedback_messages add column if not exists source_hash text;

create index if not exists app_feedback_source_created_idx
  on public.app_feedback (source_hash, created_at desc)
  where source_hash is not null;

create index if not exists feedback_messages_source_created_idx
  on public.feedback_messages (source_hash, created_at desc)
  where source_hash is not null;

comment on column public.app_feedback.source_hash is
  'Salted one-way hash of request source for server-side feedback abuse throttling.';
comment on column public.feedback_messages.source_hash is
  'Salted one-way hash of request source for server-side feedback abuse throttling.';

-- Restore service-role-only publication, matching the existing working backend.
grant execute on function public.publish_source_config(jsonb, bigint, text) to service_role;
-- Timestamp trigger uses pg_catalog.now; prevent a mutable search path.
alter function public.set_feedback_updated_at() set search_path = pg_catalog;
