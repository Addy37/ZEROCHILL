package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 30, shadows = {
        SocialKeyboardContentTest.Account.class, SocialKeyboardContentTest.Session.class,
        SocialKeyboardContentTest.Social.class})
public class SocialKeyboardContentTest {
    private static final String GIF_URL = "https://static.klipy.com/test/GIF/keyboard.gif";
    private static final byte[] GIF = java.util.Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    private static SocialMedia.Prepared submitted;
    private static String submittedBody, submittedParent, owner;

    @Implements(ZeroChillAccountRepository.class)
    public static class Account {
        @Implementation protected static boolean hasStoredSession(Context c) { return true; }
    }
    @Implements(ZeroChillSessionStore.class)
    public static class Session {
        @Implementation protected static String currentUserId(Context c) { return owner; }
    }
    @Implements(ZeroChillSocialRepository.class)
    public static class Social {
        @Implementation protected static void postComment(Context c, String page, String title,
                String parent, String body, SocialMedia.Prepared media, String id,
                ZeroChillSocialRepository.Callback<Boolean> callback) {
            submitted = media; submittedBody = body; submittedParent = parent;
            callback.complete(false, new Exception("offline"));
        }
        @Implementation protected static void sendDirectMessage(Context c, String recipient,
                String id, String body, SocialMedia.Prepared media,
                ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage> callback) {
            submitted = media; submittedBody = body;
            callback.complete(null, new Exception("offline"));
        }
    }

    @Before public void reset() { submitted = null; submittedBody = submittedParent = ""; owner = "me"; }

    @Test public void commentAndReplyReceiveGifWithoutTextAndKeepExistingUploadPath() throws Exception {
        InlineCommentsDialog dialog = comments();
        EditText input = (EditText) field(dialog, "composerInput");
        SocialMedia.Composer composer = (SocialMedia.Composer) field(dialog, "attachment");
        View send = (View) field(dialog, "sendControl");
        assertGifAdvertised(input);
        input.setText("caption");
        receive(input, GIF);
        assertTrue(composer.preparing); assertFalse(send.isEnabled());
        drain();
        assertArrayEquals(GIF, composer.selected.bytes);
        assertEquals("image/gif", composer.selected.mime);
        assertEquals("caption", input.getText().toString());
        assertEquals(View.VISIBLE, composer.getVisibility()); assertTrue(send.isEnabled());
        invoke(dialog, "submit");
        assertSame(composer.selected, submitted); assertEquals("caption", submittedBody);
        assertEquals("", submittedParent);

        ZeroChillSocialRepository.Comment parent = new ZeroChillSocialRepository.Comment(
                new JSONObject().put("id", "parent").put("user_id", "other").put("body", "hello"), false);
        Method reply = InlineCommentsDialog.class.getDeclaredMethod("startReply", ZeroChillSocialRepository.Comment.class);
        reply.setAccessible(true); reply.invoke(dialog, parent);
        receive(input, GIF); drain();
        assertEquals("", input.getText().toString()); assertTrue(send.isEnabled());
        invoke(dialog, "submit");
        assertSame(composer.selected, submitted); assertEquals("parent", submittedParent);
    }

    @Test public void dmReceivesGifWithoutTextAndRetainsMediaOnFailedSend() throws Exception {
        ZeroChillMessageActivity activity = dm();
        EditText input = (EditText) field(activity, "composer");
        SocialMedia.Composer composer = (SocialMedia.Composer) field(activity, "attachment");
        assertGifAdvertised(input);
        receive(input, GIF); drain();
        assertEquals("", input.getText().toString());
        assertArrayEquals(GIF, composer.selected.bytes);
        assertTrue(((View) field(activity, "send")).isEnabled());
        invoke(activity, "sendMessage");
        assertSame(composer.selected, submitted); assertEquals("", submittedBody);
        assertTrue(((View) field(activity, "send")).isEnabled());
        // Replacing a selected image uses the same single slot; the remove control still works.
        SocialMedia.Prepared previous = composer.selected;
        receive(input, GIF); drain(); assertNotSame(previous, composer.selected);
        composer.getChildAt(1).performClick();
        assertNull(composer.selected); assertFalse(((View) field(activity, "send")).isEnabled());
    }

