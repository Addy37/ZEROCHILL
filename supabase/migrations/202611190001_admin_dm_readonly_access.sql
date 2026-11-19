-- New Zerochill only. Extend private admin service_role column grants for
-- read-only DM review. The consumer application's participant-only RLS remains
-- unchanged, and no anon/authenticated role receives additional access.
GRANT SELECT (body, attachment_bucket, attachment_path)
  ON TABLE public.direct_messages TO service_role;
