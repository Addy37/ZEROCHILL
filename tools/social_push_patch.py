from pathlib import Path
import textwrap

ROOT = Path('.')

def write(path: str, content: str):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(content).lstrip(), encoding='utf-8')


def replace_once(path: str, old: str, new: str):
    target = ROOT / path
    text = target.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:100]!r}')
    target.write_text(text.replace(old, new, 1), encoding='utf-8')


write('supabase/functions/push-dispatch/handler.ts', r'''
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.58.0";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{12}$/i;
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
  const fields = Object.keys(record).sort();
  const messageId = String(record.message_id ?? "").trim();
  if (fields.length === 1 && fields[0] === "message_id" && UUID.test(messageId)) {
    return { kind: "direct_message", messageId };
  }

  const eventType = String(record.event_type ?? "").trim();
  if (eventType === "comment_reply") {
    const replyId = String(record.reply_id ?? "").trim();
    if (fields.length === 2 && fields[0] === "event_type" && fields[1] === "reply_id" && UUID.test(replyId)) {
      return { kind: "comment_reply", replyId };
    }
  }
  if (eventType === "comment_like") {
    const commentId = String(record.comment_id ?? "").trim();
    const userId = String(record.user_id ?? "").trim();
    if (fields.length === 3 && fields[0] === "comment_id" && fields[1] === "event_type"
      && fields[2] === "user_id" && UUID.test(commentId) && UUID.test(userId)) {
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
      grant_type: "urn:ietf:params:oauth-type:jwt-bearer".replace("oauth-type", "oauth"),
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

function pemToBytes(pem: string): Uint8Array {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  if (!base64) throw new Error("Missing Firebase private key");
  const decoded = atob(base64);
  const result = new Uint8Array(decoded.length);
  for (let i = 0; i < decoded.length; i++) result[i] = decoded.charCodeAt(i);
  return result;
}

function base64UrlText(value: string): string {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
''')

write('supabase/functions/push-dispatch/handler_test.ts', r'''
import { cleanPageUrl, cleanSenderName, parseDispatchRequest, parseServiceAccount } from "./handler.ts";

function assertEquals(actual: unknown, expected: unknown) {
  if (actual !== expected) throw new Error(`Expected ${String(expected)}, got ${String(actual)}`);
}

function assertThrows(fn: () => unknown) {
  let threw = false;
  try { fn(); } catch { threw = true; }
  if (!threw) throw new Error("Expected function to throw");
}

Deno.test("sender names are privacy-safe and bounded", () => {
  assertEquals(cleanSenderName("  vjplus  "), "vjplus");
  assertEquals(cleanSenderName("\n\t"), "Someone on ZEROCHILL");
  assertEquals(cleanSenderName("x".repeat(100)).length, 64);
});

Deno.test("push requests accept only strict supported event shapes", () => {
  const dm = parseDispatchRequest({ message_id: "12345678-1234-4234-8234-123456789012" });
  assertEquals(dm.kind, "direct_message");
  const reply = parseDispatchRequest({
    event_type: "comment_reply",
    reply_id: "22345678-1234-4234-8234-123456789012",
  });
  assertEquals(reply.kind, "comment_reply");
  const like = parseDispatchRequest({
    event_type: "comment_like",
    comment_id: "32345678-1234-4234-8234-123456789012",
    user_id: "42345678-1234-4234-8234-123456789012",
  });
  assertEquals(like.kind, "comment_like");
  assertThrows(() => parseDispatchRequest({
    event_type: "comment_reply",
    reply_id: "22345678-1234-4234-8234-123456789012",
    body: "must not be accepted",
  }));
  assertThrows(() => parseDispatchRequest({ event_type: "comment_like" }));
});

Deno.test("social content URLs are bounded and strip fragments", () => {
  assertEquals(cleanPageUrl("https://example.test/video/1#comments"), "https://example.test/video/1");
  assertEquals(cleanPageUrl("javascript:alert(1)"), "");
  assertEquals(cleanPageUrl("not a url"), "");
});

Deno.test("service account configuration is validated without exposing secrets", () => {
  const parsed = parseServiceAccount(JSON.stringify({
    project_id: "zerochill-test",
    client_email: "push@example.test",
    private_key: "-----BEGIN PRIVATE KEY-----\nZmFrZQ==\n-----END PRIVATE KEY-----",
    token_uri: "https://oauth2.googleapis.com/token",
  }));
  assertEquals(parsed.project_id, "zerochill-test");
  assertEquals(parsed.client_email, "push@example.test");
  assertThrows(() => parseServiceAccount("{}"));
});
''')

