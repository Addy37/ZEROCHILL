package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.time.Duration;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = SplashAccentTest.Preloader.class)
public class SplashAccentTest {
    @Implements(value = ChaosStartupPreloader.class, isInAndroidSdk = false)
    public static class Preloader {
        @Implementation protected static void start(Context context) { }
    }

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_prefs", 0).edit().clear().commit();
        StartupWizardPolicy.markComplete(context);
    }

    @Test public void splashAccentsAllCyanLayersButKeepsFaceAndTeeth() {
        AppearanceTheme.setAccent(RuntimeEnvironment.getApplication(), AppearanceTheme.Accent.PURPLE);
        try (ActivityController<SplashActivity> controller =
                     Robolectric.buildActivity(SplashActivity.class, new Intent()).setup()) {
            SplashActivity activity = controller.get();
            assertEquals(AppearanceTheme.Accent.PURPLE.color,
                    ((TextView) activity.findViewById(R.id.splashChill)).getCurrentTextColor());
            assertNotNull(activity.findViewById(R.id.splashMascotGlow).getBackground());
            for (int id : new int[]{R.id.splashHorns, R.id.splashOutline,
                    R.id.splashXEye, R.id.splashAngryEye, R.id.splashTongue}) {
                assertNotNull(((ImageView) activity.findViewById(id)).getColorFilter());
            }
            for (int id : new int[]{R.id.splashFace, R.id.splashTeeth}) {
                assertNull(((ImageView) activity.findViewById(id)).getColorFilter());
            }
        }
    }

    @Test public void reducedMotionShowsAllLayersWithoutChangingLaunchDeadline() {
        RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        try (ActivityController<SplashActivity> controller =
                     Robolectric.buildActivity(SplashActivity.class, new Intent()).create()) {
            SplashActivity activity = controller.get();
            for (int id : new int[]{R.id.splashMascotGlow, R.id.splashHorns, R.id.splashFace, R.id.splashOutline,
                    R.id.splashXEye, R.id.splashAngryEye, R.id.splashTeeth,
                    R.id.splashTongue, R.id.splashWordmark, R.id.splashTagline}) {
                assertEquals(1f, activity.findViewById(id).getAlpha(), 0f);
            }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(959));
            assertFalse(activity.isFinishing());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
            assertTrue(activity.isFinishing());
        }
    }

    @Test public void launcherHandoffIsMarkedButGenericNotificationIsNot() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        assertHandoff(launcher, true);
        assertHandoff(new Intent(), false);
    }

    private static void assertHandoff(Intent incoming, boolean launcher) {
        try (ActivityController<SplashActivity> controller =
                     Robolectric.buildActivity(SplashActivity.class, incoming).setup()) {
            SplashActivity activity = controller.get();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(960));
            Intent outgoing = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(outgoing);
            assertEquals(NativeMainActivity.class.getName(),
                    outgoing.getComponent().getClassName());
            assertEquals(launcher,
                    outgoing.getBooleanExtra(WhatsNewPolicy.EXTRA_LAUNCHER_ENTRY, !launcher));
        }
    }
}
