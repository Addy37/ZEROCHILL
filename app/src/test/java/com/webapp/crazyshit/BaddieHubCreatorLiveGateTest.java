package com.webapp.crazyshit;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class BaddieHubCreatorLiveGateTest {
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
    }

    @Test public void selectedAndReadySignalsFollowTheCurrentShitTokUrl() {
        String first = "https://baddiehub.com/prettyassjas/";
        String second = "https://baddiehub.com/missbnasty/";

        AppPerformance.startupItem("owner_select", first, "");
        assertTrue(AppPerformance.isShitTokSelected(first));
        assertFalse(AppPerformance.isShitTokSelectedReady(first));

        AppPerformance.startupItem("player_state_3", first, "");
        assertTrue(AppPerformance.isShitTokSelectedReady(first));

        AppPerformance.startupItem("owner_select", first, "");
        assertTrue(AppPerformance.isShitTokSelectedReady(first));

        AppPerformance.startupItem("owner_select", second, "");
        assertFalse(AppPerformance.isShitTokSelected(first));
        assertTrue(AppPerformance.isShitTokSelected(second));
        assertFalse(AppPerformance.isShitTokSelectedReady(second));
    }

    @Test public void staleCardExitsBeforeTheLiveResolverRuns() {
        Queue worker = new Queue();
        ScheduledThreadPoolExecutor liveWorker = new ScheduledThreadPoolExecutor(1);
        AtomicInteger liveLookups = new AtomicInteger();
        NativeContentItem media = baddie("prettyassjas");
        NativeContentItem match = creator("prettyassjas");
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(
                worker,
                liveWorker,
                Runnable::run,
                item -> null,
                item -> {
                    liveLookups.incrementAndGet();
                    return match;
                },
                url -> { }
        );
        try {
            AppPerformance.startupItem("owner_select", media.url, "");
            loader.identity(media);
            AppPerformance.startupItem("owner_select", "https://baddiehub.com/another/", "");

            ReflectionHelpers.callInstanceMethod(
                    loader,
                    "runLiveSelected",
                    from(NativeContentItem.class, media),
                    from(int.class, 0)
            );

            assertEquals(0, liveLookups.get());
        } finally {
            loader.close();
            liveWorker.shutdownNow();
        }
    }

    @Test public void selectedCardCanPublishAnExactLiveCreatorWithoutTouchingPlayback() {
        Queue worker = new Queue();
        ScheduledThreadPoolExecutor liveWorker = new ScheduledThreadPoolExecutor(1);
        AtomicInteger liveLookups = new AtomicInteger();
        AtomicInteger published = new AtomicInteger();
        NativeContentItem media = baddie("MissBNasty");
        NativeContentItem match = creator("MissBNasty");
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(
                worker,
                liveWorker,
                Runnable::run,
                item -> null,
                item -> {
                    liveLookups.incrementAndGet();
                    return match;
                },
                url -> published.incrementAndGet()
        );
        try {
            AppPerformance.startupItem("owner_select", media.url, "");
            loader.identity(media);

            ReflectionHelpers.callInstanceMethod(
                    loader,
                    "runLiveSelected",
                    from(NativeContentItem.class, media),
                    from(int.class, 0)
            );

            assertEquals(1, liveLookups.get());
            assertEquals(1, published.get());
            assertEquals(match.url, loader.identity(media).url);
        } finally {
            loader.close();
            liveWorker.shutdownNow();
        }
    }

    private static NativeContentItem baddie(String title) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                "https://baddiehub.com/" + CreatorNameMatcher.normalized(title).replace(' ', '-') + "/",
                "https://baddiehub.com/thumb.jpg",
                "",
                BaddieHubRepository.LABEL,
                ""
        );
    }

    private static NativeContentItem creator(String title) {
        String slug = CreatorNameMatcher.normalized(title).replace(' ', '-');
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                "https://fapello.com/" + slug + "/",
                "https://example.com/creator.webp",
                "",
                "https://fapello.com/" + slug + "/",
                "",
                "Fapello",
                title
        );
    }
}
