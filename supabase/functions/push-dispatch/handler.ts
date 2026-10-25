import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.58.0";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FID = /^[A-Za-z0-9_\-:.]{20,256}$/;
const MAX_DEVICES = 10;

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });

type FirebaseServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
};

type DeviceRegistration = {
  user_id: string;
  installation_id: string;
  application_id: string;
  firebase_installation_id: string;
};

type DispatchRequest =
  | { kind: "direct_message"; messageId: string }
  | { kind: "comment_reply"; replyId: string }
  | { kind: "comment_like"; commentId: string; userId: string };

type PreferenceField = "direct_messages" | "replies" | "likes";

export function parseDispatchRequest(payload: unknown): DispatchRequest {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    throw new Error("Invalid request");
  }
  const record = payload as Record<string, unknown>;
  const fields = Object.keys(record);
  const hasOnly = (...wanted: string[]) =>
    fields.length === wanted.length && wanted.every((field) => fields.includes(field));
  const messageId = String(record.message_id ?? "").trim();
  if (hasOnly("message_id") && UUID.test(messageId)) {
    return { kind: "direct_message", messageId };
  }

  const eventType = String(record.event_type ?? "").trim();
  if (eventType === "comment_reply") {
    const replyId = String(record.reply_id ?? "").trim();
    if (hasOnly("event_type", "reply_id") && UUID.test(replyId)) {
      return { kind: "comment_reply", replyId };
    }
  }
  if (eventType === "comment_like") {
    const commentId = String(record.comment_id ?? "").trim();
    const userId = String(record.user_id ?? "").trim();
    if (hasOnly("event_type", "comment_id", "user_id")
      && UUID.test(commentId) && UUID.test(userId)) {
      return { kind: "comment_like", commentId, userId };
    }
  }
  throw new Error("Invalid request");
}

