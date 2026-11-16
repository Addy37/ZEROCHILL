package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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

/** ShitTok's on-demand central menu. Does not own, resize, pause or recreate a player. */
final class ShitTokShareSheet extends BottomSheetDialog {
    static final class Actions {
        Runnable download, favorite, notInterested, autoScroll, blockCreator, report, comments, details;
        boolean saved, autoScrollOn, canBlock;
    }
    private final Activity activity;
    private final NativeContentItem item;
    private final CrazyShitRepository.StreamInfo stream;
    private final LinearLayout content;
    private final TextView progressText, cancel;
    private final ProgressBar progress;
    private ShitTokVideoShare.Job preparation;

    ShitTokShareSheet(Activity activity, NativeContentItem item,
                     CrazyShitRepository.StreamInfo stream, Actions actions) {
        super(activity);
        this.activity = activity;
        this.item = item;
        this.stream = stream;
        setDismissWithAnimation(true);
        LinearLayout panel = column();
        panel.setPadding(dp(12), dp(6), dp(12), dp(8));
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
        hp.bottomMargin = dp(4);
        panel.addView(handle, hp);
        LinearLayout header = row();
        TextView title = text("Share & actions", 18, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("✕", 20, 0xFFBDBDC6);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close share menu");
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        panel.addView(header);

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(false);
        content = column();
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        section("Share externally");
        LinearLayout external = row();
        addExternal(external, R.drawable.ic_shittok_rail_share, "Share Video", () -> prepareVideo(null));
        addExternal(external, android.R.drawable.ic_menu_set_as, "Copy Link", this::copyLink);
        addExternal(external, android.R.drawable.ic_menu_send, "WhatsApp", () -> prepareVideo("com.whatsapp"));
        addExternal(external, android.R.drawable.ic_dialog_email, "Messages", () -> {
            String app = android.provider.Telephony.Sms.getDefaultSmsPackage(activity);
            if (app == null) { showStatus("No default Messages app. Use More Apps to choose one."); return; }
            prepareVideo(app);
        });
        addExternal(external, R.drawable.ic_more_overflow, "More Apps", () -> prepareVideo(null));
        HorizontalScrollView externalScroll = new HorizontalScrollView(activity);
        externalScroll.setHorizontalScrollBarEnabled(false);
        externalScroll.addView(external);
        content.addView(externalScroll);
        progressText = text("", 12, AppearanceTheme.accent(activity));
        progressText.setVisibility(View.GONE);
        content.addView(progressText);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
        progress.setVisibility(View.GONE);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        cancel = text("Cancel preparation", 14, AppearanceTheme.accent(activity));
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(dp(36));
        cancel.setVisibility(View.GONE);
        cancel.setOnClickListener(v -> {
            cancelPreparation();
            showStatus("Preparation canceled.");
        });
        content.addView(cancel);

        section("Video actions");
        LinearLayout actionRow = row();
        addAction(actionRow, R.drawable.ic_shittok_rail_download, "Download", actions.download);
        addAction(actionRow, actions.saved ? R.drawable.ic_shittok_rail_bookmark_filled : R.drawable.ic_shittok_rail_bookmark,
                actions.saved ? "Favorited" : "Favorite", actions.favorite);
        addAction(actionRow, android.R.drawable.ic_menu_close_clear_cancel, "Not Interested", actions.notInterested);
        addAction(actionRow, android.R.drawable.ic_media_play, "Auto Scroll · " + (actions.autoScrollOn ? "ON" : "OFF"), actions.autoScroll);
        addAction(actionRow, android.R.drawable.ic_delete, "Block Creator", actions.canBlock ? actions.blockCreator : null);
        addAction(actionRow, android.R.drawable.ic_menu_report_image, "Report", actions.report);
        HorizontalScrollView actionScroll = new HorizontalScrollView(activity);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.addView(actionRow);
        content.addView(actionScroll);
        LinearLayout secondaryRow = row();
        addAction(secondaryRow, R.drawable.ic_shittok_rail_comments, "Comments", actions.comments);
        addAction(secondaryRow, android.R.drawable.ic_menu_info_details, "Video details", actions.details);
        content.addView(secondaryRow);

        int height = Math.min(dp(340), (int) (activity.getResources().getDisplayMetrics().heightPixels * .65f));
        setContentView(panel, new ViewGroup.LayoutParams(-1, height));
        ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            panel.setPadding(dp(12) + safe.left, dp(6), dp(12) + safe.right, dp(8) + safe.bottom);
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
    }

    @Override public void dismiss() { cancelPreparation(); super.dismiss(); }
    @Override public void cancel() { cancelPreparation(); super.cancel(); }

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
        showStatus("Preparing video…");
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.VISIBLE);
        preparation = ShitTokVideoShare.prepare(activity, item, stream, new ShitTokVideoShare.Callback() {
            @Override public void onProgress(long bytes, long total) {
                if (!usable()) return;
                progress.setIndeterminate(total <= 0);
                if (total > 0) progress.setProgress((int) (bytes * 100 / total));
                showStatus("Preparing video · " + (bytes / 1024 / 1024) + " MB");
            }
            @Override public void onReady(ShitTokVideoShare.Result result) {
                preparation = null;
                progress.setVisibility(View.GONE);
                cancel.setVisibility(View.GONE);
                if (!usable()) return;
                showStatus("Video ready to share.");
                if (ShitTokVideoShare.launch(activity, result, target)) dismiss();
            }
            @Override public void onError(String error) {
                preparation = null;
                progress.setVisibility(View.GONE);
                cancel.setVisibility(View.GONE);
                if (usable()) showStatus(error);
            }
        });
    }

    private void copyLink() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) { showStatus("Clipboard is unavailable."); return; }
        clipboard.setPrimaryClip(ClipData.newPlainText("Video source link", item.url));
        showStatus("Source link copied.");
    }

    private void showStatus(String message) {
        progressText.setText(message);
        progressText.setVisibility(View.VISIBLE);
    }

    private void section(String title) {
        TextView label = text(title, 14, Color.WHITE);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(8), 0, dp(4));
        content.addView(label);
    }
    private void addExternal(LinearLayout row, int icon, String label, Runnable action) {
        float scale = Math.max(1f, activity.getResources().getConfiguration().fontScale);
        row.addView(tile(icon, label, action), new LinearLayout.LayoutParams(dp(Math.round(68 * scale)), tileHeight()));
    }
    private void addAction(LinearLayout row, int icon, String label, Runnable action) {
        LinearLayout tile = tile(icon, label, action == null ? null : () -> { dismiss(); action.run(); });
        float scale = Math.max(1f, activity.getResources().getConfiguration().fontScale);
        row.addView(tile, new LinearLayout.LayoutParams(dp(Math.round(60 * scale)), tileHeight()));
    }
    private int tileHeight() {
        float scale = Math.max(1f, activity.getResources().getConfiguration().fontScale);
        return dp(70 + Math.round((scale - 1f) * 28));
    }
    private LinearLayout tile(int icon, String label, Runnable action) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(2), dp(2), dp(2), dp(2));
        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        image.setBackground(round(0xFF17171B, 24));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(image, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView text = text(label.replace(" · ", "\n"), 10, Color.WHITE);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setPadding(0, dp(3), 0, 0);
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
