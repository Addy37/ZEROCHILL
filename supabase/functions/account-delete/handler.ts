import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.58.0";

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });

async function removeAvatarFolder(
  admin: SupabaseClient,
  path: string,
  depth = 0,
): Promise<void> {
  if (depth > 8) throw new Error("Avatar folder depth exceeded");
  // Always start at offset zero. Removing a page shifts the next page down.
  for (let batch = 0; batch < 1000; batch++) {
    const { data, error } = await admin.storage.from("avatars").list(path, {
      limit: 100,
      offset: 0,
    });
    if (error) throw error;
    if (!data?.length) return;

    const files: string[] = [];
    for (const object of data) {
      const child = `${path}/${object.name}`;
      if (object.id === null) await removeAvatarFolder(admin, child, depth + 1);
      else files.push(child);
    }
    if (files.length) {
      const { error: removeError } = await admin.storage.from("avatars").remove(files);
      if (removeError) throw removeError;
    }
  }
  throw new Error("Avatar cleanup limit exceeded");
}

export async function handleDeleteAccount(request: Request): Promise<Response> {
  if (request.method !== "POST") return json(405, { error: "Use POST." });

  const match = /^Bearer\s+([^\s]+)$/i.exec(request.headers.get("Authorization") ?? "");
  if (!match) return json(401, { error: "Sign in again to delete your account." });

  let payload: unknown;
  try {
    payload = await request.json();
  } catch {
    return json(400, { error: "Confirm account deletion before continuing." });
  }
  if (typeof payload !== "object" || payload === null || Array.isArray(payload) ||
      Object.keys(payload).length !== 1 ||
      (payload as Record<string, unknown>).confirmation !== "DELETE") {
    return json(400, { error: "Confirm account deletion before continuing." });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    return json(503, { error: "Account deletion is temporarily unavailable." });
  }

  let userId: string;
  try {
    const userClient = createClient(url, anonKey, {
      global: { headers: { Authorization: `Bearer ${match[1]}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userResult, error: userError } = await userClient.auth.getUser(match[1]);
    if (userError || !userResult.user) {
      return json(401, { error: "Your session expired. Sign in and try again." });
    }
    const { data: sessionActive, error: sessionError } =
      await userClient.rpc("current_zerochill_session");
    if (sessionError || sessionActive !== true) {
      return json(401, { error: "Your session expired. Sign in and try again." });
    }
    userId = userResult.user.id;
  } catch {
    return json(503, { error: "Unable to check your session. Please try again." });
  }

  // This client never inherits the caller's Authorization header. Its key stays
  // in the Edge Function runtime and never enters Android or the response.
  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  try {
    await removeAvatarFolder(admin, userId);
    const { error } = await admin.auth.admin.deleteUser(userId, false);
    if (error) throw error;
    return json(200, { deleted: true });
  } catch (error) {
    console.error("Account deletion failed", error instanceof Error ? error.message : "Unknown error");
    return json(500, { error: "Unable to delete your account. Please try again." });
  }
}
