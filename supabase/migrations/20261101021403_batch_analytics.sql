-- One backend RPC/transaction for a bounded batch. Reuse the installed single-event
-- implementation so counters, UTC periods and uniqueness semantics cannot drift.
create or replace function public.record_analytics_batch(p_events jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  event jsonb;
begin
  if p_events is null or jsonb_typeof(p_events) <> 'array' then
    raise exception 'Invalid analytics batch';
  end if;
  if jsonb_array_length(p_events) not between 1 and 50
      or octet_length(p_events::text) > 32768 then
    raise exception 'Invalid analytics batch size';
  end if;
  for event in select value from jsonb_array_elements(p_events) loop
    if jsonb_typeof(event) <> 'object'
      or jsonb_typeof(event->'metric') is distinct from 'string'
      or jsonb_typeof(event->'value') is distinct from 'string'
      or jsonb_typeof(event->'day_key') is distinct from 'string'
      or jsonb_typeof(event->'week_key') is distinct from 'string'
      or jsonb_typeof(event->'month_key') is distinct from 'string' then
      raise exception 'Invalid analytics event';
    end if;
    perform public.record_analytics_metric(event->>'metric', event->>'value',
      event->>'day_key', event->>'week_key', event->>'month_key');
  end loop;
end;
$$;
revoke all on function public.record_analytics_batch(jsonb) from public, anon, authenticated;
grant execute on function public.record_analytics_batch(jsonb) to service_role;
