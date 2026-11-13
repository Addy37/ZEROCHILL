// Run with NODE_USE_ENV_PROXY=1. Creates only synthetic users, retained for inspection.
// No source connection, password hashes, real email delivery, or production path.
import assert from 'node:assert/strict';
import {randomUUID,randomBytes} from 'node:crypto';
import {testProject,verifiedMapping,remapRows} from './mapping.mjs';
const {url,anon,service}=testProject();
const ids=[], run=randomBytes(5).toString('hex');
const admin='Bearer '+service;
async function call(path,body,auth=admin,key=service,method=body?'POST':'GET') {
 const r=await fetch(url+path,{method,headers:{apikey:key,Authorization:auth,'Content-Type':'application/json',Prefer:'return=representation'},...(body?{body:JSON.stringify(body)}:{})});
 const raw=await r.text();let data;try{data=JSON.parse(raw)}catch{data=null}
 return {status:r.status,ok:r.ok,data};
}
const rpc=(name,body,token,key=anon)=>call('/rest/v1/rpc/'+name,body,'Bearer '+token,key);
const userCall=(path,body,token,method)=>call(path,body,'Bearer '+token,anon,method);
const password='Fresh-'+randomBytes(16).toString('hex');
async function provision(suffix,mapped=true) {
 const email=`zc_activation_${run}_${suffix}@example.invalid`, username=`zc_${run}_${suffix}`;
 // Supported admin create with no password and no invitation/send request.
 const r=await call('/auth/v1/admin/users',{email,email_confirm:false,user_metadata:{username,adult_confirmed:true,terms_accepted:true}});
 assert.equal(r.ok,true,'synthetic no-password provisioning');
 ids.push(r.data.id);
 const oldId=randomUUID(), newId=r.data.id;
 if(mapped) assert.equal((await call('/rest/v1/rpc/register_legacy_account',{old_id:oldId,new_id:newId})).ok,true);
 return {email,username,oldId,newId};
}
async function tokenFor(user) {
 // Admin generate_link returns a token privately WITHOUT sending email. This tests
 // real Auth verification but is NOT evidence of inbox ownership/deliverability.
 const link=await call('/auth/v1/admin/generate_link',{type:'magiclink',email:user.email});
 assert.equal(link.ok,true,'generate synthetic proof');
 const hash=link.data.hashed_token ?? link.data.properties?.hashed_token;
 assert.ok(hash,'token hash returned');
 const verified=await call('/auth/v1/verify',{type:'email',token_hash:hash},'Bearer '+anon,anon);
 assert.equal(verified.ok,true,'real Auth token verification');
 assert.ok(verified.data.user.id===user.newId,'verified UUID matches ledger');
 return {token:verified.data.access_token,refresh:verified.data.refresh_token,hash};
}
try {
 const a=await provision('a'),b=await provision('b'),outsider=await provision('c',false);
 const m=verifiedMapping([a,b].map(x=>({oldId:x.oldId,newId:x.newId})));
 const favorites=remapRows('creator_favorites',[1,2,3].map(i=>({user_id:a.oldId,creator_key:`fixture-${run}-${i}`,creator_name:`Synthetic ${i}`})),m);
 const comment=remapRows('comments',[{user_id:a.oldId,content_key:'fixture-content-key-'+run,canonical_url:'https://example.invalid/fixture',body:'Synthetic comment'}],m);
 const dm=remapRows('direct_messages',[{sender_id:a.oldId,recipient_id:b.oldId,body:'Synthetic private message'}],m);
 for(const [table,rows] of [['creator_favorites',favorites],['comments',comment],['direct_messages',dm]])
  { const result=await call('/rest/v1/'+table,rows); assert.equal(result.ok,true,`mapped fixture import ${table}: HTTP ${result.status} code ${result.data?.code}`); }
 assert.equal((await call('/rest/v1/rpc/register_legacy_account',{old_id:a.oldId,new_id:b.newId})).ok,false,'mapping immutable uniqueness');
 assert.equal((await call('/auth/v1/token?grant_type=password',{email:a.email,password},'Bearer '+anon,anon)).ok,false,'unprovisioned password cannot login');
 assert.equal((await call('/rest/v1/rpc/begin_legacy_claim',{target_id:a.newId,ticket:randomUUID(),verified_session_id:randomUUID()})).data,false,'inactive session cannot reserve claim');
 const before=(await call('/auth/v1/admin/users?page=1&per_page=1000')).data.users.length;
 const unknown=await call('/auth/v1/otp',{email:`zc_unknown_${run}@example.invalid`,create_user:false},'Bearer '+anon,anon);
 assert.ok([200,400,422].includes(unknown.status),'unknown email safe response; throttled runs are inconclusive');
 if (unknown.status===422) assert.equal(unknown.data.error_code??unknown.data.code,'otp_disabled','unknown account must be refused without signup');
 const av=await tokenFor(a),bv=await tokenFor(b),cv=await tokenFor(outsider);
 assert.equal((await rpc('legacy_activation_status',{},av.token)).data,true);
 assert.equal((await call('/rest/v1/rpc/legacy_activation_status',{},'Bearer '+anon,anon)).ok,false,'anonymous state lookup denied');
 assert.equal((await userCall('/functions/v1/account-activation',{password},'invalid-token')).ok,false,'forged bearer rejected');
 assert.equal((await rpc('legacy_activation_status',{},cv.token)).data,false,'normal user unchanged');
 assert.equal((await userCall('/rest/v1/creator_favorites?select=creator_key',null,av.token)).data.length,0,'pending cannot read favorites');
 assert.equal((await userCall('/rest/v1/direct_messages?select=id',null,av.token)).data.length,0,'pending cannot read DMs');
 assert.equal((await userCall('/rest/v1/rpc/finish_legacy_claim',{target_id:a.newId,ticket:randomUUID(),succeeded:true},av.token)).ok,false,'client cannot claim ledger');
 assert.equal((await userCall('/functions/v1/account-activation',{password,user_id:a.newId},bv.token)).status,400,'arbitrary identity parameter rejected');
 assert.equal((await userCall('/functions/v1/account-activation',{password},cv.token)).status,409,'unmapped user cannot claim');
 assert.equal((await userCall('/functions/v1/account-activation',{password:'short'},av.token)).status,400,'weak password rejected');
 const concurrent=await Promise.all([1,2].map(()=>userCall('/functions/v1/account-activation',{password},av.token)));
 assert.deepEqual(concurrent.map(r=>r.status).sort(),[200,409],'exactly one concurrent claim completes');
 assert.equal((await rpc('legacy_activation_status',{},av.token)).data,false);
 assert.equal((await userCall('/functions/v1/account-activation',{password:password+'x'},av.token)).status,409,'repeat claim rejected');
 assert.equal((await userCall('/rest/v1/creator_favorites?select=creator_key',null,av.token)).data.length,3,'all mapped favorites restored');
 assert.equal((await userCall('/rest/v1/direct_messages?select=id',null,av.token)).data.length,1,'mapped participant restored');
 assert.equal((await userCall('/rest/v1/direct_messages?select=id',null,cv.token)).data.length,0,'outsider cannot read DMs');
 assert.equal((await userCall('/rest/v1/profiles?select=username,bio&user_id=eq.'+a.newId,null,av.token)).data[0].username,a.username,'original profile restored');
 assert.equal((await call('/auth/v1/token?grant_type=password',{email:a.email,password:password+'wrong'},'Bearer '+anon,anon)).ok,false,'wrong password rejected');
 const login=await call('/auth/v1/token?grant_type=password',{email:a.email,password},'Bearer '+anon,anon);
 assert.equal(login.ok,true,'new password login');
 assert.ok(login.data.user.id===a.newId,'password login retains mapped UUID');
 assert.equal((await userCall('/functions/v1/account-activation',{password},login.data.access_token)).status,403,'password session is not mailbox proof');
 const refresh=await call('/auth/v1/token?grant_type=refresh_token',{refresh_token:login.data.refresh_token},'Bearer '+anon,anon);
 assert.equal(refresh.ok,true,'refresh works');
 assert.equal((await userCall('/auth/v1/logout',{},refresh.data.access_token)).ok,true,'signout works');
 const replay=await call('/auth/v1/verify',{type:'email',token_hash:av.hash},'Bearer '+anon,anon);
 assert.equal(replay.ok,false,'consumed token rejected');
 const invalid=await call('/auth/v1/verify',{type:'email',token_hash:'0'.repeat(64)},'Bearer '+anon,anon);
 assert.equal(invalid.ok,false,'invalid/expired token rejected');
 assert.equal((await userCall('/auth/v1/logout',{},bv.token)).ok,true);
 assert.equal((await userCall('/functions/v1/account-activation',{password},bv.token)).ok,false,'signed-out OTP session cannot claim');
 const after=(await call('/auth/v1/admin/users?page=1&per_page=1000')).data.users.length;
 assert.equal(after,before,'verification and unknown email create no duplicate user');
 console.log('PASS: synthetic provisioning, mapping, real Auth token verification, password completion, private-history RLS, negative cases, login/refresh/signout. Email delivery not tested.');
} finally {
 // Claimed mapping intentionally restricts Auth deletion; test fixtures retained
 // for private inspection until explicit SQL cleanup removes mappings first.
 console.log(`Synthetic rehearsal finished; ${ids.length} fake accounts retained in Migration Test only.`);
}
