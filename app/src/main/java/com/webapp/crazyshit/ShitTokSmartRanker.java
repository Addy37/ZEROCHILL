package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight local taste signal for ShitTok. Fast skips stay on-device and decay over time. */
final class ShitTokSmartRanker {
    static final long FAST_SKIP_MS = 2_500L;
    private static final long HALF_LIFE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final float SOURCE_SKIP_WEIGHT = 0.35f;
    private static final float CREATOR_SKIP_WEIGHT = 1.0f;
    private static final float SOURCE_PENALTY_WEIGHT = 0.55f;
    private static final float CREATOR_PENALTY_WEIGHT = 0.90f;
    private static final float MAX_SOURCE_SIGNAL = 4f;
    private static final float MAX_CREATOR_SIGNAL = 6f;
    private static final int MAX_FEATURES = 4096;
    private static final int MAX_SOURCE_SIGNALS = 64;
    private static final int MAX_CREATOR_SIGNALS = 384;
    private static final String PREFS = "shittok_smart_ranker_v1";
    private static final String KEY_SOURCES = "fast_skip_sources";
    private static final String KEY_CREATORS = "fast_skip_creators";
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor();

    private static Context app;
    private static SharedPreferences prefs;
    private static final LinkedHashMap<String, Feature> FEATURES =
            new LinkedHashMap<String, Feature>(128, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Feature> eldest) {
                    return size() > MAX_FEATURES;
                }
            };
    private static final LinkedHashMap<String, Signal> SOURCE_SIGNALS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Signal> CREATOR_SIGNALS = new LinkedHashMap<>();
    private static String activeUrl = "";
    private static long activeSinceElapsed;
    private static boolean dirty;

    private ShitTokSmartRanker() {}

    static synchronized void initialize(Context context) {
        if (context == null) return;
        Context next = context.getApplicationContext();
        if (app == next && prefs != null) return;
        app = next;
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        FEATURES.clear();
        SOURCE_SIGNALS.clear();
        CREATOR_SIGNALS.clear();
        activeUrl = "";
        activeSinceElapsed = 0L;
        dirty = false;
        readSignals(KEY_SOURCES, SOURCE_SIGNALS, MAX_SOURCE_SIGNALS);
        readSignals(KEY_CREATORS, CREATOR_SIGNALS, MAX_CREATOR_SIGNALS);
    }

    static synchronized void register(NativeContentItem item) {
        if (item == null || clean(item.url).isEmpty()) return;
        FEATURES.put(item.url, Feature.from(item));
    }

    static synchronized void onViewed(String pageUrl) {
        onViewedAt(pageUrl, SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    static synchronized float score(NativeContentItem item) {
        if (item == null) return 0f;
        ShitTokCommunityPopularity.refreshAsync();
        register(item);
        String key = ZeroChillSocialRepository.contentKey(item.url);
        return ShitTokCommunityPopularity.score(key) - penaltyLocked(Feature.from(item), System.currentTimeMillis());
    }

    static synchronized float penalty(NativeContentItem item) {
        if (item == null) return 0f;
        register(item);
        return penaltyLocked(Feature.from(item), System.currentTimeMillis());
    }

    static synchronized void onViewedAt(String pageUrl, long nowElapsed, long nowWall) {
        String url = clean(pageUrl);
        if (url.isEmpty()) return;
        if (url.equals(activeUrl)) return;

        if (!activeUrl.isEmpty() && activeSinceElapsed > 0L) {
            long dwell = Math.max(0L, nowElapsed - activeSinceElapsed);
            if (dwell > 0L && dwell <= FAST_SKIP_MS) {
                Feature skipped = FEATURES.get(activeUrl);
                if (skipped != null) recordSkipLocked(skipped, nowWall);
            }
        }
        activeUrl = url;
        activeSinceElapsed = nowElapsed;
    }

    private static float penaltyLocked(Feature feature, long nowWall) {
        if (feature == null) return 0f;
        float source = decayed(SOURCE_SIGNALS.get(feature.source), nowWall) * SOURCE_PENALTY_WEIGHT;
        float creator = decayed(CREATOR_SIGNALS.get(feature.creator), nowWall) * CREATOR_PENALTY_WEIGHT;
        return Math.min(6f, Math.max(0f, source + creator));
    }

    private static void recordSkipLocked(Feature feature, long nowWall) {
        if (feature == null) return;
        if (!feature.source.isEmpty()) {
            putSignal(SOURCE_SIGNALS, feature.source, SOURCE_SKIP_WEIGHT, MAX_SOURCE_SIGNAL,
                    MAX_SOURCE_SIGNALS, nowWall);
        }
        if (!feature.creator.isEmpty()) {
            putSignal(CREATOR_SIGNALS, feature.creator, CREATOR_SKIP_WEIGHT, MAX_CREATOR_SIGNAL,
                    MAX_CREATOR_SIGNALS, nowWall);
        }
        dirty = true;
    }

    private static void putSignal(
            LinkedHashMap<String, Signal> target,
            String key,
            float increment,
            float cap,
            int limit,
            long nowWall
    ) {
        if (key.isEmpty()) return;
        float next = Math.min(cap, decayed(target.get(key), nowWall) + increment);
        target.remove(key);
        target.put(key, new Signal(next, nowWall));
        while (target.size() > limit) target.remove(target.keySet().iterator().next());
    }

    private static float decayed(Signal signal, long nowWall) {
        if (signal == null || signal.value <= 0f) return 0f;
        long age = Math.max(0L, nowWall - signal.atWall);
        if (age <= 0L) return signal.value;
        double halves = age / (double) HALF_LIFE_MS;
        return (float) (signal.value * Math.pow(0.5d, halves));
    }

    private static void readSignals(String prefKey, LinkedHashMap<String, Signal> target, int limit) {
        if (prefs == null) return;
        try {
            JSONArray rows = new JSONArray(prefs.getString(prefKey, "[]"));
            int start = Math.max(0, rows.length() - limit);
            for (int i = start; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String key = clean(row.optString("key", ""));
                float value = (float) row.optDouble("value", 0d);
                long at = row.optLong("at", 0L);
                if (!key.isEmpty() && value > 0f && at > 0L) target.put(key, new Signal(value, at));
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized void flush() {
        if (!dirty || prefs == null) return;
        dirty = false;
        ArrayList<Map.Entry<String, Signal>> sourceSnapshot = new ArrayList<>(SOURCE_SIGNALS.entrySet());
        ArrayList<Map.Entry<String, Signal>> creatorSnapshot = new ArrayList<>(CREATOR_SIGNALS.entrySet());
        SharedPreferences target = prefs;
        DISK.execute(() -> target.edit()
                .putString(KEY_SOURCES, encode(sourceSnapshot))
                .putString(KEY_CREATORS, encode(creatorSnapshot))
                .commit());
    }

    private static String encode(ArrayList<Map.Entry<String, Signal>> entries) {
        JSONArray rows = new JSONArray();
        for (Map.Entry<String, Signal> entry : entries) {
            try {
                rows.put(new JSONObject()
                        .put("key", entry.getKey())
                        .put("value", entry.getValue().value)
                        .put("at", entry.getValue().atWall));
            } catch (Exception ignored) {
            }
        }
        return rows.toString();
    }

    static synchronized void resetForTest(Context context) {
        initialize(context);
        if (prefs != null) prefs.edit().clear().commit();
        FEATURES.clear();
        SOURCE_SIGNALS.clear();
        CREATOR_SIGNALS.clear();
        activeUrl = "";
        activeSinceElapsed = 0L;
        dirty = false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Feature {
        final String source;
        final String creator;

        Feature(String source, String creator) {
            this.source = source;
            this.creator = creator;
        }

        static Feature from(NativeContentItem item) {
            if (item == null) return new Feature("", "");
            String source = clean(ShitTokAspectPriority.sourceKey(item)).toLowerCase(Locale.US);
            String creator = clean(ShitTokCreatorMetadata.creatorName(item)).toLowerCase(Locale.US);
            return new Feature(source, creator);
        }
    }

    private static final class Signal {
        final float value;
        final long atWall;

        Signal(float value, long atWall) {
            this.value = value;
            this.atWall = atWall;
        }
    }
}
