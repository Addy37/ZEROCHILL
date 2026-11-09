package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NotificationNavigationWorkTest {
    private static final class Reads extends ContextWrapper {
        int favoriteReads;
        Reads(Context context) { super(context); }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            if (name.equals("creator_favorites")) favoriteReads++;
            return super.getSharedPreferences(name, mode);
        }
    }

    @Test public void populatedInboxUsesOneFavoriteSnapshotRatherThanOnePerEntry() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        Set<String> favorites = new HashSet<>();
        List<NativeContentItem> catalog = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String name = "Creator " + i;
            if (i < 80) favorites.add(name.toLowerCase(java.util.Locale.US));
            catalog.add(new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                    "https://example.test/creator/" + i, "", "", "", "", "", name));
        }
        app.getSharedPreferences("creator_favorites", 0).edit().putStringSet("creators", favorites).commit();
        app.getSharedPreferences("creator_catalog_v1", 0).edit()
                .putString("items", ContentItemCodec.encodeList(catalog, 1000).toString()).commit();
        JSONArray encoded = new JSONArray();
        for (int i = 0; i < 200; i++) {
            UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
            entry.id = "entry-" + i; entry.fingerprint = entry.id;
            entry.category = UpdateInboxStore.CATEGORY_ONLYFAP;
            entry.creatorName = "Creator " + (i % 80);
            entry.title = entry.creatorName;
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Item " + i, "https://example.test/item/" + i, "", "", entry.creatorName, "");
            entry.children.add(new UpdateInboxStore.CreatorItem("fapello", entry.creatorName, item, i + 1, false));
            entry.refreshCreatorState();
            encoded.put(entry.encode());
        }
        app.getSharedPreferences("zerochill_update_inbox_v1", 0).edit()
                .putString("entries", encoded.toString()).commit();
        Reads reads = new Reads(app);
        long start = System.nanoTime();
        List<UpdateInboxStore.Entry> grouped = UpdateInboxStore.allForAccount(reads, "");
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("INBOX_PROBE 200 entries/80 favorites/1000 catalog: " + elapsed
                + " ms, favorite preference reads=" + reads.favoriteReads + ", groups=" + grouped.size());
        assertEquals(80, grouped.size());
        assertEquals(200, grouped.stream().mapToInt(e -> e.children.size()).sum());
        assertTrue("Favorite inputs must be snapshotted once per operation, not once per notification",
                reads.favoriteReads <= 3);
    }
}
