package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class CreatorGalleryIdentityIntegrationTest {
    @Test public void profileAndEvidenceChangeSessionWithoutChangingFavoriteReference() throws Exception {
        NativeContentItem legacy = creator("https://fapello.com/first/");
        CreatorGallerySpec first = CreatorGallerySpec.from(legacy);
        CreatorGallerySpec second = CreatorGallerySpec.from(creator("https://fapello.com/other/"));
        NativeContentItem verified = legacy.withCreatorIdentity(
                Collections.singletonList("rynkerbelle"), Collections.singletonList(legacy.url));
        CreatorGallerySpec withEvidence = CreatorGallerySpec.from(ContentItemCodec.decode(
                ContentItemCodec.encode(verified)));

        assertEquals(first.cacheKey, second.cacheKey);
        assertEquals(first.cacheKey, withEvidence.cacheKey);
        assertNotEquals(first.sessionKey, second.sessionKey);
        assertNotEquals(first.sessionKey, withEvidence.sessionKey);
        assertEquals(Collections.singletonList("rynkerbelle"), withEvidence.aliases);
    }

    @Test public void groupedFullscreenUsesOriginalQueryAndRestoresTheSameSession() throws Exception {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity host = controller.get();
            String query = "Fixture Creator";
            String favoriteKey = "merged:v2:fixture";
            String cacheKey = CreatorGallerySpec.sessionKey(query, "", favoriteKey, null, null, null);
            String session = BunkrGallerySessionStore.createCreator(query, BunkrRepository.searchUrl(query), cacheKey);
            NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Fixture image",
                    "file:///android_asset/fixture.png", "", "", "", "");
            BunkrGallerySessionStore.recordCreatorBatch(host, session, Collections.singletonList(image),
                    true, new JSONObject().put("query", query).put("version", 3));
            Intent intent = new Intent(host, BunkrGalleryActivity.class)
                    .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                    .putExtra(BunkrGalleryActivity.EXTRA_CREATOR_QUERY, query)
                    .putExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_GALLERY_CACHE_KEY, favoriteKey);

            try (ActivityController<BunkrGalleryActivity> viewer =
                         Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create()) {
                assertEquals(query, ReflectionHelpers.getField(viewer.get(), "creatorQuery"));
                assertEquals(session, ReflectionHelpers.getField(viewer.get(), "sessionId"));
            }
        }
    }

    @Test public void unrelatedFastResultsCannotEnterGalleryAndMultipartAlbumCan() {
        List<String> verified = Collections.singletonList("rynkerbelle");
        assertFalse(BunkrCreatorGalleryRepository.confirmedAlbum(album("Ryn Rivers"), verified));
        assertFalse(BunkrCreatorGalleryRepository.confirmedAlbum(album("rynkerbelle and someone else - Part 1"), verified));
        assertFalse(BunkrCreatorGalleryRepository.confirmedAlbum(album("rynkerbelle"),
                Collections.singletonList("ryn! >_<")));
        assertTrue(BunkrCreatorGalleryRepository.confirmedAlbum(album("rynkerbelle - Part 2"), verified));
    }

    @Test public void duplicateAlbumPageAdvancesToLaterConfirmedMedia() {
        BunkrCreatorGalleryRepository gallery = new BunkrCreatorGalleryRepository();
        Object state = inner("State", ReflectionHelpers.ClassParameter.from(String.class, "Fixture Creator"));
        Object cursor = inner("AlbumCursor",
                ReflectionHelpers.ClassParameter.from(String.class, "https://bunkr.cr/a/fixture"));
        NativeContentItem first = new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "first", "https://example.com/first.jpg", "", "", "", "");
        NativeContentItem later = new NativeContentItem(NativeContentItem.KIND_IMAGE,
                "later", "https://example.com/later.jpg", "", "", "", "");
        ArrayList<NativeContentItem> output = new ArrayList<>();
        Deque<?> pending = ReflectionHelpers.getField(state, "pending");

        applyAlbum(gallery, state, cursor, output, first);
        assertEquals(cursor, pending.removeFirst());
        applyAlbum(gallery, state, cursor, output, first);
        assertEquals(cursor, pending.removeFirst());
        assertEquals(3, (int) ReflectionHelpers.getField(cursor, "nextPage"));
        applyAlbum(gallery, state, cursor, output, later);
        assertEquals(java.util.Arrays.asList(first, later), output);
    }

    @Test @Config(shadows = FapelloRecovery.class)
    public void selectedProfile404RecoversMediaWithoutFuzzyMatching() {
        FapelloRecovery.lookups = 0;
        BunkrCreatorGalleryRepository gallery = new BunkrCreatorGalleryRepository();
        FapelloRepository.Model stale = new FapelloRepository.Model(
                "Haesicks", "https://fapello.com/haesicks-2/", "");
        Object selected = inner("FapelloCursor",
                ReflectionHelpers.ClassParameter.from(FapelloRepository.Model.class, stale),
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
        Object page = fetchFapello(gallery, selected);
        FapelloRepository.MediaPage media = ReflectionHelpers.getField(page, "media");
        assertEquals(1, media.items.size());
        assertEquals("https://fapello.com/haesicks/1/", media.items.get(0).url);
        assertEquals(1, FapelloRecovery.lookups);

        ReflectionHelpers.setField(selected, "nextPage", 2);
        fetchFapello(gallery, selected);
        assertEquals(1, FapelloRecovery.lookups);
    }

    @Test @Config(shadows = ShelfAlbums.class)
    public void mainShelfCarriesConfirmedAlbumAlongsideSelectedProfile() {
        FapelloRepository.Model model = new FapelloRepository.Model(
                "Haesicks", "https://fapello.com/haesicks-2/", "https://example.com/preview.jpg");
        Object result = ReflectionHelpers.callInstanceMethod(new FapzoneCreatorRepository(), "resolve",
                ReflectionHelpers.ClassParameter.from(Context.class, null),
                ReflectionHelpers.ClassParameter.from(int.class, 0),
                ReflectionHelpers.ClassParameter.from(FapelloRepository.Model.class, model));
        NativeContentItem card = ReflectionHelpers.getField(result, "item");
        CreatorGallerySpec spec = CreatorGallerySpec.from(card);
        assertEquals(model.url, spec.profileHint);
        assertEquals("haesicks", spec.aliases.get(0));
        assertTrue(spec.seedUrls.contains("https://bunkr.cr/a/fixture"));
        assertFalse(spec.seedUrls.contains("https://bunkr.cr/a/unrelated"));

        NativeContentItem cached = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                model.name, model.url, "", "", "", "", "", model.name)
                .withCreatorIdentity(Collections.singletonList("haesicks-2"),
                        Collections.singletonList(model.url));
        CreatorGallerySpec restored = CreatorGallerySpec.from(FapzoneCreatorRepository.withListedHandle(cached));
        assertEquals("haesicks", restored.aliases.get(0));
        assertEquals(model.url, restored.profileHint);
    }

    @Implements(value = FapelloRepository.class, isInAndroidSdk = false)
    public static class FapelloRecovery {
        static int lookups;

        @Implementation protected FapelloRepository.MediaPage fetchModelMediaPage(
                Context context, FapelloRepository.Model model, int page) throws java.io.IOException {
            if (model.url.endsWith("-2/")) throw new FapelloSourceException(
                    FapelloSourceException.Reason.NOT_FOUND, 404, "Fixture profile gone");
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_IMAGE,
                    "Recovered media", model.url + "1/", "", "", "", "");
            return new FapelloRepository.MediaPage(Collections.singletonList(item), false, "");
        }

        @Implementation protected FapelloRepository.Model findReplacementProfile(
                Context context, FapelloRepository.Model stale) {
            lookups++;
            return new FapelloRepository.Model(stale.name, "https://fapello.com/haesicks/", "");
        }
    }

    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class ShelfAlbums {
        @Implementation protected List<NativeContentItem> searchAlbums(Context context, String query, int page) {
            assertEquals("haesicks", query);
            return List.of(new NativeContentItem(NativeContentItem.KIND_SERIES,
                    "Haesicks and Other Creator", "https://bunkr.cr/a/unrelated", "", "", "", ""),
                    album("Haesicks Part 2"));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object fetchFapello(BunkrCreatorGalleryRepository gallery, Object cursor) {
        return ReflectionHelpers.callInstanceMethod(gallery, "fetchFapelloPage",
                ReflectionHelpers.ClassParameter.from(Context.class, null),
                ReflectionHelpers.ClassParameter.from((Class) cursor.getClass(), cursor));
    }

    private static Object inner(String name, ReflectionHelpers.ClassParameter<?>... parameters) {
        Class<?> type = ReflectionHelpers.loadClass(BunkrCreatorGalleryRepository.class.getClassLoader(),
                BunkrCreatorGalleryRepository.class.getName() + "$" + name);
        return ReflectionHelpers.callConstructor(type, parameters);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyAlbum(BunkrCreatorGalleryRepository gallery, Object state, Object cursor,
                                   ArrayList<NativeContentItem> output, NativeContentItem item) {
        Object page = inner("AlbumPage", ReflectionHelpers.ClassParameter.from((Class) cursor.getClass(), cursor),
                ReflectionHelpers.ClassParameter.from(List.class, Collections.singletonList(item)),
                ReflectionHelpers.ClassParameter.from(Exception.class, null));
        ReflectionHelpers.callInstanceMethod(gallery, "applyPage",
                ReflectionHelpers.ClassParameter.from((Class) state.getClass(), state),
                ReflectionHelpers.ClassParameter.from(ArrayList.class, output),
                ReflectionHelpers.ClassParameter.from((Class) page.getClass(), page));
    }

    private static NativeContentItem creator(String profile) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, "ryn! >_<", profile, "", "", "",
                "", "", "ryn! >_<");
    }

    private static NativeContentItem album(String title) {
        return new NativeContentItem(NativeContentItem.KIND_SERIES, title,
                "https://bunkr.cr/a/fixture", "", "", "", "");
    }
}
