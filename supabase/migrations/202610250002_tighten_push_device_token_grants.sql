revoke all on table public.push_device_tokens from anon, authenticated, service_role;
grant select, insert, update, delete on table public.push_device_tokens to authenticated;
grant select, insert, update, delete on table public.push_device_tokens to service_role;
