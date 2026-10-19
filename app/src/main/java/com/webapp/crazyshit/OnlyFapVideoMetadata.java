package com.webapp.crazyshit;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional creator context in existing saved records. Storage reads belong on workers. */
final class OnlyFapVideoMetadata {
    static final String EXTRA_CONTENT = "onlyfap_video_content";
    private OnlyFapVideoMetadata() { }

    static boolean applies(NativeContentItem item) {
        return item != null && (FapelloRepository.isFapelloUrl(item.url)
                || OnlyHavenRepository.isOnlyHavenUrl(item.url)
                || BunkrRepository.isBunkrUrl(item.url)
                || item.description.toLowerCase(java.util.Locale.ROOT).contains("onlyfap")
                || item.description.toLowerCase(java.util.Locale.ROOT).contains("onlyhaven")
                || !ShitTokCreatorMetadata.knownProfileUrl(item).isEmpty());
    }

    static NativeContentItem decode(JSONObject object, String url) {
        if (object == null) return null;
        NativeContentItem item = ContentItemCodec.decode(object);
        return url.equals(item.url) && NativeContentItem.KIND_MEDIA.equals(item.kind)
                && applies(item) ? item : null;
    }

    static NativeContentItem merge(NativeContentItem current, NativeContentItem saved) {
        if (current == null) return saved;
        if (saved == null || !current.url.equals(saved.url)) return current;
        if (!applies(current) && !applies(saved)) return current;
        if (ShitTokCreatorMetadata.knownProfileUrl(current).isEmpty()
                && !ShitTokCreatorMetadata.knownProfileUrl(saved).isEmpty()) return saved.merge(current);
        return current.merge(saved);
    }

    static NativeContentItem creator(NativeContentItem item) {
        if (!applies(item)) return null;
        if (!FapelloRepository.isFapelloUrl(item.url) && !FapelloRepository.isModelUrl(item.uploader)
                && (item.searchQuery.trim().isEmpty() || !item.searchQuery.trim().equals(
                ShitTokCreatorMetadata.creatorName(item)))) return null;
        NativeContentItem creator = ShitTokCreatorMetadata.creatorIdentity(null, item);
        if (creator != null && !creator.title.regionMatches(true, 0, "http", 0, 4)
                && creator.title.codePoints().anyMatch(Character::isLetter)) return creator;
        // Fapello gallery items use their exact model URL as uploader and model name as title.
        String profile = ShitTokCreatorMetadata.knownProfileUrl(item);
        String name = item.title.replaceFirst("\\s+#\\d+$", "").trim();
        if (FapelloRepository.isModelUrl(item.uploader) && !profile.isEmpty()
                && !name.isEmpty() && !name.matches("(?i)(?:OnlyFap|Fapello|Video)(?: video)?")) {
            return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, profile,
                    "", "", profile, "", "Fapello", name);
        }
        return null;
    }

    static NativeContentItem creator(Context context, NativeContentItem item) {
        NativeContentItem creator = creator(item);
        String profile = ShitTokCreatorMetadata.knownProfileUrl(item);
        if (context != null && creator != null) {
            for (NativeContentItem candidate : CreatorCatalog.matching(context, creator.title, false, 20)) {
                if (!profile.isEmpty() && profile.equals(candidate.url)) return candidate.merge(creator);
            }
        }
        return creator;
    }

    static String displayTitle(NativeContentItem item) {
        NativeContentItem creator = creator(item);
        return creator == null ? item.title : creator.title;
    }

    static void put(Intent intent, NativeContentItem item) {
        if (!applies(item)) return;
        try { intent.putExtra(EXTRA_CONTENT, ContentItemCodec.encode(item).toString()); }
        catch (Exception ignored) { }
    }

    static NativeContentItem fromIntent(Intent intent, String url) {
        try { return decode(new JSONObject(intent.getStringExtra(EXTRA_CONTENT)), url); }
        catch (Exception ignored) { return null; }
    }

    static Map<String, NativeContentItem> snapshot(Context context) {
        Map<String, NativeContentItem> items = new LinkedHashMap<>();
        for (NativeContentItem item : SocialContentContextStore.knownVideos(context,
                ZeroChillSessionStore.currentUserId(context)).values()) add(items, item);
        for (NativeContentItem item : VideoDownloadStore.savedContent(context)) add(items, item);
        return items;
    }

    private static void add(Map<String, NativeContentItem> items, NativeContentItem item) {
        if (applies(item) && NativeContentItem.KIND_MEDIA.equals(item.kind))
            items.put(item.url, merge(item, items.get(item.url)));
    }

    static NativeContentItem restore(Context context, NativeContentItem item) {
        return applies(item) ? merge(item, snapshot(context).get(item.url)) : item;
    }

    static NativeContentItem basic(String title, String url, String image) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, title, url, image, "", "", "");
    }
}
