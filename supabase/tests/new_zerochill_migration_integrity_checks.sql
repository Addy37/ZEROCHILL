-- ZEROCHILL migration consistency checks, READ-ONLY.
-- Run against old and new, then the private disposable import rehearsal.
-- Must use a database role authorized to see all rows; regular RLS-filtered
-- client results are NOT sufficient. No personal data or passwords returned.
-- All counts should be 0 except that auth_users_without_profiles may be 1
-- on the existing New test backend until the test-only account is retired.
-- Run this again after the dry-run and production cutover.

with checks(label, violations) as (
  select 'auth_identity_without_user', count(*) from auth.identities i
    left join auth.users u on u.id = i.user_id where u.id is null
  union all select 'profile_without_auth_user', count(*) from public.profiles p
    left join auth.users u on u.id = p.user_id where u.id is null
  union all select 'auth_user_without_profile', count(*) from auth.users u
    left join public.profiles p on p.user_id = u.id where p.user_id is null
  union all select 'creator_favorite_without_profile', count(*) from public.creator_favorites x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'video_like_without_profile', count(*) from public.video_likes x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'comment_without_profile', count(*) from public.comments x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'comment_reply_without_parent', count(*) from public.comments x
    left join public.comments parent on parent.id = x.parent_id
    where x.parent_id is not null and parent.id is null
  union all select 'comment_like_without_profile', count(*) from public.comment_likes x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'comment_like_without_comment', count(*) from public.comment_likes x
    left join public.comments c on c.id = x.comment_id where c.id is null
  union all select 'dm_without_sender_profile', count(*) from public.direct_messages x
    left join public.profiles p on p.user_id = x.sender_id where p.user_id is null
  union all select 'dm_without_recipient_profile', count(*) from public.direct_messages x
    left join public.profiles p on p.user_id = x.recipient_id where p.user_id is null
  union all select 'reaction_without_message', count(*) from public.dm_reactions x
    left join public.direct_messages m on m.id = x.message_id where m.id is null
  union all select 'reaction_without_profile', count(*) from public.dm_reactions x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'block_without_blocker_profile', count(*) from public.user_blocks x
    left join public.profiles p on p.user_id = x.blocker_id where p.user_id is null
  union all select 'block_without_blocked_profile', count(*) from public.user_blocks x
    left join public.profiles p on p.user_id = x.blocked_id where p.user_id is null
  union all select 'report_without_reporter_profile', count(*) from public.social_reports x
    left join public.profiles p on p.user_id = x.reporter_id where p.user_id is null
  union all select 'report_without_reported_profile', count(*) from public.social_reports x
    left join public.profiles p on p.user_id = x.reported_user_id where p.user_id is null
  union all select 'preferences_without_profile', count(*) from public.notification_preferences x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'clear_state_without_owner_profile', count(*) from public.conversation_clear_state x
    left join public.profiles p on p.user_id = x.user_id where p.user_id is null
  union all select 'clear_state_without_partner_profile', count(*) from public.conversation_clear_state x
    left join public.profiles p on p.user_id = x.partner_id where p.user_id is null
  union all select 'duplicate_normalized_emails', count(*) from (
    select lower(btrim(email)) as key from auth.users where email is not null
    group by 1 having count(*) > 1
  ) duplicates
  union all select 'duplicate_normalized_usernames', count(*) from (
    select lower(btrim(username)) as key from public.profiles
    group by 1 having count(*) > 1
  ) duplicates
)
select label, violations::bigint as violations
from checks
order by label;
