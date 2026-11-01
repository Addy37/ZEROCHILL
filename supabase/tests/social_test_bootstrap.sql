-- Disposable test database only: minimal Auth/Storage platform schema, not a production migration.
create role anon; create role authenticated; create role service_role bypassrls;
create schema auth; create schema storage; create schema private;
create table auth.users(id uuid primary key, raw_user_meta_data jsonb);
create table auth.sessions(id uuid primary key,user_id uuid);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create function auth.jwt() returns jsonb language sql stable as $$ select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb $$;
grant usage on schema auth,storage,private to anon,authenticated,service_role;
create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
create table storage.objects(id uuid default gen_random_uuid(),bucket_id text,name text,owner_id text,created_at timestamptz default now(),metadata jsonb default '{}');
alter table storage.objects enable row level security;
create function storage.foldername(text) returns text[] language sql immutable as $$ select string_to_array($1,'/') $$;
grant select,insert,update,delete on storage.objects to anon,authenticated,service_role;
-- Platform-managed helper and unrelated feedback tables needed by social hardening.
create table public.app_feedback(created_at timestamptz);
create table public.feedback_messages(created_at timestamptz);
create function public.rls_auto_enable() returns event_trigger language plpgsql as $$ begin end $$;
create function public.set_feedback_updated_at() returns trigger language plpgsql as $$ begin return new; end $$;
