-- Disposable test project only: compatibility with the current Android profile query.
-- Full production-schema rehearsal is a separate gate, not silently reproduced here.
alter table public.profiles add column if not exists bio text not null default '';
grant select (bio) on public.profiles to anon, authenticated;
drop policy if exists "users update own creator favorites" on public.creator_favorites;
create policy "users update own creator favorites" on public.creator_favorites
    for update to authenticated using ((select auth.uid())=user_id)
    with check ((select auth.uid())=user_id);
grant update (creator_key,creator_name) on public.creator_favorites to authenticated;
-- Prior rehearsal removed service CRUD grants; restore only fixture operations.
grant select, insert, delete on public.creator_favorites, public.comments,
    public.direct_messages to service_role;
grant select on public.profiles to service_role;
revoke truncate, references, trigger on public.profiles, public.creator_favorites,
    public.comments, public.comment_likes, public.video_likes, public.user_blocks,
    public.direct_messages from anon, authenticated;