export async function handlePushDispatch(request: Request): Promise<Response> {
  if (request.method !== "POST") return json(405, { error: "Use POST." });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) {
    return json(503, { error: "Push delivery is temporarily unavailable." });
  }

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const webhookKey = String(request.headers.get("x-zerochill-push-key") ?? "").trim();
  if (!webhookKey) return json(401, { error: "Unauthorized." });
  const { data: verified, error: verifyError } = await admin.rpc(
    "verify_zerochill_push_webhook",
    { p_secret: webhookKey },
  );
  if (verifyError) {
    console.error("Push webhook verification failed", verifyError.message);
    return json(503, { error: "Push delivery is temporarily unavailable." });
  }
  if (verified !== true) return json(401, { error: "Unauthorized." });

  let rawPayload: unknown;
  try {
    rawPayload = await request.json();
  } catch {
    return json(400, { error: "Invalid request." });
  }

  let dispatch: DispatchRequest;
  try {
    dispatch = parseDispatchRequest(rawPayload);
  } catch {
    return json(400, { error: "Invalid request." });
  }

  let recipientId = "";
  let actorId = "";
  let preferenceField: PreferenceField = "direct_messages";
  let pushData: Record<string, string> = {};

  if (dispatch.kind === "direct_message") {
    const { data: message, error: messageError } = await admin
      .from("direct_messages")
      .select("id,sender_id,recipient_id")
      .eq("id", dispatch.messageId)
      .maybeSingle();
    if (messageError) {
      console.error("Push message lookup failed", messageError.message);
      return json(500, { error: "Unable to prepare notification." });
    }
    if (!message) return json(404, { error: "Message not found." });
    recipientId = String(message.recipient_id ?? "");
    actorId = String(message.sender_id ?? "");
    preferenceField = "direct_messages";
    pushData = {
      type: "direct_message",
      message_id: String(message.id ?? ""),
      sender_id: actorId,
      recipient_id: recipientId,
    };
  } else if (dispatch.kind === "comment_reply") {
    const { data: reply, error: replyError } = await admin
      .from("comments")
      .select("id,parent_id,user_id,canonical_url,deleted_at")
      .eq("id", dispatch.replyId)
      .maybeSingle();
    if (replyError) {
      console.error("Reply push lookup failed", replyError.message);
      return json(500, { error: "Unable to prepare notification." });
    }
    if (!reply || !reply.parent_id || reply.deleted_at) {
      return json(200, { sent: 0, skipped: "reply_unavailable" });
    }
    const { data: parent, error: parentError } = await admin
      .from("comments")
      .select("id,user_id,canonical_url,deleted_at")
      .eq("id", reply.parent_id)
      .maybeSingle();
    if (parentError) {
      console.error("Reply parent lookup failed", parentError.message);
      return json(500, { error: "Unable to prepare notification." });
    }
    if (!parent || parent.deleted_at) return json(200, { sent: 0, skipped: "parent_unavailable" });
    recipientId = String(parent.user_id ?? "");
    actorId = String(reply.user_id ?? "");
    const pageUrl = cleanPageUrl(String(reply.canonical_url || parent.canonical_url || ""));
    if (!pageUrl) return json(200, { sent: 0, skipped: "content_unavailable" });
    preferenceField = "replies";
    pushData = {
      type: "comment_reply",
      event_id: `reply:${String(reply.id ?? "")}`,
      actor_id: actorId,
      recipient_id: recipientId,
      comment_id: String(reply.id ?? ""),
      page_url: pageUrl,
    };
  } else {
    const { data: like, error: likeError } = await admin
      .from("comment_likes")
      .select("comment_id,user_id")
      .eq("comment_id", dispatch.commentId)
      .eq("user_id", dispatch.userId)
      .maybeSingle();
    if (likeError) {
      console.error("Comment-like push lookup failed", likeError.message);
      return json(500, { error: "Unable to prepare notification." });
    }
    if (!like) return json(200, { sent: 0, skipped: "like_unavailable" });
    const { data: comment, error: commentError } = await admin
      .from("comments")
      .select("id,user_id,canonical_url,deleted_at")
      .eq("id", dispatch.commentId)
      .maybeSingle();
    if (commentError) {
      console.error("Liked comment lookup failed", commentError.message);
      return json(500, { error: "Unable to prepare notification." });
    }
    if (!comment || comment.deleted_at) return json(200, { sent: 0, skipped: "comment_unavailable" });
    recipientId = String(comment.user_id ?? "");
    actorId = String(like.user_id ?? "");
    const pageUrl = cleanPageUrl(String(comment.canonical_url ?? ""));
    if (!pageUrl) return json(200, { sent: 0, skipped: "content_unavailable" });
    preferenceField = "likes";
    pushData = {
      type: "comment_like",
      event_id: `like:${String(comment.id ?? "")}:${actorId}`,
      actor_id: actorId,
      recipient_id: recipientId,
      comment_id: String(comment.id ?? ""),
      page_url: pageUrl,
    };
  }

  if (!UUID.test(recipientId) || !UUID.test(actorId)) {
    return json(200, { sent: 0, skipped: "invalid_participant" });
  }
  if (recipientId === actorId) return json(200, { sent: 0, skipped: "self" });

  const { data: preference, error: preferenceError } = await admin
    .from("notification_preferences")
    .select("direct_messages,replies,likes")
    .eq("user_id", recipientId)
    .maybeSingle();
  if (preferenceError) {
    console.error("Push preference lookup failed", preferenceError.message);
    return json(500, { error: "Unable to prepare notification." });
  }
  if (preference && (preference as Record<string, unknown>)[preferenceField] === false) {
    return json(200, { sent: 0, skipped: "disabled" });
  }

  const { data: actorProfile, error: profileError } = await admin
    .from("profiles")
    .select("username,display_name")
    .eq("user_id", actorId)
    .maybeSingle();
  if (profileError) {
    console.error("Push actor profile lookup failed", profileError.message);
    return json(500, { error: "Unable to prepare notification." });
  }
  const actorName = cleanSenderName(actorProfile?.display_name || actorProfile?.username || "");
  if (dispatch.kind === "direct_message") pushData.sender_name = actorName;
  else pushData.actor_name = actorName;

  const { data: devices, error: deviceError } = await admin
    .from("push_device_tokens")
    .select("user_id,installation_id,application_id,firebase_installation_id")
    .eq("user_id", recipientId)
    .order("updated_at", { ascending: false })
    .limit(MAX_DEVICES);
  if (deviceError) {
    console.error("Push device lookup failed", deviceError.message);
    return json(500, { error: "Unable to prepare notification." });
  }
  const registrations = (devices ?? []).filter((device: DeviceRegistration) =>
    FID.test(String(device.firebase_installation_id ?? ""))
  );
  if (!registrations.length) return json(200, { sent: 0, skipped: "no_devices" });

  const { data: firebaseConfig, error: firebaseConfigError } = await admin.rpc(
    "get_zerochill_firebase_service_account",
  );
  if (firebaseConfigError) {
    console.error("Firebase credential lookup failed", firebaseConfigError.message);
    return json(503, { error: "Push delivery is temporarily unavailable." });
  }

  let serviceAccount: FirebaseServiceAccount;
  try {
    serviceAccount = parseServiceAccount(String(firebaseConfig ?? ""));
  } catch (error) {
    console.error("Firebase credentials unavailable", error instanceof Error ? error.message : "Unknown error");
    return json(503, { error: "Push delivery is temporarily unavailable." });
  }

  let accessToken: string;
  try {
    accessToken = await mintAccessToken(serviceAccount);
  } catch (error) {
    console.error("Firebase authorization failed", error instanceof Error ? error.message : "Unknown error");
    return json(503, { error: "Push delivery is temporarily unavailable." });
  }

  let sent = 0;
  let failed = 0;
  for (const registration of registrations) {
    const result = await sendPush(
      accessToken,
      serviceAccount.project_id,
      registration.firebase_installation_id,
      pushData,
    );
    if (result.ok) {
      sent++;
      continue;
    }
    failed++;
    if (result.unregistered) await removeInvalidRegistration(admin, registration);
  }

  return json(200, { sent, failed });
}

