package com.webapp.crazyshit;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class XMegaDriveAgePromptTest {
    @Test public void continueUsesNativePromptWithoutExposingExit() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger continued = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        XMegaDriveAgePrompt prompt = new XMegaDriveAgePrompt(
                activity,
                continued::incrementAndGet,
                cancelled::incrementAndGet
        );

        prompt.show();
        View root = prompt.rootForTest();
        TextView title = root.findViewWithTag(XMegaDriveAgePrompt.TAG_TITLE);
        TextView button = root.findViewWithTag(XMegaDriveAgePrompt.TAG_CONTINUE);
        TextView cancel = root.findViewWithTag(XMegaDriveAgePrompt.TAG_CANCEL);

        assertNotNull(title);
        assertNotNull(button);
        assertNotNull(cancel);
        assertEquals("One quick check", title.getText().toString());
        assertEquals("CONTINUE", button.getText().toString());
        assertFalse(containsText(root, "EXIT"));
        assertFalse(prompt.hasContinueBeenPressed());

        button.performClick();

        assertEquals(1, continued.get());
        assertEquals(0, cancelled.get());
        assertTrue(prompt.hasContinueBeenPressed());
        assertEquals("VERIFYING…", button.getText().toString());
        assertFalse(button.isEnabled());

        prompt.dismiss();
    }

    @Test public void notNowCancelsWithoutTriggeringContinue() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger continued = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        XMegaDriveAgePrompt prompt = new XMegaDriveAgePrompt(
                activity,
                continued::incrementAndGet,
                cancelled::incrementAndGet
        );

        prompt.show();
        TextView cancel = prompt.rootForTest().findViewWithTag(XMegaDriveAgePrompt.TAG_CANCEL);
        assertNotNull(cancel);
        cancel.performClick();

        assertEquals(0, continued.get());
        assertEquals(1, cancelled.get());
        prompt.dismiss();
    }

    private static boolean containsText(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().toUpperCase().contains(needle)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsText(group.getChildAt(index), needle)) return true;
            }
        }
        return false;
    }
}
