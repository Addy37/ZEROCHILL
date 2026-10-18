-- Run against a migrated disposable database (or with a privileged test role).
-- The transaction rolls back fixture users, their cascaded rows, and blocks.
begin;

do $$
begin
  if has_column_privilege('anon', 'public.profiles', 'adult_confirmed_at', 'SELECT')
     or has_column_privilege('authenticated', 'public.profiles', 'terms_accepted_at', 'SELECT')
     or has_column_privilege('authenticated', 'public.profiles', 'username', 'UPDATE')
     or has_column_privilege('authenticated', 'public.direct_messages', 'body', 'UPDATE')
     or not has_column_privilege('authenticated', 'public.direct_messages', 'read_at', 'UPDATE')
     or not has_column_privilege('authenticated', 'public.creator_favorites', 'creator_name', 'UPDATE')
     or has_column_privilege('authenticated', 'public.creator_favorites', 'user_id', 'UPDATE')
     or has_table_privilege('anon', 'public.creator_favorites', 'SELECT') then
    raise exception 'Account/social grants expose a protected field or deny read receipts';
  end if;
end;
$$;

insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_user_meta_data, created_at, updated_at
) values
  ('b20aa450-3550-4a10-9a77-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'profile2-test-a@invalid.example', '', now(),
   '{"username":"profile2testa","adult_confirmed":true,"terms_accepted":true}'::jsonb,
   now(), now()),
  ('b20aa450-3550-4a10-9a77-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'profile2-test-b@invalid.example', '', now(),
   '{"username":"profile2testb","adult_confirmed":true,"terms_accepted":true}'::jsonb,
   now(), now()),
  ('b20aa450-3550-4a10-9a77-000000000003', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'profile2-test-c@invalid.example', '', now(),
   '{"username":"profile2testc","adult_confirmed":true,"terms_accepted":true}'::jsonb,
   now(), now());

insert into public.creator_favorites(user_id, creator_key, creator_name) values
  ('b20aa450-3550-4a10-9a77-000000000001', 'shared', 'Shared creator'),
  ('b20aa450-3550-4a10-9a77-000000000001', 'shared-two', 'Second shared creator'),
  ('b20aa450-3550-4a10-9a77-000000000001', 'private-a', 'Only A'),
  ('b20aa450-3550-4a10-9a77-000000000002', 'shared', 'Shared creator'),
  ('b20aa450-3550-4a10-9a77-000000000002', 'shared-two', 'Second shared creator'),
  ('b20aa450-3550-4a10-9a77-000000000002', 'private-b', 'Only B');

insert into public.direct_messages(id, sender_id, recipient_id, body) values
  ('b20aa450-3550-4a10-9a77-000000000010',
   'b20aa450-3550-4a10-9a77-000000000002',
   'b20aa450-3550-4a10-9a77-000000000001', 'Sample DM');

insert into public.comments(id, user_id, content_key, canonical_url, body) values
  ('b20aa450-3550-4a10-9a77-000000000011',
   'b20aa450-3550-4a10-9a77-000000000002',
   'profile-account-2-test-key', 'https://invalid.example/test', 'Parent');
insert into public.comments(id, user_id, content_key, canonical_url, parent_id, body) values
  ('b20aa450-3550-4a10-9a77-000000000012',
   'b20aa450-3550-4a10-9a77-000000000001',
   'profile-account-2-test-key', 'https://invalid.example/test',
   'b20aa450-3550-4a10-9a77-000000000011', 'Reply by another user');

insert into public.social_reports(reporter_id, reported_user_id, reason) values
  ('b20aa450-3550-4a10-9a77-000000000001',
   'b20aa450-3550-4a10-9a77-000000000002', 'spam');

set local role authenticated;
set local request.jwt.claim.sub = 'b20aa450-3550-4a10-9a77-000000000001';
set local request.jwt.claim.role = 'authenticated';
set local request.jwt.claims = '{"sub":"b20aa450-3550-4a10-9a77-000000000001","role":"authenticated"}';

do $$
begin
  if (select count(*) from public.shared_zerochill_creators(
      'b20aa450-3550-4a10-9a77-000000000002')) <> 2
     or exists (select 1 from public.shared_zerochill_creators(
      'b20aa450-3550-4a10-9a77-000000000003'))
     or exists (select 1 from public.shared_zerochill_creators(null))
     or exists (select 1 from public.shared_zerochill_creators(
      'b20aa450-3550-4a10-9a77-000000000001')) then
    raise exception 'Shared creators must contain exactly the intersection';
  end if;
end;
$$;

-- Equivalent of the existing REST favorite sync's merge-duplicates upsert.
insert into public.creator_favorites(creator_key, creator_name)
values ('shared', 'Updated shared creator')
on conflict (user_id, creator_key) do update set
  creator_key = excluded.creator_key,
  creator_name = excluded.creator_name;