    @Test @Config(sdk = 35) public void android35ReceiveContentAlsoConsumesGif() throws Exception {
        ZeroChillMessageActivity activity = dm();
        EditText input = (EditText) field(activity, "composer");
        receive(input, GIF); drain();
        assertNotNull(((SocialMedia.Composer) field(activity, "attachment")).selected);
        assertEquals("", input.getText().toString());
    }

    @Test public void appCompatInputConnectionAcceptsNativeKeyboardGif() throws Exception {
        InlineCommentsDialog dialog = comments();
        EditText input = (EditText) field(dialog, "composerInput");
        Uri uri = register(input, GIF);
        EditorInfo info = new EditorInfo();
        android.view.inputmethod.InputConnection connection = input.onCreateInputConnection(info);
        androidx.core.view.inputmethod.InputContentInfoCompat content =
                new androidx.core.view.inputmethod.InputContentInfoCompat(uri,
                        new ClipDescription("GIF", new String[]{"image/gif"}), null);
        assertTrue(androidx.core.view.inputmethod.InputConnectionCompat.commitContent(connection, info, content,
                androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null));
        drain();
        assertArrayEquals(GIF, ((SocialMedia.Composer) field(dialog, "attachment")).selected.bytes);
        assertEquals("", input.getText().toString());
    }

    @Test public void klipyConvertsOnlyAfterSuccessAndPreservesConcurrentCaptionEdits() throws Exception {
        Harness h = new Harness();
        h.input.setText("before " + GIF_URL + " after");
        assertTrue(h.composer.preparing); assertNull(h.composer.selected);
        assertEquals("before " + GIF_URL + " after", h.input.getText().toString());
        h.input.getText().insert(0, "new ");
        h.finish();
        assertEquals("new before  after", h.input.getText().toString());
        assertArrayEquals(GIF, h.composer.selected.bytes); assertEquals(1, h.fetches);
    }

    @Test public void failureLeavesUrlCaptionAndExistingAttachment() throws Exception {
        Harness h = new Harness();
        SocialMedia.Prepared previous = new SocialMedia.Prepared(GIF, "image/gif");
        h.composer.select(previous);
        h.failure = new IllegalArgumentException("Choose an animated GIF.");
        String original = "caption " + GIF_URL;
        h.input.setText(original); h.finish();
        assertEquals(original, h.input.getText().toString()); assertSame(previous, h.composer.selected);
        assertFalse(h.composer.preparing); assertTrue(h.input.getError().toString().contains("animated GIF"));
    }

