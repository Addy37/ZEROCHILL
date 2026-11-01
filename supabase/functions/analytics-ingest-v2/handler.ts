import { validateSummary, type Summary } from "./validation.ts";

export const MAX_BODY_BYTES = 160 * 1024;

type Recorder = (summaries: Summary[]) => Promise<void>;
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
    let summaries: Summary[];
    try {
      if(body && typeof body === "object" && Object.hasOwn(body,"summaries")) {
        if(Object.keys(body).length!==1 || !Array.isArray(body.summaries) || body.summaries.length<1 || body.summaries.length>5) throw Error();
        summaries=body.summaries.map((summary: unknown)=>validateSummary(summary));
      } else summaries=[validateSummary(body)];
    } catch { return json("Invalid analytics summary.",400); }
    try {
      await record(summaries);
      return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
    } catch (error) {
      if (error instanceof Error && error.message === "rate_limited") return json("Rate limited.",429);
      // Do not log anonymous keys or payloads.
      console.error("Analytics database operation failed.");
      return json("Unable to record analytics.", 503);
    }
  };
}
