grant usage on schema private to service_role;
-- Separate backend-only v2 aggregates. Legacy analytics functions/tables are untouched.
create table private.analytics_v2_totals (
 hour_et timestamp not null, version text not null, metric text not null, value text not null,
 total bigint not null check(total>=0), primary key(hour_et,version,metric,value)
);
create table private.analytics_v2_active (
 period text not null check(period in ('day','week','month')), start_et date not null,
 key text not null check(key ~ '^[a-f0-9]{64}$'), primary key(period,start_et,key)
);
alter table private.analytics_v2_totals enable row level security;
alter table private.analytics_v2_active enable row level security;
revoke all on private.analytics_v2_totals,private.analytics_v2_active from public,anon,authenticated;
grant select,insert,update,delete on private.analytics_v2_totals,private.analytics_v2_active to service_role;

create function private.analytics_v2_allowed(metric text,value text) returns boolean
language sql immutable set search_path='' as $$
 select case
 when metric in ('sessions','session_ms','shittok_videos','buffering','search','favorites','creator_gallery','update_prompt','account_signin','comments','likes','dms') then value='all'
 when metric='section' then value in ('shittok','shows','onlyfap','library')
 when metric in ('provider_failure','provider_missing') then value in ('crazyshit','efukt','fapzone','fapello','bunkr','wikifeet','wikifeetx','kaotic','baddiehub','xmegadrive','onlyhaven','unknown')
 when metric='playback_failure' then value in ('network','decoder','source','unknown')
 when metric in ('backend_error','account_error') then value in ('auth','permission','rate_limit','network','server','invalid','unknown')
 when metric in ('app_version','update_adoption') then value ~ '^[A-Za-z0-9._+\-]{1,40}$'
 when metric='android_version' then value ~ '^([0-9]{1,2}(\.[0-9]{1,2})?|Unknown)$'
 when metric='device_model' then length(value) between 1 and 80 and value !~ '[[:cntrl:]]' and value !~ '[@:<>]'
 else false end;
$$;
revoke all on function private.analytics_v2_allowed(text,text) from public,anon,authenticated;
grant execute on function private.analytics_v2_allowed(text,text) to service_role;

create function public.record_analytics_v2(summary jsonb) returns void
language plpgsql security invoker set search_path='' as $$
declare
 local_now timestamp := now() at time zone 'America/New_York';
 e jsonb; p text; start_date date; amount bigint;
begin
 if jsonb_typeof(summary->'events') <> 'array' or jsonb_array_length(summary->'events') not between 1 and 160
   or coalesce(summary->>'version','') !~ '^[A-Za-z0-9._+\-]{1,40}$' then raise exception 'Invalid summary'; end if;
 for e in select * from jsonb_array_elements(summary->'events') loop
   if not private.analytics_v2_allowed(e->>'metric',e->>'value') or coalesce(e->>'count','') !~ '^[0-9]{1,8}$' then raise exception 'Invalid metric'; end if;
   amount=(e->>'count')::bigint;
   if amount < 1 or amount > (case when e->>'metric'='session_ms' then 900000 when e->>'metric'='sessions' then 1 else 10000 end) then raise exception 'Invalid total'; end if;
   insert into private.analytics_v2_totals(hour_et,version,metric,value,total)
   values(date_trunc('hour',local_now),summary->>'version',e->>'metric',e->>'value',amount)
   on conflict(hour_et,version,metric,value) do update set total=private.analytics_v2_totals.total+excluded.total;
 end loop;
 foreach p in array array['day','week','month'] loop
   start_date=date_trunc(p,local_now)::date;
   if summary->'active'->>(p||'_period') is distinct from start_date::text
     or coalesce(summary->'active'->>(p||'_key'),'') !~ '^[a-f0-9]{64}$' then raise exception 'Invalid active period'; end if;
   insert into private.analytics_v2_active(period,start_et,key)
     values(p,start_date,summary->'active'->>(p||'_key')) on conflict do nothing;
 end loop;

end $$;
revoke all on function public.record_analytics_v2(jsonb) from public,anon,authenticated;
grant execute on function public.record_analytics_v2(jsonb) to service_role;

