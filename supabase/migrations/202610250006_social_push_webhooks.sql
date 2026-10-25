create or replace function public.enqueue_zerochill_comment_reply_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    project_url text;
    webhook_key text;
begin
    if new.parent_id is null then
        return new;
    end if;

    select decrypted_secret into project_url
    from vault.decrypted_secrets
    where name = 'zerochill_project_url'
    limit 1;

    select decrypted_secret into webhook_key
    from vault.decrypted_secrets
    where name = 'zerochill_push_webhook_key'
    limit 1;

    if coalesce(project_url, '') = '' or coalesce(webhook_key, '') = '' then
        return new;
    end if;

    perform net.http_post(
        url := rtrim(project_url, '/') || '/functions/v1/push-dispatch',
        body := jsonb_build_object(
            'event_type', 'comment_reply',
            'reply_id', new.id::text
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-zerochill-push-key', webhook_key
        ),
        timeout_milliseconds := 3000
    );
    return new;
end;
$$;

revoke execute on function public.enqueue_zerochill_comment_reply_push() from public, anon, authenticated;

drop trigger if exists comment_reply_push_webhook on public.comments;
create trigger comment_reply_push_webhook
after insert on public.comments
for each row
when (new.parent_id is not null)
execute function public.enqueue_zerochill_comment_reply_push();

create or replace function public.enqueue_zerochill_comment_like_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    project_url text;
    webhook_key text;
begin
    select decrypted_secret into project_url
    from vault.decrypted_secrets
    where name = 'zerochill_project_url'
    limit 1;

    select decrypted_secret into webhook_key
    from vault.decrypted_secrets
    where name = 'zerochill_push_webhook_key'
    limit 1;

    if coalesce(project_url, '') = '' or coalesce(webhook_key, '') = '' then
        return new;
    end if;

    perform net.http_post(
        url := rtrim(project_url, '/') || '/functions/v1/push-dispatch',
        body := jsonb_build_object(
            'event_type', 'comment_like',
            'comment_id', new.comment_id::text,
            'user_id', new.user_id::text
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-zerochill-push-key', webhook_key
        ),
        timeout_milliseconds := 3000
    );
    return new;
end;
$$;

revoke execute on function public.enqueue_zerochill_comment_like_push() from public, anon, authenticated;

drop trigger if exists comment_like_push_webhook on public.comment_likes;
create trigger comment_like_push_webhook
after insert on public.comment_likes
for each row
execute function public.enqueue_zerochill_comment_like_push();
