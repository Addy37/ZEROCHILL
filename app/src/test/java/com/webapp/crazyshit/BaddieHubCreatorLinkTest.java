package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BaddieHubCreatorLinkTest {
    private final Context app = RuntimeEnvironment.getApplication();
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void next() { tasks.removeFirst().run(); }
    }
    static final class Delayed extends FutureTask<Void> implements ScheduledFuture<Void> {
        Delayed(Runnable run) { super(run, null); }
        public long getDelay(TimeUnit unit) { return 0; }
        public int compareTo(java.util.concurrent.Delayed other) { return 0; }
    }
    static final class Scheduler extends ScheduledThreadPoolExecutor {
        final ArrayDeque<Delayed> tasks = new ArrayDeque<>();
        Scheduler() { super(1); }
        @Override public ScheduledFuture<?> schedule(Runnable run, long delay, TimeUnit unit) {
            Delayed task = new Delayed(run); tasks.add(task); return task;
        }
        void next() { tasks.removeFirst().run(); }
    }
    @Before public void clear() {
        app.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        app.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
    }
    static NativeContentItem media(String name) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name,
                "https://baddiehub.com/clip/", "", "153K", "BaddieHub", "", "");
    }
    static NativeContentItem creator(String name) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://fapello.com/" + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + "/",
                "https://example.invalid/avatar.jpg", "", "", "", "Fapello", name);
    }
    @Test public void cleanedParserTitleMatchesCanonicalWithoutViewsOrDuration() throws Exception {
        for (String name : new String[]{"prettyassjas", "MissBNasty"}) {
            NativeContentItem canonical = creator(name);
            CreatorCatalog.remember(app, Collections.singletonList(canonical));
            BaddieHubCreatorMatcher matcher = new BaddieHubCreatorMatcher(app, title -> {
                fail("Local data must win"); return null;
            });
            for (String views : new String[]{"587", "153K", "1.2M"}) {
                List<NativeContentItem> cards = BaddieHubRepository.parseShowsListing(
                        "<a href='/clip/'><div class='post-thumbnail'><img src='/a.jpg'>"
                        + "<span class='views'>" + views + "</span><span class='duration'>12:34</span>"
                        + "</div><div class='entry-header'>  " + name.toUpperCase(java.util.Locale.ROOT)
                        + "  </div></a>", "https://baddiehub.com/", "a[href]");
                assertEquals(name.toUpperCase(java.util.Locale.ROOT), cards.get(0).title);
                NativeContentItem found = matcher.live(cards.get(0));
                assertEquals(canonical.url, found.url);
                assertEquals(canonical.title, found.title);
                assertEquals(canonical.searchQuery, found.searchQuery);
            }
        }
    }
    @Test public void normalizationIsExactAndAmbiguousNamesNeverUseLiveOverride() throws Exception {
        assertTrue(BaddieHubCreatorMatcher.exact("  NINA__Crowne  ", creator("Nina Crowne")));
        assertFalse(BaddieHubCreatorMatcher.exact("Nina", creator("Nina Crowne")));
        assertFalse(BaddieHubCreatorMatcher.exact("Nina Crowne 153K 12:34", creator("Nina Crowne")));
        CreatorCatalog.remember(app, Arrays.asList(creator("Mía Rose"), creator("Mia-Rose")));
        BaddieHubCreatorMatcher matcher = new BaddieHubCreatorMatcher(app, title -> {
            fail("Ambiguous local identities must stay unlinked"); return null;
        });
        assertNull(matcher.local(media("mia rose")));
        assertNull(matcher.live(media("mia rose")));
        assertNull(BaddieHubCreatorMatcher.unique(Arrays.asList(creator("Jane"), creator("Jané"))));
    }
    @Test public void reviewedAliasesAreCanonicalAndCollisionsAreRejected() throws Exception {
        BundledCreatorIndex index = BundledCreatorIndex.parse(new BufferedReader(new StringReader(
                "Nina Crowne\tnina_crowne|official nina\nOther\tshared\nThird\tshared\n")));
        assertEquals("Nina Crowne", index.exact("OFFICIAL NINA").title);
        assertNull(index.exact("Nina"));
        assertNull(index.exact("shared"));
        assertTrue(index.ambiguous("SHARED"));
    }
    @Test public void verifiedAliasKeepsCanonicalIdentityAndSuccessfulLookupIsCached() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NativeContentItem canonical = creator("Canonical Name");
        BaddieHubCreatorMatcher matcher = new BaddieHubCreatorMatcher(app, title -> {
            calls.incrementAndGet(); assertEquals("prettyassjas", title);
            return Collections.singletonList(canonical);
        });
        assertSame(canonical, matcher.live(media("prettyassjas")));
        assertSame(canonical, matcher.live(media("  PRETTYASSJAS  ")));
        assertEquals(1, calls.get());
        assertEquals(canonical.url, CreatorCatalog.matching(app, "Canonical Name", false, 10).get(0).url);
    }
    @Test public void networkFailureCanRetryAndUnmatchedOrAmbiguousStayUnlinked() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BaddieHubCreatorMatcher matcher = new BaddieHubCreatorMatcher(app, title -> {
            if (calls.getAndIncrement() == 0) throw new IOException("temporary");
            return Collections.singletonList(creator("MissBNasty"));
        });
        try { matcher.live(media("MissBNasty")); fail(); } catch (IOException expected) { }
        assertEquals("MissBNasty", matcher.live(media("MissBNasty")).title);
        assertNull(new BaddieHubCreatorMatcher(app, title -> Collections.emptyList()).live(media("Unknown 7301")));
        assertNull(new BaddieHubCreatorMatcher(app, title -> Arrays.asList(creator("Alpha"), creator("Beta")))
                .live(media("Unknown 7301")));
    }
    @Test public void liveLookupWaitsForLocalMissAndSelectionAndPublishesOnlyCurrentCard() {
        Queue worker = new Queue(), ui = new Queue(); Scheduler live = new Scheduler();
        AtomicInteger calls = new AtomicInteger(), ready = new AtomicInteger();
        NativeContentItem clip = media("prettyassjas"), result = creator("Canonical Name");
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, live, ui,
                item -> null, item -> { calls.incrementAndGet(); return result; }, url -> ready.incrementAndGet());
        try {
            assertNull(loader.identity(clip));
            assertTrue(live.tasks.isEmpty());
            loader.select(clip);
            assertTrue(live.tasks.isEmpty()); // No network ahead of the local catalog.
            worker.next(); ui.next();
            assertEquals(1, live.tasks.size()); assertEquals(0, calls.get());
            live.next();
            loader.select(media("another")); // Recycled card, same video URL, different title.
            ui.next();
            assertEquals(1, ready.get()); // Only the local completion was announced.
            assertNull(loader.identity(media("another")));
            assertSame(result, loader.identity(clip)); // Successful identity remains cached.
            assertEquals(1, calls.get());
        } finally { loader.close(); }
    }
    @Test public void staleQueuedLiveWorkAndClearNeverPublish() {
        for (boolean clear : new boolean[]{false, true}) {
            Queue worker = new Queue(), ui = new Queue(); Scheduler live = new Scheduler();
            AtomicInteger calls = new AtomicInteger(), ready = new AtomicInteger();
            NativeContentItem clip = media("prettyassjas");
            ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, live, ui,
                    item -> null, item -> { calls.incrementAndGet(); return creator("Name"); }, url -> ready.incrementAndGet());
            try {
                loader.select(clip); worker.next(); ui.next();
                if (clear) { live.next(); loader.clear(); ui.next(); }
                else { loader.select(null); live.next(); }
                assertEquals(clear ? 1 : 0, calls.get());
                assertEquals(1, ready.get());
                assertNull(loader.identity(media("other")));
            } finally { loader.close(); }
        }
    }
    @Test public void loaderRetriesTemporaryFailuresAfterCooldownAndCachesSuccess() {
        Queue worker = new Queue(), ui = new Queue(); Scheduler live = new Scheduler();
        AtomicInteger calls = new AtomicInteger(); NativeContentItem clip = media("MissBNasty");
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, live, ui,
                item -> null, item -> {
                    if (calls.getAndIncrement() == 0) throw new IOException("offline");
                    return creator("MissBNasty");
                }, url -> {});
        try {
            loader.select(clip); worker.next(); ui.next(); live.next(); ui.next();
            loader.select(clip); assertTrue(live.tasks.isEmpty());
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(11));
            loader.select(clip); live.next(); ui.next();
            assertEquals(2, calls.get()); assertEquals("MissBNasty", loader.identity(clip).title);
            loader.select(null); loader.select(clip); assertTrue(live.tasks.isEmpty());
        } finally { loader.close(); }
    }
    @Test public void titleAvatarAndCompletedHorizontalSwipeUseSameIdentityWithoutPlayerRestart() {
        for (String action : new String[]{"title", "avatar", "swipe"}) {
            try (org.robolectric.android.controller.ActivityController<Activity> host =
                    Robolectric.buildActivity(Activity.class).setup()) {
                ChaosFeedView feed = new ChaosFeedView(host.get(), item -> {}, false);
                ShitTokCreatorMetadataLoader original = ReflectionHelpers.getField(feed, "creatorMetadata");
                original.close(); Queue worker = new Queue();
                NativeContentItem clip = media("prettyassjas"), canonical = creator("Canonical Name");
                ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, Runnable::run,
                        item -> canonical, url -> ReflectionHelpers.callInstanceMethod(feed,
                                "onCreatorMetadataReady", from(String.class, url)));
                ReflectionHelpers.setField(feed, "creatorMetadata", loader);
                ExoPlayer player = new ExoPlayer.Builder(host.get()).build();
                try {
                    Object adapter = ReflectionHelpers.getField(feed, "adapter");
                    Object holder = ReflectionHelpers.callInstanceMethod(adapter, "onCreateViewHolder",
                            from(android.view.ViewGroup.class, new FrameLayout(host.get())), from(int.class, 0));
                    ReflectionHelpers.setField(holder, "item", clip);
                    ReflectionHelpers.setField(holder, "player", player);
                    java.util.Set<Object> holders = ReflectionHelpers.getField(feed, "playerHolders"); holders.add(holder);
                    ReflectionHelpers.callInstanceMethod(holder, "bindCreatorChrome", from(NativeContentItem.class, clip));
                    TextView title = ReflectionHelpers.getField(holder, "title"), meta = ReflectionHelpers.getField(holder, "meta");
                    assertEquals(clip.title, title.getText().toString()); assertFalse(title.isClickable());
                    ReflectionHelpers.setField(feed, "userPaging", true); worker.next();
                    assertEquals(clip.title, title.getText().toString());
                    ReflectionHelpers.setField(feed, "userPaging", false);
                    ReflectionHelpers.callInstanceMethod(feed, "applyDeferredUi");
                    assertSame(player, ReflectionHelpers.getField(holder, "player"));
                    assertEquals(androidx.media3.common.Player.STATE_IDLE, player.getPlaybackState());
                    assertEquals("Canonical Name", title.getText().toString());
                    assertEquals("BaddieHub", meta.getText().toString()); assertEquals(View.VISIBLE, meta.getVisibility());
                    assertSame(clip, ReflectionHelpers.getField(holder, "item"));
                    if (action.equals("title")) title.performClick();
                    else if (action.equals("avatar")) ReflectionHelpers.<View>getField(holder, "creatorAvatarControl").performClick();
                    else {
                        View view = ReflectionHelpers.getField(holder, "playerView");
                        View.OnTouchListener touch = shadowOf(view).getOnTouchListener();
                        for (int event : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP}) {
                            MotionEvent motion = MotionEvent.obtain(0, event * 16, event,
                                    event == MotionEvent.ACTION_DOWN ? 700 : 0, 500, 0);
                            touch.onTouch(view, motion); motion.recycle();
                        }
                        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
                    }
                    Intent intent = shadowOf(host.get()).getNextStartedActivity(); assertNotNull(action, intent);
                    assertEquals(canonical.searchQuery, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
                    assertEquals(canonical.url, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_FAPELLO_PROFILE_URL));
                    assertEquals(NativeFeedBrowserActivity.class.getName(), intent.getComponent().getClassName());
                    assertTrue(worker.tasks.isEmpty());
                } finally {
                    // The holder normally owns a lease; this test injected a standalone player.
                    feed.close(); player.release();
                }
            }
        }
    }
}
