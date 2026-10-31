package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokRecommendationResetTest {
    @Test public void resetIsScopedAndRunsOnce() {
        Application app = RuntimeEnvironment.getApplication();
        String[] ranking = {"fast_skip_sources", "fast_skip_creators", "source_rewards_v2", "creator_rewards_v2"};
        for (String key : ranking) app.getSharedPreferences("shittok_smart_ranker_v1", 0).edit().putString(key, "old").commit();
        app.getSharedPreferences("shittok_discovery", 0).edit().putString("seen_v1", "old").putString("liked_v1", "saved").commit();
        app.getSharedPreferences("chaos_feed", 0).edit().putString("recent_urls", "old").putBoolean("muted", true).commit();
        String[] preserved = {"watch_later", "downloads", "app_prefs", "playback_history", "creator_favorites", "shows_history"};
        for (String file : preserved) app.getSharedPreferences(file, 0).edit().putString("sentinel", "keep").commit();
        ContentPreference.set(app, ContentPreference.WOMEN);
        ShitTokRecommendationReset.run(app);
        for (String key : ranking) assertFalse(app.getSharedPreferences("shittok_smart_ranker_v1", 0).contains(key));
        assertFalse(app.getSharedPreferences("shittok_discovery", 0).contains("seen_v1"));
        assertEquals("saved", app.getSharedPreferences("shittok_discovery", 0).getString("liked_v1", ""));
        assertFalse(app.getSharedPreferences("chaos_feed", 0).contains("recent_urls"));
        assertTrue(app.getSharedPreferences("chaos_feed", 0).getBoolean("muted", false));
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(app));
        for (String file : preserved) assertEquals("keep", app.getSharedPreferences(file, 0).getString("sentinel", ""));
        assertTrue(app.getSharedPreferences("shittok_discovery", 0).getBoolean(ShitTokRecommendationReset.FLAG, false));
        app.getSharedPreferences("shittok_discovery", 0).edit().putString("seen_v1", "new").commit();
        ShitTokRecommendationReset.run(app);
        assertEquals("new", app.getSharedPreferences("shittok_discovery", 0).getString("seen_v1", ""));
    }
    @Test public void freshInstallMarksCompletionWithoutClearingLaterLearning() {
        Application app = RuntimeEnvironment.getApplication();
        ShitTokRecommendationReset.run(app);
        app.getSharedPreferences("shittok_smart_ranker_v1", 0).edit().putString("source_rewards_v2", "new").commit();
        ShitTokRecommendationReset.run(app);
        assertEquals("new", app.getSharedPreferences("shittok_smart_ranker_v1", 0).getString("source_rewards_v2", ""));
    }
}
