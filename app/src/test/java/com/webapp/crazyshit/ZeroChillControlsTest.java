package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.drawable.Animatable;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillControlsTest {
    @Test public void cancelNeverRunsDestructiveCallbackAndPositiveRunsOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger removed = new AtomicInteger();
        AlertDialog cancel = new ZeroChillDialog.Builder(activity).destructive()
                .setTitle("Remove download")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove", (dialog, which) -> removed.incrementAndGet())
                .create();
        cancel.show();
        cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, removed.get());
        AlertDialog confirm = new ZeroChillDialog.Builder(activity).destructive()
                .setTitle("Remove download")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove", (dialog, which) -> removed.incrementAndGet())
                .create();
        confirm.show();
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, removed.get());
    }

    @Test public void singleChoiceStillExposesCheckedPositionAndExplicitApply() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger selected = new AtomicInteger(-1);
        AlertDialog dialog = new ZeroChillDialog.Builder(activity)
                .setTitle("View style")
                .setSingleChoiceItems(new String[]{"Cards", "List"}, 1, null)
                .setPositiveButton("Apply", (ignored, which) ->
                        selected.set(((AlertDialog) ignored).getListView().getCheckedItemPosition()))
                .create();
        dialog.show();
        assertEquals(1, dialog.getListView().getCheckedItemPosition());
        assertEquals(-1, selected.get());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, selected.get());
    }

    @Test public void menuDispatchesItemCallbackAndControlStylesKeepNativeState() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View anchor = new View(activity);
        ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
        AtomicInteger calls = new AtomicInteger();
        android.view.MenuItem item = menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(clicked -> { calls.incrementAndGet(); return true; });
        menu.getMenu().performItemAction(item, 0);
        assertEquals(1, calls.get());
        ZeroChillSwitch toggle = new ZeroChillSwitch(activity);
        toggle.setChecked(true);
        assertTrue(toggle.isChecked());
        assertNotNull(toggle.getThumbDrawable());
        assertTrue(new ZeroChillProgressBar(activity).getIndeterminateDrawable() instanceof Animatable);
        assertNotNull(new ZeroChillEditText(activity).getBackground());
    }

    @Test public void transientUsesActiveDialogAndHandlesNullErrors() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ZeroChillToast.onResumed(activity);
        AlertDialog dialog = new ZeroChillDialog.Builder(activity)
                .setTitle("Confirmation").setPositiveButton("Done", null).create();
        dialog.show();
        ZeroChillToast.makeText(activity, (CharSequence) null, ZeroChillToast.LENGTH_LONG).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(containsText(dialog.findViewById(android.R.id.content), "Something went wrong."));
        dialog.dismiss();
        ZeroChillToast.makeText(activity, "Something went wrong.", ZeroChillToast.LENGTH_LONG).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(containsText(activity.findViewById(android.R.id.content), "Something went wrong."));
        ZeroChillToast.onPaused(activity);
    }

    private boolean containsText(FrameLayout parent, String expected) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && expected.contentEquals(((TextView) child).getText())) return true;
        }
        return false;
    }

    @Test public void transientReplacesMessageAndClearsOnPause() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ZeroChillToast.onResumed(activity);
        ZeroChillToast.makeText(activity, "First", ZeroChillToast.LENGTH_SHORT).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ZeroChillToast.makeText(activity, "Second", ZeroChillToast.LENGTH_SHORT).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        FrameLayout content = activity.findViewById(android.R.id.content);
        int messages = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof TextView) {
                messages++;
                assertEquals("Second", ((TextView) content.getChildAt(i)).getText().toString());
            }
        }
        assertEquals(1, messages);
        ZeroChillToast.onPaused(activity);
        assertEquals(0, content.getChildCount());
    }
}
