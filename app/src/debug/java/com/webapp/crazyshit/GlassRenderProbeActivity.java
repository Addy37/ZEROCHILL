package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Debug-only renderer fixture. Never packaged in signed release candidates. */
public final class GlassRenderProbeActivity extends Activity {
    private ZeroChillBottomNavigationView nav;
    private Backdrop backdrop;
    private ScrollView showsScroll;
    private int mode;
    private SearchPanel searchPanel;
    private androidx.media3.exoplayer.ExoPlayer probePlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean alive;
    private int frame;
    private long start;
    private final Runnable animate = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            float phase = ((SystemClock.uptimeMillis() - start) % 8000) / 8000f;
            if (mode == 2) {
                if (showsScroll != null) showsScroll.scrollTo(0, (int)(dp(650) * (1f - Math.abs(phase * 2f - 1f))));
            } else if (backdrop != null) {
                backdrop.phase = phase;
                backdrop.invalidate();
            }
            frame++;
            handler.postDelayed(this, 16);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        FrostedNavigationLayout shell = new FrostedNavigationLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            shell.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        mode = getIntent().getIntExtra("mode", 0);
        if (mode == 3) {
            androidx.media3.ui.PlayerView player = (androidx.media3.ui.PlayerView)
                getLayoutInflater().inflate(R.layout.view_video_player_texture, shell, false);
            player.setUseController(false);
            player.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            probePlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            player.setPlayer(probePlayer);
            probePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri("asset:///glass-probe.mp4"));
            probePlayer.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
            probePlayer.setVolume(0f); probePlayer.prepare(); probePlayer.play();
            shell.addView(player, new LinearLayout.LayoutParams(-1, 0, 1));
            android.util.Log.i("ZeroChillGlass", "probe video surface=" + player.getVideoSurfaceView().getClass().getSimpleName());
        } else if (mode == 2) {
            // Actual production Shows hierarchy, rails, artwork ImageViews and text.
            // Local artwork makes this reproducible without depending on adult sources.
            ShowsHubView shows = new ShowsHubView(this, item -> {}, item -> {}, item -> {});
            java.util.List<NativeContentItem> items = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) items.add(new NativeContentItem(
                NativeContentItem.KIND_SERIES, "ZEROCHILL artwork " + (i + 1),
                "https://example.invalid/probe/" + i, "", "", "", ""));
            shows.setThisWeek(items); shows.setCrazyShit(items); shows.setEfukt(items);
            shows.setCategories(items); shows.finishLoading(); shows.setActive(false);
            showsScroll = (ScrollView) shows.getChildAt(0);
            shell.addView(shows, new LinearLayout.LayoutParams(-1, 0, 1));
            shows.postDelayed(() -> bindArtwork(shows), 600);
        } else {
            backdrop = new Backdrop();
            backdrop.mode = mode;
            shell.addView(backdrop, new LinearLayout.LayoutParams(-1,0,1));
        }
        nav = new ZeroChillBottomNavigationView(this);
        int[] ids={2,4,3,6}; int[] icons={R.drawable.ic_nav_series,R.drawable.ic_nav_chaos,R.drawable.ic_nav_onlyfap,R.drawable.ic_more_library};
        String[] labels={"Shows","ShitTok","OnlyFap","Library"};
        for(int i=0;i<4;i++) nav.getMenu().add(0,ids[i],i,labels[i]).setIcon(icons[i]);
        nav.getMenu().findItem(4).setChecked(true); nav.setPagerPosition(1);
        nav.setOnItemSelectedListener(item -> {for(int i=0;i<4;i++)if(ids[i]==item.getItemId())nav.setPagerPosition(i);return true;});
        LinearLayout.LayoutParams floating = new LinearLayout.LayoutParams(-1,dp(64));
        floating.setMargins(dp(10),-dp(70),dp(10),dp(6));
        shell.addView(nav,floating);
        StableBottomNavigationController.styleBar(nav); nav.reapplyCurrentGeometry();
        if (getIntent().getBooleanExtra("recent_search", false)) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            int count = getIntent().getIntExtra("recent_count", 3);
            if (state == null) {
                RecentSearchStore.clear(this);
                for (int i = 0; i < count; i++) RecentSearchStore.remember(this, "Saved search " + (i + 1));
            }
            getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled",
                    !getIntent().getBooleanExtra("reduced_motion", false)).apply();
            FrameLayout root = new FrameLayout(this);
            root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
            setContentView(root);
            searchPanel = new SearchPanel(this, new android.content.Intent(), true, () -> {});
            searchPanel.create(state);
            searchPanel.setEmptyBackdrop(shell.pageBackdropForOverlay());
            root.addView(searchPanel.view, new FrameLayout.LayoutParams(-1, -1));
            searchPanel.field().clearFocus();
            if (getIntent().getBooleanExtra("search_results", false)) {
                seedSearchResults();
            } else handler.postDelayed(() -> checkRecentGeometry(shell), 1200);
        } else setContentView(shell);
        nav.setCollapsed(getIntent().getBooleanExtra("collapsed", false), false);
        if (getIntent().getBooleanExtra("preference", false))
            shell.postDelayed(() -> ContentPreferenceUi.choose(this, null), 500);
        alive = true; start = SystemClock.uptimeMillis();
        if(getIntent().getBooleanExtra("moving",false))handler.post(animate);
    }
    /** Seed native rows locally; no provider requests or explicit media in render checks. */
    private void seedSearchResults() {
        try {
            searchPanel.field().setText("Fixture");
            java.lang.reflect.Method cancel = SearchPanel.class.getDeclaredMethod("cancelSearchWork");
            cancel.setAccessible(true); cancel.invoke(searchPanel);
            java.lang.reflect.Field adapterField = SearchPanel.class.getDeclaredField("adapter");
            adapterField.setAccessible(true);
            GlobalSearchAdapter adapter = (GlobalSearchAdapter) adapterField.get(searchPanel);
            java.util.List<GlobalSearchAdapter.Entry> entries = new java.util.ArrayList<>();
            entries.add(GlobalSearchAdapter.Entry.section("Local results"));
            for (int i = 0; i < 12; i++) entries.add(GlobalSearchAdapter.Entry.item(
                new NativeContentItem(NativeContentItem.KIND_MEDIA, "Fixture result " + (i + 1),
                    "https://example.invalid/result/" + i, "", "", "", ""), GlobalSearchAdapter.SOURCE_LIBRARY));
            adapter.replace(entries);
            handler.postDelayed(() -> {
                SearchGlassLayout search = (SearchGlassLayout) searchPanel.view;
                View pane = search.getChildAt(0), rows = search.getChildAt(1);
                boolean aligned = pane.getLeft() == rows.getLeft() && pane.getTop() == rows.getTop()
                    && pane.getWidth() == rows.getWidth() && pane.getHeight() == rows.getHeight()
                    && pane.getVisibility() == View.VISIBLE && rows.getClipToOutline();
                android.util.Log.i("ZeroChillSearch", (aligned ? "PASS" : "FAIL") + " result geometry and clip");
            }, 1200);
        } catch (Exception error) { android.util.Log.e("ZeroChillSearch", "FAIL result fixture", error); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (searchPanel != null) searchPanel.save(state);
        super.onSaveInstanceState(state);
    }
    private void checkRecentGeometry(FrostedNavigationLayout source) {
        try {
            SearchGlassLayout search = (SearchGlassLayout) searchPanel.view;
            View pane = search.getChildAt(0), rows = search.getChildAt(1);
            int count = RecentSearchStore.load(this).size();
            int desired = dp(48 + 56 * count + 14);
            if (pane.getHeight() > desired || pane.getHeight() != rows.getHeight()
                    || (count == 0 && pane.getVisibility() == View.VISIBLE))
                throw new IllegalStateException("Incorrect recent geometry " + pane.getHeight() + "/" + desired);
            java.lang.reflect.Field pageField = SearchGlassLayout.class.getDeclaredField("page");
            pageField.setAccessible(true);
            ViewPageBackdrop searchPage = (ViewPageBackdrop) pageField.get(search);
            int searchRecordings = searchPage.getRecordings();
            int recordings = source.pageBackdropForOverlay().getRecordings();
            handler.postDelayed(() -> {
                int extra = source.pageBackdropForOverlay().getRecordings() - recordings;
                int searchExtra = searchPage.getRecordings() - searchRecordings;
                android.util.Log.i("ZeroChillSearch", (extra <= 2 && searchExtra <= 2 ? "PASS" : "FAIL")
                        + " natural height=" + pane.getHeight() + " desired=" + desired
                        + " source recordings during idle=" + extra + " search recordings=" + searchExtra);
            }, 1000);
        } catch (Exception error) { android.util.Log.e("ZeroChillSearch", "FAIL", error); }
    }
    @Override protected void onDestroy(){if (searchPanel != null) searchPanel.close();alive=false;handler.removeCallbacksAndMessages(null);if (probePlayer != null) probePlayer.release();super.onDestroy();}
    private void bindArtwork(View view) {
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            com.bumptech.glide.Glide.with(this).clear(image);
            image.setImageResource(R.drawable.ic_zerochill_devil);
            image.setBackgroundColor(0xFFE6EDF2);
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++)
            bindArtwork(((ViewGroup)view).getChildAt(i));
    }
    private int dp(int n){return BrowseUi.dp(this,n);}
    private final class Backdrop extends View {
        final Paint paint = new Paint(3); int mode; float phase;
        Backdrop(){super(GlassRenderProbeActivity.this);}
        @Override public void onDraw(Canvas c) {
            int w=getWidth(),h=getHeight();
            c.drawColor(mode==1 ? Color.BLACK : 0xFF456A80);
            if(mode!=1){
                for(int i=-2;i<10;i++){
                    paint.setColor(i%2==0?0xFFE7E9E5:0xFF24323D);
                    float x=(i+phase)*w/6f;
                    c.drawRect(x,0,x+w/14f,h,paint);
                }
                paint.setColor(0xFFBF7555);c.drawCircle(w*.72f,h*.7f,w*.26f,paint);
            }
            paint.setColor(Color.WHITE);paint.setTextSize(dp(30));
            c.drawText("ZEROCHILL",dp(24),dp(56),paint);
            paint.setTextSize(dp(14));c.drawText("Native glass renderer fixture",dp(24),dp(84),paint);
            paint.setTextSize(dp(12));c.drawText("Bright / dark / moving backdrop",dp(24),h-dp(110),paint);
        }
    }
}
