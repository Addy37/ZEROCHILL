import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler, MAX_BATCH_SIZE, MAX_BODY_BYTES } from '../functions/analytics-ingest/handler.ts';
const event = (metric = 'app_open', value = 'all') => ({metric, value, day_key:'a'.repeat(64), week_key:'b'.repeat(64), month_key:'c'.repeat(64)});
const request = body => new Request('https://test.invalid', {method:'POST', body:typeof body === 'string' ? body : JSON.stringify(body)});
test('old single event and batch use one recorder operation with normalized events', async () => {
  const calls=[];
  const handler=createHandler(async (...args) => {calls.push(args);});
  assert.equal((await handler(request(event()))).status,204);
  assert.deepEqual(calls.pop(), [[event()],false]);
  const events=[event(),event('section','library'),event('device_model','OnePlus CPH2655'),event()];
  assert.equal((await handler(request({events}))).status,204);
  assert.deepEqual(calls.pop(),[events,true]);
});
test('every event validated before any write, including keys and device values', async () => {
  const handler=createHandler(async () => assert.fail('invalid batch reached database'));
  for(const bad of [event('bad','all'), {...event(), day_key:'x'}, {...event(), month_key:1}, event('device_model',' bad'),event('android_version','16;id'),null, [], event('source','unknown')]) {
    assert.equal((await handler(request({events:[event(),bad]}))).status,400);
  }
  for(const body of [null,[],{},'{', {events:[]},{events:new Array(MAX_BATCH_SIZE+1).fill(event())},{events:[event()],metric:'app_open'}])
    assert.equal((await handler(request(body))).status,400);
});
test('body limit enforced with and without content length and with multibyte bytes', async () => {
  const handler=createHandler(async () => assert.fail('oversized body reached database'));
  for(const headers of [{},{'content-length':String(MAX_BODY_BYTES+1)}]) {
    assert.equal((await handler(new Request('https://test.invalid',{method:'POST',headers, body:' '.repeat(MAX_BODY_BYTES+1)}))).status,413);
  }
  assert.equal((await handler(request('é'.repeat(MAX_BODY_BYTES)))).status,413);
});
test('maximum batch accepted and backend failures reported without payload disclosure', async () => {
  const events=new Array(MAX_BATCH_SIZE).fill(event());
  assert.equal((await createHandler(async()=>{})(request({events}))).status,204);
  const response=await createHandler(async()=>{throw Error('private database error');})(request(event()));
  assert.equal(response.status,503);
  assert.doesNotMatch(await response.text(),/private/);
});

test('current Android browser sources cannot poison a batch', async () => {
  const events=['crazyshit','efukt','bunkr','kaotic','baddiehub','xmegadrive','onlyhaven'].map(source=>event('source',source));
  assert.equal((await createHandler(async()=>{})(request({events}))).status,204);
});
