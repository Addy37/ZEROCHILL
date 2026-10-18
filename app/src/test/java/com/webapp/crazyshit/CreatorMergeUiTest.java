package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CreatorMergeUiTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before
    public void clean() {
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        ManualCreatorMergeStore.clearCacheForTest();
    }

    @Test
    public void mergedBadgeShowsProfileCountAndLinkIcon() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinkedHashMap<String, NativeContentItem> members = new LinkedHashMap<>();
        members.put("anna", creator("Anna"));
        members.put("bella", creator("Bella"));
        members.put("cara", creator("Cara"));
        CreatorCatalog.FavoriteGroup group = new CreatorCatalog.FavoriteGroup(
                members.get("anna"),
                members,
                new LinkedHashSet<>(members.keySet()),
                true
        );

        TextView badge = CreatorMergeUi.badge(activity);
        CreatorMergeUi.bind(badge, group, () -> { });

        assertEquals(View.VISIBLE, badge.getVisibility());
        assertEquals("3", badge.getText().toString());
        assertNotNull(badge.getCompoundDrawables()[0]);
        assertTrue(badge.getContentDescription().toString().contains("3 profiles"));
    }

    @Test
    public void mergedGalleryCacheKeyFindsExactManualGroup() {
        favorites("anna", "bella");
        CreatorCatalog.remember(context, Arrays.asList(creator("Anna"), creator("Bella")));
        java.util.List<CreatorCatalog.FavoriteGroup> groups = CreatorCatalog.favoriteGroups(context);
        assertEquals(2, groups.size());
        String nameKey = groups.get(0).relationshipKeys.iterator().next();
        assertTrue(ManualCreatorMergeStore.merge(
                context,
                groups.get(0).relationshipKeys,
                groups.get(1).relationshipKeys,
                nameKey,
                nameKey
        ));

        CreatorCatalog.FavoriteGroup merged = CreatorCatalog.favoriteGroups(context).get(0);
        String cacheKey = CreatorGallerySpec.from(merged).cacheKey;

        assertNotNull(CreatorMergeUi.findManualGroup(context, cacheKey));
        assertNull(CreatorMergeUi.findManualGroup(context, "merged:v2:not-the-same-group"));
        assertNull(CreatorMergeUi.findManualGroup(context, "anna"));
    }

    private void favorites(String... names) {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList(names)))
                .commit();
    }

    private NativeContentItem creator(String name) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                "https://fapello.com/" + name.toLowerCase() + "/",
                "https://cdn.example.com/" + name.toLowerCase() + ".jpg",
                "",
                "",
                "",
                "",
                name
        );
    }
}
