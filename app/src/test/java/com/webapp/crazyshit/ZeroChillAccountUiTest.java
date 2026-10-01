package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi",
        shadows = {ZeroChillAccountUiTest.Accounts.class, ZeroChillAccountUiTest.Sessions.class})
public class ZeroChillAccountUiTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Implements(value = ZeroChillAccountRepository.class, isInAndroidSdk = false)
    public static class Accounts {
        @Implementation protected static boolean isConfigured() { return true; }
        @Implementation protected static boolean hasStoredSession(Context context) { return true; }
        @Implementation protected static void current(Context context,
                ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> callback) {
            callback.complete(new ZeroChillAccountRepository.AccountState(true, false,
                    OWNER, "addy@example.com", "addy37test", "Addy37", "", "2026-09-30", "Here for creators."), null);
        }
    }
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) { return OWNER; }
    }

    @Test public void ownIdentityAndProfileFieldsRenderAndOpenPublicProfile() throws Exception {
        ZeroChillAccountActivity activity = create(null);
        render(activity);
        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "YOUR IDENTITY"));
        assertNotNull(find(root, "ACCOUNT & SECURITY"));
        assertNotNull(find(root, "SOCIAL"));
        assertNotNull(find(root, "View public profile  ›"));
        assertNotNull(findType(root, ScrollView.class));
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                activity.getWindow().getAttributes().softInputMode
                        & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
        EditText name = (EditText) findDescription(root, "Display name");
        EditText bio = (EditText) findDescription(root, "Bio");
        assertEquals("Addy37", name.getText().toString());
        assertEquals("Here for creators.", bio.getText().toString());
        name.setText("12345678901234567890123456789012345678901");
        bio.setText(new String(new char[161]).replace('\0', 'b'));
        assertEquals(40, name.length());
        assertEquals(160, bio.length());
        assertNotNull(find(root, "160/160"));

        find(root, "View public profile  ›").performClick();
        Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ZeroChillPublicProfileActivity.class.getName(),
                launched.getComponent().getClassName());
        assertEquals(OWNER, launched.getStringExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID));
    }

    @Test public void draftSurvivesRecreationForSameUser() throws Exception {
        ZeroChillAccountActivity before = create(null);
        render(before);
        ((EditText) findDescription(before.getWindow().getDecorView(), "Display name")).setText("Draft name");
        ((EditText) findDescription(before.getWindow().getDecorView(), "Bio")).setText("Draft bio");
        Bundle state = new Bundle();
        before.onSaveInstanceState(state);
        before.finish();
        ZeroChillAccountActivity after = create(state);
        render(after);
        assertEquals("Draft name", ((EditText) findDescription(
                after.getWindow().getDecorView(), "Display name")).getText().toString());
        assertEquals("Draft bio", ((EditText) findDescription(
                after.getWindow().getDecorView(), "Bio")).getText().toString());
        render(after, new ZeroChillAccountRepository.AccountState(true, false,
                "00000000-0000-0000-0000-000000000002", "another@example.com",
                "another", "Other", "", "2026-09-30", "Other bio"));
        assertEquals("Other bio", ((EditText) findDescription(
                after.getWindow().getDecorView(), "Bio")).getText().toString());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void accountRendersAtNarrowWidthAndLargeFont() throws Exception {
        ZeroChillAccountActivity activity = create(null);
        activity.getResources().getConfiguration().fontScale = 1.5f;
        render(activity);
        View root = activity.findViewById(android.R.id.content);
        int width = 640, height = 1600;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        File folder = new File("build/reports/visual-tests");
        assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, "profile-account-2-narrow.png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        image.recycle();
        assertNotNull(find(root, "CHANGE AVATAR"));
    }

    private static ZeroChillAccountActivity create(Bundle state) {
        return Robolectric.buildActivity(ZeroChillAccountActivity.class)
                .create(state).start().resume().visible().get();
    }

    private static void render(ZeroChillAccountActivity activity) throws Exception {
        render(activity, new ZeroChillAccountRepository.AccountState(true, false,
                OWNER, "addy@example.com", "addy37test", "Addy37", "", "2026-09-30",
                "Here for creators."));
    }

    private static void render(ZeroChillAccountActivity activity,
            ZeroChillAccountRepository.AccountState account) throws Exception {
        Method render = ZeroChillAccountActivity.class.getDeclaredMethod("showProfile",
                ZeroChillAccountRepository.AccountState.class);
        render.setAccessible(true);
        render.invoke(activity, account);
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

    private static View findDescription(View root, String text) {
        if (text.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findDescription(group.getChildAt(i), text);
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
}
