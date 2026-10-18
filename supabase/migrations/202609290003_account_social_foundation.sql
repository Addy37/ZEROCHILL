create schema if not exists private;

create table if not exists public.profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    username text not null check (username ~ '^[A-Za-z0-9_]{3,20}$'),
    username_key text generated always as (lower(username)) stored,
    display_name text not null default '' check (char_length(display_name) <= 40),
    avatar_path text not null default '',
    adult_confirmed_at timestamptz not null,
    terms_accepted_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists profiles_username_key_unique
    on public.profiles (username_key);

alter table public.profiles enable row level security;

drop policy if exists "profiles are publicly readable" on public.profiles;
create policy "profiles are publicly readable"
    on public.profiles for select
    to anon, authenticated
    using (true);

drop policy if exists "users update own profile" on public.profiles;
create policy "users update own profile"
    on public.profiles for update
    to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

grant select (user_id, username, username_key, display_name, avatar_path, created_at)
    on public.profiles to anon, authenticated;
grant update (display_name, avatar_path, updated_at)
    on public.profiles to authenticated;

create or replace function private.handle_new_zerochill_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    requested_username text := btrim(coalesce(new.raw_user_meta_data ->> 'username', ''));
    adult_confirmed boolean := coalesce((new.raw_user_meta_data ->> 'adult_confirmed')::boolean, false);
    terms_accepted boolean := coalesce((new.raw_user_meta_data ->> 'terms_accepted')::boolean, false);
begin
    if requested_username !~ '^[A-Za-z0-9_]{3,20}$' then
        raise exception 'Invalid username';
    end if;
    if not adult_confirmed or not terms_accepted then
        raise exception 'Adult confirmation and terms acceptance are required';
    end if;

    insert into public.profiles (
        user_id,
        username,
        adult_confirmed_at,
        terms_accepted_at
    ) values (
        new.id,
        requested_username,
        now(),
        now()
    );

    return new;
end;
$$;

revoke all on function private.handle_new_zerochill_user() from public, anon, authenticated;

drop trigger if exists on_zerochill_auth_user_created on auth.users;
create trigger on_zerochill_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_zerochill_user();

create table if not exists public.creator_favorites (
    user_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    creator_key text not null check (char_length(creator_key) between 1 and 160),
    creator_name text not null default '',
    created_at timestamptz not null default now(),
    primary key (user_id, creator_key)
);

alter table public.creator_favorites enable row level security;

drop policy if exists "users read own creator favorites" on public.creator_favorites;
create policy "users read own creator favorites"
    on public.creator_favorites for select
    to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "users add own creator favorites" on public.creator_favorites;
create policy "users add own creator favorites"
    on public.creator_favorites for insert
    to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "users remove own creator favorites" on public.creator_favorites;
create policy "users remove own creator favorites"
    on public.creator_favorites for delete
    to authenticated
    using ((select auth.uid()) = user_id);

grant select, insert, delete on public.creator_favorites to authenticated;

