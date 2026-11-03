package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.*;
import com.bumptech.glide.signature.ObjectKey;
import java.io.*;
import java.util.*;

/** Debug-only, deterministic real RecyclerView/Glide gallery device regression. */
public final class OnlyFapGalleryLayoutProbeActivity extends Activity {
    private static final String TAG = "ZeroChillGalleryLayout";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<NativeContentItem> all = new ArrayList<>();
    private final Map<String, byte[]> artwork = new HashMap<>();
    private RecyclerView list;
    private BunkrGalleryAdapter adapter;
    private int stage, scrollStep, toggles, checks;
    private long readyDeadline;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        registerArtwork();
        for (int i = 0; i < 100; i++) {
            int[] size = dimensions(i);
            all.add(new NativeContentItem(i % 3 == 0 ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                    "Synthetic gallery " + i, "https://gallery.fixture/" + i,
                    "https://gallery.fixture/" + i, "", "", "", "OnlyFap", "Fixture", 0,
                    i % 2 == 0 ? size[0] / (float) size[1] : 0));
        }
        list = new RecyclerView(this);
        list.setItemAnimator(null);
        list.setLayoutManager(new StaggeredGridLayoutManager(2, RecyclerView.VERTICAL));
        setContentView(list);
        adapter = newAdapter();
        list.setAdapter(adapter);
        adapter.replace(all, false);
        readyDeadline = android.os.SystemClock.uptimeMillis() + 15000;
        main.postDelayed(this::advance, 1500);
    }
    private BunkrGalleryAdapter newAdapter() {
        return new BunkrGalleryAdapter(this, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
        }, true);
    }
    private int[] dimensions(int id) {
        return new int[][]{{96, 192}, {192, 96}, {128, 128}, {48, 384}, {384, 48}, {96, 144}, {144, 96}}[id % 7];
    }
    private void registerArtwork() {
        Glide.get(this).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            public boolean handles(GlideUrl url) { return url.toStringUrl().startsWith("https://gallery.fixture/"); }
                            public LoadData<InputStream> buildLoadData(GlideUrl url, int w, int h, com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(url.toStringUrl()), new DataFetcher<InputStream>() {
                                    public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        main.postDelayed(() -> callback.onDataReady(new ByteArrayInputStream(png(url.toStringUrl()))), 180);
                                    }
                                    public void cleanup() { }
                                    public void cancel() { } // Late deliveries deliberately exercise Glide cancellation.
                                    public Class<InputStream> getDataClass() { return InputStream.class; }
                                    public DataSource getDataSource() { return DataSource.REMOTE; }
                                });
                            }
                        };
                    }
                    public void teardown() { }
                });
    }
    private byte[] png(String url) {
        if (artwork.containsKey(url)) return artwork.get(url);
        int id = Integer.parseInt(url.substring(url.lastIndexOf('/') + 1));
        int[] size = dimensions(id);
        Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.HSVToColor(new float[]{(id * 37) % 360, .75f, .8f}));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle();
        byte[] result = out.toByteArray(); artwork.put(url, result); return result;
    }
    private void advance() {
        try {
            if (stage == 1 && scrollStep < 32) {
                list.scrollBy(0, scrollStep++ < 16 ? 650 : -650);
                main.postDelayed(this::advance, scrollStep == 32 ? 1000 : 45); return;
            }
            if (stage == 2 && toggles < 12) {
                adapter.setFavoriteUrls(toggles++ % 2 == 0 ? Collections.singleton(all.get(3).url) : Collections.emptySet());
                main.postDelayed(this::advance, toggles == 12 ? 1000 : 45); return;
            }
            // Cold WebView/CookieManager and Glide initialization can outlast a fixed
            // startup delay. Wait for actual requests and the resulting layout pass.
            if (!imagesReady()) {
                if (android.os.SystemClock.uptimeMillis() >= readyDeadline)
                    throw new AssertionError("Glide did not settle: " + requestStates());
                main.postDelayed(this::advance, 100);
                return;
            }
            verify();
            android.util.Log.i(TAG, "stage=" + stage + " verified; children=" + list.getChildCount());
            stage++;
            if (stage == 3) {
                list.setAdapter(null); list.setAdapter(adapter); // Retained tab detach/reentry.
            } else if (stage >= 4 && stage <= 6) {
                List<NativeContentItem> tab = new ArrayList<>();
                for (NativeContentItem item : all)
                    if (stage == 6 || (stage == 4 ? item.isImage() : item.isVideo())) tab.add(item);
                adapter.replace(tab, false);
                list.scrollToPosition(0);
            } else if (stage == 7) {
                list.setAdapter(null); adapter = newAdapter(); list.setAdapter(adapter);
                adapter.replace(all, false); // Fresh creator open, no adapter ratio cache.
            } else if (stage == 8) {
                android.util.Log.i(TAG, "PASS mixed ratios, delayed Glide, favorites, scroll, tabs, reopen; checks=" + checks);
                return;
            }
            readyDeadline = android.os.SystemClock.uptimeMillis() + 15000;
            main.postDelayed(this::advance, stage <= 2 ? 45 : 1200);
        } catch (Throwable failure) {
            android.util.Log.e(TAG, "FAIL stage=" + stage, failure);
            throw new IllegalStateException(failure);
        }
    }
    private boolean imagesReady() {
        if (list.getChildCount() < 2 || list.isLayoutRequested() || list.isComputingLayout()) return false;
        for (int i = 0; i < list.getChildCount(); i++) {
            BunkrGalleryAdapter.Holder holder = (BunkrGalleryAdapter.Holder) list.getChildViewHolder(list.getChildAt(i));
            com.bumptech.glide.request.Request request = new com.bumptech.glide.request.target.DrawableImageViewTarget(holder.image).getRequest();
            if (request == null || !request.isComplete() || holder.image.getDrawable() == null
                    || holder.image.getDrawable().getIntrinsicWidth() <= 0) return false;
        }
        return true;
    }
    private String requestStates() {
        StringBuilder states = new StringBuilder();
        for (int i = 0; i < list.getChildCount(); i++) {
            BunkrGalleryAdapter.Holder holder = (BunkrGalleryAdapter.Holder) list.getChildViewHolder(list.getChildAt(i));
            com.bumptech.glide.request.Request request = new com.bumptech.glide.request.target.DrawableImageViewTarget(holder.image).getRequest();
            states.append(holder.boundItem.url).append(" complete=").append(request != null && request.isComplete())
                    .append(" running=").append(request != null && request.isRunning()).append("; ");
        }
        return states.toString();
    }
    private void verify() {
        if (list.getChildCount() < 2) throw new AssertionError("Empty gallery");
        for (int i = 0; i < list.getChildCount(); i++) {
            BunkrGalleryAdapter.Holder holder = (BunkrGalleryAdapter.Holder) list.getChildViewHolder(list.getChildAt(i));
            View child = holder.itemView;
            int id = Integer.parseInt(holder.boundItem.url.substring(holder.boundItem.url.lastIndexOf('/') + 1));
            int[] size = dimensions(id);
            int expected = Math.round(child.getWidth() * size[1] / (float) size[0]);
            if (Math.abs(child.getHeight() - expected) > Math.max(2, expected * .02f))
                throw new AssertionError("Stale height for " + id + ": " + child.getHeight() + " expected " + expected);
            if (holder.image.getDrawable() == null || holder.image.getDrawable().getIntrinsicWidth() <= 0)
                throw new AssertionError("Image still pending for " + id);
            if ((holder.play.getVisibility() == View.VISIBLE) != holder.boundItem.isVideo())
                throw new AssertionError("Incorrect video overlay");
            RectF bounds = bounds(child);
            for (int j = 0; j < i; j++) if (RectF.intersects(bounds, bounds(list.getChildAt(j))))
                throw new AssertionError("Overlapping visual bounds at " + i + ", " + j);
            checks++;
        }
    }
    private RectF bounds(View child) {
        RectF bounds = new RectF(0, 0, child.getWidth(), child.getHeight());
        child.getMatrix().mapRect(bounds); bounds.offset(child.getLeft(), child.getTop()); return bounds;
    }
    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (list != null) list.setAdapter(null);
        super.onDestroy();
    }
}
