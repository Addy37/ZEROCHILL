const all = new Set(['sessions','session_ms','shittok_videos','buffering','search','favorites','creator_gallery','update_prompt','account_signin','comments','likes','dms']);
const errors = new Set(['auth','permission','rate_limit','network','server','invalid','unknown']);
const providers = new Set(['crazyshit','efukt','fapzone','fapello','bunkr','wikifeet','wikifeetx','kaotic','baddiehub','xmegadrive','onlyhaven','unknown']);
const version = /^[A-Za-z0-9._+\-]{1,40}$/;
export type Summary = { version: string; events: {metric:string;value:string;count:number}[]; active: Record<string,string> };
export function limit(metric: string): number {
 return metric==='session_ms' ? 900_000 : metric==='sessions' ? 1 : 10_000;
}
export function allowed(metric: string,value: string): boolean {
  if(all.has(metric)) return value==='all';
  switch(metric) {
    case 'section': return ['shittok','shows','onlyfap','library'].includes(value);
    case 'provider_failure': case 'provider_missing': return providers.has(value);
    case 'playback_failure': return ['network','decoder','source','unknown'].includes(value);
    case 'backend_error': case 'account_error': return errors.has(value);
    case 'app_version': case 'update_adoption': return version.test(value);
    case 'device_model': return /^[\p{L}\p{N} ._+()/\-]{1,80}$/u.test(value);
    case 'android_version': return /^(?:[0-9]{1,2}(?:\.[0-9]{1,2})?|Unknown)$/.test(value);
    default: return false;
  }
}
// Date components come from Intl, which handles EST/EDT. Calendar arithmetic stays in UTC
// only after extracting the Eastern date, so a DST day is never assumed to be 24 hours.
export function periods(now = new Date()): Record<string,string> {
 const parts = new Intl.DateTimeFormat('en-US',{timeZone:'America/New_York',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now);
 const get=(name:string)=>parts.find(p=>p.type===name)!.value;
 const day=`${get('year')}-${get('month')}-${get('day')}`;
 const d=new Date(`${day}T12:00:00Z`); d.setUTCDate(d.getUTCDate()-((d.getUTCDay()+6)%7));
 return {day,week:d.toISOString().slice(0,10),month:day.slice(0,7)+'-01'};
}
function keys(value: object,expected: string[]) { if(Object.keys(value).sort().join()!==expected.sort().join()) throw Error('Unexpected fields'); }
export function validateSummary(input: unknown,now = new Date()): Summary {
 if(!input || typeof input!=='object' || Array.isArray(input)) throw Error();
 const s=input as Summary; keys(s,['version','events','active']);
 if(typeof s.version!=='string'||!version.test(s.version)||!Array.isArray(s.events)||s.events.length<1||s.events.length>160) throw Error();
 s.events.forEach(e=>{
   if(!e||typeof e!=='object') throw Error(); keys(e,['metric','value','count']);
   if(typeof e.metric!=='string'||typeof e.value!=='string'||!allowed(e.metric,e.value)||!Number.isSafeInteger(e.count)||e.count<1||e.count>limit(e.metric)) throw Error();
 });
 if(!s.active||typeof s.active!=='object'||Array.isArray(s.active)) throw Error();
 keys(s.active,['day_key','week_key','month_key','day_period','week_period','month_period']);
 const p=periods(now);
 for(const name of ['day','week','month']) {
   if(!/^[a-f0-9]{64}$/.test(s.active[name+'_key'])||s.active[name+'_period']!==p[name]) throw Error();
 }
 return s;
}
