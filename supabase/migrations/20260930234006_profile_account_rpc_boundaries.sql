-- Keep privileged lookups outside the exposed API schema. Public wrappers use
-- caller privileges and explicitly granted private functions still check auth.uid.
alter function public.shared_zerochill_creators(uuid) set schema private;
alter function public.current_zerochill_session() set schema private;

create function public.shared_zerochill_creators(target_user_id uuid)
returns table (creator_key text, creator_name text)
language sql stable security invoker
set search_path = ''
as $$ select * from private.shared_zerochill_creators(target_user_id); $$;

create function public.current_zerochill_session()
returns boolean
language sql stable security invoker
set search_path = ''
as $$ select private.current_zerochill_session(); $$;

revoke all on function public.shared_zerochill_creators(uuid),
    public.current_zerochill_session() from public, anon, authenticated;
grant execute on function public.shared_zerochill_creators(uuid),
    public.current_zerochill_session() to authenticated;
