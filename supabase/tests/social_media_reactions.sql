begin;
insert into auth.users(id,raw_user_meta_data) values
('b20aa450-3550-4a10-9a77-000000000001','{"username":"mediatesta","adult_confirmed":true,"terms_accepted":true}'),
('b20aa450-3550-4a10-9a77-000000000002','{"username":"mediatestb","adult_confirmed":true,"terms_accepted":true}'),
('b20aa450-3550-4a10-9a77-000000000003','{"username":"mediatestc","adult_confirmed":true,"terms_accepted":true}');
insert into auth.sessions(id,user_id) values('a20aa450-3550-4a10-9a77-000000000001','b20aa450-3550-4a10-9a77-000000000001');
set local role authenticated;
select set_config('request.jwt.claim.sub','b20aa450-3550-4a10-9a77-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"a20aa450-3550-4a10-9a77-000000000001"}',true);
select public.reserve_zerochill_media_upload('c20aa450-3550-4a10-9a77-000000000001','social-gifs');
do $$ begin
 begin insert into storage.objects(bucket_id,name) values('social-gifs','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000001'); raise exception 'Unvalidated direct upload accepted';
 exception when insufficient_privilege then null; end;
end $$;
reset role; -- Emulate Storage service only after the Edge validated file bytes.
insert into storage.objects(bucket_id,name) values('social-gifs' ,'b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000001');
set local role authenticated;
insert into public.direct_messages(id,recipient_id,body,attachment_bucket,attachment_path) values(
'c20aa450-3550-4a10-9a77-000000000001','b20aa450-3550-4a10-9a77-000000000002','GIF','social-gifs','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000001');
select public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','🔥');
do $$ begin
 if (select count(*) from public.dm_reactions)<>1 then raise exception 'Add reaction failed'; end if;
 if (select count(*) from storage.objects where bucket_id='social-gifs')<>1 then raise exception 'Sender media read failed'; end if;
end $$;
select public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','👍');
do $$ begin if (select emoji from public.dm_reactions)<>'👍' then raise exception 'Replace reaction failed'; end if; end $$;
select public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','👍');
do $$ begin if exists(select 1 from public.dm_reactions) then raise exception 'Remove reaction failed'; end if; end $$;
select public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','🔥');
-- A known path cannot be attached to someone else's row or read by an unrelated user.
select set_config('request.jwt.claim.sub','b20aa450-3550-4a10-9a77-000000000003',true);
do $$ begin
 if exists(select 1 from public.direct_messages) or exists(select 1 from storage.objects where bucket_id='social-gifs')
 or exists(select 1 from public.dm_reactions) then raise exception 'Private DM data leaked'; end if;
 begin
  perform public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','🔥');
  raise exception 'Unrelated reaction accepted';
 exception when insufficient_privilege then null; end;
 begin
  insert into storage.objects(bucket_id,name) values('social-photos','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000003');
  raise exception 'Cross-owner upload accepted';
 exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','b20aa450-3550-4a10-9a77-000000000002',true);
do $$ begin if (select count(*) from storage.objects where bucket_id='social-gifs')<>1 then raise exception 'Recipient media read failed'; end if; end $$;
reset role;
insert into auth.sessions(id,user_id) values('a20aa450-3550-4a10-9a77-000000000002','b20aa450-3550-4a10-9a77-000000000002');
set local role authenticated;
select set_config('request.jwt.claims','{"session_id":"a20aa450-3550-4a10-9a77-000000000002"}',true);
select public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','🔥');
do $$ begin
 if (select count from public.zerochill_dm_reactions('b20aa450-3550-4a10-9a77-000000000001'))<>2 then raise exception 'Grouped counts failed'; end if;
end $$;
insert into public.user_blocks(blocked_id) values('b20aa450-3550-4a10-9a77-000000000001');
do $$ begin
 begin perform public.toggle_zerochill_dm_reaction('c20aa450-3550-4a10-9a77-000000000001','🔥'); raise exception 'Blocked reaction accepted';
 exception when insufficient_privilege then null; end;
end $$;
-- Comments remain public, but tombstoning removes public attachment access.
select set_config('request.jwt.claim.sub','b20aa450-3550-4a10-9a77-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"a20aa450-3550-4a10-9a77-000000000001"}',true);
select public.reserve_zerochill_media_upload('c20aa450-3550-4a10-9a77-000000000002','social-photos');
reset role;
insert into storage.objects(bucket_id,name) values('social-photos','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000002');
set local role authenticated;
insert into public.comments(id,content_key,canonical_url,body,attachment_bucket,attachment_path) values
('c20aa450-3550-4a10-9a77-000000000002',repeat('a',64),'https://invalid.example','Photo','social-photos','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000002');
insert into public.comments(content_key,canonical_url,body,parent_id) values(repeat('a',64),'https://invalid.example','legacy text','c20aa450-3550-4a10-9a77-000000000002');
insert into public.direct_messages(recipient_id,body) values('b20aa450-3550-4a10-9a77-000000000003','legacy DM');
-- Text + photo DM and attachment-only GIF reply exercise both bucket/row combinations.
select public.reserve_zerochill_media_upload('c20aa450-3550-4a10-9a77-000000000003','social-photos');
select public.reserve_zerochill_media_upload('c20aa450-3550-4a10-9a77-000000000004','social-gifs');
reset role;
insert into storage.objects(bucket_id,name) values
('social-photos','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000003'),
('social-gifs','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000004');
set local role authenticated;
insert into public.direct_messages(id,recipient_id,body,attachment_bucket,attachment_path) values
('c20aa450-3550-4a10-9a77-000000000003','b20aa450-3550-4a10-9a77-000000000003','caption','social-photos','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000003');
insert into public.comments(id,content_key,canonical_url,parent_id,body,attachment_bucket,attachment_path) values
('c20aa450-3550-4a10-9a77-000000000004',repeat('a',64),'https://invalid.example','c20aa450-3550-4a10-9a77-000000000002','GIF','social-gifs','b20aa450-3550-4a10-9a77-000000000001/c20aa450-3550-4a10-9a77-000000000004');
select set_config('request.jwt.claim.sub','',true);
set local role anon;
do $$ begin if (select count(*) from storage.objects where bucket_id='social-photos')<>1 then raise exception 'Public comment media denied'; end if; end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub','b20aa450-3550-4a10-9a77-000000000001',true);
update public.comments set body='Comment deleted',deleted_at=now() where id='c20aa450-3550-4a10-9a77-000000000002';
select set_config('request.jwt.claim.sub','',true);
set local role anon;
do $$ begin if exists(select 1 from storage.objects where bucket_id='social-photos') then raise exception 'Deleted comment media public'; end if; end $$;
reset role;
do $$ begin
 if (select public from storage.buckets where id='social-gifs') or (select file_size_limit from storage.buckets where id='social-gifs')<>10485760
 or (select file_size_limit from storage.buckets where id='social-photos')<>5242880 then raise exception 'Bucket security limits failed'; end if;
 if (select allowed_mime_types from storage.buckets where id='social-photos') && array['image/svg+xml','text/html','application/octet-stream'] then raise exception 'Unsafe MIME allowed'; end if;
 if has_column_privilege('authenticated','public.direct_messages','body','update') then raise exception 'DM body writable'; end if;
end $$;
rollback;
