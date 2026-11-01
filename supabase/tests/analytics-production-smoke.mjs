// Explicit opt-in only. Adds 31 labeled synthetic creator events, never user/account data.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { validateSourceConfig } from '../functions/_shared/source-config-validation.ts';
assert.equal(process.env.RUN_PRODUCTION_ANALYTICS_SMOKE, '1');
const base = 'https://fketutffusxgjxjlckci.supabase.co/functions/v1/';
const marker = `zerochill-batch-smoke-${process.env.GITHUB_RUN_ID}-${process.env.GITHUB_RUN_ATTEMPT}`;
assert.match(marker, /^zerochill-batch-smoke-\d+-\d+$/);
const key = period => createHash('sha256').update(`${marker}:${period}`).digest('hex');
const event = {metric:'creator', value:marker, day_key:key('day'), week_key:key('week'), month_key:key('month')};
async function post(payload) {
  const response = await fetch(base+'analytics-ingest', {method:'POST',
    headers:{'content-type':'application/json'}, body:JSON.stringify(payload), signal:AbortSignal.timeout(20_000)});
  assert.equal(response.status,204,await response.text());
}
await post(event);
await post({events:new Array(30).fill(event)});
const response = await fetch(base+'app-config', {signal:AbortSignal.timeout(20_000)});
assert.equal(response.status,200);
const config = await response.json();
assert.equal(validateSourceConfig(config).valid,true);
const unchanged = await fetch(base+'app-config', {headers:{'if-none-match':`"${config.configVersion}"`}, signal:AbortSignal.timeout(20_000)});
assert.equal(unchanged.status,304);
console.log(JSON.stringify({marker, singleStatus:204, batchStatus:204, batchEvents:30,
  expectedEventsPerPeriod:31, expectedUniquesPerPeriod:1, configVersion:config.configVersion, configStatus:200, conditionalStatus:304}));