create function public.analytics_v2_dashboard() returns jsonb
language sql stable security invoker set search_path='' as $$
 with boundary as (select now() at time zone 'America/New_York' as local_now),
 totals as (select metric,value,sum(total) as n from private.analytics_v2_totals,boundary
   where hour_et >= date_trunc('week',local_now) group by metric,value),
 lists as (select metric,jsonb_agg(jsonb_build_object('value',value,'event_count',n,'unique_users',0,'users_today',0) order by n desc,value) as rows from totals group by metric)
 select jsonb_build_object(
 'generated_at',now(),'timezone','America/New_York',
 'active_users',jsonb_build_object(
   'daily',(select count(*) from private.analytics_v2_active,boundary where period='day' and start_et=local_now::date),
   'weekly',(select count(*) from private.analytics_v2_active,boundary where period='week' and start_et=date_trunc('week',local_now)::date),
   'monthly',(select count(*) from private.analytics_v2_active,boundary where period='month' and start_et=date_trunc('month',local_now)::date)),
 'sections',coalesce((select rows from lists where metric='section'),'[]'),
 'versions',coalesce((select rows from lists where metric='app_version'),'[]'),
 'device_models',coalesce((select rows from lists where metric='device_model'),'[]'),
 'android_versions',coalesce((select rows from lists where metric='android_version'),'[]'),
 'metrics',coalesce((select jsonb_agg(jsonb_build_object('value',metric||' / '||value,'event_count',n,'unique_users',0,'users_today',0) order by metric,value) from totals where metric not in ('section','app_version','device_model','android_version')),'[]'),
 'average_session_seconds',coalesce((select sum(n) filter(where metric='session_ms') / nullif(sum(n) filter(where metric='sessions'),0) / 1000.0 from totals),0),
 'average_shittok_videos',coalesce((select sum(n) filter(where metric='shittok_videos')::numeric / nullif(sum(n) filter(where metric='sessions'),0) from totals),0)
 );
$$;
revoke all on function public.analytics_v2_dashboard() from public,anon,authenticated;
grant execute on function public.analytics_v2_dashboard() to service_role;

-- Abuse controls remain separate from analytics. Hashes rotate hourly at the Edge and
-- are never stored with metric dimensions or anonymous active keys.
create table private.analytics_v2_rate (source_hash text primary key, requests integer not null, created_at timestamptz not null default now());
alter table private.analytics_v2_rate enable row level security;
revoke all on private.analytics_v2_rate from public,anon,authenticated;
grant select,insert,update,delete on private.analytics_v2_rate to service_role;
create function public.allow_analytics_v2_request(source_hash text) returns boolean
language sql volatile security invoker set search_path='' as $$
 insert into private.analytics_v2_rate(source_hash,requests) values(source_hash,1)
 on conflict(source_hash) do update set requests=private.analytics_v2_rate.requests+1
 returning requests<=120;
$$;
create function public.cleanup_analytics_v2() returns void language sql security invoker set search_path='' as $$
 delete from private.analytics_v2_active where start_et < (now() at time zone 'America/New_York')::date-65;
 delete from private.analytics_v2_totals where hour_et < (now() at time zone 'America/New_York')-interval '400 days';
 delete from private.analytics_v2_rate where created_at < now()-interval '2 hours';
$$;
revoke all on function public.allow_analytics_v2_request(text),public.cleanup_analytics_v2() from public,anon,authenticated;
grant execute on function public.allow_analytics_v2_request(text),public.cleanup_analytics_v2() to service_role;

create function public.record_analytics_v2_batch(summaries jsonb) returns void
language plpgsql security invoker set search_path='' as $$
declare s jsonb;
begin
 if jsonb_typeof(summaries)<>'array' or jsonb_array_length(summaries) not between 1 and 5 then raise exception 'Invalid summary batch'; end if;
 for s in select * from jsonb_array_elements(summaries) loop perform public.record_analytics_v2(s); end loop;
end $$;
revoke all on function public.record_analytics_v2_batch(jsonb) from public,anon,authenticated;
grant execute on function public.record_analytics_v2_batch(jsonb) to service_role;
