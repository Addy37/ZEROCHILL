package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.*;

 /** Local, optional membership over the existing media favorites. No duplicate media records. */
final class MediaCollectionStore {
    static final String KEY = "collections_v1";
    static final int MAX_COLLECTIONS = 64, MAX_NAME = 80, MAX_MEMBERSHIPS = 10000;
    private MediaCollectionStore() { }

    static final class Group {
        final String id, name;
        final LinkedHashSet<String> urls;
        Group(String id, String name, Collection<String> urls) {
            this.id = id; this.name = name; this.urls = new LinkedHashSet<>(urls);
        }
    }

    static final class Preview {
        final Group group;
        final int count;
        final List<String> images;
        Preview(Group group, int count, List<String> images) {
            this.group = group; this.count = count; this.images = images;
        }
    }

    static List<Group> load(Context context) {
        synchronized (FavoriteStore.class) {
            return decode(context.getSharedPreferences("watch_later", Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
        }
    }

    static List<Group> decode(String raw) {
        List<Group> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            validate(array);
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                JSONArray members = value.getJSONArray("urls");
                List<String> urls = new ArrayList<>();
                for (int j = 0; j < members.length(); j++) urls.add(members.getString(j));
                result.add(new Group(value.getString("id"), value.getString("name"), urls));
            }
        } catch (Exception ignored) { }
        return result;
    }

    static JSONArray encode(List<Group> groups) throws Exception {
        JSONArray result = new JSONArray();
        for (Group group : groups)
            result.put(new JSONObject().put("id", group.id).put("name", group.name)
                    .put("urls", new JSONArray(group.urls)));
        validate(result);
        return result;
    }

    static void validate(JSONArray array) throws Exception {
        if (array.length() > MAX_COLLECTIONS) throw new IOException("Too many collections.");
        Set<String> ids = new HashSet<>();
        int memberships = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);
            String id = value.getString("id"), name = value.getString("name");
            if (!(value.get("id") instanceof String) || !id.matches("[0-9a-fA-F-]{36}") || !ids.add(id)
                    || !(value.get("name") instanceof String) || name.trim().isEmpty() || name.length() > MAX_NAME)
                throw new IOException("Invalid collection.");
            JSONArray urls = value.getJSONArray("urls");
            if ((memberships += urls.length()) > MAX_MEMBERSHIPS) throw new IOException("Too many collection items.");
            for (int j = 0; j < urls.length(); j++) {
                if (!(urls.get(j) instanceof String)) throw new IOException("Invalid collection link.");
                String url = urls.getString(j);
                URI uri = new URI(url);
                if (url.length() > 4096 || uri.getHost() == null || uri.getUserInfo() != null
                        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                    throw new IOException("Invalid collection link.");
            }
        }
    }

    private static void save(Context context, List<Group> groups) throws Exception {
        context.getSharedPreferences("watch_later", Context.MODE_PRIVATE).edit()
                .putString(KEY, encode(groups).toString()).apply();
    }

    static Group find(Context context, String id) {
        for (Group group : load(context)) if (group.id.equals(id)) return group;
        return null;
    }

    static String create(Context context, String name) throws Exception {
        synchronized (FavoriteStore.class) {
            List<Group> groups = load(context);
            String id = UUID.randomUUID().toString();
            groups.add(new Group(id, name.trim(), Collections.emptyList()));
            save(context, groups);
            return id;
        }
    }

    static void rename(Context context, String id, String name) throws Exception {
        synchronized (FavoriteStore.class) {
            List<Group> groups = load(context);
            for (int i = 0; i < groups.size(); i++) {
                Group old = groups.get(i);
                if (old.id.equals(id)) groups.set(i, new Group(id, name.trim(), old.urls));
            }
            save(context, groups);
        }
    }

    static void delete(Context context, String id) throws Exception {
        synchronized (FavoriteStore.class) {
            List<Group> groups = load(context);
            groups.removeIf(group -> group.id.equals(id));
            save(context, groups);
        }
    }

    static void remove(Context context, String id, String url) throws Exception {
        synchronized (FavoriteStore.class) {
            List<Group> groups = load(context);
            for (Group group : groups) if (group.id.equals(id)) group.urls.remove(url);
            save(context, groups);
        }
    }

    static void assign(Context context, NativeContentItem item, String galleryKey, Set<String> selected) throws Exception {
        synchronized (FavoriteStore.class) {
            List<Group> groups = load(context);
            boolean any = false;
            for (Group group : groups) {
                if (selected.contains(group.id)) { group.urls.add(item.url); any = true; }
                else group.urls.remove(item.url);
            }
            // Validate before modifying either saved list.
            encode(groups);
            if (any) {
                boolean already = false;
                for (FavoriteStore.Item saved : FavoriteStore.mediaFavorites(context))
                    if (saved.url.equals(item.url)) { already = true; break; }
                if (!already) FavoriteStore.setMediaFavorite(context, item, true, galleryKey);
            }
            save(context, groups);
        }
    }

    static List<FavoriteStore.Item> items(Context context, String id) {
        synchronized (FavoriteStore.class) {
            Group group = find(context, id);
            List<FavoriteStore.Item> items = FavoriteStore.mediaFavorites(context);
            if (group == null) items.clear();
            else items.removeIf(item -> !group.urls.contains(item.url));
            return items;
        }
    }

    static List<Preview> previews(Context context) {
        synchronized (FavoriteStore.class) {
            List<FavoriteStore.Item> favorites = FavoriteStore.mediaFavorites(context);
            List<Preview> result = new ArrayList<>();
            for (Group group : load(context)) {
                int count = 0;
                List<String> images = new ArrayList<>();
                for (FavoriteStore.Item item : favorites) if (group.urls.contains(item.url)) {
                    count++;
                    if (images.size() < 3 && item.content != null && !item.content.imageUrl.isEmpty())
                        images.add(item.content.imageUrl);
                }
                result.add(new Preview(group, count, images));
            }
            return result;
        }
    }

    // Use the same SharedPreferences editor as FavoriteStore, so unfavoriting cannot leave stale membership.
    static void prune(Context context, android.content.SharedPreferences.Editor editor, List<FavoriteStore.Item> favorites) {
        List<Group> groups = load(context);
        Set<String> allowed = new HashSet<>();
        for (FavoriteStore.Item item : favorites) if (item.mediaFavorite) allowed.add(item.url);
        boolean changed = false;
        for (Group group : groups) changed |= group.urls.retainAll(allowed);
        if (changed) try { editor.putString(KEY, encode(groups).toString()); } catch (Exception ignored) { }
    }

    static JSONArray merge(JSONArray incoming, List<Group> local, Set<String> allowed) throws Exception {
        LinkedHashMap<String, Group> merged = new LinkedHashMap<>();
        if (incoming != null) {
            validate(incoming);
            for (Group group : decode(incoming.toString())) merged.put(group.id, group);
        }
        for (Group group : local) {
            Group previous = merged.get(group.id);
            Group copy = new Group(group.id, group.name, group.urls);
            if (previous != null) copy.urls.addAll(previous.urls);
            merged.put(copy.id, copy);
        }
        for (Group group : merged.values()) group.urls.retainAll(allowed);
        return encode(new ArrayList<>(merged.values()));
    }
}
