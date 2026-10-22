package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class SearchPreferenceTest {
    @Test public void snapshotCannotReviveCreatorsFromEarlierMode() throws Exception {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            ContentPreference.set(host.get(), ContentPreference.ALL);
            SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> { });
            try {
                panel.create(null);
                ReflectionHelpers.setField(panel, "activeQuery", "nolan");
                JSONObject old = new JSONObject().put("query", "nolan").put("preference", "all")
                        .put("complete", true).put("creators", new org.json.JSONArray());
                ContentPreference.set(host.get(), ContentPreference.MEN);
                assertFalse((Boolean) ReflectionHelpers.callInstanceMethod(panel, "restoreSnapshot",
                        from(JSONObject.class, old)));
                assertEquals(0, panel.field().length());
            } finally {
                panel.close();
                ContentPreference.set(host.get(), ContentPreference.ALL);
            }
        }
    }
}
