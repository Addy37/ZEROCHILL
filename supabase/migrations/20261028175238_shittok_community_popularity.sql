-- Read-only anonymous aggregate used by Smart ShitTok v1.
-- Existing video_likes remains the authority; no user identity is exposed through this view.
create index if not exists video_likes_created_content_idx
    on public.video_likes (created_at desc, content_key);

create or replace view public.shittok_video_popularity
with (security_invoker = true)
as
select
    content_key,
    count(*)::integer as likes,
    count(*) filter (where created_at >= now() - interval '24 hours')::integer as recent_24h,
    count(*) filter (where created_at >= now() - interval '7 days')::integer as recent_7d
from public.video_likes
group by content_key;

revoke all on public.shittok_video_popularity from public, anon, authenticated;
grant select on public.shittok_video_popularity to anon, authenticated;
