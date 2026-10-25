import { cleanSenderName, parseServiceAccount } from "./handler.ts";

function assertEquals(actual: unknown, expected: unknown) {
  if (actual !== expected) throw new Error(`Expected ${String(expected)}, got ${String(actual)}`);
}

function assertThrows(fn: () => unknown) {
  let threw = false;
  try {
    fn();
  } catch {
    threw = true;
  }
  if (!threw) throw new Error("Expected function to throw");
}

Deno.test("sender names are privacy-safe and bounded", () => {
  assertEquals(cleanSenderName("  vjplus  "), "vjplus");
  assertEquals(cleanSenderName("\n\t"), "Someone on ZEROCHILL");
  assertEquals(cleanSenderName("x".repeat(100)).length, 64);
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
