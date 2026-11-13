import {test} from 'node:test';
import assert from 'node:assert/strict';
import {verifiedMapping,remapRows,testProject} from '../migration-tools/mapping.mjs';
const a='00000000-0000-4000-8000-000000000001', b='00000000-0000-4000-8000-000000000002';
const c='00000000-0000-4000-8000-000000000003', d='00000000-0000-4000-8000-000000000004';
test('one-to-one mapping rejects collisions and identity reuse',()=>{
 assert.throws(()=>verifiedMapping([{oldId:a,newId:a}]));
 assert.throws(()=>verifiedMapping([{oldId:a,newId:c},{oldId:b,newId:c}]));
 assert.throws(()=>verifiedMapping([{oldId:a,newId:c},{oldId:a,newId:d}]));
});
test('DM participants and comment owner remap without changing reply IDs or content',()=>{
 const m=verifiedMapping([{oldId:a,newId:c},{oldId:b,newId:d}]);
 assert.deepEqual(remapRows('direct_messages',[{sender_id:a,recipient_id:b,body:'fixture'}],m),[{sender_id:c,recipient_id:d,body:'fixture'}]);
 assert.deepEqual(remapRows('comments',[{user_id:a,parent_id:b}],m),[{user_id:c,parent_id:b}]);
});
test('incomplete relation and credential imports fail closed',()=>{
 const m=verifiedMapping([{oldId:a,newId:c}]);
 for(const [table,row] of [['direct_messages',{sender_id:a,recipient_id:b}],['profiles',{user_id:a,encrypted_password:'x'}],['profiles',{user_id:a,owner_id:b}],['unknown',{user_id:a}]])
   assert.throws(()=>remapRows(table,[row],m));
});
test('production project and missing credentials rejected',()=>{
 assert.throws(()=>testProject({ZEROCHILL_TEST_SUPABASE_URL:'https://aavjpykssueavbseqdlf.supabase.co'}));
 assert.throws(()=>testProject({ZEROCHILL_TEST_SUPABASE_URL:'https://xswtgidwhesajavxjbjb.supabase.co'}));
});
