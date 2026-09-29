create extension if not exists pgcrypto;

create table if not exists public.feedback_messages (
    id uuid primary key default gen_random_uuid(),
    feedback_id uuid not null references public.app_feedback(id) on delete cascade,
    sender text not null check (sender in ('user', 'developer')),
    message text not null check (char_length(message) between 1 and 2000),
    created_at timestamptz not null default now(),
    read_at timestamptz
);

create index if not exists feedback_messages_feedback_created_idx
    on public.feedback_messages (feedback_id, created_at asc, id asc);

create index if not exists feedback_messages_unread_idx
    on public.feedback_messages (feedback_id, sender, read_at)
    where read_at is null;

alter table public.feedback_messages enable row level security;
revoke all on table public.feedback_messages from anon, authenticated;

create or replace function public.touch_feedback_from_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.app_feedback
       set updated_at = greatest(updated_at, new.created_at)
     where id = new.feedback_id;
    return new;
end;
$$;

drop trigger if exists touch_feedback_from_message on public.feedback_messages;
create trigger touch_feedback_from_message
after insert on public.feedback_messages
for each row execute function public.touch_feedback_from_message();

insert into public.feedback_messages (feedback_id, sender, message, created_at)
select f.id, 'user', f.message, f.created_at
from public.app_feedback f
where not exists (
    select 1
    from public.feedback_messages m
    where m.feedback_id = f.id
      and m.sender = 'user'
);

insert into public.feedback_messages (feedback_id, sender, message, created_at)
select f.id, 'developer', trim(f.developer_reply), f.updated_at
from public.app_feedback f
where f.developer_reply is not null
  and trim(f.developer_reply) <> ''
  and not exists (
      select 1
      from public.feedback_messages m
      where m.feedback_id = f.id
        and m.sender = 'developer'
  );
