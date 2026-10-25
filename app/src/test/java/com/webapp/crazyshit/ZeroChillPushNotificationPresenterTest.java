package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class ZeroChillPushNotificationPresenterTest {
    @Test
    public void directMessageIntentOpensExactConversation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String sender = "707ec1ff-d140-4081-a254-b2bf5d9342b2";

        Intent intent = ZeroChillPushNotificationPresenter.messageIntent(activity, sender);

        assertEquals(ZeroChillMessageActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(sender, intent.getStringExtra(ZeroChillMessageActivity.EXTRA_USER_ID));
    }

    @Test
    public void senderNameIsSanitizedAndMessageBodyIsNeverNeeded() {
        assertEquals("Someone on ZEROCHILL", ZeroChillPushNotificationPresenter.cleanSenderName(" \n\t "));
        assertEquals("vjplus", ZeroChillPushNotificationPresenter.cleanSenderName("  vjplus  "));
        assertEquals(64, ZeroChillPushNotificationPresenter.cleanSenderName("x".repeat(100)).length());
    }

    @Test
    public void messageIdProducesStableNotificationId() {
        String id = "12345678-1234-4234-8234-123456789012";
        assertEquals(
                ZeroChillPushNotificationPresenter.notificationId(id),
                ZeroChillPushNotificationPresenter.notificationId(id)
        );
    }
}