update public.profiles set display_name = 'Test A', bio = repeat('x', 160)
where user_id = 'b20aa450-3550-4a10-9a77-000000000001';
do $$
declare changed integer;
begin
  if (select char_length(bio) from public.profiles
      where user_id = 'b20aa450-3550-4a10-9a77-000000000001') <> 160
     or (select display_name from public.profiles
      where user_id = 'b20aa450-3550-4a10-9a77-000000000001') <> 'Test A' then
    raise exception 'Profile edit failed';
  end if;

  update public.profiles set bio = 'other account'
  where user_id = 'b20aa450-3550-4a10-9a77-000000000002';
  get diagnostics changed = row_count;
  if changed <> 0 then raise exception 'Another account profile was changed'; end if;

  begin
    update public.profiles set bio = repeat('x', 161)
    where user_id = 'b20aa450-3550-4a10-9a77-000000000001';
    raise exception 'Long bio passed validation';
  exception when check_violation then null;
  end;
  begin
    update public.profiles set username = 'altered'
    where user_id = 'b20aa450-3550-4a10-9a77-000000000001';
    raise exception 'Protected username was mutable';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.direct_messages set body = 'tampered'
    where id = 'b20aa450-3550-4a10-9a77-000000000010';
    raise exception 'DM body was mutable';
  exception when insufficient_privilege then null;
  end;
end;
$$;
update public.profiles set bio = ''
where user_id = 'b20aa450-3550-4a10-9a77-000000000001';
update public.direct_messages set read_at = now()
where id = 'b20aa450-3550-4a10-9a77-000000000010';

select public.save_zerochill_notification_preferences(
  '{"user_id":"b20aa450-3550-4a10-9a77-000000000002","replies":false}'::jsonb
);
select public.save_zerochill_notification_preferences(
  '{"replies":true,"likes":false,"direct_messages":false,
    "creator_updates":true,"app_updates":true}'::jsonb
);

do $$
begin
  if (select count(*) from public.notification_preferences) <> 1
     or (select replies from public.notification_preferences limit 1) <> true
     or (select likes from public.notification_preferences limit 1) <> false
     or (select direct_messages from public.notification_preferences limit 1) <> false then
    raise exception 'Owner preference upsert or read scope is wrong';
  end if;
end;
$$;

set local request.jwt.claim.sub = 'b20aa450-3550-4a10-9a77-000000000002';
set local request.jwt.claims = '{"sub":"b20aa450-3550-4a10-9a77-000000000002","role":"authenticated"}';
do $$
begin
  if exists (select 1 from public.notification_preferences) then
    raise exception 'Other account preferences are visible';
  end if;
end;
$$;
select public.save_zerochill_notification_preferences('{}'::jsonb);
do $$
begin
  if (select count(*) from public.notification_preferences) <> 1
     or (select bool_and(replies and likes and direct_messages and
         creator_updates and app_updates) from public.notification_preferences) <> true then
    raise exception 'New accounts need enabled notification defaults';
  end if;
end;
$$;
set local request.jwt.claim.sub = 'b20aa450-3550-4a10-9a77-000000000001';
set local request.jwt.claims = '{"sub":"b20aa450-3550-4a10-9a77-000000000001","role":"authenticated"}';

reset role;
insert into public.user_blocks(blocker_id, blocked_id) values
  ('b20aa450-3550-4a10-9a77-000000000002',
   'b20aa450-3550-4a10-9a77-000000000001');
set local role authenticated;

do $$
begin
  if exists (select 1 from public.shared_zerochill_creators(
      'b20aa450-3550-4a10-9a77-000000000002')) then
    raise exception 'Blocks must suppress shared creators in either direction';
  end if;
end;
$$;

reset role;
delete from public.user_blocks where
  blocker_id = 'b20aa450-3550-4a10-9a77-000000000002';
insert into public.user_blocks(blocker_id, blocked_id) values
  ('b20aa450-3550-4a10-9a77-000000000001',
   'b20aa450-3550-4a10-9a77-000000000002');
set local role authenticated;
do $$
begin
  if exists (select 1 from public.shared_zerochill_creators(
      'b20aa450-3550-4a10-9a77-000000000002')) then
    raise exception 'Own block must suppress shared creators';
  end if;
end;
$$;

reset role;
do $$
begin
  if has_function_privilege('anon', 'public.shared_zerochill_creators(uuid)', 'EXECUTE')
     or has_function_privilege('anon', 'public.current_zerochill_session()', 'EXECUTE')
     or has_function_privilege('anon',
          'public.save_zerochill_notification_preferences(jsonb)', 'EXECUTE') then
    raise exception 'Anon can call protected account functions';
  end if;
end;
$$;

-- Auth hard deletion cascades through the profile, including other users'
-- replies, DMs, and reports. These outcomes are documented for confirmation UI.
delete from auth.users where id = 'b20aa450-3550-4a10-9a77-000000000002';
do $$
begin
  if exists (select 1 from public.profiles
      where user_id = 'b20aa450-3550-4a10-9a77-000000000002')
     or exists (select 1 from public.comments where id in (
       'b20aa450-3550-4a10-9a77-000000000011',
       'b20aa450-3550-4a10-9a77-000000000012'))
     or exists (select 1 from public.direct_messages
       where id = 'b20aa450-3550-4a10-9a77-000000000010')
     or exists (select 1 from public.social_reports
       where reported_user_id = 'b20aa450-3550-4a10-9a77-000000000002')
     or not exists (select 1 from public.profiles
       where user_id = 'b20aa450-3550-4a10-9a77-000000000001') then
    raise exception 'Auth deletion cascade differs from documented behavior';
  end if;
end;
$$;

rollback;
