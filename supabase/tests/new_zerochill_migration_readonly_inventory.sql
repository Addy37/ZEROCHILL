-- ZEROCHILL New Supabase cutover, READ-ONLY inventory.
-- Run separately against OLD and NEW projects. No credentials or personal rows
-- are selected. Results are a point-in-time snapshot, not migration parity.
-- Never run an automatic full-project restore against the existing NEW project.

SELECT
    (SELECT count(*) FROM auth.users) AS auth_users,
    (SELECT count(*) FROM auth.identities) AS auth_identities,
    (SELECT count(*) FROM auth.users WHERE encrypted_password IS NOT NULL
        AND encrypted_password <> '') AS password_hashes_present,
    (SELECT count(*) FROM auth.users WHERE email_confirmed_at IS NOT NULL) AS email_confirmed,
    (SELECT count(*) FROM auth.users WHERE email_confirmed_at IS NULL) AS email_unconfirmed,
    (SELECT count(*) FROM auth.users WHERE last_sign_in_at IS NOT NULL) AS ever_signed_in,
    (SELECT count(*) FROM auth.users WHERE raw_user_meta_data->>'username' IS NOT NULL
        AND raw_user_meta_data->>'username' <> '') AS auth_username_metadata,
    (SELECT count(*) FROM auth.users WHERE raw_user_meta_data->>'adult_confirmed' = 'true')
        AS auth_adult_confirmed_metadata,
    (SELECT count(*) FROM auth.users WHERE raw_user_meta_data->>'terms_accepted' = 'true')
        AS auth_terms_accepted_metadata,
    (SELECT count(*) FROM auth.users u LEFT JOIN public.profiles p ON p.user_id=u.id
        WHERE p.user_id IS NULL) AS auth_users_without_profile,
    (SELECT count(*) FROM public.profiles p LEFT JOIN auth.users u ON u.id=p.user_id
        WHERE u.id IS NULL) AS profiles_without_auth_user,
    (SELECT count(*) FROM auth.identities i LEFT JOIN auth.users u ON u.id=i.user_id
        WHERE u.id IS NULL) AS identities_without_auth_user,
    (SELECT count(*) FROM public.direct_messages m
        LEFT JOIN auth.users u ON u.id=m.sender_id WHERE u.id IS NULL) AS dm_missing_sender,
    (SELECT count(*) FROM public.direct_messages m
        LEFT JOIN auth.users u ON u.id=m.recipient_id WHERE u.id IS NULL) AS dm_missing_recipient,
    (SELECT count(*) FROM public.comments c
        LEFT JOIN auth.users u ON u.id=c.user_id WHERE u.id IS NULL) AS comments_missing_user,
    (SELECT count(*) FROM (SELECT lower(trim(email)) FROM auth.users
        WHERE email IS NOT NULL GROUP BY 1 HAVING count(*)>1) x) AS duplicate_emails,
    (SELECT count(*) FROM (SELECT lower(trim(username)) FROM public.profiles
        WHERE username IS NOT NULL GROUP BY 1 HAVING count(*)>1) x) AS duplicate_usernames,
    (SELECT count(*) FROM public.profiles) AS profiles,
    (SELECT count(*) FROM public.creator_favorites) AS creator_favorites,
    (SELECT count(*) FROM public.comments) AS comments,
    (SELECT count(*) FROM public.comments WHERE parent_id IS NOT NULL) AS comment_replies,
    (SELECT count(*) FROM public.comment_likes) AS comment_likes,
    (SELECT count(*) FROM public.video_likes) AS video_likes,
    (SELECT count(*) FROM public.direct_messages) AS direct_messages,
    (SELECT count(*) FROM public.dm_reactions) AS dm_reactions,
    (SELECT count(*) FROM public.notification_preferences) AS notification_preferences,
    (SELECT count(*) FROM public.conversation_clear_state) AS conversation_clear_state,
    (SELECT count(*) FROM public.push_device_tokens) AS push_device_tokens,
    (SELECT count(*) FROM storage.objects) AS storage_objects,
    (SELECT count(*) FROM public.source_config_versions) AS source_config_versions;
