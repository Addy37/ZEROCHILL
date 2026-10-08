package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local, bounded inbox for source activity. Content updates stay in-app instead of spamming Android. */
final class UpdateInboxStore {
    static final String CATEGORY_ONLYFAP = "onlyfap";
    static final String CATEGORY_VIDEOS = "videos";
    static final String CATEGORY_SOCIAL = "social";
    static final String CATEGORY_APP = "app";

    private static final String PREFS = "zerochill_update_inbox_v1";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 200;
    private static final String KEY_DELETED = "deleted_entries";
    private static final String KEY_SOCIAL_CUTOFFS = "social_cleanup_cutoffs";
    private static final int MAX_DELETED = 2000;
    // Social polling only backfills 30 days. Keep markers for an extra day.
    private static final long SOCIAL_RETENTION_MS = 31L * 24 * 60 * 60 * 1000;
    private static final Object LOCK = new Object();

    private UpdateInboxStore() {
    }

    static void record(Context context, List<NotificationCoordinator.SourceAlert> alerts) {
        if (context == null || alerts == null || alerts.isEmpty()
                || !ZeroChillNotificationPreferences.cached(context).creatorUpdates) return;
        synchronized (LOCK) {
            ArrayList<Entry> incoming = buildEntries(context.getApplicationContext(), alerts);
            CleanupState cleanup = new CleanupState(context);
            incoming.removeIf(cleanup::contains);
            if (incoming.isEmpty()) return;

            ArrayList<Entry> existing = readLocked(context);
            pruneNonFavoriteContent(context, existing);
            ArrayList<Entry> combined = new ArrayList<>(incoming);
            combined.addAll(existing);
            dedupeAndTrim(combined);
            writeLocked(context, combined);
        }
    }

    static void recordAppUpdate(
            Context context,
            String version,
            String title,
            boolean beta
    ) {
        if (context == null || version == null || version.trim().isEmpty()
                || !ZeroChillNotificationPreferences.cached(context).appUpdates) return;
        synchronized (LOCK) {
            Entry entry = new Entry();
            entry.timestamp = System.currentTimeMillis();
            entry.category = CATEGORY_APP;
            entry.sourceKey = "app";
            entry.sourceLabel = beta ? "Beta" : "Stable";
            entry.title = title == null || title.trim().isEmpty()
                    ? "ZeroChill " + version.trim()
                    : title.trim();
            entry.subtitle = "App update " + version.trim() + " is ready";
            entry.appVersion = version.trim();
            entry.count = 1;
            entry.fingerprint = fingerprint(CATEGORY_APP, entry.appVersion, Collections.emptyList());
            entry.id = entry.fingerprint + ":" + entry.timestamp;

            if (new CleanupState(context).contains(entry)) return;
            ArrayList<Entry> existing = readLocked(context);
            pruneNonFavoriteContent(context, existing);
            for (Entry current : existing) {
                if (entry.fingerprint.equals(current.fingerprint)) return;
            }
            ArrayList<Entry> combined = new ArrayList<>();
            combined.add(entry);
            combined.addAll(existing);
            dedupeAndTrim(combined);
            writeLocked(context, combined);
        }
    }

