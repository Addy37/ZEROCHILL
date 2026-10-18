package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;

/** Bounded rotating favorite-profile polling. No gallery session/player/cache warm-up is created. */
final class FavoriteCreatorUpdateScanner {
    static final int MAX_PROFILES_PER_CHECK = 4;
    static final int MAX_MEDIA_PER_PROFILE = 40;
    private static final int MAX_SEEN = 160;
    private FavoriteCreatorUpdateScanner() { }

    static final class Profile {
        final String source;
        final String name;
        final NativeContentItem item;
        final String watchKey;
        Profile(String source, NativeContentItem item) {
            this.source = source;
            this.item = item;
            name = item.title.isEmpty() ? item.searchQuery : item.title;
            watchKey = "favorite_posts_v2:" + source + ":" + CreatorFavoriteStore.key(item);
        }
        List<NativeContentItem> fetch(Context context) throws IOException {
            List<NativeContentItem> result;
            if ("fapello".equals(source)) {
                FapelloRepository repository = new FapelloRepository();
                FapelloRepository.Model model = null;
                if (FapelloRepository.isModelUrl(item.url)) model = new FapelloRepository.Model(name, item.url, item.imageUrl);
                else for (FapelloRepository.Model candidate : repository.searchConfirmedModels(context, name, 2)) {
                    if (CreatorNameMatcher.normalized(name).equals(CreatorNameMatcher.normalized(candidate.name))
                            || CreatorIdentity.sameReviewedIdentity(name, candidate.name)) { model = candidate; break; }
                }
                if (model == null) throw new IOException("Favorite creator profile not found");
                result = repository.fetchModelMedia(context, model, 1);
            } else if ("onlyhaven".equals(source)) {
                Matcher profile = RemoteSourceConfigManager.snapshot().onlyHaven.creatorUrlPattern.matcher(item.url);
                if (!profile.find() || profile.groupCount() < 2) throw new IOException("Invalid OnlyHaven creator profile");
                result = new OnlyHavenRepository().fetchCreatorMedia(context, new OnlyHavenRepository.Creator(
                        profile.group(1), profile.group(2), name, item.url, item.imageUrl), 1, MAX_MEDIA_PER_PROFILE);
            } else if ("bunkr".equals(source)) {
                BunkrRepository repository = new BunkrRepository();
                String album = item.url;
                if (!BunkrRepository.isAlbumUrl(album)) {
                    album = "";
                    for (NativeContentItem candidate : repository.searchAlbums(context, name, 1)) {
                        String creator = NotificationCoordinator.onlyFapCreatorName(
                                new NotificationCoordinator.ExperienceItem("bunkr", candidate));
                        if (CreatorNameMatcher.normalized(name).equals(CreatorNameMatcher.normalized(creator))) {
                            album = candidate.url; break;
                        }
                    }
                }
                if (album.isEmpty()) throw new IOException("Favorite creator album not found");
                result = repository.fetchAlbum(context, album, 1);
            } else {
                WikiFeetRepository.Site site = "wikifeetx".equals(source)
                        ? WikiFeetRepository.Site.WIKIFEET_X : WikiFeetRepository.Site.WIKIFEET;
                result = new WikiFeetRepository().fetchCreatorMedia(context,
                        new WikiFeetRepository.Creator(site, name, item.url, item.imageUrl, 0), 1, MAX_MEDIA_PER_PROFILE);
            }
            ArrayList<NativeContentItem> media = new ArrayList<>();
            for (NativeContentItem content : result) {
                if (content == null || !(content.isImage() || content.isVideo()) || content.url.isEmpty()) continue;
                media.add(content);
                if (media.size() == MAX_MEDIA_PER_PROFILE) break;
            }
            return media;
        }
    }

    static List<Profile> plan(Context context, SharedPreferences state) {
        LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
        for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(context)) {
            for (NativeContentItem member : group.members.values()) {
                String source = profileSource(member);
                if (source.isEmpty()) continue;
                Profile profile = new Profile(source, member);
                profiles.putIfAbsent(profile.watchKey, profile);
            }
        }
        ArrayList<Profile> ordered = new ArrayList<>(profiles.values());
        ordered.sort(Comparator.comparing(profile -> profile.watchKey));
        if (ordered.isEmpty()) return ordered;
        int start = Math.floorMod(state.getInt("favorite_profile_poll_cursor_v2", 0), ordered.size());
        ArrayList<Profile> selected = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_PROFILES_PER_CHECK, ordered.size()); i++)
            selected.add(ordered.get((start + i) % ordered.size()));
        state.edit().putInt("favorite_profile_poll_cursor_v2", (start + selected.size()) % ordered.size()).apply();
        return selected;
    }

    private static String profileSource(NativeContentItem item) {
        if (FapelloRepository.isModelUrl(item.url) || item.url.isEmpty()) return "fapello";
        if (OnlyHavenRepository.isOnlyHavenUrl(item.url)) return "onlyhaven";
        if (BunkrRepository.isBunkrUrl(item.url) || BunkrRepository.isBalbumsUrl(item.url)) return "bunkr";
        for (WikiFeetRepository.Site site : WikiFeetRepository.Site.values())
            if (WikiFeetRepository.isProfileUrl(item.url, site)) return site.id;
        return "";
    }

    static NotificationCoordinator.SourceAlert detect(SharedPreferences state, Profile profile,
            List<NativeContentItem> items) {
        LinkedHashMap<String, NativeContentItem> current = new LinkedHashMap<>();
        if (items != null) for (NativeContentItem item : items) {
            if (item == null || !(item.isImage() || item.isVideo()) || item.url.isEmpty()) continue;
            current.putIfAbsent(CreatorUpdateIdentity.key(profile.source, item), item);
            if (current.size() == MAX_MEDIA_PER_PROFILE) break;
        }
        if (current.isEmpty()) return null; // A failure/empty response never overwrites a good baseline.
        String seenKey = "seen_ids_" + profile.watchKey;
        LinkedHashSet<String> previous = new LinkedHashSet<>();
        String encoded = state.getString(seenKey, "");
        if (encoded != null) for (String line : encoded.split("\n")) if (!line.isEmpty()) previous.add(line);
        boolean initialized = state.getBoolean("seen_initialized_" + profile.watchKey, false);
        ArrayList<NativeContentItem> added = new ArrayList<>();
        if (initialized) for (java.util.Map.Entry<String, NativeContentItem> entry : current.entrySet())
            if (!previous.contains(entry.getKey())) added.add(entry.getValue());
        LinkedHashSet<String> combined = new LinkedHashSet<>(current.keySet());
        combined.addAll(previous);
        StringBuilder saved = new StringBuilder();
        int count = 0;
        for (String id : combined) {
            if (count++ == MAX_SEEN) break;
            if (saved.length() > 0) saved.append('\n');
            saved.append(id);
        }
        state.edit().putString(seenKey, saved.toString())
                .putBoolean("seen_initialized_" + profile.watchKey, true).apply();
        return added.isEmpty() ? null : new NotificationCoordinator.SourceAlert(
                profile.source, profile.source, added, profile.name);
    }
}
