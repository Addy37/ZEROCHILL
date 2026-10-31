package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** Only recommendation-owned keys; saved content and general history are never opened. */
final class ShitTokRecommendationReset {
    static final String FLAG = "shittok_personalization_reset_v2_complete";
    static synchronized void run(Context context) {
        SharedPreferences discovery = context.getSharedPreferences("shittok_discovery", 0);
        if (discovery.getBoolean(FLAG, false)) return;
        context.getSharedPreferences("shittok_smart_ranker_v1", 0).edit()
                .remove("fast_skip_sources").remove("fast_skip_creators")
                .remove("source_rewards_v2").remove("creator_rewards_v2").apply();
        context.getSharedPreferences("chaos_feed", 0).edit().remove("recent_urls").apply();
        // apply updates memory immediately and queues disk writes off the swipe path.
        discovery.edit().remove("seen_v1").putBoolean(FLAG, true).apply();
    }
}
