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
            "onlyfap_trending_v4", "fapzone_creator_feed_v3_1", "fapzone_creator_feed_v3_2",
            "fapzone_creator_feed_v3_3", "fapzone_creator_feed_v1_1", "fapzone_creator_feed_v1_2",
            "fapzone_creator_feed_v1_3", FapzoneCreatorRepository.cacheName(FapzoneCreatorRepository.MODE_TOP_50),
            FapzoneCreatorRepository.cacheName(FapzoneCreatorRepository.MODE_NEW),
            FapzoneCreatorRepository.cacheName(FapzoneCreatorRepository.MODE_HOT),
            FapzoneCreatorRepository.cacheName(FapzoneCreatorRepository.MODE_POPULAR)};

    private CreatorCatalog() { }

    static String key(NativeContentItem item) {
        if (item == null) return "";
        String raw = CreatorFavoriteStore.key(item);
        String profile = OnlyFapCreatorResults.profileKey(item.url);
        // Keep retired URL records distinct from legacy name-only records for backup.
        if (profile.isEmpty() && !item.url.isEmpty()) profile = item.url;
        return profile.isEmpty() ? raw : raw + "\n" + profile;
    }

    static List<NativeContentItem> all(Context context) {
        LinkedHashMap<String, NativeContentItem> result = read(context);
        // Older versions saved favorite names only. Keep every one usable after upgrading.
        Set<String> known = new java.util.HashSet<>();
        for (NativeContentItem item : result.values()) known.add(CreatorFavoriteStore.key(item));
        for (String name : CreatorFavoriteStore.names(context)) {
            if (!known.contains(name)) result.put(name, unresolvedFavorite(name));
        }
        List<NativeContentItem> items = new ArrayList<>(result.size());
        for (NativeContentItem item : result.values()) {
            items.add(discardUnverifiedComposite(item));
        }
        return items;
    }

    private static NativeContentItem discardUnverifiedComposite(NativeContentItem item) {
        if (!isUnverifiedComposite(item)) return item;
        // The old display-name merge could combine a profile and artwork from different people.
        // Keep the saved name usable for discovery without reusing the asserted source links.
        return new NativeContentItem(item.kind, item.title, "", "", "", "", "", "", item.searchQuery);
    }

    private static boolean isUnverifiedComposite(NativeContentItem item) {
        return item.creatorProfileUrls.isEmpty() && item.description.contains(" + ");
    }

    static List<NativeContentItem> matching(Context context, String query, boolean favoritesOnly, int limit) {
        Set<String> favorites = CreatorFavoriteStore.names(context);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (favoritesOnly && favorites.isEmpty()) return result;
        if (favoritesOnly) {
            for (FavoriteGroup group : favoriteGroups(context)) {
                NativeContentItem item = group.item;
                if (OnlyFapCreatorResults.rank(item, query) != Integer.MAX_VALUE
                        || group.members.values().stream().anyMatch(member ->
                        OnlyFapCreatorResults.rank(member, query) != Integer.MAX_VALUE))
                    result.add(item);
            }
        } else for (NativeContentItem item : all(context)) {
            if (!WikiFeetRepository.isRetiredItem(item)
                    && OnlyFapCreatorResults.rank(item, query) != Integer.MAX_VALUE) result.add(item);
        }
        Comparator<NativeContentItem> names = Comparator.comparing(
                item -> CreatorNameMatcher.normalized(item.title));
        if (favoritesOnly || query.trim().isEmpty()) result.sort(names);
        else result.sort(Comparator.<NativeContentItem>comparingInt(
                        item -> OnlyFapCreatorResults.rank(item, query))
                .thenComparingInt(item -> favorites.contains(CreatorFavoriteStore.key(item)) ? 0 : 1)
                .thenComparing(names));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    static final class FavoriteGroup {
        final NativeContentItem item;
        final LinkedHashMap<String, NativeContentItem> members;
        final Set<String> relationshipKeys;
        final boolean manual;
        final boolean customAvatar;

        FavoriteGroup(NativeContentItem item, LinkedHashMap<String, NativeContentItem> members,
                      Set<String> relationshipKeys, boolean manual) {
            this(item, members, relationshipKeys, manual, false);
        }

        FavoriteGroup(NativeContentItem item, LinkedHashMap<String, NativeContentItem> members,
                      Set<String> relationshipKeys, boolean manual, boolean customAvatar) {
            this.item = item;
            this.members = members;
            this.relationshipKeys = relationshipKeys;
            this.manual = manual;
            this.customAvatar = customAvatar;
        }
    }

    /** One catalog snapshot and one relationship snapshot per refresh, never per card bind. */
    static List<FavoriteGroup> favoriteGroups(Context context) {
        Set<String> favorites = CreatorFavoriteStore.names(context);
        if (favorites.isEmpty()) return new ArrayList<>();
        Map<String, NativeContentItem> byStoredKey = favoriteCandidates(all(context));
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
            members.put(raw, exact == null ? unresolvedFavorite(raw) : exact);
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
                    route.description, route.searchQuery, route.publishedAtMillis,
                    route.aspectRatioHint).withCreatorIdentity(route.creatorAliases, route.creatorProfileUrls);
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
                    item.publishedAtMillis,
                    item.aspectRatioHint
            ).withCreatorIdentity(item.creatorAliases, item.creatorProfileUrls);
            groups.set(i, new FavoriteGroup(
                    display,
                    group.members,
                    group.relationshipKeys,
                    group.manual,
                    true
            ));
        }
        groups.sort(Comparator.comparing(group -> CreatorNameMatcher.normalized(group.item.title)));
        return groups;
    }

    private static NativeContentItem titled(NativeContentItem item, String title) {
        return new NativeContentItem(item.kind, title, item.url, item.imageUrl, item.views,
                item.uploader, item.comments, item.description, item.searchQuery,
                item.publishedAtMillis, item.aspectRatioHint)
                .withCreatorIdentity(item.creatorAliases, item.creatorProfileUrls);
    }

    private static NativeContentItem unresolvedFavorite(String name) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR,
                name, "", "", "", "", "", "", name);
    }

    private static Map<String, NativeContentItem> favoriteCandidates(List<NativeContentItem> items) {
        Map<String, NativeContentItem> candidates = new LinkedHashMap<>();
        Set<String> conflicts = new java.util.HashSet<>();
        for (NativeContentItem item : items) {
            String raw = CreatorFavoriteStore.key(item);
            NativeContentItem previous = candidates.get(raw);
            if (previous == null || previous.url.isEmpty()) {
                candidates.put(raw, item);
            } else if (!item.url.isEmpty() && !key(item).equals(key(previous))) {
                conflicts.add(raw);
            }
        }
        for (String raw : conflicts) candidates.put(raw, unresolvedFavorite(raw));
        return candidates;
    }

    /** Backup keeps each raw favorite key, including aliases hidden in the UI. */
    static List<NativeContentItem> rawFavorites(Context context) {
        Map<String, NativeContentItem> exact = favoriteCandidates(all(context));
        Map<String, NativeContentItem> artwork = new LinkedHashMap<>();
        for (NativeContentItem item : exact.values()) {
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
            if (item == null) item = unresolvedFavorite(raw);
            result.add(new NativeContentItem(item.kind, raw, item.url, item.imageUrl,
                    item.views, item.uploader, item.comments, item.description, raw)
                    .withCreatorIdentity(item.creatorAliases, item.creatorProfileUrls));
        }
        return result;
    }

    static synchronized void remember(Context context, List<NativeContentItem> items) {
        LinkedHashMap<String, NativeContentItem> records = read(context);
        for (NativeContentItem item : items) {
            if (item == null || !item.isCreator() || item.title.trim().isEmpty()) continue;
            String key = key(item);
            NativeContentItem old = records.remove(key);
            records.put(key, old == null || isUnverifiedComposite(old)
                    ? item : OnlyFapCreatorResults.combine(old, item));
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
                if (!item.title.trim().isEmpty() && item.isCreator()) {
                    String key = key(item);
                    NativeContentItem previous = out.get(key);
                    if (previous == null || previous.creatorProfileUrls.isEmpty()) {
                        out.put(key, item);
                    } else if (!item.creatorProfileUrls.isEmpty()) {
                        out.put(key, OnlyFapCreatorResults.combine(previous, item));
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    static NativeContentItem fromModel(FapelloRepository.Model model) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, model.name, model.url,
                model.imageUrl, "", model.url, "", "Fapello", model.name)
                .withCreatorIdentity(java.util.Arrays.asList(model.username, model.verifiedAlias),
                        java.util.Collections.singletonList(model.url));
    }
}
