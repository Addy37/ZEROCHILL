package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PrimaryTabWarmupTest {
    @Test public void attachingShowsCardsDoesNotResolveCollectionsAndClickStillOpens() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger warmed = new AtomicInteger(), opened = new AtomicInteger();
        ShowsHubView hub = new ShowsHubView(activity, item -> opened.incrementAndGet(),
                item -> {}, item -> warmed.incrementAndGet());
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_SERIES, "Show",
                "https://crazyshit.com/series/test/", "", "", "", "");
        hub.setCrazyShit(Collections.singletonList(item));
        Object shelf = ReflectionHelpers.getField(hub, "crazyShelf");
        RecyclerView rail = ReflectionHelpers.getField(shelf, "rail");
        bindAndAttach(rail).itemView.performClick();
        assertEquals(0, warmed.get());
        assertEquals(1, opened.get());
    }

    @Test public void bindingOnlyFapCreatorDoesNotStartGalleryWarmup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem item) {}
            @Override public void onSearch() {}
            @Override public void onViewAllFavorites() {}
        });
        try {
            NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    "Bind Probe", "https://fapello.com/bind-probe/", "", "", "", "");
            hub.setNewCreators(Collections.singletonList(creator));
            Object shelf = ReflectionHelpers.getField(hub, "newShelf");
            RecyclerView rail = ReflectionHelpers.getField(shelf, "rail");
            AtomicInteger reserved = ReflectionHelpers.getStaticField(CreatorGalleryPreloader.class, "RESERVED");
            int before = reserved.get();
            bindAndAttach(rail);
            assertEquals(before, reserved.get());
        } finally { hub.close(); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RecyclerView.ViewHolder bindAndAttach(RecyclerView rail) {
        RecyclerView.Adapter adapter = rail.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(rail, adapter.getItemViewType(0));
        adapter.bindViewHolder(holder, 0);
        adapter.onViewAttachedToWindow(holder);
        return holder;
    }
}
