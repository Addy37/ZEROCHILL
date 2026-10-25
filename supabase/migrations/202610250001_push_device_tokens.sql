create table if not exists public.push_device_tokens (
    user_id uuid not null references auth.users(id) on delete cascade,
    installation_id uuid not null,
    application_id text not null,
    fcm_token text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, installation_id, application_id),
    constraint push_device_tokens_application_id_check check (
        application_id in (
            'com.addy37.crazyshitunofficial',
            'com.addy37.crazyshitunofficial.dev'
        )
    ),
    constraint push_device_tokens_fcm_token_length_check check (
        char_length(fcm_token) between 20 and 4096
    ),
    constraint push_device_tokens_fcm_token_key unique (fcm_token)
);

alter table public.push_device_tokens enable row level security;

revoke all on table public.push_device_tokens from anon, authenticated;
grant select, insert, update, delete on table public.push_device_tokens to authenticated;
grant select, insert, update, delete on table public.push_device_tokens to service_role;

create policy "push tokens select own"
on public.push_device_tokens
for select
to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "push tokens insert own"
on public.push_device_tokens
for insert
to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "push tokens update own"
on public.push_device_tokens
for update
to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id)
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

create policy "push tokens delete own"
on public.push_device_tokens
for delete
to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);
