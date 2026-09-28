package com.webapp.crazyshit;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Applies a soft ShitTok feed preference without filtering any media.
 *
 * Priority order is portrait, 4:3-ish/square, then wide. Unknown items remain neutral until
 * existing player look-ahead teaches the session what a source or creator usually serves.
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
        ArrayList<NativeContentItem> copy = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return copy;
        Random rng = random == null ? new Random() : random;
        ArrayList<RankedItem> ranked = new ArrayList<>(candidates.size());
        for (NativeContentItem item : candidates) {
            if (item == null) continue;
            double weight = weightFor(item) * sourceWeight(item);
            double draw = Math.max(1.0e-9d, rng.nextDouble());
            ranked.add(new RankedItem(item, -Math.log(draw) / Math.max(WIDE_WEIGHT, weight)));
        }
        ranked.sort(Comparator.comparingDouble(value -> value.score));
        for (RankedItem item : ranked) copy.add(item.item);
        return copy;
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

    static double sourceWeight(NativeContentItem item) {
        if (item == null) return 1.0d;
        String source = item.uploader == null ? "" : item.uploader.trim().toLowerCase(Locale.US);
        if (source.equals("kaotic")) return 4.0d;
        if (source.equals("shit show")) return 3.5d;
        if (source.equals("bunkr") || source.equals("onlyhaven") ||
                "Fapello".equalsIgnoreCase(item.description) ||
                "OnlyFap".equalsIgnoreCase(item.description)) return 2.5d;
        if (source.equals("efukt")) return 0.6d;
        return 1.0d;
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
        String host = "";
        try {
            URI uri = URI.create(item.url == null ? "" : item.url.trim());
            host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
        } catch (Exception ignored) {
        }

        String creator = ShitTokCreatorMetadata.creatorName(item);
        if (creator != null && !creator.trim().isEmpty()) {
            return host + "|creator:" + creator.trim().toLowerCase(Locale.US);
        }
        if (!host.isEmpty()) return host;

        String uploader = item.uploader == null ? "" : item.uploader.trim().toLowerCase(Locale.US);
        return uploader;
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
}
