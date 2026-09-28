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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
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

        assertEquals(dp(activity, 350), swipe.getLayoutParams().height);
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

        Field maxItems = ShowsHubView.class.getDeclaredField("HERO_MAX_ITEMS");
        maxItems.setAccessible(true);
        assertEquals(8, maxItems.getInt(null));
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
