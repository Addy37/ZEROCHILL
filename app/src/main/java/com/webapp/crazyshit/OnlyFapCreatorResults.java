package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Profile identity and reviewed aliases keep cards stable as live metadata arrives. */
final class OnlyFapCreatorResults {
    private OnlyFapCreatorResults() { }

    static String key(NativeContentItem item) {
        return CreatorNameMatcher.normalized(item.title);
    }

    static List<NativeContentItem> merge(List<NativeContentItem> local,
                                          List<NativeContentItem> live, int limit) {
        LinkedHashMap<String, NativeContentItem> map = new LinkedHashMap<>();
        Map<String, String> profiles = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        if (local != null) for (NativeContentItem item : local) add(map, profiles, names, item);
        if (live != null) for (NativeContentItem item : live) add(map, profiles, names, item);
        ArrayList<NativeContentItem> result = new ArrayList<>(map.values());
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static void add(LinkedHashMap<String, NativeContentItem> map,
                            Map<String, String> profiles, Map<String, String> names,
                            NativeContentItem item) {
        if (item == null || !item.isCreator()) return;
        String name = CreatorIdentity.key(item.title);
        if (name.isEmpty()) return;
        String profile = profile(item);
        String primary = profile.isEmpty() ? null : profiles.get(profile);
        if (primary == null && CreatorIdentity.reviewed(item.title)) primary = "alias:" + name;
        if (primary == null) {
            String byName = names.get(name);
            NativeContentItem existing = map.get(byName);
            if (existing != null && (profile.isEmpty() || profile(existing).isEmpty()
                    || profile.equals(profile(existing)))) primary = byName;
        }
        if (primary == null) primary = profile.isEmpty() ? "name:" + name : "profile:" + profile;
        NativeContentItem previous = map.get(primary);
        String title = CreatorIdentity.display(item.title);
        if (previous == null) {
            map.put(primary, titled(item, title));
            names.putIfAbsent(name, primary);
            if (!profile.isEmpty()) profiles.put(profile, primary);
            return;
        }
        NativeContentItem updated = item.merge(previous);
        String url = FapelloRepository.isModelUrl(previous.url)
                && !FapelloRepository.isModelUrl(item.url) ? previous.url : updated.url;
        map.put(primary, new NativeContentItem(updated.kind,
                CreatorIdentity.reviewed(previous.title) ? CreatorIdentity.display(previous.title) : previous.title, url,
                updated.imageUrl, updated.views, updated.uploader, updated.comments,
                updated.description, previous.searchQuery.isEmpty() ? updated.searchQuery : previous.searchQuery,
                updated.publishedAtMillis));
        if (!profile.isEmpty()) profiles.put(profile, primary);
    }

    private static NativeContentItem titled(NativeContentItem item, String title) {
        return new NativeContentItem(item.kind, title, item.url, item.imageUrl,
                item.views, item.uploader, item.comments, item.description,
                item.searchQuery, item.publishedAtMillis);
    }

    private static String profile(NativeContentItem item) {
        String url = item.url == null ? "" : item.url.trim();
        if (url.isEmpty()) return "";
        if (FapelloRepository.isModelUrl(url)) url = FapelloRepository.canonicalModelUrl(url);
        return url.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }
}
