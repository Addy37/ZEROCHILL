package com.webapp.crazyshit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application.class, sdk = 35,
        shadows = { BunkrMediaPreviewTest.Repository.class, BunkrMediaPreviewTest.Retriever.class,
                BunkrMediaPreviewTest.Network.class, BunkrMediaPreviewTest.IndexedPreview.class })
public class BunkrMediaPreviewTest {
    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class Repository {
        static AtomicInteger calls = new AtomicInteger();
        @Implementation protected CrazyShitRepository.StreamInfo resolvePlayable(Context context, String page) {
            calls.incrementAndGet();
            return new CrazyShitRepository.StreamInfo("https://cdn.example/video.mp4?expires=123", page, "video");
        }
    }
    @Implements(value = BunkrMediaPreview.Task.class, isInAndroidSdk = false)
    public static class Network {
        @Implementation protected BunkrPreviewDataSource.Block range(String url,
                java.util.Map<String, String> headers, long start, int size) {
            return new BunkrPreviewDataSource.Block(new byte[size], 512_000);
        }
    }
    @Implements(value = PawchiveIndexedPreview.class, isInAndroidSdk = false)
    public static class IndexedPreview {
        static final AtomicInteger calls = new AtomicInteger();
        @Implementation protected static Bitmap extract(Context context, String url,
                BunkrPreviewDataSource.Reader reader, PawchiveIndexedPreview.Check check,
                java.util.function.Consumer<String> trace) throws Exception {
            calls.incrementAndGet();
            assertEquals("https://n3.pawchive.pw/data/ab/cd/visible.mp4", url);
            check.run();
            Bitmap frame = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            frame.eraseColor(android.graphics.Color.GREEN);
            return frame;
        }
    }
    @Implements(MediaMetadataRetriever.class)
    public static class Retriever {
        static AtomicInteger calls = new AtomicInteger();
        static AtomicInteger releases = new AtomicInteger();
        @Implementation protected void __constructor__() { }
        @Implementation protected void setDataSource(MediaDataSource source) { assertNotNull(source);
            try { assertEquals(512_000, source.getSize()); } catch (Exception error) { throw new AssertionError(error); }
        }
        @Implementation protected Bitmap getScaledFrameAtTime(long time, int option, int width, int height) {
            calls.incrementAndGet();
            assertEquals(1_000_000L, time);
            assertEquals(384, width); assertEquals(384, height);
            return Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888);
        }
        @Implementation protected void release() { releases.incrementAndGet(); }
    }
    @Before public void reset() {
        Repository.calls.set(0); Retriever.calls.set(0); Retriever.releases.set(0);
        IndexedPreview.calls.set(0);
    }
    private NativeContentItem video(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "video", "https://bunkr.cr/f/" + id,
                "", "", "", "");
    }
    private void await(AtomicInteger count, int expected) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (count.get() < expected && System.nanoTime() < until) {
            Thread.sleep(10); shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(expected, count.get());
    }
    @Test public void scaledVideoFrameIsSavedAndReusedWithoutResolvingExpiredCdnAgain() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = video("cached-frame");
        File file = BunkrMediaPreview.cacheFile(context, item);
        file.delete();
        AtomicInteger delivered = new AtomicInteger();
        AtomicReference<File> result = new AtomicReference<>();
        BunkrMediaPreview.request(context, item, ready -> { result.set(ready); delivered.incrementAndGet(); });
        await(delivered, 1);
        assertEquals(file, result.get());
        assertTrue(file.length() > 0);
        Bitmap saved = BitmapFactory.decodeFile(file.toString());
        assertEquals(384, saved.getWidth()); assertEquals(216, saved.getHeight()); saved.recycle();
        assertEquals(1, Retriever.releases.get());
        BunkrMediaPreview.request(context, item, ready -> { assertEquals(file, ready); delivered.incrementAndGet(); });
        await(delivered, 2);
        assertEquals(1, Repository.calls.get()); assertEquals(1, Retriever.calls.get());
        file.delete();
    }
    @Test public void playedFrameIsPublishedByClipIdentityAfterTheFileIsReady() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String url = "https://file.pawchive.pw/data/ab/cd/played-frame.mp4";
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "video",
                url, "", "", "", "");
        File file = BunkrMediaPreview.cacheFile(context, item);
        file.delete();
        AtomicInteger delivered = new AtomicInteger();
        java.util.function.Consumer<String> listener = identity -> {
            assertEquals(url, identity);
            assertEquals(file, BunkrMediaPreview.cached(context, item));
            delivered.incrementAndGet();
        };
        BunkrMediaPreview.addPlaybackPreviewListener(listener);
        try {
            Bitmap frame = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            frame.eraseColor(android.graphics.Color.GREEN);
            BunkrMediaPreview.rememberPlaybackFrame(context, item, frame);
            await(delivered, 1);
            assertEquals(0, Repository.calls.get());
        } finally {
            BunkrMediaPreview.removePlaybackPreviewListener(listener);
            file.delete();
        }
    }

    @Test public void pawchiveBlankColdFrameCannotPoisonCacheOrBlockLaterCapture() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "video",
                "https://file.pawchive.pw/data/ab/cd/blank-cache.mp4", "", "", "", "");
        File file = BunkrMediaPreview.cacheFile(context, item);
        file.delete();
        try {
            Bitmap blank = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            blank.eraseColor(android.graphics.Color.BLACK);
            assertNull(BunkrMediaPreview.writePreview(context, item, blank, null));
            assertNull(BunkrMediaPreview.cached(context, item));
            Bitmap frame = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            frame.eraseColor(android.graphics.Color.GREEN);
            assertEquals(file, BunkrMediaPreview.writePreview(context, item, frame, null));
            assertEquals(file, BunkrMediaPreview.cached(context, item));
        } finally { file.delete(); }
    }

    @Test public void coldSavedPawchiveVideoDoesNotStartRemoteExtraction() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "saved clip",
                "https://file.pawchive.pw/data/ab/cd/cold-saved.mp4", "", "", "", "")
                .withSourceMedia("https://n3.pawchive.pw/data/ab/cd/cold-saved.mp4", "");
        File cached = BunkrMediaPreview.cacheFile(context, item);
        cached.delete();
        assertTrue(PawchiveRepository.isNativeVideo(item));
        assertNull(BunkrMediaPreview.generate(context, item, new BunkrMediaPreview.Task()));
        AtomicInteger delivered = new AtomicInteger();
        BunkrMediaPreview.request(context, item, ready -> {
            assertNull(ready);
            delivered.incrementAndGet();
        });
        await(delivered, 1);
        assertFalse(cached.exists());
        assertEquals(0, Repository.calls.get());
        assertEquals(0, Retriever.calls.get());
        assertEquals(0, IndexedPreview.calls.get());
    }

    @Test public void pawchiveCreatorPreviewExtractsFromItsVideoAndReusesCachedPoster() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "visible clip",
                "https://file.pawchive.pw/data/ab/cd/visible.mp4", "", "", "", "")
                .withSourceMedia("https://n3.pawchive.pw/data/ab/cd/visible.mp4", "");
        File file = BunkrMediaPreview.cacheFile(context, item);
        file.delete();
        try {
            AtomicInteger delivered = new AtomicInteger();
            AtomicReference<File> result = new AtomicReference<>();
            BunkrMediaPreview.requestPawchiveVideo(context, item, ready -> {
                result.set(ready);
                delivered.incrementAndGet();
            });
            await(delivered, 1);
            assertEquals(file, result.get());
            assertTrue(file.length() > 0);
            BunkrMediaPreview.requestPawchiveVideo(context, item, ready -> {
                result.set(ready);
                delivered.incrementAndGet();
            });
            await(delivered, 2);
            assertEquals(file, result.get());
            assertEquals(1, IndexedPreview.calls.get());
            assertEquals(0, Repository.calls.get());
        } finally {
            file.delete();
        }
    }

    @Test public void canceledRequestNeverCallsRecycledTile() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        BunkrMediaPreview.Task task = BunkrMediaPreview.request(RuntimeEnvironment.getApplication(),
                video("cancel"), file -> delivered.incrementAndGet());
        task.cancel();
        Thread.sleep(100);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, delivered.get());
    }
    @Test public void originalImageIsDownsampledToTileSizeAndBadImageReturnsNull() {
        Bitmap original = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.compress(Bitmap.CompressFormat.PNG, 100, bytes); original.recycle();
        Bitmap preview = BunkrMediaPreview.decodeImage(bytes.toByteArray());
        assertNotNull(preview); assertEquals(384, preview.getWidth()); assertEquals(192, preview.getHeight());
        preview.recycle();
        assertNull(BunkrMediaPreview.decodeImage("<html>unavailable</html>".getBytes()));
    }
    @Test public void originalPhotoOrientationSurvivesGeneratedPreview() throws Exception {
        File original = File.createTempFile("preview-orientation", ".jpg", RuntimeEnvironment.getApplication().getCacheDir());
        Bitmap source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(original)) {
            source.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        source.recycle();
        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(original.toString());
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                String.valueOf(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90));
        exif.saveAttributes();
        Bitmap preview = BunkrMediaPreview.decodeImage(java.nio.file.Files.readAllBytes(original.toPath()));
        assertEquals(100, preview.getWidth()); assertEquals(200, preview.getHeight());
        preview.recycle(); original.delete();
    }
    @Test public void cacheIdentityUsesMediaKindAndStablePageNotTitleOrThumbnail() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem first = video("identity");
        NativeContentItem renamed = new NativeContentItem(first.kind, "renamed", first.url,
                "https://cdn.example/expired.jpg", "", "", "");
        assertEquals(BunkrMediaPreview.cacheFile(context, first), BunkrMediaPreview.cacheFile(context, renamed));
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, "photo", first.url, "", "", "", "");
        assertNotEquals(BunkrMediaPreview.cacheFile(context, first), BunkrMediaPreview.cacheFile(context, image));
    }
}
