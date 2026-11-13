import {test} from 'node:test';
import assert from 'node:assert/strict';
import {handleActivation,TEST_URL} from '../functions/account-activation/handler.ts';
const config={url:TEST_URL,anon:'anon',service:'backend-only'};
const token=(amr=[{method:'otp',timestamp:Date.now()/1000}])=>['x',Buffer.from(JSON.stringify({sub:'verified-id',session_id:'session',amr})).toString('base64url'),'x'].join('.');
const req=(body={password:'strong-password'},jwt=token())=>new Request('https://test.invalid',{method:'POST',headers:{authorization:'Bearer '+jwt},body:JSON.stringify(body)});
function backend(options={}) {
 const calls=[];
 const send=async (url,init)=>{
  const path=new URL(url).pathname;calls.push({path,init});
  if(path==='/auth/v1/user'&&init.method==='GET') return Response.json({id:'verified-id',email_confirmed_at:'now'},{status:options.authStatus??200});
  if(path.endsWith('begin_legacy_claim')) return Response.json(options.reserved??true);
  if(path==='/auth/v1/user'&&init.method==='PUT') return Response.json({},{status:options.passwordStatus??200});
  if(path.endsWith('finish_legacy_claim')) return Response.json(options.finished??true);
  throw Error('unexpected call');
 };return {calls,send};
}
test('production projects are rejected before any network call',async()=>{
 const b=backend();assert.equal((await handleActivation(req(),{...config,url:'https://aavjpykssueavbseqdlf.supabase.co'},b.send)).status,503);assert.equal(b.calls.length,0);
});
test('auth validation precedes JWT use and denies password-only sessions',async()=>{
 for(const [opts,jwt,status] of [[{authStatus:401},token(),401],[{},token([{method:'password',timestamp:Date.now()/1000}]),403],[{},token([{method:'otp',timestamp:1}]),403]]){
  const b=backend(opts);assert.equal((await handleActivation(req(undefined,jwt),config,b.send)).status,status);assert.equal(b.calls.length,1);
 }
});
test('client cannot choose target identity or send weak password',async()=>{
 for(const body of [{password:'short'},{password:'strong-password',user_id:'victim'}]){
  const b=backend();assert.equal((await handleActivation(req(body),config,b.send)).status,400);assert.equal(b.calls.length,0);
 }
});
test('reservation serializes claims; denied reservation never changes password',async()=>{
 const b=backend({reserved:false});assert.equal((await handleActivation(req(),config,b.send)).status,409);assert.equal(b.calls.some(c=>c.init.method==='PUT'),false);
});
test('only successful Auth password update marks mapping claimed',async()=>{
 for(const passwordStatus of [200,422]){
  const jwt=token(), b=backend({passwordStatus});assert.equal((await handleActivation(req(undefined,jwt),config,b.send)).status,passwordStatus===200?200:400);
  const finish=b.calls.find(c=>c.path.endsWith('finish_legacy_claim'));
  assert.equal(JSON.parse(finish.init.body).succeeded,passwordStatus===200);
  assert.equal(JSON.parse(finish.init.body).target_id,'verified-id');
  assert.equal(b.calls.find(c=>c.init.method==='PUT').init.headers.Authorization,'Bearer '+jwt);
 }
});
test('ambiguous ledger failure returns retryable failure without reporting activated',async()=>{
 const b=backend({finished:false});assert.equal((await handleActivation(req(),config,b.send)).status,503);
});
test('oversized streamed body is rejected before Auth call',async()=>{
 const b=backend();
 const body=new ReadableStream({start(c){c.enqueue(new TextEncoder().encode('x'.repeat(4097)));c.close();}});
 const request=new Request('https://test.invalid',{method:'POST',headers:{authorization:'Bearer '+token()},body,duplex:'half'});
 assert.equal((await handleActivation(request,config,b.send)).status,413);assert.equal(b.calls.length,0);
});
test('only verified session ID is passed to backend reservation',async()=>{
 const b=backend();assert.equal((await handleActivation(req(),config,b.send)).status,200);
 const reserved=b.calls.find(c=>c.path.endsWith('begin_legacy_claim'));
 assert.equal(JSON.parse(reserved.init.body).verified_session_id,'session');
});
