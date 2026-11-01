import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { createHandler } from "./handler.ts";

// Anonymous endpoint by design. Only rotating uniqueness keys and aggregate metrics are accepted.
Deno.serve(createHandler(async (events, batch) => {
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) throw new Error("Service unavailable");
  const db = createClient(url, serviceKey, { auth: { persistSession: false } });
  const event = events[0];
  const { error } = batch
    ? await db.rpc("record_analytics_batch", { p_events: events })
    : await db.rpc("record_analytics_metric", {
      p_metric: event.metric, p_value: event.value, p_day_key: event.day_key,
      p_week_key: event.week_key, p_month_key: event.month_key,
    });
  if (error) throw error;
}));
