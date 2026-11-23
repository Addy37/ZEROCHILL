-- New Zerochill: synthetic rehearsal ONLY. No OG user data or credentials.
-- Execute as one transaction against an authorized test environment.
-- NEVER remove BEGIN/ROLLBACK or substitute real identities.
BEGIN;
DO $dry$
DECLARE
 t1 uuid := '00000000-0000-4000-8000-00000000d201';
 t2 uuid := '00000000-0000-4000-8000-00000000d202';
 a uuid := '00000000-0000-4000-8000-00000000d203';
 b uuid := '00000000-0000-4000-8000-00000000d204';
 c1 uuid := '00000000-0000-4000-8000-00000000d210';
 c2 uuid := '00000000-0000-4000-8000-00000000d211';
BEGIN
 IF EXISTS (SELECT 1 FROM auth.users WHERE id IN (t1,t2,a,b))
    OR EXISTS (SELECT 1 FROM auth.users WHERE email LIKE 'zc-dry-run-%@example.invalid')
    OR EXISTS (SELECT 1 FROM public.profiles WHERE username_key IN ('zc_dry_handle','zc_dry_tester','zc_dry_friend'))
 THEN RAISE EXCEPTION 'Synthetic sentinel collision: refuse rehearsal'; END IF;
 INSERT INTO auth.users (id,instance_id,aud,role,email,encrypted_password,email_confirmed_at,raw_user_meta_data,created_at,updated_at)
 VALUES
   (t1, '00000000-0000-0000-0000-000000000000','authenticated','authenticated',
    'zc-dry-run-dup@example.invalid','',now(),
    '{"username":"zc_dry_tester","adult_confirmed":true,"terms_accepted":true}'::jsonb,now(),now()),
   (t2, '00000000-0000-0000-0000-000000000000','authenticated','authenticated',
    'zc-dry-run-unrelated@example.invalid','',now(),
    '{"username":"zc_dry_handle","adult_confirmed":true,"terms_accepted":true}'::jsonb,now(),now());
 IF (SELECT count(*) FROM public.profiles WHERE user_id IN (t1,t2)) <> 2
 THEN RAISE EXCEPTION 'Auth trigger did not auto-create both profiles'; END IF;
 INSERT INTO public.creator_favorites (user_id,creator_key,creator_name)
 VALUES (t1,'zc-dry-favorite','Temporary favorite');
 DELETE FROM auth.users WHERE id IN (t1,t2);
 IF EXISTS (SELECT 1 FROM public.profiles WHERE user_id IN (t1,t2))
   OR EXISTS (SELECT 1 FROM public.creator_favorites WHERE user_id=t1)
 THEN RAISE EXCEPTION 'Test identity deletion did not cascade'; END IF;
 INSERT INTO auth.users (id,instance_id,aud,role,email,encrypted_password,email_confirmed_at,raw_user_meta_data,created_at,updated_at)
 VALUES
   (a, '00000000-0000-0000-0000-000000000000','authenticated','authenticated',
    'zc-dry-run-dup@example.invalid','',now(),
    '{"username":"zc_dry_handle","adult_confirmed":true,"terms_accepted":true}'::jsonb,now(),now()),
   (b, '00000000-0000-0000-0000-000000000000','authenticated','authenticated',
    'zc-dry-run-counterpart@example.invalid','',now(),
    '{"username":"zc_dry_friend","adult_confirmed":true,"terms_accepted":true}'::jsonb,now(),now());
 IF (SELECT count(*) FROM public.profiles WHERE user_id IN (a,b)) <> 2
 THEN RAISE EXCEPTION 'Missing imported identity profiles'; END IF;
 IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE user_id=a AND username_key='zc_dry_handle')
 THEN RAISE EXCEPTION 'Original user did not reclaim original username'; END IF;
 INSERT INTO public.creator_favorites (user_id,creator_key,creator_name)
 VALUES (a,'zc-dry-favorite','Original favorite');
 INSERT INTO public.comments (id,user_id,content_key,canonical_url,body)
 VALUES (c1,a,'zc-content-test-0001','https://example.invalid/video','Fake comment');
 INSERT INTO public.comments (id,user_id,content_key,canonical_url,parent_id,body)
 VALUES (c2,b,'zc-content-test-0001','https://example.invalid/video',c1,'Fake reply');
 IF (SELECT count(*) FROM public.comments WHERE id IN (c1,c2))<>2
    OR NOT EXISTS (SELECT 1 FROM public.comments WHERE id=c2 AND parent_id=c1)
 THEN RAISE EXCEPTION 'Comment ownership or reply relationship missing'; END IF;
END $dry$;
ROLLBACK;
SELECT
 (SELECT count(*) FROM auth.users WHERE id IN ('00000000-0000-4000-8000-00000000d201'::uuid,
  '00000000-0000-4000-8000-00000000d202'::uuid,'00000000-0000-4000-8000-00000000d203'::uuid,
  '00000000-0000-4000-8000-00000000d204'::uuid)) as leftover_auth_users,
 (SELECT count(*) FROM public.comments WHERE id IN ('00000000-0000-4000-8000-00000000d210'::uuid,
  '00000000-0000-4000-8000-00000000d211'::uuid)) as leftover_comments,
 (SELECT count(*) FROM public.profiles WHERE username_key IN ('zc_dry_handle','zc_dry_tester','zc_dry_friend')) as leftover_profiles;
