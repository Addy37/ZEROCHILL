-- Extend the aggregate-only admin analytics dashboard for ZEROCHILL 4.3 social adoption.
-- No per-account activity details are returned.

create or replace function public.analytics_dashboard()
returns jsonb
language sql
security definer
set search_path = ''
as $$
with periods as (
  select
    (timezone('UTC', now()))::date as day_start,
    date_trunc('week', timezone('UTC', now()))::date as week_start,
    date_trunc('month', timezone('UTC', now()))::date as month_start
),
active as (
  select
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='day' and u.period_start=p.day_start and u.metric='app_open' and u.value='all') as daily,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='week' and u.period_start=p.week_start and u.metric='app_open' and u.value='all') as weekly,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='month' and u.period_start=p.month_start and u.metric='app_open' and u.value='all') as monthly
),
section_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='section'
  order by unique_users desc, c.event_count desc, c.value
  limit 20
),
source_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='source'
  order by unique_users desc, c.event_count desc, c.value
  limit 20
),
creator_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques d, periods p2
           where d.period_type='day' and d.period_start=p2.day_start
             and d.metric='creator' and d.value=c.value) as users_today
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='creator'
  order by unique_users desc, c.event_count desc, c.value
  limit 25
),
version_ranked as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type=c.period_type and u.period_start=c.period_start
             and u.metric=c.metric and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques d, periods p2
           where d.period_type='day' and d.period_start=p2.day_start
             and d.metric='app_version' and d.value=c.value) as users_today,
         coalesce((regexp_match(c.value, '^([0-9]+)'))[1], '0')::int as major,
         coalesce((regexp_match(c.value, '^[0-9]+\.([0-9]+)'))[1], '0')::int as minor,
         coalesce((regexp_match(c.value, '^[0-9]+\.[0-9]+\.([0-9]+)'))[1], '0')::int as patch,
         case when c.value ~ '-' then 0 else 1 end as stable_rank
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='app_version'
),
latest_stable_version as (
  select value
  from version_ranked
  where stable_rank = 1
  order by major desc, minor desc, patch desc, value desc
  limit 1
),
version_rows as (
  select value, event_count, unique_users, users_today,
         major, minor, patch, stable_rank,
         case when value = (select value from latest_stable_version) then 0 else 1 end as latest_rank
  from version_ranked
  order by latest_rank, unique_users desc, event_count desc,
           major desc, minor desc, patch desc, stable_rank desc, value desc
  limit 20
),
device_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type='week' and u.period_start=c.period_start
             and u.metric='device_model' and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques u, periods p2
           where u.period_type='day' and u.period_start=p2.day_start
             and u.metric='device_model' and u.value=c.value) as users_today
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='device_model'
  order by unique_users desc, c.event_count desc, c.value
  limit 25
),
manufacturer_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type='week' and u.period_start=c.period_start
             and u.metric='device_manufacturer' and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques u, periods p2
           where u.period_type='day' and u.period_start=p2.day_start
             and u.metric='device_manufacturer' and u.value=c.value) as users_today
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='device_manufacturer'
  order by unique_users desc, c.event_count desc, c.value
  limit 25
),
android_rows as (
  select c.value, c.event_count,
         (select count(*) from public.analytics_uniques u
           where u.period_type='week' and u.period_start=c.period_start
             and u.metric='android_version' and u.value=c.value) as unique_users,
         (select count(*) from public.analytics_uniques u, periods p2
           where u.period_type='day' and u.period_start=p2.day_start
             and u.metric='android_version' and u.value=c.value) as users_today
  from public.analytics_counters c, periods p
  where c.period_type='week' and c.period_start=p.week_start and c.metric='android_version'
  order by unique_users desc, c.event_count desc, c.value
  limit 25
),
social_events as (
  select user_id, created_at from public.comments where deleted_at is null
  union all
  select user_id, created_at from public.comment_likes
  union all
  select user_id, created_at from public.video_likes
  union all
  select sender_id as user_id, created_at from public.direct_messages
),
social_users as (
  select
    count(distinct user_id) as total,
    count(distinct user_id) filter (
      where created_at >= ((select day_start from periods)::timestamp at time zone 'UTC')
    ) as today,
    count(distinct user_id) filter (
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as week
  from social_events
),
account_summary as (
  select
    count(*) as total,
    count(*) filter (
      where created_at >= ((select day_start from periods)::timestamp at time zone 'UTC')
    ) as new_today,
    count(*) filter (
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as new_week,
    count(*) filter (where nullif(btrim(avatar_path), '') is not null) as with_avatar,
    count(*) filter (where nullif(btrim(bio), '') is not null) as with_bio
  from public.profiles
),
comment_summary as (
  select
    count(*) filter (where deleted_at is null) as total,
    count(*) filter (
      where deleted_at is null
        and created_at >= ((select day_start from periods)::timestamp at time zone 'UTC')
    ) as today,
    count(*) filter (
      where deleted_at is null
        and created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as week,
    count(*) filter (where deleted_at is null and parent_id is not null) as replies_total,
    count(*) filter (
      where deleted_at is null and parent_id is not null
        and created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as replies_week
  from public.comments
),
like_summary as (
  select
    (select count(*) from public.video_likes) as video_total,
    (select count(*) from public.video_likes
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')) as video_week,
    (select count(*) from public.comment_likes) as comment_total,
    (select count(*) from public.comment_likes
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')) as comment_week
),
message_summary as (
  select
    count(*) as total,
    count(*) filter (
      where created_at >= ((select day_start from periods)::timestamp at time zone 'UTC')
    ) as today,
    count(*) filter (
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as week,
    count(distinct sender_id) filter (
      where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC')
    ) as senders_week,
    count(distinct least(sender_id, recipient_id)::text || ':' || greatest(sender_id, recipient_id)::text)
      filter (where created_at >= ((select week_start from periods)::timestamp at time zone 'UTC'))
      as conversations_week,
    count(*) filter (where read_at is not null) as read,
    count(*) filter (where read_at is null) as unread,
    case when count(*) = 0 then 0
      else round((count(*) filter (where read_at is not null))::numeric * 100.0 / count(*), 1)
    end as read_rate_percent
  from public.direct_messages
),
favorite_summary as (
  select
    count(*) as total,
    count(distinct user_id) as accounts,
    case when count(distinct user_id) = 0 then 0
      else round(count(*)::numeric / count(distinct user_id), 1)
    end as average_per_account
  from public.creator_favorites
),
safety_summary as (
  select count(*) as blocks from public.user_blocks
),
preference_summary as (
  select
    count(*) as customized_users,
    count(*) filter (where not replies) as replies_disabled,
    count(*) filter (where not likes) as likes_disabled,
    count(*) filter (where not direct_messages) as direct_messages_disabled
  from public.notification_preferences
),
release_adoption as (
  select
    coalesce((select value from latest_stable_version), '') as version,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='day' and u.period_start=p.day_start
        and u.metric='app_version' and u.value=(select value from latest_stable_version)) as users_today,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='week' and u.period_start=p.week_start
        and u.metric='app_version' and u.value=(select value from latest_stable_version)) as users_week,
    (select count(*) from public.analytics_uniques u, periods p
      where u.period_type='month' and u.period_start=p.month_start
        and u.metric='app_version' and u.value=(select value from latest_stable_version)) as users_month
),
release_with_percent as (
  select
    r.*,
    case when a.daily = 0 then 0 else round(r.users_today::numeric * 100.0 / a.daily, 1) end as percent_today,
    case when a.weekly = 0 then 0 else round(r.users_week::numeric * 100.0 / a.weekly, 1) end as percent_week,
    case when a.monthly = 0 then 0 else round(r.users_month::numeric * 100.0 / a.monthly, 1) end as percent_month
  from release_adoption r cross join active a
)
select jsonb_build_object(
  'generated_at', timezone('UTC', now()),
  'active_users', jsonb_build_object(
    'daily', (select daily from active),
    'weekly', (select weekly from active),
    'monthly', (select monthly from active)
  ),
  'release_adoption', (
    select jsonb_build_object(
      'version', version,
      'users_today', users_today,
      'users_week', users_week,
      'users_month', users_month,
      'percent_today', percent_today,
      'percent_week', percent_week,
      'percent_month', percent_month
    )
    from release_with_percent
  ),
  'social', jsonb_build_object(
    'accounts', (
      select jsonb_build_object(
        'total', total,
        'new_today', new_today,
        'new_week', new_week
      ) from account_summary
    ),
    'profile_adoption', (
      select jsonb_build_object(
        'with_avatar', with_avatar,
        'with_bio', with_bio
      ) from account_summary
    ),
    'social_users', (
      select jsonb_build_object('total', total, 'today', today, 'week', week)
      from social_users
    ),
    'comments', (
      select jsonb_build_object(
        'total', total,
        'today', today,
        'week', week,
        'replies_total', replies_total,
        'replies_week', replies_week
      ) from comment_summary
    ),
    'likes', (
      select jsonb_build_object(
        'video_total', video_total,
        'video_week', video_week,
        'comment_total', comment_total,
        'comment_week', comment_week
      ) from like_summary
    ),
    'messages', (
      select jsonb_build_object(
        'total', total,
        'today', today,
        'week', week,
        'senders_week', senders_week,
        'conversations_week', conversations_week,
        'read', read,
        'unread', unread,
        'read_rate_percent', read_rate_percent
      ) from message_summary
    ),
    'creator_favorites', (
      select jsonb_build_object(
        'total', total,
        'accounts', accounts,
        'average_per_account', average_per_account
      ) from favorite_summary
    ),
    'safety', (
      select jsonb_build_object('blocks', blocks) from safety_summary
    ),
    'notification_preferences', (
      select jsonb_build_object(
        'customized_users', customized_users,
        'replies_disabled', replies_disabled,
        'likes_disabled', likes_disabled,
        'direct_messages_disabled', direct_messages_disabled
      ) from preference_summary
    )
  ),
  'sections', coalesce((select jsonb_agg(to_jsonb(section_rows)) from section_rows), '[]'::jsonb),
  'sources', coalesce((select jsonb_agg(to_jsonb(source_rows)) from source_rows), '[]'::jsonb),
  'creators', coalesce((select jsonb_agg(to_jsonb(creator_rows)) from creator_rows), '[]'::jsonb),
  'versions', coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'value', value,
        'event_count', event_count,
        'unique_users', unique_users,
        'users_today', users_today
      )
      order by latest_rank, unique_users desc, event_count desc,
               major desc, minor desc, patch desc, stable_rank desc, value desc
    )
    from version_rows
  ), '[]'::jsonb),
  'device_models', coalesce((select jsonb_agg(to_jsonb(device_rows)) from device_rows), '[]'::jsonb),
  'device_manufacturers', coalesce((select jsonb_agg(to_jsonb(manufacturer_rows)) from manufacturer_rows), '[]'::jsonb),
  'android_versions', coalesce((select jsonb_agg(to_jsonb(android_rows)) from android_rows), '[]'::jsonb)
);
$$;

revoke all on function public.analytics_dashboard() from public, anon, authenticated;
grant execute on function public.analytics_dashboard() to service_role;
