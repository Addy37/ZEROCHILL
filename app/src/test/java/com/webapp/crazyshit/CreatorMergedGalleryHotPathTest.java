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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class CreatorMergedGalleryHotPathTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before
    public void clearState() throws Exception {
        context.getSharedPreferences("onlyfap_hot_gallery_sessions", 0).edit().clear().commit();

        Field sessions = BunkrGallerySessionStore.class.getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        ((Map<?, ?>) sessions.get(null)).clear();

        Field preloadSessions = CreatorGalleryPreloader.class.getDeclaredField("SESSIONS");
        preloadSessions.setAccessible(true);
        ((Map<?, ?>) preloadSessions.get(null)).clear();
    }

    private NativeContentItem creator(String name, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                url,
                "",
                "",
                url,
                "",
                "",
                name
        );
    }

    private NativeContentItem media(String title, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_IMAGE,
                title,
                url,
                url,
                "",
                "",
                "",
                "",
                ""
        );
    }

    @Test
    public void mergedSessionComposesHotMemberSnapshotsBeforeNetwork() throws Exception {
        NativeContentItem anna = creator("Anna", "https://fapello.com/anna/");
        NativeContentItem ana = creator("Ana", "https://fapello.com/ana/");

        String annaSession = BunkrGallerySessionStore.createCreator(
                "Anna", BunkrRepository.searchUrl("Anna"), "Anna");
        NativeContentItem annaMedia = media("Anna 1", "https://cdn.example.com/anna-1.jpg");
        BunkrGallerySessionStore.appendPreview(annaSession, Arrays.asList(annaMedia));
        BunkrGallerySessionStore.recordCreatorBatch(
                context,
                annaSession,
                java.util.Collections.emptyList(),
                false,
                new JSONObject().put("query", "Anna")
        );

        String anaSession = BunkrGallerySessionStore.createCreator(
                "Ana", BunkrRepository.searchUrl("Ana"), "Ana");
        NativeContentItem anaMedia = media("Ana 1", "https://cdn.example.com/ana-1.jpg");
        BunkrGallerySessionStore.appendPreview(anaSession, Arrays.asList(anaMedia));
        BunkrGallerySessionStore.recordCreatorBatch(
                context,
                anaSession,
                java.util.Collections.emptyList(),
                false,
                new JSONObject().put("query", "Ana")
        );

        LinkedHashMap<String, NativeContentItem> members = new LinkedHashMap<>();
        members.put("anna", anna);
        members.put("ana", ana);
        CreatorCatalog.FavoriteGroup group = new CreatorCatalog.FavoriteGroup(
                anna,
                members,
                new LinkedHashSet<>(members.keySet()),
                true
        );
        CreatorGallerySpec spec = CreatorGallerySpec.from(group);

        String mergedSession =
                CreatorGalleryPreloader.composeInMemoryMergedSession(context, spec);
        assertFalse(mergedSession.isEmpty());

        BunkrGallerySessionStore.Snapshot snapshot =
                BunkrGallerySessionStore.snapshot(mergedSession);
        assertNotNull(snapshot);
        assertEquals(2, snapshot.items.size());
        assertTrue(snapshot.items.stream().anyMatch(
                item -> annaMedia.url.equals(item.url)));
        assertTrue(snapshot.items.stream().anyMatch(
                item -> anaMedia.url.equals(item.url)));

        BunkrGallerySessionStore.recordCreatorBatch(
                context,
                mergedSession,
                java.util.Collections.emptyList(),
                false,
                new JSONObject().put("query", spec.query)
        );
        assertEquals(
                mergedSession,
                CreatorGalleryPreloader.sessionId(context, spec.cacheKey)
        );
    }
}
