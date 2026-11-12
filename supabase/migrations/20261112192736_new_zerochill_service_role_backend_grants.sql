-- New Zerochill backend-only tables originally lacked service-role table ACLs.
-- Match the original production project's grants without allowing direct app access.
-- RLS is enabled; direct access remains denied to anon and authenticated.
grant select, insert, update, delete
on table public.source_config_versions, public.app_feedback, public.feedback_messages
to service_role;
