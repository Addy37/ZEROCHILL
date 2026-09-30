package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibrarySocialHubTest {
    private static final class FakeInbox implements LibrarySocialHubView.InboxLoader {
        final ArrayList<ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>>> calls = new ArrayList<>();
        @Override public void load(Context context,
                ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>> callback) {
            calls.add(callback);
        }
    }

    private static ZeroChillSocialRepository.Conversation conversation(String name, String body,
            String avatar, int unread) throws Exception {
        ZeroChillSocialRepository.PublicProfile profile = new ZeroChillSocialRepository.PublicProfile(
                new JSONObject().put("user_id", "other").put("username", "other")
                        .put("display_name", name).put("avatar_path", avatar), false);
        ZeroChillSocialRepository.DirectMessage message = new ZeroChillSocialRepository.DirectMessage(
                new JSONObject().put("id", "m1").put("sender_id", "other")
                        .put("recipient_id", "me").put("body", body)
                        .put("created_at", "2026-09-30T12:00:00Z"));
        return new ZeroChillSocialRepository.Conversation(profile, message, unread);
    }

    @Test public void cardsOpenExistingDestinationsAndHandleEmptyNotificationHistory() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibrarySocialHubView hub = new LibrarySocialHubView(activity, new FakeInbox(), context -> "");
        activity.setContentView(hub);
        assertNotNull(findDescription(hub, "Messages."));
        assertNotNull(findDescription(hub, "Notifications."));
        assertEquals(View.GONE, badge(hub, "messageBadge").getVisibility());
        assertEquals(View.GONE, badge(hub, "notificationBadge").getVisibility());
        findDescription(hub, "Messages.").performClick();
        assertEquals(ZeroChillInboxActivity.class.getName(), Shadows.shadowOf(activity)
                .getNextStartedActivity().getComponent().getClassName());
        findDescription(hub, "Notifications.").performClick();
        assertEquals(UpdateInboxActivity.class.getName(), Shadows.shadowOf(activity)
                .getNextStartedActivity().getComponent().getClassName());
        hub.close();
    }

    @Test public void unreadPreviewLongTextAvatarAndLateAccountCallbackStayScoped() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String[] account = {"account-a"};
        FakeInbox loader = new FakeInbox();
        LibrarySocialHubView hub = new LibrarySocialHubView(activity, loader, context -> account[0]);
        activity.setContentView(hub);
        assertEquals(0, loader.calls.size());
        hub.setActive(true);
        hub.refresh();
        assertEquals("One request while loading", 1, loader.calls.size());
        String longName = new String(new char[120]).replace('\0', 'A');
        String longBody = new String(new char[300]).replace('\0', 'B');
        ArrayList<ZeroChillSocialRepository.Conversation> messages = new ArrayList<>(Arrays.asList(
                conversation(longName, longBody, "", 3)));
        loader.calls.get(0).complete(messages, null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("3", badge(hub, "messageBadge").getText().toString());
        assertEquals(View.VISIBLE, badge(hub, "messageBadge").getVisibility());
        ImageView avatar = (ImageView) field(hub, "messageAvatar");
        assertTrue(avatar.getClipToOutline());
        assertNotNull(avatar.getDrawable());
        assertTrue(((TextView) field(hub, "messageName")).getEllipsize()
                == android.text.TextUtils.TruncateAt.END);
        measure(hub, 320, 270);
        assertTrue(hub.getMeasuredHeight() <= BrowseUi.dp(activity, 270));

        hub.refresh();
        assertEquals(2, loader.calls.size());
        account[0] = "account-b";
        hub.refresh();
        assertEquals(3, loader.calls.size());
        assertEquals(View.GONE, badge(hub, "messageBadge").getVisibility());
        loader.calls.get(1).complete(messages, null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, badge(hub, "messageBadge").getVisibility());
        loader.calls.get(2).complete(new ArrayList<>(), null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("No conversations yet", ((TextView) field(hub, "messageName")).getText());
        hub.setActive(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS);
        assertEquals(3, loader.calls.size());
        hub.close();
    }

    @Test public void libraryRenderHasCompactHubAndExistingEmptyState() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryHubView library = new LibraryHubView(activity, null);
        activity.setContentView(library);
        assertNotNull(findDescription(library, "Messages."));
        assertNotNull(findDescription(library, "Notifications."));
        assertNotNull(findText(library, "Your Library is ready"));
        int width = BrowseUi.dp(activity, 320), height = BrowseUi.dp(activity, 800);
        library.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        library.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        library.draw(new Canvas(bitmap));
        File dir = new File("build/reports/visual-tests");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, "library-social-hub.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
        library.close();
    }

    private static TextView badge(LibrarySocialHubView hub, String name) {
        return (TextView) field(hub, name);
    }

    private static Object field(Object object, String name) {
        try {
            java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static View findDescription(View root, String prefix) {
        CharSequence description = root.getContentDescription();
        if (description != null && description.toString().startsWith(prefix)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findText(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void measure(View view, int width, int height) {
        int w = BrowseUi.dp(view.getContext(), width), h = BrowseUi.dp(view.getContext(), height);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, w, view.getMeasuredHeight());
    }
}
