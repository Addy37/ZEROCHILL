-- New Zerochill project only. Applied as hosted migration 20261113232554.
-- The private admin endpoint uses service_role to read selected social metadata.
-- Existing anon/authenticated policies and write privileges are unchanged.
GRANT SELECT (user_id, username, display_name, bio, avatar_path, created_at, updated_at)
  ON TABLE public.profiles TO service_role;
GRANT SELECT (id, user_id, content_key, canonical_url, video_title, parent_id, body, created_at, deleted_at)
  ON TABLE public.comments TO service_role;
GRANT SELECT (content_key, user_id, created_at)
  ON TABLE public.video_likes TO service_role;
GRANT SELECT (comment_id, user_id, created_at)
  ON TABLE public.comment_likes TO service_role;
-- The admin sees message counts, participants and timestamps, never body or attachments.
GRANT SELECT (id, sender_id, recipient_id, created_at, read_at)
  ON TABLE public.direct_messages TO service_role;
GRANT SELECT (id, reporter_id, reported_user_id, direct_message_id, reason, details, created_at)
  ON TABLE public.social_reports TO service_role;
GRANT SELECT (blocker_id, blocked_id, created_at)
  ON TABLE public.user_blocks TO service_role;
