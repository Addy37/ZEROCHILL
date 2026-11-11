package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShowsHeroDesignTest {
    @Test
    public void usesSwipeableProtectedArtworkHeroGeometry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
        activity.setContentView(hub);

        ScrollView scroll = (ScrollView) hub.getChildAt(0);
        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        HorizontalSwipeFrameLayout swipe = (HorizontalSwipeFrameLayout) content.getChildAt(0);
        MaterialCardView hero = (MaterialCardView) swipe.getChildAt(0);

        assertEquals(
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * 9f / 16f)
                        + dp(activity, 60),
                swipe.getLayoutParams().height);
        assertEquals(0f, hero.getRadius(), 0.01f);
        assertEquals(0, content.getPaddingLeft());
        assertEquals(0, content.getPaddingRight());
        assertTrue(hasText(hero, "Shows"));

        Field backdropField = ShowsHubView.class.getDeclaredField("heroBackdrop");
        backdropField.setAccessible(true);
        ImageView backdrop = (ImageView) backdropField.get(hub);
        FrameLayout.LayoutParams backdropParams =
                (FrameLayout.LayoutParams) backdrop.getLayoutParams();
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, backdropParams.width);
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, backdropParams.height);

        Field imageField = ShowsHubView.class.getDeclaredField("heroImage");
        imageField.setAccessible(true);
        ImageView artwork = (ImageView) imageField.get(hub);
        MaterialCardView artworkCard = (MaterialCardView) artwork.getParent();
        FrameLayout.LayoutParams artworkParams =
                (FrameLayout.LayoutParams) artworkCard.getLayoutParams();
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, artworkParams.width);
        assertEquals(
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * 9f / 16f),
                artworkParams.height
        );
        assertEquals(ImageView.ScaleType.FIT_CENTER, artwork.getScaleType());

        Field previousField = ShowsHubView.class.getDeclaredField("heroPrevious");
        previousField.setAccessible(true);
        ImageView previous = (ImageView) previousField.get(hub);
        assertEquals(artworkCard, previous.getParent());
        assertEquals(View.GONE, previous.getVisibility());

        Field rotation = ShowsHubView.class.getDeclaredField("HERO_ROTATION_MS");
        rotation.setAccessible(true);
        assertEquals(6_000L, rotation.getLong(null));

        Field maxItems = ShowsHubView.class.getDeclaredField("HERO_MAX_ITEMS");
        maxItems.setAccessible(true);
        assertEquals(8, maxItems.getInt(null));
    }

    @Test
    public void keepsContinueWatchingAndBaddiePortraitCardsWhileBrowseShelvesUseTwoRows()
            throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
        activity.setContentView(hub);

        String[] browseShelfFields = new String[] {
                "thisWeekShelf",
                "crazyShelf",
                "efuktShelf",
                "categoryShelf",
                "kaoticCategoryShelf"
        };
        for (String fieldName : browseShelfFields) {
            RecyclerView rail = shelfRail(hub, fieldName);
            RecyclerView.ViewHolder holder =
                    rail.getAdapter().createViewHolder(rail, rail.getAdapter().getItemViewType(0));
            assertEquals(dp(activity, 160), holder.itemView.getLayoutParams().width);
            androidx.recyclerview.widget.GridLayoutManager manager =
                    (androidx.recyclerview.widget.GridLayoutManager) rail.getLayoutManager();
            assertEquals(2, manager.getSpanCount());
            assertEquals(RecyclerView.HORIZONTAL, manager.getOrientation());
            assertTrue(!manager.canScrollVertically());
        }

        RecyclerView baddie = shelfRail(hub, "baddieHubCategoryShelf");
        RecyclerView.ViewHolder baddieHolder = baddie.getAdapter().createViewHolder(baddie, 0);
        assertEquals(dp(activity, 128), baddieHolder.itemView.getLayoutParams().width);
        assertEquals(dp(activity, 178), baddieHolder.itemView.getLayoutParams().height);
        assertEquals(2 * (dp(activity, 178) + dp(activity, 6)), baddie.getLayoutParams().height);
        androidx.recyclerview.widget.GridLayoutManager baddieManager =
                (androidx.recyclerview.widget.GridLayoutManager) baddie.getLayoutManager();
        assertEquals(2, baddieManager.getSpanCount());
        assertEquals(RecyclerView.HORIZONTAL, baddieManager.getOrientation());
        assertTrue(!baddieManager.canScrollVertically());

        RecyclerView xMega = shelfRail(hub, "xMegaDriveCategoryShelf");
        RecyclerView.ViewHolder xMegaHolder = xMega.getAdapter().createViewHolder(xMega, 0);
        assertEquals(baddieHolder.itemView.getLayoutParams().width,
                xMegaHolder.itemView.getLayoutParams().width);
        assertEquals(baddieHolder.itemView.getLayoutParams().height,
                xMegaHolder.itemView.getLayoutParams().height);
        assertEquals(baddie.getLayoutParams().height, xMega.getLayoutParams().height);
        assertEquals(
                ((com.google.android.material.card.MaterialCardView) baddieHolder.itemView).getRadius(),
                ((com.google.android.material.card.MaterialCardView) xMegaHolder.itemView).getRadius(),
                0.5f
        );
        androidx.recyclerview.widget.GridLayoutManager xMegaManager =
                (androidx.recyclerview.widget.GridLayoutManager) xMega.getLayoutManager();
        assertEquals(2, xMegaManager.getSpanCount());
        assertEquals(RecyclerView.HORIZONTAL, xMegaManager.getOrientation());
        assertTrue(!xMegaManager.canScrollVertically());

        RecyclerView continueRail = shelfRail(hub, "continueShelf");
        RecyclerView.ViewHolder continueHolder = continueRail.getAdapter().createViewHolder(
                continueRail,
                continueRail.getAdapter().getItemViewType(0)
        );
        assertEquals(dp(activity, 160), continueHolder.itemView.getLayoutParams().width);
        assertEquals(dp(activity, 129), continueHolder.itemView.getLayoutParams().height);
        assertTrue(continueHolder.itemView.getLayoutParams().width
                > continueHolder.itemView.getLayoutParams().height);
    }

    @Test
    public void heroDotSelectsArtworkAndTracksSourceIdentity() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
        activity.setContentView(hub);

        NativeContentItem first = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "First", "first", "", "", "", "");
        NativeContentItem second = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "Second", "second", "", "", "", "");
        Field itemsField = ShowsHubView.class.getDeclaredField("heroItems");
        itemsField.setAccessible(true);
        itemsField.set(hub, Arrays.asList(first, second));
        Method showHero = ShowsHubView.class.getDeclaredMethod("showHero", int.class, boolean.class);
        showHero.setAccessible(true);
        showHero.invoke(hub, 0, false);

        Field dotsField = ShowsHubView.class.getDeclaredField("heroDots");
        dotsField.setAccessible(true);
        LinearLayout dots = (LinearLayout) dotsField.get(hub);
        assertEquals(2, dots.getChildCount());
        assertTrue(dots.getChildAt(1).performClick());

        Field imageField = ShowsHubView.class.getDeclaredField("heroImage");
        imageField.setAccessible(true);
        ImageView artwork = (ImageView) imageField.get(hub);
        assertEquals("second", artwork.getTag(R.id.shows_watch_source_page));
        assertEquals(2, dots.getChildCount());
    }

    private static RecyclerView shelfRail(ShowsHubView hub, String fieldName) throws Exception {
        Field shelfField = ShowsHubView.class.getDeclaredField(fieldName);
        shelfField.setAccessible(true);
        Object shelf = shelfField.get(hub);

        Field railField = shelf.getClass().getDeclaredField("rail");
        railField.setAccessible(true);
        return (RecyclerView) railField.get(shelf);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static boolean hasText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof android.view.ViewGroup)) return false;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (hasText(group.getChildAt(index), value)) return true;
        }
        return false;
    }
}
