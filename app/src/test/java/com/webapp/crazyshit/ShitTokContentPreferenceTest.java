package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokContentPreferenceTest {
    private final Application app = RuntimeEnvironment.getApplication();

    @Before public void clean() {
        ContentPreference.prefs(app).edit().clear().commit();
    }

    @Test public void migrationCopiesLegacyOnceWithoutChangingOnlyFap() {
        ContentPreference.set(app, ContentPreference.TRANS_WOMEN);
        assertEquals(ContentPreference.TRANS_WOMEN, ShitTokContentPreference.get(app));
        assertEquals(ContentPreference.TRANS_WOMEN,
                ContentPreference.get(app));
        assertEquals("trans_women", ContentPreference.prefs(app)
                .getString(ShitTokContentPreference.KEY, ""));

        ContentPreference.set(app, ContentPreference.GROUPS);
        assertEquals(ContentPreference.GROUPS, ContentPreference.get(app));
        assertEquals(ContentPreference.TRANS_WOMEN, ShitTokContentPreference.get(app));

        assertTrue(ShitTokContentPreference.set(app, ContentPreference.MEN));
        assertFalse(ShitTokContentPreference.set(app, ContentPreference.MEN));
        assertEquals(ContentPreference.GROUPS, ContentPreference.get(app));
        assertEquals(ContentPreference.MEN, ShitTokContentPreference.get(app));
    }

    @Test public void eachSidePersistsIndependentlyAcrossRecreation() {
        assertEquals(ContentPreference.ALL, ShitTokContentPreference.get(app));
        ContentPreference.set(app, ContentPreference.WOMEN);
        ShitTokContentPreference.set(app, ContentPreference.TRANS_MEN);
        Activity recreated = Robolectric.buildActivity(Activity.class).setup().recreate().get();
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(recreated));
        assertEquals(ContentPreference.TRANS_MEN, ShitTokContentPreference.get(recreated));
        ShitTokContentPreference.set(recreated, ContentPreference.GROUPS);
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(app));
    }

    @Test public void invalidShitTokValueFallsBackWithoutTouchingOnlyFap() {
        ContentPreference.set(app, ContentPreference.WOMEN);
        ContentPreference.prefs(app).edit().putString(
                ShitTokContentPreference.KEY, "obsolete").commit();
        assertEquals(ContentPreference.ALL, ShitTokContentPreference.get(app));
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(app));
    }
}
