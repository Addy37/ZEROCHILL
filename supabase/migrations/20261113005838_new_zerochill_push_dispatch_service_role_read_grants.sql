-- New Zerochill only. Supabase hosted migration 20261113005838.
-- Push-dispatch uses the private service_role credential, but newer projects
-- do not automatically grant its read access to social tables.
-- Keep all application/user permissions and RLS policies unchanged.
--
-- Minimum columns read by supabase/functions/push-dispatch/handler.ts.
grant select (id, sender_id, recipient_id)
  on public.direct_messages to service_role;

grant select (id, parent_id, user_id, canonical_url, deleted_at)
  on public.comments to service_role;

grant select (comment_id, user_id)
  on public.comment_likes to service_role;

grant select (user_id, username, display_name)
  on public.profiles to service_role;

grant select (user_id, direct_messages, replies, likes)
  on public.notification_preferences to service_role;

-- public.push_device_tokens already permits SELECT/DELETE for service_role.
-- Never grant these dispatch reads or private webhook secrets to anon/authenticated.
