package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.Context;
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

    @Test public void upgradeDefaultUnknownAndRemovedModeAreAll() {
        assertEquals(ContentPreference.ALL, ContentPreference.get(context));
        assertFalse(ContentPreference.acknowledge(context, "onlyfap"));
        assertFalse(ContentPreference.set(context, ContentPreference.ALL));
        ContentPreference.prefs(context).edit().putString(ContentPreference.KEY, "future").commit();
        assertEquals(ContentPreference.ALL, ContentPreference.get(context));
        ContentPreference.prefs(context).edit().putString(ContentPreference.KEY, "non_binary").commit();
        assertEquals(ContentPreference.ALL, ContentPreference.get(context));
        assertEquals(6, ContentPreference.values().length);
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
        assertNull(dialog.getWindow().getDecorView().findViewWithTag("preference_non_binary"));
        dialog.getWindow().getDecorView().findViewWithTag("preference_men").performClick();
        assertEquals(ContentPreference.MEN, ContentPreference.get(context));
        assertEquals(1, changed[0]);
        ContentPreferenceUi.choose(activity, () -> changed[0]++);
        ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("preference_men").performClick();
        assertEquals(1, changed[0]);
    }
    @Test public void settingsAndOnlyFapSelectorChangeTheSamePreference() {
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity settings = controller.get();
        android.view.View title = findText(settings.getWindow().getDecorView(), "Content preference");
        assertNotNull(title);
        ((android.view.View) title.getParent().getParent()).performClick();
        ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("preference_men").performClick();
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(host, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) {}
            @Override public void onSearch() {}
            @Override public void onViewAllFavorites() {}
        });
        android.widget.TextView selector = org.robolectric.util.ReflectionHelpers.getField(hub, "preferenceSelector");
        assertEquals("♂  ▾", selector.getText().toString());
        assertEquals("Content preference: Men", selector.getContentDescription());
        selector.performClick();
        ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView().findViewWithTag("preference_women").performClick();
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(settings));
        assertEquals("♀  ▾", selector.getText().toString());
        assertEquals("Content preference: Women", selector.getContentDescription());
        hub.close(); controller.destroy();
    }
    @Test public void glassPillAndPickerReflectEveryModeAfterRecreationAndCancelDoesNotChangeIt() {
        org.robolectric.android.controller.ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup();
        for (ContentPreference mode : ContentPreference.values()) {
            ContentPreference.set(context, mode);
            controller.recreate();
            Activity activity = controller.get();
            android.widget.TextView pill = new android.widget.TextView(activity);
            ContentPreferenceUi.bindSelector(pill);
            assertEquals((mode.symbol.isEmpty() ? "" : mode.symbol + "  ") + "▾", pill.getText().toString());
            assertFalse(pill.getText().toString().contains(mode.label));
            assertEquals(mode.icon() != 0, pill.getCompoundDrawablesRelative()[0] != null);
            assertEquals("Content preference: " + mode.label, pill.getContentDescription());
            assertEquals(mode, pill.getTag(R.id.content_preference_mode));
            assertNotNull(pill.getBackground());
            ContentPreferenceUi.choose(activity, () -> fail("Cancel cannot change preference"));
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            android.view.View row = dialog.getWindow().getDecorView().findViewWithTag("preference_" + mode.value);
            assertTrue(row.isSelected());
            android.view.accessibility.AccessibilityNodeInfo info = row.createAccessibilityNodeInfo();
            assertTrue(info.isCheckable()); assertTrue(info.isChecked());
            dialog.getWindow().getDecorView().findViewWithTag("preference_cancel").performClick();
            assertFalse(dialog.isShowing());
            assertEquals(mode, ContentPreference.get(activity));
        }
        controller.destroy();
    }

    @Test public void filteredHeroAcceptsCategoryCreatorsAndRejectsUnrelatedCachedCreators() throws Exception {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = new OnlyFapHubView(host, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) {}
            @Override public void onSearch() {}
            @Override public void onViewAllFavorites() {}
        });
        ContentPreference.set(context, ContentPreference.MEN);
        hub.preparePreference(ContentPreference.MEN);
        NativeContentItem matching = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Matching", "https://cum.st/creators/onlyfans/123", "", "", "", "", "OnlyHaven", "Matching");
        hub.setPreferenceCreators(Collections.singletonList(matching));
        Class<?> candidateClass = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> constructor = candidateClass.getDeclaredConstructor(
                NativeContentItem.class, String.class, String.class);
        constructor.setAccessible(true);
        java.lang.reflect.Method allowed = OnlyFapHubView.class.getDeclaredMethod("startupAllowed", candidateClass);
        allowed.setAccessible(true);
        assertEquals(true, allowed.invoke(hub, constructor.newInstance(matching, "", "")));
        assertEquals(false, allowed.invoke(hub, constructor.newInstance(clip("unrelated"), "", "")));
        hub.close();
    }

    private android.view.View findText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void categoryMappingUsesVerifiedProviderTokensAndConfiguredRoute() throws Exception {
        JSONObject document = new JSONObject(new String(Files.readAllBytes(Paths.get("src/main/assets/source_config_defaults.json")), java.nio.charset.StandardCharsets.UTF_8));
        String[] tokens = {"woman", "man", "trans_woman", "trans_man", "group"};
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

    @Test public void latePreferenceAdmissionPreservesNormalTailCurrentNextAndExclusions() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.set(activity, ContentPreference.MEN);
        ChaosFeedView feed = new ChaosFeedView(activity, item -> {}, false);
        ArrayList<NativeContentItem> items = org.robolectric.util.ReflectionHelpers.getField(feed, "items");
        NativeContentItem current = clip("current"), next = clip("next"),
                kaotic = clip("kaotic"), fapello = clip("fapello"), efukt = clip("efukt");
        items.addAll(Arrays.asList(current, next, kaotic, fapello, efukt));
        NativeContentItem match = havenClip("match"), blocked = havenClip("blocked"),
                watched = havenClip("watched"), liked = havenClip("liked");
        Set<String> blockedKeys = org.robolectric.util.ReflectionHelpers.getField(feed, "blockedCreatorKeys");
        blockedKeys.add(ShitTokBlockedCreatorStore.keyFor(blocked));
        ShitTokSeenStore seen = org.robolectric.util.ReflectionHelpers.getField(feed, "seenStore");
        seen.watch(watched.url, null); seen.heart(liked.url, null);
        ChaosSourceMixer mixer = org.robolectric.util.ReflectionHelpers.getField(feed, "sourceMixer");
        Set<String> matches = org.robolectric.util.ReflectionHelpers.getField(mixer, "preferenceMatches");
        for (NativeContentItem item : Arrays.asList(match, blocked, watched, liked))
            matches.add(ContentPreference.MEN.value + "|" + item.url);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(feed, "appendUnique",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(List.class,
                        Arrays.asList(blocked, watched, liked, match, match)));
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(feed, "prioritizePreferenceQueue");
        assertEquals(Arrays.asList(current, next, match, kaotic, fapello, efukt), items);
        assertEquals(1, (int) Collections.frequency(items, match));
        assertTrue(seen.seen(watched.url, null)); assertTrue(seen.liked(liked.url, null));
        feed.close();
    }

    @Test public void onlyHavenCatalogLockCannotBlockOrdinaryProviderPagination() throws Exception {
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(1));
        org.robolectric.util.ReflectionHelpers.setField(mixer, "onlyHavenCatalogAttempted", true);
        Object lock = org.robolectric.util.ReflectionHelpers.getField(mixer, "onlyHavenLock");
        java.lang.reflect.Method load = ChaosSourceMixer.class.getDeclaredMethod(
                "loadOnlyHavenBatch", Context.class, ContentPreference.class);
        load.setAccessible(true);
        java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Future<?> haven;
        try {
            synchronized (lock) {
                haven = io.submit(() -> {
                    started.countDown();
                    try { return load.invoke(mixer, context, ContentPreference.ALL); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                });
                assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
                try {
                    haven.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                    fail("The OnlyHaven loader must use its dedicated catalog lock");
                } catch (java.util.concurrent.TimeoutException expected) { }
                int page = io.submit(() -> mixer.nextPage("kaotic", 32))
                        .get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(page >= 1 && page <= 32);
            }
            haven.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally { io.shutdownNow(); }
    }

    private NativeContentItem havenClip(String name) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name,
                "https://e1.cum.st/media/" + name + ".mp4", "", "",
                "https://cum.st/creators/onlyfans/" + name, "", "onlyfans · OnlyHaven", name, 0L, .6f);
    }

    private NativeContentItem clip(String name) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name, "https://test/" + name,
                "", "", name, "", "", "");
    }
}
