package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Creator identity merging keeps one stable card while richer source metadata arrives. */
final class OnlyFapCreatorResults {
    private OnlyFapCreatorResults() { }

    static String key(NativeContentItem item) {
        return CreatorNameMatcher.normalized(item.title);
    }

    static List<NativeContentItem> merge(List<NativeContentItem> local,
                                          List<NativeContentItem> live, int limit) {
        return merge(local, live, limit, null);
    }

    static List<NativeContentItem> merge(List<NativeContentItem> local,
                                          List<NativeContentItem> live, int limit,
                                          BundledCreatorIndex aliases) {
        LinkedHashMap<String, Candidate> map = new LinkedHashMap<>();
        if (local != null) for (NativeContentItem item : local) add(map, item, aliases);
        if (live != null) for (NativeContentItem item : live) add(map, item, aliases);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (Candidate candidate : map.values()) result.add(candidate.item);
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static void add(LinkedHashMap<String, Candidate> map, NativeContentItem item,
                            BundledCreatorIndex aliases) {
        if (item == null || !item.isCreator()) return;
        String key = aliases == null ? key(item) : aliases.canonicalKey(item.title);
        if (key.isEmpty()) return;

        String displayName = aliases == null ? item.title : aliases.canonicalName(item.title);
        NativeContentItem incoming = withTitle(item, displayName);
        boolean exact = aliases == null || aliases.isCanonicalSpelling(item.title);

        Candidate previous = map.get(key);
        if (previous == null) {
            map.put(key, new Candidate(incoming, exact));
            return;
        }

        // Preserve the original name-based merge behavior for callers that do not opt into the
        // bundled identity index.
        if (aliases == null) {
            NativeContentItem updated = incoming.merge(previous.item);
            String url = FapelloRepository.isModelUrl(previous.item.url)
                    && !FapelloRepository.isModelUrl(incoming.url) ? previous.item.url : updated.url;
            map.put(key, new Candidate(new NativeContentItem(updated.kind, previous.item.title, url,
                    updated.imageUrl, updated.views, updated.uploader, updated.comments,
                    updated.description, updated.searchQuery, updated.publishedAtMillis), true));
            return;
        }

        // Once an exact canonical spelling arrives, keep its identity and profile URL while still
        // filling any missing metadata from truncated or typo variants. Until then, newer source
        // metadata keeps the same precedence as before.
        NativeContentItem merged;
        boolean mergedExact = previous.exact || exact;
        if (previous.exact && !exact) merged = mergePreferred(previous.item, incoming);
        else merged = mergePreferred(incoming, previous.item);
        map.put(key, new Candidate(merged, mergedExact));
    }

    private static NativeContentItem withTitle(NativeContentItem item, String title) {
        String resolved = title == null || title.trim().isEmpty() ? item.title : title.trim();
        if (resolved.equals(item.title)) return item;
        return new NativeContentItem(item.kind, resolved, item.url, item.imageUrl, item.views,
                item.uploader, item.comments, item.description, item.searchQuery,
                item.publishedAtMillis);
    }

    private static NativeContentItem mergePreferred(NativeContentItem preferred,
                                                     NativeContentItem fallback) {
        NativeContentItem updated = preferred.merge(fallback);
        String url = FapelloRepository.isModelUrl(fallback.url)
                && !FapelloRepository.isModelUrl(preferred.url) ? fallback.url : updated.url;
        return new NativeContentItem(updated.kind, preferred.title, url,
                updated.imageUrl, updated.views, updated.uploader, updated.comments,
                updated.description, updated.searchQuery, updated.publishedAtMillis);
    }

    private static final class Candidate {
        final NativeContentItem item;
        final boolean exact;

        Candidate(NativeContentItem item, boolean exact) {
            this.item = item;
            this.exact = exact;
        }
    }
}
