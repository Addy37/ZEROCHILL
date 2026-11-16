package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillVideoCardTest {
    @Implements(PlayableSourceRouter.class)
    public static class Resolver {
        @Implementation protected static CrazyShitRepository.StreamInfo resolve(android.content.Context context, String page) {
            return new CrazyShitRepository.StreamInfo("https://media.example/video.mp4", page, "Shared clip", "https://crazyshit.com/");
        }
    }

    @Test @Config(shadows = Resolver.class)
    public void tappingReceivedCardResolvesIntoExistingNativePlayer() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView body = new TextView(activity);
        String page = "https://crazyshit.com/cnt/medias/123";
        ZeroChillVideoCard.bind(activity, body, ZeroChillVideoCard.encode(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Shared clip", page, "", "", "", "")));
        body.performClick();
        android.content.Intent intent = null;
        for (int i = 0; i < 100 && intent == null; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            intent = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            if (intent == null) Thread.sleep(10);
        }
        assertNotNull(intent);
        assertEquals(VideoDetailActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("https://media.example/video.mp4", intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL));
        assertEquals(page, intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL));
        assertNull(intent.getData());
        activity.finish();
    }
    @Test public void roundTripUsesPageIdentityWithoutCdnOrCredentials() {
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "A video", "https://crazyshit.com/cnt/medias/123-a-video", "", "", "", "");
        String encoded = ZeroChillVideoCard.encode(item);
        assertNotNull(encoded);
        assertTrue(encoded.length() <= 2000);
        assertEquals(item.url, ZeroChillVideoCard.decode(encoded).url);
        assertEquals(item.title, ZeroChillVideoCard.decode(encoded).title);
        assertEquals("Video · A video", ZeroChillVideoCard.preview(encoded));
        assertEquals("Video · A video", ZeroChillPushNotificationPresenter.cleanMessagePreview(encoded));
        assertFalse(encoded.contains("Cookie"));
        assertFalse(encoded.contains("mediaUrl"));
    }

    @Test public void malformedOrUntrustedCardsAreNotPlayable() {
        for (String page : new String[]{"file:///etc/passwd", "content://private/video", "https://127.0.0.1/a",
                "https://crazyshit.com.evil.test/a", "https://user:pass@crazyshit.com/a", "https://crazyshit.com:444/a"}) {
            assertFalse(page, ZeroChillVideoCard.supportedPage(page));
            assertNull(ZeroChillVideoCard.decode(ZeroChillVideoCard.PREFIX + "{\"page\":\"" + page + "\",\"title\":\"Video\"}"));
        }
        assertFalse(ZeroChillVideoCard.supportedPage("https://fapello.com/clip.mp4?token=private"));
        assertFalse(ZeroChillVideoCard.supportedPage("https://crazyshit.com/video?token=private"));
        assertNull(ZeroChillVideoCard.decode("hello"));
        assertNull(ZeroChillVideoCard.decode(ZeroChillVideoCard.PREFIX + "{broken"));
        assertNull(ZeroChillVideoCard.decode("ZEROCHILL video · v2\n{}"));
    }

    @Test public void cardBindingAndRecyclingRestoreOrdinaryMessageBehavior() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView body = new TextView(activity);
        String card = ZeroChillVideoCard.encode(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Example", "https://efukt.com/123.html", "", "", "", ""));
        ZeroChillVideoCard.bind(activity, body, card);
        assertTrue(body.isClickable());
        assertTrue(body.getText().toString().contains("Tap to play in ZEROCHILL"));
        Object cardBinding = body.getTag();
        body.setEnabled(false);
        ZeroChillVideoCard.bind(activity, body, "Ordinary message");
        assertEquals("Ordinary message", body.getText().toString());
        assertNotSame(cardBinding, body.getTag());
        assertTrue(body.isEnabled());
        assertFalse(body.isClickable());
        assertEquals("Ordinary message", ZeroChillVideoCard.preview("Ordinary message"));
        activity.finish();
    }
}
