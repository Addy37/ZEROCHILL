-- Reusable moderation ban layer for ZEROCHILL social writes.
-- Operational ban records are inserted separately and are not hard-coded in migrations.

create table if not exists private.social_bans (
    user_id uuid primary key references auth.users(id) on delete cascade,
    reason text not null default 'moderation',
    banned_at timestamptz not null default now(),
    banned_until timestamptz null,
    constraint social_bans_reason_check check (char_length(reason) between 1 and 500),
    constraint social_bans_until_check check (banned_until is null or banned_until > banned_at)
);

revoke all on table private.social_bans from public, anon, authenticated;

drop function if exists private.is_socially_banned(uuid);
create function private.is_socially_banned(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog
as $$
    select exists (
        select 1
        from private.social_bans b
        where b.user_id = p_user_id
          and (b.banned_until is null or b.banned_until > now())
    );
$$;

revoke all on function private.is_socially_banned(uuid) from public, anon;
grant execute on function private.is_socially_banned(uuid) to authenticated, service_role;

alter policy "users send direct messages as themselves"
on public.direct_messages
with check (
    (select auth.uid()) = sender_id
    and not private.is_socially_banned(sender_id)
    and private.can_send_zerochill_dm(recipient_id)
);

alter policy "users post own comments"
on public.comments
with check (
    (select auth.uid()) = user_id
    and not private.is_socially_banned(user_id)
    and deleted_at is null
    and edited_at is null
    and private.can_post_zerochill_comment()
);

alter policy "users edit own active comments"
on public.comments
using (
    (select auth.uid()) = user_id
    and deleted_at is null
    and not private.is_socially_banned(user_id)
)
with check (
    (select auth.uid()) = user_id
    and not private.is_socially_banned(user_id)
    and (
        (deleted_at is null and char_length(btrim(body)) between 1 and 2000)
        or (deleted_at is not null and body = 'Comment deleted')
    )
);

alter policy "users like comments as themselves"
on public.comment_likes
with check (
    (select auth.uid()) = user_id
    and not private.is_socially_banned(user_id)
    and exists (
        select 1
        from public.comments comment
        where comment.id = comment_likes.comment_id
          and comment.deleted_at is null
    )
);

alter policy "users like videos as themselves"
on public.video_likes
with check (
    (select auth.uid()) = user_id
    and not private.is_socially_banned(user_id)
);

alter policy "users submit own social reports"
on public.social_reports
with check (
    (select auth.uid()) = reporter_id
    and not private.is_socially_banned(reporter_id)
    and reported_user_id <> (select auth.uid())
    and (
        direct_message_id is null
        or exists (
            select 1
            from public.direct_messages dm
            where dm.id = social_reports.direct_message_id
              and (
                  (dm.sender_id = (select auth.uid()) and dm.recipient_id = social_reports.reported_user_id)
                  or
                  (dm.recipient_id = (select auth.uid()) and dm.sender_id = social_reports.reported_user_id)
              )
        )
    )
    and private.can_submit_zerochill_report()
);

alter policy "users create own blocks"
on public.user_blocks
with check (
    (select auth.uid()) = blocker_id
    and not private.is_socially_banned(blocker_id)
);
