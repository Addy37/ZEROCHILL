package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

/** Library identity and local account shortcuts. Never fetches profile or inbox data. */
final class LibraryProfileHeader extends LinearLayout {
    private final Activity activity;
    private final ImageView avatar;
    private final TextView name;
    private final TextView messages;
    private final TextView notifications;
    private final LinearLayout identityCard;
    private final FrameLayout avatarFrame;
    private final TextView profileArrow;
    private final List<View> actionTiles = new ArrayList<>();
    private final List<ImageView> actionIcons = new ArrayList<>();
    private final List<SharedPreferences> observed = new ArrayList<>();
    private final Runnable update = this::refresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (prefs, key) -> {
        removeCallbacks(update);
        post(update);
    };
    private boolean active;
    private final java.util.concurrent.ExecutorService reader;
    private int readRevision;
    private java.util.concurrent.Future<?> pendingRead;
    private boolean closed;
    private String boundOwner = "", boundPath = "";

    LibraryProfileHeader(Activity activity) { this(activity, null); }

    LibraryProfileHeader(Activity activity, java.util.concurrent.ExecutorService reader) {
        super(activity);
        this.activity = activity;
        this.reader = reader;
        setOrientation(VERTICAL);
        setPadding(0, dp(8), 0, dp(24));

        // V2-inspired identity card. Avatar, profile reads and navigation stay native.
        identityCard = new LinearLayout(activity);
        identityCard.setGravity(Gravity.CENTER_VERTICAL);
        identityCard.setPadding(dp(17), dp(17), dp(17), dp(17));
        identityCard.setMinimumHeight(dp(104));
        identityCard.setClipToOutline(true);
        identityCard.setClickable(true);
        identityCard.setFocusable(true);
        identityCard.setContentDescription("Your profile and account");
        identityCard.setOnClickListener(v -> activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class)));
        ZeroChillMotion.installPressFeedback(identityCard);

        avatarFrame = new FrameLayout(activity);
        avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(BrowseUi.rounded(activity, AppearanceTheme.raised(activity), 30));
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        avatarFrame.addView(avatar, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        identityCard.addView(avatarFrame, new LayoutParams(dp(70), dp(70)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        copy.setPadding(dp(14), 0, dp(6), 0);
        name = label("Your Library", 23, AppearanceTheme.primaryText(activity));
        ZeroChillTypography.apply(name, ZeroChillTypography.Role.SECTION);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name, new LayoutParams(-1, -2));
        identityCard.addView(copy, new LayoutParams(0, -2, 1));

        profileArrow = label("\u203a", 30, AppearanceTheme.accent(activity));
        profileArrow.setGravity(Gravity.CENTER);
        profileArrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        identityCard.addView(profileArrow, new LayoutParams(dp(20), -2));
        addView(identityCard, new LayoutParams(-1, -2));

        // Independent, lightweight shortcuts; no backdrop capture during Library scrolling.
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        messages = action(actions, "Messages", R.drawable.ic_action_message, ZeroChillInboxActivity.class);
        notifications = action(actions, "Notifications", R.drawable.ic_more_update, UpdateInboxActivity.class);
        ((View) messages.getParent()).setContentDescription("Messages. Open your inbox");
        ((View) notifications.getParent()).setContentDescription("Notifications. Open notifications");
        action(actions, "Settings", R.drawable.ic_more_settings, SettingsActivity.class);
        addView(actions, new LayoutParams(-1, -2));
        applyTheme();
        if (reader == null) { observe(); refresh(); }
    }

    private void observe() {
        if (!observed.isEmpty()) return;
        observed.add(AccountProfileCache.preferences(activity));
        observed.add(ZeroChillSessionStore.preferences(activity));
        observed.add(activity.getSharedPreferences(ZeroChillMessageBadgeStore.PREFS, 0));
        observed.add(activity.getSharedPreferences("zerochill_update_inbox_v1", 0));
    }

    private TextView action(LinearLayout row, String title, int drawable, Class<?> destination) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(10), dp(4), dp(10));
        tile.setMinimumHeight(dp(82));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(title);
        tile.setOnClickListener(v -> activity.startActivity(new Intent(activity, destination)));
        ZeroChillMotion.installPressFeedback(tile);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawable);
        icon.setColorFilter(AppearanceTheme.accent(activity));
        tile.addView(icon, new LayoutParams(dp(23), dp(23)));
        TextView caption = label(title, 11, AppearanceTheme.secondaryText(activity));
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        LayoutParams captionParams = new LayoutParams(-1, -2);
        captionParams.topMargin = dp(7);
        tile.addView(caption, captionParams);
        LayoutParams tileParams = new LayoutParams(0, dp(84), 1);
        if (row.getChildCount() > 0) tileParams.leftMargin = dp(10);
        row.addView(tile, tileParams);
        actionTiles.add(tile);
        actionIcons.add(icon);
        return caption;
    }

    private void applyTheme() {
        int accent = AppearanceTheme.accent(activity);
        GradientDrawable profile = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {
                        tinted(Color.rgb(23, 37, 46), accent, .15f),
                        tinted(Color.rgb(14, 20, 27), accent, .08f),
                        Color.rgb(9, 12, 17)
                });
        profile.setCornerRadius(dp(23));
        profile.setStroke(dp(1), translucent(accent, 65));
        identityCard.setBackground(profile);

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(tinted(AppearanceTheme.raised(activity), accent, .13f));
        ring.setStroke(dp(1), translucent(accent, 135));
        avatarFrame.setBackground(ring);
        name.setTextColor(AppearanceTheme.primaryText(activity));
        profileArrow.setTextColor(accent);
        for (View tile : actionTiles) {
            GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            tinted(Color.rgb(20, 28, 36), accent, .09f),
                            Color.rgb(12, 17, 23)
                    });
            surface.setCornerRadius(dp(19));
            surface.setStroke(dp(1), translucent(accent, 44));
            tile.setBackground(surface);
        }
        for (ImageView icon : actionIcons) icon.setColorFilter(accent);
        messages.setTextColor(AppearanceTheme.secondaryText(activity));
        notifications.setTextColor(AppearanceTheme.secondaryText(activity));
    }

    private static int tinted(int base, int accent, float amount) {
        return Color.rgb(
                Math.round(Color.red(base) * (1 - amount) + Color.red(accent) * amount),
                Math.round(Color.green(base) * (1 - amount) + Color.green(accent) * amount),
                Math.round(Color.blue(base) * (1 - amount) + Color.blue(accent) * amount));
    }

    private static int translucent(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    void refresh() {
        if (closed || (reader != null && !active)) return;
        applyTheme();
        if (reader == null) { apply(new Identity(activity)); return; }
        String owner = ZeroChillSessionStore.currentUserId(activity);
        if (!owner.equals(boundOwner)) {
            name.setText("Your Library");
            boundOwner = owner; boundPath = "";
            Glide.with(avatar).clear(avatar);
            AccountAvatarImages.track(avatar, owner);
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setColorFilter(AppearanceTheme.accent(activity));
            avatar.setPadding(dp(14), dp(14), dp(14), dp(14));
        }
        final int token = ++readRevision;
        if (pendingRead != null) pendingRead.cancel(false);
        pendingRead = reader.submit(() -> {
            Identity identity = new Identity(activity);
            post(() -> {
                if (!closed && active && token == readRevision
                        && identity.owner.equals(ZeroChillSessionStore.currentUserId(activity))) apply(identity);
            });
        });
    }

    private static final class Identity {
        final String owner, display, path;
        final int messages, notifications;
        Identity(Activity activity) {
            owner = ZeroChillSessionStore.currentUserId(activity);
            SharedPreferences cache = AccountProfileCache.preferences(activity);
            boolean known = !owner.isEmpty() && owner.equals(cache.getString("owner", ""));
            display = known ? cache.getString("name", "") : "";
            path = known ? cache.getString("avatar", "") : "";
            messages = ZeroChillMessageBadgeStore.unreadCount(activity);
            notifications = UpdateInboxStore.unreadCount(activity);
        }
    }

    private void apply(Identity identity) {
        String owner = identity.owner, path = identity.path, display = identity.display;
        name.setText(display == null || display.trim().isEmpty()
                ? owner.isEmpty() ? "Your Library" : "Your ZEROCHILL" : display);
        if (!owner.equals(boundOwner) || !path.equals(boundPath) || avatar.getDrawable() == null) {
            boundOwner = owner;
            boundPath = path;
            Glide.with(avatar).clear(avatar);
            AccountAvatarImages.track(avatar, owner);
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setColorFilter(AppearanceTheme.accent(activity));
            avatar.setPadding(dp(14), dp(14), dp(14), dp(14));
            if (!owner.isEmpty() && !path.isEmpty()) {
                avatar.clearColorFilter();
                avatar.setPadding(0, 0, 0, 0);
                AccountAvatarImages.bind(avatar, owner, path);
            }
        }
        badge(messages, "Messages", identity.messages);
        badge(notifications, "Notifications", identity.notifications);
    }

    private void badge(TextView view, String title, int count) {
        view.setText(title + (count > 0 ? " (" + (count > 99 ? "99+" : count) + ")" : ""));
        ((View) view.getParent()).setContentDescription(title + ". "
                + (count > 0 ? count + " unread" : "No unread " + title.toLowerCase(java.util.Locale.US)));
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        readRevision++;
        if (value) observe();
        for (SharedPreferences prefs : observed) {
            if (value) prefs.registerOnSharedPreferenceChangeListener(changed);
            else prefs.unregisterOnSharedPreferenceChangeListener(changed);
        }
        if (value) refresh();
        else {
            removeCallbacks(update);
            if (pendingRead != null) pendingRead.cancel(false);
        }
    }

    void close() {
        setActive(false);
        closed = true;
        readRevision++;
        AccountAvatarImages.track(avatar, "");
        Glide.with(avatar).clear(avatar);
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
