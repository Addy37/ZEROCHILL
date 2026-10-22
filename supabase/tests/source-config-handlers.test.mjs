import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const defaults = JSON.parse(readFileSync(new URL("../../app/src/main/assets/source_config_defaults.json", import.meta.url)));
let handler;
let db;
const env = new Map([["SUPABASE_URL", "https://test.invalid"], ["SUPABASE_SERVICE_ROLE_KEY", "test-only"]]);
globalThis.Deno = { env: { get: key => env.get(key) }, serve: value => { handler = value; } };
globalThis.sourceConfigTestClient = () => db;
async function load(name) {
  const url = new URL(`../functions/${name}/index.ts`, import.meta.url);
  let source = readFileSync(url, "utf8")
    .replace('import { createClient } from "https://esm.sh/@supabase/supabase-js@2";', "const createClient = globalThis.sourceConfigTestClient;")
    .replaceAll('"../_shared/source-config-validation.ts"', JSON.stringify(new URL("../functions/_shared/source-config-validation.ts", import.meta.url).href))
    .replaceAll('"../_shared/source-config-rollback.ts"', JSON.stringify(new URL("../functions/_shared/source-config-rollback.ts", import.meta.url).href));
  await import(`data:text/javascript;base64,${Buffer.from(stripTypeScriptTypes(source)).toString("base64")}`);
  return handler;
}
function table(result) {
  const query = { select: () => query, eq: () => query, maybeSingle: async () => ({ data: result }) };
  return query;
}
const publicHandler = await load("app-config");
const adminHandler = await load("app-config-admin");

test("public config serves validated full config and conditional refresh", async () => {
  db = { from: () => table({ config_version: defaults.configVersion, config: defaults }) };
  const response = await publicHandler(new Request("https://test.invalid/app-config"));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), defaults);
  assert.equal(response.headers.get("etag"), `"${defaults.configVersion}"`);
  const unchanged = await publicHandler(new Request("https://test.invalid/app-config", { headers: { "if-none-match": response.headers.get("etag") } }));
  assert.equal(unchanged.status, 304);
  assert.equal(await unchanged.text(), "");
  for (const tag of [`W/${response.headers.get("etag")}`, `"0", W/${response.headers.get("etag")}`, "*"]) {
    assert.equal((await publicHandler(new Request("https://test.invalid/app-config", {
      headers: { "if-none-match": tag },
    }))).status, 304);
  }
});

test("incomplete or mismatched active config never reaches a client", async () => {
  const incomplete = structuredClone(defaults);
  delete incomplete.sources.onlyhaven;
  for (const row of [{ config_version: defaults.configVersion, config: incomplete },
    { config_version: defaults.configVersion + 1, config: defaults }]) {
    db = { from: () => table(row) };
    const response = await publicHandler(new Request(`https://test.invalid/app-config?currentVersion=${defaults.configVersion + 100}`));
    assert.equal(response.status, 503);
  }
});

test("methods and admin authentication remain enforced", async () => {
  assert.equal((await publicHandler(new Request("https://test.invalid", { method: "POST" }))).status, 405);
  assert.equal((await adminHandler(new Request("https://test.invalid"))).status, 405);
  assert.equal((await adminHandler(new Request("https://test.invalid", { method: "POST", body: "{}" }))).status, 401);
});

async function adminRequest(body) {
  const token = "test-only-admin-token";
  env.set("FEEDBACK_ADMIN_TOKEN_HASH", Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token))).toString("hex"));
  return adminHandler(new Request("https://test.invalid", { method: "POST", headers: { "x-admin-token": token }, body: JSON.stringify(body) }));
}

test("authenticated admin validates without publishing and rejects invalid config", async () => {
  db = { rpc: () => { throw Error("validate must not publish"); } };
  assert.equal((await adminRequest({ action: "validate", config: defaults })).status, 200);
  assert.equal((await adminRequest({ action: "validate", config: {} })).status, 400);
});

test("publishing rejects a stale version", async () => {
  db = { from: () => table({ config_version: defaults.configVersion }), rpc: () => { throw Error("stale version must not publish"); } };
  assert.equal((await adminRequest({ action: "publish", config: defaults })).status, 409);
});

test("admin rollback publishes a new complete document from an older partial snapshot", async () => {
  const old = structuredClone(defaults);
  delete old.sources.onlyhaven;
  let reads = 0;
  db = { from: () => table(reads++ === 0 ? { config: old } : { config_version: defaults.configVersion, config: defaults }),
    rpc: async (name, args) => {
      assert.equal(name, "publish_source_config");
      assert.equal(args.p_action, "rollback");
      assert.equal(args.p_based_on_version, 1);
      assert.equal(args.p_config.configVersion, defaults.configVersion + 1);
      assert.deepEqual(args.p_config.sources.onlyhaven, defaults.sources.onlyhaven);
      return { data: args.p_config.configVersion };
    } };
  const response = await adminRequest({ action: "rollback", configVersion: 1 });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).rolledBack, true);
});
