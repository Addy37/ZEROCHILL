package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import androidx.media3.exoplayer.ExoPlayer;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h640dp-xhdpi")
public class ShitTokShareSheetTest {
    private View find(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = find(((ViewGroup) root).getChildAt(i), description);
            if (result != null) return result;
        }
        return null;
    }

    private boolean containsText(View root, String phrase) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(phrase)) return true;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            if (containsText(((ViewGroup) root).getChildAt(i), phrase)) return true;
        }
        return false;
    }

    private NativeContentItem item() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", "https://crazyshit.com/cnt/medias/123", "", "", "", "");
    }

    private void assertCompactLayout(String qualifier, float fontScale) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        Configuration previous = new Configuration(activity.getResources().getConfiguration());
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        actions.notInterested = actions.autoScroll =
                actions.blockCreator = actions.report = actions.details = () -> { };
        actions.canBlock = true;
        ShitTokShareSheet sheet = new ShitTokShareSheet(activity, item(), null, actions);
        try {
            sheet.show();
            View root = sheet.getWindow().getDecorView();
            assertNull(find(root, "Find recipient"));
            assertNull(find(root, "Send to ZEROCHILL"));
            for (String removed : new String[]{"Send to ZEROCHILL", "Find recipient", "Sends a video file",
                    "Some streams can't", "Video cards play", "WhatsApp", "Messages", "More Apps", "Download", "Favorite", "Comments", "Video details"}) {
                assertFalse("Removed text still visible: " + removed, containsText(root, removed));
            }
            assertEquals(5, ((ViewGroup) find(root, "Not Interested").getParent()).getChildCount());
            ViewGroup actionRow = (ViewGroup) find(root, "Not Interested").getParent();
            assertTrue(actionRow.getParent() instanceof HorizontalScrollView);
            assertSame(actionRow, find(root, "Report").getParent());
            ViewGroup externalRow = (ViewGroup) find(root, "Share Video").getParent();
            assertEquals(2, externalRow.getChildCount());
            assertTrue(externalRow.getParent() instanceof HorizontalScrollView);
            assertSame(externalRow, find(root, "Copy Link").getParent());
            ViewGroup content = ReflectionHelpers.getField(sheet, "content");
            View panel = (View) content.getParent().getParent();
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().heightPixels,
                            View.MeasureSpec.AT_MOST));
            int height = panel.getMeasuredHeight();
            assertTrue(height <= activity.getResources().getDisplayMetrics().heightPixels * .65f + 1);
            panel.layout(0, 0, width, height);
            NestedScrollView verticalScroll = (NestedScrollView) content.getParent();
            if ("landscape".equals(qualifier)) {
                assertTrue("Landscape content needs vertical scrolling", content.getHeight() > verticalScroll.getHeight());
            }
            for (ViewGroup row : new ViewGroup[]{externalRow, actionRow}) {
                for (int i = 1; i < row.getChildCount(); i++) {
                    assertTrue("Tiles overlap in " + qualifier, row.getChildAt(i - 1).getRight() <= row.getChildAt(i).getLeft());
                }
                assertTrue("Row clips its tiles", row.getChildAt(row.getChildCount() - 1).getRight() <= row.getWidth());
                if ("portrait".equals(qualifier)) {
                    assertTrue("Tiles should fit a 384dp phone without horizontal scrolling",
                            row.getWidth() <= ((HorizontalScrollView) row.getParent()).getWidth());
                }
                for (int i = 0; i < row.getChildCount(); i++) {
                    ViewGroup tile = (ViewGroup) row.getChildAt(i);
                    TextView label = (TextView) tile.getChildAt(1);
                    assertTrue("Label clipped: " + label.getText(), label.getBottom() <= tile.getHeight());
                    assertTrue("Label has too many lines: " + label.getText(), label.getLineCount() <= 4);
                    assertEquals("Label truncated: " + label.getText(), label.getText().length(),
                            label.getLayout().getLineEnd(label.getLineCount() - 1));
                }
            }
            assertNull(find(root, "Comments"));
            assertNotNull(find(root, "Details"));
            if (fontScale == 1f) assertTrue("Menu should be shorter than beta 3", height < BrowseUi.dp(activity, 340));
            if (!"landscape".equals(qualifier)) assertTrue("Portrait hides bottom action labels",
                    content.getHeight() <= verticalScroll.getHeight());
        } finally {
            sheet.dismiss();
            activity.getResources().updateConfiguration(previous, activity.getResources().getDisplayMetrics());
            activity.finish();
        }
    }

    @Test @Config(qualifiers = "w384dp-h800dp-xhdpi")
    public void compactPortraitFitsExternalAndActionRows() { assertCompactLayout("portrait", 1f); }

    @Test @Config(qualifiers = "w640dp-h320dp-xhdpi")
    public void compactLandscapeKeepsRowsAndCapsHeight() { assertCompactLayout("landscape", 1f); }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    public void narrowScreenWithLargeTextCanScrollWithoutClippingLabels() { assertCompactLayout("large text", 1.5f); }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void smallScreenUsesOledSurfaceAndSafeInsetsAndRendersPreview() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        actions.notInterested = actions.autoScroll =
                actions.blockCreator = actions.report = actions.details = () -> { };
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
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().heightPixels,
                            View.MeasureSpec.AT_MOST));
            int height = panel.getMeasuredHeight();
            panel.layout(0, 0, width, height);
            assertTrue("Insets must not hide action labels on a portrait phone",
                    content.getHeight() <= ((View) content.getParent()).getHeight());
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
            for (String label : new String[]{"Share Video", "Copy Link", "Not Interested", "Auto Scroll · ON", "Block Creator", "Report", "Details"}) {
                assertNotNull(label, find(root, label));
            }
            assertFalse(find(root, "Block Creator").isEnabled());
            assertNull(find(root, "Find recipient"));
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.getBehavior().getState());
            find(root, "Copy Link").performClick();
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals(item().url, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            assertNull(ReflectionHelpers.getField(sheet, "preparation"));
            find(root, "Auto Scroll · ON").performClick();
            assertEquals(1, calls.get());
            assertTrue(sheet.isShowing());
            assertNotNull(find(root, "Auto Scroll · OFF"));
            assertFalse(find(root, "Auto Scroll · OFF").isSelected());
        } finally { sheet.dismiss(); activity.finish(); }
    }

    @Test public void autoScrollIconAndStateUpdateImmediatelyAndBlockCanResolveLater() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        actions.autoScroll = () -> { };
        actions.blockCreator = () -> { };
        ShitTokShareSheet sheet = new ShitTokShareSheet(activity, item(), null, actions);
        try {
            sheet.show();
            View root = sheet.getWindow().getDecorView();
            View toggle = find(root, "Auto Scroll · OFF");
            assertFalse(toggle.isSelected());
            toggle.performClick();
            assertSame(toggle, find(root, "Auto Scroll · ON"));
            assertTrue(toggle.isSelected());
            assertEquals(Color.BLACK, ((android.widget.ImageView) ((ViewGroup) toggle).getChildAt(0))
                    .getImageTintList().getDefaultColor());
            assertFalse(find(root, "Block Creator").isEnabled());
            sheet.updateCanBlock(true);
            assertTrue(find(root, "Block Creator").isEnabled());
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
            f.selectedPlayer.state = androidx.media3.common.Player.STATE_ENDED;
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
    @Test public void resolvedAvatarIdentityEnablesBlockingAndPrunesMatchingClips() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.host.get().setTheme(R.style.Theme_CrazyShit);
            NativeContentItem media = ReflectionHelpers.getField(f.selected, "item");
            ShitTokCreatorMetadataLoader loader = ReflectionHelpers.getField(f.feed, "creatorMetadata");
            String key = ReflectionHelpers.callStaticMethod(ShitTokCreatorMetadataLoader.class, "key",
                    from(NativeContentItem.class, media));
            java.util.Map<String, NativeContentItem> identities = ReflectionHelpers.getField(loader, "identities");
            ReflectionHelpers.callInstanceMethod(f.feed, "share", from(NativeContentItem.class, media));
            ShitTokShareSheet sheet = ReflectionHelpers.getField(f.feed, "shareSheet");
            assertFalse(find(sheet.getWindow().getDecorView(), "Block Creator").isEnabled());
            identities.put(key, new NativeContentItem(NativeContentItem.KIND_CREATOR, "Known Creator",
                    "https://fapello.com/known/", "", "", "", ""));
            ReflectionHelpers.callInstanceMethod(f.feed, "onCreatorMetadataReady", from(String.class, media.url));
            assertTrue(find(sheet.getWindow().getDecorView(), "Block Creator").isEnabled());
            find(sheet.getWindow().getDecorView(), "Block Creator").performClick();
            AlertDialog confirmation = ReflectionHelpers.getField(f.feed, "actionDialog");
            assertNotNull(confirmation);
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(ShitTokBlockedCreatorStore.keys(f.host.get()).contains(
                    ShitTokBlockedCreatorStore.keyForName("Known Creator")));
            java.util.List<NativeContentItem> items = ReflectionHelpers.getField(f.feed, "items");
            assertFalse(items.contains(media));
        }
    }
    @Test public void offscreenBlockedIdentityIsPrunedWhenMetadataResolves() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            java.util.List<NativeContentItem> items = ReflectionHelpers.getField(f.feed, "items");
            NativeContentItem selected = items.get(0), offscreen = items.get(1);
            ShitTokCreatorMetadataLoader loader = ReflectionHelpers.getField(f.feed, "creatorMetadata");
            String key = ReflectionHelpers.callStaticMethod(ShitTokCreatorMetadataLoader.class, "key",
                    from(NativeContentItem.class, offscreen));
            java.util.Map<String, NativeContentItem> identities = ReflectionHelpers.getField(loader, "identities");
            identities.put(key, new NativeContentItem(NativeContentItem.KIND_CREATOR, "Blocked Creator",
                    "https://fapello.com/blocked/", "", "", "", ""));
            ShitTokBlockedCreatorStore.blockName(f.host.get(), "Blocked Creator");
            ReflectionHelpers.callInstanceMethod(f.feed, "reloadBlockedCreatorKeys");
            ReflectionHelpers.callInstanceMethod(f.feed, "onCreatorMetadataReady", from(String.class, offscreen.url));
            assertSame(selected, items.get(0));
            assertFalse(items.contains(offscreen));
        }
    }
}
