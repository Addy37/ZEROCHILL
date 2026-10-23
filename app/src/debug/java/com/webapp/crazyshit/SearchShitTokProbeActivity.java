package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.lang.reflect.Field;
import java.util.*;

/** Real feed/player/texture and Search, with the existing local video and no source requests. */
public final class SearchShitTokProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ChaosFeedView feed;
    private SearchPanel search;
    private SearchGlassOwner glassOwner;
    private FrameLayout root;
    private FrostedNavigationLayout shell;
    private PlayerView surface;
    private ExoPlayer player;
    private boolean manualPause;
    private long deadline;
    private final String url = "https://example.invalid/search-video";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        glassOwner = new SearchGlassOwner(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", true).apply();
        getSharedPreferences("chaos_feed", 0).edit().putBoolean("muted", false).apply();
        manualPause = getIntent().getBooleanExtra("manual_pause", false);
        root = new FrameLayout(this);
        shell = new FrostedNavigationLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this); title.setText("ShitTok Search video check");
        title.setTextColor(Color.WHITE); title.setGravity(Gravity.CENTER);
        GlassHeaderView header = new GlassHeaderView(this, title);
        shell.addView(header, new LinearLayout.LayoutParams(-1, BrowseUi.dp(this, 56)));
        shell.setFrostedHeaderView(header);
        feed = new ChaosFeedView(this, item -> {}, false);
        shell.addView(feed, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        try {
            ArrayList<NativeContentItem> items = field(feed, "items");
            items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA, "Local video", url, "", "", "", ""));
            Map<String, CrazyShitRepository.StreamInfo> cache = field(feed, "streamCache");
            String mediaUrl = "https://example.invalid/local-search-probe.mp4";
            seedVideoCache(mediaUrl);
            cache.put(url, new CrazyShitRepository.StreamInfo(mediaUrl, url, "Local video"));
            ViewPager2 pager = field(feed, "pager"); pager.getAdapter().notifyDataSetChanged();
            feed.setActive(true);
            deadline = SystemClock.uptimeMillis() + 15000;
            handler.postDelayed(this::awaitVideo, 100);
        } catch (Exception e) { fail(e); }
    }
    private void awaitVideo() {
        try {
            ViewPager2 pager = field(feed, "pager");
            RecyclerView.ViewHolder holder = ((RecyclerView) pager.getChildAt(0)).findViewHolderForAdapterPosition(0);
            if (holder != null) surface = field(holder, "playerView");
            player = surface == null ? null : (ExoPlayer) surface.getPlayer();
            if (player == null || player.getCurrentPosition() < 150 || !hasVideo()) {
                check(SystemClock.uptimeMillis() < deadline, "video never rendered");
                handler.postDelayed(this::awaitVideo, 100); return;
            }
            if (manualPause) ((ShitTokPlaybackOwner) field(feed, "playbackOwner")).setUserPaused(url, true);
            feed.onSearchOpened();
            check(surface.getPlayer() == player, "Search detached current video");
            RecentSearchStore.clear(this);
            for (String query : new String[]{"One", "Two", "Three"}) RecentSearchStore.remember(this, query);
            search = new SearchPanel(this, new android.content.Intent(), true, this::closeSearch);
            search.create(null); search.setEmptyBackdrop(shell.pageBackdropForOverlay());
            root.addView(search.view, new FrameLayout.LayoutParams(-1, -1));
            search.field().clearFocus();
            handler.postDelayed(() -> {
                try {
                    check(surface.getPlayer() == player && hasVideo(), "Search blacked out video");
                    check(!player.getPlayWhenReady() && player.getVolume() == 0f, "audio remained active");
                    long at = player.getCurrentPosition();
                    handler.postDelayed(() -> {
                        try {
                            check(Math.abs(player.getCurrentPosition() - at) < 50, "video advanced behind Search");
                            check(hasVideo(), "paused texture was cleared");
                            android.util.Log.i("ZeroChillSearchVideo", "READY retained paused video; manual=" + manualPause);
                        } catch (Exception e) { fail(e); }
                    }, 500);
                } catch (Exception e) { fail(e); }
            }, 500);
        } catch (Exception e) { fail(e); }
    }
    private void seedVideoCache(String mediaUrl) throws Exception {
        byte[] bytes;
        try (java.io.InputStream input = getAssets().open("glass-probe.mp4")) { bytes = input.readAllBytes(); }
        androidx.media3.datasource.cache.Cache cache = ShitTokMediaCache.get(this);
        androidx.media3.datasource.cache.CacheDataSink sink = new androidx.media3.datasource.cache.CacheDataSink(cache, 1024 * 1024);
        sink.open(new androidx.media3.datasource.DataSpec.Builder().setUri(mediaUrl).setLength(bytes.length).build());
        try { sink.write(bytes, 0, bytes.length); } finally { sink.close(); }
        androidx.media3.datasource.cache.ContentMetadataMutations metadata = new androidx.media3.datasource.cache.ContentMetadataMutations();
        androidx.media3.datasource.cache.ContentMetadataMutations.setContentLength(metadata, bytes.length);
        cache.applyContentMetadataMutations(mediaUrl, metadata);
    }
    private boolean hasVideo() {
        if (surface == null || !(surface.getVideoSurfaceView() instanceof TextureView)) return false;
        Bitmap bitmap = ((TextureView) surface.getVideoSurfaceView()).getBitmap(24, 40);
        if (bitmap == null) return false;
        int lit = 0;
        for (int y=0; y<40; y++) for (int x=0; x<24; x++) {
            int c=bitmap.getPixel(x,y);
            if (Color.red(c)+Color.green(c)+Color.blue(c)>90) lit++;
        }
        bitmap.recycle();
        return lit > 100;
    }
    private void closeSearch() {
        if (search == null) return;
        root.removeView(search.view); search.close(); search = null;
        long at = player.getCurrentPosition();
        feed.onHostResume();
        handler.postDelayed(() -> {
            try {
                check(surface.getPlayer() == player && hasVideo(), "return replaced video");
                check(manualPause ? !player.getPlayWhenReady() : player.getPlayWhenReady(), "manual pause intent changed");
                if (manualPause) check(Math.abs(player.getCurrentPosition()-at)<50, "manual pause advanced");
                android.util.Log.i("ZeroChillSearchVideo", "PASS retained frame, silent Search, same player return; manual=" + manualPause);
            } catch (Exception e) { fail(e); }
        }, 500);
    }
    @Override public void onBackPressed() { if (search != null) search.back(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); if (feed != null) feed.onHostPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (search != null) search.close();
        if (feed != null) feed.close();
        if (glassOwner != null) glassOwner.close();
        super.onDestroy();
    }
    @SuppressWarnings("unchecked") private <T> T field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(value);
    }
    private void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void fail(Exception e) { android.util.Log.e("ZeroChillSearchVideo", "FAIL", e); throw new IllegalStateException(e); }
}
