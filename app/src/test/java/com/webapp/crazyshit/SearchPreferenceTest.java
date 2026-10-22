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

    @Test public void modeChangeClearsVisibleCreatorCardsSynchronouslyInBothSearchSurfaces() {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            ContentPreference.set(host.get(), ContentPreference.ALL);
            NativeContentItem card = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    "Nolan", "https://cum.st/creators/onlyfans/nolan-1", "", "", "", "",
                    "OnlyHaven", "nolantwink");
            SearchPanel creatorPanel = new SearchPanel(host.get(), new Intent(), true, () -> { });
            SearchPanel globalPanel = new SearchPanel(host.get(), new Intent(), false, () -> { });
            try {
                creatorPanel.create(null);
                OnlyFapCreatorSearchAdapter creatorAdapter =
                        ReflectionHelpers.getField(creatorPanel, "onlyFapAdapter");
                creatorAdapter.replace(java.util.Collections.singletonList(card));
                assertEquals(1, creatorAdapter.getItemCount());
                ContentPreference.set(host.get(), ContentPreference.MEN);
                ReflectionHelpers.callInstanceMethod(creatorPanel, "startOnlyFapSearch",
                        from(String.class, "nolan"));
                assertEquals(0, creatorAdapter.getItemCount());

                ContentPreference.set(host.get(), ContentPreference.ALL);
                globalPanel.create(null);
                GlobalSearchAdapter globalAdapter = ReflectionHelpers.getField(globalPanel, "adapter");
                globalAdapter.replace(java.util.Collections.singletonList(GlobalSearchAdapter.Entry.item(
                        card, GlobalSearchAdapter.SOURCE_REMOTE)));
                assertEquals(1, globalAdapter.getItemCount());
                ReflectionHelpers.setField(globalPanel, "activeQuery", "nolan");
                ReflectionHelpers.setField(globalPanel, "searchPreference", ContentPreference.ALL);
                ContentPreference.set(host.get(), ContentPreference.MEN);
                ReflectionHelpers.callInstanceMethod(globalPanel, "startGlobalSearch",
                        from(String.class, "nolan"), from(boolean.class, true));
                assertEquals(0, globalAdapter.getItemCount());
            } finally {
                creatorPanel.close();
                globalPanel.close();
                ContentPreference.set(host.get(), ContentPreference.ALL);
            }
        }
    }
}
