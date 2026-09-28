package com.webapp.crazyshit;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.*;

public class CreatorMergedGalleryTest {

    private NativeContentItem creator(String name, String url, String image) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                url,
                image,
                "",
                url,
                "",
                "",
                name
        );
    }

    @After
    public void clearRepositoryState() throws Exception {
        Field statesField = BunkrCreatorGalleryRepository.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        ((Map<?, ?>) statesField.get(null)).clear();
    }

    @Test
    public void groupedFavoriteCarriesEveryKnownProfileIntoSeparateGalleryCache() {
        NativeContentItem primary = creator(
                "Sasha Fox",
                "https://fapello.com/sasha-fox/",
                "https://cdn.example.com/a.jpg"
        );
        NativeContentItem alias = creator(
                "Sasha Foxx",
                "https://cum.st/creators/onlyfans/example",
                "https://img.cum.st/thumbnail/abc/preview.webp"
        );
        LinkedHashMap<String, NativeContentItem> members = new LinkedHashMap<>();
        members.put("sasha fox", primary);
        members.put("sasha foxx", alias);
        LinkedHashSet<String> keys = new LinkedHashSet<>(members.keySet());

        CreatorCatalog.FavoriteGroup group =
                new CreatorCatalog.FavoriteGroup(primary, members, keys, true);
        CreatorGallerySpec spec = CreatorGallerySpec.from(group);

        assertTrue(spec.grouped);
        assertEquals("Sasha Fox", spec.query);
        assertEquals(2, spec.seedUrls.size());
        assertTrue(spec.seedUrls.contains("https://fapello.com/sasha-fox/"));
        assertTrue(spec.seedUrls.contains("https://cum.st/creators/onlyfans/example"));
        assertTrue(spec.cacheKey.startsWith("merged:"));
        assertNotEquals(spec.query, spec.cacheKey);
    }

    @Test
    public void groupedCacheKeyIsStableAcrossMemberIterationOrder() {
        NativeContentItem first = creator("Anna", "https://fapello.com/anna/", "");
        NativeContentItem second = creator("Ana", "https://fapello.com/ana/", "");

        LinkedHashMap<String, NativeContentItem> one = new LinkedHashMap<>();
        one.put("anna", first);
        one.put("ana", second);
        LinkedHashMap<String, NativeContentItem> two = new LinkedHashMap<>();
        two.put("ana", second);
        two.put("anna", first);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("anna");
        keys.add("ana");

        CreatorGallerySpec a = CreatorGallerySpec.from(
                new CreatorCatalog.FavoriteGroup(first, one, keys, true));
        CreatorGallerySpec b = CreatorGallerySpec.from(
                new CreatorCatalog.FavoriteGroup(first, two, keys, true));

        assertEquals(a.cacheKey, b.cacheKey);
    }

    @Test
    public void repositorySeedsMultipleMergedProfilesWithoutExtraCatalogSearches() throws Exception {
        BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
        ArrayList<String> names = new ArrayList<>();
        names.add("Sasha Fox");
        names.add("Sasha Foxx");
        names.add("Sasha Fox OnlyHaven");
        ArrayList<String> urls = new ArrayList<>();
        urls.add("https://fapello.com/sasha-fox/");
        urls.add("https://fapello.com/sasha-foxx/");
        urls.add("https://cum.st/creators/onlyfans/example");
        ArrayList<String> images = new ArrayList<>();
        images.add("");
        images.add("");
        images.add("https://img.cum.st/thumbnail/abc/preview.webp");

        repository.reset(
                "merged-session",
                "Sasha Fox",
                "",
                "Sasha Fox",
                names,
                urls,
                images
        );

        Field statesField = BunkrCreatorGalleryRepository.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object state = ((Map<?, ?>) statesField.get(null)).get("merged-session");
        assertNotNull(state);

        Field fapelloField = state.getClass().getDeclaredField("fapelloPending");
        fapelloField.setAccessible(true);
        Field onlyHavenField = state.getClass().getDeclaredField("onlyHavenPending");
        onlyHavenField.setAccessible(true);

        assertEquals(2, ((ArrayDeque<?>) fapelloField.get(state)).size());
        assertEquals(1, ((ArrayDeque<?>) onlyHavenField.get(state)).size());
    }
}
