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

/** Portrait/square admission followed by smart ranking with source diversity. */
final class ShitTokAspectPriority {
    static final int BUCKET_VERTICAL = 0;
    static final int BUCKET_STANDARD = 1;
    static final int BUCKET_WIDE = 2;
    static final int BUCKET_UNKNOWN = 3;
    private static final int EXPLORATION_INTERVAL = 4;

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
        ShitTokCommunityPopularity.refreshAsync();

        LinkedHashMap<String, ArrayList<NativeContentItem>> grouped = new LinkedHashMap<>();
        for (NativeContentItem item : candidates) {
            if (item == null || !item.isVideo()) continue;
            float aspect = observed.containsKey(item.url) ? observed.get(item.url) : item.aspectRatioHint;
            if (!ShitTokOrientation.eligible(aspect)) continue;
            ShitTokSmartRanker.register(item);
            grouped.computeIfAbsent(sourceKey(item), ignored -> new ArrayList<>()).add(item);
        }

        ArrayList<SourceQueue> active = new ArrayList<>();
        for (Map.Entry<String, ArrayList<NativeContentItem>> entry : grouped.entrySet()) {
            active.add(new SourceQueue(entry.getKey(), rankedSource(entry.getValue(), rng)));
        }

        String lastSource = "";
        int round = 0;
        while (!active.isEmpty()) {
            // One round in four keeps source order exploratory. Other rounds let the strongest
            // currently available community/local signal lead, while still taking one item from
            // every source before any source gets another slot.
            Collections.shuffle(active, rng);
            if (round % EXPLORATION_INTERVAL != EXPLORATION_INTERVAL - 1) {
                active.sort((left, right) -> Float.compare(right.headScore(), left.headScore()));
            }
            if (active.size() > 1 && active.get(0).key.equals(lastSource)) {
                Collections.swap(active, 0, 1);
            }
            for (SourceQueue source : active) {
                NativeContentItem item = source.items.pollFirst();
                if (item != null) {
                    result.add(item);
                    lastSource = source.key;
                }
            }
            active.removeIf(source -> source.items.isEmpty());
            round++;
        }
        return result;
    }

    private static ArrayDeque<NativeContentItem> rankedSource(
            ArrayList<NativeContentItem> source,
            Random rng
    ) {
        ArrayList<NativeContentItem> remaining = new ArrayList<>(source);
        Collections.shuffle(remaining, rng);
        // Stable sort preserves randomized tie order.
        remaining.sort((left, right) -> Float.compare(
                ShitTokSmartRanker.score(right), ShitTokSmartRanker.score(left)));

        ArrayList<NativeContentItem> ranked = new ArrayList<>(remaining.size());
        int position = 0;
        while (!remaining.isEmpty()) {
            int index = 0;
            // Roughly 25% of positions deliberately sample beyond the top score so community
            // popularity and skip learning never collapse a source into the same narrow pattern.
            if (position % EXPLORATION_INTERVAL == EXPLORATION_INTERVAL - 1 && remaining.size() > 1) {
                index = rng.nextInt(remaining.size());
            }
            ranked.add(remaining.remove(index));
            position++;
        }
        return new ArrayDeque<>(ranked);
    }

    static String sourceKey(NativeContentItem item) {
        if (item == null) return "unknown";

        String uploader = item.uploader == null ? "" : item.uploader.trim().toLowerCase(Locale.US);
        if (uploader.equals("kaotic")) return "kaotic";
        if (uploader.equals("shit show")) return "shit-show";
        if (uploader.equals("baddiehub") || BaddieHubRepository.isBaddieHubUrl(item.url)) return "baddiehub";
        if (BunkrRepository.isBunkrUrl(item.url)) return "bunkr";
        if (uploader.equals("onlyhaven") ||
                (uploader.contains("cum.st") && OnlyHavenRepository.isOnlyHavenUrl(item.uploader))) {
            return "onlyhaven";
        }
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

        float headScore() {
            NativeContentItem head = items.peekFirst();
            return head == null ? Float.NEGATIVE_INFINITY : ShitTokSmartRanker.score(head);
        }
    }
}
