import { handleDeleteAccount } from "./handler.ts";

const assert = (ok: unknown, message = "Assertion failed") => { if (!ok) throw new Error(message); };
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { "Content-Type": "application/json" },
});
const post = (body: unknown, token = "test.jwt.token") => new Request("https://edge.test/account-delete", {
  method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

Deno.test("Deletion rejects missing token and extra identity before accessing Auth", async () => {
  assert((await handleDeleteAccount(new Request("https://edge.test", { method: "POST" }))).status === 401);
  assert((await handleDeleteAccount(post({ confirmation: "DELETE", user_id: "victim" }))).status === 400);
  assert((await handleDeleteAccount(post({ confirmation: "delete" }))).status === 400);
});

async function withBackend(sessionActive: boolean, execute: (calls: string[]) => Promise<void>, storageFailure = false) {
  const previousFetch = globalThis.fetch;
  const env = ["SUPABASE_URL", "SUPABASE_ANON_KEY", "SUPABASE_SERVICE_ROLE_KEY"];
  const previous = env.map(key => Deno.env.get(key));
  Deno.env.set(env[0], "https://backend.test");
  Deno.env.set(env[1], "public-test-key");
  Deno.env.set(env[2], "server-test-key");
  const calls: string[] = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    calls.push(url);
    if (url.endsWith("/auth/v1/user")) return response({ id: "11111111-1111-4111-8111-111111111111", aud: "authenticated", email: "test@example.com" });
    if (url.endsWith("/rest/v1/rpc/current_zerochill_session")) return response(sessionActive);
    if (url.includes("/storage/v1/object/list/avatars")) {
      assert(String(init?.body).includes("11111111-1111-4111-8111-111111111111"), "Must list only the verified owner's avatar folder");
      return storageFailure ? response({ message: "Storage unavailable" }, 500) : response([]);
    }
    if (url.endsWith("/auth/v1/admin/users/11111111-1111-4111-8111-111111111111")) {
      assert(init?.method === "DELETE");
      assert(new Headers(init?.headers).get("Authorization") === "Bearer server-test-key");
      return response({ id: "11111111-1111-4111-8111-111111111111" });
    }
    throw new Error(`Unexpected request: ${url}`);
  };
  try { await execute(calls); }
  finally {
    globalThis.fetch = previousFetch;
    env.forEach((key, i) => previous[i] === undefined ? Deno.env.delete(key) : Deno.env.set(key, previous[i]!));
  }
}

Deno.test("Revoked session cannot delete an account or storage", async () => {
  await withBackend(false, async calls => {
    assert((await handleDeleteAccount(post({ confirmation: "DELETE" }))).status === 401);
    assert(calls.length === 2);
  });
});

Deno.test("Deletion derives owner from verified Auth and uses server-only admin client", async () => {
  await withBackend(true, async calls => {
    const result = await handleDeleteAccount(post({ confirmation: "DELETE" }));
    assert(result.status === 200);
    assert((await result.json()).deleted === true);
    assert(calls.some(url => url.endsWith("/auth/v1/admin/users/11111111-1111-4111-8111-111111111111")));
  });
});

Deno.test("Storage failure preserves the Auth user for retry", async () => {
  await withBackend(true, async calls => {
    assert((await handleDeleteAccount(post({ confirmation: "DELETE" }))).status === 500);
    assert(!calls.some(url => url.includes("/admin/users/")));
  }, true);
});
