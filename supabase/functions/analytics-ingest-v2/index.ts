import { createClient } from 'npm:@supabase/supabase-js@2.58.0';
import { createHandler } from './handler.ts';
Deno.serve(async request => {
 const salt=Deno.env.get('ANALYTICS_V2_RATE_SALT');
 if(!salt) return new Response(null,{status:503});
 // Abuse-only, hourly rotating source token. Never returned, logged or joined to metrics.
 const source=request.headers.get('cf-connecting-ip') ?? request.headers.get('x-forwarded-for')?.split(',').pop()?.trim() ?? 'unknown';
 const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(salt+'\n'+new Date().toISOString().slice(0,13)+'\n'+source));
 const sourceHash=Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
 return createHandler(async summaries => {
 const url=Deno.env.get('SUPABASE_URL'), key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 if(!url||!key) throw Error('Unavailable');
 const db=createClient(url,key,{auth:{persistSession:false}});
 const {data:allowed,error:rateError}=await db.rpc('allow_analytics_v2_request',{source_hash:sourceHash});
 if(rateError) throw rateError;
 if(!allowed) throw Error('rate_limited');
 const {error}=await db.rpc('record_analytics_v2_batch',{summaries});
 if(error) throw error;
 })(request);
});
