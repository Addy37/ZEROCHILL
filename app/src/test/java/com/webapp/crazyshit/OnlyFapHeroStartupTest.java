package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class OnlyFapHeroStartupTest {
    private Activity activity;
    private OnlyFapHubView hub;
    private final AtomicInteger fetches = new AtomicInteger();
    private NativeContentItem opened;
    private static final String IMAGE = "https://cdn.example.com/remembered.jpg";
    private static final String FRESH = "https://cdn.example.com/fresh.jpg";

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        Bitmap bitmap = Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        byte[] png = bytes.toByteArray();
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    @Override public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            @Override public boolean handles(GlideUrl model) { return true; }
                            @Override public LoadData<InputStream> buildLoadData(GlideUrl model,
                                    int width, int height, com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(model.toStringUrl()), new DataFetcher<InputStream>() {
                                    @Override public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        fetches.incrementAndGet();
                                        callback.onDataReady(new ByteArrayInputStream(png));
                                    }
                                    @Override public void cleanup() { }
                                    @Override public void cancel() { }
                                    @Override public Class<InputStream> getDataClass() { return InputStream.class; }
                                    @Override public DataSource getDataSource() { return DataSource.REMOTE; }
                                });
                            }
                        };
                    }
                    @Override public void teardown() { }
                });
        hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { opened = creator; }
            @Override public void onSearch() { }
            @Override public void onMore() { }
            @Override public void onViewAllFavorites() { }
        });
        activity.setContentView(hub);
        hub.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        hub.layout(0, 0, 400, 800);
        ReflectionHelpers.setField(hub, "active", true);
        // Isolate startup from source discovery; existing discovery tests cover that system.
        ReflectionHelpers.setField(hub, "heroSelectionLoading", true);
    }

    @After public void close() { if (hub != null) hub.close(); }

    @Test public void rememberedHeroDisplaysFromGlideCacheBeforeDiscoveryAndFreshReplacesIt() throws Exception {
        NativeContentItem previous = creator("Previous", "previous", IMAGE);
        OnlyFapHeroMemory.remember(activity, previous, IMAGE, previous.url);
        warm(IMAGE, previous.url);
        int before = fetches.get();
        ReflectionHelpers.callInstanceMethod(hub, "requestStartupHero");
        await(() -> title().equals("Previous"));
        assertEquals(before, fetches.get());
        assertNull(ReflectionHelpers.getField(hub, "heroItem"));
        assertTrue(((List<?>) ReflectionHelpers.getField(hub, "heroItems")).isEmpty());
        ReflectionHelpers.callInstanceMethod(hub, "openHero");
        assertEquals(previous.url, opened.url);

        NativeContentItem fresh = creator("Fresh", "fresh", FRESH);
        List<OnlyFapHeroPolicy.Artwork> validated = Collections.singletonList(
                new OnlyFapHeroPolicy.Artwork(FRESH, fresh.url, false));
        ReflectionHelpers.callInstanceMethod(hub, "finishHeroResolution",
                ReflectionHelpers.ClassParameter.from(int.class, 0),
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, fresh),
                ReflectionHelpers.ClassParameter.from(List.class, validated));
        await(() -> ReflectionHelpers.getField(hub, "startupHero") == null
                && title().equals("Fresh") && OnlyFapHeroMemory.read(activity).imageUrl.equals(FRESH));
        ReflectionHelpers.callInstanceMethod(hub, "openHero");
        assertEquals(fresh.url, opened.url);
        assertEquals(1, ((List<?>) ReflectionHelpers.getField(hub, "heroItems")).size());
    }

    @Test public void uncachedRememberedHeroFallsBackToKnownThumbnail() throws Exception {
        NativeContentItem previous = creator("Fallback", "fallback", "https://cdn.example.com/avatar.jpg");
        OnlyFapHeroMemory.remember(activity, previous, "https://cdn.example.com/not-cached.jpg", previous.url);
        ReflectionHelpers.callInstanceMethod(hub, "requestStartupHero");
        await(() -> title().equals("Fallback"));
        await(() -> OnlyFapHeroMemory.read(activity) == null);
        assertTrue(fetches.get() > 0);
        assertNull(ReflectionHelpers.getField(hub, "heroItem"));
    }

    @Test public void staleOrCorruptMetadataIsIgnoredAndForgetDoesNotEraseNewerHero() {
        NativeContentItem creator = creator("Saved", "saved", IMAGE);
        OnlyFapHeroMemory.remember(activity, creator, IMAGE, creator.url);
        assertNotNull(OnlyFapHeroMemory.read(activity));
        OnlyFapHeroMemory.remember(activity, creator, FRESH, creator.url);
        OnlyFapHeroMemory.forget(activity, IMAGE);
        assertEquals(FRESH, OnlyFapHeroMemory.read(activity).imageUrl);
        android.content.SharedPreferences prefs = activity.getSharedPreferences("onlyfap_hero_startup", 0);
        String raw = prefs.getString("hero", "");
        try {
            org.json.JSONObject stale = new org.json.JSONObject(raw).put("saved", 1L);
            prefs.edit().putString("hero", stale.toString()).commit();
        } catch (Exception failure) { throw new AssertionError(failure); }
        assertNull(OnlyFapHeroMemory.read(activity));
        prefs.edit().putString("hero", "broken").commit();
        assertNull(OnlyFapHeroMemory.read(activity));
    }

    @Test public void excludedRememberedCreatorDoesNotEnterStartupOrRotationPool() throws Exception {
        NativeContentItem previous = creator("Excluded", "excluded", IMAGE);
        OnlyFapHeroMemory.remember(activity, previous, IMAGE, previous.url);
        warm(IMAGE, previous.url);
        ReflectionHelpers.setField(hub, "hotItems", Collections.singletonList(previous));
        ReflectionHelpers.callInstanceMethod(hub, "requestStartupHero");
        java.util.concurrent.ExecutorService io = ReflectionHelpers.getField(hub, "metadataIo");
        io.submit(() -> { }).get(5, TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();
        assertNull(ReflectionHelpers.getField(hub, "startupHero"));
        assertNull(ReflectionHelpers.getField(hub, "heroItem"));
    }

    private void warm(String image, String referer) throws Exception {
        GlideUrl model = ReflectionHelpers.callInstanceMethod(hub, "remoteImage",
                ReflectionHelpers.ClassParameter.from(String.class, image),
                ReflectionHelpers.ClassParameter.from(String.class, referer));
        CompletableFuture.runAsync(() -> {
            com.bumptech.glide.request.FutureTarget<Bitmap> target = Glide.with(activity.getApplicationContext())
                    .asBitmap().load(model).diskCacheStrategy(DiskCacheStrategy.ALL).submit(360, 540);
            try { target.get(5, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            finally { target.cancel(true); }
        }).get(10, TimeUnit.SECONDS);
    }

    private String title() {
        return ((TextView) ReflectionHelpers.getField(hub, "heroTitle")).getText().toString();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("Startup condition did not complete");
    }
    private static NativeContentItem creator(String name, String slug, String image) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://example.com/" + slug, image, "", "", "");
    }
}