    @Test public void staleUrlAccountOrCancelledModeCannotReplaceAttachment() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            owner = "me";
            Harness h = new Harness();
            h.input.setText(GIF_URL);
            if (mode == 0) h.input.setText("changed draft");
            if (mode == 1) owner = "another";
            if (mode == 2) h.composer.clear();
            h.finish(); assertNull(h.composer.selected);
            assertEquals(mode == 0 ? "changed draft" : GIF_URL, h.input.getText().toString());
        }
    }

    @Test public void ordinaryUrlsUnsupportedHostsAndIncompleteGifAreNeverFetched() throws Exception {
        Harness h = new Harness();
        for (String text : new String[]{"https://example.com/image.gif", "https://static.klipy.com/a.jpg",
                "http://static.klipy.com/a.gif", "https://static.klipy.com.evil.test/a.gif",
                "https://static.klipy.com@127.0.0.1/a.gif", "https://static.klipy.com:444/a.gif",
                "https://static.klipy.com/a.gi", "ordinary caption"}) {
            h.input.setText(text); assertEquals(text, h.input.getText().toString());
        }
        assertEquals(0, h.work.size()); assertEquals(0, h.fetches);
        assertTrue(SocialKeyboardContent.supportedUrl(GIF_URL + "?token=abc"));
        assertFalse(SocialKeyboardContent.supportedUrl(GIF_URL + "#fragment"));
    }

    @Test public void blockedAndEditComposersDoNotAutoFetch() throws Exception {
        Harness h = new Harness(); h.enabled = false;
        h.input.setText(GIF_URL); assertTrue(h.work.isEmpty());
        ZeroChillMessageActivity activity = dm();
        set(activity, "blockedByMe", true);
        EditText input = (EditText) field(activity, "composer");
        receive(input, GIF);
        assertFalse(((SocialMedia.Composer) field(activity, "attachment")).preparing);
        assertNotNull(input.getError());
        InlineCommentsDialog dialog = comments();
        ZeroChillSocialRepository.Comment comment = new ZeroChillSocialRepository.Comment(
                new JSONObject().put("id", "mine").put("user_id", "me").put("body", "original"), false);
        set(dialog, "editTarget", comment);
        input = (EditText) field(dialog, "composerInput");
        receive(input, GIF);
        assertFalse(((SocialMedia.Composer) field(dialog, "attachment")).preparing);
    }

    @Test public void richInvalidGifAndOversizedGifKeepCaptionAndPreviousSelection() throws Exception {
        InlineCommentsDialog dialog = comments();
        EditText input = (EditText) field(dialog, "composerInput");
        SocialMedia.Composer composer = (SocialMedia.Composer) field(dialog, "attachment");
        SocialMedia.Prepared previous = new SocialMedia.Prepared(GIF, "image/gif");
        composer.select(previous); input.setText("caption");
        receive(input, "<html>no gif</html>".getBytes()); drain();
        assertSame(previous, composer.selected); assertNotNull(input.getError());
        byte[] large = new byte[SocialMedia.GIF_LIMIT + 1]; System.arraycopy(GIF, 0, large, 0, GIF.length);
        receive(input, large); drain();
        assertSame(previous, composer.selected); assertTrue(input.getError().toString().contains("10 MB"));
        assertEquals("caption", input.getText().toString());
    }

    @Test public void fragmentedContentUriHeaderStillEnforcesGifLimitEarly() throws Exception {
        Activity activity = host();
        Uri uri = Uri.parse("content://keyboard/fragmented");
        byte[] huge = new byte[SocialMedia.PHOTO_SOURCE_LIMIT + 1];
        System.arraycopy(GIF, 0, huge, 0, GIF.length);
        ByteArrayInputStream stream = new ByteArrayInputStream(huge) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, pos < 6 ? 1 : length);
            }
        };
        Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri, stream);
        try { SocialMedia.prepare(activity, uri); fail("Accepted oversized GIF"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("10 MB")); }
        assertTrue(stream.available() > SocialMedia.GIF_LIMIT - 16384);
    }

    @Test public void downloadPreservesAnimationAndRejectsNonGifAndOversizeResponses() throws Exception {
        Activity activity = host();
        byte[] animated = getClass().getResourceAsStream("/social-media/animated.gif").readAllBytes();
        FakeConnection connection = new FakeConnection(animated, 200, -1);
        assertArrayEquals(animated, SocialKeyboardContent.download(activity, GIF_URL, url -> connection).bytes);
        assertFalse(connection.getInstanceFollowRedirects()); assertTrue(connection.disconnected);
        rejectDownload(new FakeConnection("<html>bad</html>".getBytes(), 200, -1), "JPEG");
        rejectDownload(new FakeConnection(GIF, 200, SocialMedia.GIF_LIMIT + 1L), "10 MB");
        byte[] huge = new byte[SocialMedia.GIF_LIMIT + 1]; System.arraycopy(GIF, 0, huge, 0, GIF.length);
        rejectDownload(new FakeConnection(huge, 200, -1), "10 MB");
    }

    @Test public void redirectsAreRejectedBeforeReadingAndUnsupportedUrlNeverOpensConnection() throws Exception {
        for (int status : new int[]{301, 302, 307, 308, 404}) {
            FakeConnection c = new FakeConnection(GIF, status, -1);
            rejectDownload(c, "downloaded"); assertFalse(c.read); assertTrue(c.disconnected);
        }
        try {
            SocialKeyboardContent.download(host(), "https://127.0.0.1/a.gif", url -> {
                fail("Unsupported URL opened"); return null;
            }); fail();
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("host")); }
    }

    @Test public void sharedNonSocialEditTextDoesNotAdvertiseImages() {
        assertNull(ViewCompat.getOnReceiveContentMimeTypes(new ZeroChillEditText(host())));
    }

    private class Harness {
        final Activity activity = host();
        final EditText input = new ZeroChillEditText(activity);
        final SocialMedia.Composer composer = new SocialMedia.Composer(activity);
        final ArrayDeque<Runnable> work = new ArrayDeque<>(), main = new ArrayDeque<>();
        Exception failure; int fetches; boolean enabled = true;
        Harness() {
            new SocialKeyboardContent(activity, input, composer, () -> enabled, work::add, main::add, url -> {
                fetches++; if (failure != null) throw failure;
                return SocialMedia.prepareBytes(activity, GIF, "image/gif");
            }).install();
        }
        void finish() { work.remove().run(); main.remove().run(); }
    }

    private static class FakeConnection extends HttpURLConnection {
        final byte[] bytes; final int status; final long size; boolean disconnected, read;
        FakeConnection(byte[] bytes, int status, long size) throws Exception {
            super(new URL(GIF_URL)); this.bytes = bytes; this.status = status; this.size = size;
        }
        public int getResponseCode() { return status; }
        public long getContentLengthLong() { return size; }
        public InputStream getInputStream() { read = true; return new ByteArrayInputStream(bytes); }
        public String getHeaderField(String name) { return "Location".equals(name) ? "https://127.0.0.1/private.gif" : null; }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
    }
    private void rejectDownload(FakeConnection connection, String message) throws Exception {
        try { SocialKeyboardContent.download(host(), GIF_URL, url -> connection); fail("Accepted invalid GIF"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage(), e.getMessage().contains(message)); }
    }
    private Activity host() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit); return activity;
    }
    private InlineCommentsDialog comments() {
        return new InlineCommentsDialog(host(), "https://crazyshit.com/video/clip", "Video", "", null);
    }
    private ZeroChillMessageActivity dm() throws Exception {
        ZeroChillMessageActivity activity = Robolectric.buildActivity(ZeroChillMessageActivity.class).get();
        set(activity, "partnerId", "other"); invoke(activity, "buildUi"); return activity;
    }
    private Uri register(EditText input, byte[] bytes) {
        Uri uri = Uri.parse("content://keyboard/gif/" + System.nanoTime());
        Shadows.shadowOf(input.getContext().getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(bytes));
        return uri;
    }
    private void receive(EditText input, byte[] bytes) {
        ClipData clip = new ClipData("GIF", new String[]{"image/gif"}, new ClipData.Item(register(input, bytes)));
        assertNull(ViewCompat.performReceiveContent(input,
                new ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_INPUT_METHOD).build()));
    }
    private void assertGifAdvertised(EditText input) {
        assertTrue(java.util.Arrays.asList(ViewCompat.getOnReceiveContentMimeTypes(input)).contains("image/gif"));
        EditorInfo info = new EditorInfo(); input.onCreateInputConnection(info);
        assertTrue(java.util.Arrays.asList(EditorInfoCompat.getContentMimeTypes(info)).contains("image/gif"));
    }
    private void drain() throws Exception {
        SocialMedia.WORK.submit(() -> {}).get(15, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value);
    }
    private void invoke(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(o);
    }
}
