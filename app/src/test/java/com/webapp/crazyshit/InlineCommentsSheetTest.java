package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.EditText;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-xhdpi",
        shadows = {InlineCommentsSheetTest.Social.class, InlineCommentsSheetTest.Account.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InlineCommentsSheetTest {
    @Implements(ZeroChillSocialRepository.class)
    public static class Social {
        static boolean includePostedReply;
        static ZeroChillSocialRepository.Callback<Boolean> pendingPost;
        @Implementation protected static void loadComments(Context context, String pageUrl,
                ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Comment>> callback) {
            ArrayList<ZeroChillSocialRepository.Comment> comments = new ArrayList<>();
            try {
                comments.add(new ZeroChillSocialRepository.Comment(new JSONObject()
                        .put("id", "parent").put("user_id", "other")
                        .put("display_name", "Creator").put("username", "creator")
                        .put("body", "A useful comment that wraps naturally on a phone screen.")
                        .put("created_at", "2026-11-10T12:00:00Z").put("like_count", 12), false));
                comments.add(new ZeroChillSocialRepository.Comment(new JSONObject()
                        .put("id", "reply").put("parent_id", "parent").put("user_id", "other")
                        .put("display_name", "Viewer").put("username", "viewer")
                        .put("body", "A threaded reply.").put("created_at", "2026-11-10T12:01:00Z"), false));
                if (includePostedReply) comments.add(new ZeroChillSocialRepository.Comment(new JSONObject()
                        .put("id", "posted").put("parent_id", "parent").put("user_id", "me")
                        .put("display_name", "Me").put("body", "New reply")
                        .put("created_at", "2026-11-10T12:02:00Z"), false));
            } catch (Exception e) { throw new AssertionError(e); }
            new android.os.Handler(Looper.getMainLooper()).post(() -> callback.complete(comments, null));
        }
        @Implementation protected static void postComment(Context context, String pageUrl,
                String title, String parentId, String body, SocialMedia.Prepared media, String id,
                ZeroChillSocialRepository.Callback<Boolean> callback) {
            pendingPost = callback;
        }
    }

    @Implements(ZeroChillAccountRepository.class)
    public static class Account {
        @Implementation protected static boolean hasStoredSession(Context context) { return true; }
    }

    @Test public void successfulReplyOpensCollapsedParentAfterReload() throws Exception {
        Social.includePostedReply = false;
        Social.pendingPost = null;
        try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            activity.setTheme(R.style.Theme_CrazyShit);
            InlineCommentsDialog dialog = new InlineCommentsDialog(activity,
                    "https://crazyshit.com/video/clip", "Video", "", null);
            dialog.show();
            shadowOf(Looper.getMainLooper()).idle();
            android.widget.LinearLayout list = ReflectionHelpers.getField(dialog, "commentsContainer");
            assertEquals(View.GONE, list.getChildAt(1).getVisibility());
            java.util.Map<String, ZeroChillSocialRepository.Comment> comments =
                    ReflectionHelpers.getField(dialog, "commentStates");
            ReflectionHelpers.callInstanceMethod(dialog, "startReply",
                    ReflectionHelpers.ClassParameter.from(ZeroChillSocialRepository.Comment.class,
                            comments.get("parent")));
            ((EditText) ReflectionHelpers.getField(dialog, "composerInput")).setText("New reply");
            ReflectionHelpers.callInstanceMethod(dialog, "submit");
            assertNotNull(Social.pendingPost);
            Social.includePostedReply = true;
            Social.pendingPost.complete(true, null);
            shadowOf(Looper.getMainLooper()).idle();
            View posted = ((java.util.Map<String, View>) ReflectionHelpers.getField(dialog, "commentViews"))
                    .get("posted");
            assertNotNull(posted);
            assertEquals(View.VISIBLE, posted.getVisibility());
            dialog.dismiss();
        } finally {
            Social.includePostedReply = false;
            Social.pendingPost = null;
        }
    }

    @Test public void sheetStartsAtSixtyPercentExpandsAndRestoresAfterImeResize() {
        try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            activity.setTheme(R.style.Theme_CrazyShit);
            AtomicInteger moved = new AtomicInteger(), closed = new AtomicInteger();
            InlineCommentsDialog dialog = new InlineCommentsDialog(activity,
                    "https://crazyshit.com/video/clip", "Video", "",
                    new InlineCommentsDialog.ResizeListener() {
                        @Override public void onSheetTopChanged(int top) { moved.incrementAndGet(); }
                        @Override public void onSheetClosed() { closed.incrementAndGet(); }
                    });
            dialog.show();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            FrameLayout sheet = ReflectionHelpers.getField(dialog, "bottomSheet");
            BottomSheetBehavior<?> behavior = ReflectionHelpers.getField(dialog, "behavior");
            assertNotNull(sheet);
            assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, behavior.getState());
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                    dialog.getWindow().getAttributes().softInputMode
                            & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
            View parent = (View) sheet.getParent();
            assertEquals(parent.getHeight() * (1f - behavior.getHalfExpandedRatio()),
                    sheet.getTop(), 3f);
            int width = Math.max(parent.getWidth(), 800);
            int fullHeight = Math.max(parent.getHeight(), 1600);
            parent.layout(0, 0, width, fullHeight);
            shadowOf(Looper.getMainLooper()).idle();
            assertComposerAbove((EditText) ReflectionHelpers.getField(dialog, "composerInput"),
                    parent, 0);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            shadowOf(Looper.getMainLooper()).idle();
            int fullSheetHeight = sheet.getLayoutParams().height;
            assertTrue(fullSheetHeight >= Math.round(fullHeight * .85f));
            dialog.getWindow().setLayout(width, fullHeight - 500);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            assertTrue(sheet.getLayoutParams().height < fullSheetHeight);
            assertComposerAbove((EditText) ReflectionHelpers.getField(dialog, "composerInput"),
                    parent, 0);
            dialog.getWindow().setLayout(width, fullHeight);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            assertEquals(fullSheetHeight, sheet.getLayoutParams().height);
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.getState());
            assertComposerAbove((EditText) ReflectionHelpers.getField(dialog, "composerInput"),
                    parent, 0);
            assertTrue(moved.get() > 0);
            dialog.dismiss();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            assertEquals(1, closed.get());
        }
    }

    @Test public void imeInsetsWithoutParentResizeLiftComposerAndRestoreHalfHeight() {
        try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = host.get();
            activity.setTheme(R.style.Theme_CrazyShit);
            InlineCommentsDialog dialog = new InlineCommentsDialog(activity,
                    "https://crazyshit.com/video/clip", "Video", "", null);
            dialog.show();
            shadowOf(Looper.getMainLooper()).idle();
            FrameLayout sheet = ReflectionHelpers.getField(dialog, "bottomSheet");
            View parent = (View) sheet.getParent();
            View shell = ReflectionHelpers.getField(dialog, "sheetContent");
            int width = Math.max(parent.getWidth(), 800), height = Math.max(parent.getHeight(), 1600);
            parent.layout(0, 0, width, height);
            WindowInsetsCompat open = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 500))
                    .setVisible(WindowInsetsCompat.Type.ime(), true).build();
            ViewCompat.dispatchApplyWindowInsets(shell, open);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(500, shell.getPaddingBottom());
            assertEquals(BottomSheetBehavior.STATE_EXPANDED,
                    ((BottomSheetBehavior<?>) ReflectionHelpers.getField(dialog, "behavior")).getState());
            assertComposerAbove((EditText) ReflectionHelpers.getField(dialog, "composerInput"),
                    parent, 500);
            WindowInsetsCompat hidden = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false).build();
            ViewCompat.dispatchApplyWindowInsets(shell, hidden);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, shell.getPaddingBottom());
            assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED,
                    ((BottomSheetBehavior<?>) ReflectionHelpers.getField(dialog, "behavior")).getState());
            assertComposerAbove((EditText) ReflectionHelpers.getField(dialog, "composerInput"),
                    parent, 0);
            dialog.dismiss();
        }
    }

    private static void assertComposerAbove(EditText composer, View parent, int keyboardBottom) {
        int[] field = new int[2], container = new int[2];
        composer.getLocationOnScreen(field);
        parent.getLocationOnScreen(container);
        assertTrue("Composer must fit above the keyboard",
                field[1] + composer.getHeight() <= container[1] + parent.getHeight() - keyboardBottom + 2);
    }

    @Test public void populatedSheetRendersInPortraitAndLandscape() throws Exception {
        for (String qualifiers : new String[]{"w400dp-h800dp-xhdpi", "w800dp-h400dp-land-xhdpi"}) {
            RuntimeEnvironment.setQualifiers(qualifiers);
            try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
                Activity activity = host.get();
                activity.setTheme(R.style.Theme_CrazyShit);
                InlineCommentsDialog dialog = new InlineCommentsDialog(activity,
                        "https://crazyshit.com/video/clip", "Video", "", null);
                dialog.show();
                shadowOf(Looper.getMainLooper()).idle();
                View list = ReflectionHelpers.getField(dialog, "commentsContainer");
                assertTrue(((ViewGroup) list).getChildCount() > 0);
                View decor = dialog.getWindow().getDecorView();
                int width = activity.getResources().getDisplayMetrics().widthPixels;
                int height = activity.getResources().getDisplayMetrics().heightPixels;
                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                decor.layout(0, 0, width, height);
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
                FrameLayout sheet = ReflectionHelpers.getField(dialog, "bottomSheet");
                View parent = (View) sheet.getParent();
                BottomSheetBehavior<?> behavior = ReflectionHelpers.getField(dialog, "behavior");
                assertEquals(parent.getHeight() * (1f - behavior.getHalfExpandedRatio()),
                        sheet.getTop(), 3f);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(bitmap));
                File folder = new File("build/reports/visual-tests");
                assertTrue(folder.exists() || folder.mkdirs());
                File file = new File(folder, qualifiers.contains("land")
                        ? "comments-sheet-landscape.png" : "comments-sheet-portrait.png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
                }
                bitmap.recycle();
                dialog.dismiss();
            }
        }
    }
}
