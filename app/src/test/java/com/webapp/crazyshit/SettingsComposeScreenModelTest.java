package com.webapp.crazyshit;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SettingsComposeScreenModelTest {
    @Test public void settingsEntriesRetainActionsAndOrder() {
        SettingsComposeScreen.Section section = new SettingsComposeScreen.Section("Notifications & updates");
        AtomicInteger calls = new AtomicInteger();
        SettingsComposeScreen.Item action = section.addAction(
                "Check frequency", "Every hour", "", calls::incrementAndGet);
        section.addAction("Check creator updates", "Idle", "", () -> {});
        assertEquals(2, section.getItems().size());
        assertEquals("Check frequency", section.getItems().get(0).getTitle());
        action.getAction().run();
        assertEquals(1, calls.get());
        action.setSubtitle("Every 3 hours");
        assertEquals("Every 3 hours", action.getSubtitle());
    }

    @Test public void switchInvokesCallbackOncePerActualChange() {
        SettingsComposeScreen.Section section = new SettingsComposeScreen.Section("Appearance");
        AtomicBoolean saved = new AtomicBoolean(true);
        AtomicInteger changes = new AtomicInteger();
        SettingsComposeScreen.Item toggle = section.addSwitch(
                "Haptic feedback", "", true, checked -> {
                    saved.set(checked);
                    changes.incrementAndGet();
                });
        toggle.toggle(false);
        toggle.toggle(false);
        assertFalse(saved.get());
        assertEquals(Boolean.FALSE, toggle.getChecked());
        assertEquals(1, changes.get());
    }
}
