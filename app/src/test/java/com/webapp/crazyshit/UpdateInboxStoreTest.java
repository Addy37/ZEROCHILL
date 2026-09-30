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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class UpdateInboxStoreTest {
    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        app.getSharedPreferences("creator_favorites", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void inboxKeepsOnlyFavoriteCreatorUpdatesAndIgnoresGeneralVideos() {
        NativeContentItem favorite = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                "Emily Rinaudo",
                "https://fapello.com/emily-rinaudo/",
                "https://img.example/avatar.jpg",
                "",
                "",
                "",
                "Fapello",
                "Emily Rinaudo"
        );
        CreatorFavoriteStore.toggle(app, favorite);

        NativeContentItem fapello = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "OnlyFap video #123",
                "https://fapello.com/video/emily-rinaudo/123/",
                "https://img.example/fapello.jpg",
                "",
                "Emily Rinaudo",
                "",
                "Fapello"
        );
        NativeContentItem onlyHaven = new NativeContentItem(
                NativeContentItem.KIND_SERIES,
                "Emily Rinaudo",
                "https://onlyhaven.example/emily",
                "https://img.example/avatar.jpg",
                "",
                "",
                "",
                "OnlyHaven"
        );
        NativeContentItem nonFavorite = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "OnlyFap video #999",
                "https://fapello.com/video/grace-bartlow/999/",
                "https://img.example/grace.jpg",
                "",
                "Grace Bartlow",
                "",
                "Fapello"
        );
        NativeContentItem crazy = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Fresh clip",
                "https://crazyshit.com/video/fresh",
                "https://img.example/video.jpg",
                "",
                "",
                "",
                "CrazyShit"
        );

        UpdateInboxStore.record(app, Arrays.asList(
                new NotificationCoordinator.SourceAlert(
                        "fapello", "Fapello", Arrays.asList(fapello, nonFavorite)
                ),
                new NotificationCoordinator.SourceAlert(
                        "onlyhaven", "OnlyHaven", Arrays.asList(onlyHaven)
                ),
                new NotificationCoordinator.SourceAlert(
                        "crazyshit", "CrazyShit", Arrays.asList(crazy)
                )
        ));

        List<UpdateInboxStore.Entry> all = UpdateInboxStore.all(app);
        List<UpdateInboxStore.Entry> onlyFap =
                UpdateInboxStore.filtered(app, UpdateInboxStore.CATEGORY_ONLYFAP);

        assertEquals(1, all.size());
        assertEquals(1, onlyFap.size());
        assertEquals("Emily Rinaudo", onlyFap.get(0).creatorName);
        assertEquals(2, onlyFap.get(0).count);
        assertEquals(2, onlyFap.get(0).freshUrls.size());
        assertEquals("https://img.example/avatar.jpg", onlyFap.get(0).avatarUrl);
        assertEquals(1, UpdateInboxStore.unreadCount(app));

        UpdateInboxStore.markRead(app, onlyFap.get(0).id);
        assertEquals(0, UpdateInboxStore.unreadCount(app));
    }

    @Test
    public void creatorUnreadCountCanBeConsumedByOpeningCreator() {
        NativeContentItem favorite = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                "Emily Rinaudo",
                "https://fapello.com/emily-rinaudo/",
                "",
                "",
                "",
                "",
                "Fapello",
                "Emily Rinaudo"
        );
        CreatorFavoriteStore.toggle(app, favorite);

        NativeContentItem first = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "OnlyFap video #1",
                "https://fapello.com/video/emily-rinaudo/1/",
                "",
                "",
                "Emily Rinaudo",
                "",
                "Fapello"
        );
        NativeContentItem second = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "OnlyFap video #2",
                "https://fapello.com/video/emily-rinaudo/2/",
                "",
                "",
                "Emily Rinaudo",
                "",
                "Fapello"
        );
        UpdateInboxStore.record(app, Arrays.asList(
                new NotificationCoordinator.SourceAlert(
                        "fapello", "Fapello", Arrays.asList(first, second)
                )
        ));

        assertEquals(2, UpdateInboxStore.unreadCreatorContentCount(
                app, Arrays.asList("Emily Rinaudo")
        ));

        UpdateInboxStore.markCreatorRead(app, Arrays.asList("Emily Rinaudo"));

        assertEquals(0, UpdateInboxStore.unreadCreatorContentCount(
                app, Arrays.asList("Emily Rinaudo")
        ));
        assertEquals(0, UpdateInboxStore.unreadCount(app));
    }

    @Test
    public void unfavoritingCreatorPrunesExistingInboxEntries() {
        NativeContentItem favorite = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                "Emily Rinaudo",
                "https://fapello.com/emily-rinaudo/",
                "",
                "",
                "",
                "",
                "Fapello",
                "Emily Rinaudo"
        );
        CreatorFavoriteStore.toggle(app, favorite);

        NativeContentItem fapello = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "OnlyFap video #123",
                "https://fapello.com/video/emily-rinaudo/123/",
                "",
                "",
                "Emily Rinaudo",
                "",
                "Fapello"
        );
        UpdateInboxStore.record(app, Arrays.asList(
                new NotificationCoordinator.SourceAlert(
                        "fapello", "Fapello", Arrays.asList(fapello)
                )
        ));
        assertEquals(1, UpdateInboxStore.all(app).size());

        CreatorFavoriteStore.toggle(app, favorite);

        assertTrue(UpdateInboxStore.all(app).isEmpty());
        assertEquals(0, UpdateInboxStore.unreadCount(app));
    }

    @Test
    public void socialActivityIsDedupedAndScopedToTheSignedInAccount() throws Exception {
        ZeroChillSocialRepository.PublicProfile actor =
                new ZeroChillSocialRepository.PublicProfile(
                        new JSONObject()
                                .put("user_id", "actor-1")
                                .put("username", "reply_guy")
                                .put("display_name", "Reply Guy")
                                .put("avatar_path", ""),
                        false
                );
        ZeroChillSocialRepository.SocialActivity like =
                new ZeroChillSocialRepository.SocialActivity(
                        "like:comment-1:actor-1",
                        ZeroChillSocialRepository.SocialActivity.TYPE_LIKE,
                        "actor-1",
                        actor,
                        "comment-1",
                        "https://example.com/video/1",
                        "Video one",
                        "My original comment",
                        "",
                        "2026-09-30T15:00:00Z"
                );
        ZeroChillSocialRepository.SocialActivity reply =
                new ZeroChillSocialRepository.SocialActivity(
                        "reply:reply-1",
                        ZeroChillSocialRepository.SocialActivity.TYPE_REPLY,
                        "actor-1",
                        actor,
                        "reply-1",
                        "https://example.com/video/1",
                        "Video one",
                        "My original comment",
                        "This is a reply",
                        "2026-09-30T16:00:00Z"
                );

        UpdateInboxStore.recordSocialActivities(app, "account-a", Arrays.asList(like, reply));
        UpdateInboxStore.recordSocialActivities(app, "account-a", Arrays.asList(like, reply));

        List<UpdateInboxStore.Entry> mine = UpdateInboxStore.allForAccount(app, "account-a");
        assertEquals(2, mine.size());
        assertEquals(UpdateInboxStore.CATEGORY_SOCIAL, mine.get(0).category);
        assertEquals("Reply Guy replied to your comment", mine.get(0).title);
        assertEquals("reply-1", mine.get(0).commentId);
        assertTrue(UpdateInboxStore.allForAccount(app, "account-b").isEmpty());
        assertTrue(UpdateInboxStore.allForAccount(app, "").isEmpty());
    }

    @Test
    public void repeatedAppReleaseDoesNotBecomeUnreadAgain() {
        UpdateInboxStore.recordAppUpdate(app, "3.1.4", "ZeroChill v3.1.4", false);

        List<UpdateInboxStore.Entry> first = UpdateInboxStore.all(app);
        assertEquals(1, first.size());
        assertEquals(UpdateInboxStore.CATEGORY_APP, first.get(0).category);
        assertFalse(first.get(0).read);
        assertEquals(1, UpdateInboxStore.unreadCount(app));

        UpdateInboxStore.markAllRead(app);
        UpdateInboxStore.recordAppUpdate(app, "3.1.4", "ZeroChill v3.1.4", false);

        List<UpdateInboxStore.Entry> repeated = UpdateInboxStore.all(app);
        assertEquals(1, repeated.size());
        assertTrue(repeated.get(0).read);
        assertEquals(0, UpdateInboxStore.unreadCount(app));

        UpdateInboxStore.recordAppUpdate(app, "3.1.5", "ZeroChill v3.1.5", false);
        assertEquals(2, UpdateInboxStore.all(app).size());
        assertEquals(1, UpdateInboxStore.unreadCount(app));
    }
}
