package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OnlyFapGalleryGlideTest {
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private Activity activity;
    private RecyclerView list;
    private BunkrGalleryAdapter adapter;
    private final Map<String, DataFetcher.DataCallback<? super InputStream>> pending = new ConcurrentHashMap<>();

    @Before public void setup() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup(); activity = controller.get();
        Glide.init(activity, new com.bumptech.glide.GlideBuilder());
        CompletableFuture.runAsync(() -> Glide.get(activity).clearDiskCache()).get(10, TimeUnit.SECONDS);
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            public boolean handles(GlideUrl url) { return true; }
                            public LoadData<InputStream> buildLoadData(GlideUrl url, int w, int h, com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(url.toStringUrl()), new DataFetcher<InputStream>() {
                                    public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        pending.put(url.toStringUrl(), callback);
                                    }
                                    public void cleanup() { }
                                    public void cancel() { } // Deliberately allow a late source result.
                                    public Class<InputStream> getDataClass() { return InputStream.class; }
                                    public DataSource getDataSource() { return DataSource.REMOTE; }
                                });
                            }
                        };
                    }
                    public void teardown() { }
                });
        list = new RecyclerView(activity); list.setItemAnimator(null);
        list.setLayoutManager(new StaggeredGridLayoutManager(2, RecyclerView.VERTICAL));
        adapter = new BunkrGalleryAdapter(activity, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
        }, true);
        list.setAdapter(adapter); activity.setContentView(list);
    }
    @After public void teardown() {
        list.setAdapter(null); controller.pause().stop().destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idle(); Glide.tearDown();
    }
    private NativeContentItem item(String name) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, name,
                "https://gallery.fixture/" + name, "https://gallery.fixture/" + name,
                "", "", "", "OnlyFap", "Fixture", 0, 0);
    }
    private void layout() {
        list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 400, 800); list.getViewTreeObserver().dispatchOnPreDraw();
    }
    private BunkrGalleryAdapter.Holder first() {
        return (BunkrGalleryAdapter.Holder) list.findViewHolderForAdapterPosition(0);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); layout();
            if (condition.getAsBoolean()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for actual Glide completion");
    }
    private byte[] png(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.CYAN);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle(); return out.toByteArray();
    }
    private void complete(NativeContentItem item, int w, int h) throws Exception {
        await(() -> pending.containsKey(item.imageUrl));
        pending.remove(item.imageUrl).onDataReady(new ByteArrayInputStream(png(w, h)));
    }
    @Test public void realGlideDimensionsReplaceSquareAndDoNotLeakAfterReplacement() throws Exception {
        NativeContentItem portrait = item("portrait"), wide = item("wide");
        adapter.replace(Collections.singletonList(portrait), false); layout();
        assertEquals(first().itemView.getWidth(), first().itemView.getHeight());
        complete(portrait, 96, 192);
        await(() -> first().image.getDrawable() != null && first().image.getDrawable().getIntrinsicWidth() > 0);
        assertEquals(first().itemView.getWidth() * 2, first().itemView.getHeight());
        adapter.replace(Collections.singletonList(wide), false); layout();
        assertEquals(first().itemView.getWidth(), first().itemView.getHeight());
        complete(wide, 384, 48);
        await(() -> first().image.getDrawable() != null && first().image.getDrawable().getIntrinsicWidth() > 0);
        assertEquals(Math.round(first().itemView.getWidth() / 8f), first().itemView.getHeight());
    }
    @Test public void canceledGlideResultCannotResizeReplacementItem() throws Exception {
        NativeContentItem old = item("old"), replacement = item("replacement");
        adapter.replace(Collections.singletonList(old), false); layout();
        await(() -> pending.containsKey(old.imageUrl));
        adapter.replace(Collections.singletonList(replacement), false); layout();
        complete(replacement, 192, 96);
        await(() -> first().image.getDrawable() != null && first().image.getDrawable().getIntrinsicWidth() > 0);
        complete(old, 48, 384);
        for (int i = 0; i < 30; i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5); }
        layout();
        assertEquals(replacement.url, first().boundItem.url);
        assertEquals(Math.round(first().itemView.getWidth() / 2f), first().itemView.getHeight());
    }
}
