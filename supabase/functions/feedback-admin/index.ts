import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const encoder = new TextEncoder();
const allowedStatuses = new Set([
  "submitted",
  "reviewing",
  "planned",
  "in_progress",
  "completed",
  "declined",
]);
const feedbackIdPattern = /^[0-9a-f-]{36}$/i;
const userIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return Array.from(new Uint8Array(digest))
    .map((part) => part.toString(16).padStart(2, "0"))
    .join("");
}

function constantTimeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i++) {
    difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  }
  return difference === 0;
}

function validMessage(value: string) {
  return value.length >= 1 && value.length <= 2000;
}

function dateKey(value: Date) {
  return value.toISOString().slice(0, 10);
}

function utcPeriodStarts(now = new Date()) {
  const day = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const week = new Date(day);
  week.setUTCDate(day.getUTCDate() - ((day.getUTCDay() + 6) % 7));
  const month = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  return { day: dateKey(day), week: dateKey(week), month: dateKey(month) };
}

function clean(value: unknown) {
  return String(value ?? "").trim();
}

function lower(value: unknown) {
  return clean(value).toLocaleLowerCase();
}

function limitFrom(value: unknown, fallback = 100) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(200, Math.max(1, Math.trunc(parsed)));
}

function accountStatus(user: any) {
  const deletedAt = clean(user?.deleted_at);
  if (deletedAt) return "deleted";
  const bannedUntil = clean(user?.banned_until);
  if (bannedUntil) {
    const bannedTime = Date.parse(bannedUntil);
    if (Number.isFinite(bannedTime) && bannedTime > Date.now()) return "banned";
  }
  if (!clean(user?.email_confirmed_at)) return "unconfirmed";
  return "active";
}

function profileName(profile: any, userId = "") {
  const display = clean(profile?.display_name);
  if (display) return display;
  const username = clean(profile?.username);
  if (username) return `@${username}`;
  return userId;
}

function accountRow(user: any, profile: any) {
  return {
    id: clean(user?.id),
    email: clean(user?.email),
    username: clean(profile?.username),
    display_name: clean(profile?.display_name),
    bio: clean(profile?.bio),
    avatar_path: clean(profile?.avatar_path),
    status: accountStatus(user),
    created_at: clean(user?.created_at || profile?.created_at),
    last_sign_in_at: user?.last_sign_in_at ?? null,
    email_confirmed_at: user?.email_confirmed_at ?? null,
    banned_until: user?.banned_until ?? null,
    deleted_at: user?.deleted_at ?? null,
  };
}

function matchesAccount(item: any, query: string) {
  if (!query) return true;
  const haystack = [item.id, item.email, item.username, item.display_name, item.bio]
    .map(lower)
    .join("\n");
  return haystack.includes(query);
}

function socialMatches(item: any, query: string) {
  if (!query) return true;
  const haystack = [
    item.id,
    item.user_id,
    item.username,
    item.display_name,
    item.text,
    item.secondary,
    item.state,
    item.type,
  ].map(lower).join("\n");
  return haystack.includes(query);
}

function reportMatches(item: any, query: string) {
  if (!query) return true;
  const haystack = [
    item.id,
    item.reporter_id,
    item.reporter_name,
    item.reported_user_id,
    item.reported_name,
    item.reason,
    item.details,
    item.direct_message_id,
  ].map(lower).join("\n");
  return haystack.includes(query);
}

async function listAllUsers(db: ReturnType<typeof createClient>) {
  const users: any[] = [];
  const perPage = 1000;
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await db.auth.admin.listUsers({ page, perPage });
    if (error) throw error;
    const pageUsers = data?.users ?? [];
    users.push(...pageUsers);
    if (pageUsers.length < perPage) break;
  }
  return users;
}

async function loadProfiles(db: ReturnType<typeof createClient>) {
  const profiles: any[] = [];
  const pageSize = 1000;
  for (let offset = 0; offset < 20_000; offset += pageSize) {
    const { data, error } = await db
      .from("profiles")
      .select("user_id,username,display_name,bio,avatar_path,created_at,updated_at")
      .order("created_at", { ascending: false })
      .range(offset, offset + pageSize - 1);
    if (error) throw error;
    const rows = data ?? [];
    profiles.push(...rows);
    if (rows.length < pageSize) break;
  }
  return profiles;
}

