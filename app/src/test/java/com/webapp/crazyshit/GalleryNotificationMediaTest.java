package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.HttpUrlFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = GalleryNotificationMediaTest.Images.class)
public class GalleryNotificationMediaTest {
    @Implements(value = HttpUrlFetcher.class, isInAndroidSdk = false)
    public static class Images {
        @RealObject HttpUrlFetcher real;
        static final ConcurrentHashMap<String, DataFetcher.DataCallback<? super InputStream>> pending = new ConcurrentHashMap<>();
        @Implementation protected void loadData(com.bumptech.glide.Priority priority,
                DataFetcher.DataCallback<? super InputStream> callback) {
            GlideUrl url = ReflectionHelpers.getField(real, "glideUrl");
            pending.put(url.toStringUrl(), callback);
        }
    }

    private Activity activity;
    private BunkrGalleryPagerAdapter adapter;
    private BunkrGalleryPagerAdapter.Holder holder;
    private int failedResolved;
    private final String preview = "https://images.example/preview.jpg";
    private final String full = "https://images.example/full.jpg";

    @After public void clear() {
        if (activity != null) activity.finish();
        Images.pending.clear();
        com.bumptech.glide.Glide.tearDown();
    }

    private void setup(boolean resolved) throws Exception {
        Images.pending.clear();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        adapter = new BunkrGalleryPagerAdapter(activity, new BunkrGalleryPagerAdapter.Listener() {
            public void onMediaTap(int p, NativeContentItem item) { }
            public void onMediaLongPress(int p, NativeContentItem item) { }
            public void onResolvedImageFailed(int p, NativeContentItem item) { failedResolved++; }
        });
        adapter.replace(Collections.singletonList(new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "Photo", "https://fapello.com/creator/6620/", preview, "", "https://fapello.com/creator/", "")),
                resolved ? Collections.singletonMap("https://fapello.com/creator/6620/", full) : Collections.emptyMap());
        ViewPager2 pager = new ViewPager2(activity);
        pager.setAdapter(adapter);
        activity.setContentView(pager);
        pager.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 400, 800);
        holder = (BunkrGalleryPagerAdapter.Holder) ((RecyclerView) pager.getChildAt(0)).findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        await(() -> Images.pending.containsKey(preview));
    }

    private static void await(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 250; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("Image state did not settle");
    }

    private void succeed(String url) throws Exception {
        await(() -> Images.pending.containsKey(url));
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aT7sAAAAASUVORK5CYII=");
        Images.pending.remove(url).onDataReady(new ByteArrayInputStream(png));
        await(() -> holder.hasUsableImage);
    }

    private void failImage(String url) throws Exception {
        await(() -> Images.pending.containsKey(url));
        Images.pending.remove(url).onLoadFailed(new IOException("Image unavailable"));
        await(() -> adapter.isFailed(0));
    }

    @Test public void successfulPreviewSurvivesFullFailureWithoutFalseOverlayAndCanRetry() throws Exception {
        setup(true);
        succeed(preview);
        failImage(full);
        assertEquals(1, failedResolved);
        assertNotNull(holder.image.getDrawable());
        assertEquals(View.GONE, holder.failure.getVisibility());
        assertFalse(holder.imageLoading);
        // Same URL retry must restart Glide, then full success clears failure/loading state.
        adapter.setFailed(0, false);
        adapter.setResolvedUrl(0, full);
        adapter.onBindViewHolder(holder, 0);
        succeed(full);
        await(() -> !adapter.isFailed(0) && !holder.imageLoading);
        assertEquals(View.GONE, holder.failure.getVisibility());
        assertFalse(adapter.isLoading(0));
    }

    @Test public void totalImageFailureShowsLocalRetryUntilAnImageLoads() throws Exception {
        setup(false);
        failImage(preview);
        assertEquals(View.VISIBLE, holder.failure.getVisibility());
        adapter.setFailed(0, false);
        adapter.onBindViewHolder(holder, 0);
        succeed(preview);
        await(() -> !holder.imageLoading);
        assertEquals(View.GONE, holder.failure.getVisibility());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void resolverFailureAfterSuccessfulPreviewDoesNotCoverTheImage() throws Exception {
        setup(false);
        succeed(preview);
        adapter.setFailed(0, true);
        adapter.onBindViewHolder(holder, 0, Collections.singletonList(new Object()));
        assertEquals(View.GONE, holder.failure.getVisibility());
        assertTrue(adapter.isFailed(0)); // Tap retries resolution without discarding the preview.
    }

    @Test public void knownFapelloPhotoUsesExistingDirectOriginalAndPreservesItemFields() throws Exception {
        NativeContentItem photo = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "https://fapello.com/sydney-sweeney/6620/", "https://cdn.fapello.com/content/s/s/sydney-sweeney_6620_300px.jpg",
                "", "https://fapello.com/sydney-sweeney/", "");
        NativeContentItem restored = ContentItemCodec.decode(ContentItemCodec.encode(photo));
        assertEquals("https://cdn.fapello.com/content/s/s/sydney-sweeney_6620.jpg", FapelloRepository.photoUrlFromItem(restored));
        assertEquals(photo.url, restored.url);
        assertEquals(photo.imageUrl, restored.imageUrl);
        assertEquals(photo.uploader, restored.uploader);
        assertEquals("", FapelloRepository.photoUrlFromItem(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Video", photo.url, photo.imageUrl, "", photo.uploader, "")));
    }
}
