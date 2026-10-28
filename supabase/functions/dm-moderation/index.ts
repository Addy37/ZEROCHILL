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

function limitFrom(value: unknown, maximum = 100) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return maximum;
  return Math.min(maximum, Math.max(1, Math.trunc(parsed)));
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

async function loadMessages(db: ReturnType<typeof createClient>) {
  const messages: any[] = [];
  const pageSize = 1000;
  for (let offset = 0; offset < 5000; offset += pageSize) {
    const { data, error } = await db.from("direct_messages")
      .select("id,sender_id,recipient_id,body,created_at,read_at")
      .order("created_at", { ascending: false })
      .range(offset, offset + pageSize - 1);
    if (error) throw error;
    const rows = data ?? [];
    messages.push(...rows);
    if (rows.length < pageSize) break;
  }
  return messages;
}

async function loadMessageReports(db: ReturnType<typeof createClient>) {
  const reports: any[] = [];
  const pageSize = 1000;
  for (let offset = 0; offset < 5000; offset += pageSize) {
    const { data, error } = await db.from("social_reports")
      .select("id,direct_message_id,reason,details,created_at")
      .not("direct_message_id", "is", null)
      .order("created_at", { ascending: false })
      .range(offset, offset + pageSize - 1);
    if (error) throw error;
    const rows = data ?? [];
    reports.push(...rows);
    if (rows.length < pageSize) break;
  }
  return reports;
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

function reportMap(reports: any[]) {
  const map = new Map<string, any[]>();
  for (const report of reports) {
    const messageId = clean(report.direct_message_id);
    if (!messageId) continue;
    const current = map.get(messageId) ?? [];
    current.push(report);
    map.set(messageId, current);
  }
  return map;
}

function normalizeMessage(row: any, reportsByMessage: Map<string, any[]>, profilesById: Map<string, any>) {
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
}

function matches(item: any, query: string) {
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
    const action = clean(body.action);
    if (action !== "list" && action !== "conversations" && action !== "conversation") {
      return response({ error: "Invalid action." }, 400);
    }

    const [reports, profiles] = await Promise.all([loadMessageReports(db), loadProfiles(db)]);
    const profilesById = profileMap(profiles);
    const reportsByMessage = reportMap(reports);

    if (action === "conversation") {
      const participantAId = clean(body.participant_a_id);
      const participantBId = clean(body.participant_b_id);
      if (!participantAId || !participantBId || participantAId === participantBId) {
        return response({ error: "Two conversation participants are required." }, 400);
      }
      const limit = limitFrom(body.limit, 1000);
      const [forward, reverse] = await Promise.all([
        db.from("direct_messages")
          .select("id,sender_id,recipient_id,body,created_at,read_at")
          .eq("sender_id", participantAId)
          .eq("recipient_id", participantBId)
          .order("created_at", { ascending: true })
          .limit(limit),
        db.from("direct_messages")
          .select("id,sender_id,recipient_id,body,created_at,read_at")
          .eq("sender_id", participantBId)
          .eq("recipient_id", participantAId)
          .order("created_at", { ascending: true })
          .limit(limit),
      ]);
      if (forward.error) throw forward.error;
      if (reverse.error) throw reverse.error;
      const items = [...(forward.data ?? []), ...(reverse.data ?? [])]
        .sort((left, right) => clean(left.created_at).localeCompare(clean(right.created_at)))
        .slice(-limit)
        .map((row) => normalizeMessage(row, reportsByMessage, profilesById));
      return response({ items });
    }

    const query = lower(body.query);
    const filter = clean(body.filter) || "all";
    if (filter !== "all" && filter !== "reported") return response({ error: "Invalid filter." }, 400);
    const limit = limitFrom(body.limit);
    const messages = (await loadMessages(db)).map((row) => normalizeMessage(row, reportsByMessage, profilesById));

    if (action === "list") {
      const items = messages
        .filter((item) => filter === "all" || item.report_count > 0)
        .filter((item) => matches(item, query))
        .slice(0, limit);
      return response({ items });
    }

    const grouped = new Map<string, any>();
    for (const item of messages) {
      const participantIds = [item.sender_id, item.recipient_id].sort();
      const participantAId = participantIds[0];
      const participantBId = participantIds[1];
      const key = `${participantAId}:${participantBId}`;
      let conversation = grouped.get(key);
      if (!conversation) {
        conversation = {
          participant_a_id: participantAId,
          participant_a_name: profileName(profilesById.get(participantAId), participantAId),
          participant_b_id: participantBId,
          participant_b_name: profileName(profilesById.get(participantBId), participantBId),
          last_message_id: item.id,
          last_message_body: item.body,
          last_sender_id: item.sender_id,
          last_sender_name: item.sender_name,
          last_created_at: item.created_at,
          message_count: 0,
          unread_count: 0,
          report_count: 0,
          report_reasons_set: new Set<string>(),
          query_match: !query,
        };
        grouped.set(key, conversation);
      }
      conversation.message_count += 1;
      if (!item.read_at) conversation.unread_count += 1;
      conversation.report_count += item.report_count;
      for (const reason of item.report_reasons) conversation.report_reasons_set.add(reason);
      if (matches(item, query)) conversation.query_match = true;
    }

    const items = Array.from(grouped.values())
      .filter((item) => item.query_match)
      .filter((item) => filter === "all" || item.report_count > 0)
      .slice(0, limit)
      .map((item) => ({
        participant_a_id: item.participant_a_id,
        participant_a_name: item.participant_a_name,
        participant_b_id: item.participant_b_id,
        participant_b_name: item.participant_b_name,
        last_message_id: item.last_message_id,
        last_message_body: item.last_message_body,
        last_sender_id: item.last_sender_id,
        last_sender_name: item.last_sender_name,
        last_created_at: item.last_created_at,
        message_count: item.message_count,
        unread_count: item.unread_count,
        report_count: item.report_count,
        report_reasons: Array.from(item.report_reasons_set),
      }));

    return response({ items });
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to load direct-message moderation data." }, 500);
  }
});
