package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchPaneGeometryTest {
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void opaqueSearchClipsOverflowToItsFullWidthViewport() {
        var host = Robolectric.buildActivity(Activity.class).create().start().resume().visible();
        Activity activity = host.get();
        SearchGlassOwner owner = new SearchGlassOwner(activity);
        SearchGlassLayout shell = new SearchGlassLayout(activity);
        GlassHeaderView pane = new GlassHeaderView(activity, new FrameLayout(activity), 24f, true);
        View overflowing = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                Paint paint = new Paint(); paint.setColor(Color.MAGENTA);
                canvas.drawRect(-1000, -1000, 2000, 2000, paint);
            }
        };
        shell.install(new GlassHeaderView(activity, new FrameLayout(activity), 28f),
                new GlassHeaderView(activity, new FrameLayout(activity), 24f), pane, overflowing);
        shell.installInsets();
        activity.setContentView(shell);
        shell.setResultCount(20);
        for (int ime : new int[]{0, 160, 300}) {
            ViewCompat.dispatchApplyWindowInsets(shell, insets(ime));
            layout(shell);

            assertAligned(pane, overflowing);
            assertFalse(overflowing.getClipToOutline());
            assertEquals(400, overflowing.getWidth());
            Bitmap image = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
            shell.draw(new Canvas(image));
            assertEquals(Color.MAGENTA, image.getPixel(pane.getLeft() + pane.getWidth()/2, pane.getTop() + 40));
            assertEquals(Color.MAGENTA, image.getPixel(pane.getLeft(), (int)pane.getY()));
            assertEquals(Color.MAGENTA, image.getPixel(pane.getLeft(), (int)pane.getY() + pane.getHeight() - 1));
            assertNotEquals(Color.MAGENTA, image.getPixel(pane.getLeft() + pane.getWidth()/2, (int)pane.getY() + pane.getHeight() + 2));
            assertNotEquals(Color.MAGENTA, image.getPixel(pane.getLeft() + pane.getWidth()/2, pane.getTop() - 2));
            image.recycle();
        }
        owner.close(); host.pause().stop().destroy();
    }

    @Test public void imeAnimationContractsBottomWithoutMovingAnchorOrApplyingEndInsetEarly() {
        var host = Robolectric.buildActivity(Activity.class).create().start().resume().visible();
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        SearchGlassLayout shell = (SearchGlassLayout) panel.view;
        shell.setResultCount(20);
        ViewCompat.dispatchApplyWindowInsets(shell, insets(0)); layout(shell);
        View pane = ReflectionHelpers.getField(panel, "resultSurface");
        View rows = ReflectionHelpers.getField(panel, "resultsControls");
        View pill = ReflectionHelpers.getField(panel, "header");
        int top = pane.getTop(), pillTop = pill.getTop(), initialHeight = pane.getHeight();
        WindowInsetsAnimationCompat.Callback callback = ReflectionHelpers.getField(shell, "insetAnimation");
        WindowInsetsAnimationCompat animation = new WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), null, 200);
        callback.onPrepare(animation);
        ViewCompat.dispatchApplyWindowInsets(shell, insets(300)); layout(shell);
        assertEquals(initialHeight, pane.getHeight());
        for (int bottom : new int[]{0, 1, 20, 21, 24, 60, 160, 300, 300, 300}) {
            callback.onProgress(insets(bottom), Collections.singletonList(animation));
            layout(shell);
            assertEquals(top, pane.getTop()); assertEquals(pillTop, pill.getTop());
            assertAligned(pane, rows);
            int gap = BrowseUi.dp(host.get(), 12) - Math.min(BrowseUi.dp(host.get(), 4), Math.max(0, bottom - 20));
            assertEquals(800 - Math.max(20, bottom) - gap, pane.getBottom());
            assertEquals(initialHeight - pane.getHeight(), Math.max(20, bottom) - 20 + gap - BrowseUi.dp(host.get(), 12));
            assertFalse(rows.getClipToOutline());
        }
        callback.onEnd(animation); layout(shell);
        assertEquals(top, pane.getTop()); assertAligned(pane, rows);
        ViewCompat.dispatchApplyWindowInsets(shell, insets(0)); layout(shell);
        assertEquals(initialHeight, pane.getHeight());
        panel.close(); owner.close(); host.pause().stop().destroy();
    }

    private static WindowInsetsCompat insets(int ime) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 20))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime)).build();
    }
    private static void assertAligned(View pane, View rows) {
        assertEquals(pane.getLeft(), rows.getLeft()); assertEquals(pane.getTop(), rows.getTop());
        assertEquals(pane.getRight(), rows.getRight()); assertEquals(pane.getBottom(), rows.getBottom());
    }
    private static void layout(View view) {
        for (int i = 0; i < 2; i++) {
            view.forceLayout();
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 400, 800);
        }
    }
}
