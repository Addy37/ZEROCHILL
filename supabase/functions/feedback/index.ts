import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const allowedTypes = new Set(["feature_request", "bug_report", "general_feedback"]);
const encoder = new TextEncoder();
const feedbackIdPattern = /^[0-9a-f-]{36}$/i;

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function installationHash(value: string) {
  const salt = Deno.env.get("FEEDBACK_ID_SALT") ?? "";
  const bytes = encoder.encode(`${salt}:${value}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((part) => part.toString(16).padStart(2, "0"))
    .join("");
}

function validMessage(value: string, min = 1) {
  return value.length >= min && value.length <= 2000;
}

async function ownedFeedback(db: ReturnType<typeof createClient>, id: string, hash: string) {
  if (!feedbackIdPattern.test(id)) return null;
  const { data, error } = await db
    .from("app_feedback")
    .select("id,type,message,rating,status,developer_reply,created_at,updated_at")
    .eq("id", id)
    .eq("installation_hash", hash)
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

Deno.serve(async (request) => {
  if (request.method !== "POST") return response({ error: "Method not allowed." }, 405);

  try {
    const body = await request.json();
    const installationId = String(body.installation_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(installationId)) {
      return response({ error: "Invalid installation ID." }, 400);
    }

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) return response({ error: "Service unavailable." }, 503);

    const db = createClient(url, serviceKey, { auth: { persistSession: false } });
    const hash = await installationHash(installationId);
    const action = String(body.action ?? "");

    if (action === "list") {
      const { data, error } = await db
        .from("app_feedback")
        .select("id,type,message,rating,status,developer_reply,created_at,updated_at")
        .eq("installation_hash", hash)
        .order("created_at", { ascending: false })
        .limit(50);
      if (error) throw error;

      const rows = data ?? [];
      const messages = await messagesFor(db, rows.map((item) => item.id), true);
      const latest = new Map<string, typeof messages[number]>();
      const unread = new Map<string, number>();
      for (const message of messages) {
        latest.set(message.feedback_id, message);
        if (message.sender === "developer" && !message.read_at) {
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
      const feedbackId = String(body.feedback_id ?? "");
      const item = await ownedFeedback(db, feedbackId, hash);
      if (!item) return response({ error: "Feedback thread not found." }, 404);

      const now = new Date().toISOString();
      const { error: readError } = await db
        .from("feedback_messages")
        .update({ read_at: now })
        .eq("feedback_id", feedbackId)
        .eq("sender", "developer")
        .is("read_at", null);
      if (readError) throw readError;

      const messages = await messagesFor(db, [feedbackId], true);
      return response({ item, messages });
    }

    if (action === "reply") {
      const feedbackId = String(body.feedback_id ?? "");
      const message = String(body.message ?? "").trim();
      const item = await ownedFeedback(db, feedbackId, hash);
      if (!item) return response({ error: "Feedback thread not found." }, 404);
      if (!validMessage(message)) {
        return response({ error: "Message must contain 1 to 2000 characters." }, 400);
      }

      const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
      const { count, error: countError } = await db
        .from("feedback_messages")
        .select("id", { count: "exact", head: true })
        .eq("feedback_id", feedbackId)
        .eq("sender", "user")
        .gte("created_at", oneHourAgo);
      if (countError) throw countError;
      if ((count ?? 0) >= 20) {
        return response({ error: "Please wait before sending more replies." }, 429);
      }

      const { data, error } = await db
        .from("feedback_messages")
        .insert({
          feedback_id: feedbackId,
          sender: "user",
          message,
        })
        .select("id,feedback_id,sender,message,created_at,read_at")
        .single();
      if (error) throw error;
      return response({ message: data }, 201);
    }

    if (action !== "submit") return response({ error: "Invalid action." }, 400);

    const type = String(body.type ?? "");
    const message = String(body.message ?? "").trim();
    const rating = body.rating == null ? null : Number(body.rating);
    if (!allowedTypes.has(type)) return response({ error: "Invalid feedback type." }, 400);
    if (!validMessage(message, 5)) {
      return response({ error: "Message must contain 5 to 2000 characters." }, 400);
    }
    if (rating != null && (!Number.isInteger(rating) || rating < 1 || rating > 5)) {
      return response({ error: "Rating must be from 1 to 5." }, 400);
    }

    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    const { count, error: countError } = await db
      .from("app_feedback")
      .select("id", { count: "exact", head: true })
      .eq("installation_hash", hash)
      .gte("created_at", oneHourAgo);
    if (countError) throw countError;
    if ((count ?? 0) >= 5) {
      return response({ error: "Please wait before sending more feedback." }, 429);
    }

    const { data, error } = await db
      .from("app_feedback")
      .insert({
        installation_hash: hash,
        type,
        message,
        rating,
        app_version: String(body.app_version ?? "unknown").slice(0, 80),
        android_version: String(body.android_version ?? "unknown").slice(0, 80),
        device: String(body.device ?? "unknown").slice(0, 160),
        section: String(body.section ?? "unknown").slice(0, 80),
      })
      .select("id")
      .single();
    if (error) throw error;

    const { error: messageError } = await db
      .from("feedback_messages")
      .insert({
        feedback_id: data.id,
        sender: "user",
        message,
      });
    if (messageError) throw messageError;

    return response({ id: data.id }, 201);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to process feedback." }, 500);
  }
});
