package com.webapp.crazyshit;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Keeps ShitTok source-diverse while still softly preferring portrait media inside each source.
 *
 * Sources are first split into independent queues, each queue is ranked by portrait preference,
 * then the queues are interleaved round-robin. A source that contributes a larger candidate pool
 * therefore does not automatically dominate the visible feed. Unknown aspect ratios remain neutral
 * until existing player look-ahead teaches the session what a source or creator usually serves.
 */
final class ShitTokAspectPriority {
    static final int BUCKET_VERTICAL = 0;
    static final int BUCKET_STANDARD = 1;
    static final int BUCKET_WIDE = 2;
    static final int BUCKET_UNKNOWN = 3;

    private static final float VERTICAL_MAX = 0.99f;
    private static final float STANDARD_MAX = 1.50f;
    private static final double VERTICAL_WEIGHT = 6.0d;
    private static final double STANDARD_WEIGHT = 3.0d;
    private static final double UNKNOWN_WEIGHT = 1.5d;
    private static final double WIDE_WEIGHT = 1.0d;
    private static final int MIN_LEARNED_SAMPLES = 2;

    private final Map<String, Stats> groupStats = new HashMap<>();

    synchronized void record(NativeContentItem item, float aspectRatio) {
        int bucket = bucket(aspectRatio);
        if (bucket == BUCKET_UNKNOWN || item == null) return;
        String key = groupKey(item);
        if (key.isEmpty()) return;
        Stats stats = groupStats.computeIfAbsent(key, ignored -> new Stats());
        stats.samples++;
        stats.weightTotal += weightForBucket(bucket);
    }

    List<NativeContentItem> order(List<NativeContentItem> candidates, Random random) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return result;

        Random rng = random == null ? new Random() : random;
        LinkedHashMap<String, ArrayList<NativeContentItem>> grouped = new LinkedHashMap<>();
        for (NativeContentItem item : candidates) {
            if (item == null) continue;
            grouped.computeIfAbsent(sourceKey(item), ignored -> new ArrayList<>()).add(item);
        }

        ArrayList<SourceQueue> active = new ArrayList<>();
        for (Map.Entry<String, ArrayList<NativeContentItem>> entry : grouped.entrySet()) {
            ArrayList<RankedItem> ranked = new ArrayList<>(entry.getValue().size());
            for (NativeContentItem item : entry.getValue()) {
                double weight = weightFor(item);
                double draw = Math.max(1.0e-9d, rng.nextDouble());
                ranked.add(new RankedItem(item, -Math.log(draw) / Math.max(WIDE_WEIGHT, weight)));
            }
            ranked.sort(Comparator.comparingDouble(value -> value.score));

            ArrayDeque<NativeContentItem> queue = new ArrayDeque<>();
            for (RankedItem item : ranked) queue.addLast(item.item);
            if (!queue.isEmpty()) active.add(new SourceQueue(entry.getKey(), queue));
        }

        String lastSource = "";
        while (!active.isEmpty()) {
            Collections.shuffle(active, rng);
            if (active.size() > 1 && active.get(0).key.equals(lastSource)) {
                for (int i = 1; i < active.size(); i++) {
                    if (!active.get(i).key.equals(lastSource)) {
                        Collections.swap(active, 0, i);
                        break;
                    }
                }
            }

            for (SourceQueue source : active) {
                NativeContentItem item = source.items.pollFirst();
                if (item == null) continue;
                result.add(item);
                lastSource = source.key;
            }
            active.removeIf(source -> source.items.isEmpty());
        }
        return result;
    }

    synchronized double weightFor(NativeContentItem item) {
        if (item == null) return UNKNOWN_WEIGHT;
        int hinted = bucket(item.aspectRatioHint);
        if (hinted != BUCKET_UNKNOWN) return weightForBucket(hinted);

        String key = groupKey(item);
        Stats stats = groupStats.get(key);
        if (stats == null || stats.samples < MIN_LEARNED_SAMPLES) return UNKNOWN_WEIGHT;
        return Math.max(WIDE_WEIGHT, Math.min(VERTICAL_WEIGHT, stats.weightTotal / stats.samples));
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
        if (aspectRatio <= VERTICAL_MAX) return BUCKET_VERTICAL;
        if (aspectRatio <= STANDARD_MAX) return BUCKET_STANDARD;
        return BUCKET_WIDE;
    }

    private static double weightForBucket(int bucket) {
        if (bucket == BUCKET_VERTICAL) return VERTICAL_WEIGHT;
        if (bucket == BUCKET_STANDARD) return STANDARD_WEIGHT;
        if (bucket == BUCKET_WIDE) return WIDE_WEIGHT;
        return UNKNOWN_WEIGHT;
    }

    private static String groupKey(NativeContentItem item) {
        if (item == null) return "";
        String host = hostFor(item.url);

        String creator = ShitTokCreatorMetadata.creatorName(item);
        if (creator != null && !creator.trim().isEmpty()) {
            return host + "|creator:" + creator.trim().toLowerCase(Locale.US);
        }
        if (!host.isEmpty()) return host;

        String uploader = item.uploader == null ? "" : item.uploader.trim().toLowerCase(Locale.US);
        return uploader;
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

    private static final class Stats {
        int samples;
        double weightTotal;
    }

    private static final class RankedItem {
        final NativeContentItem item;
        final double score;

        RankedItem(NativeContentItem item, double score) {
            this.item = item;
            this.score = score;
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
