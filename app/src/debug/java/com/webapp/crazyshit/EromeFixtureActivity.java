package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.util.UnstableApi;
import androidx.annotation.OptIn;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

/** Interactive local-only phone preview; no provider routes, favorites, or production writes. */
@OptIn(markerClass = UnstableApi.class)
public final class EromeFixtureActivity extends Activity {
    private static final String ALBUM = "album", FILTER = "filter", VIEWER = "viewer", POSITION = "position";
    private final Handler main = new Handler(Looper.getMainLooper());
    private int albumId = -1, limit = EromeFixtureCatalog.PAGE_SIZE, columns = 2, epoch, initialPosition;
    private String uploader = "", tag = "", filter = EromeFixtureCatalog.ALL;
    private boolean viewer, simulateFailure, fullscreen, pausedByUser;
    private LinearLayout root, cards;
    private RecyclerView grid;
    private BunkrGalleryPagerAdapter pagerAdapter;
    private ViewPager2 pager;
    private TextView viewerHeader;
    private ExoPlayer player;
    private long resumePosition;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppearanceTheme.applyAccentOverlay(this);
        GalleryMediaTransition.requestWindowFeature(this);
        ZeroChillUi.applySystemBars(this);
        EromeFixtureArtwork.register(this);
        viewer = getIntent().getBooleanExtra(VIEWER, false);
        albumId = getIntent().getIntExtra(ALBUM, -1);
        filter = getIntent().getStringExtra(FILTER);
        if (filter == null) filter = EromeFixtureCatalog.ALL;
        initialPosition = getIntent().getIntExtra(POSITION, 0);
        if (state != null) {
            albumId = state.getInt(ALBUM, albumId);
            filter = state.getString(FILTER, filter);
            uploader = state.getString("uploader", "");
            tag = state.getString("tag", "");
            limit = state.getInt("limit", limit);
            columns = state.getInt("columns", 2);
            initialPosition = state.getInt(POSITION, initialPosition);
            resumePosition = state.getLong("resume", 0L);
            pausedByUser = state.getBoolean("paused", false);
        }
        if (viewer) showViewer(state == null);
        else {
            GalleryMediaTransition.configureSource(this);
            render();
        }
    }

    private void detachGrid() {
        if (grid != null) { grid.setAdapter(null); grid = null; }
    }
    private void render() {
        epoch++;
        main.removeCallbacksAndMessages(null);
        detachGrid();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(10), dp(14), dp(12));
        setContentView(root);
        EromeFixtureCatalog.Album album = EromeFixtureCatalog.album(albumId);
        header(album != null ? album.title : uploader.isEmpty() ? "Erome" : uploader);
        TextView preview = label("PHASE 1  •  LOCAL SAMPLE CONTENT", 11, AppearanceTheme.accent(this));
        root.addView(preview);
        if (album == null) browse();
        else gallery(album);
    }
    private void header(String title) {
        LinearLayout bar = row();
        bar.addView(action("‹", this::onBackPressed), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView text = label(title, 23, Color.WHITE);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(text, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(bar);
    }
    private void browse() {
        if (!tag.isEmpty()) root.addView(action("#" + tag + "   ×", () -> { tag = ""; limit = 3; render(); }));
        if (uploader.isEmpty()) root.addView(label("Explore sample albums", 15, AppearanceTheme.secondaryText(this)));
        else root.addView(label("Uploader profile • " + EromeFixtureCatalog.matching(uploader, "").size() + " albums", 14, AppearanceTheme.secondaryText(this)));
        ScrollView scroll = new ScrollView(this);
        cards = new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        List<EromeFixtureCatalog.Album> matches = EromeFixtureCatalog.matching(uploader, tag);
        for (int i = 0; i < Math.min(limit, matches.size()); i++) albumCard(matches.get(i));
        if (matches.isEmpty()) cards.addView(label("No sample albums match.", 16, AppearanceTheme.secondaryText(this)));
        if (limit < matches.size()) cards.addView(action("Load more", this::loadMore));
        // Deliberate fixture-only controls for phone validation of empty/error states.
        root.addView(action("Test loading / retry", () -> { simulateFailure = true; loadMore(); }));
        root.addView(action("Test empty results", () -> { tag = "no-fixture-match"; limit = 3; render(); }));
    }
    private void loadMore() {
        final int token = epoch;
        final boolean fail = simulateFailure;
        simulateFailure = false;
        TextView status = label("Loading sample albums…", 14, AppearanceTheme.secondaryText(this));
        root.addView(status);
        main.postDelayed(() -> {
            if (token != epoch || isFinishing()) return;
            if (fail) {
                status.setText("Sample request failed. Tap to retry.");
                status.setTextColor(AppearanceTheme.accent(this));
                status.setOnClickListener(v -> { limit += EromeFixtureCatalog.PAGE_SIZE; render(); });
            } else { limit += EromeFixtureCatalog.PAGE_SIZE; render(); }
        }, 350L);
    }
    private void albumCard(EromeFixtureCatalog.Album album) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ZeroChillUi.glass(this));
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        cards.addView(card, params);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(EromeFixtureArtwork.bitmap(album.cover()));
        image.setContentDescription("Sample album cover: " + album.title);
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(160)));
        Runnable open = () -> { albumId = album.id; filter = EromeFixtureCatalog.ALL; render(); };
        image.setOnClickListener(v -> open.run());
        card.addView(action(album.title + "  ›", open));
        card.addView(label(album.pictures + " pictures  •  " + album.videos + " videos", 12, AppearanceTheme.secondaryText(this)));
        card.addView(action(album.uploader, () -> { uploader = album.uploader; tag = ""; limit = 3; render(); }));
        card.addView(action("#" + String.join("  #", album.tags), () -> { tag = album.tags.get(0); limit = 3; render(); }));
    }
    private void gallery(EromeFixtureCatalog.Album album) {
        root.addView(action(album.uploader + "  ›", () -> {
            albumId = -1; uploader = album.uploader; tag = ""; limit = 3; render();
        }));
        root.addView(label("#" + String.join("  #", album.tags), 13, AppearanceTheme.accent(this)));
        LinearLayout tabs = row();
        for (String mode : new String[]{EromeFixtureCatalog.ALL, EromeFixtureCatalog.PICTURES, EromeFixtureCatalog.VIDEOS}) {
            String title = mode.equals("all") ? "All" : mode.equals("pictures") ? "Pictures" : "Videos";
            TextView button = action(title, () -> { filter = mode; render(); });
            button.setTextColor(mode.equals(filter) ? AppearanceTheme.accent(this) : Color.WHITE);
            button.setSelected(mode.equals(filter));
            tabs.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(tabs);
        root.addView(action("Grid: " + columns + " columns", () -> { columns = columns == 2 ? 3 : 2; render(); }));
        List<NativeContentItem> items = album.media(filter);
        if (items.isEmpty()) {
            root.addView(label("No " + filter + " in this sample album.", 16, AppearanceTheme.secondaryText(this)));
            return;
        }
        grid = new RecyclerView(this);
        grid.setItemAnimator(null);
        grid.setLayoutManager(new StaggeredGridLayoutManager(columns, RecyclerView.VERTICAL));
        BunkrGalleryAdapter adapter = new BunkrGalleryAdapter(this, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) {
                Intent intent = new Intent(EromeFixtureActivity.this, EromeFixtureActivity.class)
                        .putExtra(VIEWER, true).putExtra(ALBUM, album.id)
                        .putExtra(FILTER, filter).putExtra(POSITION, position);
                GalleryMediaTransition.start(EromeFixtureActivity.this, intent, anchor, item);
            }
            public void onLongPress(NativeContentItem item, View anchor) {
                android.widget.Toast.makeText(EromeFixtureActivity.this, "Sample media only", android.widget.Toast.LENGTH_SHORT).show();
            }
        }, true);
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter.replace(items, false);
    }
    private void showViewer(boolean fresh) {
        EromeFixtureCatalog.Album album = EromeFixtureCatalog.album(albumId);
        if (album == null) { finish(); return; }
        List<NativeContentItem> items = album.media(filter);
        if (items.isEmpty()) { finish(); return; }
        initialPosition = Math.max(0, Math.min(items.size() - 1, initialPosition));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        pager = new ViewPager2(this);
        frame.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        viewerHeader = action("‹  " + album.title, this::onBackPressed);
        viewerHeader.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP);
        hp.setMargins(dp(8), dp(8), dp(8), 0);
        frame.addView(viewerHeader, hp);
        pagerAdapter = new BunkrGalleryPagerAdapter(this, new BunkrGalleryPagerAdapter.Listener() {
            public void onMediaTap(int position, NativeContentItem item) {
                if (item.isVideo()) pagerAdapter.toggleVideoControls(position);
                else viewerHeader.setVisibility(viewerHeader.isShown() ? View.GONE : View.VISIBLE);
            }
            public void onMediaLongPress(int position, NativeContentItem item) { }
            public void onResolvedImageFailed(int position, NativeContentItem item) { pagerAdapter.setFailed(position, true); }
            public void onVideoBack() { onBackPressed(); }
            public void onVideoFullscreen() {
                fullscreen = !fullscreen;
                getWindow().getDecorView().setSystemUiVisibility(fullscreen ? 5894 : 0);
                viewerHeader.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
                pagerAdapter.setVideoFullscreen(fullscreen);
            }
            public void onSharedElementReady(View target) { startPostponedEnterTransition(); }
        });
        String shared = fresh ? getIntent().getStringExtra(BunkrGalleryActivity.EXTRA_SHARED_ELEMENT_NAME) : null;
        if (shared != null && !shared.isEmpty()) {
            postponeEnterTransition();
            GalleryMediaTransition.configureViewer(this, () -> pagerAdapter.finishOpeningTransition());
            pagerAdapter.setInitialSharedElement(items.get(initialPosition).url, shared);
            main.postDelayed(() -> { startPostponedEnterTransition(); pagerAdapter.finishOpeningTransition(); }, 800L);
        }
        pagerAdapter.replace(items, Collections.emptyMap());
        pager.setAdapter(pagerAdapter);
        pager.setCurrentItem(initialPosition, false);
        setContentView(frame);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                if (position != initialPosition) { resumePosition = 0L; pausedByUser = false; }
                selectMedia(position);
            }
        });
        pager.post(() -> { if (!isFinishing() && player == null) selectMedia(pager.getCurrentItem()); });
    }

    private File sampleVideo() throws java.io.IOException {
        File path = new File(getCacheDir(), "erome-phase1-fixture-v1.mp4");
        if (path.length() == 1524) return path;
        try (InputStream in = getAssets().open("erome_phase1_sample.b64");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096]; int read;
            while ((read = in.read(chunk)) != -1) bytes.write(chunk, 0, read);
            byte[] video = android.util.Base64.decode(bytes.toByteArray(), android.util.Base64.DEFAULT);
            if (video.length != 1524) throw new java.io.IOException("Invalid fixture");
            try (FileOutputStream out = new FileOutputStream(path)) { out.write(video); }
        }
        return path;
    }
    private void selectMedia(int position) {
        if (pagerAdapter == null) return;
        releasePlayer();
        NativeContentItem item = pagerAdapter.itemAt(position);
        if (item == null || !item.isVideo()) return;
        try {
            ExoPlayer local = new ExoPlayer.Builder(this).build();
            player = local;
            pagerAdapter.activateVideo(position, local);
            local.addListener(new Player.Listener() {
                public void onRenderedFirstFrame() {
                    if (player == local) pagerAdapter.onVideoFirstFrame(position, local);
                }
                public void onPlaybackStateChanged(int state) {
                    if (player == local) pagerAdapter.onVideoBuffering(position, local, state == Player.STATE_BUFFERING);
                }
                public void onPlayerError(PlaybackException error) {
                    if (player == local) pagerAdapter.setFailed(position, true);
                }
            });
            local.setMediaItem(MediaItem.fromUri(Uri.fromFile(sampleVideo())));
            local.setRepeatMode(Player.REPEAT_MODE_ONE);
            local.prepare();
            if (resumePosition > 0) local.seekTo(resumePosition);
            local.setPlayWhenReady(!pausedByUser);
        } catch (Exception error) { releasePlayer(); pagerAdapter.setFailed(position, true); }
    }
    private void releasePlayer() {
        if (pagerAdapter != null) pagerAdapter.clearActiveVideo();
        if (player != null) { player.release(); player = null; }
    }
    @Override protected void onStop() {
        if (player != null) { resumePosition = player.getCurrentPosition(); pausedByUser = !player.getPlayWhenReady(); player.pause(); }
        super.onStop();
    }
    @Override protected void onRestart() {
        super.onRestart();
        if (player != null && !pausedByUser) player.play();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(ALBUM, albumId); state.putString(FILTER, filter);
        state.putString("uploader", uploader); state.putString("tag", tag);
        state.putInt("limit", limit); state.putInt("columns", columns);
        if (pager != null) state.putInt(POSITION, pager.getCurrentItem());
        state.putLong("resume", player == null ? resumePosition : player.getCurrentPosition());
        state.putBoolean("paused", player == null ? pausedByUser : !player.getPlayWhenReady());
    }
    @Override public void onBackPressed() {
        if (viewer) {
            if (player != null) player.pause();
            if (pager != null && pager.getCurrentItem() == initialPosition) finishAfterTransition();
            else finish();
        } else if (albumId >= 0) { albumId = -1; render(); }
        else if (!uploader.isEmpty() || !tag.isEmpty()) { uploader = ""; tag = ""; limit = 3; render(); }
        else finish();
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        releasePlayer();
        if (pager != null) pager.setAdapter(null);
        detachGrid();
        super.onDestroy();
    }
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(dp(8), dp(5), dp(8), dp(5));
        return view;
    }
    private TextView action(String value, Runnable click) {
        TextView view = label(value, 14, Color.WHITE);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44));
        view.setBackground(ZeroChillUi.glass(this));
        view.setClickable(true); view.setFocusable(true); view.setOnClickListener(v -> click.run());
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
