package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        Map<String, String> identityToPrimary = new HashMap<>();
        Map<String, String> sourceToPrimary = new HashMap<>();
        if (local != null) for (NativeContentItem item : local) {
            add(map, identityToPrimary, sourceToPrimary, item, aliases);
        }
        if (live != null) for (NativeContentItem item : live) {
            add(map, identityToPrimary, sourceToPrimary, item, aliases);
        }
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (Candidate candidate : map.values()) result.add(candidate.item);
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static void add(LinkedHashMap<String, Candidate> map,
                            Map<String, String> identityToPrimary,
                            Map<String, String> sourceToPrimary,
                            NativeContentItem item,
                            BundledCreatorIndex aliases) {
        if (item == null || !item.isCreator()) return;

        String identity = identityKey(item, aliases);
        if (identity.isEmpty()) return;
        String source = sourceKey(item);

        // A stable source profile is the strongest identity signal. Name identity is the fallback
        // for old favorites that were saved before profile URLs were persisted.
        String primary = source.isEmpty() ? null : sourceToPrimary.get(source);
        if (primary == null) primary = identityToPrimary.get(identity);

        String displayName = aliases == null ? item.title : aliases.canonicalName(item.title);
        NativeContentItem incoming = withTitle(item, displayName);
        boolean exact = aliases == null || aliases.isCanonicalSpelling(item.title);

        if (primary == null) {
            primary = identity;
            map.put(primary, new Candidate(incoming, exact));
            identityToPrimary.put(identity, primary);
            if (!source.isEmpty()) sourceToPrimary.put(source, primary);
            return;
        }

        Candidate previous = map.get(primary);
        if (previous == null) {
            map.put(primary, new Candidate(incoming, exact));
            previous = map.get(primary);
        } else if (aliases == null) {
            // Preserve the original name-based merge behavior for callers that do not opt into
            // the bundled identity index.
            NativeContentItem updated = incoming.merge(previous.item);
            String url = FapelloRepository.isModelUrl(previous.item.url)
                    && !FapelloRepository.isModelUrl(incoming.url) ? previous.item.url : updated.url;
            map.put(primary, new Candidate(new NativeContentItem(updated.kind, previous.item.title, url,
                    updated.imageUrl, updated.views, updated.uploader, updated.comments,
                    updated.description, updated.searchQuery, updated.publishedAtMillis), true));
        } else {
            // Keep a known canonical name when one exists. For unknown identities, newer live
            // metadata may supply the cleaner display name and avatar. Preserve the established
            // search key so existing favorites continue to match after the records collapse.
            NativeContentItem merged;
            boolean mergedExact = previous.exact || exact;
            String stableSearchQuery = previous.item.searchQuery;
            if (previous.exact && !exact) {
                merged = mergePreferred(previous.item, incoming, stableSearchQuery);
            } else {
                merged = mergePreferred(incoming, previous.item, stableSearchQuery);
            }
            map.put(primary, new Candidate(merged, mergedExact));
        }

        identityToPrimary.put(identity, primary);
        if (!source.isEmpty()) sourceToPrimary.put(source, primary);
    }

    private static String identityKey(NativeContentItem item, BundledCreatorIndex aliases) {
        String title = item.title == null ? "" : item.title;
        if (aliases != null) {
            String normalized = CreatorNameMatcher.normalized(title);
            String canonical = aliases.canonicalKey(title);
            if (!canonical.isEmpty() && !canonical.equals(normalized)) {
                return CreatorNameMatcher.identity(canonical);
            }
        }
        String identity = CreatorNameMatcher.identity(title);
        if (identity.isEmpty()) identity = CreatorNameMatcher.identity(item.searchQuery);
        return identity;
    }

    private static String sourceKey(NativeContentItem item) {
        String url = item.url == null ? "" : item.url.trim();
        if (url.isEmpty()) return "";
        if (FapelloRepository.isModelUrl(url)) {
            String canonical = FapelloRepository.canonicalModelUrl(url);
            if (!canonical.isEmpty()) return "fapello:" + canonical.toLowerCase(Locale.ROOT);
        }
        return "url:" + url.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private static NativeContentItem withTitle(NativeContentItem item, String title) {
        String resolved = title == null || title.trim().isEmpty() ? item.title : title.trim();
        if (resolved.equals(item.title)) return item;
        return new NativeContentItem(item.kind, resolved, item.url, item.imageUrl, item.views,
                item.uploader, item.comments, item.description, item.searchQuery,
                item.publishedAtMillis);
    }

    private static NativeContentItem mergePreferred(NativeContentItem preferred,
                                                     NativeContentItem fallback,
                                                     String stableSearchQuery) {
        NativeContentItem updated = preferred.merge(fallback);
        String url = FapelloRepository.isModelUrl(fallback.url)
                && !FapelloRepository.isModelUrl(preferred.url) ? fallback.url : updated.url;
        String searchQuery = stableSearchQuery == null || stableSearchQuery.trim().isEmpty()
                ? updated.searchQuery : stableSearchQuery;
        return new NativeContentItem(updated.kind, preferred.title, url,
                updated.imageUrl, updated.views, updated.uploader, updated.comments,
                updated.description, searchQuery, updated.publishedAtMillis);
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
