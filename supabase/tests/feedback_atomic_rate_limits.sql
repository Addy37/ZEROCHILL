-- Rollback-only regression coverage for database-side feedback rate limiting.
-- Run against a disposable/test database or inside a transaction-capable validation step.

begin;

do $$
declare
    i integer;
    blocked boolean := false;
begin
    for i in 1..5 loop
        insert into public.app_feedback (
            installation_hash, source_hash, type, message, app_version,
            android_version, device, section
        ) values (
            'atomic-test-install', 'atomic-test-source', 'general_feedback',
            'atomic rate-limit test', 'test', 'test', 'test', 'test'
        );
    end loop;

    begin
        insert into public.app_feedback (
            installation_hash, source_hash, type, message, app_version,
            android_version, device, section
        ) values (
            'atomic-test-install', 'atomic-test-source-2', 'general_feedback',
            'atomic rate-limit test', 'test', 'test', 'test', 'test'
        );
    exception when others then
        blocked := position('zerochill_feedback_rate_limited' in sqlerrm) > 0;
    end;

    if not blocked then
        raise exception 'installation limiter did not block the sixth insert';
    end if;
end;
$$;

do $$
declare
    i integer;
    blocked boolean := false;
begin
    for i in 1..5 loop
        insert into public.app_feedback (
            installation_hash, source_hash, type, message, app_version,
            android_version, device, section
        ) values (
            'atomic-test-rotate-' || i::text, 'atomic-test-source-rotate', 'general_feedback',
            'atomic source test', 'test', 'test', 'test', 'test'
        );
    end loop;

    begin
        insert into public.app_feedback (
            installation_hash, source_hash, type, message, app_version,
            android_version, device, section
        ) values (
            'atomic-test-rotate-6', 'atomic-test-source-rotate', 'general_feedback',
            'atomic source test', 'test', 'test', 'test', 'test'
        );
    exception when others then
        blocked := position('zerochill_feedback_rate_limited' in sqlerrm) > 0;
    end;

    if not blocked then
        raise exception 'source limiter did not block the sixth insert';
    end if;
end;
$$;

do $$
declare
    feedback_uuid uuid;
    i integer;
    blocked boolean := false;
begin
    insert into public.app_feedback (
        installation_hash, source_hash, type, message, app_version,
        android_version, device, section
    ) values (
        'atomic-test-reply-install', null, 'general_feedback',
        'atomic reply test', 'test', 'test', 'test', 'test'
    ) returning id into feedback_uuid;

    for i in 1..10 loop
        insert into public.feedback_messages (feedback_id, sender, message, source_hash)
        values (feedback_uuid, 'user', 'reply ' || i::text, 'atomic-test-reply-source');
    end loop;

    begin
        insert into public.feedback_messages (feedback_id, sender, message, source_hash)
        values (feedback_uuid, 'user', 'reply 11', 'atomic-test-reply-source');
    exception when others then
        blocked := position('zerochill_feedback_reply_rate_limited' in sqlerrm) > 0;
    end;

    if not blocked then
        raise exception 'reply limiter did not block the eleventh insert';
    end if;
end;
$$;

rollback;
