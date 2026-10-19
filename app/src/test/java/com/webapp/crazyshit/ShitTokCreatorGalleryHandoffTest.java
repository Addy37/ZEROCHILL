package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.widget.FrameLayout;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = ShitTokCreatorGalleryHandoffTest.Preloads.class)
public class ShitTokCreatorGalleryHandoffTest {
    private static final String PROFILE = "https://cum.st/creators/onlyfans/carrot-id";
    private final Context context = RuntimeEnvironment.getApplication();

    @Implements(value = CreatorGalleryPreloader.class, isInAndroidSdk = false)
    public static class Preloads {
        static String name, query, profile;
        @Implementation protected static void warm(Context context, String name, String query,
                String profile, int priority) {
            Preloads.name = name;
            Preloads.query = query;
            Preloads.profile = profile;
        }
    }

    @Before public void clear() {
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        Preloads.name = Preloads.query = Preloads.profile = null;
    }

    private NativeContentItem clip(String profile) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "CarrotSoki",
                "https://e1.cum.st/media/clip/original.mp4", "https://img.cum.st/clip.webp",
                "", profile, "", "onlyfans · OnlyHaven", "", 123L, .5f);
    }

    @Test public void harvestPreservesProfilePlaybackAndOrientationMetadata() {
        NativeContentItem original = clip(PROFILE);
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator(
                "onlyfans", "carrot-id", "CarrotSoki", PROFILE, "");
        // Even legacy source media without an uploader can use the harvested creator.
        NativeContentItem harvested = ChaosSourceMixer.onlyHavenClip(clip(""), creator);
        assertEquals(PROFILE, harvested.uploader);
        assertEquals(original.url, harvested.url);
        assertEquals(original.title, harvested.title);
        assertEquals(original.imageUrl, harvested.imageUrl);
        assertEquals(original.publishedAtMillis, harvested.publishedAtMillis);
        assertEquals(original.aspectRatioHint, harvested.aspectRatioHint, 0f);
        assertEquals(ShitTokAspectPriority.sourceKey(clip("OnlyHaven")),
                ShitTokAspectPriority.sourceKey(harvested));
        assertEquals(PROFILE, ShitTokCreatorMetadata.creatorIdentity(null, harvested).url);
    }

    @Test public void exactProfileSurvivesCatalogEnrichmentAndKeepsFavoriteAndArtwork() {
        NativeContentItem catalog = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "CarrotSoki", "https://fapello.com/carrotsoki/", "https://example.com/avatar.jpg",
                "", "", "", "", "CarrotSoki");
        CreatorCatalog.remember(context, Collections.singletonList(catalog));
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", Collections.singleton("carrotsoki")).commit();
        NativeContentItem immediate = ShitTokCreatorMetadata.creatorIdentity(null, clip(PROFILE));
        NativeContentItem enriched = ShitTokCreatorMetadata.creatorIdentity(context, clip(PROFILE));
        assertEquals(PROFILE, immediate.url);
        assertEquals(PROFILE, enriched.url);
        assertEquals(catalog.imageUrl, enriched.imageUrl);
        assertEquals(catalog.title, enriched.title);
        assertTrue(CreatorFavoriteStore.contains(context, immediate));
        assertTrue(CreatorFavoriteStore.contains(context, enriched));
    }

    @Test public void preloadPassesExactProfileWithoutCatalogLookup() {
        ShitTokCreatorGalleryPreloader.warm(context, clip(PROFILE));
        assertEquals("CarrotSoki", Preloads.name);
        assertEquals("CarrotSoki", Preloads.query);
        assertEquals(PROFILE, Preloads.profile);
    }

    @Test public void nameOnlyPreloadRetainsEmptyProfileFallback() {
        ShitTokCreatorGalleryPreloader.warm(context, clip("OnlyHaven"));
        assertEquals("CarrotSoki", Preloads.query);
        assertEquals("", Preloads.profile);
        assertEquals("", ShitTokCreatorMetadata.creatorIdentity(null, clip("OnlyHaven")).url);
    }

    @Test public void holderNavigationPassesKnownProfile() {
        assertNavigation(clip(PROFILE), PROFILE);
    }

    @Test public void holderNavigationKeepsNameOnlyFallback() {
        assertNavigation(clip("OnlyHaven"), "");
    }

    private void assertNavigation(NativeContentItem media, String expectedProfile) {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(host.get(), item -> {}, false);
            ShitTokCreatorMetadataLoader original = ReflectionHelpers.getField(feed, "creatorMetadata");
            original.close();
            ArrayDeque<Runnable> worker = new ArrayDeque<>();
            ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(
                    worker::add, Runnable::run, item -> { fail("Lookup must stay off the tap path");
                        return null; }, url -> {});
            ReflectionHelpers.setField(feed, "creatorMetadata", loader);
            try {
                Object adapter = ReflectionHelpers.getField(feed, "adapter");
                Object holder = ReflectionHelpers.callInstanceMethod(adapter, "onCreateViewHolder",
                        from(android.view.ViewGroup.class, new FrameLayout(host.get())), from(int.class, 0));
                ReflectionHelpers.setField(holder, "item", media);
                ReflectionHelpers.callInstanceMethod(holder, "openCreatorGallery",
                        from(boolean.class, false), from(String.class, "test-return-token"));
                Intent intent = shadowOf(host.get()).getNextStartedActivity();
                assertNotNull(intent);
                assertEquals("CarrotSoki", intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
                String profile = intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_FAPELLO_PROFILE_URL);
                assertEquals(expectedProfile, profile == null ? "" : profile);
                assertEquals(1, worker.size()); // Deferred metadata enrichment, never synchronous.
            } finally { feed.close(); }
        }
    }

    @Test public void knownProfileSeedsOnlyHavenWithoutCreatorRediscovery() {
        BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
        String session = "shittok-known-haven";
        repository.reset(session, "CarrotSoki", PROFILE, "CarrotSoki");
        Map<?, ?> states = ReflectionHelpers.getStaticField(BunkrCreatorGalleryRepository.class, "STATES");
        Object state = states.get(session);
        assertTrue((boolean) ReflectionHelpers.getField(state, "onlyHavenCatalogLoaded"));
        ArrayDeque<?> pending = ReflectionHelpers.getField(state, "onlyHavenPending");
        assertEquals(1, pending.size());
        OnlyHavenRepository.Creator creator = ReflectionHelpers.getField(pending.peek(), "creator");
        assertEquals(PROFILE, creator.url);
        assertEquals("carrot-id", creator.id);
        // This method must return before it can search, even without a usable Context.
        ReflectionHelpers.callInstanceMethod(repository, "loadOnlyHavenCatalog",
                from(Context.class, null), from((Class) state.getClass(), state));
        assertEquals(1, pending.size());
    }

    @Test public void fapelloWeeklyClipRetainsExactCarrotSokiProfileThroughNavigationAndPreload() {
        String clipUrl = "https://fapello.com/video/week/31142429/";
        String profile = "https://fapello.com/carrotsoki-2/";
        String html = "<div class='card'><a href='" + profile
                + "' title='CarrotSoki'>CarrotSoki</a><a href='" + clipUrl
                + "'><img src='/thumbnail.jpg'></a></div>";
        NativeContentItem media = new FapelloRepository().parsePopularVideos(
                org.jsoup.Jsoup.parse(html, FapelloRepository.BASE), FapelloRepository.BASE).get(0);
        assertEquals(clipUrl, media.url);
        assertEquals("CarrotSoki", media.uploader);
        assertEquals(profile, media.searchQuery);
        NativeContentItem immediate = ShitTokCreatorMetadata.creatorIdentity(null, media);
        assertEquals(profile, immediate.url);
        assertEquals("CarrotSoki", immediate.searchQuery);
        ShitTokCreatorGalleryPreloader.warm(context, media);
        assertEquals(profile, Preloads.profile);
        assertEquals("CarrotSoki", Preloads.query);
        assertNavigation(media, profile);

        // A similarly named catalog entry must not redirect the known profile.
        CreatorCatalog.remember(context, Collections.singletonList(new NativeContentItem(
                NativeContentItem.KIND_CREATOR, "CarrotSoki", "https://fapello.com/carrotsoki/",
                "https://example.com/avatar.jpg", "", "", "", "", "CarrotSoki")));
        assertEquals(profile, ShitTokCreatorMetadata.creatorIdentity(context, media).url);
    }

    @Test public void fapelloProfileAndCatalogFallbackStillWork() {
        NativeContentItem media = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Clip", "https://fapello.com/carrotsoki/123/", "", "", "CarrotSoki", "", "OnlyFap");
        NativeContentItem catalog = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "CarrotSoki", "https://fapello.com/carrotsoki/", "https://example.com/avatar.jpg",
                "", "", "", "", "CarrotSoki");
        CreatorCatalog.remember(context, Collections.singletonList(catalog));
        NativeContentItem identity = ShitTokCreatorMetadata.creatorIdentity(context, media);
        assertEquals(catalog.url, identity.url);
        assertEquals(catalog.imageUrl, identity.imageUrl);
        assertEquals("CarrotSoki", identity.searchQuery);
    }
}
