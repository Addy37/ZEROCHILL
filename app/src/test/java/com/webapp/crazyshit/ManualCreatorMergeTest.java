package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ManualCreatorMergeTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before public void clean() {
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        ManualCreatorMergeStore.clearCacheForTest();
    }

    private NativeContentItem creator(String name, String image) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://fapello.com/" + name.replace(' ', '-').toLowerCase() + "/",
                image, "", "", "", "", name);
    }

    private void favorites(String... names) {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList(names))).commit();
    }

    private Set<String> keys(String... names) { return new LinkedHashSet<>(Arrays.asList(names)); }

    @Test public void mergePersistsAcrossRestartAndUnmergePreservesFavoritesAndBackup() throws Exception {
        favorites("anna", "bella");
        CreatorCatalog.remember(context, Arrays.asList(creator("Anna", "https://example.com/a.jpg"),
                creator("Bella", "https://example.com/b.jpg")));
        JSONObject before = AppBackupStore.export(context);
        assertTrue(ManualCreatorMergeStore.merge(context, keys("anna"), keys("bella"), "bella", "anna"));
        assertEquals(1, CreatorCatalog.favoriteGroups(context).size());
        assertEquals("Bella", CreatorCatalog.favoriteGroups(context).get(0).item.title);
        assertEquals("https://example.com/a.jpg", CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl);
        ManualCreatorMergeStore.clearCacheForTest();
        assertEquals(1, CreatorCatalog.favoriteGroups(context).size());
        assertEquals(before.getJSONArray("creators").length(), AppBackupStore.export(context)
                .getJSONArray("creators").length());
        assertFalse(AppBackupStore.export(context).has("manualCreatorMerges"));
        assertTrue(ManualCreatorMergeStore.unmerge(context, keys("anna", "bella")));
        assertEquals(2, CreatorCatalog.favoriteGroups(context).size());
        assertEquals(keys("anna", "bella"), CreatorFavoriteStore.names(context));
    }

    @Test public void primaryNameAndAvatarChangeIndependently() {
        favorites("anna", "bella");
        CreatorCatalog.remember(context, Arrays.asList(creator("Anna", "https://example.com/a.jpg"),
                creator("Bella", "https://example.com/b.jpg")));
        assertTrue(ManualCreatorMergeStore.merge(context, keys("anna"), keys("bella"), "bella", "anna"));
        assertTrue(ManualCreatorMergeStore.changePrimary(context, keys("anna", "bella"), "anna", "bella"));
        CreatorCatalog.FavoriteGroup group = CreatorCatalog.favoriteGroups(context).get(0);
        assertEquals("Anna", group.item.title);
        assertEquals("https://example.com/b.jpg", group.item.imageUrl);
    }

    @Test public void multipleAliasesAndReviewedIdentityStayGrouped() {
        favorites("sasha foxx", "sasha foxxx", "anna", "bella");
        assertEquals(3, CreatorCatalog.favoriteGroups(context).size());
        assertTrue(ManualCreatorMergeStore.merge(context,
                keys("sasha foxx", "sasha foxxx"), keys("anna"), "anna", "anna"));
        assertEquals(2, CreatorCatalog.favoriteGroups(context).size());
        CreatorCatalog.FavoriteGroup merged = CreatorCatalog.favoriteGroups(context).stream()
                .filter(group -> group.manual).findFirst().get();
        assertEquals(3, merged.members.size());
        assertTrue(ManualCreatorMergeStore.merge(context, merged.relationshipKeys,
                keys("bella"), "bella", "bella"));
        assertEquals(1, CreatorCatalog.favoriteGroups(context).size());
        assertEquals(4, CreatorFavoriteStore.names(context).size());
    }

    @Test public void removalAndReaddPreserveDormantRelationship() {
        favorites("anna", "bella");
        assertTrue(ManualCreatorMergeStore.merge(context, keys("anna"), keys("bella"), "bella", "bella"));
        favorites("anna");
        assertEquals(1, CreatorCatalog.favoriteGroups(context).size());
        assertEquals("anna", CreatorCatalog.favoriteGroups(context).get(0).item.title);
        favorites("anna", "bella");
        assertEquals(1, CreatorCatalog.favoriteGroups(context).size());
        assertEquals("bella", CreatorCatalog.favoriteGroups(context).get(0).item.title);
    }

    @Test public void cyclesAndMalformedOrDuplicateRelationshipsFailSafely() {
        favorites("anna", "bella", "cara");
        assertFalse(ManualCreatorMergeStore.merge(context, keys("anna"), keys("anna"), "anna", "anna"));
        assertTrue(ManualCreatorMergeStore.merge(context, keys("anna"), keys("bella"), "bella", "bella"));
        assertFalse(ManualCreatorMergeStore.merge(context, keys("anna", "bella"), keys("bella", "cara"),
                "cara", "cara"));
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit()
                .putString("groups", "[{\"keys\":[\"anna\",\"bella\"],\"name\":\"bella\",\"avatar\":\"bella\"},"
                        + "{\"keys\":[\"anna\",\"cara\"],\"name\":\"cara\",\"avatar\":\"cara\"},"
                        + "{\"keys\":[\"cara\",\"cara\"],\"name\":\"cara\",\"avatar\":\"cara\"}]").commit();
        ManualCreatorMergeStore.clearCacheForTest();
        assertEquals(1, ManualCreatorMergeStore.load(context).size());
        assertEquals(2, CreatorCatalog.favoriteGroups(context).size());
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit().putString("groups", "broken").commit();
        ManualCreatorMergeStore.clearCacheForTest();
        assertEquals(3, CreatorCatalog.favoriteGroups(context).size());
    }
}
