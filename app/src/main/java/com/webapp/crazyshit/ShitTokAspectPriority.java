package com.webapp.crazyshit;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Hard portrait/square admission followed by source-diverse round-robin ordering. */
final class ShitTokAspectPriority {
    static final int BUCKET_VERTICAL = 0;
    static final int BUCKET_STANDARD = 1;
    static final int BUCKET_WIDE = 2;
    static final int BUCKET_UNKNOWN = 3;

    // Playback observations apply to this exact URL only, never its source or creator.
    private final LinkedHashMap<String, Float> observed = new LinkedHashMap<String, Float>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Float> entry) {
            return size() > 512;
        }
    };

    synchronized void record(NativeContentItem item, float aspectRatio) {
        if (item != null && !item.url.isEmpty()) observed.put(item.url, aspectRatio);
    }

    synchronized List<NativeContentItem> order(List<NativeContentItem> candidates, Random random) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (candidates == null) return result;
        Random rng = random == null ? new Random() : random;
        LinkedHashMap<String, ArrayList<NativeContentItem>> grouped = new LinkedHashMap<>();
        for (NativeContentItem item : candidates) {
            if (item == null || !item.isVideo()) continue;
            float aspect = observed.containsKey(item.url) ? observed.get(item.url) : item.aspectRatioHint;
            if (!ShitTokOrientation.eligible(aspect)) continue;
            grouped.computeIfAbsent(sourceKey(item), ignored -> new ArrayList<>()).add(item);
        }
        ArrayList<SourceQueue> active = new ArrayList<>();
        for (Map.Entry<String, ArrayList<NativeContentItem>> entry : grouped.entrySet()) {
            Collections.shuffle(entry.getValue(), rng);
            active.add(new SourceQueue(entry.getKey(), new ArrayDeque<>(entry.getValue())));
        }
        String lastSource = "";
        while (!active.isEmpty()) {
            Collections.shuffle(active, rng);
            if (active.size() > 1 && active.get(0).key.equals(lastSource)) {
                Collections.swap(active, 0, 1);
            }
            for (SourceQueue source : active) {
                NativeContentItem item = source.items.pollFirst();
                if (item != null) { result.add(item); lastSource = source.key; }
            }
            active.removeIf(source -> source.items.isEmpty());
        }
        return result;
    }

    static String sourceKey(NativeContentItem item) {
        if (item == null) return "unknown";

        String uploader = item.uploader == null ? "" : item.uploader.trim().toLowerCase(Locale.US);
        if (uploader.equals("kaotic")) return "kaotic";
        if (uploader.equals("shit show")) return "shit-show";
        if (uploader.equals("baddiehub")) return "baddiehub";
        if (uploader.equals("onlyhaven")) return "onlyhaven";
        if (uploader.equals("efukt")) return "efukt";
        if (uploader.equals("bunkr")) return "bunkr";

        String description = item.description == null ? "" : item.description.trim();
        if ("Fapello".equalsIgnoreCase(description) || "OnlyFap".equalsIgnoreCase(description)) {
            return "fapello";
        }

        String host = hostFor(item.url);
        if (!host.isEmpty()) return host;
        if (!uploader.isEmpty()) return "uploader:" + uploader;
        return "unknown";
    }

    static int bucket(float aspectRatio) {
        if (!(aspectRatio > 0f) || !Float.isFinite(aspectRatio)) return BUCKET_UNKNOWN;
        if (aspectRatio < 1f) return BUCKET_VERTICAL;
        if (aspectRatio == 1f) return BUCKET_STANDARD;
        return BUCKET_WIDE;
    }

    private static String hostFor(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class SourceQueue {
        final String key;
        final ArrayDeque<NativeContentItem> items;

        SourceQueue(String key, ArrayDeque<NativeContentItem> items) {
            this.key = key;
            this.items = items;
        }
    }
}