function profileMap(profiles: any[]) {
  const map = new Map<string, any>();
  for (const profile of profiles) map.set(clean(profile.user_id), profile);
  return map;
}

async function activeCount(
  db: ReturnType<typeof createClient>,
  periodType: "day" | "week" | "month",
  periodStart: string,
) {
  const { count, error } = await db
    .from("analytics_uniques")
    .select("anon_key", { count: "exact", head: true })
    .eq("period_type", periodType)
    .eq("period_start", periodStart)
    .eq("metric", "app_open")
    .eq("value", "all");
  if (error) throw error;
  return count ?? 0;
}

async function exactCount(query: any) {
  const { count, error } = await query;
  if (error) throw error;
  return count ?? 0;
}

async function feedbackById(db: ReturnType<typeof createClient>, id: string) {
  if (!feedbackIdPattern.test(id)) return null;
  const { data, error } = await db
    .from("app_feedback")
    .select("id,type,message,rating,status,developer_reply,app_version,android_version,device,section,created_at,updated_at")
    .eq("id", id)
    .maybeSingle();
  if (error) throw error;
  return data;
}

async function messagesFor(
  db: ReturnType<typeof createClient>,
  feedbackIds: string[],
  ascending = true,
) {
  if (feedbackIds.length === 0) return [];
  const { data, error } = await db
    .from("feedback_messages")
    .select("id,feedback_id,sender,message,created_at,read_at")
    .in("feedback_id", feedbackIds)
    .order("created_at", { ascending })
    .order("id", { ascending });
  if (error) throw error;
  return data ?? [];
}

function commentActivity(row: any, profiles: Map<string, any>) {
  const userId = clean(row.user_id);
  const profile = profiles.get(userId);
  return {
    type: "comment",
    id: clean(row.id),
    user_id: userId,
    username: clean(profile?.username),
    display_name: clean(profile?.display_name),
    text: clean(row.body),
    secondary: clean(row.video_title) || clean(row.canonical_url) || clean(row.content_key),
    created_at: clean(row.created_at),
    state: clean(row.parent_id) ? "reply" : "comment",
  };
}

function videoLikeActivity(row: any, profiles: Map<string, any>) {
  const userId = clean(row.user_id);
  const profile = profiles.get(userId);
  return {
    type: "video_like",
    id: `${clean(row.content_key)}:${userId}`,
    user_id: userId,
    username: clean(profile?.username),
    display_name: clean(profile?.display_name),
    text: "",
    secondary: clean(row.content_key),
    created_at: clean(row.created_at),
    state: "liked",
  };
}

function commentLikeActivity(row: any, profiles: Map<string, any>, comments: Map<string, any>) {
  const userId = clean(row.user_id);
  const profile = profiles.get(userId);
  const commentId = clean(row.comment_id);
  const comment = comments.get(commentId);
  return {
    type: "comment_like",
    id: `${commentId}:${userId}`,
    user_id: userId,
    username: clean(profile?.username),
    display_name: clean(profile?.display_name),
    text: comment ? clean(comment.body) : "",
    secondary: comment
      ? (clean(comment.video_title) || `Comment ${commentId}`)
      : `Comment ${commentId}`,
    created_at: clean(row.created_at),
    state: "liked",
  };
}

function messageActivity(row: any, profiles: Map<string, any>) {
  const senderId = clean(row.sender_id);
  const recipientId = clean(row.recipient_id);
  const sender = profiles.get(senderId);
  const recipient = profiles.get(recipientId);
  return {
    type: "message",
    id: clean(row.id),
    user_id: senderId,
    username: clean(sender?.username),
    display_name: clean(sender?.display_name),
    text: "",
    secondary: `To ${profileName(recipient, recipientId)}`,
    created_at: clean(row.created_at),
    state: clean(row.read_at) ? "read" : "unread",
  };
}

