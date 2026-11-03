package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.WeakHashMap;
import org.json.JSONObject;

/** Main-thread cache partition, not an authorization decision. Storage still checks the JWT. */
final class SocialMediaSession {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ImageView, Boolean> views = new WeakHashMap<>();
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static String identity;
    private static Object scope;
    private static android.app.Application application;
    private static final android.app.Application.ActivityLifecycleCallbacks lifecycle =
            new android.app.Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityDestroyed(android.app.Activity activity) {
                    for (ImageView image : new ArrayList<>(views.keySet()))
                        if (image.getContext() == activity) SocialMedia.clear(image);
                }
                @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) { }
                @Override public void onActivityStarted(android.app.Activity a) { }
                @Override public void onActivityResumed(android.app.Activity a) { }
                @Override public void onActivityPaused(android.app.Activity a) { }
                @Override public void onActivityStopped(android.app.Activity a) { }
                @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) { }
            };

    static Object current(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences next = ZeroChillSessionStore.preferences(app);
        if (preferences != next) {
            if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(listener);
            if (application != null) application.unregisterActivityLifecycleCallbacks(lifecycle);
            application = (android.app.Application) app;
            application.registerActivityLifecycleCallbacks(lifecycle);
            preferences = next;
            identity = null;
            listener = (prefs, key) -> {
                if (prefs != preferences) return;
                // Observe logout even if the same account signs back in before the posted apply.
                if (!prefs.contains("session_value")) identity = null;
                MAIN.post(() -> { if (prefs == preferences) current(app); });
            };
            preferences.registerOnSharedPreferenceChangeListener(listener);
        }
        String nextIdentity = identity(app);
        if (!nextIdentity.equals(identity)) {
            identity = nextIdentity;
            scope = UUID.randomUUID().toString();
            // Drop displayed pixels and cancel pending work, including same-user logout/login.
            // Retired memory-cache keys cannot be reused; no private image is written to disk.
            for (ImageView view : new ArrayList<>(views.keySet())) SocialMedia.clear(view);
        }
        return scope;
    }

    static void track(ImageView image) { views.put(image, Boolean.TRUE); }
    static void forget(ImageView image) { views.remove(image); }

    static boolean valid(Object expected, Context context) {
        // Request listeners may reject a delivery, but must never clear/restart Glide reentrantly.
        return expected == scope && identity(context).equals(identity);
    }

    private static String identity(Context context) {
        String session = ZeroChillSessionStore.read(context);
        if (session.isEmpty()) return "guest";
        try {
            String token = new JSONObject(session).optString("access_token");
            String[] parts = token.split("\\.");
            JSONObject claims = new JSONObject(new String(android.util.Base64.decode(parts[1],
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP
                            | android.util.Base64.NO_PADDING), StandardCharsets.UTF_8));
            // Supabase session_id survives token refresh, but changes on a new login.
            String sessionId = claims.optString("session_id");
            return claims.optString("sub") + ":" + (sessionId.isEmpty() ? token : sessionId);
        } catch (Exception invalid) {
            return session; // Invalid/legacy sessions still get an isolated partition.
        }
    }
}
