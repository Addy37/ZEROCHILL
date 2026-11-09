package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Layout-only checks. Real collection stores, media and routing stay with their activities. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class LibraryDestinationChromeTest {
    @Test public void headerPreservesBackAndOverflowActions() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(activity);
        AtomicInteger backCalls = new AtomicInteger();
        AtomicInteger menuCalls = new AtomicInteger();
        TextView back = BrowseUi.action(activity, "‹", "Back", v -> backCalls.incrementAndGet());
        TextView title = BrowseUi.text(activity, "Favorite creators", 20, -1);
        TextView menu = BrowseUi.action(activity, "⋮", "Menu", v -> menuCalls.incrementAndGet());
        header.addView(back, new LinearLayout.LayoutParams(48, 48));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(menu, new LinearLayout.LayoutParams(48, 48));

        LibraryDestinationChrome.attachHeader(root, header, title);

        assertSame(header, root.getChildAt(0));
        assertEquals("Favorite creators", title.getText().toString());
        assertEquals(2, title.getMaxLines());
        assertEquals(AppearanceTheme.accent(activity), back.getCurrentTextColor());
        assertTrue(back.performClick());
        assertTrue(menu.performClick());
        assertEquals(1, backCalls.get());
        assertEquals(1, menuCalls.get());
        assertTrue(back.getMinimumHeight() >= BrowseUi.dp(activity, 48));
    }

    @Test public void searchAndSummaryKeepTheirExistingTextAndInput() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText input = new EditText(activity);
        input.setHint("Search Downloads");
        input.setText("keep query");
        LibraryDestinationChrome.styleSearch(activity, input);
        assertEquals("keep query", input.getText().toString());
        assertEquals("Search Downloads", input.getHint().toString());
        assertTrue(input.getBackground() instanceof GradientDrawable);
        TextView count = new TextView(activity);
        count.setText("3 downloads");
        LibraryDestinationChrome.styleSummary(activity, count);
        assertEquals("3 downloads", count.getText().toString());
    }

    @Test public void libraryCardsAndLikedRowsRetainClickTargetsAndAccent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        MaterialCardView card = new MaterialCardView(activity);
        LibraryDestinationChrome.styleMediaCard(activity, card);
        assertTrue(card.getStrokeWidth() >= 1);
        assertEquals(0f, card.getCardElevation(), 0.01f);
        AtomicInteger clicks = new AtomicInteger();
        LinearLayout row = new LinearLayout(activity);
        row.setOnClickListener(v -> clicks.incrementAndGet());
        row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        LibraryDestinationChrome.styleLikedRow(activity, row);
        ImageView image = new ImageView(activity);
        LibraryDestinationChrome.styleLikedArtwork(activity, image);
        assertTrue(image.getClipToOutline());
        assertTrue(row.getBackground() instanceof GradientDrawable);
        assertTrue(row.performClick());
        assertEquals(1, clicks.get());
    }
}
