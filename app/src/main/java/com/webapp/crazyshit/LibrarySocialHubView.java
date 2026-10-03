package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact communication entry points above the Library media rails. */
final class LibrarySocialHubView extends LinearLayout {
    interface InboxLoader {
        void load(Context context,
                  ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>> callback);
    }

    interface SessionProvider {
        String current(Context context);
    }

    private static final long POLL_MS = 12_000L;
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final InboxLoader inboxLoader;
    private final SessionProvider sessionProvider;
    private final SharedPreferences updatesPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener updatesListener =
            (preferences, key) -> post(this::refreshNotifications);
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active || closed) return;
            refreshMessages();
            refreshSocialActivity();
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final TextView messageBadge;
    private final TextView notificationBadge;
    private final TextView messageName;
    private final TextView messagePreview;
    private final TextView messageTime;
    private final TextView notificationPreview;
    private final TextView notificationTime;
    private final ImageView messageAvatar;
    private String session = "";
    private boolean active;
    private boolean loading;
    private boolean socialLoading;
    private boolean closed;
    private boolean listening;
    private int requestGeneration;
    private int socialGeneration;
    private String boundAvatarUrl = "";

    LibrarySocialHubView(Activity activity) {
        this(activity, ZeroChillSocialRepository::loadInbox, LibrarySocialHubView::accountId);
    }

    LibrarySocialHubView(Activity activity, InboxLoader inboxLoader, SessionProvider sessionProvider) {
        super(activity);
        this.activity = activity;
        this.inboxLoader = inboxLoader;
        this.sessionProvider = sessionProvider;
        updatesPreferences = activity.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE);
        setOrientation(VERTICAL);
        setPadding(dp(2), 0, dp(2), 0);

        TextView heading = label("CONNECT", 11, UiPalette.PRIMARY);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setLetterSpacing(0.15f);
        LayoutParams headingParams = new LayoutParams(-1, -2);
        headingParams.setMargins(dp(8), 0, 0, dp(9));
        addView(heading, headingParams);

        MaterialCardView messages = card("Messages", "Private conversations", R.drawable.ic_action_message);
        LinearLayout messageContent = (LinearLayout) messages.getChildAt(0);
        LinearLayout messageDetails = (LinearLayout) messageContent.getChildAt(1);
        messageBadge = (TextView) messageContent.getChildAt(2);
        LinearLayout recent = new LinearLayout(activity);
        recent.setGravity(Gravity.CENTER_VERTICAL);
        recent.setMinimumHeight(dp(43));
        LayoutParams recentParams = new LayoutParams(-1, -2);
        recentParams.topMargin = dp(8);
        messageDetails.addView(recent, recentParams);

        messageAvatar = new ImageView(activity);
        messageAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        messageAvatar.setBackground(BrowseUi.rounded(activity, Color.rgb(29, 36, 42), 22));
        messageAvatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        messageAvatar.setClipToOutline(true);
        recent.addView(messageAvatar, new LayoutParams(dp(40), dp(40)));
        LinearLayout messageCopy = new LinearLayout(activity);
        messageCopy.setOrientation(VERTICAL);
        messageCopy.setPadding(dp(10), 0, 0, 0);
        recent.addView(messageCopy, new LayoutParams(0, -2, 1));
        LinearLayout nameRow = new LinearLayout(activity);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        messageCopy.addView(nameRow, new LayoutParams(-1, -2));
        messageName = label("No conversations yet", 13, Color.WHITE);
        singleLine(messageName);
        messageName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameRow.addView(messageName, new LayoutParams(0, -2, 1));
        messageTime = label("", 10, BrowseUi.MUTED);
        nameRow.addView(messageTime, new LayoutParams(-2, -2));
        messagePreview = label("Your DMs will appear here", 11, BrowseUi.MUTED);
        singleLine(messagePreview);
        messageCopy.addView(messagePreview, new LayoutParams(-1, -2));
        messages.setContentDescription("Messages. No unread messages.");
        messages.setOnClickListener(v -> activity.startActivity(new Intent(activity, ZeroChillInboxActivity.class)));
        LayoutParams messagesParams = new LayoutParams(-1, -2);
        messagesParams.bottomMargin = dp(7);
        addView(messages, messagesParams);

        MaterialCardView notifications = card("Notifications", "Recent activity", R.drawable.ic_more_update);
        LinearLayout notificationContent = (LinearLayout) notifications.getChildAt(0);
        LinearLayout notificationDetails = (LinearLayout) notificationContent.getChildAt(1);
        notificationBadge = (TextView) notificationContent.getChildAt(2);
        notificationPreview = label("No notifications yet", 12, BrowseUi.MUTED);
        singleLine(notificationPreview);
        LayoutParams previewParams = new LayoutParams(-1, -2);
        previewParams.topMargin = dp(6);
        notificationDetails.addView(notificationPreview, previewParams);
        notificationTime = label("", 10, BrowseUi.MUTED);
        notificationDetails.addView(notificationTime, new LayoutParams(-1, -2));
        notifications.setOnClickListener(v -> activity.startActivity(new Intent(activity, UpdateInboxActivity.class)));
        addView(notifications, new LayoutParams(-1, -2));

        LayoutParams bottomSpace = new LayoutParams(1, dp(22));
        addView(new View(activity), bottomSpace);
        refreshNotifications();
        showMessages(null, 0);
    }

    private MaterialCardView card(String title, String subtitle, int iconResource) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.rgb(20, 25, 30));
        card.setRadius(dp(19));
        card.setStrokeColor(Color.argb(70, 8, 146, 208));
        card.setStrokeWidth(dp(1));
        card.setCardElevation(0);
        card.setClickable(true);
        card.setFocusable(true);
        ZeroChillMotion.installPressFeedback(card);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(13), dp(11), dp(13), dp(11));
        ImageView symbol = new ImageView(activity);
        symbol.setImageResource(iconResource);
        symbol.setColorFilter(UiPalette.PRIMARY);
        symbol.setPadding(dp(8), dp(8), dp(8), dp(8));
        symbol.setBackground(BrowseUi.rounded(activity, Color.rgb(28, 45, 55), 13));
        row.addView(symbol, new LayoutParams(dp(35), dp(35)));
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(VERTICAL);
        details.setPadding(dp(10), 0, dp(7), 0);
        TextView titleView = label(title, 15, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        singleLine(titleView);
        details.addView(titleView);
        TextView subtitleView = label(subtitle, 10, BrowseUi.MUTED);
        details.addView(subtitleView);
        row.addView(details, new LayoutParams(0, -2, 1));
        TextView badge = label("", 10, Color.WHITE);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(24));
        badge.setPadding(dp(6), 0, dp(6), 0);
        badge.setBackground(BrowseUi.rounded(activity, UiPalette.PRIMARY, 12));
        badge.setVisibility(GONE);
        row.addView(badge, new LayoutParams(-2, dp(24)));
        card.addView(row);
        return card;
    }

    void setActive(boolean value) {
        if (closed) return;
        active = value;
        handler.removeCallbacks(poll);
        if (value) {
            refreshNotifications();
            refreshMessages();
            refreshSocialActivity();
            handler.postDelayed(poll, POLL_MS);
        }
    }

    void refresh() {
        refreshNotifications();
        refreshMessages();
        refreshSocialActivity();
    }

    private void refreshNotifications() {
        if (closed) return;
        List<UpdateInboxStore.Entry> entries = UpdateInboxStore.all(activity);
        int unread = 0;
        for (UpdateInboxStore.Entry entry : entries) if (!entry.read) unread++;
        setBadge(notificationBadge, unread);
        UpdateInboxStore.Entry latest = entries.isEmpty() ? null : entries.get(0);
        notificationPreview.setText(latest == null ? "No notifications yet"
                : latest.title + (latest.subtitle.isEmpty() ? "" : " · " + latest.subtitle));
        notificationPreview.setTextColor(latest != null && !latest.read
                ? Color.WHITE : BrowseUi.MUTED);
        notificationTime.setText(latest == null ? "" : (latest.read ? "Read" : "Unread")
                + " · " + time(latest.timestamp));
        ((View) notificationBadge.getParent().getParent()).setContentDescription(
                "Notifications. " + (unread == 0 ? "No unread notifications" : unread + " unread notifications")
                        + (latest == null ? "" : ". Latest: " + latest.title));
    }

    private String syncSession() {
        String current = sessionProvider.current(activity);
        if (current == null) current = "";
        if (!current.equals(session)) {
            session = current;
            requestGeneration++;
            socialGeneration++;
            loading = false;
            socialLoading = false;
            showMessages(null, 0);
            refreshNotifications();
        }
        return session;
    }

    private void refreshMessages() {
        if (closed) return;
        syncSession();
        if (session.isEmpty() || loading || !active) return;
        final String requestedSession = session;
        final int generation = ++requestGeneration;
        loading = true;
        inboxLoader.load(activity, (items, error) -> activity.runOnUiThread(() -> {
            if (closed || generation != requestGeneration) return;
            loading = false;
            String actualSession = sessionProvider.current(activity);
            if (!requestedSession.equals(actualSession)) {
                refreshMessages();
                return;
            }
            if (error != null || items == null) {
                if (messageName.getText().toString().equals("No conversations yet")) {
                    messagePreview.setText("Couldn't load messages. Tap to open inbox.");
                }
                return;
            }
            int unread = 0;
            for (ZeroChillSocialRepository.Conversation item : items) unread += item.unreadCount;
            showMessages(items.isEmpty() ? null : items.get(0), unread);
            ZeroChillMessageBadgeStore.setUnreadCount(activity, unread);
        }));
    }

    private void refreshSocialActivity() {
        if (!closed && active) SocialActivityCoordinator.requestRefresh(activity);
    }

    private void showMessages(ZeroChillSocialRepository.Conversation latest, int unread) {
        setBadge(messageBadge, unread);
        String name = "No conversations yet";
        String preview = session.isEmpty() ? "Sign in to see your messages" : "Your DMs will appear here";
        String stamp = "";
        if (latest != null && latest.profile != null && latest.lastMessage != null) {
            ZeroChillSocialRepository.PublicProfile profile = latest.profile;
            name = SocialUi.name(profile.displayName, profile.username);
            preview = latest.lastMessage.body;
            stamp = time(latest.lastMessage.createdAt);
        }
        String avatarUrl = latest == null || latest.profile == null ? ""
                : ZeroChillAccountRepository.avatarUrl(latest.profile.avatarPath);
        if (!avatarUrl.equals(boundAvatarUrl) || messageAvatar.getDrawable() == null) {
            boundAvatarUrl = avatarUrl;
            Glide.with(messageAvatar).clear(messageAvatar);
            messageAvatar.setImageResource(R.drawable.ic_more_account);
            messageAvatar.setColorFilter(UiPalette.PRIMARY);
            messageAvatar.setPadding(dp(9), dp(9), dp(9), dp(9));
            if (!avatarUrl.isEmpty()) {
                messageAvatar.clearColorFilter();
                messageAvatar.setPadding(0, 0, 0, 0);
                Glide.with(messageAvatar).load(avatarUrl).circleCrop()
                        .transition(ThumbnailFades.avatar())
                        .placeholder(R.drawable.ic_more_account)
                        .error(R.drawable.ic_more_account).into(messageAvatar);
            }
        }
        messageName.setText(name);
        messagePreview.setText(preview);
        messagePreview.setTextColor(latest != null && latest.unreadCount > 0
                ? Color.WHITE : BrowseUi.MUTED);
        messageTime.setText(stamp);
        ((View) messageBadge.getParent().getParent()).setContentDescription(
                "Messages. " + (unread == 0 ? "No unread messages" : unread + " unread messages")
                        + (latest == null ? "" : ". Latest from " + name + ": " + preview));
    }

    private static void setBadge(TextView badge, int count) {
        String next = count > 99 ? "99+" : String.valueOf(count);
        boolean changed = badge.getVisibility() != (count > 0 ? VISIBLE : GONE)
                || !next.contentEquals(badge.getText());
        badge.setText(next);
        badge.setVisibility(count > 0 ? VISIBLE : GONE);
        if (count > 0 && changed) {
            badge.animate().cancel();
            badge.setScaleX(0.88f);
            badge.setScaleY(0.88f);
            badge.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
        }
    }

    private static void singleLine(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    private static String accountId(Context context) {
        return ZeroChillSessionStore.currentUserId(context);
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private String time(String raw) {
        try { return time(Instant.parse(raw).toEpochMilli()); }
        catch (Exception ignored) { return ""; }
    }

    private String time(long value) {
        if (value <= 0) return "";
        long age = Math.max(0L, System.currentTimeMillis() - value);
        if (age < 60_000L) return "Now";
        if (age < 60L * 60_000L) return age / 60_000L + "m";
        if (age < 24L * 60L * 60_000L) return age / (60L * 60_000L) + "h";
        return DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatesPreferences.registerOnSharedPreferenceChangeListener(updatesListener);
        listening = true;
        if (active) {
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, POLL_MS);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (listening) updatesPreferences.unregisterOnSharedPreferenceChangeListener(updatesListener);
        listening = false;
        handler.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    void close() {
        closed = true;
        active = false;
        requestGeneration++;
        socialGeneration++;
        socialLoading = false;
        handler.removeCallbacksAndMessages(null);
        if (listening) updatesPreferences.unregisterOnSharedPreferenceChangeListener(updatesListener);
        listening = false;
        Glide.with(messageAvatar).clear(messageAvatar);
    }

    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
