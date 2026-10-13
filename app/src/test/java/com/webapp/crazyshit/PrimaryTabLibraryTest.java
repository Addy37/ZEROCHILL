package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PrimaryTabLibraryTest {
    @Test public void unchangedReturnKeepsViewsAndFavoriteMutationRefreshesAfterEntry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("watch_later", 0).edit().clear().commit();
        LibraryHubView library = new LibraryHubView(activity, null);
        activity.setContentView(library);
        Map<String, Runnable> work = new LinkedHashMap<>();
        library.setWorkScheduler(work::put);
        try {
            library.setSocialActive(true);
            assertNull(ReflectionHelpers.getField(library, "displayed"));
            refresh(library, work);
            View initial = ReflectionHelpers.getField(library, "mediaContent");
            library.setSocialActive(false);
            library.setSocialActive(true);
            assertSame(initial, ReflectionHelpers.getField(library, "mediaContent"));
            refresh(library, work);
            assertSame("Unchanged data must keep image targets and rail views", initial,
                    ReflectionHelpers.getField(library, "mediaContent"));
            library.setSocialActive(false);
            FavoriteStore.add(activity, "Saved one", "https://example.com/saved-one");
            shadowOf(Looper.getMainLooper()).idle();
            assertSame(initial, ReflectionHelpers.getField(library, "mediaContent"));
            library.setSocialActive(true);
            refresh(library, work);
            LinearLayout updated = ReflectionHelpers.getField(library, "mediaContent");
            assertNotSame(initial, updated);
            assertEquals(1, FavoriteStore.load(activity).size());
            FavoriteStore.remove(activity, "https://example.com/saved-one");
            shadowOf(Looper.getMainLooper()).idle();
            refresh(library, work);
            assertTrue(FavoriteStore.load(activity).isEmpty());
        } finally { library.close(); }
    }

    private void refresh(LibraryHubView library, Map<String, Runnable> work) throws Exception {
        Runnable refresh = work.remove("library-refresh");
        if (refresh != null) refresh.run();
        ExecutorService io = ReflectionHelpers.getField(library, "refreshIo");
        io.submit(() -> {}).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        Runnable publish = work.remove("library-publish");
        assertNotNull("Snapshot must be ready without touching displayed views", publish);
        publish.run();
        shadowOf(Looper.getMainLooper()).idle();
    }
}
