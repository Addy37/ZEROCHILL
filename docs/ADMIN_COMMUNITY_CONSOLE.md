# ZeroChill Admin Community Console

The private ZeroChill Admin app includes a read-only Community console for account and social visibility.

## Scope

The first pass is intentionally read-only. It does not add account suspension, bans, content deletion, report resolution, or other moderation mutations.

### Accounts

- Lists ZeroChill accounts with profile identity, email, status, and signup date.
- Searches by username, display name, email, account ID, or bio text.
- Filters active, unconfirmed, and banned accounts.
- Account detail shows profile/auth metadata and social totals.
- Account detail includes recent comments, likes, direct-message metadata, and reports involving the account.

Direct-message bodies are not returned to the Community console. Message visibility is limited to participants, timestamp, and read state.

### Social

- Recent comments and replies.
- Recent video likes and comment likes.
- Recent direct-message metadata.
- Search and type filters.

### Reports

- Recent social reports.
- Reporter and reported-account identity.
- Reason, details, timestamp, and whether the report references a direct message.
- Search and reason filters.

## Security

Community data uses the existing private `feedback-admin` Edge Function and existing `x-admin-token` authentication. The service-role key remains on the backend and is never included in the admin APK.

The new Community actions are read-only. Existing feedback management actions remain unchanged.

## Backend actions

- `community_overview`
- `accounts`
- `account_detail`
- `social_activity`
- `reports`

The repository copy of `feedback-admin` also retains the production `active_counts` action so deploying this version does not roll back the live endpoint.

## Data changes

No database schema migration is required. The console reads the existing auth, profile, comment, like, messaging, report, and block data already used by ZeroChill.
