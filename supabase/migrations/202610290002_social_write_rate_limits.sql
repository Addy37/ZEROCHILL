-- Rate-limit remaining user-generated social write surfaces without changing client APIs.

create index if not exists comments_user_created_idx
    on public.comments (user_id, created_at desc);

create or replace function private.can_post_zerochill_comment()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    actor uuid := (select auth.uid());
    recent_30_seconds integer;
    recent_5_minutes integer;
begin
    if actor is null then
        return false;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(actor::text || ':comment', 0)
    );

    select count(*)::integer
      into recent_30_seconds
      from public.comments c
     where c.user_id = actor
       and c.created_at >= pg_catalog.now() - interval '30 seconds';

    if recent_30_seconds >= 5 then
        return false;
    end if;

    select count(*)::integer
      into recent_5_minutes
      from public.comments c
     where c.user_id = actor
       and c.created_at >= pg_catalog.now() - interval '5 minutes';

    if recent_5_minutes >= 30 then
        return false;
    end if;

    return true;
end;
$$;

revoke all on function private.can_post_zerochill_comment() from public, anon;
grant execute on function private.can_post_zerochill_comment() to authenticated;

create or replace function private.can_submit_zerochill_report()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    actor uuid := (select auth.uid());
    recent_5_minutes integer;
    recent_hour integer;
begin
    if actor is null then
        return false;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(actor::text || ':report', 0)
    );

    select count(*)::integer
      into recent_5_minutes
      from public.social_reports r
     where r.reporter_id = actor
       and r.created_at >= pg_catalog.now() - interval '5 minutes';

    if recent_5_minutes >= 3 then
        return false;
    end if;

    select count(*)::integer
      into recent_hour
      from public.social_reports r
     where r.reporter_id = actor
       and r.created_at >= pg_catalog.now() - interval '1 hour';

    if recent_hour >= 10 then
        return false;
    end if;

    return true;
end;
$$;

revoke all on function private.can_submit_zerochill_report() from public, anon;
grant execute on function private.can_submit_zerochill_report() to authenticated;

-- Likes can be deleted and reinserted, so keep a private accepted-write ledger for the
-- notification-producing comment-like surface instead of counting only current rows.
create table if not exists private.social_rate_events (
    id bigint generated always as identity primary key,
    actor_id uuid not null,
    event_type text not null,
    created_at timestamptz not null default pg_catalog.now()
);

revoke all on table private.social_rate_events from public, anon, authenticated;

create index if not exists social_rate_events_actor_type_created_idx
    on private.social_rate_events (actor_id, event_type, created_at desc);

create or replace function private.guard_zerochill_comment_like_rate()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    actor uuid := (select auth.uid());
    recent_30_seconds integer;
    recent_5_minutes integer;
begin
    -- Trusted backend writes have no end-user JWT and are not subject to client throttles.
    if actor is null then
        return new;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(actor::text || ':comment_like', 0)
    );

    select count(*)::integer
      into recent_30_seconds
      from private.social_rate_events e
     where e.actor_id = actor
       and e.event_type = 'comment_like'
       and e.created_at >= pg_catalog.now() - interval '30 seconds';

    if recent_30_seconds >= 15 then
        raise exception using errcode = 'P0001', message = 'comment_like_rate_limited';
    end if;

    select count(*)::integer
      into recent_5_minutes
      from private.social_rate_events e
     where e.actor_id = actor
       and e.event_type = 'comment_like'
       and e.created_at >= pg_catalog.now() - interval '5 minutes';

    if recent_5_minutes >= 60 then
        raise exception using errcode = 'P0001', message = 'comment_like_rate_limited';
    end if;

    insert into private.social_rate_events (actor_id, event_type)
    values (actor, 'comment_like');

    return new;
end;
$$;

revoke all on function private.guard_zerochill_comment_like_rate()
    from public, anon, authenticated;

drop trigger if exists comment_likes_rate_guard on public.comment_likes;
create trigger comment_likes_rate_guard
before insert on public.comment_likes
for each row execute function private.guard_zerochill_comment_like_rate();

drop policy if exists "users post own comments" on public.comments;
create policy "users post own comments"
on public.comments
for insert
to authenticated
with check (
    (select auth.uid()) = user_id
    and deleted_at is null
    and edited_at is null
    and private.can_post_zerochill_comment()
);

drop policy if exists "users submit own social reports" on public.social_reports;
create policy "users submit own social reports"
on public.social_reports
for insert
to authenticated
with check (
    (select auth.uid()) = reporter_id
    and reported_user_id <> (select auth.uid())
    and (
        direct_message_id is null
        or exists (
            select 1
            from public.direct_messages dm
            where dm.id = social_reports.direct_message_id
              and (
                  (dm.sender_id = (select auth.uid()) and dm.recipient_id = social_reports.reported_user_id)
                  or
                  (dm.recipient_id = (select auth.uid()) and dm.sender_id = social_reports.reported_user_id)
              )
        )
    )
    and private.can_submit_zerochill_report()
);
