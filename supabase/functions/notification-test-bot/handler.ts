import { createClient } from "npm:@supabase/supabase-js@2.58.0";

const BOT_USERNAME = "ZeroChillTestBot";
const BOT_EMAIL = "zerochill-test-bot@example.com";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });

export async function handleNotificationTestBot(request: Request): Promise<Response> {
  if (request.method !== "POST") return json(405, { error: "Use POST." });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return json(503, { error: "Test bot is unavailable." });

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const suppliedKey = String(request.headers.get("x-zerochill-push-key") ?? "").trim();
  if (!suppliedKey) return json(401, { error: "Unauthorized." });
  const { data: verified, error: verifyError } = await admin.rpc(
    "verify_zerochill_push_webhook",
    { p_secret: suppliedKey },
  );
  if (verifyError || verified !== true) return json(401, { error: "Unauthorized." });

  let payload: unknown;
  try {
    payload = await request.json();
  } catch {
    return json(400, { error: "Invalid request." });
  }
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    return json(400, { error: "Invalid request." });
  }

  const body = payload as Record<string, unknown>;
  const action = String(body.action ?? "").trim();
  if (action === "ensure_bot") {
    const bot = await ensureBot(admin);
    return json(200, { ready: true, username: BOT_USERNAME, user_id: bot.userId });
  }

  if (action === "send_dm") {
    const recipientId = String(body.recipient_id ?? "").trim();
    const message = String(body.message ?? "").trim();
    if (!UUID.test(recipientId)) return json(400, { error: "Invalid recipient." });
    if (!message || message.length > 500) {
      return json(400, { error: "Test message must contain 1 to 500 characters." });
    }

    const bot = await ensureBot(admin);
    if (recipientId === bot.userId) return json(400, { error: "Test bot cannot message itself." });

    const { data: recipient, error: recipientError } = await admin
      .from("profiles")
      .select("user_id")
      .eq("user_id", recipientId)
      .maybeSingle();
    if (recipientError) {
      console.error("Test bot recipient lookup failed", recipientError.message);
      return json(500, { error: "Unable to send test message." });
    }
    if (!recipient) return json(404, { error: "Recipient not found." });

    const messageId = crypto.randomUUID();
    const { error: insertError } = await admin
      .from("direct_messages")
      .insert({
        id: messageId,
        sender_id: bot.userId,
        recipient_id: recipientId,
        body: message,
      });
    if (insertError) {
      console.error("Test bot DM insert failed", insertError.message);
      return json(500, { error: "Unable to send test message." });
    }

    return json(200, { sent: true, message_id: messageId, sender: BOT_USERNAME });
  }

  return json(400, { error: "Unknown action." });
}

async function ensureBot(admin: ReturnType<typeof createClient>): Promise<{ userId: string }> {
  const { data: existing, error: existingError } = await admin
    .from("profiles")
    .select("user_id,username")
    .eq("username", BOT_USERNAME)
    .maybeSingle();
  if (existingError) throw existingError;

  let userId = String(existing?.user_id ?? "").trim();
  if (!UUID.test(userId)) {
    const password = `${crypto.randomUUID()}${crypto.randomUUID()}Aa1!`;
    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email: BOT_EMAIL,
      password,
      email_confirm: true,
      user_metadata: {
        username: BOT_USERNAME,
        adult_confirmed: true,
        terms_accepted: true,
        system_account: true,
      },
    });
    if (createError || !created.user) {
      throw createError ?? new Error("Unable to create test bot");
    }
    userId = created.user.id;
  }

  const { error: profileError } = await admin
    .from("profiles")
    .update({
      display_name: "ZEROCHILL Test Bot",
      bio: "Official ZEROCHILL notification test account. Automated test messages only.",
    })
    .eq("user_id", userId);
  if (profileError) throw profileError;

  const { error: preferenceError } = await admin
    .from("notification_preferences")
    .upsert({
      user_id: userId,
      replies: false,
      likes: false,
      direct_messages: false,
      creator_updates: false,
      app_updates: false,
    });
  if (preferenceError) throw preferenceError;

  return { userId };
}
