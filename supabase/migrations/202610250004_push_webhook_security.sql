create extension if not exists pg_net with schema extensions;

create or replace function public.verify_zerochill_push_webhook(p_secret text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(
        (
            select decrypted_secret = p_secret
            from vault.decrypted_secrets
            where name = 'zerochill_push_webhook_key'
            limit 1
        ),
        false
    );
$$;

revoke execute on function public.verify_zerochill_push_webhook(text) from public, anon, authenticated;
grant execute on function public.verify_zerochill_push_webhook(text) to service_role;

create or replace function public.get_zerochill_firebase_service_account()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select decrypted_secret
    from vault.decrypted_secrets
    where name = 'zerochill_firebase_service_account'
    limit 1;
$$;

revoke execute on function public.get_zerochill_firebase_service_account() from public, anon, authenticated;
grant execute on function public.get_zerochill_firebase_service_account() to service_role;