    static List<Entry> recordSocialActivities(
            Context context,
            String accountId,
            List<ZeroChillSocialRepository.SocialActivity> activity
    ) {
        if (context == null || clean(accountId).isEmpty() || activity == null || activity.isEmpty()) return Collections.emptyList();
        synchronized (LOCK) {
            ArrayList<Entry> existing = readLocked(context);
            pruneNonFavoriteContent(context, existing);
            LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
            for (Entry current : existing) fingerprints.add(current.fingerprint);

            CleanupState cleanup = new CleanupState(context);
            boolean refreshed = false;
            ArrayList<Entry> incoming = new ArrayList<>();
            for (ZeroChillSocialRepository.SocialActivity item : activity) {
                if (item == null || clean(item.eventId).isEmpty() || item.actor == null
                        || !ZeroChillNotificationPreferences.cachedForAccount(context, accountId).allowsSocial(item.type)) continue;
                Entry entry = new Entry();
                entry.timestamp = parseTimestamp(item.createdAt);
                if (entry.timestamp <= 0L) entry.timestamp = System.currentTimeMillis();
                entry.category = CATEGORY_SOCIAL;
                entry.sourceKey = "social";
                entry.sourceLabel = "Social";
                entry.accountId = clean(accountId);
                entry.socialType = clean(item.type);
                entry.pageUrl = clean(item.pageUrl);
                entry.videoTitle = clean(item.videoTitle);
                entry.commentId = clean(item.commentId);
                NativeContentItem content = SocialContentContextStore.find(context, entry.pageUrl);
                if (content != null) entry.items.add(content);
                String actor = SocialUi.name(item.actor.displayName, item.actor.username);
                entry.actorName = actor;
                entry.actorId = clean(item.actor.userId);
                entry.title = actor + (ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(item.type)
                        ? " replied to your comment"
                        : " liked your comment");
                entry.subtitle = truncate(
                        ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(item.type)
                                ? item.replyBody
                                : item.originalBody,
                        180
                );
                entry.avatarUrl = ZeroChillAccountRepository.avatarUrl(item.actor.avatarPath);
                entry.count = 1;
                entry.fingerprint = fingerprint(
                        CATEGORY_SOCIAL,
                        entry.accountId + "|" + item.eventId,
                        Collections.emptyList()
                );
                entry.id = entry.fingerprint + ":" + entry.timestamp;
                if (cleanup.contains(entry)) continue;
                if (fingerprints.add(entry.fingerprint)) {
                    incoming.add(entry);
                } else {
                    for (Entry current : existing) {
                        if (!entry.accountId.equals(current.accountId)
                                || !entry.fingerprint.equals(current.fingerprint)) continue;
                        if (!entry.avatarUrl.equals(current.avatarUrl) || !entry.actorId.equals(current.actorId)) {
                            current.avatarUrl = entry.avatarUrl;
                            current.actorId = entry.actorId;
                            refreshed = true;
                        }
                        break;
                    }
                }
            }

            if (incoming.isEmpty()) {
                if (refreshed) writeLocked(context, existing);
                return Collections.emptyList();
            }
            ArrayList<Entry> inserted = new ArrayList<>(incoming);
            incoming.addAll(existing);
            dedupeAndTrim(incoming);
            writeLocked(context, incoming);
            return inserted;
        }
    }

