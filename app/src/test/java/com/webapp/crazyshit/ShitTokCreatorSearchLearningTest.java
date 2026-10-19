package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = ShitTokCreatorSearchLearningTest.LiveSearch.class)
public class ShitTokCreatorSearchLearningTest {
    private static final String PROFILE = "https://fapello.com/carrotsoki-2/";
    private final Context context = RuntimeEnvironment.getApplication();

    @Implements(value = FapzoneCreatorSearchRepository.class, isInAndroidSdk = false)
    public static class LiveSearch {
        static boolean unavailable;
        @Implementation protected List<NativeContentItem> search(Context context, String query,
                int limit, FapzoneCreatorSearchRepository.ResultListener listener) throws IOException {
            if (unavailable) throw new IOException("Partial source failure");
            return Collections.emptyList();
        }
    }

    private static class Queue extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean closed;
        @Override public void execute(Runnable task) { tasks.add(task); }
        void runAll() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; tasks.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    @Before public void clear() {
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        LiveSearch.unavailable = false;
    }

    private NativeContentItem clip(String profile) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Clip title",
                "https://fapello.com/video/week/31142429/", "https://example.com/clip.jpg",
                "", "CarrotSoki", "", "OnlyFap", profile);
    }

    private NativeContentItem learn() {
        return ShitTokCreatorMetadataLoader.resolver(context).apply(clip(PROFILE));
    }

    @Test public void bindingReturnsImmediatelyAndWorkerMakesKnownCreatorSearchable() {
        Queue worker = new Queue(), ui = new Queue();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                ShitTokCreatorMetadataLoader.resolver(context), url -> {});
        try {
            assertEquals(PROFILE, loader.identity(clip(PROFILE)).url);
            assertTrue(CreatorCatalog.matching(context, "Carrot Soki", false, 10).isEmpty());
            worker.runAll();
            List<NativeContentItem> found = CreatorCatalog.matching(context, "Carrot Soki", false, 10);
            assertEquals(1, found.size());
            assertEquals(PROFILE, found.get(0).url);
            assertEquals("CarrotSoki", found.get(0).title);
            assertEquals("Fapello", found.get(0).description);
            assertTrue(CreatorFavoriteStore.names(context).isEmpty());
            String stored = context.getSharedPreferences(CreatorCatalog.PREFS, 0).getString("items", "");
            ui.runAll();
            loader.identity(new NativeContentItem(NativeContentItem.KIND_MEDIA, "Other clip",
                    "https://fapello.com/video/week/111/", "https://example.com/other.jpg", "",
                    "CarrotSoki", "", "OnlyFap", PROFILE));
            worker.runAll();
            assertEquals(stored, context.getSharedPreferences(CreatorCatalog.PREFS, 0).getString("items", ""));
        } finally { loader.close(); }
    }

    @Test public void nameOnlyFallbackIsNeverLearnedAsAConfirmedProfile() {
        ShitTokCreatorMetadataLoader.resolver(context).apply(clip(""));
        assertTrue(CreatorCatalog.matching(context, "CarrotSoki", false, 10).isEmpty());
    }

    @Test public void opaqueOrUrlLabelsAreNotLearnedAsCreatorNames() {
        for (String label : new String[]{"123456", PROFILE}) {
            NativeContentItem media = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Clip",
                    "https://fapello.com/video/week/31142429/", "", "", label, "", "OnlyFap", PROFILE);
            ShitTokCreatorMetadataLoader.resolver(context).apply(media);
        }
        assertEquals("", context.getSharedPreferences(CreatorCatalog.PREFS, 0).getString("items", ""));
    }

    @Test public void onlyHavenLearnsCreatorNameInsteadOfPostCaption() {
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator("onlyfans", "jane-id",
                "Jane Doe", "https://cum.st/creators/onlyfans/jane-id", "");
        NativeContentItem post = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Unrelated caption",
                "https://e1.cum.st/post.mp4", "", "", creator.url, "", "onlyfans · OnlyHaven");
        ShitTokCreatorMetadataLoader.resolver(context).apply(ChaosSourceMixer.onlyHavenClip(post, creator));
        assertEquals(creator.url, CreatorCatalog.matching(context, "Jane Doe", false, 10).get(0).url);
        assertTrue(CreatorCatalog.matching(context, "Unrelated caption", false, 10).isEmpty());
    }

    @Test public void legacyOnlyHavenPostWithoutKnownCreatorNameDoesNotTeachItsCaption() {
        NativeContentItem media = new NativeContentItem(NativeContentItem.KIND_MEDIA, "A post caption",
                "https://e1.cum.st/post.mp4", "", "", "https://cum.st/creators/onlyfans/123", "", "OnlyHaven");
        ShitTokCreatorMetadataLoader.resolver(context).apply(media);
        assertTrue(CreatorCatalog.matching(context, "A post caption", false, 10).isEmpty());
    }

    @Test public void existingCreatorArtworkAndFavoritesAreNotRewrittenByClipLearning() {
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR, "CarrotSoki",
                PROFILE, "https://example.com/avatar.jpg", "", "", "", "Fapello", "CarrotSoki");
        CreatorCatalog.remember(context, Collections.singletonList(creator));
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", Collections.singleton("carrotsoki")).commit();
        String stored = context.getSharedPreferences(CreatorCatalog.PREFS, 0).getString("items", "");
        NativeContentItem identity = learn();
        assertEquals(creator.imageUrl, identity.imageUrl);
        assertTrue(CreatorFavoriteStore.contains(context, identity));
        assertEquals(stored, context.getSharedPreferences(CreatorCatalog.PREFS, 0).getString("items", ""));
    }

    @Test public void actualOnlyFapSearchShowsLearnedCreatorWhenLiveSearchIsEmptyOrUnavailable() {
        learn();
        for (boolean unavailable : new boolean[]{false, true}) {
            LiveSearch.unavailable = unavailable;
            withPanel((panel, queue) -> {
                ReflectionHelpers.callInstanceMethod(panel, "startOnlyFapSearch", from(String.class, "Carrot Soki"));
                queue.runAll();
                OnlyFapCreatorSearchAdapter adapter = ReflectionHelpers.getField(panel, "onlyFapAdapter");
                assertEquals(1, adapter.getItemCount());
                List<NativeContentItem> local = ReflectionHelpers.getField(panel, "onlyFapLocal");
                assertEquals(PROFILE, local.get(0).url);
                TextView state = ReflectionHelpers.getField(panel, "searchState");
                if (unavailable) assertTrue(state.getText().toString().contains("incomplete"));
            });
        }
    }

    @Test public void actualSearchDoesNotClaimNoMatchesWhenSourcesWereUnavailable() {
        LiveSearch.unavailable = true;
        withPanel((panel, queue) -> {
            ReflectionHelpers.callInstanceMethod(panel, "startOnlyFapSearch", from(String.class, "CarrotSoki"));
            queue.runAll();
            TextView status = ReflectionHelpers.getField(panel, "status");
            assertTrue(status.getText().toString().contains("couldn't be checked"));
            assertFalse(status.getText().toString().contains("No creator matches"));
        });
    }

    private void withPanel(java.util.function.BiConsumer<SearchPanel, Queue> assertion) {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout root = new FrameLayout(host.get());
            host.get().setContentView(root);
            SearchPanel panel = new SearchPanel(host.get(), new Intent().putExtra("scope", "bunkr"), true, () -> {});
            ExecutorService original = ReflectionHelpers.getField(panel, "io");
            original.shutdownNow();
            Queue queue = new Queue();
            ReflectionHelpers.setField(panel, "io", queue);
            try {
                panel.create(null);
                root.addView(panel.view);
                assertion.accept(panel, queue);
            } finally { panel.close(); }
        }
    }
}
