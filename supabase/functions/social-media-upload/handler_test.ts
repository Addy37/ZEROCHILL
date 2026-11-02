import {handleUpload} from './handler.ts';
const owner='b20aa450-3550-4a10-9a77-000000000001', id='c20aa450-3550-4a10-9a77-000000000001';
const assert=(ok:unknown,message='Assertion failed')=>{if(!ok)throw Error(message);};
const json=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const request=(bytes:Uint8Array,mime:string)=>new Request('https://edge.test/social-media-upload?id='+id,{method:'POST',headers:{Authorization:'Bearer caller-test-token','Content-Type':mime},body:new Uint8Array(bytes).buffer});
async function backend(test:(calls:string[],uploads:Uint8Array[])=>Promise<void>, denied=false,failUpload=false) {
 const previousFetch=globalThis.fetch;
 const names=['SUPABASE_URL','SUPABASE_ANON_KEY','SUPABASE_SERVICE_ROLE_KEY'];
 const previous=names.map(name=>Deno.env.get(name));
 ['https://backend.test','public-test-key','server-test-key'].forEach((value,i)=>Deno.env.set(names[i],value));
 const calls:string[]=[],uploads:Uint8Array[]=[];
 globalThis.fetch=async(input,init)=>{
  const url=String(input);calls.push(url);
  const headers=new Headers(init?.headers);
  if(url.endsWith('/auth/v1/user'))return json({id:owner,aud:'authenticated',role:'authenticated'});
  if(url.endsWith('/rest/v1/rpc/reserve_zerochill_media_upload')) {
   assert(headers.get('Authorization')==='Bearer caller-test-token','Reservation must use the caller session');
   const body=JSON.parse(String(init?.body));assert(body.media_id===id&&!body.owner_id,'Caller cannot select owner');
   return denied?json({message:'denied'},403):json(null);
  }
  if(url.startsWith('https://backend.test/storage/v1/object/')){
   assert(url.endsWith('/'+owner+'/'+id),'Storage path must derive from verified Auth');
   assert(headers.get('Authorization')==='Bearer server-test-key','Storage credential stays backend-only');
   assert(headers.get('x-upsert')==='false','Object must be immutable');
   uploads.push(new Uint8Array(await new Response(init?.body).arrayBuffer()));
   return failUpload?json({message:'unavailable'},503):json({Key:url.split('/object/')[1]});
  }
  throw Error('Unexpected backend request');
 };
 try {await test(calls,uploads);} finally {globalThis.fetch=previousFetch;names.forEach((name,i)=>previous[i]===undefined?Deno.env.delete(name):Deno.env.set(name,previous[i]!));}
}
Deno.test('unauthenticated request is rejected before backend access',async()=>{
 assert((await handleUpload(new Request('https://edge.test/?id='+id,{method:'POST'}))).status===401);
});
Deno.test('JPEG PNG WebP and animated GIF upload unchanged bytes into correct private bucket',async()=>{
 for(const [file,mime,bucket] of [['orientation-6.jpg','image/jpeg','social-photos'],['static.png','image/png','social-photos'],['static.webp','image/webp','social-photos'],['animated.gif','image/gif','social-gifs']]) {
  const bytes=await Deno.readFile(new URL('../../../app/src/test/resources/social-media/'+file,import.meta.url));
  await backend(async(calls,uploads)=>{
   const response=await handleUpload(request(bytes,mime));assert(response.status===200);
   assert(calls.some(url=>url.includes('/object/'+bucket+'/')));
   assert(uploads.length===1&&uploads[0].every((v,i)=>v===bytes[i])&&uploads[0].length===bytes.length,'Animation/image bytes must remain unchanged');
   const output=await response.text();assert(!output.includes(owner)&&!output.includes('server-test-key'),'Response must not expose path or private credential');
  });
 }
});
Deno.test('MIME spoofing is rejected before reservation or Storage',async()=>{
 await backend(async(calls,uploads)=>{
  assert((await handleUpload(request(new TextEncoder().encode('<svg/>'),'image/jpeg'))).status===400);
  assert(calls.length===1&&uploads.length===0);
 });
});
Deno.test('revoked banned or rate-limited reservation never uploads',async()=>{
 const bytes=await Deno.readFile(new URL('../../../app/src/test/resources/social-media/animated.gif',import.meta.url));
 await backend(async(_calls,uploads)=>{assert((await handleUpload(request(bytes,'image/gif'))).status===403);assert(!uploads.length);},true);
});
Deno.test('Storage failure returns retryable failure without creating a social row',async()=>{
 const bytes=await Deno.readFile(new URL('../../../app/src/test/resources/social-media/animated.gif',import.meta.url));
 await backend(async(calls,_uploads)=>{
  assert((await handleUpload(request(bytes,'image/gif'))).status===503);
  assert(!calls.some(url=>url.includes('/rest/v1/comments')||url.includes('/rest/v1/direct_messages')));
 },false,true);
});
