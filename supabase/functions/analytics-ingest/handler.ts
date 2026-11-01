import { validateEvent, type AnalyticsEvent } from "./events.ts";

export const MAX_BATCH_SIZE = 50;
export const MAX_BODY_BYTES = 32 * 1024;

type Recorder = (events: AnalyticsEvent[], batch: boolean) => Promise<void>;
function json(error: string, status: number) {
  return new Response(JSON.stringify({ error }), { status,
    headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

// Count actual streamed bytes: Content-Length alone does not bound a chunked request.
async function readBody(request: Request): Promise<string> {
  if (Number(request.headers.get("content-length")) > MAX_BODY_BYTES) throw new RangeError();
  const reader = request.body?.getReader();
  if (!reader) return "";
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) { await reader.cancel(); throw new RangeError(); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

export function createHandler(record: Recorder) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return json("Method not allowed.", 405);
    let body;
    try { body = JSON.parse(await readBody(request)); }
    catch (error) { return json("Invalid or oversized request.", error instanceof RangeError ? 413 : 400); }
    let events: AnalyticsEvent[];
    const batch = body != null && Object.hasOwn(body, "events");
    try {
      if (batch) {
        if (!Array.isArray(body.events) || body.events.length < 1 || body.events.length > MAX_BATCH_SIZE
            || Object.hasOwn(body, "metric")) throw new Error("Invalid batch.");
        events = body.events.map(validateEvent);
      } else { events = [validateEvent(body)]; }
    } catch { return json("Invalid analytics event or batch.", 400); }
    try {
      await record(events, batch);
      return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
    } catch {
      // Do not log anonymous keys or payloads.
      console.error("Analytics database operation failed.");
      return json("Unable to record analytics.", 503);
    }
  };
}
