package com.webapp.crazyshit;

import android.app.Application;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.LinkedHashSet;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PreferenceSnapshotTest {
    @Test public void changeKeyIgnoresSetInsertionOrderAndDetectsMutationAndClear() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("snapshot-test", 0);
        String empty = PreferenceSnapshot.key(prefs);
        prefs.edit().putStringSet("favorites", new LinkedHashSet<>(Arrays.asList("alpha", "beta"))).commit();
        String before = PreferenceSnapshot.key(prefs);
        assertNotEquals(empty, before);
        prefs.edit().putStringSet("favorites", new LinkedHashSet<>(Arrays.asList("beta", "alpha"))).commit();
        assertEquals(before, PreferenceSnapshot.key(prefs));
        prefs.edit().putString("progress", "120000").commit();
        assertNotEquals(before, PreferenceSnapshot.key(prefs));
        prefs.edit().clear().commit();
        assertEquals(empty, PreferenceSnapshot.key(prefs));
    }
}