    static void removeAccount(Context context, String accountId) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            entries.removeIf(entry -> CATEGORY_SOCIAL.equals(entry.category) && accountId.equals(entry.accountId));
            ArrayList<Entry> deleted = readDeletedLocked(context);
            deleted.removeIf(entry -> accountId.equals(entry.accountId));
            JSONObject cutoffs = readCutoffsLocked(context);
            cutoffs.remove(accountId);
            writeCleanupLocked(context, deleted, cutoffs);
            writeLocked(context, entries);
        }
    }

    /** Only current-account social history and installation-wide creator/app history are removable. */
    static boolean delete(Context context, String id, String accountId) {
        synchronized (LOCK) {
            if (!clean(accountId).equals(ZeroChillSessionStore.currentUserId(context))) return false;
            ArrayList<Entry> entries = readLocked(context);
            pruneNonFavoriteContent(context, entries);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (!clean(id).equals(entry.id) || !visibleTo(entry, accountId)) continue;
                rememberDeletedLocked(context, Collections.singletonList(entry));
                entries.remove(i);
                writeLocked(context, entries);
                return true;
            }
            return false;
        }
    }

    static int clearAll(Context context, String accountId) {
        synchronized (LOCK) {
            if (!clean(accountId).equals(ZeroChillSessionStore.currentUserId(context))) return 0;
            ArrayList<Entry> entries = readLocked(context);
            pruneNonFavoriteContent(context, entries);
            ArrayList<Entry> removed = new ArrayList<>();
            entries.removeIf(entry -> {
                if (!visibleTo(entry, accountId)) return false;
                removed.add(entry);
                return true;
            });
            if (!removed.isEmpty()) {
                rememberDeletedLocked(context, removed);
                writeLocked(context, entries);
            }
            return removed.size();
        }
    }

    static void refreshActorAvatar(Context context, String actorId, String avatarPath) {
        if (clean(actorId).isEmpty()) return;
        synchronized (LOCK) {
            String accountId = ZeroChillSessionStore.currentUserId(context);
            String url = ZeroChillAccountRepository.avatarUrl(avatarPath);
            ArrayList<Entry> entries = readLocked(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (!CATEGORY_SOCIAL.equals(entry.category) || !accountId.equals(entry.accountId)
                        || !actorId.equals(entry.actorId) || url.equals(entry.avatarUrl)) continue;
                entry.avatarUrl = url;
                changed = true;
            }
            if (changed) writeLocked(context, entries);
        }
    }

    private static boolean visibleTo(Entry entry, String accountId) {
        return !CATEGORY_SOCIAL.equals(entry.category)
                || (!clean(accountId).isEmpty() && clean(accountId).equals(entry.accountId));
    }

    private static final class CleanupState {
        final LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
        final JSONObject cutoffs;
        CleanupState(Context context) {
            cutoffs = readCutoffsLocked(context);
            for (Entry deleted : readDeletedLocked(context)) {
                fingerprints.add(deleted.accountId + "|" + deleted.fingerprint);
            }
        }
        boolean contains(Entry entry) {
            return (CATEGORY_SOCIAL.equals(entry.category)
                    && entry.timestamp <= cutoffs.optLong(entry.accountId, -1L))
                    || fingerprints.contains(entry.accountId + "|" + entry.fingerprint);
        }
    }

    private static ArrayList<Entry> readDeletedLocked(Context context) {
        ArrayList<Entry> deleted = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DELETED, "[]"));
            long cutoff = System.currentTimeMillis() - SOCIAL_RETENTION_MS;
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                Entry entry = new Entry();
                entry.fingerprint = value.optString("fingerprint");
                entry.accountId = value.optString("accountId");
                entry.timestamp = value.optLong("timestamp");
                if (!entry.accountId.isEmpty() && entry.timestamp < cutoff) continue;
                deleted.add(entry);
            }
        } catch (Exception ignored) { }
        return deleted;
    }

    private static JSONObject readCutoffsLocked(Context context) {
        try {
            return new JSONObject(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_SOCIAL_CUTOFFS, "{}"));
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private static void rememberDeletedLocked(Context context, List<Entry> removed) {
        ArrayList<Entry> deleted = readDeletedLocked(context);
        JSONObject cutoffs = readCutoffsLocked(context);
        deleted.addAll(removed);
        while (deleted.size() > MAX_DELETED) {
            Entry oldest = deleted.remove(0);
            // Compact excessive social markers into an account cutoff. Existing unremoved rows
            // stay visible; only old polling backfill is suppressed. Other accounts stay isolated.
            if (!oldest.accountId.isEmpty()) {
                try { cutoffs.put(oldest.accountId, Math.max(oldest.timestamp,
                        cutoffs.optLong(oldest.accountId, -1L))); } catch (Exception ignored) { }
            }
        }
        writeCleanupLocked(context, deleted, cutoffs);
    }

    private static void writeCleanupLocked(Context context, List<Entry> deleted, JSONObject cutoffs) {
        JSONArray values = new JSONArray();
        for (Entry entry : deleted) {
            try {
                values.put(new JSONObject().put("fingerprint", entry.fingerprint)
                        .put("accountId", entry.accountId).put("timestamp", entry.timestamp));
            } catch (Exception ignored) { }
        }
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DELETED, values.toString()).putString(KEY_SOCIAL_CUTOFFS, cutoffs.toString()).apply();
    }

    static List<Entry> all(Context context) {
        return allForAccount(context, ZeroChillSessionStore.currentUserId(context));
    }

    static List<Entry> allForAccount(Context context, String accountId) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            if (pruneNonFavoriteContent(context, entries)) writeLocked(context, entries);
            ArrayList<Entry> visible = new ArrayList<>();
            String current = clean(accountId);
            for (Entry entry : entries) {
                if (CATEGORY_SOCIAL.equals(entry.category)
                        && (current.isEmpty() || !current.equals(entry.accountId))) {
                    continue;
                }
                visible.add(entry);
            }
            return visible;
        }
    }

    static List<Entry> filtered(Context context, String category) {
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (category == null || category.isEmpty() || category.equals(entry.category)) {
                result.add(entry);
            }
        }
        return result;
    }

    static int unreadCount(Context context) {
        int count = 0;
        for (Entry entry : all(context)) if (!entry.read) count++;
        return count;
    }

    static int unreadCreatorContentCount(
            Context context,
            java.util.Collection<String> creatorNames
    ) {
        LinkedHashSet<String> wanted = normalizedCreatorNames(creatorNames);
        if (wanted.isEmpty()) return 0;
        int count = 0;
        for (Entry entry : all(context)) {
            if (entry.read || !CATEGORY_ONLYFAP.equals(entry.category)) continue;
            if (!wanted.contains(CreatorNameMatcher.normalized(entry.creatorName))) continue;
            count += Math.max(1, entry.count);
        }
        return count;
    }

    static ArrayList<String> unreadCreatorFreshUrls(
            Context context,
            java.util.Collection<String> creatorNames
    ) {
        LinkedHashSet<String> wanted = normalizedCreatorNames(creatorNames);
        LinkedHashSet<String> fresh = new LinkedHashSet<>();
        if (wanted.isEmpty()) return new ArrayList<>();
        for (Entry entry : all(context)) {
            if (entry.read || !CATEGORY_ONLYFAP.equals(entry.category)) continue;
            if (!wanted.contains(CreatorNameMatcher.normalized(entry.creatorName))) continue;
            for (String url : entry.freshUrls) {
                String clean = clean(url);
                if (!clean.isEmpty()) fresh.add(clean);
            }
        }
        return new ArrayList<>(fresh);
    }

    static void markCreatorRead(
            Context context,
            java.util.Collection<String> creatorNames
    ) {
        LinkedHashSet<String> wanted = normalizedCreatorNames(creatorNames);
        if (wanted.isEmpty()) return;
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (entry.read || !CATEGORY_ONLYFAP.equals(entry.category)) continue;
                if (!wanted.contains(CreatorNameMatcher.normalized(entry.creatorName))) continue;
                entry.read = true;
                changed = true;
            }
            if (changed) writeLocked(context, entries);
        }
    }

    static void markRead(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return;
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (id.equals(entry.id) && !entry.read) {
                    entry.read = true;
                    changed = true;
                    break;
                }
            }
            if (changed) writeLocked(context, entries);
        }
    }

    static void markAllRead(Context context) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = readLocked(context);
            String accountId = ZeroChillSessionStore.currentUserId(context);
            boolean changed = false;
            for (Entry entry : entries) {
                if (CATEGORY_SOCIAL.equals(entry.category)
                        && (accountId.isEmpty() || !accountId.equals(entry.accountId))) {
                    continue;
                }
                if (!entry.read) {
                    entry.read = true;
                    changed = true;
                }
            }
            if (changed) writeLocked(context, entries);
        }
    }

    private static ArrayList<Entry> buildEntries(
            Context context,
            List<NotificationCoordinator.SourceAlert> alerts
    ) {
        long now = System.currentTimeMillis();
        ArrayList<Entry> result = new ArrayList<>();
        LinkedHashMap<String, CreatorAccumulator> creators = new LinkedHashMap<>();
        int sequence = 0;

        for (NotificationCoordinator.SourceAlert alert : alerts) {
            if (alert == null || alert.items == null || alert.items.isEmpty()) continue;
            String sourceKey = clean(alert.key).toLowerCase(Locale.US);
            if (!isOnlyFapSource(sourceKey)) continue;

            for (NativeContentItem item : alert.items) {
                if (item == null || item.url == null || item.url.trim().isEmpty()) continue;
                NotificationCoordinator.ExperienceItem wrapped =
                        new NotificationCoordinator.ExperienceItem(sourceKey, item);
                String creator = NotificationCoordinator.onlyFapCreatorName(wrapped);
                if (creator.isEmpty() || !isFavoriteCreator(context, creator)) continue;

                String key = CreatorNameMatcher.normalized(creator);
                CreatorAccumulator acc = creators.get(key);
                if (acc == null) {
                    acc = new CreatorAccumulator(creator);
                    creators.put(key, acc);
                }
                acc.add(sourceKey, item);
            }
        }

        for (CreatorAccumulator acc : creators.values()) {
            result.add(creatorEntry(context, now + sequence++, acc));
        }

        Collections.sort(result, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        return result;
    }

    private static Entry creatorEntry(Context context, long timestamp, CreatorAccumulator acc) {
        Entry entry = new Entry();
        entry.timestamp = timestamp;
        entry.category = CATEGORY_ONLYFAP;
        entry.sourceKey = "onlyfap";
        entry.sourceLabel = "OnlyFap";
        entry.creatorName = acc.name;
        entry.title = acc.name;
        entry.count = acc.items.size();
        entry.videoCount = acc.videoCount;
        entry.subtitle = acc.videoCount == acc.items.size() && entry.count > 0
                ? entry.count + (entry.count == 1 ? " new video" : " new videos")
                : entry.count == 1 ? "New content" : entry.count + " new items";
        entry.freshUrls.addAll(acc.urls);
        entry.items.addAll(acc.items);
        applyCreatorMetadata(context, entry, acc);
        entry.fingerprint = fingerprint(entry.category, entry.creatorName, entry.freshUrls);
        entry.id = entry.fingerprint + ":" + timestamp;
        return entry;
    }

    private static void applyCreatorMetadata(
            Context context,
            Entry entry,
            CreatorAccumulator acc
    ) {
        String wanted = CreatorNameMatcher.normalized(entry.creatorName);
        for (NativeContentItem candidate : CreatorCatalog.all(context)) {
            if (!wanted.equals(CreatorNameMatcher.normalized(candidate.title)) &&
                    !wanted.equals(CreatorNameMatcher.normalized(candidate.searchQuery))) continue;
            if (entry.avatarUrl.isEmpty() && candidate.imageUrl != null &&
                    !candidate.imageUrl.trim().isEmpty()) {
                entry.avatarUrl = candidate.imageUrl.trim();
                entry.avatarReferer = candidate.uploader == null || candidate.uploader.trim().isEmpty()
                        ? clean(candidate.url)
                        : candidate.uploader.trim();
            }
            if (entry.fapelloProfileUrl.isEmpty() &&
                    FapelloRepository.isModelUrl(candidate.url)) {
                entry.fapelloProfileUrl = candidate.url.trim();
            }
        }

        if (entry.avatarUrl.isEmpty()) {
            for (SourceItem sourceItem : acc.sourceItems) {
                if (!"onlyhaven".equals(sourceItem.sourceKey)) continue;
                NativeContentItem item = sourceItem.item;
                if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                    entry.avatarUrl = item.imageUrl.trim();
                    entry.avatarReferer = clean(item.url);
                    break;
                }
            }
        }
    }

    private static String fingerprint(String category, String subject, List<String> urls) {
        ArrayList<String> stable = new ArrayList<>();
        if (urls != null) stable.addAll(urls);
        Collections.sort(stable);
        return Integer.toHexString(
                (clean(category) + "|" + clean(subject) + "|" + stable.toString()).hashCode()
        );
    }

    private static void dedupeAndTrim(ArrayList<Entry> entries) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        java.util.Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry == null || entry.fingerprint.isEmpty() || !seen.add(entry.fingerprint)) {
                iterator.remove();
            }
        }
        Collections.sort(entries, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }

    private static ArrayList<Entry> readLocked(Context context) {
        ArrayList<Entry> result = new ArrayList<>();
        try {
            String encoded = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ENTRIES, "[]");
            JSONArray values = new JSONArray(encoded);
            for (int i = 0; i < Math.min(values.length(), MAX_ENTRIES); i++) {
                JSONObject value = values.optJSONObject(i);
                Entry entry = Entry.decode(value);
                if (entry != null) result.add(entry);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void writeLocked(Context context, List<Entry> entries) {
        JSONArray values = new JSONArray();
        for (Entry entry : entries) {
            if (entry == null) continue;
            try {
                values.put(entry.encode());
            } catch (Exception ignored) {
            }
            if (values.length() >= MAX_ENTRIES) break;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, values.toString())
                .apply();
    }

    private static boolean isOnlyFapSource(String key) {
        return "fapello".equals(key) || "bunkr".equals(key) || "onlyhaven".equals(key);
    }

    static boolean isFavoriteCreator(Context context, String creatorName) {
        if (context == null || creatorName == null || creatorName.trim().isEmpty()) return false;
        String wanted = CreatorNameMatcher.normalized(creatorName);
        if (wanted.isEmpty()) return false;
        for (String favorite : CreatorFavoriteStore.names(context)) {
            if (wanted.equals(CreatorNameMatcher.normalized(favorite))) return true;
        }
        return false;
    }

    private static boolean pruneNonFavoriteContent(Context context, ArrayList<Entry> entries) {
        boolean changed = false;
        java.util.Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry == null) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (CATEGORY_APP.equals(entry.category) || CATEGORY_SOCIAL.equals(entry.category)) continue;
            boolean keepFavoriteCreator = CATEGORY_ONLYFAP.equals(entry.category)
                    && !entry.creatorName.isEmpty()
                    && isFavoriteCreator(context, entry.creatorName);
            if (!keepFavoriteCreator) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static LinkedHashSet<String> normalizedCreatorNames(
            java.util.Collection<String> creatorNames
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (creatorNames == null) return result;
        for (String name : creatorNames) {
            String normalized = CreatorNameMatcher.normalized(name);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private static long parseTimestamp(String value) {
        try {
            return java.time.Instant.parse(clean(value)).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String truncate(String value, int max) {
        String clean = clean(value);
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Entry {
        String id = "";
        String fingerprint = "";
        String category = "";
        String sourceKey = "";
        String sourceLabel = "";
        String title = "";
        String subtitle = "";
        String creatorName = "";
        String avatarUrl = "";
        String avatarReferer = "";
        String fapelloProfileUrl = "";
        String appVersion = "";
        String accountId = "";
        String socialType = "";
        String pageUrl = "";
        String videoTitle = "";
        String commentId = "";
        String actorName = "";
        String actorId = "";
        long timestamp;
        int count;
        int videoCount;
        boolean read;
        final ArrayList<String> freshUrls = new ArrayList<>();
        final ArrayList<NativeContentItem> items = new ArrayList<>();

        JSONObject encode() throws Exception {
            JSONObject value = new JSONObject()
                    .put("id", id)
                    .put("fingerprint", fingerprint)
                    .put("category", category)
                    .put("sourceKey", sourceKey)
                    .put("sourceLabel", sourceLabel)
                    .put("title", title)
                    .put("subtitle", subtitle)
                    .put("creatorName", creatorName)
                    .put("avatarUrl", avatarUrl)
                    .put("avatarReferer", avatarReferer)
                    .put("fapelloProfileUrl", fapelloProfileUrl)
                    .put("appVersion", appVersion)
                    .put("accountId", accountId)
                    .put("socialType", socialType)
                    .put("pageUrl", pageUrl)
                    .put("videoTitle", videoTitle)
                    .put("commentId", commentId)
                    .put("actorName", actorName)
                    .put("actorId", actorId)
                    .put("timestamp", timestamp)
                    .put("count", count)
                    .put("videoCount", videoCount)
                    .put("read", read)
                    .put("freshUrls", new JSONArray(freshUrls))
                    .put("items", ContentItemCodec.encodeList(items, 1));
            return value;
        }

        static Entry decode(JSONObject value) {
            if (value == null) return null;
            Entry entry = new Entry();
            entry.id = value.optString("id", "");
            entry.fingerprint = value.optString("fingerprint", "");
            entry.category = value.optString("category", "");
            entry.sourceKey = value.optString("sourceKey", "");
            entry.sourceLabel = value.optString("sourceLabel", "");
            entry.title = value.optString("title", "");
            entry.subtitle = value.optString("subtitle", "");
            entry.creatorName = value.optString("creatorName", "");
            entry.avatarUrl = value.optString("avatarUrl", "");
            entry.avatarReferer = value.optString("avatarReferer", "");
            entry.fapelloProfileUrl = value.optString("fapelloProfileUrl", "");
            entry.appVersion = value.optString("appVersion", "");
            entry.accountId = value.optString("accountId", "");
            entry.socialType = value.optString("socialType", "");
            entry.pageUrl = value.optString("pageUrl", "");
            entry.videoTitle = value.optString("videoTitle", "");
            entry.commentId = value.optString("commentId", "");
            entry.actorName = value.optString("actorName", "");
            entry.actorId = value.optString("actorId", "");
            if (entry.actorId.isEmpty() && CATEGORY_SOCIAL.equals(entry.category)) {
                String base = clean(BuildConfig.ACCOUNT_SUPABASE_URL) + "/storage/v1/object/public/avatars/";
                if (entry.avatarUrl.startsWith(base)) {
                    String folder = entry.avatarUrl.substring(base.length()).split("/", 2)[0];
                    try { entry.actorId = java.util.UUID.fromString(folder).toString(); } catch (Exception ignored) { }
                }
            }
            entry.timestamp = value.optLong("timestamp", 0L);
            entry.count = value.optInt("count", 0);
            entry.videoCount = value.optInt("videoCount", 0);
            entry.read = value.optBoolean("read", false);
            JSONArray urls = value.optJSONArray("freshUrls");
            if (urls != null) {
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i, "").trim();
                    if (!url.isEmpty()) entry.freshUrls.add(url);
                }
            }
            entry.items.addAll(ContentItemCodec.decodeList(value.optJSONArray("items"), 1));
            if (entry.fingerprint.isEmpty()) {
                entry.fingerprint = fingerprint(entry.category, entry.title, entry.freshUrls);
            }
            if (entry.id.isEmpty()) entry.id = entry.fingerprint + ":" + entry.timestamp;
            return entry;
        }

        NativeContentItem firstItem() {
            return items.isEmpty() ? null : items.get(0);
        }
    }

    private static final class CreatorAccumulator {
        final String name;
        final ArrayList<NativeContentItem> items = new ArrayList<>();
        final ArrayList<String> urls = new ArrayList<>();
        final ArrayList<SourceItem> sourceItems = new ArrayList<>();
        int videoCount;

        CreatorAccumulator(String name) {
            this.name = clean(name);
        }

        void add(String sourceKey, NativeContentItem item) {
            items.add(item);
            sourceItems.add(new SourceItem(sourceKey, item));
            if (!urls.contains(item.url.trim())) urls.add(item.url.trim());
            if (item.isVideo()) videoCount++;
        }
    }

    private static final class SourceItem {
        final String sourceKey;
        final NativeContentItem item;

        SourceItem(String sourceKey, NativeContentItem item) {
            this.sourceKey = sourceKey;
            this.item = item;
        }
    }
}

