package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.*;
import com.bumptech.glide.signature.ObjectKey;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Debug-only device regression: actual hub, Glide targets, delayed resources and tab queue. */
public final class OnlyFapTransitionProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OnlyFapHubView hub;
    private PrimaryTabWorkQueue queue;
    private int step;
    private long deadline;
    private int[] controls;
    private int frames;
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            try {
                frames++;
                View title = (View) field("heroTitle"), previous = (View) field("previousHeroTitle");
                check(previous.getVisibility() != View.VISIBLE || title.getAlpha() * previous.getAlpha() < .001f,
                        "overlapping creator names");
                if (controls != null) check(Arrays.equals(controls, bounds()), "controls moved during transition");
                String expected = step == 0 ? "Initial creator" : step == 1 ? "Fast category" :
                        step == 2 ? "Latest category with a longer name across two lines" : "Qualified gallery";
                if (expected.equals(((TextView) title).getText().toString()) &&
                        !(boolean) field("preferenceHeroPending") && field("heroMorph") == null) {
                    android.util.Log.i("ZeroChillTransition", "stage=" + step + " ready " + expected);
                    if (step == 3) {
                        check(frames > 10, "insufficient observed animation frames");
                        android.util.Log.i("ZeroChillTransition", "PASS real Glide startup, rapid switch, qualified artwork, stable bounds; frames=" + frames);
                        return;
                    }
                    controls = bounds();
                    if (step == 0) change(ContentPreference.MEN, "Fast category");
                    else if (step == 1) {
                        change(ContentPreference.TRANS_MEN, "Superseded slow category");
                        handler.postDelayed(() -> change(ContentPreference.GROUPS,
                                "Latest category with a longer name across two lines"), 40);
                    } else qualified();
                    step++;
                    deadline = SystemClock.uptimeMillis() + 6000;
                }
                check(SystemClock.uptimeMillis() < deadline, "transition stuck at stage " + step);
                hub.postOnAnimation(this);
            } catch (Exception error) {
                android.util.Log.e("ZeroChillTransition", "FAIL", error);
                throw new IllegalStateException(error);
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            registerArtwork();
            hub = new OnlyFapHubView(this, new OnlyFapHubView.Listener() {
                public void onOpenCreator(NativeContentItem item) { }
                public void onSearch() { }
                public void onViewAllFavorites() { }
            });
            ((ExecutorService) field("heroIo")).shutdownNow();
            set("heroIo", new AbstractExecutorService() {
                public void execute(Runnable command) { } // Gallery network is controlled at completion below.
                public void shutdown() { }
                public List<Runnable> shutdownNow() { return Collections.emptyList(); }
                public boolean isShutdown() { return false; }
                public boolean isTerminated() { return false; }
                public boolean awaitTermination(long n, TimeUnit unit) { return true; }
            });
            setContentView(hub);
            queue = new PrimaryTabWorkQueue(hub);
            queue.select(3); queue.setSettled(true);
            hub.setWorkScheduler((key, task) -> queue.submit(3, key, task));
            set("active", true);
            change(ContentPreference.WOMEN, "Initial creator");
            deadline = SystemClock.uptimeMillis() + 6000;
            hub.postOnAnimation(watch);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private void change(ContentPreference mode, String title) {
        ContentPreference.set(this, mode);
        hub.preparePreference(mode); hub.clear(); // Actual pager refresh's double clear.
        hub.setPreferenceCreators(Collections.singletonList(creator(title, true)));
    }
    private NativeContentItem creator(String title, boolean thumbnail) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, title,
                "https://cum.st/creators/onlyfans/" + title.replace(' ', '-'),
                thumbnail ? "https://transition.invalid/" + title.replace(' ', '-') + ".png" : "",
                "Controlled local artwork", "", "");
    }
    private void qualified() throws Exception {
        ContentPreference.set(this, ContentPreference.TRANS_WOMEN);
        hub.preparePreference(ContentPreference.TRANS_WOMEN); hub.clear();
        NativeContentItem creator = creator("Qualified gallery", false);
        hub.setPreferenceCreators(Collections.singletonList(creator));
        int generation = (int) field("heroGeneration");
        Method finish = OnlyFapHubView.class.getDeclaredMethod("finishHeroResolution", int.class,
                NativeContentItem.class, List.class);
        finish.setAccessible(true);
        // Through the production page queue, not the image-ready or commit method.
        queue.submit(3, "hero-resolution", () -> {
            try { finish.invoke(hub, generation, creator, Collections.singletonList(
                    new OnlyFapHeroPolicy.Artwork("https://transition.invalid/qualified.png", creator.url, false))); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
    }
    private int[] bounds() throws Exception {
        View control = (View) field("preferenceSelector");
        int[] xy = new int[2]; control.getLocationOnScreen(xy);
        return new int[]{xy[0], xy[1], control.getWidth(), control.getHeight()};
    }
    private Object field(String name) throws Exception {
        Field f = OnlyFapHubView.class.getDeclaredField(name); f.setAccessible(true); return f.get(hub);
    }
    private void set(String name, Object value) throws Exception {
        Field f = OnlyFapHubView.class.getDeclaredField(name); f.setAccessible(true); f.set(hub, value);
    }
    private void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private void registerArtwork() throws IOException {
        int[] colors = {0xFF365D79, 0xFF546B49, 0xFF6B4673, 0xFF397A7C, 0xFF9C6440};
        byte[][] portraits = new byte[colors.length][];
        for (int i = 0; i < colors.length; i++) {
            Bitmap bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(colors[i]);
            Paint paint = new Paint(3); paint.setColor(0xFFB8DDE5);
            canvas.drawCircle(180, 190, 110, paint); paint.setColor(0xFF111D29);
            canvas.drawRect(70, 300, 290, 640, paint);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle();
            portraits[i] = bytes.toByteArray();
        }
        Glide.get(this).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
            public void teardown() { }
            public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                return new ModelLoader<GlideUrl, InputStream>() {
                    public boolean handles(GlideUrl model) { return model.toStringUrl().startsWith("https://transition.invalid/"); }
                    public LoadData<InputStream> buildLoadData(GlideUrl model, int w, int h, com.bumptech.glide.load.Options options) {
                        String url = model.toStringUrl();
                        byte[] png = portraits[url.contains("qualified") ? 4 : url.contains("Latest") ? 3 :
                                url.contains("Superseded") ? 2 : url.contains("Fast") ? 1 : 0];
                        return new LoadData<>(new ObjectKey(url), new DataFetcher<InputStream>() {
                            public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                handler.postDelayed(() -> callback.onDataReady(new ByteArrayInputStream(png)),
                                        model.toStringUrl().contains("Superseded") ? 1200 : 180);
                            }
                            public void cleanup() { }
                            public void cancel() { } // Deliberately let late delivery exercise Glide cancellation.
                            public Class<InputStream> getDataClass() { return InputStream.class; }
                            public DataSource getDataSource() { return DataSource.REMOTE; }
                        });
                    }
                };
            }
        });
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (hub != null) { hub.removeCallbacks(watch); hub.close(); }
        if (queue != null) queue.close();
        super.onDestroy();
    }
}