function reportRow(row: any, profiles: Map<string, any>) {
  const reporterId = clean(row.reporter_id);
  const reportedId = clean(row.reported_user_id);
  return {
    id: clean(row.id),
    reporter_id: reporterId,
    reporter_name: profileName(profiles.get(reporterId), reporterId),
    reported_user_id: reportedId,
    reported_name: profileName(profiles.get(reportedId), reportedId),
    direct_message_id: row.direct_message_id ?? null,
    reason: clean(row.reason),
    details: clean(row.details),
    created_at: clean(row.created_at),
  };
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return response({ error: "Method not allowed." }, 405);

  const suppliedToken = request.headers.get("x-admin-token") ?? "";
  const expectedHash = Deno.env.get("FEEDBACK_ADMIN_TOKEN_HASH") ?? "";
  if (
    !suppliedToken ||
    !expectedHash ||
    !constantTimeEqual(await sha256(suppliedToken), expectedHash)
  ) {
    return response({ error: "Unauthorized." }, 401);
  }

  try {
    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) return response({ error: "Service unavailable." }, 503);

    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const body = await request.json();
    const action = String(body.action ?? "");

    if (action === "list") {
      const { data, error } = await db
        .from("app_feedback")
        .select("id,type,message,rating,status,developer_reply,app_version,android_version,device,section,created_at,updated_at")
        .order("created_at", { ascending: false })
        .limit(200);
      if (error) throw error;

      const rows = data ?? [];
      const messages = await messagesFor(db, rows.map((item) => item.id), true);
      const latest = new Map<string, typeof messages[number]>();
      const unread = new Map<string, number>();
      for (const message of messages) {
        latest.set(message.feedback_id, message);
        if (message.sender === "user" && !message.read_at) {
          unread.set(message.feedback_id, (unread.get(message.feedback_id) ?? 0) + 1);
        }
      }

      const items = rows.map((item) => {
        const last = latest.get(item.id);
        return {
          ...item,
          last_message: last?.message ?? item.developer_reply ?? item.message,
          last_sender: last?.sender ?? (item.developer_reply ? "developer" : "user"),
          last_message_at: last?.created_at ?? item.created_at,
          unread_count: unread.get(item.id) ?? 0,
        };
      }).sort((left, right) =>
        String(right.last_message_at).localeCompare(String(left.last_message_at))
      );
      return response({ items });
    }

    if (action === "thread") {
      const id = String(body.id ?? "");
      const item = await feedbackById(db, id);
      if (!item) return response({ error: "Feedback thread not found." }, 404);

      const now = new Date().toISOString();
      const { error: readError } = await db
        .from("feedback_messages")
        .update({ read_at: now })
        .eq("feedback_id", id)
        .eq("sender", "user")
        .is("read_at", null);
      if (readError) throw readError;

      const messages = await messagesFor(db, [id], true);
      return response({ item, messages });
    }

    if (action === "reply") {
      const id = String(body.id ?? "");
      const status = String(body.status ?? "");
      const message = String(body.message ?? "").trim();
      const item = await feedbackById(db, id);
      if (!item) return response({ error: "Feedback thread not found." }, 404);
      if (!validMessage(message)) {
        return response({ error: "Reply must contain 1 to 2000 characters." }, 400);
      }
      if (status && !allowedStatuses.has(status)) {
        return response({ error: "Invalid status." }, 400);
      }

      const { data: sent, error: messageError } = await db
        .from("feedback_messages")
        .insert({ feedback_id: id, sender: "developer", message })
        .select("id,feedback_id,sender,message,created_at,read_at")
        .single();
      if (messageError) throw messageError;

      const update: Record<string, unknown> = { developer_reply: message };
      if (status) update.status = status;
      const { error: updateError } = await db.from("app_feedback").update(update).eq("id", id);
      if (updateError) throw updateError;
      return response({ message: sent });
    }

    if (action === "status") {
      const id = String(body.id ?? "");
      const status = String(body.status ?? "");
      if (!feedbackIdPattern.test(id)) return response({ error: "Invalid feedback ID." }, 400);
      if (!allowedStatuses.has(status)) return response({ error: "Invalid status." }, 400);
      const { data, error } = await db
        .from("app_feedback")
        .update({ status })
        .eq("id", id)
        .select("id,status,updated_at")
        .single();
      if (error) throw error;
      return response({ item: data });
    }

    if (action === "update") {
      const id = String(body.id ?? "");
      const status = String(body.status ?? "");
      const developerReply = String(body.developer_reply ?? "").trim();
      const current = await feedbackById(db, id);
      if (!current) return response({ error: "Feedback thread not found." }, 404);
      if (!allowedStatuses.has(status)) return response({ error: "Invalid status." }, 400);
      if (developerReply.length > 2000) return response({ error: "Reply is too long." }, 400);

      if (developerReply && developerReply !== String(current.developer_reply ?? "").trim()) {
        const { error: messageError } = await db
          .from("feedback_messages")
          .insert({ feedback_id: id, sender: "developer", message: developerReply });
        if (messageError) throw messageError;
      }

      const { data, error } = await db
        .from("app_feedback")
        .update({ status, developer_reply: developerReply || null })
        .eq("id", id)
        .select("id,status,developer_reply,updated_at")
        .single();
      if (error) throw error;
      return response({ item: data });
    }

    if (action === "delete") {
      const id = String(body.id ?? "");
      if (!feedbackIdPattern.test(id)) return response({ error: "Invalid feedback ID." }, 400);
      const current = await feedbackById(db, id);
      if (!current) return response({ error: "Feedback thread not found." }, 404);
      const { error } = await db.from("app_feedback").delete().eq("id", id);
      if (error) throw error;
      return response({ deleted: true, id });
    }

    if (action === "active_counts") {
      const periods = utcPeriodStarts();
      const [daily, weekly, monthly] = await Promise.all([
        activeCount(db, "day", periods.day),
        activeCount(db, "week", periods.week),
        activeCount(db, "month", periods.month),
      ]);
      return response({
        analytics: {
          generated_at: new Date().toISOString(),
          active_users: { daily, weekly, monthly },
        },
      });
    }

    if (action === "analytics") {
      const { data, error } = await db.rpc("analytics_dashboard");
      if (error) throw error;
      return response({ analytics: data ?? {} });
    }

    if (action === "community_overview") {
      const [users, comments, videoLikes, commentLikes, messages, reports, blocks] = await Promise.all([
        listAllUsers(db),
        exactCount(db.from("comments").select("id", { count: "exact", head: true }).is("deleted_at", null)),
        exactCount(db.from("video_likes").select("content_key", { count: "exact", head: true })),
        exactCount(db.from("comment_likes").select("comment_id", { count: "exact", head: true })),
        exactCount(db.from("direct_messages").select("id", { count: "exact", head: true })),
        exactCount(db.from("social_reports").select("id", { count: "exact", head: true })),
        exactCount(db.from("user_blocks").select("blocker_id", { count: "exact", head: true })),
      ]);
      const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const liveUsers = users.filter((user) => accountStatus(user) !== "deleted");
      const newAccounts7d = liveUsers.filter((user) => {
        const created = Date.parse(clean(user.created_at));
        return Number.isFinite(created) && created >= cutoff;
      }).length;
      return response({
        overview: {
          accounts: liveUsers.length,
          new_accounts_7d: newAccounts7d,
          comments,
          video_likes: videoLikes,
          comment_likes: commentLikes,
          messages,
          reports,
          blocks,
        },
      });
    }

    if (action === "accounts") {
      const query = lower(body.query);
      const filter = clean(body.filter) || "all";
      const limit = limitFrom(body.limit);
      const [users, profiles] = await Promise.all([listAllUsers(db), loadProfiles(db)]);
      const profilesById = profileMap(profiles);
      const items = users
        .map((user) => accountRow(user, profilesById.get(clean(user.id))))
        .filter((item) => filter === "all" || item.status === filter)
        .filter((item) => matchesAccount(item, query))
        .sort((left, right) => clean(right.created_at).localeCompare(clean(left.created_at)))
        .slice(0, limit);
      return response({ items });
    }

    if (action === "account_detail") {
      const userId = clean(body.user_id);
      if (!userIdPattern.test(userId)) return response({ error: "Invalid user ID." }, 400);

      const [users, profiles] = await Promise.all([listAllUsers(db), loadProfiles(db)]);
      const user = users.find((candidate) => clean(candidate.id) === userId);
      if (!user) return response({ error: "Account not found." }, 404);
      const profilesById = profileMap(profiles);
      const account = accountRow(user, profilesById.get(userId));

      const [
        commentsCount,
        videoLikesCount,
        commentLikesCount,
        messagesSent,
        messagesReceived,
        reportsFiled,
        reportsReceived,
        blocksMade,
        blocksReceived,
      ] = await Promise.all([
        exactCount(db.from("comments").select("id", { count: "exact", head: true }).eq("user_id", userId).is("deleted_at", null)),
        exactCount(db.from("video_likes").select("content_key", { count: "exact", head: true }).eq("user_id", userId)),
        exactCount(db.from("comment_likes").select("comment_id", { count: "exact", head: true }).eq("user_id", userId)),
        exactCount(db.from("direct_messages").select("id", { count: "exact", head: true }).eq("sender_id", userId)),
        exactCount(db.from("direct_messages").select("id", { count: "exact", head: true }).eq("recipient_id", userId)),
        exactCount(db.from("social_reports").select("id", { count: "exact", head: true }).eq("reporter_id", userId)),
        exactCount(db.from("social_reports").select("id", { count: "exact", head: true }).eq("reported_user_id", userId)),
        exactCount(db.from("user_blocks").select("blocker_id", { count: "exact", head: true }).eq("blocker_id", userId)),
        exactCount(db.from("user_blocks").select("blocker_id", { count: "exact", head: true }).eq("blocked_id", userId)),
      ]);

      const [commentsResult, videoLikesResult, commentLikesResult, messagesResult, reportsResult] = await Promise.all([
        db.from("comments")
          .select("id,user_id,content_key,canonical_url,video_title,parent_id,body,created_at")
          .eq("user_id", userId)
          .is("deleted_at", null)
          .order("created_at", { ascending: false })
          .limit(10),
        db.from("video_likes")
          .select("content_key,user_id,created_at")
          .eq("user_id", userId)
          .order("created_at", { ascending: false })
          .limit(10),
        db.from("comment_likes")
          .select("comment_id,user_id,created_at")
          .eq("user_id", userId)
          .order("created_at", { ascending: false })
          .limit(10),
        db.from("direct_messages")
          .select("id,sender_id,recipient_id,created_at,read_at")
          .or(`sender_id.eq.${userId},recipient_id.eq.${userId}`)
          .order("created_at", { ascending: false })
          .limit(10),
        db.from("social_reports")
          .select("id,reporter_id,reported_user_id,direct_message_id,reason,details,created_at")
          .or(`reporter_id.eq.${userId},reported_user_id.eq.${userId}`)
          .order("created_at", { ascending: false })
          .limit(10),
      ]);
      for (const result of [commentsResult, videoLikesResult, commentLikesResult, messagesResult, reportsResult]) {
        if (result.error) throw result.error;
      }

      const commentLikeRows = commentLikesResult.data ?? [];
      const commentIds = commentLikeRows.map((row) => clean(row.comment_id)).filter(Boolean);
      const commentsById = new Map<string, any>();
      if (commentIds.length > 0) {
        const { data, error } = await db
          .from("comments")
          .select("id,body,video_title")
          .in("id", commentIds);
        if (error) throw error;
        for (const row of data ?? []) commentsById.set(clean(row.id), row);
      }

      const recentComments = (commentsResult.data ?? []).map((row) => commentActivity(row, profilesById));
      const recentLikes = [
        ...(videoLikesResult.data ?? []).map((row) => videoLikeActivity(row, profilesById)),
        ...commentLikeRows.map((row) => commentLikeActivity(row, profilesById, commentsById)),
      ].sort((left, right) => clean(right.created_at).localeCompare(clean(left.created_at))).slice(0, 10);
      const recentMessages = (messagesResult.data ?? []).map((row) => messageActivity(row, profilesById));
      const recentReports = (reportsResult.data ?? []).map((row) => reportRow(row, profilesById));

      return response({
        detail: {
          account,
          metrics: {
            comments: commentsCount,
            video_likes: videoLikesCount,
            comment_likes: commentLikesCount,
            messages_sent: messagesSent,
            messages_received: messagesReceived,
            reports_filed: reportsFiled,
            reports_received: reportsReceived,
            blocks_made: blocksMade,
            blocks_received: blocksReceived,
          },
          recent_comments: recentComments,
          recent_likes: recentLikes,
          recent_messages: recentMessages,
          recent_reports: recentReports,
        },
      });
    }

    if (action === "social_activity") {
      const query = lower(body.query);
      const filter = clean(body.filter) || "all";
      const limit = limitFrom(body.limit);
      const profiles = await loadProfiles(db);
      const profilesById = profileMap(profiles);
      const items: any[] = [];

      if (filter === "all" || filter === "comments") {
        const { data, error } = await db
          .from("comments")
          .select("id,user_id,content_key,canonical_url,video_title,parent_id,body,created_at")
          .is("deleted_at", null)
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;
        items.push(...(data ?? []).map((row) => commentActivity(row, profilesById)));
      }

      if (filter === "all" || filter === "likes") {
        const [videoLikesResult, commentLikesResult] = await Promise.all([
          db.from("video_likes")
            .select("content_key,user_id,created_at")
            .order("created_at", { ascending: false })
            .limit(200),
          db.from("comment_likes")
            .select("comment_id,user_id,created_at")
            .order("created_at", { ascending: false })
            .limit(200),
        ]);
        if (videoLikesResult.error) throw videoLikesResult.error;
        if (commentLikesResult.error) throw commentLikesResult.error;
        const commentLikeRows = commentLikesResult.data ?? [];
        const commentIds = commentLikeRows.map((row) => clean(row.comment_id)).filter(Boolean);
        const commentsById = new Map<string, any>();
        if (commentIds.length > 0) {
          const { data, error } = await db.from("comments").select("id,body,video_title").in("id", commentIds);
          if (error) throw error;
          for (const row of data ?? []) commentsById.set(clean(row.id), row);
        }
        items.push(...(videoLikesResult.data ?? []).map((row) => videoLikeActivity(row, profilesById)));
        items.push(...commentLikeRows.map((row) => commentLikeActivity(row, profilesById, commentsById)));
      }

      if (filter === "all" || filter === "messages") {
        const { data, error } = await db
          .from("direct_messages")
          .select("id,sender_id,recipient_id,created_at,read_at")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;
        items.push(...(data ?? []).map((row) => messageActivity(row, profilesById)));
      }

      const filtered = items
        .filter((item) => socialMatches(item, query))
        .sort((left, right) => clean(right.created_at).localeCompare(clean(left.created_at)))
        .slice(0, limit);
      return response({ items: filtered });
    }

    if (action === "reports") {
      const query = lower(body.query);
      const filter = clean(body.filter) || "all";
      const limit = limitFrom(body.limit);
      const profiles = await loadProfiles(db);
      const profilesById = profileMap(profiles);
      const { data, error } = await db
        .from("social_reports")
        .select("id,reporter_id,reported_user_id,direct_message_id,reason,details,created_at")
        .order("created_at", { ascending: false })
        .limit(500);
      if (error) throw error;
      const items = (data ?? [])
        .map((row) => reportRow(row, profilesById))
        .filter((item) => filter === "all" || item.reason === filter)
        .filter((item) => reportMatches(item, query))
        .slice(0, limit);
      return response({ items });
    }

    return response({ error: "Invalid action." }, 400);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to manage admin data." }, 500);
  }
});
