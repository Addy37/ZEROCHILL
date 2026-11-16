package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h640dp-xhdpi")
public class ShitTokShareSheetTest {
    @Implements(ZeroChillSessionStore.class)
    public static class Session {
        @Implementation protected static String currentUserId(Context context) { return "owner"; }
    }
    @Implements(ZeroChillSocialRepository.class)
    public static class Messages {
        static final java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        static boolean fail;
        @Implementation protected static void loadInbox(Context context,
                ZeroChillSocialRepository.Callback<java.util.ArrayList<ZeroChillSocialRepository.Conversation>> callback) {
            callback.complete(new java.util.ArrayList<>(), null);
        }
        @Implementation protected static void sendDirectMessage(Context context, String recipient,
                String id, String body, ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage> callback) {
            ids.add(id);
            assertNotNull(ZeroChillVideoCard.decode(body));
            callback.complete(null, fail ? new IllegalStateException("Offline") : null);
        }
    }
    private View find(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = find(((ViewGroup) root).getChildAt(i), description);
            if (result != null) return result;
        }
        return null;
    }

    private NativeContentItem item() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", "https://crazyshit.com/cnt/medias/123", "", "", "", "");
    }

    @Test @Config(shadows = {Session.class, Messages.class})
    public void dmRetryRetainsIdButNewSuccessfulSendUsesNewId() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ShitTokShareSheet sheet = new ShitTokShareSheet(activity, item(), null, new ShitTokShareSheet.Actions());
        ZeroChillSocialRepository.PublicProfile recipient = new ZeroChillSocialRepository.PublicProfile(
                new org.json.JSONObject().put("user_id", "recipient").put("username", "friend"), false);
        Messages.ids.clear();
        try {
            sheet.show();
            Messages.fail = true;
            ReflectionHelpers.callInstanceMethod(sheet, "sendCard", from(ZeroChillSocialRepository.PublicProfile.class, recipient));
            Messages.fail = false;
            ReflectionHelpers.callInstanceMethod(sheet, "sendCard", from(ZeroChillSocialRepository.PublicProfile.class, recipient));
            ReflectionHelpers.callInstanceMethod(sheet, "sendCard", from(ZeroChillSocialRepository.PublicProfile.class, recipient));
            assertEquals(3, Messages.ids.size());
            assertEquals(Messages.ids.get(0), Messages.ids.get(1));
            assertNotEquals(Messages.ids.get(1), Messages.ids.get(2));
        } finally { sheet.dismiss(); activity.finish(); }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void smallScreenUsesOledSurfaceAndSafeInsetsAndRendersPreview() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        actions.download = actions.favorite = actions.notInterested = actions.autoScroll =
                actions.blockCreator = actions.report = actions.comments = actions.details = () -> { };
        actions.canBlock = true;
        ShitTokShareSheet sheet = new ShitTokShareSheet(activity, item(), null, actions);
        try {
            sheet.show();
            ViewGroup content = ReflectionHelpers.getField(sheet, "content");
            View panel = (View) content.getParent().getParent();
            assertEquals(Color.BLACK, ((GradientDrawable) panel.getBackground()).getColor().getDefaultColor());
            int initialBottom = panel.getPaddingBottom();
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(panel,
                    new androidx.core.view.WindowInsetsCompat.Builder()
                            .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                                    androidx.core.graphics.Insets.of(10, 24, 10, 32)).build());
            assertEquals(initialBottom + 32, panel.getPaddingBottom());
            assertTrue(panel.getPaddingLeft() >= 10);
            View share = find(sheet.getWindow().getDecorView(), "Share Video");
            android.widget.ImageView icon = (android.widget.ImageView) ((ViewGroup) share).getChildAt(0);
            assertEquals(AppearanceTheme.accent(activity), icon.getImageTintList().getDefaultColor());
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = panel.getLayoutParams().height;
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            panel.layout(0, 0, width, height);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            panel.draw(new android.graphics.Canvas(bitmap));
            java.io.File output = new java.io.File("build/reports/shittok-share/menu-small-screen.png");
            output.getParentFile().mkdirs();
            try (java.io.FileOutputStream file = new java.io.FileOutputStream(output)) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file));
            } finally { bitmap.recycle(); }
        } finally { sheet.dismiss(); activity.finish(); }
    }

    @Test public void compactSheetRestoresActionsAndSeparatesCopyLinkFromVideoShare() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        AtomicInteger calls = new AtomicInteger();
        actions.autoScrollOn = true;
        actions.autoScroll = calls::incrementAndGet;
        ShitTokShareSheet sheet = new ShitTokShareSheet(activity, item(), null, actions);
        try {
            sheet.show();
            View root = sheet.getWindow().getDecorView();
            for (String label : new String[]{"Share Video", "Copy Link", "WhatsApp", "Messages", "More Apps",
                    "Download", "Add to Favorites", "Not Interested", "Auto Scroll · ON", "Block Creator", "Report", "Comments", "Video details"}) {
                assertNotNull(label, find(root, label));
            }
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.getBehavior().getState());
            find(root, "Copy Link").performClick();
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals(item().url, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            assertNull(ReflectionHelpers.getField(sheet, "preparation"));
            find(root, "Auto Scroll · ON").performClick();
            assertEquals(1, calls.get());
        } finally { sheet.dismiss(); activity.finish(); }
    }

    @Test public void openingAndRepeatedDismissalRetainPlayerAndPositionWhileGuardingAutoScroll() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.host.get().setTheme(R.style.Theme_CrazyShit);
            Object originalPlayer = ReflectionHelpers.getField(f.selected, "player");
            int sources = f.selectedPlayer.sources, seeks = f.selectedPlayer.seeks;
            NativeContentItem current = ReflectionHelpers.getField(f.selected, "item");
            for (int i = 0; i < 3; i++) {
                ReflectionHelpers.callInstanceMethod(f.feed, "share", from(NativeContentItem.class, current));
                ShitTokShareSheet sheet = ReflectionHelpers.getField(f.feed, "shareSheet");
                assertNotNull(sheet);
                assertTrue(sheet.isShowing());
                assertSame(originalPlayer, ReflectionHelpers.getField(f.selected, "player"));
                assertEquals(sources, f.selectedPlayer.sources);
                assertEquals(seeks, f.selectedPlayer.seeks);
                ReflectionHelpers.callInstanceMethod(f.feed, "requestAutoAdvance", from(int.class, 0));
                assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
                sheet.dismiss();
                shadowOf(Looper.getMainLooper()).idle();
            }
            assertEquals(0, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
            assertEquals(0, f.selectedPlayer.releases);
        }
    }

    @Test public void completionLoopsUnderMenuAndBlockRequiresConfirmation() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.host.get().setTheme(R.style.Theme_CrazyShit);
            ShitTokShareSheet sheet = new ShitTokShareSheet(f.host.get(), item(), null, new ShitTokShareSheet.Actions());
            ReflectionHelpers.setField(f.feed, "shareSheet", sheet);
            sheet.show();
            ReflectionHelpers.setField(f.feed, "autoScrollEnabled", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion", from(ExoPlayer.class, f.selectedPlayer.player));
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            assertEquals(1, f.selectedPlayer.seeks);
            sheet.dismiss();
            NativeContentItem creatorVideo = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Creator video",
                    "https://fapello.com/example/123", "", "", "Example", "");
            ReflectionHelpers.callInstanceMethod(f.feed, "confirmBlockCreator", from(NativeContentItem.class, creatorVideo));
            AlertDialog confirmation = ReflectionHelpers.getField(f.feed, "actionDialog");
            assertNotNull(confirmation);
            assertTrue(ShitTokBlockedCreatorStore.keys(f.host.get()).isEmpty());
            confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(ShitTokBlockedCreatorStore.keys(f.host.get()).isEmpty());
            ReflectionHelpers.callInstanceMethod(f.feed, "confirmBlockCreator", from(NativeContentItem.class, creatorVideo));
            confirmation = ReflectionHelpers.getField(f.feed, "actionDialog");
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(ShitTokBlockedCreatorStore.keys(f.host.get()).isEmpty());
        }
    }
}
