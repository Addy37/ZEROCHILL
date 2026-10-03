-- Rollback-only fixture against a migrated disposable DB or privileged test role.
begin;
insert into auth.users(id, instance_id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_user_meta_data, created_at, updated_at) values
('c1ea0000-0000-4000-8000-000000000001','00000000-0000-0000-0000-000000000000',
 'authenticated','authenticated','cleanup-a@invalid.example','',now(),
 '{"username":"cleanupfixturea","adult_confirmed":true,"terms_accepted":true}',now(),now()),
('c1ea0000-0000-4000-8000-000000000002','00000000-0000-0000-0000-000000000000',
 'authenticated','authenticated','cleanup-b@invalid.example','',now(),
 '{"username":"cleanupfixtureb","adult_confirmed":true,"terms_accepted":true}',now(),now()),
('c1ea0000-0000-4000-8000-000000000003','00000000-0000-0000-0000-000000000000',
 'authenticated','authenticated','cleanup-c@invalid.example','',now(),
 '{"username":"cleanupfixturec","adult_confirmed":true,"terms_accepted":true}',now(),now());
insert into auth.sessions(id,user_id) values
('c1ea0000-0000-4000-8000-000000000010','c1ea0000-0000-4000-8000-000000000001'),
('c1ea0000-0000-4000-8000-000000000020','c1ea0000-0000-4000-8000-000000000002');
insert into public.direct_messages(id,sender_id,recipient_id,body,created_at) values
('c1ea0000-0000-4000-8000-000000000011','c1ea0000-0000-4000-8000-000000000002',
 'c1ea0000-0000-4000-8000-000000000001','Old unread incoming',now()-interval '1 day'),
('c1ea0000-0000-4000-8000-000000000012','c1ea0000-0000-4000-8000-000000000001',
 'c1ea0000-0000-4000-8000-000000000002','Old outgoing',now()-interval '1 hour');
set local role authenticated;
set local request.jwt.claim.sub = 'c1ea0000-0000-4000-8000-000000000001';
set local request.jwt.claims = '{"sub":"c1ea0000-0000-4000-8000-000000000001","role":"authenticated","session_id":"c1ea0000-0000-4000-8000-000000000010"}';
select public.clear_zerochill_conversation('c1ea0000-0000-4000-8000-000000000002');
do $$ begin
 if exists(select 1 from public.visible_zerochill_direct_messages())
 or (select count(*) from public.conversation_clear_state) <> 1
 or (select count(*) from public.direct_messages) <> 2 then
   raise exception 'Clear must hide only own view and preserve shared rows';
 end if;
 begin
   insert into public.conversation_clear_state(user_id,partner_id,cleared_at) values
   (auth.uid(),'c1ea0000-0000-4000-8000-000000000003',now()+interval '1 year');
   raise exception 'Direct cleanup writes must be denied';
 exception when insufficient_privilege then null; end;
 begin
   perform public.clear_zerochill_conversation('c1ea0000-0000-4000-8000-000000000003');
   raise exception 'Non-participant cleanup must be denied';
 exception when invalid_parameter_value then null; end;
 begin
   delete from public.direct_messages;
   raise exception 'Shared message deletion must be denied';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
-- Exact cutoff is excluded. Later messages in either direction return.
insert into public.direct_messages(id,sender_id,recipient_id,body,created_at)
select 'c1ea0000-0000-4000-8000-000000000013',partner_id,user_id,'At cutoff',cleared_at
from public.conversation_clear_state;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.visible_zerochill_direct_messages()) then
   raise exception 'Cutoff comparison must be strict'; end if;
end $$;
set local request.jwt.claim.sub = 'c1ea0000-0000-4000-8000-000000000002';
set local request.jwt.claims = '{"sub":"c1ea0000-0000-4000-8000-000000000002","role":"authenticated","session_id":"c1ea0000-0000-4000-8000-000000000020"}';
do $$ begin
 if exists(select 1 from public.conversation_clear_state)
 or (select count(*) from public.visible_zerochill_direct_messages()) <> 3 then
   raise exception 'Another participant must retain history and cannot read cleanup state';
 end if;
end $$;
insert into public.direct_messages(id,recipient_id,body,created_at) values
('c1ea0000-0000-4000-8000-000000000014','c1ea0000-0000-4000-8000-000000000001',
 'New incoming',clock_timestamp());
set local request.jwt.claim.sub = 'c1ea0000-0000-4000-8000-000000000001';
set local request.jwt.claims = '{"sub":"c1ea0000-0000-4000-8000-000000000001","role":"authenticated","session_id":"c1ea0000-0000-4000-8000-000000000010"}';
insert into public.direct_messages(id,recipient_id,body,created_at) values
('c1ea0000-0000-4000-8000-000000000015','c1ea0000-0000-4000-8000-000000000002',
 'New outgoing',clock_timestamp());
do $$ begin
 if (select count(*) from public.visible_zerochill_direct_messages()) <> 2
 or (select count(*) from public.visible_zerochill_direct_messages()
       where recipient_id=auth.uid() and read_at is null) <> 1 then
   raise exception 'New messages must restore thread with only new unread data'; end if;
end $$;
select public.clear_zerochill_conversation('c1ea0000-0000-4000-8000-000000000002');
do $$ begin
 if exists(select 1 from public.visible_zerochill_direct_messages()) then
   raise exception 'A second clear must advance cutoff'; end if;
end $$;
set local request.jwt.claims = '{"sub":"c1ea0000-0000-4000-8000-000000000001","role":"authenticated"}';
do $$ begin
 begin
   perform public.clear_zerochill_conversation('c1ea0000-0000-4000-8000-000000000002');
   raise exception 'Missing live session must be denied';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
do $$ begin
 if has_table_privilege('anon','public.conversation_clear_state','SELECT')
 or has_table_privilege('authenticated','public.conversation_clear_state','UPDATE')
 or has_function_privilege('anon','public.clear_zerochill_conversation(uuid)','EXECUTE')
 or has_function_privilege('anon','public.visible_zerochill_direct_messages()','EXECUTE')
 or has_column_privilege('authenticated','public.direct_messages','body','UPDATE')
 or not has_column_privilege('authenticated','public.direct_messages','read_at','UPDATE') then
   raise exception 'Cleanup grants weakened social permissions'; end if;
end $$;
rollback;
