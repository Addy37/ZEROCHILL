package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapGenderShelvesTest {
    private Activity activity;
    private OnlyFapHubView hub;
    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.prefs(activity).edit().clear().commit();
        hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            public void onOpenCreator(NativeContentItem item) { }
            public void onSearch() { }
            public void onViewAllFavorites() { }
        });
    }
    @After public void close() { hub.close(); }

    private void mode(ContentPreference mode) {
        ContentPreference.set(activity, mode);
        hub.preparePreference(mode);
        hub.beginRefresh();
    }
    private OnlyFapCategoryLoader.Listener publish(ContentPreference mode) {
        return new OnlyFapCategoryLoader.Listener() {
            public void shelf(int index, List<NativeContentItem> items, boolean failed) {
                hub.setPreferenceShelf(mode, index, items, failed);
            }
            public void finished(boolean failed) { hub.finishPreferenceShelves(mode, failed); }
        };
    }
    @Test public void everyFilteredModeUsesDistinctPagesAndPublishesThreeCategoryShelves() {
        for (ContentPreference preference : ContentPreference.values()) {
            if (preference == ContentPreference.ALL) continue;
            mode(preference);
            List<Integer> offsets = new ArrayList<>();
            OnlyFapCategoryLoader.load(preference, (selected, limit, offset) -> {
                assertEquals(preference, selected);
                assertEquals(16, limit);
                offsets.add(offset);
                return new OnlyFapCategoryLoader.Batch(creators(preference.value, offset, 16), true);
            }, () -> true, publish(preference));
            assertEquals(Arrays.asList(0, 16, 32), offsets);
            assertEquals(48, hub.itemCount());
            assertEquals(16, rail("trendingShelf").getAdapter().getItemCount());
            assertEquals(16, rail("newShelf").getAdapter().getItemCount());
            assertEquals(16, rail("hotShelf").getAdapter().getItemCount());
            assertEquals(0, rail("popularShelf").getAdapter().getItemCount());
            assertEquals("Featured " + preference.label, title("trendingShelf"));
            assertEquals("Explore " + preference.label, title("newShelf"));
            assertEquals("More " + preference.label, title("hotShelf"));
            for (NativeContentItem creator : candidates()) assertTrue(creator.url.contains(preference.value));
        }
    }
    @Test public void sameProviderIdentityAppearsInOneShelfEvenWhenItsDisplayNameChanges() {
        mode(ContentPreference.MEN);
        OnlyFapCategoryLoader.load(ContentPreference.MEN, (selected, limit, offset) -> {
            List<NativeContentItem> batch = creators("men", offset == 0 ? 0 : offset - 1, 16);
            if (offset == 16) batch.set(0, creator("Renamed", "https://cum.st/creators/onlyfans/men15?alias=1"));
            return new OnlyFapCategoryLoader.Batch(batch, true);
        }, () -> true, publish(ContentPreference.MEN));
        Set<String> identities = new HashSet<>();
        for (NativeContentItem item : candidates()) assertTrue(identities.add(item.url.split("[?#]", 2)[0]));
        assertEquals(47, candidates().size());
    }
    @Test public void sameModeRefreshCannotDuplicateCreatorsAcrossOldAndNewPageBoundaries() {
        mode(ContentPreference.MEN);
        hub.setPreferenceShelf(ContentPreference.MEN, 0, creators("men", 0, 16), false);
        hub.setPreferenceShelf(ContentPreference.MEN, 1, creators("men", 16, 16), false);
        hub.finishPreferenceShelves(ContentPreference.MEN, false);
        hub.beginRefresh();
        hub.setPreferenceShelf(ContentPreference.MEN, 0, creators("men", 8, 16), false);
        assertEquals(24, hub.itemCount());
        assertEquals(8, rail("newShelf").getAdapter().getItemCount());
        assertEquals(24, new HashSet<>(candidates()).size());
        hub.setPreferenceShelf(ContentPreference.MEN, 1, Collections.emptyList(), true);
        assertEquals(24, hub.itemCount());
    }

    @Test public void differentProviderIdsWithTheSameDisplayNameAreNotMerged() {
        mode(ContentPreference.MEN);
        OnlyFapCategoryLoader.load(ContentPreference.MEN, (selected, limit, offset) ->
                new OnlyFapCategoryLoader.Batch(Arrays.asList(
                        creator("Same name", "https://cum.st/creators/onlyfans/1"),
                        creator("Same name", "https://cum.st/creators/fansly/1")), false),
                () -> true, publish(ContentPreference.MEN));
        assertEquals(2, hub.itemCount());
    }
    @Test public void firstShelfAndHeroPoolPublishBeforeTheSecondRequestFinishes() {
        mode(ContentPreference.WOMEN);
        OnlyFapCategoryLoader.load(ContentPreference.WOMEN, (selected, limit, offset) -> {
            if (offset == 16) {
                assertEquals(16, hub.itemCount());
                assertEquals(16, candidates().size());
                assertFalse(ReflectionHelpers.getField(hub, "shelvesFinished"));
                assertEquals(View.VISIBLE, ((View) rail("trendingShelf").getParent()).getVisibility());
            }
            return new OnlyFapCategoryLoader.Batch(creators("women", offset, 16), true);
        }, () -> true, publish(ContentPreference.WOMEN));
    }
    @Test public void onePageFailureDoesNotEraseAnotherShelfAndLaterPagesStillPublish() {
        mode(ContentPreference.MEN);
        OnlyFapCategoryLoader.load(ContentPreference.MEN, (selected, limit, offset) -> {
            if (offset == 16) throw new java.io.IOException("temporary");
            return new OnlyFapCategoryLoader.Batch(creators("men", offset, 16), true);
        }, () -> true, publish(ContentPreference.MEN));
        assertEquals(32, hub.itemCount());
        assertEquals(16, rail("trendingShelf").getAdapter().getItemCount());
        assertEquals(0, rail("newShelf").getAdapter().getItemCount());
        assertEquals(16, rail("hotShelf").getAdapter().getItemCount());
        TextView message = ReflectionHelpers.getField(hub, "loadingLabel");
        assertEquals(View.VISIBLE, message.getVisibility());
        assertTrue(message.getText().toString().contains("Tap to retry"));
    }
    @Test public void providerFailureCanBeRetriedWithoutChangingThePreferenceOrFavorites() {
        mode(ContentPreference.GROUPS);
        int[] retries = {0};
        hub.setPreferenceChanged(() -> retries[0]++);
        OnlyFapCategoryLoader.load(ContentPreference.GROUPS, (selected, limit, offset) -> {
            throw new java.io.IOException("unavailable");
        }, () -> true, publish(ContentPreference.GROUPS));
        TextView message = ReflectionHelpers.getField(hub, "loadingLabel");
        message.performClick();
        assertEquals(1, retries[0]);
        hub.clear(); hub.beginRefresh();
        OnlyFapCategoryLoader.load(ContentPreference.GROUPS, (selected, limit, offset) ->
                new OnlyFapCategoryLoader.Batch(creators("groups", 0, 3), false),
                () -> true, publish(ContentPreference.GROUPS));
        assertEquals(3, hub.itemCount());
        assertEquals(View.GONE, message.getVisibility());
        assertEquals(ContentPreference.GROUPS, ContentPreference.get(activity));
    }
    @Test public void sparseAndEmptyCategoriesStopAtTheProviderEndWithoutUnfilteredFallback() {
        for (int count : new int[]{0, 1, 7}) {
            mode(ContentPreference.GROUPS); hub.clear();
            List<Integer> requests = new ArrayList<>();
            OnlyFapCategoryLoader.load(ContentPreference.GROUPS, (selected, limit, offset) -> {
                requests.add(offset);
                return new OnlyFapCategoryLoader.Batch(creators("groups", 0, count), false);
            }, () -> true, publish(ContentPreference.GROUPS));
            assertEquals(Collections.singletonList(0), requests);
            assertEquals(count, hub.itemCount());
            assertEquals(0, rail("newShelf").getAdapter().getItemCount());
        }
    }
    @Test public void cancelledInFlightRequestCannotPublishOrStartAnotherPage() {
        AtomicBoolean current = new AtomicBoolean(true);
        int[] requests = {0}, callbacks = {0};
        OnlyFapCategoryLoader.load(ContentPreference.MEN, (selected, limit, offset) -> {
            requests[0]++; current.set(false); // Simulates an uncancellable request returning after invalidation.
            return new OnlyFapCategoryLoader.Batch(creators("men", 0, 16), true);
        }, current::get, new OnlyFapCategoryLoader.Listener() {
            public void shelf(int i, List<NativeContentItem> items, boolean failed) { callbacks[0]++; }
            public void finished(boolean failed) { callbacks[0]++; }
        });
        assertEquals(1, requests[0]); assertEquals(0, callbacks[0]);
    }
    @Test public void rapidSwitchesRejectQueuedShelvesAndCompletionFromEachOldMode() {
        for (ContentPreference selected : new ContentPreference[]{ContentPreference.WOMEN, ContentPreference.MEN,
                ContentPreference.TRANS_WOMEN, ContentPreference.ALL, ContentPreference.WOMEN}) {
            mode(selected);
            for (ContentPreference old : ContentPreference.values()) if (old != selected) {
                hub.setPreferenceShelf(old, 1, creators(old.value, 0, 16), false);
                hub.finishPreferenceShelves(old, false);
            }
            assertEquals(0, hub.itemCount());
            assertFalse(ReflectionHelpers.getField(hub, "shelvesFinished"));
        }
    }
    @Test public void currentPreferenceGuardRejectsAnOldViewBeforeActivationRefresh() {
        mode(ContentPreference.MEN);
        ContentPreference.set(activity, ContentPreference.WOMEN);
        hub.setPreferenceShelf(ContentPreference.MEN, 0, creators("men", 0, 16), false);
        hub.finishPreferenceShelves(ContentPreference.MEN, false);
        assertEquals(0, hub.itemCount());
    }
    @Test public void allContentRetainsItsFapelloShelfSetAndIgnoresCategoryCallbacks() {
        mode(ContentPreference.MEN); mode(ContentPreference.ALL);
        hub.setPreferenceShelf(ContentPreference.MEN, 0, creators("men", 0, 16), false);
        assertEquals("Discover", title("trendingShelf"));
        assertEquals("New Creators", title("newShelf"));
        assertEquals("Hot", title("hotShelf"));
        assertEquals("Popular", title("popularShelf"));
        hub.setNewCreators(creators("fapello", 0, 4));
        hub.setHot(creators("fapello", 4, 4));
        hub.setPopular(creators("fapello", 8, 4));
        hub.finishLoading();
        assertEquals(4, rail("newShelf").getAdapter().getItemCount());
        assertEquals(4, rail("hotShelf").getAdapter().getItemCount());
        assertEquals(4, rail("popularShelf").getAdapter().getItemCount());
    }
    @Test public void favoritesRemainVisibleInTheCatalogSnapshotAcrossEveryPreference() {
        NativeContentItem saved = creator("Saved unrelated creator", "https://fapello.com/saved/");
        activity.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", Collections.singleton(CreatorFavoriteStore.key(saved))).commit();
        CreatorCatalog.remember(activity, Collections.singletonList(saved));
        for (ContentPreference selected : ContentPreference.values()) {
            mode(selected);
            assertTrue(CreatorFavoriteStore.contains(activity, saved));
            assertTrue(CreatorCatalog.favoriteGroups(activity).stream().anyMatch(group -> group.item.url.equals(saved.url)));
        }
    }
    @Test public void restoringOrDeferringAnotherPreferencesPositionsCannotMoveTheCurrentPage() {
        mode(ContentPreference.MEN);
        Bundle men = new Bundle(); hub.saveState(men); men.putInt("scroll_y", 500);
        men.putInt("new_position", 12);
        hub.restoreState(men);
        ReflectionHelpers.callInstanceMethod(hub, "applyPendingRestoreState");
        mode(ContentPreference.WOMEN);
        hub.restoreState(men);
        ShadowLooper.idleMainLooper();
        assertNull(ReflectionHelpers.getField(hub, "pendingRestoreState"));
        assertEquals(0, ((android.widget.ScrollView) ReflectionHelpers.getField(hub, "scroll")).getScrollY());
        assertEquals(0, hub.itemCount());
    }
    @Test public void categoryParsingCountsRawPageBeforeRemovingEmptyInvalidAndContradictoryRows() throws Exception {
        SourceConfig.OnlyHaven config = SourceConfig.parseAndValidate(new String(Files.readAllBytes(
                Paths.get("src/main/assets/source_config_defaults.json")))).onlyHaven;
        String json = "[" + row("valid", "man", 5) + "," + row("empty", "man", 0) + ","
                + row("wrong", "woman", 5) + "," + row("invalid:id", "man", 5) + "]";
        OnlyHavenRepository.CategoryPage page = new OnlyHavenRepository().parseCategoryPage(json, config, ContentPreference.MEN, 4);
        assertTrue(page.hasMore);
        assertEquals(1, page.creators.size());
        assertEquals("valid", page.creators.get(0).id);
        assertFalse(new OnlyHavenRepository().parseCategoryPage("[]", config, ContentPreference.MEN, 16).hasMore);
        assertTrue(OnlyHavenRepository.categoryRoute(config, ContentPreference.MEN, 16, 32).contains("o=32"));
    }
    private static String row(String id, String gender, int posts) {
        return "{\"id\":\"" + id + "\",\"service\":\"onlyfans\",\"name\":\"Misleading name\",\"gender\":\""
                + gender + "\",\"postCount\":" + posts + ",\"dmCount\":0}";
    }
    private List<NativeContentItem> candidates() { return ReflectionHelpers.getField(hub, "discoverCandidates"); }
    private RecyclerView rail(String field) { return ReflectionHelpers.getField(ReflectionHelpers.getField(hub, field), "rail"); }
    private String title(String field) { return ((TextView) ((android.view.ViewGroup) rail(field).getParent()).getChildAt(0)).getText().toString(); }
    static List<NativeContentItem> creators(String mode, int offset, int count) {
        List<NativeContentItem> result = new ArrayList<>();
        for (int i = offset; i < offset + count; i++) result.add(creator("Creator " + i, "https://cum.st/creators/onlyfans/" + mode + i));
        return result;
    }
    static NativeContentItem creator(String name, String address) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, address, "", "", "", "", "OnlyHaven", name);
    }
}
