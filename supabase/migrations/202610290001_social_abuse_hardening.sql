-- ZEROCHILL social abuse hardening.
-- Keeps existing client contracts while enforcing abuse controls on the backend.

alter table public.app_feedback
    add column if not exists source_hash text;

alter table public.feedback_messages
    add column if not exists source_hash text;

create index if not exists app_feedback_source_created_idx
    on public.app_feedback (source_hash, created_at desc)
    where source_hash is not null;

create index if not exists feedback_messages_source_created_idx
    on public.feedback_messages (source_hash, created_at desc)
    where source_hash is not null;

comment on column public.app_feedback.source_hash is
    'Salted one-way hash of the request source used only for server-side abuse throttling.';
comment on column public.feedback_messages.source_hash is
    'Salted one-way hash of the request source used only for server-side abuse throttling.';

create or replace function private.can_send_zerochill_dm(recipient uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    sender uuid := (select auth.uid());
    pair_30_seconds integer;
    pair_5_minutes integer;
    sender_5_minutes integer;
begin
    if sender is null or recipient is null or sender = recipient then
        return false;
    end if;

    if exists (
        select 1
        from public.user_blocks b
        where (b.blocker_id = sender and b.blocked_id = recipient)
           or (b.blocker_id = recipient and b.blocked_id = sender)
    ) then
        return false;
    end if;

    -- Serialize sends from one account so parallel requests cannot race the counters.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(sender::text, 0)
    );

    select count(*)::integer
      into pair_30_seconds
      from public.direct_messages dm
     where dm.sender_id = sender
       and dm.recipient_id = recipient
       and dm.created_at >= pg_catalog.now() - interval '30 seconds';

    if pair_30_seconds >= 8 then
        return false;
    end if;

    select count(*)::integer
      into pair_5_minutes
      from public.direct_messages dm
     where dm.sender_id = sender
       and dm.recipient_id = recipient
       and dm.created_at >= pg_catalog.now() - interval '5 minutes';

    if pair_5_minutes >= 30 then
        return false;
    end if;

    select count(*)::integer
      into sender_5_minutes
      from public.direct_messages dm
     where dm.sender_id = sender
       and dm.created_at >= pg_catalog.now() - interval '5 minutes';

    if sender_5_minutes >= 40 then
        return false;
    end if;

    return true;
end;
$$;

revoke all on function private.can_send_zerochill_dm(uuid) from public;
revoke all on function private.can_send_zerochill_dm(uuid) from anon;
grant execute on function private.can_send_zerochill_dm(uuid) to authenticated;

-- This event-trigger helper is internal database infrastructure, not a client RPC.
revoke execute on function public.rls_auto_enable() from public, anon, authenticated, service_role;

-- Pin the trigger function search path so object resolution cannot be influenced by callers.
alter function public.set_feedback_updated_at() set search_path = pg_catalog;
