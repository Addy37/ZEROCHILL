#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 -f supabase/tests/social_test_bootstrap.sql
for name in \
  202609290003_account_social_foundation \
  202609300001_dm_inbox_controls \
  202609300006_comment_owner_controls \
  20260930212316_profile_account_2 \
  20260930234006_profile_account_rpc_boundaries \
  20261003171153_conversation_cleanup \
  202610290001_social_abuse_hardening \
  202610290002_social_write_rate_limits \
  202610290003_social_bans \
  20261101194912_social_media_reactions \
  20261101194929_analytics_v2; do
  psql -v ON_ERROR_STOP=1 -f "supabase/migrations/$name.sql"
done
psql -v ON_ERROR_STOP=1 -f supabase/tests/social_media_reactions.sql
psql -v ON_ERROR_STOP=1 -f supabase/tests/analytics_v2.sql
