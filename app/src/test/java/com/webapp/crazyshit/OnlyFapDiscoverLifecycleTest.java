package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class OnlyFapDiscoverLifecycleTest {
    @Test
    public void discoverPublishesOnceAfterProgressiveShelvesAndRemainsStable() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onViewAllFavorites() { }
        });
        activity.setContentView(hub);
        // Disable unrelated hero IO; the shelf setters and adapter remain real.
        hub.close();
        hub.clear();
        View discover = find(hub, "Discover creators shelf");
        assertNotNull(discover);
        RecyclerView rail = rail(discover);
        assertNotNull(rail);
        hub.setTrending(OnlyFapDiscoverTest.creators(0, 138));
        assertEquals(View.GONE, discover.getVisibility());
        for (int count : new int[]{6, 12, 18, 24, 30}) {
            hub.setNewCreators(OnlyFapDiscoverTest.creators(0, count));
            hub.setHot(OnlyFapDiscoverTest.creators(30, count));
            hub.setPopular(OnlyFapDiscoverTest.creators(60, count));
            assertEquals(View.GONE, discover.getVisibility());
            assertEquals(0, rail.getAdapter().getItemCount());
        }
        assertEquals(30, rail(find(hub, "New creators shelf")).getAdapter().getItemCount());
        assertEquals(30, rail(find(hub, "Hot creators shelf")).getAdapter().getItemCount());
        assertEquals(30, rail(find(hub, "Popular creators shelf")).getAdapter().getItemCount());
        hub.finishLoading();
        assertEquals(View.VISIBLE, discover.getVisibility());
        assertEquals(16, rail.getAdapter().getItemCount());
        long firstId = rail.getAdapter().getItemId(0);
        // Later repeated regular callbacks cannot mutate the committed Discover row.
        hub.setNewCreators(OnlyFapDiscoverTest.creators(0, 30));
        hub.setHot(OnlyFapDiscoverTest.creators(30, 30));
        hub.setPopular(OnlyFapDiscoverTest.creators(60, 30));
        assertEquals(View.VISIBLE, discover.getVisibility());
        assertEquals(16, rail.getAdapter().getItemCount());
        assertEquals(firstId, rail.getAdapter().getItemId(0));
    }

    @Test
    public void successfulDiscoverCacheSurvivesOfflineAndPartialRefresh() {
        Application context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("onlyfap_discover_v2", 0).edit().clear().commit();
        FapzoneCreatorRepository repository = new FapzoneCreatorRepository();
        List<NativeContentItem> cached = repository.completeDiscover(context,
                OnlyFapDiscoverTest.creators(100, 48), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), (listing, page) -> {
                    throw new AssertionError("Full pool should not fetch more pages");
                });
        assertEquals(48, cached.size());
        FapzoneCreatorRepository recreated = new FapzoneCreatorRepository();
        List<NativeContentItem> offline = recreated.completeDiscover(context,
                OnlyFapDiscoverTest.creators(100, 2), OnlyFapDiscoverTest.creators(0, 30),
                Collections.emptyList(), Collections.emptyList(), (listing, page) -> {
                    throw new IOException("offline");
                });
        assertEquals(48, offline.size());
        assertEquals(16, FapzoneCreatorRepository.selectDiscoverItems(offline,
                OnlyFapDiscoverTest.creators(0, 30), Collections.emptyList(),
                Collections.emptyList()).size());
    }

    private static View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
                ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static RecyclerView rail(View view) {
        if (view instanceof RecyclerView) return (RecyclerView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecyclerView found = rail(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