create table if not exists public.comments (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    content_key text not null check (char_length(content_key) between 16 and 128),
    canonical_url text not null check (char_length(canonical_url) between 1 and 2048),
    video_title text not null default '' check (char_length(video_title) <= 300),
    parent_id uuid references public.comments(id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted_at timestamptz
);

create index if not exists comments_content_created_idx
    on public.comments (content_key, created_at);
create index if not exists comments_parent_idx
    on public.comments (parent_id);

alter table public.comments enable row level security;

drop policy if exists "comments are publicly readable" on public.comments;
create policy "comments are publicly readable"
    on public.comments for select
    to anon, authenticated
    using (deleted_at is null);

drop policy if exists "users post own comments" on public.comments;
create policy "users post own comments"
    on public.comments for insert
    to authenticated
    with check ((select auth.uid()) = user_id);

grant select on public.comments to anon, authenticated;
grant insert on public.comments to authenticated;

create table if not exists public.comment_likes (
    comment_id uuid not null references public.comments(id) on delete cascade,
    user_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (comment_id, user_id)
);

alter table public.comment_likes enable row level security;

drop policy if exists "comment likes are publicly readable" on public.comment_likes;
create policy "comment likes are publicly readable"
    on public.comment_likes for select
    to anon, authenticated
    using (true);

drop policy if exists "users like comments as themselves" on public.comment_likes;
create policy "users like comments as themselves"
    on public.comment_likes for insert
    to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "users remove own comment likes" on public.comment_likes;
create policy "users remove own comment likes"
    on public.comment_likes for delete
    to authenticated
    using ((select auth.uid()) = user_id);

grant select on public.comment_likes to anon, authenticated;
grant insert, delete on public.comment_likes to authenticated;

create or replace view public.comment_feed
with (security_invoker = true)
as
select
    c.id,
    c.content_key,
    c.parent_id,
    c.user_id,
    c.body,
    c.created_at,
    p.username,
    p.display_name,
    p.avatar_path,
    (
        select count(*)::integer
        from public.comment_likes cl
        where cl.comment_id = c.id
    ) as like_count
from public.comments c
join public.profiles p on p.user_id = c.user_id
where c.deleted_at is null;

grant select on public.comment_feed to anon, authenticated;

create table if not exists public.video_likes (
    content_key text not null check (char_length(content_key) between 16 and 128),
    user_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (content_key, user_id)
);

alter table public.video_likes enable row level security;

drop policy if exists "video likes are publicly readable" on public.video_likes;
create policy "video likes are publicly readable"
    on public.video_likes for select
    to anon, authenticated
    using (true);

drop policy if exists "users like videos as themselves" on public.video_likes;
create policy "users like videos as themselves"
    on public.video_likes for insert
    to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "users remove own video likes" on public.video_likes;
create policy "users remove own video likes"
    on public.video_likes for delete
    to authenticated
    using ((select auth.uid()) = user_id);

grant select on public.video_likes to anon, authenticated;
grant insert, delete on public.video_likes to authenticated;

create table if not exists public.user_blocks (
    blocker_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    blocked_id uuid not null references public.profiles(user_id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

alter table public.user_blocks enable row level security;

drop policy if exists "users read own blocks" on public.user_blocks;
create policy "users read own blocks"
    on public.user_blocks for select
    to authenticated
    using ((select auth.uid()) = blocker_id);

drop policy if exists "users create own blocks" on public.user_blocks;
create policy "users create own blocks"
    on public.user_blocks for insert
    to authenticated
    with check ((select auth.uid()) = blocker_id);

drop policy if exists "users remove own blocks" on public.user_blocks;
create policy "users remove own blocks"
    on public.user_blocks for delete
    to authenticated
    using ((select auth.uid()) = blocker_id);

grant select, insert, delete on public.user_blocks to authenticated;

create table if not exists public.direct_messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
    recipient_id uuid not null references public.profiles(user_id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now(),
    read_at timestamptz,
    check (sender_id <> recipient_id)
);

create index if not exists direct_messages_sender_recipient_idx
    on public.direct_messages (sender_id, recipient_id, created_at desc);
create index if not exists direct_messages_recipient_sender_idx
    on public.direct_messages (recipient_id, sender_id, created_at desc);

alter table public.direct_messages enable row level security;

drop policy if exists "participants read direct messages" on public.direct_messages;
create policy "participants read direct messages"
    on public.direct_messages for select
    to authenticated
    using ((select auth.uid()) = sender_id or (select auth.uid()) = recipient_id);

create or replace function private.can_send_zerochill_dm(recipient uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        auth.uid() is not null
        and recipient <> auth.uid()
        and not exists (
            select 1
            from public.user_blocks b
            where
                (b.blocker_id = recipient and b.blocked_id = auth.uid())
                or (b.blocker_id = auth.uid() and b.blocked_id = recipient)
        );
$$;

revoke all on function private.can_send_zerochill_dm(uuid) from public, anon;
grant usage on schema private to authenticated;
grant execute on function private.can_send_zerochill_dm(uuid) to authenticated;

drop policy if exists "users send direct messages as themselves" on public.direct_messages;
create policy "users send direct messages as themselves"
    on public.direct_messages for insert
    to authenticated
    with check (
        (select auth.uid()) = sender_id
        and private.can_send_zerochill_dm(recipient_id)
    );

grant select, insert on public.direct_messages to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'avatars',
    'avatars',
    true,
    524288,
    array['image/jpeg', 'image/png', 'image/webp']::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "avatar files are publicly readable" on storage.objects;
create policy "avatar files are publicly readable"
    on storage.objects for select
    to anon, authenticated
    using (bucket_id = 'avatars');

drop policy if exists "users upload own avatar" on storage.objects;
create policy "users upload own avatar"
    on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists "users update own avatar" on storage.objects;
create policy "users update own avatar"
    on storage.objects for update
    to authenticated
    using (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    )
    with check (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists "users delete own avatar" on storage.objects;
create policy "users delete own avatar"
    on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );
