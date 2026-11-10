package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.Player;
import java.util.Locale;

/** V2-sized photo pills and video dock over the existing native gallery/player. */
final class OnlyFapViewerChrome extends FrameLayout {
    interface Actions {
        void close(); void favorite(); void playback(); void fullscreen();
        void more(View anchor); void hide();
    }
    private final Actions actions;
    private final LinearLayout photoRow, videoDock;
    private final TextView photoName, photoPosition, videoTitle, videoPosition, elapsed, total;
    private final ImageButton photoFavorite, videoFavorite, play, fullscreen;
    private final SeekBar seek;
    private Player player;
    private boolean video, shown = true, scrubbing, favorite, canFullscreen, isFullscreen;
    private boolean artworkReady;
    private boolean menuHeld, held, disposed;
    private String key = "";
    private String title = "";
    private int position, count;
    private final Runnable hide = this::hideIfIdle;
    private void hideIfIdle() { if (shown && canAutoHide()) actions.hide(); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updatePlayback();
            if (shown && isAttachedToWindow()) postDelayed(this, 250L);
        }
    };
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) {
            updatePlayback(); restartHide();
        }
    };

    OnlyFapViewerChrome(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setTag("galleryViewerGlassControls");
        setPadding(dp(14), dp(4), dp(14), dp(14));
        photoRow = new LinearLayout(context);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        addView(photoRow, new LayoutParams(-1, -2));
        photoRow.addView(button(R.drawable.ic_more_close, "Close viewer", actions::close, true), cell());
        photoPosition = label(14, Color.WHITE);
        photoPosition.setGravity(Gravity.CENTER);
        LinearLayout photoPill = new LinearLayout(context); photoPill.setOrientation(LinearLayout.VERTICAL);
        photoPill.setGravity(Gravity.CENTER); photoPill.setBackground(glass(24));
        photoPill.setPadding(dp(8), 0, dp(8), 0);
        photoName = label(12, Color.WHITE); photoName.setGravity(Gravity.CENTER); photoName.setVisibility(GONE);
        photoPill.addView(photoName, new LinearLayout.LayoutParams(-1, -2));
        photoPill.addView(photoPosition, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams pill = new LinearLayout.LayoutParams(0, dp(48), 1f);
        pill.setMargins(dp(9), 0, dp(9), 0);
        photoRow.addView(photoPill, pill);
        photoFavorite = button(R.drawable.ic_action_heart_outline, "Favorite media", actions::favorite, true);
        photoRow.addView(photoFavorite, cell());
        ImageButton photoMore = button(R.drawable.ic_more_overflow, "More options", null, true);
        photoMore.setOnClickListener(v -> { setMenuOpen(true); actions.more(v); });
        LinearLayout.LayoutParams moreCell = cell(); moreCell.leftMargin = dp(9);
        photoRow.addView(photoMore, moreCell);

        videoDock = new LinearLayout(context);
        videoDock.setOrientation(LinearLayout.VERTICAL);
        videoDock.setPadding(dp(4), dp(2), dp(4), dp(2));
        videoDock.setBackground(glass(20));
        addView(videoDock, new LayoutParams(-1, -2));
        LinearLayout timeline = new LinearLayout(context);
        timeline.setPadding(dp(8), 0, dp(8), 0);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = label(10, 0xCCFFFFFF); total = label(10, 0x90FFFFFF);
        elapsed.setTypeface(android.graphics.Typeface.MONOSPACE);
        total.setTypeface(android.graphics.Typeface.MONOSPACE);
        timeline.addView(elapsed, new LinearLayout.LayoutParams(-2, dp(28)));
        seek = new SeekBar(context);
        seek.setMax(1000); seek.setMinimumHeight(0);
        seek.setContentDescription("Video position");
        int accent = AppearanceTheme.accent(context);
        GradientDrawable thumb = new GradientDrawable(); thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent); thumb.setSize(dp(8), dp(8)); seek.setThumb(thumb);
        GradientDrawable track = new GradientDrawable(); track.setColor(0x38FFFFFF); track.setSize(dp(1), dp(3));
        GradientDrawable active = new GradientDrawable(); active.setColor(accent); active.setSize(dp(1), dp(3));
        android.graphics.drawable.ClipDrawable progress = new android.graphics.drawable.ClipDrawable(active,
                Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{track, progress});
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress);
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL); layers.setLayerHeight(0, dp(3));
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL); layers.setLayerHeight(1, dp(3));
        seek.setProgressDrawable(layers); seek.setSplitTrack(false);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(0, dp(28), 1f);
        trackParams.setMargins(dp(5), 0, dp(5), 0); timeline.addView(seek, trackParams);
        timeline.addView(total, new LinearLayout.LayoutParams(-2, dp(28)));
        videoDock.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { scrubbing = true; hold(); }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && canSeek()) player.seekTo(player.getDuration() * bar.getProgress() / 1000L);
                scrubbing = false; interact();
            }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && player != null && player.getDuration() > 0) {
                    elapsed.setText(clock(player.getDuration() * value / 1000L));
                    if (!scrubbing && canSeek()) player.seekTo(player.getDuration() * value / 1000L);
                    interact();
                }
            }
        });
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(button(R.drawable.ic_more_close, "Close viewer", actions::close, false), cell());
        LinearLayout text = new LinearLayout(context); text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL); text.setMinimumWidth(0);
        videoTitle = label(13, Color.WHITE); videoTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        videoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        videoPosition = label(10, 0xA3FFFFFF); videoPosition.setGravity(Gravity.START);
        text.addView(videoTitle, new LinearLayout.LayoutParams(-1, -2));
        text.addView(videoPosition, new LinearLayout.LayoutParams(-1, -2));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        play = button(R.drawable.ic_player_play, "Play video", actions::playback, false);
        play.setColorFilter(accent);
        GradientDrawable prominent = new GradientDrawable(); prominent.setShape(GradientDrawable.OVAL);
        prominent.setColor((accent & 0xFFFFFF) | 0x29000000);
        play.setBackground(new android.graphics.drawable.InsetDrawable(prominent, dp(7)));
        play.setPadding(dp(14), dp(14), dp(14), dp(14)); row.addView(play, cell());
        videoFavorite = button(R.drawable.ic_action_heart_outline, "Favorite media", actions::favorite, false);
        row.addView(videoFavorite, cell());
        fullscreen = button(R.drawable.ic_action_fullscreen, "Fullscreen", actions::fullscreen, false);
        row.addView(fullscreen, cell());
        ImageButton more = button(R.drawable.ic_more_overflow, "More options", null, false);
        more.setOnClickListener(v -> { setMenuOpen(true); actions.more(v); }); row.addView(more, cell());
        videoDock.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            setPadding(dp(14) + safe.left, dp(4), dp(14) + safe.right, dp(14) + safe.bottom);
            return insets;
        });
        updateMedia("", "", 0, 0, false);
    }

    void updateMedia(String key, String title, int position, int count, boolean video) {
        if (!this.key.equals(key)) { favorite = false; artworkReady = video; }
        this.key = key; this.title = title; this.position = position; this.count = count; this.video = video;
        photoRow.setVisibility(video ? GONE : VISIBLE); videoDock.setVisibility(video ? VISIBLE : GONE);
        String countLabel = position > 0 ? position + " of " + count : "Media";
        photoPosition.setText(countLabel); photoPosition.setContentDescription(title + ", " + countLabel);
        videoTitle.setText(title.isEmpty() ? "Video" : title); videoPosition.setText(countLabel);
        updateFavorite(favorite); updatePlayback(); restartHide();
    }
    TextView creatorLabel() { return photoName; }
    void setCreatorAction(String name, Runnable action) {
        photoName.setText(name); photoName.setVisibility(name.isEmpty() ? GONE : VISIBLE);
        photoPosition.setTextSize(name.isEmpty() ? 14 : 10);
        photoName.setContentDescription(action == null ? name : "Open gallery for " + name);
        photoName.setOnClickListener(action == null ? null : v -> { interact(); action.run(); });
        photoName.setClickable(action != null); photoName.setFocusable(action != null);
    }
    void updateFavorite(boolean value) {
        favorite = value;
        for (ImageButton button : new ImageButton[]{photoFavorite, videoFavorite}) {
            button.setImageResource(value ? R.drawable.ic_action_heart_filled : R.drawable.ic_action_heart_outline);
            button.setColorFilter(value ? AppearanceTheme.accent(getContext()) : Color.WHITE);
            button.setContentDescription(value ? "Unfavorite media" : "Favorite media");
            button.setEnabled(position > 0); button.setAlpha(position > 0 ? 1f : .35f);
        }
    }
    void updatePlayer(Player incoming) {
        if (player != null) player.removeListener(playerListener);
        player = incoming;
        if (player != null) player.addListener(playerListener);
        updatePlayback(); restartHide();
    }
    void updateOrientation(boolean canFullscreen, boolean fullscreen) {
        this.canFullscreen = canFullscreen; isFullscreen = fullscreen;
        this.fullscreen.setVisibility(canFullscreen ? VISIBLE : GONE);
        this.fullscreen.setImageResource(fullscreen ? R.drawable.ic_action_fullscreen_exit : R.drawable.ic_action_fullscreen);
        this.fullscreen.setContentDescription(fullscreen ? "Exit fullscreen" : "Fullscreen");
    }
    void setVisible(boolean visible) {
        if (disposed) return;
        if (visible) held = false;
        else {
            // A canceled touch stream need not deliver SeekBar.onStopTrackingTouch.
            // Never carry a stale drag or window hold into the next resume.
            scrubbing = held = menuHeld = false;
            updatePlayback();
        }
        shown = visible; animate().cancel(); removeCallbacks(tick); removeCallbacks(hide);
        if (visible) { setVisibility(VISIBLE); setAlpha(1f); post(tick); restartHide(); }
        else if (ZeroChillMotion.animationsEnabled(getContext())) {
            animate().alpha(0f).setDuration(140L).withEndAction(() -> { if (!shown) setVisibility(INVISIBLE); }).start();
        } else { setAlpha(0f); setVisibility(INVISIBLE); }
    }
    void artworkReady() { artworkReady = true; restartHide(); }
    void setMenuOpen(boolean open) {
        if (disposed) return;
        menuHeld = open;
        if (open) removeCallbacks(hide); else restartHide();
    }
    void hold() { held = true; removeCallbacks(hide); }
    void interact() { held = false; restartHide(); }
    void dispose() {
        disposed = true; shown = false; menuHeld = held = scrubbing = false;
        removeCallbacks(tick); removeCallbacks(hide); animate().cancel(); updatePlayer(null);
    }
    private boolean canSeek() { return player != null && player.getDuration() > 0
            && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM); }
    private boolean canAutoHide() {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        return !disposed && !menuHeld && !held && !scrubbing && (manager == null || !manager.isTouchExplorationEnabled())
                && (video ? player != null && player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_READY : artworkReady);
    }
    private void restartHide() { removeCallbacks(hide); if (shown && canAutoHide() && isAttachedToWindow()) postDelayed(hide, 3200L); }
    private void updatePlayback() {
        boolean playing = player != null && player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED;
        play.setImageResource(playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
        play.setContentDescription(playing ? "Pause video" : "Play video");
        long duration = player == null ? 0 : Math.max(0, player.getDuration());
        long current = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        seek.setEnabled(canSeek());
        if (!scrubbing) { seek.setProgress(duration > 0 ? (int) (current * 1000L / duration) : 0); elapsed.setText(clock(current)); }
        total.setText(duration > 0 ? clock(duration) : "--:--");
    }
    static String clock(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000L;
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private ImageButton button(int icon, String description, Runnable action, boolean glass) {
        ImageButton button = new ImageButton(getContext()); button.setImageResource(icon);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int padding = dp(glass ? 13 : 15);
        button.setPadding(padding, padding, padding, padding); button.setColorFilter(Color.WHITE);
        button.setContentDescription(description); button.setBackground(glass ? glass(24)
                : new android.graphics.drawable.InsetDrawable(circle(), dp(9)));
        if (action != null) button.setOnClickListener(v -> { interact(); action.run(); });
        ZeroChillMotion.installPressFeedback(button); return button;
    }
    /** Readable static optics: never samples a playing video or continuously records the pager. */
    private android.graphics.drawable.Drawable glass(int radius) {
        Context c = getContext(); int accent = AppearanceTheme.accent(c);
        float transparency = AppearanceTheme.glassTransparency(c);
        boolean off = AppearanceTheme.glassPreset(c) == AppearanceTheme.GlassPreset.OFF;
        int opacity = off ? 250 : Math.round(205 + AppearanceTheme.menuTint(c) * 30 - transparency * 58);
        GradientDrawable base = new GradientDrawable(); base.setCornerRadius(dp(radius));
        base.setColor(Color.argb(opacity, 14, 18, 24));
        base.setStroke(dp(1), Color.argb(Math.round(28 + AppearanceTheme.glassEdge(c) * 35), 220, 236, 255));
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{off ? Color.TRANSPARENT : 0x16FFFFFF, Color.TRANSPARENT, off ? Color.TRANSPARENT : (accent & 0xFFFFFF) | 0x10000000});
        sheen.setCornerRadius(dp(radius));
        return new LayerDrawable(new android.graphics.drawable.Drawable[]{base, sheen});
    }
    private android.graphics.drawable.Drawable circle() {
        GradientDrawable shape = new GradientDrawable(); shape.setShape(GradientDrawable.OVAL); shape.setColor(0x0EFFFFFF); return shape;
    }
    private TextView label(int size, int color) {
        TextView label = new TextView(getContext()); label.setTextColor(color); label.setTextSize(size);
        label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER_VERTICAL); return label;
    }
    private LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(dp(48), dp(48)); }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    @Override protected void onMeasure(int width, int height) {
        int maximum = dp(video ? 620 : 550);
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(width), maximum), MeasureSpec.EXACTLY), height);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (shown) post(tick); restartHide(); ViewCompat.requestApplyInsets(this); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); removeCallbacks(hide); animate().cancel(); super.onDetachedFromWindow(); }
}
