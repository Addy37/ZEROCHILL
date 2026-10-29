package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cached anonymous aggregate of community video likes used only to rank existing ShitTok candidates. */
final class ShitTokCommunityPopularity {
    private static final long REFRESH_MS = 15L * 60L * 1000L;
    private static final long RETRY_MS = 60L * 1000L;
    private static final int LIMIT = 512;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private static volatile Map<String, Float> scores = Collections.emptyMap();
    private static volatile long loadedAt;
    private static volatile long attemptedAt;
    private static volatile Map<String, Float> testScores;

    private ShitTokCommunityPopularity() {}

    static void refreshAsync() {
        if (testScores != null || !configured()) return;
        long now = monotonicMillis();
        if (loadedAt > 0L && now - loadedAt < REFRESH_MS) return;
        if (attemptedAt > 0L && now - attemptedAt < RETRY_MS) return;
        if (!REFRESHING.compareAndSet(false, true)) return;
        attemptedAt = now;
        IO.execute(() -> {
            try {
                Map<String, Float> fresh = fetch();
                if (!fresh.isEmpty()) {
                    scores = Collections.unmodifiableMap(fresh);
                    loadedAt = monotonicMillis();
                }
            } catch (Exception ignored) {
                // Ranking remains functional with local skip/diversity signals when community data is unavailable.
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    static float score(String contentKey) {
        if (contentKey == null || contentKey.isEmpty()) return 0f;
        Map<String, Float> override = testScores;
        Float value = (override == null ? scores : override).get(contentKey);
        return value == null ? 0f : Math.max(0f, value);
    }

    private static Map<String, Float> fetch() throws Exception {
        String endpoint = BuildConfig.ACCOUNT_SUPABASE_URL
                + "/rest/v1/shittok_video_popularity"
                + "?select=content_key,likes,recent_24h,recent_7d"
                + "&order=recent_7d.desc,likes.desc"
                + "&limit=" + LIMIT;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_500);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("apikey", BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Popularity HTTP " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && body.length() < 512_000) body.append(line);
            }
            JSONArray rows = new JSONArray(body.toString());
            HashMap<String, Float> result = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String key = row.optString("content_key", "");
                if (!key.matches("[0-9a-f]{64}")) continue;
                int total = Math.max(0, row.optInt("likes", 0));
                int recent7d = Math.max(0, row.optInt("recent_7d", 0));
                int recent24h = Math.max(0, row.optInt("recent_24h", 0));
                // Recent momentum matters most; log scaling prevents a long-running hit from dominating forever.
                float score = (float) (
                        0.25d * Math.log1p(total)
                                + 0.55d * Math.log1p(recent7d)
                                + 0.75d * Math.log1p(recent24h)
                );
                if (score > 0f) result.put(key, Math.min(5f, score));
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static boolean configured() {
        return BuildConfig.ACCOUNT_SUPABASE_URL != null
                && !BuildConfig.ACCOUNT_SUPABASE_URL.trim().isEmpty()
                && BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY != null
                && !BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY.trim().isEmpty();
    }

    static void setScoresForTest(Map<String, Float> values) {
        testScores = values == null ? Collections.emptyMap() : new HashMap<>(values);
    }

    static void resetForTest() {
        testScores = null;
        scores = Collections.emptyMap();
        loadedAt = 0L;
        attemptedAt = 0L;
    }
}
