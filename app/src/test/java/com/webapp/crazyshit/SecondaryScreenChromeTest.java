package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SecondaryScreenChromeTest {
    @Test public void flatHeaderAndBackIconKeepExistingActionAndSelection() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout header = new LinearLayout(activity);
        AtomicInteger clicks = new AtomicInteger();
        TextView back = BrowseUi.action(activity, "‹", "Back", v -> clicks.incrementAndGet());
        TextView title = BrowseUi.text(activity, "Messages", 20, Color.WHITE);
        header.addView(back, new LinearLayout.LayoutParams(48, 48));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        SecondaryScreenChrome.header(header, title, false);
        SecondaryScreenChrome.back(back, false);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) header.getBackground()).getColor());
        assertEquals("Messages", title.getText().toString());
        assertNotNull(back.getCompoundDrawables()[0]);
        assertEquals("Back", back.getContentDescription());
        assertTrue(back.performClick());
        SecondaryScreenChrome.back(back, true);
        assertEquals("Clear message selection", back.getContentDescription());
        assertNotNull(back.getCompoundDrawables()[0]);
        assertTrue(back.performClick());
        SecondaryScreenChrome.back(back, false);
        assertEquals("Back", back.getContentDescription());
        assertEquals(2, clicks.get());
        assertEquals(2, header.getChildCount());
    }
}