write('supabase/migrations/202610250006_social_push_webhooks.sql', r'''
create or replace function public.enqueue_zerochill_comment_reply_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    project_url text;
    webhook_key text;
begin
    if new.parent_id is null then
        return new;
    end if;

    select decrypted_secret into project_url
    from vault.decrypted_secrets
    where name = 'zerochill_project_url'
    limit 1;

    select decrypted_secret into webhook_key
    from vault.decrypted_secrets
    where name = 'zerochill_push_webhook_key'
    limit 1;

    if coalesce(project_url, '') = '' or coalesce(webhook_key, '') = '' then
        return new;
    end if;

    perform net.http_post(
        url := rtrim(project_url, '/') || '/functions/v1/push-dispatch',
        body := jsonb_build_object(
            'event_type', 'comment_reply',
            'reply_id', new.id::text
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-zerochill-push-key', webhook_key
        ),
        timeout_milliseconds := 3000
    );
    return new;
end;
$$;

revoke execute on function public.enqueue_zerochill_comment_reply_push() from public, anon, authenticated;

drop trigger if exists comment_reply_push_webhook on public.comments;
create trigger comment_reply_push_webhook
after insert on public.comments
for each row
when (new.parent_id is not null)
execute function public.enqueue_zerochill_comment_reply_push();

create or replace function public.enqueue_zerochill_comment_like_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    project_url text;
    webhook_key text;
begin
    select decrypted_secret into project_url
    from vault.decrypted_secrets
    where name = 'zerochill_project_url'
    limit 1;

    select decrypted_secret into webhook_key
    from vault.decrypted_secrets
    where name = 'zerochill_push_webhook_key'
    limit 1;

    if coalesce(project_url, '') = '' or coalesce(webhook_key, '') = '' then
        return new;
    end if;

    perform net.http_post(
        url := rtrim(project_url, '/') || '/functions/v1/push-dispatch',
        body := jsonb_build_object(
            'event_type', 'comment_like',
            'comment_id', new.comment_id::text,
            'user_id', new.user_id::text
        ),
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-zerochill-push-key', webhook_key
        ),
        timeout_milliseconds := 3000
    );
    return new;
end;
$$;

revoke execute on function public.enqueue_zerochill_comment_like_push() from public, anon, authenticated;

drop trigger if exists comment_like_push_webhook on public.comment_likes;
create trigger comment_like_push_webhook
after insert on public.comment_likes
for each row
execute function public.enqueue_zerochill_comment_like_push();
''')

write('app/src/main/java/com/webapp/crazyshit/ZeroChillFirebaseMessagingService.java', r'''
package com.webapp.crazyshit;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Receives Firebase installation lifecycle callbacks and privacy-safe server push data. */
public final class ZeroChillFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onRegistered(@NonNull String installationId) {
        super.onRegistered(installationId);
        ZeroChillPushRegistration.onRegistered(this, installationId);
    }

    @Override
    public void onUnregistered(@NonNull String installationId) {
        super.onUnregistered(installationId);
        ZeroChillPushRegistration.onUnregistered(this, installationId);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String, String> data = remoteMessage.getData();
        String type = clean(data.get("type"));
        String recipientId = clean(data.get("recipient_id"));
        String currentUserId = ZeroChillSessionStore.currentUserId(this);
        if (currentUserId.isEmpty() || !currentUserId.equalsIgnoreCase(recipientId)) return;

        if ("direct_message".equals(type)) {
            ZeroChillPushNotificationPresenter.showDirectMessage(
                    this,
                    clean(data.get("message_id")),
                    clean(data.get("sender_id")),
                    clean(data.get("sender_name"))
            );
            return;
        }

        String socialType;
        if ("comment_reply".equals(type)) socialType = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY;
        else if ("comment_like".equals(type)) socialType = ZeroChillSocialRepository.SocialActivity.TYPE_LIKE;
        else return;

        String eventId = clean(data.get("event_id"));
        UpdateInboxStore.Entry entry = UpdateInboxStore.recordSocialPush(
                this,
                recipientId,
                eventId,
                socialType,
                clean(data.get("actor_id")),
                clean(data.get("actor_name")),
                clean(data.get("page_url")),
                clean(data.get("comment_id"))
        );
        if (entry == null) return;
        ZeroChillPushNotificationPresenter.showSocialActivity(
                this,
                eventId,
                entry.id,
                socialType,
                clean(data.get("actor_name"))
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
''')

