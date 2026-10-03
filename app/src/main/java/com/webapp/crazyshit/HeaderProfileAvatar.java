package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.bumptech.glide.Glide;

/** Session-driven header shortcut. No timer or independent profile polling. */
final class HeaderProfileAvatar extends FrameLayout {
    interface Session { String current(Context context); }
    interface Loader { void load(Context context, ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> callback); }
    private final Activity activity;
    private final Session session;
    private final Loader loader;
    private final ImageView image;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable render = this::refresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        handler.removeCallbacks(render);
        handler.post(render);
    };
    private String owner = "";
    private String path = "";
    private String attemptedOwner = "";
    private boolean active;
    private int generation;

    HeaderProfileAvatar(Activity activity) {
        this(activity, ZeroChillSessionStore::currentUserId, AccountProfileCache::hydrate);
    }

    HeaderProfileAvatar(Activity activity, Session session, Loader loader) {
        super(activity);
        this.activity = activity;
        this.session = session;
        this.loader = loader;
        setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(BrowseUi.rounded(activity, Color.rgb(20, 25, 30), 16));
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        image.setClipToOutline(true);
        LayoutParams params = new LayoutParams(dp(32), dp(32), Gravity.CENTER);
        addView(image, params);
        ZeroChillMotion.installPressFeedback(this);
        setOnClickListener(v -> {
            String current = session.current(activity);
            if (!owner.isEmpty() && owner.equals(current)) {
                activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class));
            } else refresh();
        });
    }

    void resume() {
        if (!active) {
            active = true;
            AccountProfileCache.preferences(activity).registerOnSharedPreferenceChangeListener(listener);
            ZeroChillSessionStore.preferences(activity).registerOnSharedPreferenceChangeListener(listener);
        }
        refresh();
    }

    void pause() {
        active = false;
        AccountProfileCache.preferences(activity).unregisterOnSharedPreferenceChangeListener(listener);
        ZeroChillSessionStore.preferences(activity).unregisterOnSharedPreferenceChangeListener(listener);
        handler.removeCallbacks(render);
    }

    void close() {
        pause();
        generation++;
        AccountAvatarImages.track(image, "");
        Glide.with(image).clear(image);
    }

    void refresh() {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        String current = session.current(activity);
        if (current == null) current = "";
        if (!current.equals(owner)) {
            generation++;
            owner = current;
            path = "";
            attemptedOwner = "";
            AccountAvatarImages.track(image, "");
            Glide.with(image).clear(image);
            image.setImageDrawable(null);
        }
        if (owner.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        SharedPreferences cache = AccountProfileCache.preferences(activity);
        boolean known = owner.equals(cache.getString("owner", ""));
        String next = known ? cache.getString("avatar", "") : "";
        if (!next.equals(path) || image.getDrawable() == null) {
            path = next;
            AccountAvatarImages.bind(image, owner, path);
        }
        setContentDescription("Your profile" + (known ? ": " + cache.getString("name", "") : ""));
        setVisibility(VISIBLE);
        // Older installs have no derived profile cache. Hydrate once for this account/view.
        // Normal sign-in, profile save and upload already publish the same cache.
        if (!known && !owner.equals(attemptedOwner)) {
            attemptedOwner = owner;
            String requested = owner;
            int requestGeneration = generation;
            loader.load(activity, (state, error) -> handler.post(() -> {
                if (requestGeneration != generation || !requested.equals(session.current(activity))
                        || activity.isFinishing() || activity.isDestroyed()) return;
                if (error == null && state != null && requested.equals(state.userId)) {
                    AccountProfileCache.record(activity, state);
                }
                refresh();
            }));
        }
    }

    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
