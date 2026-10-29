package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapGalleryFavoritesTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private NativeContentItem item(String id, boolean image, float ratio) {
        return new NativeContentItem(image ? NativeContentItem.KIND_IMAGE : NativeContentItem.KIND_MEDIA,
                "Creator " + id, "https://fapello.com/creator/" + id + "/", "", "",
                "https://fapello.com/creator/", "", "OnlyFap", "Creator", 0, ratio);
    }
    private BunkrGalleryAdapter adapter() {
        return new BunkrGalleryAdapter(context, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
        }, true);
    }
    @Before public void clear() {
        context.getSharedPreferences("watch_later", 0).edit().clear().commit();
    }
    @Test public void imageFavoritePersistsKindCreatorRatioAndGalleryWithoutDownloading() {
        NativeContentItem image = item("1", true, 2.8f);
        FavoriteStore.setMediaFavorite(context, image, true, "merged:v2:fixture");
        FavoriteStore.Item saved = FavoriteStore.mediaFavorites(context).get(0);
        assertTrue(saved.content.isImage());
        assertEquals(image.uploader, saved.content.uploader);
        assertEquals(2.8f, saved.content.aspectRatioHint, .001f);
        assertEquals("merged:v2:fixture", saved.galleryKey);
        assertTrue(FavoriteStore.watchLater(context).isEmpty());
        assertFalse(FavoriteStore.contains(context, image.url));
        // Reads come from serialized SharedPreferences, not retained adapter state.
        assertEquals(image.url, FavoriteStore.load(context).get(0).url);
        FavoriteStore.setMediaFavorite(context, image, false);
        assertTrue(FavoriteStore.load(context).isEmpty());
    }
    @Test public void watchLaterAndFavoriteMembershipsRemainIndependent() {
        NativeContentItem video = item("2", false, .45f);
        FavoriteStore.add(context, video);
        FavoriteStore.setMediaFavorite(context, video, true, "Creator");
        FavoriteStore.setMediaFavorite(context, video, false);
        assertTrue(FavoriteStore.contains(context, video.url));
        assertTrue(FavoriteStore.mediaFavorites(context).isEmpty());
        FavoriteStore.setMediaFavorite(context, video, true);
        FavoriteStore.clear(context);
        assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        assertTrue(FavoriteStore.watchLater(context).isEmpty());
    }
    @Test public void legacyItemsRemainWatchLaterAndBackupRoundTripsFavorites() throws Exception {
        context.getSharedPreferences("watch_later", 0).edit().putString("items",
                "[{\"title\":\"Older video\",\"url\":\"https://example.com/video\",\"savedAt\":1}]").commit();
        assertEquals(1, FavoriteStore.watchLater(context).size());
        FavoriteStore.setMediaFavorite(context, item("3", true, 3f), true, "Creator");
        JSONObject backup = AppBackupStore.export(context);
        BackupDocument.validate(backup);
        context.getSharedPreferences("watch_later", 0).edit().clear().commit();
        AppBackupStore.restore(context, backup);
        assertEquals(1, FavoriteStore.mediaFavorites(context).size());
        assertEquals(1, FavoriteStore.watchLater(context).size());
        assertTrue(FavoriteStore.mediaFavorites(context).get(0).content.isImage());
    }
    @Test public void pinningIsStableAndUnfavoriteRestoresSourceOrderIncludingLaterPages() {
        BunkrGalleryAdapter adapter = adapter();
        NativeContentItem a = item("a", true, 1), b = item("b", false, 1), c = item("c", true, 1);
        adapter.replace(Arrays.asList(a, b), false);
        adapter.setFavoriteUrls(Collections.singleton(b.url));
        assertEquals(Arrays.asList(b, a), adapter.snapshot());
        adapter.append(Collections.singletonList(c), false);
        adapter.setFavoriteUrls(Collections.emptySet());
        assertEquals(Arrays.asList(a, b, c), adapter.snapshot());
        adapter.setFavoriteUrls(Collections.singleton(c.url));
        adapter.replace(Collections.singletonList(a), false);
        adapter.appendPinned(Collections.singletonList(c));
        assertEquals(Arrays.asList(c, a), adapter.snapshot());
        adapter.append(Arrays.asList(b, c), false);
        adapter.setFavoriteUrls(Collections.emptySet());
        assertEquals(Arrays.asList(a, b, c), adapter.snapshot());
    }
    @Test public void naturalRatioHintsAreNotClampedToPortraitOrLandscapePresets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        BunkrGalleryAdapter adapter = adapter();
        FrameLayout parent = new FrameLayout(activity);
        for (float ratio : new float[]{.3f, 1f, 3f}) {
            adapter.replace(Collections.singletonList(item("ratio", true, ratio)), false);
            BunkrGalleryAdapter.Holder holder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(holder, 0);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertEquals(Math.round(300 / ratio), holder.itemView.getMeasuredHeight());
            adapter.onViewRecycled(holder);
        }
    }
    @Test public void longPressOnlyOpensMenuAndDownloadRequiresItsOwnRowTap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout source = new FrameLayout(activity);
        View anchor = new View(activity); source.addView(anchor);
        activity.setContentView(source);
        int[] actions = new int[5];
        Runnable[] callbacks = new Runnable[5];
        for (int i = 0; i < 5; i++) { final int index = i; callbacks[i] = () -> actions[index]++; }
        GalleryContextMenu menu = new GalleryContextMenu(activity, anchor, source, false, callbacks, () -> { });
        for (int count : actions) assertEquals(0, count);
        TextView download = find(menu, "Download");
        assertNotNull(download); download.performClick();
        assertEquals(1, actions[4]); assertNull(menu.getParent());
        for (int i = 0; i < 4; i++) assertEquals(0, actions[i]);
    }
    private TextView find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { TextView row = find(group.getChildAt(i), text); if (row != null) return row; }
        }
        return null;
    }
}
