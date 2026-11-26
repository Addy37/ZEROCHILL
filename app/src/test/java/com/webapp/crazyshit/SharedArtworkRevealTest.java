package com.webapp.crazyshit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SharedArtworkRevealTest {
    @Test public void transitionKeepsRealDrawableAndExpiresWithoutDrawCallbacks() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView view = new ImageView(activity);
        activity.setContentView(view);
        view.layout(0, 0, 200, 200);
        ColorDrawable photo = new ColorDrawable(Color.RED);
        assertTrue(SharedArtworkReveal.transition("test", "one", false)
                .transition(photo, new DrawableImageViewTarget(view)));
        assertSame(photo, view.getDrawable());
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertSame(photo, view.getDrawable());
        assertFalse(SharedArtworkReveal.pending(view));
    }

    @Test public void recyclingCannotRestorePreviousDrawableOrLeaveMask() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView view = new ImageView(activity);
        activity.setContentView(view);
        view.layout(0, 0, 200, 200);
        ColorDrawable first = new ColorDrawable(Color.RED);
        ColorDrawable second = new ColorDrawable(Color.BLUE);
        view.setImageDrawable(first);
        SharedArtworkReveal.start(view, first, "first", false);
        ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS);
        view.setImageDrawable(second);
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertSame(second, view.getDrawable());
        assertFalse(SharedArtworkReveal.pending(view));
    }

    @Test public void completedIdentityDoesNotReplayAndScopesStaySeparate() {
        SharedArtworkReveal.Session session = new SharedArtworkReveal.Session();
        assertFalse(session.seen("shows:one"));
        session.mark("shows:one");
        assertTrue(session.seen("shows:one"));
        assertFalse(session.seen("library:one"));
    }

    @Test public void denseRevealsOverlapWithoutLongQueueDelay() {
        SharedArtworkReveal.Session session = new SharedArtworkReveal.Session();
        assertEquals(1000L, session.reserve(1000L, 5));
        assertEquals(1025L, session.reserve(1000L, 5));
        for (int i = 0; i < 100; i++) assertTrue(session.reserve(1000L, 5) <= 1150L);
    }
}
