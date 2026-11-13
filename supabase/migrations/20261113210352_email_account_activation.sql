-- Rehearsal only. Never apply to OG or live New Zerochill during this task.
create schema if not exists private;
create table private.legacy_account_mapping (
    old_auth_id uuid primary key,
    new_auth_id uuid not null unique references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    claimed_at timestamptz,
    claim_ticket uuid,
    claim_started_at timestamptz,
    check (old_auth_id <> new_auth_id)
);
alter table private.legacy_account_mapping enable row level security;
revoke all on private.legacy_account_mapping from public, anon, authenticated;
grant usage on schema private to service_role;
-- Only the provisioning RPC inserts; only claim RPCs update state. No remapping API.
grant select, insert, update on private.legacy_account_mapping to service_role;

create function public.register_legacy_account(old_id uuid, new_id uuid)
returns void language sql security invoker set search_path = '' as $$
    insert into private.legacy_account_mapping(old_auth_id,new_auth_id) values(old_id,new_id);
$$;
revoke all on function public.register_legacy_account(uuid,uuid) from public, anon, authenticated;
grant execute on function public.register_legacy_account(uuid,uuid) to service_role;

grant select (id,user_id,not_after) on auth.sessions to service_role;
create function public.begin_legacy_claim(target_id uuid, ticket uuid, verified_session_id uuid)
returns boolean language plpgsql security invoker set search_path = '' as $$
begin
    update private.legacy_account_mapping
    set claim_ticket=ticket, claim_started_at=now()
    where new_auth_id=target_id and claimed_at is null
      and exists (select 1 from auth.sessions s where s.id=verified_session_id
          and s.user_id=target_id and (s.not_after is null or s.not_after>now()))
      and (claim_started_at is null or claim_started_at < now()-interval '5 minutes');
    return found;
end;
$$;
create function public.finish_legacy_claim(target_id uuid, ticket uuid, succeeded boolean)
returns boolean language plpgsql security invoker set search_path = '' as $$
begin
    update private.legacy_account_mapping
    set claimed_at=case when succeeded then now() else null end,
        claim_ticket=null, claim_started_at=null
    where new_auth_id=target_id and claimed_at is null and claim_ticket=ticket;
    return found;
end;
$$;
revoke all on function public.begin_legacy_claim(uuid,uuid,uuid), public.finish_legacy_claim(uuid,uuid,boolean) from public,anon,authenticated;
grant execute on function public.begin_legacy_claim(uuid,uuid,uuid), public.finish_legacy_claim(uuid,uuid,boolean) to service_role;

-- An OTP session alone cannot expose private history or mutate migrated data.
-- Normal accounts have no mapping and retain their existing policies.
create function private.legacy_access_allowed()
returns boolean language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and not exists (
        select 1 from private.legacy_account_mapping m
        where m.new_auth_id=auth.uid() and m.claimed_at is null
    );
$$;
revoke all on function private.legacy_access_allowed() from public,anon;
grant execute on function private.legacy_access_allowed() to authenticated;
grant usage on schema private to authenticated;
-- Only a signed-in user can learn their OWN activation state. No email/old-ID input.
create function public.legacy_activation_status()
returns boolean language sql stable security invoker set search_path = '' as $$
    select auth.uid() is not null and not private.legacy_access_allowed();
$$;
revoke all on function public.legacy_activation_status() from public, anon;
grant execute on function public.legacy_activation_status() to authenticated;


do $$
declare t text;
begin
    foreach t in array array['profiles','creator_favorites','comments','comment_likes',
        'video_likes','user_blocks','direct_messages','notification_preferences',
        'social_reports','push_device_tokens'] loop
        if to_regclass('public.'||t) is not null then
            execute format('create policy legacy_activation_required on public.%I as restrictive for all to authenticated using ((select private.legacy_access_allowed())) with check ((select private.legacy_access_allowed()))',t);
        end if;
    end loop;
end;
$$;
-- Storage policies are permissive in the existing app; this adds a mandatory gate.
create policy legacy_activation_required on storage.objects as restrictive
    for all to authenticated using ((select private.legacy_access_allowed()))
    with check ((select private.legacy_access_allowed()));
