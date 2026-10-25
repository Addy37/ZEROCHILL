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
  const dm = parseDispatchRequest({ message_id: "550e8400-e29b-41d4-a716-446655440000" });
  assertEquals(dm.kind, "direct_message");
  const reply = parseDispatchRequest({
    event_type: "comment_reply",
    reply_id: "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
  });
  assertEquals(reply.kind, "comment_reply");
  const like = parseDispatchRequest({
    event_type: "comment_like",
    comment_id: "7d444840-9dc0-4c2d-bccf-1de3b50ac4d5",
    user_id: "8f14e45f-ea5a-4f1d-a2b3-c4d5e6f78901",
  });
  assertEquals(like.kind, "comment_like");
  assertThrows(() => parseDispatchRequest({
    event_type: "comment_reply",
    reply_id: "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
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
