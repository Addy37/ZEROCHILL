package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapFavoriteAvatarTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before
    public void clean() {
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("creator_avatar_overrides_v1", 0).edit().clear().commit();
        context.getSharedPreferences("manual_creator_merges_v1", 0).edit().clear().commit();
        for (String prefs : new String[]{
                "popular_creator_feed_v3", "popular_creator_feed_v2", "onlyfap_trending_v4",
                "fapzone_creator_feed_v3_1", "fapzone_creator_feed_v3_2",
                "fapzone_creator_feed_v3_3", "fapzone_creator_feed_v1_1",
                "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"
        }) {
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        }
        CreatorAvatarOverrideStore.clearCacheForTest();
        ManualCreatorMergeStore.clearCacheForTest();
    }

    @Test
    public void freshAccountFavoriteHydratesExactArtworkAndPersistsIt() {
        favorites("kira pregiato");
        AtomicInteger callbacks = new AtomicInteger();
        FavoriteCreatorArtworkHydrator hydrator = new FavoriteCreatorArtworkHydrator(
                (ignored, query, limit) -> Arrays.asList(
                        new FapelloRepository.Model(
                                "Kira Something",
                                "https://fapello.com/kira-something/",
                                "https://cdn.example.com/wrong.jpg"
                        ),
                        new FapelloRepository.Model(
                                "Kira Pregiato",
                                "https://fapello.com/kira-pregiato/",
                                "https://cdn.example.com/kira.jpg"
                        )
                )
        );

        hydrator.hydrate(context, 10, callbacks::incrementAndGet);

        assertEquals(1, callbacks.get());
        CreatorCatalog.FavoriteGroup group = CreatorCatalog.favoriteGroups(context).get(0);
        assertEquals("https://cdn.example.com/kira.jpg", group.item.imageUrl);
        assertEquals("https://fapello.com/kira-pregiato/", group.item.url);
    }

    @Test
    public void shelfMetadataRebuildsRestoredFavoriteCards() {
        favorites("anna");
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = hub(activity);
        activity.setContentView(hub);

        LinearLayout rail = favoriteRail(hub);
        assertEquals(1, rail.getChildCount());
        View placeholderCard = rail.getChildAt(0);

        hub.close();
        NativeContentItem resolved = creator("Anna", "https://cdn.example.com/anna.jpg");
        CreatorCatalog.remember(context, Collections.singletonList(resolved));
        hub.setNewCreators(Collections.singletonList(resolved));

        assertEquals(1, rail.getChildCount());
        assertNotSame(placeholderCard, rail.getChildAt(0));
        assertEquals(
                "https://cdn.example.com/anna.jpg",
                CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl
        );
    }

    @Test
    public void customFavoriteAvatarIsClippedToCircleAtRenderTime() {
        favorites("anna");
        CreatorCatalog.remember(
                context,
                Collections.singletonList(creator("Anna", "https://cdn.example.com/default.jpg"))
        );
        assertTrue(CreatorAvatarOverrideStore.save(
                context,
                Collections.singleton("anna"),
                "https://cdn.example.com/custom.jpg",
                "https://fapello.com/anna/",
                0.35f,
                0.6f,
                2f
        ));

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapHubView hub = hub(activity);
        activity.setContentView(hub);

        CreatorAvatarImageView image = findAvatar(favoriteRail(hub));
        assertNotNull(image);
        assertTrue(image.getClipToOutline());
        View frame = (View) image.getParent();
        MaterialCardView card = (MaterialCardView) frame.getParent();
        assertTrue(card.getClipToOutline());

        hub.close();
    }

    private OnlyFapHubView hub(Activity activity) {
        return new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onMore() { }
            @Override public void onViewAllFavorites() { }
        });
    }

    private void favorites(String... names) {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList(names)))
                .commit();
    }

    private NativeContentItem creator(String name, String image) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                "https://fapello.com/" + name.toLowerCase().replace(' ', '-') + "/",
                image,
                "",
                "https://fapello.com/" + name.toLowerCase().replace(' ', '-') + "/",
                "",
                "Fapello",
                name
        );
    }

    private LinearLayout favoriteRail(View root) {
        View shelf = find(root, "Favorite creators shelf");
        assertTrue(shelf instanceof HorizontalScrollView);
        return (LinearLayout) ((HorizontalScrollView) shelf).getChildAt(0);
    }

    private CreatorAvatarImageView findAvatar(View view) {
        if (view instanceof CreatorAvatarImageView) return (CreatorAvatarImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                CreatorAvatarImageView found = findAvatar(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View find(View view, String description) {
        CharSequence current = view.getContentDescription();
        if (description.contentEquals(current == null ? "" : current)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = find(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }
}
