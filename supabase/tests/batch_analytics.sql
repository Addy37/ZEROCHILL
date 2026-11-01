begin;
do $$
declare
  a jsonb := jsonb_build_object('metric','creator','value','batch-regression-only',
    'day_key',repeat('a',64),'week_key',repeat('b',64),'month_key',repeat('c',64));
  b jsonb;
  period text;
  baseline bigint;
  before_unique bigint;
  bad jsonb;
begin
  select coalesce(sum(event_count),0) into baseline from public.analytics_counters
    where metric='creator' and value='batch-regression-only';
  select count(*) into before_unique from public.analytics_uniques
    where metric='creator' and value='batch-regression-only';
  b := a || jsonb_build_object('day_key',repeat('d',64),'week_key',repeat('e',64),'month_key',repeat('f',64));
  perform public.record_analytics_batch(jsonb_build_array(a,a,b,
    a || jsonb_build_object('metric','app_version','value','batch-regression-only')));
  if (select sum(event_count) from public.analytics_counters where metric='creator' and value='batch-regression-only') <> baseline+9
    or (select count(*) from public.analytics_uniques where metric='creator' and value='batch-regression-only') <> before_unique+6 then
    raise exception 'Batch counters or unique semantics changed';
  end if;
  foreach period in array array['day','week','month'] loop
    if not exists (select from public.analytics_counters where metric='creator'
      and value='batch-regression-only' and period_type=period and event_count>=3) then
      raise exception 'Missing period %',period;
    end if;
  end loop;
  -- A later failure must roll back earlier events in the same RPC.
  begin
    perform public.record_analytics_batch(jsonb_build_array(a,a || '{"metric":"invalid"}'::jsonb));
    raise exception 'Invalid metric accepted';
  exception when others then
    if sqlerrm='Invalid metric accepted' then raise; end if;
  end;
  begin
    perform public.record_analytics_batch(jsonb_build_array(a,a || '{"day_key":"bad"}'::jsonb));
    raise exception 'Invalid key accepted';
  exception when others then
    if sqlerrm='Invalid key accepted' then raise; end if;
  end;
  if (select sum(event_count) from public.analytics_counters where metric='creator' and value='batch-regression-only') <> baseline+9 then
    raise exception 'Failed batch was not atomic';
  end if;
  for bad in select value from jsonb_array_elements(jsonb_build_array(
      'null'::jsonb, '{}'::jsonb, '[]'::jsonb,
      (select jsonb_agg(a) from generate_series(1,51)),
      jsonb_build_array(a || jsonb_build_object('value',repeat('x',32769))),
      jsonb_build_array(a || '{"day_key":null}'::jsonb))) loop
    begin
      perform public.record_analytics_batch(bad);
      raise exception 'Malformed batch accepted';
    exception when others then
      if sqlerrm='Malformed batch accepted' then raise; end if;
    end;
  end loop;
  if has_function_privilege('anon','public.record_analytics_batch(jsonb)','EXECUTE')
    or has_function_privilege('authenticated','public.record_analytics_batch(jsonb)','EXECUTE')
    or not has_function_privilege('service_role','public.record_analytics_batch(jsonb)','EXECUTE')
    or has_function_privilege('anon','public.record_analytics_metric(text,text,text,text,text)','EXECUTE') then
    raise exception 'Analytics privilege regression';
  end if;
end;
$$;
set local role service_role;
select public.record_analytics_batch('[{"metric":"app_open","value":"all","day_key":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","week_key":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","month_key":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}]'::jsonb);
reset role;
rollback;
