package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class BaddieHubCreatorLinkTest {
    private static final String CREATOR_NAME = "Match Probe Alpha 9037";
    private static final String CREATOR_URL = "https://fapello.com/match-probe-alpha-9037/";

    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable task) {
            tasks.add(task);
        }

        void next() {
            tasks.removeFirst().run();
        }
    }

    @Test public void baddieHubTitleIsOnlyACandidateUntilCatalogVerification() {
        NativeContentItem media = baddie(CREATOR_NAME);

        assertTrue(BaddieHubCreatorMatcher.isCandidate(media));
        assertEquals("", ShitTokCreatorMetadata.creatorName(media));
        assertNull(ShitTokCreatorMetadata.creatorIdentity(null, media));
    }

    @Test public void exactMatchAllowsFormattingDifferencesButRejectsPartialNames() {
        NativeContentItem creator = creator("Nina Crowne", "https://fapello.com/nina-crowne/");

        assertTrue(BaddieHubCreatorMatcher.exact("Nina_Crowne", creator));
        assertTrue(BaddieHubCreatorMatcher.exact("Nina-Crowne", creator));
        assertFalse(BaddieHubCreatorMatcher.exact("Nina", creator));
        assertFalse(BaddieHubCreatorMatcher.exact("Nina Crowne Extra", creator));
    }

    @Test public void catalogMatchReturnsExistingOnlyFapCreatorInsteadOfInventingOne() {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem expected = creator(CREATOR_NAME, CREATOR_URL);
        CreatorCatalog.remember(context, java.util.Collections.singletonList(expected));

        NativeContentItem matched = BaddieHubCreatorMatcher.match(
                context,
                baddie("Match-Probe Alpha 9037")
        );

        assertEquals(CREATOR_NAME, matched.title);
        assertEquals(CREATOR_URL, matched.url);
        assertEquals(expected.imageUrl, matched.imageUrl);
    }

    @Test public void bundledIndexCanVerifyAnUnseenCreatorAlias() {
        Context context = RuntimeEnvironment.getApplication();

        NativeContentItem matched = BaddieHubCreatorMatcher.match(
                context,
                baddie("48-pxnmoxk-79")
        );

        assertNotNull(matched);
        assertEquals("48_pxnmoxk", matched.title);
        assertTrue(matched.url.isEmpty());
    }

    @Test public void genericAndNonBaddieHubTitlesNeverBecomeCandidates() {
        assertFalse(BaddieHubCreatorMatcher.isCandidate(baddie("Video")));
        assertFalse(BaddieHubCreatorMatcher.isCandidate(baddie("BaddieHub")));
        assertFalse(BaddieHubCreatorMatcher.isCandidate(new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                CREATOR_NAME,
                "https://crazyshit.com/cnt/medias/123/example/",
                "",
                "",
                "Uploader",
                ""
        )));
    }

    @Test public void loaderPublishesOnlyTheVerifiedCreatorAndCachesTheResult() {
        Queue worker = new Queue();
        Queue ui = new Queue();
        List<String> ready = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        NativeContentItem expected = creator(CREATOR_NAME, CREATOR_URL);
        NativeContentItem media = baddie(CREATOR_NAME);
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(
                worker,
                ui,
                item -> {
                    lookups.incrementAndGet();
                    return expected;
                },
                ready::add
        );

        assertNull(loader.identity(media));
        assertEquals(1, worker.tasks.size());
        assertEquals(0, lookups.get());

        worker.next();
        assertEquals(1, lookups.get());
        assertNull(loader.identity(media));
        assertEquals(1, ui.tasks.size());

        ui.next();
        assertEquals(CREATOR_URL, loader.identity(media).url);
        assertEquals(1, ready.size());
        assertTrue(worker.tasks.isEmpty());
        loader.close();
    }

    @Test public void unmatchedBaddieHubTitleIsCheckedOnceAndStaysUnlinked() {
        Queue worker = new Queue();
        Queue ui = new Queue();
        AtomicInteger lookups = new AtomicInteger();
        NativeContentItem media = baddie("No Matching Creator 4812");
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(
                worker,
                ui,
                item -> {
                    lookups.incrementAndGet();
                    return null;
                },
                url -> { }
        );

        assertNull(loader.identity(media));
        worker.next();
        ui.next();
        assertNull(loader.identity(media));
        assertEquals(1, lookups.get());
        assertTrue(worker.tasks.isEmpty());
        loader.close();
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

    private static NativeContentItem creator(String title, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                url,
                "https://example.com/creator.webp",
                "",
                url,
                "",
                "Fapello",
                title
        );
    }
}