write('app/src/main/java/com/webapp/crazyshit/ZeroChillPushNotificationPresenter.java', r'''
package com.webapp.crazyshit;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

/** Builds privacy-preserving Android notifications for server-delivered ZEROCHILL events. */
final class ZeroChillPushNotificationPresenter {
    static final String CHANNEL_MESSAGES = "messages_v1";
    static final String CHANNEL_SOCIAL = "social_v1";
    private static final String GROUP_MESSAGES = "zerochill_direct_messages";
    private static final String GROUP_SOCIAL = "zerochill_social";

    private ZeroChillPushNotificationPresenter() { }

    static void initialize(Context context) {
        Context app = context.getApplicationContext();
        createMessagesChannel(app);
        createSocialChannel(app);
    }

    static void showDirectMessage(
            Context context,
            String messageId,
            String senderId,
            String senderName
    ) {
        Context app = context.getApplicationContext();
        if (!isUuid(messageId) || !isUuid(senderId) || !canPost(app)) return;
        if (!ZeroChillAccountRepository.hasStoredSession(app)) return;

        createMessagesChannel(app);
        String name = cleanSenderName(senderName);
        PendingIntent open = messagePendingIntent(app, senderId, messageId);

        Notification publicVersion = new NotificationCompat.Builder(app, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New ZEROCHILL message")
                .setContentText("Open ZEROCHILL to view it.")
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setColor(UiPalette.PRIMARY)
                .setContentTitle(name)
                .setContentText("Sent you a message")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name + " sent you a message."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(GROUP_MESSAGES)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManagerCompat.from(app).notify(notificationId(messageId), notification);
    }

    static void showSocialActivity(
            Context context,
            String eventId,
            String entryId,
            String socialType,
            String actorName
    ) {
        Context app = context.getApplicationContext();
        if (clean(eventId).isEmpty() || clean(entryId).isEmpty() || !isSocialType(socialType) || !canPost(app)) return;
        if (!ZeroChillAccountRepository.hasStoredSession(app)) return;

        createSocialChannel(app);
        String name = cleanSenderName(actorName);
        boolean reply = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(socialType);
        String action = reply ? "Replied to your comment" : "Liked your comment";
        PendingIntent open = socialPendingIntent(app, entryId, eventId);

        Notification publicVersion = new NotificationCompat.Builder(app, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New ZEROCHILL social activity")
                .setContentText("Open ZEROCHILL to view it.")
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setColor(UiPalette.PRIMARY)
                .setContentTitle(name)
                .setContentText(action)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name + " " + action.toLowerCase() + "."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(GROUP_SOCIAL)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManagerCompat.from(app).notify(socialNotificationId(eventId), notification);
    }

    static Intent messageIntent(Context context, String senderId) {
        Intent intent = new Intent(context, ZeroChillMessageActivity.class);
        intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, senderId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    static Intent socialIntent(Context context, String entryId) {
        Intent intent = new Intent(context, UpdateInboxActivity.class);
        intent.putExtra(UpdateInboxActivity.EXTRA_SOCIAL_PUSH_ENTRY_ID, entryId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    static int notificationId(String messageId) {
        return 12_000 + Math.abs((messageId == null ? "" : messageId).hashCode() % 20_000);
    }

    static int socialNotificationId(String eventId) {
        return 32_000 + Math.abs((eventId == null ? "" : eventId).hashCode() % 20_000);
    }

    static String cleanSenderName(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > 64) clean = clean.substring(0, 64).trim();
        return clean.isEmpty() ? "Someone on ZEROCHILL" : clean;
    }

    private static PendingIntent messagePendingIntent(Context context, String senderId, String messageId) {
        return PendingIntent.getActivity(
                context,
                notificationId(messageId),
                messageIntent(context, senderId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent socialPendingIntent(Context context, String entryId, String eventId) {
        return PendingIntent.getActivity(
                context,
                socialNotificationId(eventId),
                socialIntent(context, entryId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void createMessagesChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Direct message alerts from other ZEROCHILL users");
        channel.enableLights(true);
        channel.setLightColor(UiPalette.PRIMARY);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    private static void createSocialChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_SOCIAL,
                "Social",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Replies and likes on your ZEROCHILL comments");
        channel.enableLights(true);
        channel.setLightColor(UiPalette.PRIMARY);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    private static boolean isUuid(String value) {
        try {
            String clean = value == null ? "" : value.trim();
            return UUID.fromString(clean).toString().equalsIgnoreCase(clean);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSocialType(String value) {
        return ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(value)
                || ZeroChillSocialRepository.SocialActivity.TYPE_LIKE.equals(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
''')

