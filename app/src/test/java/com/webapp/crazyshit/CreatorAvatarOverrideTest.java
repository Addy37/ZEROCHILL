package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CreatorAvatarOverrideTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before
    public void clean() {
        context.getSharedPreferences("creator_avatar_overrides_v1", 0).edit().clear().commit();
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        CreatorAvatarOverrideStore.clearCacheForTest();
        ManualCreatorMergeStore.clearCacheForTest();
    }

    private NativeContentItem creator(String name, String image) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                "https://fapello.com/" + name.replace(' ', '-').toLowerCase() + "/",
                image,
                "",
                "",
                "",
                "",
                name
        );
    }

    private void favorites(String... names) {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList(names))).commit();
    }

    private Set<String> keys(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @Test
    public void galleryAvatarAppliesToSingleFavoriteWithoutChangingRawFavorite() {
        favorites("anna");
        CreatorCatalog.remember(context, Arrays.asList(
                creator("Anna", "https://example.com/default.jpg")
        ));

        assertTrue(CreatorAvatarOverrideStore.save(
                context,
                keys("anna"),
                "https://cdn.example.com/custom.jpg",
                "https://fapello.com/anna/"
        ));

        CreatorCatalog.FavoriteGroup group = CreatorCatalog.favoriteGroups(context).get(0);
        assertEquals("https://cdn.example.com/custom.jpg", group.item.imageUrl);
        assertEquals("https://fapello.com/anna/", group.item.uploader);
        assertEquals(keys("anna"), CreatorFavoriteStore.names(context));
        assertEquals(
                "https://example.com/default.jpg",
                CreatorCatalog.rawFavorites(context).get(0).imageUrl
        );
    }

    @Test
    public void mergedGroupUsesCustomAvatarAndDefaultCanBeRestored() {
        favorites("anna", "bella");
        CreatorCatalog.remember(context, Arrays.asList(
                creator("Anna", "https://example.com/a.jpg"),
                creator("Bella", "https://example.com/b.jpg")
        ));
        assertTrue(ManualCreatorMergeStore.merge(
                context,
                keys("anna"),
                keys("bella"),
                "bella",
                "anna"
        ));
        assertTrue(CreatorAvatarOverrideStore.save(
                context,
                keys("anna", "bella"),
                "https://cdn.example.com/merged.jpg",
                "https://fapello.com/bella/"
        ));

        CreatorCatalog.FavoriteGroup merged = CreatorCatalog.favoriteGroups(context).get(0);
        assertEquals("https://cdn.example.com/merged.jpg", merged.item.imageUrl);

        assertTrue(CreatorAvatarOverrideStore.clear(context, keys("anna", "bella")));
        merged = CreatorCatalog.favoriteGroups(context).get(0);
        assertEquals("https://example.com/a.jpg", merged.item.imageUrl);
    }

    @Test
    public void groupSpecificOverridesRemainDormantAcrossMergeAndUnmerge() {
        favorites("anna", "bella");
        CreatorCatalog.remember(context, Arrays.asList(
                creator("Anna", "https://example.com/a.jpg"),
                creator("Bella", "https://example.com/b.jpg")
        ));
        assertTrue(CreatorAvatarOverrideStore.save(
                context,
                keys("anna"),
                "https://cdn.example.com/anna-custom.jpg",
                "https://fapello.com/anna/"
        ));
        assertTrue(ManualCreatorMergeStore.merge(
                context,
                keys("anna"),
                keys("bella"),
                "bella",
                "bella"
        ));
        assertTrue(CreatorAvatarOverrideStore.save(
                context,
                keys("anna", "bella"),
                "https://cdn.example.com/merged.jpg",
                "https://fapello.com/bella/"
        ));
        assertEquals(
                "https://cdn.example.com/merged.jpg",
                CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl
        );

        assertTrue(ManualCreatorMergeStore.unmerge(context, keys("anna", "bella")));
        CreatorCatalog.FavoriteGroup anna = CreatorCatalog.favoriteGroups(context).stream()
                .filter(group -> group.item.title.equals("Anna"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("https://cdn.example.com/anna-custom.jpg", anna.item.imageUrl);
    }

    @Test
    public void invalidRemoteAvatarIsRejected() {
        assertFalse(CreatorAvatarOverrideStore.save(
                context,
                keys("anna"),
                "file:///tmp/avatar.jpg",
                ""
        ));
        assertFalse(CreatorAvatarOverrideStore.has(context, keys("anna")));
    }
}
