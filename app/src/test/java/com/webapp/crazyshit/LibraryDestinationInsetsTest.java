package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class LibraryDestinationInsetsTest {
    @Test public void resumedLibraryShellKeepsOneInsetOwnerAndKeyboardHandling() {
        // Attach the real Activity types without starting stores, networking or thumbnail workers.
        // All Favorites routes (History/Favorites/Watch Later/Continue) share this root.
        for (Class<? extends Activity> type : java.util.List.of(FavoritesActivity.class,
                DownloadedActivity.class, CreatorsActivity.class, LikedVideosActivity.class)) {
            Activity activity = Robolectric.buildActivity(type).get();
            activity.setTheme(R.style.Theme_CrazyShit);
            LibraryDestinationChrome.prepareWindow(activity);
            LinearLayout root = BrowseUi.screen(activity);
            LinearLayout header = new LinearLayout(activity);
            TextView title = new TextView(activity);
            title.setText(type.getSimpleName());
            header.addView(title);
            LibraryDestinationChrome.attachHeader(root, header, title);
            activity.setContentView(root);
            WindowInsetsCompat bars = insets(48, 60, 0);
            ViewCompat.dispatchApplyWindowInsets(root, bars);

            // Application -> UiFoundationCoordinator schedules this on every resume.
            // The old controller captured the already applied 60px as base padding.
            ResponsiveFitmentController.applySoon(activity);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            ViewCompat.dispatchApplyWindowInsets(root, bars);
            assertGeometry(activity, root, header, 60, 28);
            ViewCompat.dispatchApplyWindowInsets(root, insets(48, 60, 310));
            assertGeometry(activity, root, header, 60, 310);
            // Repeated resumes / IME hide must not accumulate padding or retain the keyboard gap.
            ResponsiveFitmentController.apply(activity);
            ViewCompat.dispatchApplyWindowInsets(root, bars);
            assertGeometry(activity, root, header, 60, 28);
            ViewCompat.dispatchApplyWindowInsets(root, insets(0, 0, 0));
            assertGeometry(activity, root, header, 0, 28);
            // A genuine large cutout must never be clipped by an arbitrary dp cap.
            ViewCompat.dispatchApplyWindowInsets(root, insets(48, 160, 0));
            assertGeometry(activity, root, header, 160, 28);
            ResponsiveFitmentController.release(activity);
        }
    }

    private static WindowInsetsCompat insets(int status, int cutout, int ime) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(12, cutout, 8, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 28))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime)).build();
    }

    private static void assertGeometry(Activity activity, LinearLayout root, View header,
            int top, int bottom) {
        String name = activity.getClass().getSimpleName();
        assertEquals(name, top, root.getPaddingTop());
        assertEquals(name, bottom, root.getPaddingBottom());
        assertEquals(name, 12, root.getPaddingLeft());
        assertEquals(name, 8, root.getPaddingRight());
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 1920);
        assertEquals(name, top + BrowseUi.dp(activity, 6), header.getTop());
    }
}
