package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShitTokOverlayTextViewTest {
    @Test public void outlinedMultilineTextIsVisibleOverBrightAndDarkFootageWithoutAWindowScrim() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShitTokOverlayTextView text = new ShitTokOverlayTextView(activity);
        text.setText("Readable title over bright footage\nCreator name • Video source");
        text.setTextSize(18);
        text.setPadding(16, 12, 16, 12);
        text.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        text.layout(0, 0, 360, 120);
        for (int background : new int[]{Color.WHITE, Color.BLACK}) {
            Bitmap bitmap = Bitmap.createBitmap(360, 120, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(background);
            text.draw(canvas);
            int contrasted = 0;
            for (int y = 5; y < 100; y++) for (int x = 5; x < 355; x++) {
                int value = Color.red(bitmap.getPixel(x, y));
                if (background == Color.WHITE ? value < 100 : value > 200) contrasted++;
            }
            assertTrue("Text contrast must survive the footage", contrasted > 200);
            assertEquals(background, bitmap.getPixel(2, 110));
            assertEquals(background, bitmap.getPixel(350, 110));
            assertEquals(Color.WHITE, text.getCurrentTextColor());
            assertEquals(Paint.Style.FILL, text.getPaint().getStyle());
            File folder = new File("build/reports/shittok-text-polish");
            assertTrue(folder.exists() || folder.mkdirs());
            try (FileOutputStream out = new FileOutputStream(new File(folder,
                    background == Color.WHITE ? "bright-footage.png" : "dark-footage.png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            bitmap.recycle();
        }
        activity.finish();
    }
}
