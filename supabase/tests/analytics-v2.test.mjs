import {test} from 'node:test';
import assert from 'node:assert/strict';
import {periods,validateSummary,allowed} from '../functions/analytics-ingest-v2/validation.ts';
import {createHandler,MAX_BODY_BYTES} from '../functions/analytics-ingest-v2/handler.ts';
const summary=(now=new Date())=>({version:'5.7.0-beta.1',events:[{metric:'sessions',value:'all',count:1},{metric:'search',value:'all',count:300}],active:Object.fromEntries(Object.entries(periods(now)).flatMap(([k,v])=>[[k+'_period',v],[k+'_key',k[0].repeat(64).replaceAll('w','b').replaceAll('m','c')]]))});
const request=body=>new Request('https://test.invalid',{method:'POST',body:JSON.stringify(body)});
test('one compact session summary replaces 300 event requests with one write',async()=>{
 let calls=0;
 const h=createHandler(async s=>{calls++;assert.equal(s[0].events[1].count,300);});
 assert.equal((await h(request(summary()))).status,204);assert.equal(calls,1);
});
test('strict metric and field allowlists reject excluded payloads before recording',async()=>{
 const h=createHandler(async()=>assert.fail('private data reached recorder'));
 for(const metric of ['creator','source','focus_mode','downloads','history','search_terms','recipient','attachment_url']) {
  const s=summary();s.events.push({metric,value:'secret',count:1});assert.equal((await h(request(s))).status,400);
 }
 for(const field of ['text','search_terms','path','url','user_id','recipient_id']) {
  const s=summary();s.events[0][field]='secret';assert.equal((await h(request(s))).status,400);
 }
 assert.equal(allowed('section','downloads'),false);assert.equal(allowed('provider_failure','https://secret'),false);
});
test('Eastern boundaries and DST spring/fall use local calendar dates',()=>{
 assert.deepEqual(periods(new Date('2026-03-08T04:59:59Z')),{day:'2026-03-07',week:'2026-03-02',month:'2026-03-01'});
 assert.equal(periods(new Date('2026-03-08T05:00:00Z')).day,'2026-03-08');
 assert.equal(periods(new Date('2026-03-09T03:59:59Z')).day,'2026-03-08');
 assert.equal(periods(new Date('2026-03-09T04:00:00Z')).week,'2026-03-09');
 for(const time of ['2026-11-01T05:30:00Z','2026-11-01T06:30:00Z']) assert.equal(periods(new Date(time)).day,'2026-11-01');
 assert.equal(periods(new Date('2026-12-01T04:59:59Z')).month,'2026-11-01');
 assert.equal(periods(new Date('2026-12-01T05:00:00Z')).month,'2026-12-01');
});
test('bad keys, stale periods, oversized counts, bodies and backend failures are bounded',async()=>{
 const h=createHandler(async()=>{});
 for(const mutate of [s=>s.active.day_key='stable-account',s=>s.active.day_period='2020-01-01',s=>delete s.active.month_period,s=>s.events[0].count=2,s=>s.events[1].count=10001,s=>s.events=[]]) {
  const s=summary();mutate(s);assert.equal((await h(request(s))).status,400);
 }
 assert.equal((await h(new Request('https://test.invalid',{method:'POST',body:'x'.repeat(MAX_BODY_BYTES+1)}))).status,413);
 const response=await createHandler(async()=>{throw Error('secret backend text');})(request(summary()));
 assert.equal(response.status,503);assert.doesNotMatch(await response.text(),/secret/);
 assert.equal((await createHandler(async()=>{throw Error('rate_limited');})(request(summary()))).status,429);
});

test('retry backlog batches up to five complete summaries into one transaction',async()=>{
 let writes=0;
 const h=createHandler(async batch=>{writes++;assert.equal(batch.length,5);});
 assert.equal((await h(request({summaries:Array.from({length:5},()=>summary())}))).status,204);
 assert.equal(writes,1);
 assert.equal((await h(request({summaries:Array.from({length:6},()=>summary())}))).status,400);
});
