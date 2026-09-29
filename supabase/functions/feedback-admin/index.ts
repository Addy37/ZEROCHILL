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
        .insert({
          feedback_id: id,
          sender: "developer",
          message,
        })
        .select("id,feedback_id,sender,message,created_at,read_at")
        .single();
      if (messageError) throw messageError;

      const update: Record<string, unknown> = { developer_reply: message };
      if (status) update.status = status;
      const { error: updateError } = await db
        .from("app_feedback")
        .update(update)
        .eq("id", id);
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
      if (developerReply.length > 2000) {
        return response({ error: "Reply is too long." }, 400);
      }

      if (developerReply && developerReply !== String(current.developer_reply ?? "").trim()) {
        const { error: messageError } = await db
          .from("feedback_messages")
          .insert({
            feedback_id: id,
            sender: "developer",
            message: developerReply,
          });
        if (messageError) throw messageError;
      }

      const { data, error } = await db
        .from("app_feedback")
        .update({
          status,
          developer_reply: developerReply || null,
        })
        .eq("id", id)
        .select("id,status,developer_reply,updated_at")
        .single();
      if (error) throw error;
      return response({ item: data });
    }

    if (action === "analytics") {
      const { data, error } = await db.rpc("analytics_dashboard");
      if (error) throw error;
      return response({ analytics: data ?? {} });
    }

    return response({ error: "Invalid action." }, 400);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to manage feedback." }, 500);
  }
});