store_path = 'app/src/main/java/com/webapp/crazyshit/UpdateInboxStore.java'
store = (ROOT / store_path).read_text(encoding='utf-8')
needle = '''    static void removeAccount(Context context, String accountId) {\n'''
insert = r'''    static Entry recordSocialPush(
            Context context,
            String accountId,
            String eventId,
            String socialType,
            String actorId,
            String actorName,
            String pageUrl,
            String commentId
    ) {
        if (context == null) return null;
        String account = clean(accountId);
        String event = clean(eventId);
        String type = clean(socialType);
        String actor = clean(actorId);
        String page = clean(pageUrl);
        String comment = clean(commentId);
        if (account.isEmpty() || event.isEmpty() || actor.isEmpty() || page.isEmpty() || comment.isEmpty()) return null;
        if (!account.equals(ZeroChillSessionStore.currentUserId(context))) return null;
        if (!ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(type)
                && !ZeroChillSocialRepository.SocialActivity.TYPE_LIKE.equals(type)) return null;

        Entry entry = new Entry();
        entry.timestamp = System.currentTimeMillis();
        entry.category = CATEGORY_SOCIAL;
        entry.sourceKey = "social";
        entry.sourceLabel = "Social";
        entry.accountId = account;
        entry.socialType = type;
        entry.pageUrl = page;
        entry.commentId = comment;
        entry.actorId = actor;
        entry.actorName = clean(actorName).isEmpty() ? "Someone on ZEROCHILL" : clean(actorName);
        entry.title = entry.actorName + (ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(type)
                ? " replied to your comment" : " liked your comment");
        entry.subtitle = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(type)
                ? "Open to view the reply." : "Open to view your comment.";
        entry.count = 1;
        entry.fingerprint = fingerprint(CATEGORY_SOCIAL, account + "|" + event, Collections.emptyList());
        entry.id = entry.fingerprint + ":" + entry.timestamp;

        synchronized (LOCK) {
            ArrayList<Entry> existing = readLocked(context);
            pruneNonFavoriteContent(context, existing);
            CleanupState cleanup = new CleanupState(context);
            if (cleanup.contains(entry)) return null;
            for (Entry current : existing) {
                if (!account.equals(current.accountId) || !entry.fingerprint.equals(current.fingerprint)) continue;
                boolean changed = false;
                if (current.pageUrl.isEmpty()) { current.pageUrl = entry.pageUrl; changed = true; }
                if (current.commentId.isEmpty()) { current.commentId = entry.commentId; changed = true; }
                if (current.actorId.isEmpty()) { current.actorId = entry.actorId; changed = true; }
                if (current.actorName.isEmpty()) { current.actorName = entry.actorName; changed = true; }
                if (changed) writeLocked(context, existing);
                return current;
            }
            existing.add(0, entry);
            dedupeAndTrim(existing);
            writeLocked(context, existing);
            return entry;
        }
    }

    static Entry findVisibleById(Context context, String id, String accountId) {
        String wanted = clean(id);
        if (context == null || wanted.isEmpty()) return null;
        for (Entry entry : allForAccount(context, accountId)) {
            if (wanted.equals(entry.id)) return entry;
        }
        return null;
    }

'''
if needle not in store:
    raise SystemExit('UpdateInboxStore removeAccount anchor missing')
