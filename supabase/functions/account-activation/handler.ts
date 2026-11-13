// No third-party runtime dependencies. Authentication is checked by Auth /user,
// never by trusting an unverified JWT payload or user-editable metadata.
export const TEST_URL = 'https://xswtgidwhesajavxjbjb.supabase.co';
const json = (status: number, value: object) => new Response(JSON.stringify(value), {
  status, headers: {'Content-Type':'application/json', 'Cache-Control':'no-store'},
});
type Config = {url: string; anon: string; service: string};
export async function handleActivation(req: Request, config: Config, send: typeof fetch = fetch): Promise<Response> {
  if (config.url !== TEST_URL) return json(503, {error:'Activation rehearsal is unavailable.'});
  if (req.method !== 'POST') return json(405, {error:'Use POST.'});
  const authorization = req.headers.get('authorization') ?? '';
  if (!/^Bearer \S+$/i.test(authorization)) return json(401, {error:'Verify your email again.'});
  if (Number(req.headers.get('content-length') ?? 0) > 4096) return json(413, {error:'Request too large.'});
  let password: string;
  try {
    const reader = req.body?.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    if (reader) {
      while (true) {
        const {done,value} = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 4096) { await reader.cancel(); return json(413,{error:'Request too large.'}); }
        chunks.push(value);
      }
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk,offset); offset += chunk.byteLength; }
    const raw = new TextDecoder().decode(bytes);
    const body = JSON.parse(raw);
    if (Object.keys(body).some(k => k !== 'password')) return json(400,{error:'Invalid activation request.'});
    password = body.password;
    if (typeof password !== 'string' || password.length < 8 || password.length > 128)
      return json(400, {error:'Use a password of 8 to 128 characters.'});
  } catch { return json(400, {error:'Invalid activation request.'}); }
  const call = (path: string, key: string, auth: string, body?: object, method?: string) => send(config.url+path, {
    method: method ?? (body ? 'POST' : 'GET'),
    headers: {apikey:key, Authorization:auth, 'Content-Type':'application/json'},
    ...(body ? {body:JSON.stringify(body)} : {}), signal:AbortSignal.timeout(15000),
  });
  let ticket = '', id = '';
  const admin = 'Bearer '+config.service;
  try {
    const verified = await call('/auth/v1/user',config.anon,authorization);
    if (!verified.ok) return json(401,{error:'Verify your email again.'});
    const user = await verified.json();
    // /user validated the exact bearer first, so its AMR can now be inspected.
    const payload = JSON.parse(atob(authorization.split(' ')[1].split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));
    const recentOtp = Array.isArray(payload.amr) && payload.amr.some((a: {method:string;timestamp:number}) =>
      a.method === 'otp' && a.timestamp <= Date.now()/1000+30 && a.timestamp > Date.now()/1000-3600);
    if (payload.sub !== user.id || !user.email_confirmed_at || !recentOtp || !payload.session_id)
      return json(403,{error:'Verify your registered email before creating a password.'});
    id = user.id;
    ticket = crypto.randomUUID();
    const reserved = await call('/rest/v1/rpc/begin_legacy_claim', config.service,admin,{target_id:id,ticket,verified_session_id:payload.session_id});
    if (!reserved.ok || await reserved.json() !== true) return json(409,{error:'Activation is complete or in progress. Try signing in or retry later.'});
    const updated = await call('/auth/v1/user',config.anon,authorization,{password},'PUT');
    if (!updated.ok) {
      await call('/rest/v1/rpc/finish_legacy_claim',config.service,admin,{target_id:id,ticket,succeeded:false});
      return json(400,{error:'Password was not accepted. Try another password or verify your email again.'});
    }
    const finished = await call('/rest/v1/rpc/finish_legacy_claim',config.service,admin,{target_id:id,ticket,succeeded:true});
    if (!finished.ok || await finished.json() !== true) throw new Error('Incomplete claim');
    return json(200,{activated:true});
  } catch {
    // Leave ambiguous writes locked for five minutes; retry is safe after expiry.
    return json(503,{error:'Activation could not finish. Wait five minutes and verify your email again.'});
  }
}
