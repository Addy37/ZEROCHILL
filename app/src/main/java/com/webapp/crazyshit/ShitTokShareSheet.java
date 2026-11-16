package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** ShitTok's on-demand central menu. Does not own, resize, pause or recreate a player. */
final class ShitTokShareSheet extends BottomSheetDialog {
    static final class Actions {
        Runnable download, favorite, notInterested, autoScroll, blockCreator, report, comments, details;
        boolean saved, autoScrollOn, canBlock;
    }
    private final Activity activity;
    private final NativeContentItem item;
    private final CrazyShitRepository.StreamInfo stream;
    private final String owner;
    private final LinearLayout content, contacts, searchArea;
    private final TextView status, progressText, cancel;
    private final ProgressBar progress;
    private final Map<String, String> messageIds = new HashMap<>();
    private ShitTokVideoShare.Job preparation;
    private boolean sending;
    private int searchGeneration;

    ShitTokShareSheet(Activity activity, NativeContentItem item,
                     CrazyShitRepository.StreamInfo stream, Actions actions) {
        super(activity);
        this.activity = activity;
        this.item = item;
        this.stream = stream;
        owner = ZeroChillSessionStore.currentUserId(activity);
        setDismissWithAnimation(true);
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(8), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        float r = dp(24);
        background.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        panel.setBackground(background);
        panel.setClipToOutline(true);
        View handle = new View(activity);
        handle.setBackground(round(0xFF45454D, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(8);
        panel.addView(handle, hp);
        LinearLayout header = row();
        TextView title = text("Share & actions", 18, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("✕", 20, 0xFFBDBDC6);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close share menu");
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(false);
        content = column();
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        section("Send to ZEROCHILL");
        HorizontalScrollView contactScroll = new HorizontalScrollView(activity);
        contactScroll.setHorizontalScrollBarEnabled(false);
        contacts = row();
        contactScroll.addView(contacts);
        content.addView(contactScroll);
        LinearLayout searchButton = tile(android.R.drawable.ic_menu_search, "Find recipient", this::toggleSearch);
        contacts.addView(searchButton, new LinearLayout.LayoutParams(dp(88), dp(86)));
        status = text(owner.isEmpty() ? "Sign in from Library to send video cards."
                : "Loading recent contacts…", 12, 0xFFBDBDC6);
        status.setPadding(0, dp(4), 0, dp(8));
        content.addView(status);
        searchArea = column();
        searchArea.setVisibility(View.GONE);
        content.addView(searchArea);
        buildSearch();
        section("Share externally");
        LinearLayout external = row();
        addExternal(external, R.drawable.ic_shittok_rail_share, "Share Video", () -> prepareVideo(null));
        addExternal(external, android.R.drawable.ic_menu_set_as, "Copy Link", this::copyLink);
        addExternal(external, android.R.drawable.ic_menu_send, "WhatsApp", () -> prepareVideo("com.whatsapp"));
        addExternal(external, android.R.drawable.ic_dialog_email, "Messages", () -> {
            String app = android.provider.Telephony.Sms.getDefaultSmsPackage(activity);
            if (app == null) { status.setText("No default Messages app. Use More Apps to choose one."); return; }
            prepareVideo(app);
        });
        addExternal(external, R.drawable.ic_more_overflow, "More Apps", () -> prepareVideo(null));
        HorizontalScrollView externalScroll = new HorizontalScrollView(activity);
        externalScroll.setHorizontalScrollBarEnabled(false);
        externalScroll.addView(external);
        content.addView(externalScroll);
        TextView mediaHint = text("Sends a video file. Copy Link copies the original source link.\nSome streams can't be shared as files.", 12, 0xFFBDBDC6);
        content.addView(mediaHint);
        progressText = text("", 12, AppearanceTheme.accent(activity));
        content.addView(progressText);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
        progress.setVisibility(View.GONE);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        cancel = text("Cancel preparation", 14, AppearanceTheme.accent(activity));
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(dp(48));
        cancel.setVisibility(View.GONE);
        cancel.setOnClickListener(v -> {
            cancelPreparation();
            progressText.setText("Preparation canceled.");
        });
        content.addView(cancel);

        section("Video actions");
        LinearLayout gridRow = row();
        addAction(gridRow, R.drawable.ic_shittok_rail_download, "Download", actions.download);
        addAction(gridRow, actions.saved ? R.drawable.ic_shittok_rail_bookmark_filled : R.drawable.ic_shittok_rail_bookmark,
                actions.saved ? "Remove favorite" : "Add to Favorites", actions.favorite);
        addAction(gridRow, android.R.drawable.ic_menu_close_clear_cancel, "Not Interested", actions.notInterested);
        content.addView(gridRow);
        gridRow = row();
        addAction(gridRow, android.R.drawable.ic_media_play, "Auto Scroll · " + (actions.autoScrollOn ? "ON" : "OFF"), actions.autoScroll);
        addAction(gridRow, android.R.drawable.ic_delete, "Block Creator", actions.canBlock ? actions.blockCreator : null);
        addAction(gridRow, android.R.drawable.ic_menu_report_image, "Report", actions.report);
        content.addView(gridRow);
        gridRow = row();
        addAction(gridRow, R.drawable.ic_shittok_rail_comments, "Comments", actions.comments);
        addAction(gridRow, android.R.drawable.ic_menu_info_details, "Video details", actions.details);
        gridRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        content.addView(gridRow);

        int height = Math.min(dp(660), (int) (activity.getResources().getDisplayMetrics().heightPixels * .88f));
        setContentView(panel, new ViewGroup.LayoutParams(-1, height));
        ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            panel.setPadding(dp(16) + safe.left, dp(8), dp(16) + safe.right, dp(12) + safe.bottom);
            return insets;
        });
    }

    @Override public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            window.setNavigationBarColor(Color.BLACK);
            androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView())
                    .setAppearanceLightNavigationBars(false);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        View bottom = findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottom != null) bottom.setBackgroundColor(Color.TRANSPARENT);
        getBehavior().setSkipCollapsed(true);
        getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        if (!owner.isEmpty()) loadRecent();
    }

    @Override public void dismiss() { cancelPreparation(); searchGeneration++; super.dismiss(); }
    @Override public void cancel() { cancelPreparation(); searchGeneration++; super.cancel(); }

    private boolean current() {
        return usable()
                && owner.equals(ZeroChillSessionStore.currentUserId(activity));
    }

    private boolean usable() {
        return isShowing() && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void cancelPreparation() {
        if (preparation != null) preparation.cancel();
        preparation = null;
        if (progress != null) progress.setVisibility(View.GONE);
        if (cancel != null) cancel.setVisibility(View.GONE);
    }

    private void prepareVideo(String target) {
        if (preparation != null) return;
        progressText.setText("Preparing video…");
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.VISIBLE);
        preparation = ShitTokVideoShare.prepare(activity, item, stream, new ShitTokVideoShare.Callback() {
            @Override public void onProgress(long bytes, long total) {
                if (!usable()) return;
                progress.setIndeterminate(total <= 0);
                if (total > 0) progress.setProgress((int) (bytes * 100 / total));
                progressText.setText("Preparing video · " + (bytes / 1024 / 1024) + " MB");
            }
            @Override public void onReady(ShitTokVideoShare.Result result) {
                preparation = null;
                progress.setVisibility(View.GONE);
                cancel.setVisibility(View.GONE);
                if (!usable()) return;
                progressText.setText("Video ready to share.");
                if (ShitTokVideoShare.launch(activity, result, target)) dismiss();
            }
            @Override public void onError(String error) {
                preparation = null;
                progress.setVisibility(View.GONE);
                cancel.setVisibility(View.GONE);
                if (usable()) progressText.setText(error);
            }
        });
    }

    private void copyLink() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { status.setText("Clipboard is unavailable."); return; }
        clipboard.setPrimaryClip(ClipData.newPlainText("Video source link", item.url));
        status.setText("Source link copied. Use Share Video to send the video file.");
    }

    private void loadRecent() {
        ZeroChillSocialRepository.loadInbox(activity, (inbox, error) -> activity.runOnUiThread(() -> {
            if (!current()) return;
            if (error != null || inbox == null) { status.setText("Couldn't load contacts. Try recipient search."); return; }
            status.setText("Video cards play in ZEROCHILL. Both users need this beta; source availability applies.");
            int count = Math.min(12, inbox.size());
            for (int i = 0; i < count; i++) {
                ZeroChillSocialRepository.PublicProfile profile = inbox.get(i).profile;
                LinearLayout tile = tile(android.R.drawable.ic_menu_myplaces, "@" + profile.username, () -> sendCard(profile));
                contacts.addView(tile, new LinearLayout.LayoutParams(dp(88), dp(86)));
            }
        }));
    }

    private void toggleSearch() {
        if (owner.isEmpty()) { status.setText("Sign in from Library to send video cards."); return; }
        searchArea.setVisibility(searchArea.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void buildSearch() {
        EditText query = new ZeroChillEditText(activity);
        query.setHint("ZEROCHILL username");
        query.setHintTextColor(0xFFBDBDC6);
        query.setTextColor(Color.WHITE);
        query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(21)});
        searchArea.addView(query, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView search = text("Search usernames", 14, AppearanceTheme.accent(activity));
        search.setMinHeight(dp(48));
        search.setGravity(Gravity.CENTER_VERTICAL);
        searchArea.addView(search);
        LinearLayout results = column();
        searchArea.addView(results);
        search.setOnClickListener(v -> {
            String term = query.getText().toString().trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
            final int generation = ++searchGeneration;
            results.removeAllViews();
            if (!term.matches("[a-z0-9_]{2,20}")) { status.setText("Enter 2–20 username characters: letters, numbers or _."); return; }
            status.setText("Searching…");
            ZeroChillSocialRepository.searchProfiles(activity, term, (profiles, error) -> activity.runOnUiThread(() -> {
                if (!current() || generation != searchGeneration) return;
                String latest = query.getText().toString().trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
                if (!term.equals(latest)) { status.setText("Search again for the new username."); return; }
                if (error != null || profiles == null) { status.setText("Couldn't search. Try again."); return; }
                status.setText(profiles.isEmpty() ? "No matching recipients." : "Tap a recipient to send this video card.");
                for (ZeroChillSocialRepository.PublicProfile profile : profiles) {
                    if (profile.currentUser) continue;
                    TextView send = text("Send to @" + profile.username, 14, Color.WHITE);
                    send.setMinHeight(dp(48));
                    send.setGravity(Gravity.CENTER_VERTICAL);
                    send.setOnClickListener(button -> sendCard(profile));
                    results.addView(send);
                }
            }));
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            search.performClick();
            return true;
        });
    }

    private void sendCard(ZeroChillSocialRepository.PublicProfile recipient) {
        if (!current() || owner.isEmpty() || sending) return;
        String body = ZeroChillVideoCard.encode(item);
        if (body == null) { status.setText("This source doesn't support ZEROCHILL video cards."); return; }
        sending = true;
        status.setText("Sending video card to @" + recipient.username + "…");
        String id = messageIds.computeIfAbsent(recipient.userId, key -> UUID.randomUUID().toString());
        ZeroChillSocialRepository.sendDirectMessage(activity, recipient.userId, id, body, (message, error) -> activity.runOnUiThread(() -> {
            sending = false;
            if (error == null) messageIds.remove(recipient.userId);
            if (!current()) return;
            status.setText(error == null ? "Video card sent to @" + recipient.username + "."
                    : "Couldn't send this video card. Check your connection or messaging permissions, then retry.");
        }));
    }

    private void section(String title) {
        TextView label = text(title, 14, Color.WHITE);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(14), 0, dp(8));
        content.addView(label);
    }
    private void addExternal(LinearLayout row, int icon, String label, Runnable action) {
        row.addView(tile(icon, label, action), new LinearLayout.LayoutParams(dp(76), dp(86)));
    }
    private void addAction(LinearLayout row, int icon, String label, Runnable action) {
        LinearLayout tile = tile(icon, label, action == null ? null : () -> { dismiss(); action.run(); });
        row.addView(tile, new LinearLayout.LayoutParams(0, dp(90), 1));
    }
    private LinearLayout tile(int icon, String label, Runnable action) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(3), dp(4), dp(3), dp(4));
        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
        image.setPadding(dp(11), dp(11), dp(11), dp(11));
        image.setBackground(round(0xFF17171B, 24));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(image, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView text = text(label, 11, Color.WHITE);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setPadding(0, dp(6), 0, 0);
        tile.addView(text, new LinearLayout.LayoutParams(-1, -2));
        tile.setContentDescription(label);
        tile.setFocusable(action != null);
        tile.setEnabled(action != null);
        tile.setAlpha(action == null ? .4f : 1f);
        if (action != null) tile.setOnClickListener(v -> action.run());
        ZeroChillMotion.installPressFeedback(tile);
        return tile;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private TextView text(String value, int size, int color) {
        return BrowseUi.text(activity, value, size, color);
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
