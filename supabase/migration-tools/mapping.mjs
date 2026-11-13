// Explicit relation inventory. Unsupported tables/owner fields fail closed.
export const ownership = Object.freeze({
  profiles:['user_id'], creator_favorites:['user_id'], video_likes:['user_id'],
  comments:['user_id'], comment_likes:['user_id'],
  direct_messages:['sender_id','recipient_id'], user_blocks:['blocker_id','blocked_id'],
  notification_preferences:['user_id'],
});
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function verifiedMapping(pairs) {
  const map = new Map(), targets = new Set();
  for (const {oldId,newId} of pairs) {
    if (!uuid.test(oldId) || !uuid.test(newId) || oldId===newId || map.has(oldId) || targets.has(newId))
      throw new Error('Mapping must contain distinct valid one-to-one UUIDs');
    map.set(oldId,newId); targets.add(newId);
  }
  return map;
}
export function remapRows(table, rows, mapping) {
  const columns = ownership[table];
  if (!columns) throw new Error('Unsupported relation; inventory its ownership first');
  return rows.map(row => {
    if (Object.keys(row).some(k => /password|token|secret|email/i.test(k)))
      throw new Error('Credentials and emails are never migration row data');
    const copy = {...row};
    for (const column of columns) {
      if (!mapping.has(row[column])) throw new Error('Unmapped owner; refusing partial migration');
      copy[column] = mapping.get(row[column]);
    }
    for (const key of Object.keys(row)) {
      if (/(?:user|sender|recipient|blocker|blocked|owner|auth)_id$/.test(key) && !columns.includes(key))
        throw new Error('Uninventoried owner column');
    }
    return copy;
  });
}
export function testProject(env=process.env) {
  const url = 'https://xswtgidwhesajavxjbjb.supabase.co';
  if (env.ZEROCHILL_TEST_SUPABASE_URL !== url || !env.ZEROCHILL_TEST_SUPABASE_ANON_KEY || !env.ZEROCHILL_TEST_SUPABASE_SERVICE_ROLE_KEY)
    throw new Error('Only the disposable Migration Test project is allowed');
  return {url,anon:env.ZEROCHILL_TEST_SUPABASE_ANON_KEY,service:env.ZEROCHILL_TEST_SUPABASE_SERVICE_ROLE_KEY};
}