store = store.replace(needle, insert + needle, 1)
old_refresh = '''                        if (!entry.avatarUrl.equals(current.avatarUrl) || !entry.actorId.equals(current.actorId)) {\n                            current.avatarUrl = entry.avatarUrl;\n                            current.actorId = entry.actorId;\n                            refreshed = true;\n                        }\n'''
new_refresh = '''                        boolean changed = false;\n                        if (!entry.avatarUrl.equals(current.avatarUrl)) { current.avatarUrl = entry.avatarUrl; changed = true; }\n                        if (!entry.actorId.equals(current.actorId)) { current.actorId = entry.actorId; changed = true; }\n                        if (!entry.actorName.equals(current.actorName)) { current.actorName = entry.actorName; changed = true; }\n                        if (!entry.title.equals(current.title)) { current.title = entry.title; changed = true; }\n                        if (!entry.subtitle.equals(current.subtitle)) { current.subtitle = entry.subtitle; changed = true; }\n                        if (!entry.pageUrl.equals(current.pageUrl)) { current.pageUrl = entry.pageUrl; changed = true; }\n                        if (!entry.videoTitle.equals(current.videoTitle)) { current.videoTitle = entry.videoTitle; changed = true; }\n                        if (!entry.commentId.equals(current.commentId)) { current.commentId = entry.commentId; changed = true; }\n                        if (changed) refreshed = true;\n'''
if old_refresh not in store:
    raise SystemExit('UpdateInboxStore social refresh anchor missing')
store = store.replace(old_refresh, new_refresh, 1)
(ROOT / store_path).write_text(store, encoding='utf-8')

activity_path = 'app/src/main/java/com/webapp/crazyshit/UpdateInboxActivity.java'
activity = (ROOT / activity_path).read_text(encoding='utf-8')
activity = activity.replace(
    'public final class UpdateInboxActivity extends Activity {\n',
    'public final class UpdateInboxActivity extends Activity {\n    static final String EXTRA_SOCIAL_PUSH_ENTRY_ID = "zerochill_social_push_entry_id";\n',
    1
)
activity = activity.replace(
    '    private boolean refreshAvatarsPending;\n',
    '    private boolean refreshAvatarsPending;\n    private String pendingSocialPushEntryId = "";\n',
    1
)
activity = activity.replace(
    '        historyAccount = ZeroChillSessionStore.currentUserId(this);\n',
    '        historyAccount = ZeroChillSessionStore.currentUserId(this);\n        pendingSocialPushEntryId = socialPushEntryId(getIntent());\n',
    1
)
activity = activity.replace(
    '        refreshAvatarsPending = true;\n        render();\n    }\n\n    private void refreshStoredAvatars',
    '        refreshAvatarsPending = true;\n        render();\n        if (recycler != null) recycler.post(this::openPendingSocialPush);\n    }\n\n    @Override\n    protected void onNewIntent(Intent intent) {\n        super.onNewIntent(intent);\n        setIntent(intent);\n        pendingSocialPushEntryId = socialPushEntryId(intent);\n        if (observing && recycler != null) recycler.post(this::openPendingSocialPush);\n    }\n\n    private void openPendingSocialPush() {\n        String id = pendingSocialPushEntryId == null ? "" : pendingSocialPushEntryId.trim();\n        if (id.isEmpty() || isFinishing() || isDestroyed()) return;\n        pendingSocialPushEntryId = "";\n        UpdateInboxStore.Entry entry = UpdateInboxStore.findVisibleById(\n                this, id, ZeroChillSessionStore.currentUserId(this));\n        if (entry != null && UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)) {\n            openSocial(entry, false);\n        }\n    }\n\n    private static String socialPushEntryId(Intent intent) {\n        return intent == null ? "" : String.valueOf(intent.getStringExtra(EXTRA_SOCIAL_PUSH_ENTRY_ID) == null\n                ? "" : intent.getStringExtra(EXTRA_SOCIAL_PUSH_ENTRY_ID)).trim();\n    }\n\n    private void refreshStoredAvatars',
    1
)
(ROOT / activity_path).write_text(activity, encoding='utf-8')

