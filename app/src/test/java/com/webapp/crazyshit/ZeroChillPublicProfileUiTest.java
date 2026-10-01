package com.webapp.crazyshit;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZeroChillPublicProfileUiTest {
    @Test public void publicBioIsPlainTextAndEmptyBioHasNoArea() throws Exception {
        ZeroChillPublicProfileActivity activity = create();
        render(activity, profile(false, "Miami. Here for chaos and creators."));
        assertNotNull(find(activity.getWindow().getDecorView(), "Miami. Here for chaos and creators."));
        assertNotNull(find(activity.getWindow().getDecorView(), "addy37test"));
        assertNotNull(findType(activity.getWindow().getDecorView(), ScrollView.class));
        capture(activity.findViewById(android.R.id.content), "profile-account-2-public.png", 320, 800);

        render(activity, profile(false, ""));
        assertNull(find(activity.getWindow().getDecorView(), "Miami. Here for chaos and creators."));
    }

    @Test public void ownProfileRoutesToAccountWithoutOtherUsersActions() throws Exception {
        ZeroChillPublicProfileActivity activity = create();
        render(activity, profile(true, "My bio"));
        assertNull(find(activity.getWindow().getDecorView(), "MESSAGE"));
        assertNull(find(activity.getWindow().getDecorView(), "BLOCK"));
        assertNull(find(activity.getWindow().getDecorView(), "REPORT"));
        View edit = find(activity.getWindow().getDecorView(), "EDIT MY PROFILE");
        assertNotNull(edit);
        edit.performClick();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ZeroChillAccountActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test public void publicProfileUsesCompactHeroAndMessageSizing() throws Exception {
        ZeroChillPublicProfileActivity activity = create();
        render(activity, profile(false, ""));

        View message = find(activity.getWindow().getDecorView(), "MESSAGE");
        assertNotNull(message);
        assertEquals(dp(activity, 44), message.getLayoutParams().height);

        ImageView avatar = (ImageView) findType(activity.getWindow().getDecorView(), ImageView.class);
        assertNotNull(avatar);
        View halo = (View) avatar.getParent();
        assertEquals(dp(activity, 96), halo.getLayoutParams().width);
        assertEquals(dp(activity, 96), halo.getLayoutParams().height);
    }

    @Test public void sharedCreatorsOpenInZeroChillSheet() throws Exception {
        ZeroChillPublicProfileActivity activity = create();
        render(activity, profile(false, ""));

        ArrayList<ZeroChillSocialRepository.SharedCreator> rows = new ArrayList<>();
        rows.add(new ZeroChillSocialRepository.SharedCreator(
                new JSONObject().put("creator_key", "haesicks").put("creator_name", "haesicks")));
        rows.add(new ZeroChillSocialRepository.SharedCreator(
                new JSONObject().put("creator_key", "kira").put("creator_name", "kira pregiato")));

        Method method = ZeroChillPublicProfileActivity.class.getDeclaredMethod(
                "showSharedCreatorsSheet",
                ArrayList.class
        );
        method.setAccessible(true);
        method.invoke(activity, rows);

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertEquals(Dialog.class, dialog.getClass());
        assertNotNull(find(dialog.getWindow().getDecorView(), "Shared creators"));
        assertNotNull(find(dialog.getWindow().getDecorView(), "haesicks"));
        assertNotNull(find(dialog.getWindow().getDecorView(), "kira pregiato"));
        assertNotNull(find(dialog.getWindow().getDecorView(), "CLOSE"));
    }

    @Test public void sharedCreatorNamesStayBoundedWhileCountCanShowFullIntersection() throws Exception {
        ArrayList<ZeroChillSocialRepository.SharedCreator> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(new ZeroChillSocialRepository.SharedCreator(
                new JSONObject().put("creator_key", "key" + i).put("creator_name", "Creator " + i)));
        String preview = ZeroChillPublicProfileActivity.sharedNames(rows);
        assertTrue(preview.contains("Creator 0"));
        assertTrue(preview.contains("Creator 2"));
        assertFalse(preview.contains("Creator 3"));
    }

    private static ZeroChillPublicProfileActivity create() {
        Intent intent = new Intent();
        intent.putExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID, "00000000-0000-0000-0000-000000000001");
        return Robolectric.buildActivity(ZeroChillPublicProfileActivity.class, intent).setup().get();
    }

    private static ZeroChillSocialRepository.PublicProfile profile(boolean mine, String bio) throws Exception {
        return new ZeroChillSocialRepository.PublicProfile(new JSONObject()
                .put("user_id", "00000000-0000-0000-0000-000000000001")
                .put("username", "addy37test")
                .put("display_name", "Addy37")
                .put("bio", bio)
                .put("created_at", "2026-09-30T00:00:00Z"), mine);
    }

    private static void render(ZeroChillPublicProfileActivity activity,
            ZeroChillSocialRepository.PublicProfile profile) throws Exception {
        Method render = ZeroChillPublicProfileActivity.class.getDeclaredMethod("render",
                ZeroChillSocialRepository.PublicProfile.class);
        render.setAccessible(true);
        render.invoke(activity, profile);
        Method busy = ZeroChillPublicProfileActivity.class.getDeclaredMethod("showBusy", boolean.class);
        busy.setAccessible(true);
        busy.invoke(activity, false);
    }

    private static int dp(ZeroChillPublicProfileActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static View find(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static View findType(View root, Class<?> type) {
        if (type.isInstance(root)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findType(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }

    static void capture(View root, String name, int widthDp, int heightDp) throws Exception {
        int width = Math.round(widthDp * root.getResources().getDisplayMetrics().density);
        int height = Math.round(heightDp * root.getResources().getDisplayMetrics().density);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/reports/visual-tests");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }
}
