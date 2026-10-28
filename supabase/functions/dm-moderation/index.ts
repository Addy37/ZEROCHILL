import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const encoder = new TextEncoder();

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
  for (let i = 0; i < left.length; i++) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

function clean(value: unknown) {
  return String(value ?? "").trim();
}

function lower(value: unknown) {
  return clean(value).toLocaleLowerCase();
}

function limitFrom(value: unknown) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 100;
  return Math.min(100, Math.max(1, Math.trunc(parsed)));
}

async function loadProfiles(db: ReturnType<typeof createClient>) {
  const profiles: any[] = [];
  const pageSize = 1000;
  for (let offset = 0; offset < 20_000; offset += pageSize) {
    const { data, error } = await db
      .from("profiles")
      .select("user_id,username,display_name")
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

function profileName(profile: any, userId: string) {
  const displayName = clean(profile?.display_name);
  if (displayName) return displayName;
  const username = clean(profile?.username);
  if (username) return `@${username}`;
  return userId;
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
    if (String(body.action ?? "") !== "list") return response({ error: "Invalid action." }, 400);

    const query = lower(body.query);
    const filter = clean(body.filter) || "all";
    if (filter !== "all" && filter !== "reported") return response({ error: "Invalid filter." }, 400);
    const limit = limitFrom(body.limit);

    const [messagesResult, reportsResult, profiles] = await Promise.all([
      db.from("direct_messages")
        .select("id,sender_id,recipient_id,body,created_at,read_at")
        .order("created_at", { ascending: false })
        .limit(1000),
      db.from("social_reports")
        .select("id,direct_message_id,reason,details,created_at")
        .not("direct_message_id", "is", null)
        .order("created_at", { ascending: false })
        .limit(1000),
      loadProfiles(db),
    ]);
    if (messagesResult.error) throw messagesResult.error;
    if (reportsResult.error) throw reportsResult.error;

    const profilesById = profileMap(profiles);
    const reportsByMessage = new Map<string, any[]>();
    for (const report of reportsResult.data ?? []) {
      const messageId = clean(report.direct_message_id);
      if (!messageId) continue;
      const current = reportsByMessage.get(messageId) ?? [];
      current.push(report);
      reportsByMessage.set(messageId, current);
    }

    const items = (messagesResult.data ?? []).map((row) => {
      const id = clean(row.id);
      const senderId = clean(row.sender_id);
      const recipientId = clean(row.recipient_id);
      const reports = reportsByMessage.get(id) ?? [];
      return {
        id,
        sender_id: senderId,
        sender_name: profileName(profilesById.get(senderId), senderId),
        recipient_id: recipientId,
        recipient_name: profileName(profilesById.get(recipientId), recipientId),
        body: clean(row.body),
        created_at: clean(row.created_at),
        read_at: row.read_at ?? null,
        report_count: reports.length,
        report_reasons: Array.from(new Set(reports.map((report) => clean(report.reason)).filter(Boolean))),
      };
    }).filter((item) => filter === "all" || item.report_count > 0)
      .filter((item) => {
        if (!query) return true;
        return [
          item.id,
          item.sender_id,
          item.sender_name,
          item.recipient_id,
          item.recipient_name,
          item.body,
          ...item.report_reasons,
        ].map(lower).join("\n").includes(query);
      })
      .slice(0, limit);

    return response({ items });
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to load direct-message moderation data." }, 500);
  }
});
