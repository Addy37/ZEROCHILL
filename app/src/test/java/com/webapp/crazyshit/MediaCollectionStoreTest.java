package com.webapp.crazyshit;
import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class MediaCollectionStoreTest {
    @Test public void multiMembershipAndDeletionPreserveFavorite() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = OnlyFapVideoMetadata.basic("Example", "https://example.com/video/one", "");
        String first = MediaCollectionStore.create(context, "First");
        String second = MediaCollectionStore.create(context, "Second");
        MediaCollectionStore.assign(context, item, "", new HashSet<>(Arrays.asList(first, second)));
        assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        assertEquals(1, MediaCollectionStore.items(context, first).size());
        assertEquals(1, MediaCollectionStore.items(context, second).size());
        MediaCollectionStore.delete(context, first);
        assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        MediaCollectionStore.rename(context, second, "Renamed");
        assertEquals("Renamed", MediaCollectionStore.find(context, second).name);
        FavoriteStore.setMediaFavorite(context, item, false);
        assertTrue(MediaCollectionStore.find(context, second).urls.isEmpty());
    }
    @Test public void membershipRemovalDoesNotUnfavorite() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = OnlyFapVideoMetadata.basic("Example", "https://example.com/video/two", "");
        String id = MediaCollectionStore.create(context, "Keep");
        MediaCollectionStore.assign(context, item, "", Collections.singleton(id));
        MediaCollectionStore.remove(context, id, item.url);
        assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        assertTrue(MediaCollectionStore.items(context, id).isEmpty());
    }
    @Test public void backupMergePreservesLocalAndImportsMembership() throws Exception {
        String id = UUID.randomUUID().toString();
        String first = "https://example.com/one", second = "https://example.com/two";
        MediaCollectionStore.Group local = new MediaCollectionStore.Group(id, "Local", Collections.singleton(first));
        MediaCollectionStore.Group remote = new MediaCollectionStore.Group(id, "Other", Collections.singleton(second));
        List<MediaCollectionStore.Group> groups = MediaCollectionStore.decode(MediaCollectionStore.merge(
                MediaCollectionStore.encode(Collections.singletonList(remote)),
                Collections.singletonList(local), new HashSet<>(Arrays.asList(first, second))).toString());
        assertEquals(1, groups.size());
        assertEquals("Local", groups.get(0).name);
        assertEquals(2, groups.get(0).urls.size());
    }
}