export function cleanSenderName(value: string): string {
  const clean = String(value ?? "")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 64)
    .trim();
  return clean || "Someone on ZEROCHILL";
}

export function cleanPageUrl(value: string): string {
  try {
    const url = new URL(String(value ?? "").trim());
    if (url.protocol !== "https:" && url.protocol !== "http:") return "";
    url.hash = "";
    return url.toString().slice(0, 2048);
  } catch {
    return "";
  }
}

export function parseServiceAccount(raw: string): FirebaseServiceAccount {
  const parsed = JSON.parse(raw) as Partial<FirebaseServiceAccount>;
  const projectId = String(parsed.project_id ?? "").trim();
  const email = String(parsed.client_email ?? "").trim();
  const privateKey = String(parsed.private_key ?? "").trim();
  const tokenUri = String(parsed.token_uri ?? "https://oauth2.googleapis.com/token").trim();
  if (!projectId || !email || !privateKey.includes("BEGIN PRIVATE KEY") || !tokenUri.startsWith("https://")) {
    throw new Error("Invalid Firebase service account configuration");
  }
  return {
    project_id: projectId,
    client_email: email,
    private_key: privateKey,
    token_uri: tokenUri,
  };
}

async function mintAccessToken(account: FirebaseServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlText(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64UrlText(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: account.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claims}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${base64UrlBytes(new Uint8Array(signature))}`;

  const response = await fetch(account.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const body = await response.json().catch(() => ({})) as Record<string, unknown>;
  const token = String(body.access_token ?? "").trim();
  if (!response.ok || !token) throw new Error(`OAuth ${response.status}`);
  return token;
}

async function sendPush(
  accessToken: string,
  projectId: string,
  firebaseInstallationId: string,
  data: Record<string, string>,
): Promise<{ ok: boolean; unregistered: boolean }> {
  try {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json; charset=utf-8",
        },
        body: JSON.stringify({
          message: {
            token: firebaseInstallationId,
            data,
            android: {
              priority: "high",
              ttl: "86400s",
            },
          },
        }),
      },
    );
    if (response.ok) return { ok: true, unregistered: false };
    const body = await response.json().catch(() => ({})) as Record<string, unknown>;
    const unregistered = response.status === 404 || hasFcmErrorCode(body, "UNREGISTERED");
    console.error("FCM send failed", response.status, unregistered ? "UNREGISTERED" : "SEND_ERROR");
    return { ok: false, unregistered };
  } catch (error) {
    console.error("FCM network send failed", error instanceof Error ? error.message : "Unknown error");
    return { ok: false, unregistered: false };
  }
}

function hasFcmErrorCode(body: Record<string, unknown>, wanted: string): boolean {
  const error = body.error;
  if (typeof error !== "object" || error === null) return false;
  const details = (error as Record<string, unknown>).details;
  if (!Array.isArray(details)) return false;
  return details.some((detail) =>
    typeof detail === "object" && detail !== null &&
    String((detail as Record<string, unknown>).errorCode ?? "") === wanted
  );
}

async function removeInvalidRegistration(admin: SupabaseClient, registration: DeviceRegistration) {
  const { error } = await admin
    .from("push_device_tokens")
    .delete()
    .eq("user_id", registration.user_id)
    .eq("installation_id", registration.installation_id)
    .eq("application_id", registration.application_id)
    .eq("firebase_installation_id", registration.firebase_installation_id);
  if (error) console.error("Unable to remove invalid Firebase installation", error.message);
}

function pemToBytes(pem: string): ArrayBuffer {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  if (!base64) throw new Error("Missing Firebase private key");
  const decoded = atob(base64);
  const buffer = new ArrayBuffer(decoded.length);
  const result = new Uint8Array(buffer);
  for (let i = 0; i < decoded.length; i++) result[i] = decoded.charCodeAt(i);
  return buffer;
}

function base64UrlText(value: string): string {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
