package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrAlbumShowsTest {
    private static final String ALBUM = "https://bunkr.cr/a/collection";

    @Test public void filenameTitlesAreCleanedOnlyForDisplayAndNumbersStayIntact() {
        NativeContentItem video = file("media", "My_Trip.S01E03_7.5_1080p.MP4", "title");
        assertEquals("My Trip S01E03 7.5 1080p", BunkrAlbumTitles.display(video));
        assertEquals("My_Trip.S01E03_7.5_1080p.MP4", video.title);
        assertEquals("Dr. Smith", BunkrAlbumTitles.display(file("media", "Dr. Smith", "prose")));
        NativeContentItem unrelated = new NativeContentItem("media", "Keep_this.mp4",
                "https://example.test/video", "", "", "", "");
        assertEquals(unrelated.title, BunkrAlbumTitles.display(unrelated));
        NativeContentItem image = file("image", "Keep_this.jpg", "photo-title");
        assertEquals(image.title, BunkrAlbumTitles.display(image));
        assertEquals("S01E03", BunkrAlbumTitles.display(file("media", "S01E03.webm", "episode")));
        NativeFeedBrowserActivity browser = browser();
        ReflectionHelpers.callInstanceMethod(browser, "replaceBunkrItems",
                ReflectionHelpers.ClassParameter.from(List.class, java.util.Collections.singletonList(video)));
        NativeFeedAdapter adapter = ReflectionHelpers.getField(browser, "adapter");
        RecyclerView recycler = ReflectionHelpers.getField(browser, "recycler");
        NativeFeedAdapter.Holder holder = adapter.onCreateViewHolder(recycler, NativeFeedAdapter.VIEW_LIST);
        adapter.onBindViewHolder(holder, 0);
        assertEquals("My Trip S01E03 7.5 1080p", holder.title.getText().toString());
        assertEquals("My_Trip.S01E03_7.5_1080p.MP4", video.title);
        adapter.close();
    }

    @Test public void originalFilenameRemainsAvailableAndCopyable() {
        NativeFeedBrowserActivity browser = browser();
        NativeContentItem video = file("media", "Original_File.Name.mp4", "original");
        BunkrAlbumTitles.showOriginal(browser, video);
        android.app.AlertDialog dialog = (android.app.AlertDialog)
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(video.title, message.getText().toString());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                browser.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        assertEquals(video.title, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        ((NativeFeedAdapter) ReflectionHelpers.getField(browser, "adapter")).close();
    }

    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class AlbumPages {
        static NativeContentItem video, image;
        @Implementation protected List<NativeContentItem> fetchAlbum(android.content.Context context,
                String url, int page) throws java.io.IOException {
            if (page == 2) return java.util.Collections.singletonList(video);
            if (page == 3) return java.util.Collections.singletonList(image);
            throw new java.io.IOException("End of fixture");
        }
    }

    private NativeContentItem file(String kind, String title, String id) {
        return new NativeContentItem(kind, title, "https://bunkr.cr/" + (kind.equals("image") ? "i/" : "f/") + id,
                "", "", "Bunkr", "");
    }

    private NativeFeedBrowserActivity browser() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent()).get();
        browser.setTheme(R.style.Theme_CrazyShit);
        for (String name : new String[]{"creatorQuery", "fapelloProfileUrl", "showImageUrl", "showDescription"})
            ReflectionHelpers.setField(browser, name, "");
        ReflectionHelpers.setField(browser, "title", "Album");
        ReflectionHelpers.setField(browser, "baseUrl", ALBUM);
        ReflectionHelpers.setField(browser, "source", NativeFeedBrowserActivity.SOURCE_BUNKR);
        ReflectionHelpers.setField(browser, "showKind", NativeContentItem.KIND_SERIES);
        ReflectionHelpers.setField(browser, "showDetailsMode", true);
        ReflectionHelpers.callInstanceMethod(browser, "buildUi");
        return browser;
    }

    @Test public void albumReusesShowsRangesAndPicturesStayOutOfEpisodes() {
        NativeFeedBrowserActivity browser = browser();
        NativeContentItem image = file("image", "Picture", "photo");
        List<NativeContentItem> files = new ArrayList<>();
        files.add(image);
        for (int i = 0; i < 24; i++) files.add(file("media", "Video " + i, "v" + i));
        files.add(files.get(1));
        ReflectionHelpers.callInstanceMethod(browser, "replaceBunkrItems",
                ReflectionHelpers.ClassParameter.from(List.class, files));
        ReflectionHelpers.callInstanceMethod(browser, "appendBunkrItems",
                ReflectionHelpers.ClassParameter.from(List.class, files));
        List<NativeContentItem> episodes = ReflectionHelpers.getField(browser, "showEpisodes");
        List<NativeContentItem> all = ReflectionHelpers.getField(browser, "bunkrAlbumItems");
        assertEquals(25, all.size());
        assertEquals(24, episodes.size());
        assertTrue(episodes.stream().allMatch(NativeContentItem::isVideo));
        assertEquals(files.get(1).url, episodes.get(0).url);
        NativeFeedAdapter adapter = ReflectionHelpers.getField(browser, "adapter");
        assertEquals(10, adapter.getItemCount());
        RecyclerView recycler = ReflectionHelpers.getField(browser, "recycler");
        assertTrue(recycler.getAdapter() instanceof ConcatAdapter);
        ReflectionHelpers.callInstanceMethod(browser, "applyEpisodeRange",
                ReflectionHelpers.ClassParameter.from(boolean.class, false),
                ReflectionHelpers.ClassParameter.from(int.class, 22));
        assertEquals(4, adapter.getItemCount());
        TextView pictures = ReflectionHelpers.getField(browser, "albumImagesAction");
        assertEquals(View.VISIBLE, pictures.getVisibility());
        assertEquals("Pictures · 1", pictures.getText().toString());
        pictures.performClick();
        Intent viewer = shadowOf(browser).getNextStartedActivity();
        assertEquals(BunkrGalleryActivity.class.getName(), viewer.getComponent().getClassName());
        assertEquals(BunkrGalleryActivity.FILTER_PICTURES,
                viewer.getStringExtra(BunkrGalleryActivity.EXTRA_MEDIA_FILTER));
        assertEquals(image.url, viewer.getStringExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL));
        adapter.close();
    }

    @Test public void orderedAlbumSessionCrossesTheCompactIntentLimitAndNeverPlaysImages() {
        String id = BunkrGallerySessionStore.create("Album", ALBUM);
        List<NativeContentItem> files = new ArrayList<>();
        for (int i = 0; i < 350; i++) files.add(file("media", "Episode " + (350 - i), "v" + i));
        NativeContentItem image = file("image", "Episode 1", "photo");
        files.add(201, image);
        BunkrGallerySessionStore.replace(id, files, 4, false);
        Intent intent = ShowsWatchSession.attach(new Intent()
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, files.get(199).url), files, true, "Album");
        ShowsWatchSession.attachAlbum(intent, id);
        ShowsWatchSession session = ShowsWatchSession.from(intent);
        assertEquals(350, session.items.size());
        assertEquals(files.get(200).url, session.next(files.get(199).url).url);
        assertEquals(files.get(202).url, session.next(files.get(200).url).url);
        assertNull(session.next(files.get(files.size() - 1).url));
        NativeContentItem next = file("media", "First in next page", "next");
        BunkrGallerySessionStore.append(id, Arrays.asList(image, next, next), 5, true);
        session = session.withAlbum(BunkrGallerySessionStore.snapshot(id));
        assertEquals(next.url, session.next(files.get(files.size() - 1).url).url);
        assertEquals(351, session.items.size());
    }

    @Test public void creatorGalleryDoesNotBecomeAnAlbumCollection() {
        NativeFeedBrowserActivity browser = browser();
        ReflectionHelpers.setField(browser, "creatorQuery", "Creator");
        assertFalse((boolean) ReflectionHelpers.callInstanceMethod(browser, "isBunkrAlbum"));
        ((NativeFeedAdapter) ReflectionHelpers.getField(browser, "adapter")).close();
    }

    @Test @Config(shadows = AlbumPages.class)
    public void picturesViewerContinuesAcrossVideoOnlyPageWithoutEndingSharedAlbum() throws Exception {
        NativeContentItem first = file("image", "First picture", "first");
        AlbumPages.video = file("media", "Video on next page", "video");
        AlbumPages.image = file("image", "Next picture", "second");
        String id = BunkrGallerySessionStore.create("Album", ALBUM);
        BunkrGallerySessionStore.replace(id, java.util.Collections.singletonList(first), 1, true);
        BunkrGallerySessionStore.setResolvedUrl(id, first.url, "https://example.test/first.jpg");
        BunkrGallerySessionStore.setResolvedUrl(id, AlbumPages.image.url, "https://example.test/second.jpg");
        Intent intent = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, id)
                .putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, ALBUM)
                .putExtra(BunkrGalleryActivity.EXTRA_MEDIA_FILTER, BunkrGalleryActivity.FILTER_PICTURES);
        org.robolectric.android.controller.ActivityController<BunkrGalleryActivity> controller =
                Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create();
        BunkrGalleryActivity activity = controller.get();
        java.util.concurrent.ExecutorService worker = ReflectionHelpers.getField(activity, "pageIo");
        try {
            ReflectionHelpers.setField(activity, "endReached", false);
            BunkrGallerySessionStore.updatePaging(id, 1, false);
            ReflectionHelpers.callInstanceMethod(activity, "loadMore");
            for (int i = 0; i < 3; i++) {
                worker.submit(() -> {}).get(3, java.util.concurrent.TimeUnit.SECONDS);
                shadowOf(android.os.Looper.getMainLooper()).idle();
                // This fixture creates the viewer without attaching its pager to a window.
                // Drive the next near-end request that the attached pager normally posts.
                ReflectionHelpers.callInstanceMethod(activity, "loadMore");
            }
            BunkrGallerySessionStore.Snapshot album = BunkrGallerySessionStore.snapshot(id);
            assertTrue(album.items.stream().anyMatch(item -> item.url.equals(AlbumPages.video.url)));
            assertTrue(album.items.stream().anyMatch(item -> item.url.equals(AlbumPages.image.url)));
            BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
            assertEquals(2, adapter.getItemCount());
            assertTrue(adapter.itemAt(1).isImage());
        } finally { controller.destroy(); }
    }
}
