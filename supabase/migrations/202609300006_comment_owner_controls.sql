-- Allow signed-in users to edit or tombstone only their own active comments.
-- Deleted comments remain readable only as sanitized placeholders so reply threads
-- and existing notification deep-links keep their structure.

drop policy if exists "comments are publicly readable" on public.comments;
create policy "comments are publicly readable"
    on public.comments for select
    to anon, authenticated
    using (
        deleted_at is null
        or (deleted_at is not null and body = 'Comment deleted')
    );

drop policy if exists "users post own comments" on public.comments;
create policy "users post own comments"
    on public.comments for insert
    to authenticated
    with check (
        (select auth.uid()) = user_id
        and deleted_at is null
        and edited_at is null
        and (
            parent_id is null
            or exists (
                select 1
                from public.comments parent
                where parent.id = parent_id
                  and parent.deleted_at is null
            )
        )
    );

drop policy if exists "users edit own active comments" on public.comments;
create policy "users edit own active comments"
    on public.comments for update
    to authenticated
    using (
        (select auth.uid()) = user_id
        and deleted_at is null
    )
    with check (
        (select auth.uid()) = user_id
        and (
            (
                deleted_at is null
                and char_length(btrim(body)) between 1 and 2000
            )
            or (
                deleted_at is not null
                and body = 'Comment deleted'
            )
        )
    );

revoke all on public.comments from anon, authenticated;
grant select on public.comments to anon, authenticated;
grant insert on public.comments to authenticated;
grant update (body, edited_at, deleted_at) on public.comments to authenticated;

drop policy if exists "users like comments as themselves" on public.comment_likes;
create policy "users like comments as themselves"
    on public.comment_likes for insert
    to authenticated
    with check (
        (select auth.uid()) = user_id
        and exists (
            select 1
            from public.comments comment
            where comment.id = comment_id
              and comment.deleted_at is null
        )
    );

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
    c.edited_at,
    c.deleted_at,
    p.username,
    p.display_name,
    p.avatar_path,
    case
        when c.deleted_at is not null then 0
        else (
            select count(*)::integer
            from public.comment_likes cl
            where cl.comment_id = c.id
        )
    end as like_count
from public.comments c
join public.profiles p on p.user_id = c.user_id;

grant select on public.comment_feed to anon, authenticated;
