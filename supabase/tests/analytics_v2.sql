begin;
set local role service_role;
do $$ declare s jsonb; dashboard jsonb; local_now timestamp := now() at time zone 'America/New_York'; begin
s=jsonb_build_object('version','test-v2','events','[{"metric":"sessions","value":"all","count":1},{"metric":"session_ms","value":"all","count":30000},{"metric":"shittok_videos","value":"all","count":4}]'::jsonb,
'active',jsonb_build_object('day_key',repeat('a',64),'week_key',repeat('b',64),'month_key',repeat('c',64),
'day_period',local_now::date::text,'week_period',date_trunc('week',local_now)::date::text,'month_period',date_trunc('month',local_now)::date::text));
perform public.record_analytics_v2(s);
perform public.record_analytics_v2(s);
dashboard=public.analytics_v2_dashboard();
if (dashboard->'active_users'->>'daily')::integer<>1 or (dashboard->'active_users'->>'weekly')::integer<>1 or (dashboard->'active_users'->>'monthly')::integer<>1 then raise exception 'Anonymous active dedup failed'; end if;
if (dashboard->>'average_session_seconds')::numeric<>30 or (dashboard->>'average_shittok_videos')::numeric<>4 then raise exception 'Session averages wrong'; end if;
if dashboard->>'timezone'<>'America/New_York' then raise exception 'Wrong reporting zone'; end if;
begin
 perform public.record_analytics_v2(jsonb_set(s,'{events}','[{"metric":"creator","value":"private name","count":1}]'));
 raise exception 'Unapproved metric accepted';
exception when raise_exception then if sqlerrm='Unapproved metric accepted' then raise; end if; end;
end $$;
reset role;
do $$ begin
 if has_function_privilege('anon','public.record_analytics_v2(jsonb)','execute') or has_function_privilege('authenticated','public.analytics_v2_dashboard()','execute') then raise exception 'V2 backend grants leaked'; end if;
end $$;
rollback;
