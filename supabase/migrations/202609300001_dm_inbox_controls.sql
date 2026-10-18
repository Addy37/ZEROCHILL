create index if not exists direct_messages_recipient_unread_idx
    on public.direct_messages (recipient_id, created_at desc)
    where read_at is null;

drop policy if exists "recipients mark direct messages read" on public.direct_messages;
create policy "recipients mark direct messages read"
    on public.direct_messages for update
    to authenticated
    using ((select auth.uid()) = recipient_id)
    with check ((select auth.uid()) = recipient_id);

grant update (read_at) on public.direct_messages to authenticated;

create table if not exists public.social_reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null default auth.uid()
        references public.profiles(user_id) on delete cascade,
    reported_user_id uuid not null
        references public.profiles(user_id) on delete cascade,
    direct_message_id uuid
        references public.direct_messages(id) on delete set null,
    reason text not null
        check (reason in ('spam', 'harassment', 'other')),
    details text not null default ''
        check (char_length(details) <= 1000),
    created_at timestamptz not null default now(),
    check (reporter_id <> reported_user_id)
);

create index if not exists social_reports_reporter_created_idx
    on public.social_reports (reporter_id, created_at desc);
create index if not exists social_reports_reported_user_idx
    on public.social_reports (reported_user_id, created_at desc);
create index if not exists social_reports_message_idx
    on public.social_reports (direct_message_id)
    where direct_message_id is not null;

alter table public.social_reports enable row level security;

drop policy if exists "users submit own social reports" on public.social_reports;
create policy "users submit own social reports"
    on public.social_reports for insert
    to authenticated
    with check (
        (select auth.uid()) = reporter_id
        and reported_user_id <> (select auth.uid())
        and (
            direct_message_id is null
            or exists (
                select 1
                from public.direct_messages dm
                where dm.id = direct_message_id
                  and (
                      (dm.sender_id = (select auth.uid()) and dm.recipient_id = reported_user_id)
                      or
                      (dm.recipient_id = (select auth.uid()) and dm.sender_id = reported_user_id)
                  )
            )
        )
    );

revoke all on public.social_reports from anon;
revoke select, update, delete on public.social_reports from authenticated;
grant insert on public.social_reports to authenticated;
