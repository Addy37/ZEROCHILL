package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchFloatingPresentationTest {
    @Test public void headerAndFavoritesStayAboveOpaqueFullHeightResultsAndHonorReducedMotion() {
        org.robolectric.android.controller.ActivityController<SearchActivity> host =
                Robolectric.buildActivity(SearchActivity.class).create().start().resume().visible();
        try {
            SearchActivity activity = host.get();
            activity.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).apply();
            android.provider.Settings.Global.putFloat(activity.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
            SearchPanel panel = activity.panel;
            layout(panel.view);
            View pill = ReflectionHelpers.getField(panel, "header");
            View favorite = ReflectionHelpers.getField(panel, "favoriteSurface");
            View results = ReflectionHelpers.getField(panel, "resultSurface");
            assertEquals(BrowseUi.dp(activity, 14), pill.getLeft());
            assertEquals(BrowseUi.dp(activity, 56), pill.getHeight());
            assertTrue(pill.getRight() < favorite.getLeft());
            assertTrue(pill.getClipToOutline());
            assertEquals(View.VISIBLE, results.getVisibility());
            panel.field().setText("a");
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            layout(panel.view);
            assertEquals(1f, results.getAlpha(), 0f);
            assertEquals(View.VISIBLE, results.getVisibility());
            assertTrue(results.getTop() > pill.getBottom());
            assertTrue(results.getHeight() > BrowseUi.dp(activity, 500));
            assertEquals(Color.BLACK, ((ColorDrawable) panel.view.getBackground()).getColor());
            View resultLayer = ReflectionHelpers.getField(panel, "resultLayer");
            assertEquals(View.VISIBLE, resultLayer.getVisibility());
            assertEquals(View.GONE, ((View) ReflectionHelpers.getField(panel, "recentLayer")).getVisibility());
            View clear = ReflectionHelpers.getField(panel, "clear");
            clear.performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            assertEquals(View.VISIBLE, results.getVisibility());
            assertFalse(activity.isFinishing());
            SearchResultsCompose list = ReflectionHelpers.getField(panel, "results");
            assertTrue(list.getView() instanceof androidx.compose.ui.platform.ComposeView);
            assertEquals(0, ((FrameLayout.LayoutParams) list.getView().getLayoutParams()).topMargin);
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void searchRowsAreTransparentAndDoNotDuplicateAnAvatarAsPreview() {
        org.robolectric.android.controller.ActivityController<SearchActivity> host =
                Robolectric.buildActivity(SearchActivity.class).create().start().resume();
        android.content.Context context = host.get();
        FrameLayout parent = new FrameLayout(context);
        OnlyFapCreatorSearchAdapter creators = new OnlyFapCreatorSearchAdapter(item -> {});
        OnlyFapCreatorSearchAdapter.Holder holder = creators.onCreateViewHolder(parent, 0);
        assertEquals(Color.TRANSPARENT, holder.card.getCardBackgroundColor().getDefaultColor());
        assertEquals(View.GONE, ((View) holder.preview.getParent()).getVisibility());
        assertEquals(0f, holder.card.getCardElevation(), .01f);
        GlobalSearchAdapter global = new GlobalSearchAdapter(item -> {});
        RecyclerView.ViewHolder result = global.onCreateViewHolder(parent, 1);
        assertTrue(result.itemView instanceof MaterialCardView);
        assertEquals(Color.TRANSPARENT,
                ((MaterialCardView) result.itemView).getCardBackgroundColor().getDefaultColor());
        CreatorListAdapter favorites = new CreatorListAdapter(context, item -> {}, () -> {});
        assertNotNull(favorites.onCreateViewHolder(parent, 0).itemView.getBackground());
        host.pause().stop().destroy();
    }

    private void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 400, 800);
        view.forceLayout();
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 400, 800);
    }
}
