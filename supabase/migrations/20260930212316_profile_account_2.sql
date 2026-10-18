-- Profile + Account 2.0. Existing clients may continue reading their old field set.
alter table public.profiles
    add column if not exists bio text not null default '';

alter table public.profiles
    add constraint profiles_bio_length check (char_length(bio) <= 160);

-- Supabase's default table grants were broader than the column grants in the
-- original social migration. Table privileges override column restrictions.
-- Revoke first, then expose the exact existing client operations.
revoke all on public.profiles, public.creator_favorites, public.comments,
    public.comment_likes, public.video_likes, public.user_blocks,
    public.direct_messages, public.social_reports from anon, authenticated;

grant select (user_id, username, username_key, display_name, bio,
    avatar_path, created_at) on public.profiles to anon, authenticated;
grant update (display_name, bio, avatar_path, updated_at)
    on public.profiles to authenticated;

grant select, insert, delete on public.creator_favorites to authenticated;
-- Existing and older Android clients use Prefer: resolution=merge-duplicates.
-- PostgREST needs UPDATE for the creator fields when an upsert conflicts.
drop policy if exists "users update own creator favorites" on public.creator_favorites;
create policy "users update own creator favorites"
    on public.creator_favorites for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
grant update (creator_key, creator_name) on public.creator_favorites
    to authenticated;
grant select on public.comments to anon, authenticated;
grant insert on public.comments to authenticated;
grant update (body, edited_at, deleted_at) on public.comments to authenticated;
grant select on public.comment_likes, public.video_likes to anon, authenticated;
grant insert, delete on public.comment_likes, public.video_likes to authenticated;
grant select, insert, delete on public.user_blocks to authenticated;
grant select, insert on public.direct_messages to authenticated;
grant update (read_at) on public.direct_messages to authenticated;
grant insert on public.social_reports to authenticated;

-- The visible notification history lives on device. Only user choices sync.
create table public.notification_preferences (
    user_id uuid primary key default auth.uid()
        references public.profiles(user_id) on delete cascade,
    replies boolean not null default true,
    likes boolean not null default true,
    direct_messages boolean not null default true,
    creator_updates boolean not null default true,
    app_updates boolean not null default true,
    updated_at timestamptz not null default now()
);

alter table public.notification_preferences enable row level security;

create policy "users read own notification preferences"
    on public.notification_preferences for select to authenticated
    using ((select auth.uid()) = user_id);
create policy "users create own notification preferences"
    on public.notification_preferences for insert to authenticated
    with check ((select auth.uid()) = user_id);
create policy "users update own notification preferences"
    on public.notification_preferences for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

revoke all on public.notification_preferences from anon, authenticated;
grant select, insert on public.notification_preferences to authenticated;
grant update (replies, likes, direct_messages, creator_updates,
    app_updates, updated_at) on public.notification_preferences to authenticated;

create function public.save_zerochill_notification_preferences(choices jsonb)
returns void
language plpgsql security invoker
set search_path = ''
as $$
begin
    if auth.uid() is null or jsonb_typeof(choices) is distinct from 'object' then
        raise exception 'A signed-in account and preference choices are required';
    end if;

    insert into public.notification_preferences (
        user_id, replies, likes, direct_messages, creator_updates, app_updates
    ) values (
        auth.uid(),
        coalesce((choices ->> 'replies')::boolean, true),
        coalesce((choices ->> 'likes')::boolean, true),
        coalesce((choices ->> 'direct_messages')::boolean, true),
        coalesce((choices ->> 'creator_updates')::boolean, true),
        coalesce((choices ->> 'app_updates')::boolean, true)
    ) on conflict (user_id) do update set
        replies = excluded.replies,
        likes = excluded.likes,
        direct_messages = excluded.direct_messages,
        creator_updates = excluded.creator_updates,
        app_updates = excluded.app_updates,
        updated_at = now();
end;
$$;

revoke all on function public.save_zerochill_notification_preferences(jsonb)
    from public, anon, authenticated;
grant execute on function public.save_zerochill_notification_preferences(jsonb)
    to authenticated;

-- An intersection reveals no favorites that the caller has not selected too.
-- An unblocked signed-in user can compare only with one requested profile.
create function public.shared_zerochill_creators(target_user_id uuid)
returns table (creator_key text, creator_name text)
language sql stable security definer
set search_path = ''
as $$
    select mine.creator_key, mine.creator_name
    from public.creator_favorites mine
    join public.creator_favorites theirs
      on theirs.creator_key = mine.creator_key
     and theirs.user_id = target_user_id
    where auth.uid() is not null
      and target_user_id is not null
      and target_user_id <> auth.uid()
      and mine.user_id = auth.uid()
      and not exists (
          select 1 from public.user_blocks b
          where (b.blocker_id = auth.uid() and b.blocked_id = target_user_id)
             or (b.blocker_id = target_user_id and b.blocked_id = auth.uid())
      )
    order by lower(mine.creator_name), mine.creator_key;
$$;

revoke all on function public.shared_zerochill_creators(uuid)
    from public, anon, authenticated;
grant execute on function public.shared_zerochill_creators(uuid) to authenticated;

-- A live session is required for irreversible deletion. Auth user verification
-- alone can accept a still-unexpired access JWT after sign-out.
create function public.current_zerochill_session()
returns boolean
language sql stable security definer
set search_path = ''
as $$
    select auth.uid() is not null
       and nullif(auth.jwt() ->> 'session_id', '') is not null
       and exists (
           select 1 from auth.sessions s
           where s.id = nullif(auth.jwt() ->> 'session_id', '')::uuid
             and s.user_id = auth.uid()
       );
$$;

revoke all on function public.current_zerochill_session()
    from public, anon, authenticated;
grant execute on function public.current_zerochill_session() to authenticated;
