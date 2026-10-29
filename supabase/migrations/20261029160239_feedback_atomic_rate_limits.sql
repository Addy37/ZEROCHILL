-- Serialize feedback abuse checks inside PostgreSQL so parallel requests cannot race
-- the Edge Function's count-then-insert prechecks.

create or replace function private.enforce_app_feedback_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    install_hour_count integer;
    source_minute_count integer;
    source_hour_count integer;
begin
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('zc-feedback-install:' || new.installation_hash, 0)
    );

    if new.source_hash is not null then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('zc-feedback-source:' || new.source_hash, 0)
        );
    end if;

    select count(*)::integer
      into install_hour_count
      from public.app_feedback f
     where f.installation_hash = new.installation_hash
       and f.created_at >= pg_catalog.now() - interval '1 hour';

    if install_hour_count >= 5 then
        raise exception using errcode = 'P0001', message = 'zerochill_feedback_rate_limited';
    end if;

    if new.source_hash is not null then
        select count(*)::integer
          into source_minute_count
          from public.app_feedback f
         where f.source_hash = new.source_hash
           and f.created_at >= pg_catalog.now() - interval '1 minute';

        if source_minute_count >= 5 then
            raise exception using errcode = 'P0001', message = 'zerochill_feedback_rate_limited';
        end if;

        select count(*)::integer
          into source_hour_count
          from public.app_feedback f
         where f.source_hash = new.source_hash
           and f.created_at >= pg_catalog.now() - interval '1 hour';

        if source_hour_count >= 20 then
            raise exception using errcode = 'P0001', message = 'zerochill_feedback_rate_limited';
        end if;
    end if;

    return new;
end;
$$;

revoke all on function private.enforce_app_feedback_rate_limit() from public, anon, authenticated, service_role;

drop trigger if exists app_feedback_rate_limit_guard on public.app_feedback;
create trigger app_feedback_rate_limit_guard
before insert on public.app_feedback
for each row execute function private.enforce_app_feedback_rate_limit();

create or replace function private.enforce_feedback_message_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    thread_hour_count integer;
    source_minute_count integer;
    source_hour_count integer;
begin
    if new.sender <> 'user' then
        return new;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('zc-feedback-thread:' || new.feedback_id::text, 0)
    );

    if new.source_hash is not null then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('zc-feedback-message-source:' || new.source_hash, 0)
        );
    end if;

    select count(*)::integer
      into thread_hour_count
      from public.feedback_messages m
     where m.feedback_id = new.feedback_id
       and m.sender = 'user'
       and m.created_at >= pg_catalog.now() - interval '1 hour';

    if thread_hour_count >= 20 then
        raise exception using errcode = 'P0001', message = 'zerochill_feedback_reply_rate_limited';
    end if;

    if new.source_hash is not null then
        select count(*)::integer
          into source_minute_count
          from public.feedback_messages m
         where m.source_hash = new.source_hash
           and m.sender = 'user'
           and m.created_at >= pg_catalog.now() - interval '1 minute';

        if source_minute_count >= 10 then
            raise exception using errcode = 'P0001', message = 'zerochill_feedback_reply_rate_limited';
        end if;

        select count(*)::integer
          into source_hour_count
          from public.feedback_messages m
         where m.source_hash = new.source_hash
           and m.sender = 'user'
           and m.created_at >= pg_catalog.now() - interval '1 hour';

        if source_hour_count >= 60 then
            raise exception using errcode = 'P0001', message = 'zerochill_feedback_reply_rate_limited';
        end if;
    end if;

    return new;
end;
$$;

revoke all on function private.enforce_feedback_message_rate_limit() from public, anon, authenticated, service_role;

drop trigger if exists feedback_messages_rate_limit_guard on public.feedback_messages;
create trigger feedback_messages_rate_limit_guard
before insert on public.feedback_messages
for each row execute function private.enforce_feedback_message_rate_limit();
