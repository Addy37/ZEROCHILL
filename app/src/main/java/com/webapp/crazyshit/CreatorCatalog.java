package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/** Creator metadata learned from real listings and search responses, plus existing favorites. */
final class CreatorCatalog {
    static final String PREFS = "creator_catalog_v1";
    // Learned rich metadata is bounded separately from the immutable bundled names.
    private static final int MAX_CACHED = 2000;
    private static final String[] SHELVES = {"popular_creator_feed_v3", "popular_creator_feed_v2",
            "onlyfap_trending_v4", "fapzone_creator_feed_v3_1",
            "fapzone_creator_feed_v3_2", "fapzone_creator_feed_v3_3",
            "fapzone_creator_feed_v1_1", "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"};

    private CreatorCatalog() { }

    static String key(NativeContentItem item) {
        // Search can ignore accents and punctuation; stored creator identities must not.
        return CreatorFavoriteStore.key(item);
    }

    static List<NativeContentItem> all(Context context) {
        LinkedHashMap<String, NativeContentItem> result = read(context);
        // Older versions saved favorite names only. Keep every one usable after upgrading.
        for (String name : CreatorFavoriteStore.names(context)) {
            NativeContentItem legacy = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    name, "", "", "", "", "", "", name);
            result.putIfAbsent(key(legacy), legacy);
        }
        return new ArrayList<>(result.values());
    }

    static List<NativeContentItem> matching(Context context, String query, boolean favoritesOnly, int limit) {
        Set<String> favorites = CreatorFavoriteStore.names(context);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (favoritesOnly && favorites.isEmpty()) return result;
        if (favoritesOnly) {
            for (FavoriteGroup group : favoriteGroups(context)) {
                NativeContentItem item = group.item;
                if (CreatorNameMatcher.rank(item.title, query) != Integer.MAX_VALUE
                        || CreatorNameMatcher.rank(item.searchQuery, query) != Integer.MAX_VALUE
                        || group.members.values().stream().anyMatch(member ->
                        CreatorNameMatcher.rank(member.title, query) != Integer.MAX_VALUE))
                    result.add(item);
            }
        } else for (NativeContentItem item : all(context)) {
            if (CreatorNameMatcher.rank(item.title, query) != Integer.MAX_VALUE
                    || CreatorNameMatcher.rank(item.searchQuery, query) != Integer.MAX_VALUE) result.add(item);
        }
        Comparator<NativeContentItem> names = Comparator.comparing(
                item -> CreatorNameMatcher.normalized(item.title));
        if (favoritesOnly || query.trim().isEmpty()) result.sort(names);
        else result.sort(Comparator.<NativeContentItem>comparingInt(
                        item -> Math.min(CreatorNameMatcher.rank(item.title, query),
                                CreatorNameMatcher.rank(item.searchQuery, query)))
                .thenComparingInt(item -> favorites.contains(CreatorFavoriteStore.key(item)) ? 0 : 1)
                .thenComparing(names));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    static final class FavoriteGroup {
        final NativeContentItem item;
        final LinkedHashMap<String, NativeContentItem> members;
        final Set<String> relationshipKeys;
        final boolean manual;

        FavoriteGroup(NativeContentItem item, LinkedHashMap<String, NativeContentItem> members,
                      Set<String> relationshipKeys, boolean manual) {
            this.item = item;
            this.members = members;
            this.relationshipKeys = relationshipKeys;
            this.manual = manual;
        }
    }

    /** One catalog snapshot and one relationship snapshot per refresh, never per card bind. */
    static List<FavoriteGroup> favoriteGroups(Context context) {
        Set<String> favorites = CreatorFavoriteStore.names(context);
        if (favorites.isEmpty()) return new ArrayList<>();
        Map<String, NativeContentItem> byStoredKey = new java.util.HashMap<>();
        for (NativeContentItem item : all(context)) byStoredKey.put(key(item), item);
        LinkedHashMap<String, FavoriteGroup> automatic = new LinkedHashMap<>();
        for (String raw : favorites) {
            String identity = CreatorIdentity.reviewed(raw) ? CreatorIdentity.key(raw) : "raw:" + raw;
            NativeContentItem exact = byStoredKey.get(raw);
            NativeContentItem best = exact;
            List<String> aliases = CreatorIdentity.storedKeys(raw);
            if (aliases != null) for (String alias : aliases) {
                NativeContentItem candidate = byStoredKey.get(alias);
                if (candidate != null && (best == null || best.imageUrl.isEmpty()
                        && !candidate.imageUrl.isEmpty() || best.imageUrl.isEmpty()
                        == candidate.imageUrl.isEmpty() && best.url.isEmpty()
                        && !candidate.url.isEmpty())) best = candidate;
            }
            if (best == null) continue;
            FavoriteGroup previous = automatic.get(identity);
            LinkedHashMap<String, NativeContentItem> members = previous == null
                    ? new LinkedHashMap<>() : previous.members;
            members.put(raw, exact == null ? new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    raw, "", "", "", "", "", "", raw) : exact);
            NativeContentItem display = previous != null && (!previous.item.imageUrl.isEmpty()
                    || best.imageUrl.isEmpty()) ? previous.item : best;
            automatic.put(identity, new FavoriteGroup(titled(display, CreatorIdentity.display(display.title)),
                    members, new LinkedHashSet<>(members.keySet()), false));
        }
        List<FavoriteGroup> groups = new ArrayList<>(automatic.values());
        for (ManualCreatorMergeStore.Group relation : ManualCreatorMergeStore.load(context)) {
            List<FavoriteGroup> matched = new ArrayList<>();
            for (FavoriteGroup group : groups) {
                if (!java.util.Collections.disjoint(group.members.keySet(), relation.keys)) matched.add(group);
            }
            if (matched.size() < 2) continue;
            LinkedHashMap<String, NativeContentItem> members = new LinkedHashMap<>();
            for (FavoriteGroup group : matched) members.putAll(group.members);
            NativeContentItem name = members.get(relation.nameKey);
            if (name == null) name = matched.get(0).item;
            NativeContentItem artwork = members.get(relation.avatarKey);
            if (artwork == null || artwork.imageUrl.isEmpty()) {
                artwork = null;
                for (NativeContentItem member : members.values()) if (!member.imageUrl.isEmpty()) {
                    artwork = member; break;
                }
            }
            NativeContentItem route = members.get(relation.nameKey);
            if (route == null || route.url.isEmpty()) route = matched.get(0).item;
            NativeContentItem display = new NativeContentItem(route.kind, CreatorIdentity.display(name.title),
                    route.url, artwork == null ? "" : artwork.imageUrl, route.views,
                    artwork == null ? route.uploader : artwork.uploader, route.comments,
                    route.description, route.searchQuery, route.publishedAtMillis);
            groups.removeAll(matched);
            groups.add(new FavoriteGroup(display, members, relation.keys, true));
        }
        List<CreatorAvatarOverrideStore.Override> avatarOverrides =
                CreatorAvatarOverrideStore.load(context);
        for (int i = 0; i < groups.size(); i++) {
            FavoriteGroup group = groups.get(i);
            CreatorAvatarOverrideStore.Override avatar =
                    CreatorAvatarOverrideStore.find(avatarOverrides, group.relationshipKeys);
            if (avatar == null) continue;
            NativeContentItem item = group.item;
            NativeContentItem display = new NativeContentItem(
                    item.kind,
                    item.title,
                    item.url,
                    avatar.imageUrl,
                    item.views,
                    avatar.referer,
                    item.comments,
                    item.description,
                    item.searchQuery,
                    item.publishedAtMillis
            );
            groups.set(i, new FavoriteGroup(
                    display,
                    group.members,
                    group.relationshipKeys,
                    group.manual
            ));
        }
        groups.sort(Comparator.comparing(group -> CreatorNameMatcher.normalized(group.item.title)));
        return groups;
    }

    private static NativeContentItem titled(NativeContentItem item, String title) {
        return new NativeContentItem(item.kind, title, item.url, item.imageUrl, item.views,
                item.uploader, item.comments, item.description, item.searchQuery,
                item.publishedAtMillis);
    }

    /** Backup keeps each raw favorite key, including aliases hidden in the UI. */
    static List<NativeContentItem> rawFavorites(Context context) {
        Map<String, NativeContentItem> exact = new LinkedHashMap<>();
        Map<String, NativeContentItem> artwork = new LinkedHashMap<>();
        for (NativeContentItem item : all(context)) {
            exact.put(key(item), item);
            if (!item.imageUrl.isEmpty() && CreatorIdentity.reviewed(item.title))
                artwork.put(CreatorIdentity.key(item.title), item);
        }
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (String raw : CreatorFavoriteStore.names(context)) {
            NativeContentItem item = exact.get(raw);
            if ((item == null || item.imageUrl.isEmpty()) && CreatorIdentity.reviewed(raw)) {
                NativeContentItem richer = artwork.get(CreatorIdentity.key(raw));
                if (richer != null) item = richer;
            }
            if (item == null) item = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    raw, "", "", "", "", "", "", raw);
            result.add(new NativeContentItem(item.kind, raw, item.url, item.imageUrl,
                    item.views, item.uploader, item.comments, item.description, raw));
        }
        return result;
    }

    static synchronized void remember(Context context, List<NativeContentItem> items) {
        LinkedHashMap<String, NativeContentItem> records = read(context);
        for (NativeContentItem item : items) {
            if (item == null || !item.isCreator() || item.title.trim().isEmpty()) continue;
            String key = key(item);
            NativeContentItem old = records.remove(key);
            records.put(key, old == null ? item : item.merge(old));
        }
        Set<String> favorites = CreatorFavoriteStore.names(context);
        java.util.Iterator<Map.Entry<String, NativeContentItem>> iterator = records.entrySet().iterator();
        while (records.size() > MAX_CACHED && iterator.hasNext()) {
            if (!favorites.contains(CreatorFavoriteStore.key(iterator.next().getValue()))) iterator.remove();
        }
        try {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("items", ContentItemCodec.encodeList(new ArrayList<>(records.values()), 5000).toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private static LinkedHashMap<String, NativeContentItem> read(Context context) {
        LinkedHashMap<String, NativeContentItem> out = new LinkedHashMap<>();
        for (String shelf : SHELVES) readInto(context, shelf, out);
        readInto(context, PREFS, out);
        return out;
    }

    private static void readInto(Context context, String prefs, Map<String, NativeContentItem> out) {
        try {
            JSONArray values = new JSONArray(context.getApplicationContext()
                    .getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("items", "[]"));
            for (NativeContentItem item : ContentItemCodec.decodeList(values, 5000)) {
                if (!item.title.trim().isEmpty() && item.isCreator()) out.put(key(item), item);
            }
        } catch (Exception ignored) { }
    }

    static NativeContentItem fromModel(FapelloRepository.Model model) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, model.name, model.url,
                model.imageUrl, "", model.url, "", "Fapello", model.name);
    }
}
