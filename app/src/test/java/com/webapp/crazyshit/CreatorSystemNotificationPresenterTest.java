package com.webapp.crazyshit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CreatorSystemNotificationPresenterTest {
    @Test
    public void creatorUpdatesAreCombinedIntoOneBatch() {
        NotificationCoordinator.SourceAlert ninaOne = new NotificationCoordinator.SourceAlert(
                "fapello",
                "Fapello",
                Arrays.asList(item("one"), item("two")),
                "Nina Crowne"
        );
        NotificationCoordinator.SourceAlert ninaTwo = new NotificationCoordinator.SourceAlert(
                "bunkr",
                "Bunkr",
                Arrays.asList(item("three")),
                "Nina Crowne"
        );
        NotificationCoordinator.SourceAlert emily = new NotificationCoordinator.SourceAlert(
                "onlyhaven",
                "OnlyHaven",
                Arrays.asList(item("four")),
                "Emily Rinaudo"
        );
        List<NotificationCoordinator.SourceAlert> alerts = Arrays.asList(ninaOne, ninaTwo, emily);

        assertEquals(
                Arrays.asList("Nina Crowne · 3 new items", "Emily Rinaudo · 1 new item"),
                CreatorSystemNotificationPresenter.notificationLines(alerts)
        );
        assertEquals("2 creators posted new content",
                CreatorSystemNotificationPresenter.notificationTitle(alerts));
    }

    @Test
    public void oneCreatorGetsCreatorSpecificTitle() {
        NotificationCoordinator.SourceAlert alert = new NotificationCoordinator.SourceAlert(
                "fapello",
                "Fapello",
                Arrays.asList(item("one")),
                "Nina Crowne"
        );

        assertEquals("Nina Crowne posted new content",
                CreatorSystemNotificationPresenter.notificationTitle(Arrays.asList(alert)));
    }

    private static NativeContentItem item(String suffix) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Item " + suffix,
                "https://example.com/" + suffix,
                "",
                "",
                "",
                ""
        );
    }
}
