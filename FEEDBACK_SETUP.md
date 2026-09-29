# ZeroChill feedback setup

ZEROCHILL uses anonymous per-installation feedback identity. Users do not need accounts.
Each app installation gets a random UUID. The feedback Edge Function hashes that UUID before
database access, so the raw installation ID is not stored in Supabase.

## Live endpoints

- Public app feedback: `dynamic-api`
- Private admin feedback: `feedback-admin`

The repository keeps the public handler under both
`supabase/functions/dynamic-api/index.ts` (the live deployed slug) and
`supabase/functions/feedback/index.ts` for compatibility with the original setup documentation.

## Database

Run:

- `supabase/migrations/202609120001_create_feedback.sql` for the base feedback table on a new project.
- `supabase/migrations/202609290001_feedback_conversations.sql` for threaded conversations and read receipts.

`app_feedback` remains the ticket metadata and legacy compatibility row.
`feedback_messages` stores every user/developer message with `created_at` and nullable
`read_at`.

The conversation migration backfills each existing ticket's original message and any existing
`developer_reply` into `feedback_messages`. It does not delete or rename legacy columns.

## Secrets and client configuration

The public function requires:

- `FEEDBACK_ID_SALT`
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`

The private admin function also requires:

- `FEEDBACK_ADMIN_TOKEN_HASH`

Release builds receive:

- `FEEDBACK_ENDPOINT`
- `FEEDBACK_ANON_KEY`

The Supabase URL and publishable/anon key are client configuration. Keep the service-role key and
admin token on the backend only.

## Conversation behavior

The public app can:

- submit a new feedback ticket
- list tickets owned by its hashed installation ID
- open one owned thread
- reply to that thread
- mark developer messages read when the thread is opened

The admin app can:

- list all feedback threads
- see unread user reply counts
- open a thread
- reply as ZEROCHILL
- update ticket status
- mark user messages read when the thread is opened
- see when a developer message was read by the user

Read receipts are therefore symmetric:

- the user sees **Seen by ZEROCHILL** on user messages after the admin opens the thread
- the admin sees **Seen by user** on developer messages after the user opens the thread

Existing installed versions remain compatible because `message` and `developer_reply` continue
to be populated on `app_feedback`.

Allowed statuses are `submitted`, `reviewing`, `planned`, `in_progress`, `completed`, and
`declined`.
