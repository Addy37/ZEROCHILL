package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
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
        setPadding(dp(4), dp(8), dp(4), dp(24));
        LinearLayout identity = new LinearLayout(activity);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(0, 0, 0, dp(20));
        identity.setClickable(true);
        identity.setFocusable(true);
        identity.setContentDescription("Your profile and account");
        identity.setOnClickListener(v -> activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class)));
        avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(BrowseUi.rounded(activity, UiPalette.RAISED, 30));
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        identity.addView(avatar, new LayoutParams(dp(60), dp(60)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        name = label("Your Library", 24, UiPalette.TEXT_PRIMARY);
        ZeroChillTypography.apply(name, ZeroChillTypography.Role.SECTION);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name, new LayoutParams(-1, -2));
        identity.addView(copy, new LayoutParams(0, -2, 1));
        addView(identity, new LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackground(BrowseUi.rounded(activity, UiPalette.SURFACE, 18));
        messages = action(actions, "Messages", R.drawable.ic_action_message, ZeroChillInboxActivity.class);
        notifications = action(actions, "Notifications", R.drawable.ic_more_update, UpdateInboxActivity.class);
        ((View) messages.getParent()).setContentDescription("Messages. Open your inbox");
        ((View) notifications.getParent()).setContentDescription("Notifications. Open notifications");
        action(actions, "Settings", R.drawable.ic_more_settings, SettingsActivity.class);
        addView(actions, new LayoutParams(-1, -2));
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
        LinearLayout action = new LinearLayout(activity);
        action.setOrientation(VERTICAL);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(2), dp(12), dp(2), dp(12));
        action.setMinimumHeight(dp(68));
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(title);
        action.setOnClickListener(v -> activity.startActivity(new Intent(activity, destination)));
        ZeroChillMotion.installPressFeedback(action);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawable);
        icon.setColorFilter(UiPalette.PRIMARY);
        action.addView(icon, new LayoutParams(dp(22), dp(22)));
        TextView caption = label(title, 11, UiPalette.TEXT_SECONDARY);
        caption.setGravity(Gravity.CENTER);
        LayoutParams captionParams = new LayoutParams(-1, -2);
        captionParams.topMargin = dp(6);
        action.addView(caption, captionParams);
        row.addView(action, new LayoutParams(0, -2, 1));
        return caption;
    }

    void refresh() {
        if (closed || (reader != null && !active)) return;
        if (reader == null) { apply(new Identity(activity)); return; }
        String owner = ZeroChillSessionStore.currentUserId(activity);
        if (!owner.equals(boundOwner)) {
            name.setText("Your Library");
            boundOwner = owner; boundPath = "";
            Glide.with(avatar).clear(avatar);
            AccountAvatarImages.track(avatar, owner);
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setColorFilter(UiPalette.PRIMARY);
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
            avatar.setColorFilter(UiPalette.PRIMARY);
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
