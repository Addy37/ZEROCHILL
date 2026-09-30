package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InlineCommentsUiTest {
    private ZeroChillSocialRepository.Comment comment(String id, String parent, String body, boolean deleted, boolean edited, boolean liked) throws Exception {
        return new ZeroChillSocialRepository.Comment(new JSONObject().put("id", id).put("parent_id", parent)
                .put("user_id", "other").put("body", body).put("username", "@addy37").put("display_name", "Addy")
                .put("created_at", java.time.Instant.now().minusSeconds(240).toString())
                .put("edited_at", edited ? "2026-09-30T12:00:00Z" : JSONObject.NULL)
                .put("deleted_at", deleted ? "2026-09-30T12:01:00Z" : JSONObject.NULL).put("like_count", liked ? 4 : 3), liked);
    }
    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void render(InlineCommentsDialog dialog, ZeroChillSocialRepository.Comment... comments) throws Exception {
        Method m = InlineCommentsDialog.class.getDeclaredMethod("render", ArrayList.class); m.setAccessible(true);
        m.invoke(dialog, new ArrayList<>(Arrays.asList(comments)));
    }
    private InlineCommentsDialog shell() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        activity.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("immersive_motion_enabled", false).commit();
        return new InlineCommentsDialog(activity, "https://crazyshit.com/video/clip", "Video", "", null);
    }
    private String text(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('|');
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) result.append(text(((ViewGroup)view).getChildAt(i)));
        return result.toString();
    }
    @Test public void likesKeepRowAndScrollContainerIdentityAndMetadataIsQuiet() throws Exception {
        InlineCommentsDialog dialog = shell();
        ZeroChillSocialRepository.Comment first = comment("1", "", "A real comment that wraps naturally", false, true, false);
        render(dialog, first);
        LinearLayout list = (LinearLayout) field(dialog, "commentsContainer");
        View row = list.getChildAt(0);
        assertTrue(text(row).contains("Addy")); assertFalse(text(row).contains("@"));
        assertTrue(text(row).contains("Edited"));
        render(dialog, first.withLikeState(true));
        assertSame(row, list.getChildAt(0));
        assertTrue(text(row).contains("♥ 4"));
        assertTrue(((LinearLayout) row).getChildAt(0).getClipToOutline());
    }
    @Test public void deletedParentRemainsAdjacentToItsReplyWithoutControls() throws Exception {
        InlineCommentsDialog dialog = shell();
        ZeroChillSocialRepository.Comment parent = comment("1", "", "Private old body", true, false, false);
        ZeroChillSocialRepository.Comment unrelated = comment("2", "", "Other thread", false, false, false);
        ZeroChillSocialRepository.Comment reply = comment("3", "1", "Still attached", false, false, false);
        render(dialog, parent, unrelated, reply);
        LinearLayout list = (LinearLayout) field(dialog, "commentsContainer");
        assertTrue(text(list.getChildAt(0)).contains("Comment deleted"));
        assertFalse(text(list.getChildAt(0)).contains("Private old body"));
        assertFalse(text(list.getChildAt(0)).contains("Reply"));
        assertFalse(text(list.getChildAt(0)).contains("♡"));
        assertTrue(text(list.getChildAt(1)).contains("Still attached"));
        assertTrue(((LinearLayout.LayoutParams)list.getChildAt(1).getLayoutParams()).leftMargin > 0);
    }
    @Test public void cyclicAndOrphanRepliesRemainBoundedAndRenderOnce() throws Exception {
        ZeroChillSocialRepository.Comment a = comment("a", "b", "a", false, false, false);
        ZeroChillSocialRepository.Comment b = comment("b", "a", "b", false, false, false);
        ZeroChillSocialRepository.Comment orphan = comment("c", "missing", "c", false, false, false);
        ArrayList<ZeroChillSocialRepository.Comment> sorted = InlineCommentsDialog.ordered(new ArrayList<>(Arrays.asList(a,b,orphan)));
        assertEquals(3, sorted.size());
        assertEquals("c", sorted.get(0).id);
    }
    @Test public void commentsRenderAtNarrowPhoneWidth() throws Exception {
        InlineCommentsDialog dialog = shell();
        render(dialog, comment("1", "", "There should be someone hopefully eventually", false, false, false),
                comment("2", "1", "You can keep scrolling and come back to this conversation.", false, true, true),
                comment("3", "", "Comment deleted", true, false, false),
                comment("4", "3", "The reply stays right here.", false, false, false));
        View list = (View) field(dialog,"commentsContainer");
        View shell = list;
        while (shell.getParent() instanceof View) shell = (View) shell.getParent();
        int width = 640, height = 1080;
        shell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        shell.layout(0,0,width,height);
        Bitmap bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); shell.draw(new Canvas(bitmap));
        File folder = new File("build/reports/visual-tests"); assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder,"comments-social-polish.png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        bitmap.recycle();
    }
    @Test public void namesAndRelativeTimesArePresentationOnly() {
        assertEquals("Addy", SocialUi.name("Addy", "addy37"));
        assertEquals("addy37", SocialUi.name("", "@addy37"));
        assertEquals("", SocialUi.relativeTime("bad"));
        assertEquals("now", SocialUi.relativeTime(System.currentTimeMillis()));
        assertEquals("4m", SocialUi.relativeTime(System.currentTimeMillis()-240000L));
    }
}
