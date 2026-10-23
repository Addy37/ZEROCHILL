package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "mdpi")
public class SearchTextContrastTest {
    @Test public void localShadowPreservesNativeTextMetricsAndTransparentRow() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        try {
            LinearLayout row = new LinearLayout(host.get());
            TextView text = new TextView(host.get());
            text.setText("Readable on bright glass"); text.setTextSize(18f);
            text.setPadding(14, 0, 8, 0); row.addView(text);
            row.measure(0, 0);
            int width = row.getMeasuredWidth(), height = row.getMeasuredHeight();
            float size = text.getTextSize();
            SearchTextContrast.apply(row);
            row.forceLayout(); text.forceLayout(); row.measure(0, 0);
            assertEquals(width, row.getMeasuredWidth());
            assertEquals(height, row.getMeasuredHeight());
            assertEquals(size, text.getTextSize(), 0f);
            assertEquals(14, text.getPaddingLeft());
            assertEquals(8, text.getPaddingRight());
            assertEquals(2f, text.getShadowRadius(), 0f);
            assertEquals(0xE6000000, text.getShadowColor());
            assertNull(row.getBackground());
        } finally { host.pause().stop().destroy(); }
    }
}
