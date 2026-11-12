-- New Zerochill 7.0: one small, privacy-safe read for visible video UI.
-- Replaces separate per-video raw user-id like lists and full comment-feed
-- downloads for UI counters after the Android client is updated.
-- Caller-visible rows are still limited by existing table RLS.
create or replace function public.zerochill_video_social_summary(p_content_key text)
returns table (
  like_count bigint,
  comment_count bigint,
  liked_by_me boolean
)
language sql
stable
security invoker
set search_path = ''
as $function$
  select
    (select count(*) from public.video_likes v where v.content_key = p_content_key),
    (select count(*) from public.comments c
       where c.content_key = p_content_key and c.deleted_at is null),
    exists(
      select 1 from public.video_likes v
       where v.content_key = p_content_key
         and v.user_id = (select auth.uid())
    )
  where p_content_key is not null and char_length(p_content_key) between 16 and 128;
$function$;

revoke all on function public.zerochill_video_social_summary(text)
  from public, anon, authenticated;
grant execute on function public.zerochill_video_social_summary(text)
  to anon, authenticated;
