import {createClient} from 'npm:@supabase/supabase-js@2.58.0';
import {readMedia,validateMedia} from './validation.ts';
const json=(status:number,value:object)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
export async function handleUpload(request:Request):Promise<Response> {
 if(request.method!=='POST') return json(405,{error:'Use POST.'});
 const authorization=request.headers.get('authorization')??'';
 const token=/^Bearer (\S+)$/i.exec(authorization)?.[1];
 const id=new URL(request.url).searchParams.get('id')??'';
 if(!token) return json(401,{error:'Sign in to attach media.'});
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) return json(400,{error:'Invalid attachment.'});
 const url=Deno.env.get('SUPABASE_URL'), key=Deno.env.get('SUPABASE_ANON_KEY'),service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 if(!url||!key||!service) return json(503,{error:'Upload unavailable.'});
 const user=createClient(url,key,{global:{headers:{Authorization:authorization}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:auth,error:authError}=await user.auth.getUser(token);
 if(authError||!auth.user) return json(401,{error:'Sign in again to attach media.'});
 let bytes:Uint8Array,bucket:string;
 const mime=request.headers.get('content-type')?.split(';')[0].trim()??'';
 try {bytes=await readMedia(request);bucket=validateMedia(bytes,mime);}
 catch(error) {return json(400,{error:error instanceof Error?error.message:'Invalid image.'});}
 const {error:permitError}=await user.rpc('reserve_zerochill_media_upload',{media_id:id,media_bucket:bucket});
 if(permitError) return json(403,{error:'Upload unavailable. Check your account or try again later.'});
 const path=auth.user.id+'/'+id;
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const {error}=await admin.storage.from(bucket).upload(path,bytes,{contentType:mime,upsert:false});
 // An identical UUID retry must never overwrite an immutable object.
 if(error && String((error as {statusCode?:string}).statusCode)!=='409') return json(503,{error:'Upload failed. Your draft is saved; try again.'});
 return json(200,{uploaded:true});
}
