package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class FavoriteImageNavigationTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private static final String PROFILE = "https://fapello.com/fixture-creator/";
    private static final String NAME = "Fixture Creator";

    @Before public void reset() {
        for (String prefs : new String[]{"watch_later", "creator_favorites", CreatorCatalog.PREFS,
                "manual_creator_merges_v1", "creator_avatar_overrides_v1"})
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        ManualCreatorMergeStore.clearCacheForTest();
        CreatorAvatarOverrideStore.clearCacheForTest();
    }

    @Test public void favoriteViewerMenuOpensTheNativeCreatorGalleryWithoutChangingMembership() {
        FavoriteStore.Item saved = save(photo("fixture-image", NAME + " #123", PROFILE));
        try (ActivityController<BunkrGalleryActivity> controller = viewer(saved)) {
            BunkrGalleryActivity activity = controller.get();
            PopupWindow popup = menu(activity);
            assertTrue(GlassMenuTestSupport.hasAction(popup, "Remove from favorites"));
            assertFalse(GlassMenuTestSupport.hasAction(popup, "Open item page"));
            assertFalse(GlassMenuTestSupport.hasAction(popup, "Open album page"));
            assertTrue(GlassMenuTestSupport.click(popup, "Open gallery"));
            assertGallery(shadowOf(activity).getNextStartedActivity(), NAME, PROFILE);
            assertFalse(activity.isFinishing());
            assertEquals(saved.url, FavoriteStore.mediaFavorites(context).get(0).url);
        }
    }

    @Test public void bothCreatorLabelsOpenTheSameGalleryAndSurviveRecreation() {
        FavoriteStore.Item saved = save(photo("fixture-image", NAME + " #123", PROFILE));
        try (ActivityController<BunkrGalleryActivity> controller = viewer(saved)) {
            controller.recreate();
            BunkrGalleryActivity activity = controller.get();
            for (String field : new String[]{"creatorTitleView", "itemTitleView"}) {
                TextView label = ReflectionHelpers.getField(activity, field);
                assertEquals(NAME, label.getText().toString());
                assertTrue(label.performClick());
                assertGallery(shadowOf(activity).getNextStartedActivity(), NAME, PROFILE);
            }
        }
    }

    @Test public void removalPreservesWatchLaterAndOtherImageFavorites() {
        NativeContentItem image = photo("fixture-image", NAME + " #123", PROFILE);
        FavoriteStore.add(context, image);
        FavoriteStore.Item saved = save(image);
        FavoriteStore.setMediaFavorite(context, photo("another-image", NAME + " #456", PROFILE), true);
        try (ActivityController<BunkrGalleryActivity> controller = viewer(saved)) {
            BunkrGalleryActivity activity = controller.get();
            assertTrue(GlassMenuTestSupport.click(menu(activity), "Remove from favorites"));
            assertTrue(activity.isFinishing());
            assertTrue(FavoriteStore.contains(context, image.url));
            assertEquals(1, FavoriteStore.mediaFavorites(context).size());
            assertEquals("another-image", FavoriteStore.mediaFavorites(context).get(0).url);
            assertEquals(image.url, FavoriteStore.watchLater(context).get(0).url);
        }
    }

    @Test public void onlyHavenCreatorProfileIsNotReplacedByItsMediaUrl() {
        String profile = "https://cum.st/creators/onlyfans/fixture-creator/";
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, NAME,
                "fixture-haven-image", "", "", profile, "", "OnlyHaven");
        FavoriteStore.Item saved = save(image);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertGallery(SavedGalleryMedia.creatorGallery(activity, saved), NAME, profile);
    }

    @Test public void mergedSavedGalleryKeepsItsCacheKeyAndBothCreatorSeeds() {
        NativeContentItem alpha = creator("Testcreator000"), beta = creator("Testcreator001");
        CreatorCatalog.remember(context, Arrays.asList(alpha, beta));
        context.getSharedPreferences("creator_favorites", 0).edit().putStringSet("creators",
                new HashSet<>(Arrays.asList("testcreator000", "testcreator001"))).commit();
        assertTrue(ManualCreatorMergeStore.merge(context, Collections.singleton("testcreator000"),
                Collections.singleton("testcreator001"), "testcreator000", "testcreator000"));
        CreatorGallerySpec spec = CreatorGallerySpec.from(CreatorCatalog.favoriteGroups(context).get(0));
        NativeContentItem image = photo("fixture-image", "Testcreator001 #1", beta.url);
        FavoriteStore.setMediaFavorite(context, image, true, spec.cacheKey);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent target = SavedGalleryMedia.creatorGallery(activity, FavoriteStore.mediaFavorites(context).get(0));
        assertNotNull(target);
        assertEquals(spec.cacheKey, target.getStringExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_GALLERY_CACHE_KEY));
        assertEquals(new HashSet<>(Arrays.asList("Testcreator000", "Testcreator001")), new HashSet<>(
                target.getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_SEED_NAMES)));
    }

    @Test public void unknownCreatorHasNoDeadPageFallback() {
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Unidentified photo",
                "unknown-image", "", "", "", "", "OnlyFap");
        FavoriteStore.Item saved = save(image);
        try (ActivityController<BunkrGalleryActivity> controller = viewer(saved)) {
            BunkrGalleryActivity activity = controller.get();
            PopupWindow popup = menu(activity);
            assertFalse(GlassMenuTestSupport.enabled(popup, "Open gallery"));
            assertFalse(GlassMenuTestSupport.hasAction(popup, "Open item page"));
            TextView label = ReflectionHelpers.getField(activity, "creatorTitleView");
            assertFalse(label.isClickable());
            assertNull(shadowOf(activity).getNextStartedActivity());
            popup.dismiss();
        }
    }

    @Test public void favoritesCardNameAndOverflowOpenGalleryWithoutUnfavoriting() {
        FavoriteStore.Item saved = save(photo("fixture-image", NAME + " #123", PROFILE));
        Intent intent = new Intent(context, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, FavoritesActivity.START_WATCH_LATER)
                .putExtra(FavoritesActivity.EXTRA_MEDIA_FAVORITES, true);
        try (ActivityController<FavoritesActivity> controller = Robolectric.buildActivity(FavoritesActivity.class, intent).setup()) {
            FavoritesActivity activity = controller.get();
            View card = ReflectionHelpers.callInstanceMethod(activity, "makeWatchLaterCard",
                    ReflectionHelpers.ClassParameter.from(FavoriteStore.Item.class, saved));
            activity.setContentView(card);
            View name = GlassMenuTestSupport.description(card, "Open gallery for " + NAME);
            assertNotNull(name);
            assertTrue(name.performClick());
            assertGallery(shadowOf(activity).getNextStartedActivity(), NAME, PROFILE);
            assertTrue(card.findViewWithTag("library-card-options").performClick());
            PopupWindow popup = shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
            GlassMenuTestSupport.assertGlass(popup);
            assertTrue(GlassMenuTestSupport.click(popup, "Open gallery"));
            assertGallery(shadowOf(activity).getNextStartedActivity(), NAME, PROFILE);
            assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        }
    }

    @Test public void storedCreatorQueryWorksWithoutAProfileAndMergedHashesAreNeverSearchTerms() {
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "legacy-image", "", "", "", "", "OnlyFap");
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FavoriteStore.Item legacy = new FavoriteStore.Item("Photo", image.url, 0, image, true, false, NAME);
        Intent gallery = SavedGalleryMedia.creatorGallery(activity, legacy);
        assertNotNull(gallery);
        assertEquals(NAME, gallery.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
        FavoriteStore.Item orphaned = new FavoriteStore.Item("Photo", image.url, 0, image,
                true, false, "merged:v2:unknown");
        assertNull(SavedGalleryMedia.creatorGallery(activity, orphaned));
    }

    @Test public void wikiFeetSavedProfileUsesTheExistingNativeGalleryRoute() {
        String profile = "https://www.wikifeet.com/Fixture_Creator";
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, NAME + " #123",
                "wikifeet-image", "", "", profile, "", "WikiFeet");
        FavoriteStore.Item saved = new FavoriteStore.Item(image.title, image.url, 0, image, true, false, "");
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent gallery = SavedGalleryMedia.creatorGallery(activity, saved);
        assertNotNull(gallery);
        assertEquals(NativeFeedBrowserActivity.class.getName(), gallery.getComponent().getClassName());
        assertEquals(NAME, gallery.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
    }

    private NativeContentItem photo(String url, String title, String profile) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, title, url, "", "", profile, "", "Fapello");
    }
    private NativeContentItem creator(String name) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://fapello.com/" + name.toLowerCase(java.util.Locale.US) + "/", "", "", "", "", "", name);
    }
    private FavoriteStore.Item save(NativeContentItem image) {
        FavoriteStore.setMediaFavorite(context, image, true);
        return FavoriteStore.mediaFavorites(context).get(0);
    }
    private ActivityController<BunkrGalleryActivity> viewer(FavoriteStore.Item saved) {
        try (ActivityController<Activity> source = Robolectric.buildActivity(Activity.class).setup()) {
            SavedGalleryMedia.open(source.get(), saved);
            Intent intent = shadowOf(source.get()).getNextStartedActivity();
            return Robolectric.buildActivity(BunkrGalleryActivity.class, intent).setup();
        }
    }
    private PopupWindow menu(BunkrGalleryActivity activity) {
        View more = GlassMenuTestSupport.description(activity.getWindow().getDecorView(), "More options");
        assertNotNull(more);
        assertTrue(more.performClick());
        PopupWindow popup = shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
        GlassMenuTestSupport.assertGlass(popup);
        return popup;
    }
    private void assertGallery(Intent intent, String name, String profile) {
        assertNotNull(intent);
        assertEquals(NativeFeedBrowserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(name, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
        assertEquals(profile, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_FAPELLO_PROFILE_URL));
    }
}