write('app/src/test/java/com/webapp/crazyshit/ZeroChillSocialPushRoutingTest.java', r'''
package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = ZeroChillSocialPushRoutingTest.Sessions.class)
public class ZeroChillSocialPushRoutingTest {
    private static final String ACCOUNT = "12345678-1234-4234-8234-123456789012";
    private static final String OTHER = "22345678-1234-4234-8234-123456789012";
    private static String current = ACCOUNT;
    private Application app;

    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) { return current; }
    }

    @Before public void reset() {
        current = ACCOUNT;
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void socialPushIsStoredForCurrentAccountAndRoutesByEntryId() {
        UpdateInboxStore.Entry entry = UpdateInboxStore.recordSocialPush(
                app,
                ACCOUNT,
                "reply:32345678-1234-4234-8234-123456789012",
                ZeroChillSocialRepository.SocialActivity.TYPE_REPLY,
                OTHER,
                "Other User",
                "https://www.crazyshit.com/video/example",
                "32345678-1234-4234-8234-123456789012"
        );
        assertNotNull(entry);
        assertEquals("Other User replied to your comment", entry.title);
        assertNotNull(UpdateInboxStore.findVisibleById(app, entry.id, ACCOUNT));

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent intent = ZeroChillPushNotificationPresenter.socialIntent(activity, entry.id);
        assertEquals(UpdateInboxActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(entry.id, intent.getStringExtra(UpdateInboxActivity.EXTRA_SOCIAL_PUSH_ENTRY_ID));
    }

    @Test public void socialPushCannotLeakAcrossAccounts() {
        assertNull(UpdateInboxStore.recordSocialPush(
                app,
                OTHER,
                "like:32345678-1234-4234-8234-123456789012:" + OTHER,
                ZeroChillSocialRepository.SocialActivity.TYPE_LIKE,
                OTHER,
                "Other User",
                "https://www.crazyshit.com/video/example",
                "32345678-1234-4234-8234-123456789012"
        ));
    }
}
''')

# Extend the existing presenter test with social channel/id/intent coverage without duplicating its setup.
test_path = ROOT / 'app/src/test/java/com/webapp/crazyshit/ZeroChillPushNotificationPresenterTest.java'
test = test_path.read_text(encoding='utf-8')
anchor = '''    @Test\n    public void messageIdProducesStableNotificationId() {\n'''
addition = '''    @Test\n    public void socialIntentOpensNotificationCenterWithExactEntry() {\n        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();\n        String entryId = "social-entry-123";\n\n        Intent intent = ZeroChillPushNotificationPresenter.socialIntent(activity, entryId);\n\n        assertEquals(UpdateInboxActivity.class.getName(), intent.getComponent().getClassName());\n        assertEquals(entryId, intent.getStringExtra(UpdateInboxActivity.EXTRA_SOCIAL_PUSH_ENTRY_ID));\n    }\n\n    @Test\n    public void socialEventProducesStableSeparateNotificationId() {\n        String eventId = "reply:12345678-1234-4234-8234-123456789012";\n        assertEquals(\n                ZeroChillPushNotificationPresenter.socialNotificationId(eventId),\n                ZeroChillPushNotificationPresenter.socialNotificationId(eventId)\n        );\n    }\n\n'''
if anchor not in test:
    raise SystemExit('Presenter test anchor missing')
test_path.write_text(test.replace(anchor, addition + anchor, 1), encoding='utf-8')

print('social push patch applied')
