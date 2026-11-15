package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrGalleryPlayableCacheTest {
    private static final String PAGE = "https://bunkr.cr/f/sample";
    private static final String REFERER = "https://dl.bunkr.cr/file/123";

    @Test public void pagerRejectsExpiredBunkrPlayableButKeepsOtherProviders() {
        long now = System.currentTimeMillis() / 1000L;
        String expired = "https://cdn.example/video.mp4?token=abc&ex=" + (now - 60);
        String fresh = "https://cdn.example/video.mp4?token=abc&ex=" + (now + 600);
        BunkrGalleryPagerAdapter adapter = adapter(PAGE, expired);
        assertEquals("", adapter.resolvedUrl(0));
        adapter.setResolvedUrl(0, fresh);
        assertEquals(fresh, adapter.resolvedUrl(0));

        BunkrGalleryPagerAdapter other = adapter("https://example.com/video", expired);
        assertEquals(expired, other.resolvedUrl(0));
    }

    @Test public void sessionSnapshotCarriesVideoRefererAndClearsItWithPlayable() {
        String id = BunkrGallerySessionStore.create("Gallery", PAGE);
        BunkrGallerySessionStore.setResolvedVideo(id, PAGE, "https://cdn.example/clip.mp4", REFERER);
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(id);
        assertEquals(REFERER, snapshot.videoReferers.get(PAGE));
        assertEquals("https://cdn.example/clip.mp4", snapshot.resolvedUrls.get(PAGE));

        BunkrGallerySessionStore.clearResolvedUrl(id, PAGE);
        assertFalse(BunkrGallerySessionStore.snapshot(id).videoReferers.containsKey(PAGE));
        assertEquals(REFERER, snapshot.videoReferers.get(PAGE));
    }

    @Test public void replacingPlayableWithoutRefererDropsOldProvenance() {
        String id = BunkrGallerySessionStore.create("Gallery", PAGE);
        BunkrGallerySessionStore.setResolvedVideo(id, PAGE, "https://cdn.example/old.mp4", REFERER);
        BunkrGallerySessionStore.setResolvedUrl(id, PAGE, "https://cdn.example/new.mp4");
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(id);
        assertEquals("https://cdn.example/new.mp4", snapshot.resolvedUrls.get(PAGE));
        assertFalse(snapshot.videoReferers.containsKey(PAGE));
    }

    @Test public void activityRejectsBunkrCacheWithUnknownReferer() {
        String id = BunkrGallerySessionStore.create("Gallery", PAGE);
        String playable = "https://cdn.example/legacy.mp4";
        BunkrGallerySessionStore.setResolvedUrl(id, PAGE, playable);
        BunkrGalleryPagerAdapter adapter = adapter(PAGE, playable);
        BunkrGalleryActivity activity = new BunkrGalleryActivity();
        ReflectionHelpers.setField(activity, "adapter", adapter);
        ReflectionHelpers.setField(activity, "sessionId", id);

        String cached = ReflectionHelpers.callInstanceMethod(activity, "cachedVideoUrl",
                ReflectionHelpers.ClassParameter.from(int.class, 0),
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, adapter.itemAt(0)));
        assertEquals("", cached);
        assertEquals("", adapter.resolvedUrl(0));
        assertFalse(BunkrGallerySessionStore.snapshot(id).resolvedUrls.containsKey(PAGE));

        BunkrGallerySessionStore.setResolvedVideo(id, PAGE, playable, REFERER);
        adapter.setResolvedUrl(0, playable);
        Map<String, String> referers = ReflectionHelpers.getField(activity, "videoReferers");
        referers.putAll(BunkrGallerySessionStore.snapshot(id).videoReferers);
        String replay = ReflectionHelpers.callInstanceMethod(activity, "cachedVideoUrl",
                ReflectionHelpers.ClassParameter.from(int.class, 0),
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, adapter.itemAt(0)));
        assertEquals(playable, replay);
        assertEquals(REFERER, referers.get(PAGE));
    }

    private BunkrGalleryPagerAdapter adapter(String page, String playable) {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        BunkrGalleryPagerAdapter adapter = new BunkrGalleryPagerAdapter(context,
                new BunkrGalleryPagerAdapter.Listener() {
                    @Override public void onMediaTap(int position, NativeContentItem item) { }
                    @Override public void onMediaLongPress(int position, NativeContentItem item) { }
                    @Override public void onResolvedImageFailed(int position, NativeContentItem item) { }
                });
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Video", page, "", "", "", "");
        adapter.replace(Collections.singletonList(item), Collections.singletonMap(page, playable));
        return adapter;
    }
}
