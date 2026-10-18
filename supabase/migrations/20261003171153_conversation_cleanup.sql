-- Additive, per-account inbox cleanup. Never deletes shared direct_messages.
create table public.conversation_clear_state (
    user_id uuid not null references public.profiles(user_id) on delete cascade,
    partner_id uuid not null references public.profiles(user_id) on delete cascade,
    cleared_at timestamptz not null,
    primary key (user_id, partner_id),
    check (user_id <> partner_id)
);
alter table public.conversation_clear_state enable row level security;
create policy "users read own conversation cleanup"
    on public.conversation_clear_state for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "users insert own conversation cleanup"
    on public.conversation_clear_state for insert to authenticated
    with check ((select auth.uid()) = user_id);
create policy "users update own conversation cleanup"
    on public.conversation_clear_state for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
revoke all on public.conversation_clear_state from public, anon, authenticated;
grant select on public.conversation_clear_state to authenticated;

-- Only the RPC can write: client clocks cannot hide future messages.
create function private.clear_zerochill_conversation(partner uuid)
returns timestamptz
language plpgsql security definer
set search_path = ''
as $$
declare
    owner_id uuid := auth.uid();
    cutoff timestamptz;
begin
    if owner_id is null or not private.current_zerochill_session() then
        raise exception 'A current account session is required' using errcode = '42501';
    end if;
    if partner is null or partner = owner_id or not exists (
        select 1 from public.direct_messages dm
        where (dm.sender_id = owner_id and dm.recipient_id = partner)
           or (dm.sender_id = partner and dm.recipient_id = owner_id)
    ) then
        raise exception 'Conversation is unavailable' using errcode = '22023';
    end if;
    insert into public.conversation_clear_state(user_id, partner_id, cleared_at)
    values (owner_id, partner, clock_timestamp())
    on conflict (user_id, partner_id) do update
        set cleared_at = greatest(public.conversation_clear_state.cleared_at, excluded.cleared_at)
    returning cleared_at into cutoff;
    return cutoff;
end;
$$;
revoke all on function private.clear_zerochill_conversation(uuid) from public, anon, authenticated;
grant execute on function private.clear_zerochill_conversation(uuid) to authenticated;

create function public.clear_zerochill_conversation(partner uuid)
returns timestamptz
language sql security invoker
set search_path = ''
as $$ select private.clear_zerochill_conversation(partner); $$;
revoke all on function public.clear_zerochill_conversation(uuid) from public, anon, authenticated;
grant execute on function public.clear_zerochill_conversation(uuid) to authenticated;

-- PostgREST applies ordering/limits after this filter. The existing participant
-- RLS remains active, and older clients retain their original shared-row view.
create function public.visible_zerochill_direct_messages()
returns setof public.direct_messages
language sql stable security invoker
set search_path = ''
as $$
    select dm.* from public.direct_messages dm
    left join public.conversation_clear_state state
        on state.user_id = (select auth.uid())
       and state.partner_id = case when dm.sender_id = (select auth.uid())
                                  then dm.recipient_id else dm.sender_id end
    where ((select auth.uid()) = dm.sender_id or (select auth.uid()) = dm.recipient_id)
      and (state.cleared_at is null or dm.created_at > state.cleared_at);
$$;
revoke all on function public.visible_zerochill_direct_messages() from public, anon, authenticated;
grant execute on function public.visible_zerochill_direct_messages() to authenticated;
