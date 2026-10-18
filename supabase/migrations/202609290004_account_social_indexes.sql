create index if not exists comments_user_id_idx
    on public.comments (user_id);

create index if not exists comment_likes_user_id_idx
    on public.comment_likes (user_id);

create index if not exists user_blocks_blocked_id_idx
    on public.user_blocks (blocked_id);

create index if not exists video_likes_user_id_idx
    on public.video_likes (user_id);
