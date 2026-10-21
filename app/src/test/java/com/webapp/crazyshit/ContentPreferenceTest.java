package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.ListView;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ContentPreferenceTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void clean() { ContentPreference.prefs(context).edit().clear().commit(); }

    @Test public void upgradeDefaultAndUnknownValueAreAll() {
        assertEquals(ContentPreference.ALL, ContentPreference.get(context));
        assertFalse(ContentPreference.acknowledge(context, "onlyfap"));
        assertFalse(ContentPreference.set(context, ContentPreference.ALL));
        ContentPreference.prefs(context).edit().putString(ContentPreference.KEY, "future").commit();
        assertEquals(ContentPreference.ALL, ContentPreference.get(context));
    }
    @Test public void actualChangesAreAcknowledgedOncePerSurfaceAcrossRecreation() {
        assertTrue(ContentPreference.set(context, ContentPreference.MEN));
        assertTrue(ContentPreference.acknowledge(context, "onlyfap"));
        assertFalse(ContentPreference.set(context, ContentPreference.MEN));
        Activity activity = Robolectric.buildActivity(Activity.class).setup().recreate().get();
        assertEquals(ContentPreference.MEN, ContentPreference.get(activity));
        assertFalse(ContentPreference.acknowledge(activity, "onlyfap"));
        assertTrue(ContentPreference.acknowledge(activity, "shittok"));
        assertFalse(ContentPreference.acknowledge(activity, "shittok"));
        assertTrue(ContentPreference.set(activity, ContentPreference.ALL));
        assertTrue(ContentPreference.acknowledge(context, "onlyfap"));
    }
    @Test public void sharedNativePickerPersistsAndDoesNotTreatReselectionAsChange() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        int[] changed = {0};
        ContentPreferenceUi.choose(activity, () -> changed[0]++);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ListView choices = dialog.getListView();
        choices.performItemClick(null, ContentPreference.MEN.ordinal(), 0);
        assertEquals(ContentPreference.MEN, ContentPreference.get(context));
        assertEquals(1, changed[0]);
        ContentPreferenceUi.choose(activity, () -> changed[0]++);
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, ContentPreference.MEN.ordinal(), 0);
        assertEquals(1, changed[0]);
    }
    @Test public void categoryMappingUsesVerifiedProviderTokensAndConfiguredRoute() throws Exception {
        JSONObject document = new JSONObject(Files.readString(Paths.get("src/main/assets/source_config_defaults.json")));
        String[] tokens = {"woman", "man", "trans_woman", "trans_man", "nonbinary", "group"};
        SourceConfig.OnlyHaven config = SourceConfig.parseAndValidate(document.toString()).onlyHaven;
        for (int i = 1; i < ContentPreference.values().length; i++) {
            String route = OnlyHavenRepository.categoryRoute(config, ContentPreference.values()[i], 100, 50);
            assertEquals("api/v1/creators?gender=" + tokens[i - 1] + "&content=imported&n=50&o=50", route);
        }
        // An old remote config keeps the bundled contract; a new one can replace data only.
        JSONObject haven = document.getJSONObject("sources").getJSONObject("onlyhaven");
        haven.getJSONObject("routes").remove("creatorCategoryApi");
        haven.remove("categoryValues");
        assertEquals(OnlyHavenRepository.categoryRoute(config, ContentPreference.MEN, 50, 0),
                OnlyHavenRepository.categoryRoute(SourceConfig.parseAndValidate(document.toString()).onlyHaven,
                        ContentPreference.MEN, 50, 0));
        haven.getJSONObject("routes").put("creatorCategoryApi", "api/custom?g={category}&n={limit}&o={offset}");
        assertTrue(OnlyHavenRepository.categoryRoute(SourceConfig.parseAndValidate(document.toString()).onlyHaven,
                ContentPreference.MEN, 10, 0).startsWith("api/custom?g=man"));
    }
    @Test public void preferenceMixPrioritizesOnlyEligibleMatchesWithoutDroppingNormalProviders() {
        NativeContentItem a = clip("kaotic"), b = clip("shit-show"), c = clip("fapello"),
                d = clip("onlyhaven-1"), e = clip("onlyhaven-2"), f = clip("efukt");
        List<NativeContentItem> original = Arrays.asList(a, b, c, d, e, f);
        List<NativeContentItem> ordered = ShitTokPreferenceMix.order(original,
                item -> item == d || item == e);
        assertEquals(Arrays.asList(d, a, b, e, c, f), ordered);
        assertEquals(new HashSet<>(original), new HashSet<>(ordered));
        assertEquals(original, ShitTokPreferenceMix.order(original, item -> false));
    }
    @Test public void preferenceChangePreservesPlayingGenerationAndNextQueueItem() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ChaosFeedView feed = new ChaosFeedView(activity, item -> {}, false);
        org.robolectric.util.ReflectionHelpers.setField(feed, "queuePreference", ContentPreference.ALL);
        org.robolectric.util.ReflectionHelpers.setField(feed, "feedGeneration", 7);
        ArrayList<NativeContentItem> items = org.robolectric.util.ReflectionHelpers.getField(feed, "items");
        NativeContentItem current = clip("current"), next = clip("next"), future = clip("future");
        items.add(current); items.add(next); items.add(future);
        ContentPreference.set(activity, ContentPreference.MEN);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(feed, "applyContentPreference");
        assertEquals(7, (int) org.robolectric.util.ReflectionHelpers.getField(feed, "feedGeneration"));
        assertEquals(Arrays.asList(current, next), items);
        assertEquals(0, (int) org.robolectric.util.ReflectionHelpers.getField(feed, "selectedPosition"));
        assertEquals(ContentPreference.MEN, org.robolectric.util.ReflectionHelpers.getField(feed, "queuePreference"));
        feed.close();
    }

    private NativeContentItem clip(String name) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name, "https://test/" + name,
                "", "", name, "", "", "");
    }
}
