-- Additive social media and participant-owned reactions. Existing text rows are unchanged.
alter table public.comments add column attachment_bucket text not null default '', add column attachment_path text not null default '';
alter table public.direct_messages add column attachment_bucket text not null default '', add column attachment_path text not null default '';
alter table public.comments add constraint comment_attachment_pair check ((attachment_bucket = '' and attachment_path = '') or (attachment_bucket in ('social-photos','social-gifs') and attachment_path = user_id::text || '/' || id::text));
alter table public.direct_messages add constraint dm_attachment_pair check ((attachment_bucket = '' and attachment_path = '') or (attachment_bucket in ('social-photos','social-gifs') and attachment_path = sender_id::text || '/' || id::text));
create unique index comments_attachment_idx on public.comments(attachment_bucket,attachment_path) where attachment_path <> '';
create unique index dm_attachment_idx on public.direct_messages(attachment_bucket,attachment_path) where attachment_path <> '';

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
('social-photos','social-photos',false,5242880,array['image/jpeg','image/png','image/webp']),
('social-gifs','social-gifs',false,10485760,array['image/gif']);

-- Immutable objects: no UPDATE/upsert policy. Names are owner UUID / generated row UUID.
-- Serialize uploads per account to make a generous staging quota resistant to parallel requests.
create function private.can_upload_social_media(object_name text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := auth.uid();
begin
 if actor_id is null or not private.current_zerochill_session() or private.is_socially_banned(actor_id)
    or object_name !~ ('^' || actor_id::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then return false; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(actor_id::text, 31));
 return (select count(*) < 40 from storage.objects where bucket_id in ('social-photos','social-gifs')
   and (storage.foldername(name))[1] = actor_id::text and created_at > now() - interval '5 minutes');
end $$;
revoke all on function private.can_upload_social_media(text) from public, anon;
grant execute on function private.can_upload_social_media(text) to authenticated;
-- No client INSERT/UPDATE policy: only the authenticated, byte-validating Edge uploader
-- writes objects with its server-only service credential. Even spoofed direct Storage requests fail.
create table private.social_media_uploads (
 id uuid primary key, owner_id uuid not null references auth.users(id) on delete cascade,
 bucket text not null check(bucket in ('social-photos','social-gifs')), created_at timestamptz not null default now()
);
create index social_media_uploads_owner_time_idx on private.social_media_uploads(owner_id,created_at);
alter table private.social_media_uploads enable row level security;
revoke all on private.social_media_uploads from public,anon,authenticated;
create function private.reserve_zerochill_media_upload(media_id uuid,media_bucket text) returns void
language plpgsql security definer set search_path='' as $$
declare actor_id uuid:=auth.uid();
begin
 if media_id is null or media_bucket not in ('social-photos','social-gifs')
    or not private.can_upload_social_media(actor_id::text||'/'||media_id::text) then
    raise exception 'Upload unavailable' using errcode='42501';
 end if;
 if exists(select 1 from private.social_media_uploads u where u.id=media_id and u.owner_id=actor_id and u.bucket=media_bucket) then return; end if;
 if (select count(*) from private.social_media_uploads u where u.owner_id=actor_id and u.created_at>now()-interval '5 minutes')>=40 then
   raise exception 'Upload rate limit reached' using errcode='42501';
 end if;
 insert into private.social_media_uploads(id,owner_id,bucket) values(media_id,actor_id,media_bucket);
end $$;
revoke all on function private.reserve_zerochill_media_upload(uuid,text) from public,anon;
grant execute on function private.reserve_zerochill_media_upload(uuid,text) to authenticated;
create function public.reserve_zerochill_media_upload(media_id uuid,media_bucket text) returns void
language sql security invoker set search_path='' as $$ select private.reserve_zerochill_media_upload(media_id,media_bucket); $$;
revoke all on function public.reserve_zerochill_media_upload(uuid,text) from public,anon;
grant execute on function public.reserve_zerochill_media_upload(uuid,text) to authenticated;

-- SECURITY INVOKER subqueries retain the existing comment and DM RLS. DM paths do not
-- encode recipient identities and cannot be read by an unrelated authenticated user.
create policy "read visible social media" on storage.objects for select to anon, authenticated using (
 bucket_id in ('social-photos','social-gifs') and (
   (select auth.uid())::text = (storage.foldername(name))[1]
   or exists(select 1 from public.comments c where c.attachment_bucket=bucket_id and c.attachment_path=name and c.deleted_at is null)
 ));
-- anon cannot SELECT direct_messages, so keep participant lookup in a separate authenticated policy.
create policy "participants read dm media" on storage.objects for select to authenticated using (
 bucket_id in ('social-photos','social-gifs') and exists(
 select 1 from public.direct_messages dm where dm.attachment_bucket=bucket_id and dm.attachment_path=name
 and ((select auth.uid())=dm.sender_id or (select auth.uid())=dm.recipient_id)));

-- Privileged existence checks avoid policy recursion. Caller can delete only their own,
-- unreferenced staging object; never delete a successfully attached object on a timeout.
create function private.social_media_unlinked(bucket text, object_name text) returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and (storage.foldername(object_name))[1]=auth.uid()::text
 and not exists(select 1 from public.comments where attachment_bucket=bucket and attachment_path=object_name)
 and not exists(select 1 from public.direct_messages where attachment_bucket=bucket and attachment_path=object_name);
$$;
revoke all on function private.social_media_unlinked(text,text) from public,anon;
grant execute on function private.social_media_unlinked(text,text) to authenticated;
create policy "owners clean staged social media" on storage.objects for delete to authenticated using (
 bucket_id in ('social-photos','social-gifs') and private.social_media_unlinked(bucket_id,name));

create function private.validate_social_attachment() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.attachment_path <> '' and not exists(select 1 from storage.objects o
   where o.bucket_id=new.attachment_bucket and o.name=new.attachment_path) then
   raise exception 'Upload the attachment before posting' using errcode='23514';
 end if;
 return new;
end $$;
revoke all on function private.validate_social_attachment() from public,anon,authenticated;
create trigger validate_comment_attachment before insert on public.comments for each row execute function private.validate_social_attachment();
create trigger validate_dm_attachment before insert on public.direct_messages for each row execute function private.validate_social_attachment();

create or replace view public.comment_feed with (security_invoker=true) as
select c.id,c.content_key,c.parent_id,c.user_id,c.body,c.created_at,p.username,p.display_name,p.avatar_path,
case when c.deleted_at is not null then 0 else (select count(*)::integer from public.comment_likes cl where cl.comment_id=c.id) end as like_count,
c.edited_at,c.deleted_at,
case when c.deleted_at is null then c.attachment_bucket else '' end as attachment_bucket,
case when c.deleted_at is null then c.attachment_path else '' end as attachment_path
from public.comments c join public.profiles p on p.user_id=c.user_id;

create table public.dm_reactions (
 message_id uuid not null references public.direct_messages(id) on delete cascade,
 user_id uuid not null default auth.uid() references public.profiles(user_id) on delete cascade,
 emoji text not null check(emoji in ('❤️','😂','🔥','👍','😮','👎')),
 primary key(message_id,user_id)
);
create index dm_reactions_user_idx on public.dm_reactions(user_id);
alter table public.dm_reactions enable row level security;
create policy "participants read reactions" on public.dm_reactions for select to authenticated using (
 exists(select 1 from public.direct_messages dm where dm.id=message_id and (auth.uid()=dm.sender_id or auth.uid()=dm.recipient_id)));
create function private.can_react_dm(message uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and private.current_zerochill_session() and not private.is_socially_banned(auth.uid())
 and exists(select 1 from public.direct_messages dm where dm.id=message and (auth.uid()=dm.sender_id or auth.uid()=dm.recipient_id)
 and not exists(select 1 from public.user_blocks b where (b.blocker_id=dm.sender_id and b.blocked_id=dm.recipient_id)
 or (b.blocker_id=dm.recipient_id and b.blocked_id=dm.sender_id)));
$$;
revoke all on function private.can_react_dm(uuid) from public,anon;
grant execute on function private.can_react_dm(uuid) to authenticated;
create policy "own participant reaction insert" on public.dm_reactions for insert to authenticated with check(auth.uid()=user_id and private.can_react_dm(message_id));
create policy "own participant reaction update" on public.dm_reactions for update to authenticated using(auth.uid()=user_id and private.can_react_dm(message_id)) with check(auth.uid()=user_id and private.can_react_dm(message_id));
create policy "own participant reaction delete" on public.dm_reactions for delete to authenticated using(auth.uid()=user_id and private.can_react_dm(message_id));
grant select,insert,delete on public.dm_reactions to authenticated;
grant update(emoji) on public.dm_reactions to authenticated;
create function public.toggle_zerochill_dm_reaction(message uuid,reaction text) returns void
language plpgsql security invoker set search_path='' as $$
begin
 if not private.can_react_dm(message) then raise exception 'Reaction unavailable' using errcode='42501'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(message::text || auth.uid()::text, 32));
 if exists(select 1 from public.dm_reactions where message_id=message and user_id=auth.uid() and emoji=reaction) then
   delete from public.dm_reactions where message_id=message and user_id=auth.uid();
 else
   insert into public.dm_reactions(message_id,user_id,emoji) values(message,auth.uid(),reaction)
   on conflict(message_id,user_id) do update set emoji=excluded.emoji;
 end if;
end $$;
create function public.zerochill_dm_reactions(partner uuid) returns table(message_id uuid,emoji text,count bigint,mine boolean)
language sql stable security invoker set search_path='' as $$
 select r.message_id,r.emoji,count(*),bool_or(r.user_id=auth.uid()) from public.dm_reactions r
 join public.visible_zerochill_direct_messages() dm on dm.id=r.message_id
 where (dm.sender_id=auth.uid() and dm.recipient_id=partner) or (dm.recipient_id=auth.uid() and dm.sender_id=partner)
 group by r.message_id,r.emoji;
$$;
revoke all on function public.toggle_zerochill_dm_reaction(uuid,text),public.zerochill_dm_reactions(uuid) from public,anon;
grant execute on function public.toggle_zerochill_dm_reaction(uuid,text),public.zerochill_dm_reactions(uuid) to authenticated;
