package com.webapp.crazyshit;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Combines creator cards using profile identity and explicitly reviewed aliases. */
final class OnlyFapCreatorResults {
    private OnlyFapCreatorResults() { }

    static String key(NativeContentItem item) {
        String profile = profileKey(item.url);
        if (!profile.isEmpty()) return "profile:" + profile;
        return "name:" + CreatorIdentity.key(item.title)
                + (item.creatorAliases.isEmpty() ? "" : ":" + item.creatorAliases.get(0));
    }

    static int rank(NativeContentItem item, String query) {
        int rank = Math.min(CreatorNameMatcher.rank(item.title, query),
                CreatorNameMatcher.rank(item.searchQuery, query));
        for (String alias : item.creatorAliases) {
            rank = Math.min(rank, CreatorNameMatcher.rank(alias, query));
        }
        return rank;
    }

    static List<NativeContentItem> merge(List<NativeContentItem> local,
                                         List<NativeContentItem> live, int limit) {
        ArrayList<NativeContentItem> input = new ArrayList<>();
        if (local != null) input.addAll(local);
        if (live != null) input.addAll(live);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        ArrayList<Set<String>> profiles = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        Map<String, Set<String>> profilesByName = new HashMap<>();
        for (NativeContentItem item : input) {
            if (!usable(item)) continue;
            String profile = profileKey(item.url);
            if (!profile.isEmpty()) {
                profilesByName.computeIfAbsent(CreatorIdentity.key(item.title),
                        unused -> new LinkedHashSet<>()).add(profile);
            }
        }
        for (NativeContentItem item : input) {
            if (!usable(item)) continue;
            Set<String> itemProfiles = profileKeys(item);
            String name = CreatorIdentity.key(item.title);
            Set<String> namedProfiles = profilesByName.get(name);
            boolean uniqueName = namedProfiles == null || namedProfiles.size() <= 1;
            boolean reviewedName = CreatorIdentity.reviewed(item.title);
            int match = -1;
            for (int i = 0; i < result.size(); i++) {
                Set<String> existingProfiles = profiles.get(i);
                boolean sameName = name.equals(names.get(i));
                boolean reviewed = sameName && reviewedName
                        && compatibleSources(existingProfiles, itemProfiles);
                boolean placeholder = sameName && uniqueName
                        && canEnrichPlaceholder(result.get(i), existingProfiles, item, itemProfiles);
                boolean isolatedPawchive = PawchiveRepository.isProfile(item.url)
                        || PawchiveRepository.isProfile(result.get(i).url);
                if (overlaps(existingProfiles, itemProfiles) || (!isolatedPawchive && (reviewed || placeholder))) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                result.add(PawchiveRepository.isProfile(item.url) ? item : titled(item, CreatorIdentity.display(item.title)));
                profiles.add(itemProfiles);
                names.add(name);
            } else {
                NativeContentItem previous = result.get(match);
                NativeContentItem combined = combine(previous, item);
                result.set(match, combined);
                profiles.set(match, profileKeys(combined));
            }
        }
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static boolean canEnrichPlaceholder(NativeContentItem previous, Set<String> existingProfiles,
                                                NativeContentItem item, Set<String> itemProfiles) {
        if (!existingProfiles.isEmpty() && !itemProfiles.isEmpty()) return false;
        NativeContentItem placeholder = existingProfiles.isEmpty() ? previous : item;
        NativeContentItem other = existingProfiles.isEmpty() ? item : previous;
        if (placeholder.creatorAliases.isEmpty()) return true;
        for (String alias : placeholder.creatorAliases) {
            for (String otherAlias : other.creatorAliases) {
                if (CreatorGalleryMatch.exactName(alias, otherAlias)) return true;
            }
        }
        return false;
    }

    private static boolean usable(NativeContentItem item) {
        return item != null && item.isCreator() && !WikiFeetRepository.isRetiredItem(item)
                && !CreatorIdentity.key(item.title).isEmpty();
    }

    /** Both inputs have already been established as the same creator. */
    static NativeContentItem combine(NativeContentItem first, NativeContentItem second) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>(first.creatorAliases);
        aliases.addAll(second.creatorAliases);
        LinkedHashSet<String> profiles = new LinkedHashSet<>(first.creatorProfileUrls);
        profiles.addAll(second.creatorProfileUrls);
        if (!first.url.isEmpty()) profiles.add(first.url);
        if (!second.url.isEmpty()) profiles.add(second.url);

        NativeContentItem updated = second.merge(first);
        NativeContentItem route = first.url.isEmpty()
                || FapelloRepository.isModelUrl(second.url) && !FapelloRepository.isModelUrl(first.url)
                ? second : first;
        NativeContentItem artwork = second.imageUrl.isEmpty() ? first : second;
        return new NativeContentItem(updated.kind, CreatorIdentity.display(first.title), route.url,
                artwork.imageUrl, updated.views, artwork.uploader, updated.comments, updated.description,
                route.searchQuery, updated.publishedAtMillis, updated.aspectRatioHint)
                .withCreatorIdentity(new ArrayList<>(aliases), new ArrayList<>(profiles));
    }

    private static NativeContentItem titled(NativeContentItem item, String title) {
        if (title.equals(item.title)) return item;
        return new NativeContentItem(item.kind, title, item.url, item.imageUrl,
                item.views, item.uploader, item.comments, item.description,
                item.searchQuery, item.publishedAtMillis, item.aspectRatioHint)
                .withCreatorIdentity(item.creatorAliases, item.creatorProfileUrls);
    }

    private static Set<String> profileKeys(NativeContentItem item) {
        Set<String> result = new LinkedHashSet<>();
        String primary = profileKey(item.url);
        if (!primary.isEmpty()) result.add(primary);
        for (String url : item.creatorProfileUrls) {
            String profile = profileKey(url);
            if (!profile.isEmpty()) result.add(profile);
        }
        return result;
    }

    private static boolean overlaps(Set<String> first, Set<String> second) {
        for (String profile : first) if (second.contains(profile)) return true;
        return false;
    }

    /** A reviewed name cannot override two different profile IDs on the same provider. */
    private static boolean compatibleSources(Set<String> first, Set<String> second) {
        for (String left : first) {
            int boundary = left.indexOf('/');
            String provider = boundary < 0 ? left : left.substring(0, boundary);
            for (String right : second) {
                if (!left.equals(right) && (right.startsWith(provider + "/") || right.equals(provider))) {
                    return false;
                }
            }
        }
        return true;
    }

    static String profileKey(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty() || WikiFeetRepository.isRetiredPageUrl(url)) return "";
        if (FapelloRepository.isModelUrl(url)) url = FapelloRepository.canonicalModelUrl(url);
        try {
            URI parsed = new URI(url);
            if (parsed.getHost() == null || !"https".equalsIgnoreCase(parsed.getScheme())) return "";
            String host = parsed.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            String path = parsed.getRawPath();
            return host + (path == null ? "" : path.replaceAll("/+$", ""));
        } catch (java.net.URISyntaxException ignored) {
            return "";
        }
    }
}
