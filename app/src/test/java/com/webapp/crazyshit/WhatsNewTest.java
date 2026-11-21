package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class WhatsNewTest {
    private Context context;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WhatsNewPolicy.STORE, 0).edit().clear().commit();
        context.getSharedPreferences("app_prefs", 0).edit().clear().commit();
        install(10000L, 20000L);
    }

    private void install(long first, long update) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        info.firstInstallTime = first;
        info.lastUpdateTime = update;
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    }

    @Test public void freshInstallBaselinesWithoutPopupThenUpgradeIsPending() throws Exception {
        install(10000L, 10000L);
        assertFalse(WhatsNewPolicy.pending(context));
        assertEquals(BuildConfig.VERSION_CODE, context.getSharedPreferences(WhatsNewPolicy.STORE, 0).getLong(WhatsNewPolicy.ACK, 0));
        context.getSharedPreferences(WhatsNewPolicy.STORE, 0).edit().putLong(WhatsNewPolicy.ACK, BuildConfig.VERSION_CODE - 1L).commit();
        install(10000L, 30000L);
        assertTrue(WhatsNewPolicy.pending(context));
    }

    @Test public void existingInstallShowsOnceUntilNextVersionAndKeepsUserPreferences() {
        AppearanceTheme.setAccent(context, AppearanceTheme.Accent.PURPLE);
        assertTrue(WhatsNewPolicy.pending(context));
        assertTrue(WhatsNewPolicy.pending(context));
        WhatsNewPolicy.suppressForCurrentVersion(context);
        assertFalse(WhatsNewPolicy.pending(context));
        assertFalse(WhatsNewPolicy.pending(context));
        assertEquals(AppearanceTheme.Accent.PURPLE, AppearanceTheme.accentChoice(context));
    }

    @Test public void restoredAcknowledgementCannotSuppressUpdatesOnDifferentInstall() throws Exception {
        WhatsNewPolicy.acknowledge(context, BuildConfig.VERSION_CODE + 10L);
        install(50000L, 50000L);
        assertFalse(WhatsNewPolicy.pending(context));
        assertEquals(BuildConfig.VERSION_CODE, context.getSharedPreferences(WhatsNewPolicy.STORE, 0).getLong(WhatsNewPolicy.ACK, 0));
        // Restored user preferences do not cause a first-install popup.
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean(StartupWizardPolicy.PREF_COMPLETE, true).commit();
        assertFalse(WhatsNewPolicy.pending(context));
    }

    @Test public void futureAcknowledgementSurvivesDowngradeWithinSameInstall() {
        WhatsNewPolicy.acknowledge(context, BuildConfig.VERSION_CODE + 10L);
        WhatsNewPolicy.suppressForCurrentVersion(context);
        assertFalse(WhatsNewPolicy.pending(context));
    }

    @Test public void onlyOrdinaryLauncherEntryMayInterruptWithPopup() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        assertTrue(WhatsNewPolicy.isLauncherEntry(launcher));
        assertTrue(WhatsNewPolicy.allowsAutomaticDisplay(launcher));
        assertFalse(WhatsNewPolicy.allowsAutomaticDisplay(new Intent())); // generic notification
        assertFalse(WhatsNewPolicy.allowsAutomaticDisplay(new Intent(AppShortcuts.ACTION_CHAOS)));
        assertFalse(WhatsNewPolicy.allowsAutomaticDisplay(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))));
        Intent handoff = new Intent().putExtra(WhatsNewPolicy.EXTRA_LAUNCHER_ENTRY, true);
        assertTrue(WhatsNewPolicy.allowsAutomaticDisplay(handoff));
        assertFalse(WhatsNewPolicy.allowsAutomaticDisplay(handoff.putExtra("notification", true)));
        assertFalse(WhatsNewPolicy.isLauncherEntry(launcher.putExtra("notification", true)));
    }

    @Test public void mainGateRespectsOnboardingNavigationAndSettingsAcknowledgement() throws Exception {
        Intent launcher = new Intent().putExtra(WhatsNewPolicy.EXTRA_LAUNCHER_ENTRY, true);
        NativeMainActivity activity = Robolectric.buildActivity(NativeMainActivity.class, launcher).get();
        java.lang.reflect.Field pending = NativeMainActivity.class.getDeclaredField("whatsNewPending");
        pending.setAccessible(true);
        pending.setBoolean(activity, true);
        java.lang.reflect.Method gate = NativeMainActivity.class.getDeclaredMethod("shouldOfferWhatsNew");
        gate.setAccessible(true);
        assertFalse((boolean) gate.invoke(activity)); // access has not been accepted
        AccessNoticeDialog.markAccepted(context);
        StartupWizardPolicy.markComplete(context);
        assertTrue((boolean) gate.invoke(activity));
        activity.setIntent(new Intent()); // notification navigation
        assertFalse((boolean) gate.invoke(activity));
        activity.setIntent(launcher);
        WhatsNewPolicy.suppressForCurrentVersion(context); // acknowledged from Settings
        assertFalse((boolean) gate.invoke(activity));
    }

    @Test public void uncertainInstallMetadataDoesNotInventUpgrade() {
        assertFalse(WhatsNewPolicy.isUpgrade(0, 20000));
        assertFalse(WhatsNewPolicy.isUpgrade(10000, 10000));
        assertFalse(WhatsNewPolicy.isUpgrade(10000, 10500));
        assertTrue(WhatsNewPolicy.isUpgrade(10000, 20000));
    }

    @Test public void dialogRestoresWithoutAcknowledgingAndCanBeDeferredForNavigation() {
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        assertTrue(WhatsNewDialog.show(activity, true));
        assertTrue(WhatsNewDialog.show(activity, true));
        assertTrue(WhatsNewDialog.visible(activity));
        controller.recreate();
        activity = controller.get();
        assertTrue(WhatsNewDialog.visible(activity));
        assertTrue(WhatsNewPolicy.pending(context));
        WhatsNewDialog.deferAutomatic(activity);
        activity.getFragmentManager().executePendingTransactions();
        assertFalse(WhatsNewDialog.visible(activity));
        assertTrue(WhatsNewPolicy.pending(context));
        controller.pause().stop().destroy();
    }

    @Test public void taskSavedBeforeUpdateRestoresCurrentVersionHighlights() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        WhatsNewDialog.show(activity, false);
        WhatsNewDialog fragment = (WhatsNewDialog) activity.getFragmentManager().findFragmentByTag(WhatsNewDialog.TAG);
        fragment.getArguments().putLong("code", BuildConfig.VERSION_CODE - 1L);
        fragment.getArguments().putString("version", "previous-version");
        controller.recreate();
        fragment = (WhatsNewDialog) controller.get().getFragmentManager().findFragmentByTag(WhatsNewDialog.TAG);
        assertEquals(BuildConfig.VERSION_CODE, fragment.getArguments().getLong("code"));
        assertEquals(BuildConfig.VERSION_NAME, fragment.getArguments().getString("version"));
        assertTrue(WhatsNewPolicy.pending(context));
        controller.pause().stop().destroy();
    }

    @Test public void explicitAcknowledgementAndManualReopeningWorkOffline() {
        context.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        assertTrue(WhatsNewDialog.show(activity, false));
        WhatsNewDialog fragment = (WhatsNewDialog) activity.getFragmentManager().findFragmentByTag(WhatsNewDialog.TAG);
        java.util.List<String> notes = WhatsNewDialog.notes(activity, "7.0-test");
        assertEquals(8, notes.size());
        assertTrue(notes.get(0).contains("smoother fades"));
        assertTrue(notes.get(0).contains("without changing the navigation dock"));
        assertTrue(notes.get(1).contains("instead of briefly turning black"));
        assertTrue(notes.get(2).contains("featured creator artwork appears sooner"));
        assertTrue(notes.get(3).contains("unrelated profiles"));
        assertTrue(notes.get(4).contains("Bunkr videos"));
        assertTrue(notes.get(4).contains("outside ZEROCHILL's control"));
        assertTrue(notes.get(4).contains("Retry"));
        assertTrue(notes.get(5).contains("Favorite Creators"));
        assertTrue(notes.get(6).contains("BaddieHub"));
        assertTrue(notes.get(7).contains("WikiFeet X"));
        assertEquals(notes, WhatsNewDialog.notes(activity, "6.0.1-dev"));
        find(fragment.getDialog().getWindow().getDecorView(), "LET’S GO").performClick();
        activity.getFragmentManager().executePendingTransactions();
        assertFalse(WhatsNewPolicy.pending(context));
        assertTrue(WhatsNewDialog.show(activity, false));
        fragment = (WhatsNewDialog) activity.getFragmentManager().findFragmentByTag(WhatsNewDialog.TAG);
        find(fragment.getDialog().getWindow().getDecorView(), "Customize appearance").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        activity.getFragmentManager().executePendingTransactions();
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(AppearanceActivity.class.getName(), next.getComponent().getClassName());
        controller.pause().stop().destroy();
    }

    @Test public void longWhatsNewCopyScrollsWithoutEllipsisOnSmallViewport() {
        context.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        assertTrue(WhatsNewDialog.show(activity, false));
        WhatsNewDialog fragment = (WhatsNewDialog) activity.getFragmentManager()
                .findFragmentByTag(WhatsNewDialog.TAG);
        ScrollView scroll = findScroll(fragment.getDialog().getWindow().getDecorView());
        assertNotNull("What's New must use a vertical ScrollView", scroll);
        assertTrue("Scroll indicator should be available", scroll.isVerticalScrollBarEnabled());

        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        TextView longNote = new TextView(activity);
        StringBuilder longCopy = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            longCopy.append("A full release note about the updated app that stays available. ");
        }
        longNote.setText(longCopy.toString());
        content.addView(longNote, content.getChildCount() - 2);

        int width = 280;
        int height = 260;
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, width, height);
        assertTrue("Long notes should exceed a compact viewport",
                content.getHeight() > scroll.getHeight());
        scroll.scrollTo(0, content.getHeight());
        assertTrue("Users must be able to scroll to the bottom", scroll.getScrollY() > 0);
        assertEquals(longCopy.toString(), longNote.getText().toString());
        assertNull("Notes must not use ellipsizing", longNote.getEllipsize());
        assertNotNull(find(content, "LET’S GO"));
        assertNotNull(find(content, "Customize appearance"));
        controller.pause().stop().destroy();
    }

    private ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ScrollView found = findScroll(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private View find(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
}
