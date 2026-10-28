package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {
        BunkrMediaPreviewLifecycleTest.Metadata.class, BunkrMediaPreviewLifecycleTest.Preview.class })
public class BunkrMediaPreviewLifecycleTest {
    @Implements(value = BunkrArtworkResolver.class, isInAndroidSdk = false)
    public static class Metadata {
        static int calls;
        @Implementation protected static boolean request(Context context, String page, String rejected,
                Consumer<String> callback) {
            calls++; callback.accept(""); return true;
        }
    }
    @Implements(value = BunkrMediaPreview.class, isInAndroidSdk = false)
    public static class Preview {
        static int calls;
        static Consumer<File> callback;
        static BunkrMediaPreview.Task task;
        @Implementation protected static BunkrMediaPreview.Task request(Context context,
                NativeContentItem item, Consumer<File> ready) {
            calls++; callback = ready; task = new BunkrMediaPreview.Task(); return task;
        }
    }
    @Before public void reset() { Metadata.calls = 0; Preview.calls = 0; Preview.callback = null; }
    private BunkrGalleryAdapter adapter(String url) {
        Context context = RuntimeEnvironment.getApplication();
        BunkrGalleryAdapter adapter = new BunkrGalleryAdapter(context, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
        });
        adapter.replace(Collections.singletonList(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "video", url, "", "", "", "")), false);
        return adapter;
    }
    private BunkrGalleryAdapter.Holder holder(BunkrGalleryAdapter adapter) {
        return adapter.onCreateViewHolder(new FrameLayout(RuntimeEnvironment.getApplication()), 0);
    }
    @Test public void missingArtworkWaitsForVisibleTileAndDetachCancelsPreview() {
        BunkrGalleryAdapter adapter = adapter("https://bunkr.cr/f/visible");
        BunkrGalleryAdapter.Holder holder = holder(adapter);
        adapter.onBindViewHolder(holder, 0);
        assertEquals(0, Metadata.calls); assertEquals(0, Preview.calls);
        adapter.onViewAttachedToWindow(holder);
        assertEquals(1, Metadata.calls); assertEquals(1, Preview.calls);
        BunkrMediaPreview.Task old = Preview.task;
        adapter.onViewDetachedFromWindow(holder);
        assertTrue(old.canceled);
        adapter.onViewAttachedToWindow(holder);
        assertEquals(2, Preview.calls);
    }
    @Test public void failedPreviewShowsLabelAndRebindRejectsStaleCallback() {
        BunkrGalleryAdapter adapter = adapter("https://bunkr.cr/f/rebind");
        BunkrGalleryAdapter.Holder holder = holder(adapter);
        adapter.onBindViewHolder(holder, 0); adapter.onViewAttachedToWindow(holder);
        Consumer<File> stale = Preview.callback;
        adapter.onViewRecycled(holder);
        stale.accept(null);
        assertEquals(View.GONE, holder.unavailable.getVisibility());
        adapter.onBindViewHolder(holder, 0);
        Preview.callback.accept(null);
        assertEquals(View.VISIBLE, holder.unavailable.getVisibility());
        adapter.onViewRecycled(holder);
    }
    @Test public void otherSourcesDoNotStartBunkrFallback() {
        BunkrGalleryAdapter adapter = adapter("https://fapello.com/creator/123/");
        BunkrGalleryAdapter.Holder holder = holder(adapter);
        adapter.onBindViewHolder(holder, 0); adapter.onViewAttachedToWindow(holder);
        assertEquals(0, Metadata.calls); assertEquals(0, Preview.calls);
    }
}
