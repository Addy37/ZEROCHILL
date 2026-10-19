package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {
        OnlyFapLibraryPlaybackTest.Pages.class, OnlyFapLibraryPlaybackTest.Galleries.class})
public class OnlyFapLibraryPlaybackTest {
    private static final String URL = "https://fapello.com/video/week/31142429/";
    private static final String PROFILE = "https://fapello.com/carrotsoki-2/";

    @Implements(value = FapelloRepository.class, isInAndroidSdk = false)
    public static class Pages {
        static int calls;
        static boolean unavailable;
        @Implementation protected NativeContentItem recoverVideoMetadata(Context context,
                NativeContentItem item) throws IOException {
            calls++;
            if (unavailable) throw new IOException("Source unavailable");
            return clip();
        }
    }

    @Implements(value = BunkrCreatorGalleryRepository.class, isInAndroidSdk = false)
    public static class Galleries {
        static int calls;
        static String profile;
        @Implementation protected BunkrCreatorGalleryRepository.Batch fetchNext(Context context,
                String session, String query, String sourceProfile, String name) {
            calls++;
            profile = sourceProfile;
            return new BunkrCreatorGalleryRepository.Batch(new ArrayList<>(), true);
        }
    }

    private static class Queue extends AbstractExecutorService {
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        boolean closed;
        @Override public void execute(Runnable task) { pending.add(task); }
        void runAll() { while (!pending.isEmpty()) pending.removeFirst().run(); }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; pending.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed && pending.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    @Before public void clear() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackHistoryStore.resetForTests();
        for (String prefs : new String[]{"playback_history", "watch_later", "native_video_downloads",
                "zerochill_account_secure_state", "creator_favorites", CreatorCatalog.PREFS,
                SocialContentContextStore.PREFS, "onlyfap_hot_gallery_sessions"})
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        Pages.calls = 0; Pages.unavailable = false;
        Galleries.calls = 0; Galleries.profile = "";
    }

    private static NativeContentItem clip() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "OnlyFap video", URL,
                "", "", "CarrotSoki", "", "OnlyFap", PROFILE);
    }

    @Test public void knownCreatorUsesSharedGalleryOffThreadWithoutProfileRecovery() {
        withPlayer(clip(), (activity, queue) -> {
            ReflectionHelpers.callInstanceMethod(activity, "loadRelated");
            assertEquals(0, Pages.calls);
            assertEquals(0, Galleries.calls);
            queue.runAll(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, Pages.calls);
            assertEquals(1, Galleries.calls);
            assertEquals(PROFILE, Galleries.profile);
            TextView title = ReflectionHelpers.getField(activity, "relatedTitle");
            assertEquals("More from CarrotSoki", title.getText().toString());
            assertEquals("file:///playing.mp4", ReflectionHelpers.getField(activity, "mediaUrl"));
        });
    }

    @Test public void olderSavedClipRecoversOnWorkerWithoutReplacingSavedIdentity() {
        NativeContentItem old = OnlyFapVideoMetadata.basic("OnlyFap video", URL, "");
        FavoriteStore.add(RuntimeEnvironment.getApplication(), old);
        withPlayer(old, (activity, queue) -> {
            ReflectionHelpers.callInstanceMethod(activity, "loadRelated");
            assertEquals(0, Pages.calls);
            queue.runAll(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, Pages.calls);
            assertEquals(PROFILE, Galleries.profile);
            assertEquals(URL, ReflectionHelpers.getField(activity, "pageUrl"));
            assertEquals(PROFILE, OnlyFapVideoMetadata.creator(FavoriteStore.load(activity).get(0).content).url);
        });
    }

    @Test public void sourceFailureLeavesPlaybackAvailableAndDoesNotLoadGenericRelatedFeed() {
        Pages.unavailable = true;
        withPlayer(OnlyFapVideoMetadata.basic("OnlyFap video", URL, ""), (activity, queue) -> {
            ReflectionHelpers.callInstanceMethod(activity, "loadRelated");
            queue.runAll(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, Pages.calls);
            assertEquals(0, Galleries.calls);
            LinearLayout row = ReflectionHelpers.getField(activity, "onlyFapCreatorRow");
            assertTrue(hasText(row, "Creator unavailable"));
            assertEquals("file:///playing.mp4", ReflectionHelpers.getField(activity, "mediaUrl"));
        });
    }

    @Test public void onlyHavenCreatorUsesKnownProfileAndPreservesPostCaption() {
        String profile = "https://cum.st/creators/onlyfans/fixture";
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "A post caption",
                "https://e1.cum.st/clip.mp4", "", "", profile, "", "OnlyHaven", "Fixture Creator");
        withPlayer(item, (activity, queue) -> {
            ReflectionHelpers.callInstanceMethod(activity, "loadRelated");
            queue.runAll(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, Pages.calls);
            assertEquals(profile, Galleries.profile);
            NativeContentItem content = ReflectionHelpers.callInstanceMethod(activity, "currentContent");
            assertEquals("A post caption", content.title);
            assertEquals("Fixture Creator", OnlyFapVideoMetadata.displayTitle(content));
        });
    }

    private void withPlayer(NativeContentItem content,
            java.util.function.BiConsumer<VideoDetailActivity, Queue> assertion) {
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class).get();
        ((ExecutorService) ReflectionHelpers.getField(activity, "io")).shutdownNow();
        Queue queue = new Queue();
        ReflectionHelpers.setField(activity, "io", queue);
        ReflectionHelpers.setField(activity, "onlyFapContent", content);
        ReflectionHelpers.setField(activity, "pageUrl", content.url);
        ReflectionHelpers.setField(activity, "mediaUrl", "file:///playing.mp4");
        ReflectionHelpers.setField(activity, "title", content.title);
        ReflectionHelpers.setField(activity, "posterUrl", "");
        ReflectionHelpers.setField(activity, "views", "");
        ReflectionHelpers.setField(activity, "uploader", "");
        ReflectionHelpers.setField(activity, "comments", "");
        ReflectionHelpers.setField(activity, "onlyFapCreatorRow", new LinearLayout(activity));
        ReflectionHelpers.setField(activity, "relatedTitle", new TextView(activity));
        ReflectionHelpers.setField(activity, "relatedContainer", new LinearLayout(activity));
        try { assertion.accept(activity, queue); }
        finally { queue.shutdownNow(); }
    }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
}
