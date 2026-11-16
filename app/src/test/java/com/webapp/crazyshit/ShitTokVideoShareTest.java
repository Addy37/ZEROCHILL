package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaFormat;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaExtractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import org.json.JSONObject;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokVideoShareTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void sendsMediaStreamWithReadGrantAndClipDataWithoutWebpage() throws Exception {
        File file = temp.newFile("video.mp4");
        Uri uri = Uri.parse("content://com.addy37.crazyshitunofficial.video-share/shared_videos/video.mp4");
        Intent intent = ShitTokVideoShare.sendIntent(new ShitTokVideoShare.Result(uri, "video/mp4", "Video", file));
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("video/mp4", intent.getType());
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT));
        assertFalse((intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
    }

    @Test public void rejectsManifestsAndNonNetworkInputsWithoutGuessingExtension() {
        assertFalse(ShitTokVideoShare.supportsUrl("https://cdn.example/video.M3U8?token=abc"));
        assertFalse(ShitTokVideoShare.supportsUrl("https://cdn.example/video.mpd"));
        assertFalse(ShitTokVideoShare.supportsUrl("https://cdn.example/live/Manifest"));
        assertFalse(ShitTokVideoShare.supportsUrl("file:///private/video.mp4"));
        assertFalse(ShitTokVideoShare.supportsUrl("javascript:run()"));
        assertTrue(ShitTokVideoShare.supportsUrl("https://cdn.example/signed-token?type=mp4"));
    }

    @Test public void inspectsBytesRatherThanTrustingVideoUrlOrServerMime() {
        assertEquals("", ShitTokVideoShare.sniffMime("<html>Access denied</html>".getBytes()));
        assertEquals("", ShitTokVideoShare.sniffMime("#EXTM3U\n#EXT-X-VERSION:3".getBytes()));
        assertEquals("video/mp4", ShitTokVideoShare.sniffMime(new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m'}));
        assertEquals("", ShitTokVideoShare.sniffMime(new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3}));
        assertEquals("video/webm", ShitTokVideoShare.sniffMime(new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,'w','e','b','m'}));
    }

    @Test public void copiesActualVideoBytesAndReportsProgress() throws Exception {
        byte[] bytes = Files.readAllBytes(new File("src/test/resources/shittok-orientation/portrait.mp4").toPath());
        File directory = temp.newFolder();
        File output = new File(directory, "out.part");
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        long[] finalProgress = new long[2];
        ShitTokVideoShare.copy(input::read, output, bytes.length, directory, new ShitTokVideoShare.Job(),
                (read, total) -> { finalProgress[0] = read; finalProgress[1] = total; });
        assertArrayEquals(bytes, Files.readAllBytes(output.toPath()));
        assertArrayEquals(new long[]{bytes.length, bytes.length}, finalProgress);
    }

    @Test public void failsTruncatedTransfersInsteadOfDeliveringPartialVideo() throws Exception {
        File directory = temp.newFolder();
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1,2,3});
        IOException error = assertThrows(IOException.class, () -> ShitTokVideoShare.copy(input::read,
                new File(directory, "out.part"), 10, directory, new ShitTokVideoShare.Job(), (read,total) -> {}));
        assertTrue(error.getMessage().contains("incomplete"));
    }

    @Test public void cancellationPreventsAnyRead() throws Exception {
        File directory = temp.newFolder();
        ShitTokVideoShare.Job job = new ShitTokVideoShare.Job();
        job.cancel();
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IOException.class, () -> ShitTokVideoShare.copy((bytes,offset,length) -> {
            reads.incrementAndGet(); return -1;
        }, new File(directory, "out.part"), C.LENGTH_UNSET, directory, job, (read,total) -> {}));
        assertEquals(0, reads.get());
    }

    @Test public void rejectsOversizedFilesBeforeReadingNetwork() throws Exception {
        File directory = temp.newFolder();
        AtomicInteger reads = new AtomicInteger();
        IOException error = assertThrows(IOException.class, () -> ShitTokVideoShare.copy((bytes,offset,length) -> {
            reads.incrementAndGet(); return -1;
        }, new File(directory, "out.part"), ShitTokVideoShare.MAX_VIDEO_BYTES + 1, directory,
                new ShitTokVideoShare.Job(), (read,total) -> {}));
        assertTrue(error.getMessage().contains("100 MB"));
        assertEquals(0, reads.get());
    }

    @Test public void cleanupKeepsRecentReceiverFilesAndOnlyExpiresOldFiles() throws Exception {
        File directory = temp.newFolder();
        File recent = new File(directory, "recent.mp4");
        File old = new File(directory, "old.mp4");
        Files.write(recent.toPath(), new byte[]{1});
        Files.write(old.toPath(), new byte[]{2});
        long now = System.currentTimeMillis();
        assertTrue(recent.setLastModified(now - 1_000));
        assertTrue(old.setLastModified(now - ShitTokVideoShare.RETENTION_MS - 1_000));
        ShitTokVideoShare.cleanup(directory, now);
        assertTrue(recent.exists());
        assertFalse(old.exists());
    }

    @Test public void repeatExportReadsPlaybackCacheWithoutAnotherUpstreamTransfer() throws Exception {
        byte[] bytes = Files.readAllBytes(new File("src/test/resources/shittok-orientation/portrait.mp4").toPath());
        AtomicInteger networkBytes = new AtomicInteger();
        DataSource.Factory upstream = () -> {
            ByteArrayDataSource source = new ByteArrayDataSource(bytes);
            source.addTransferListener(new TransferListener() {
                @Override public void onTransferInitializing(DataSource s, DataSpec spec, boolean network) {}
                @Override public void onTransferStart(DataSource s, DataSpec spec, boolean network) {}
                @Override public void onTransferEnd(DataSource s, DataSpec spec, boolean network) {}
                @Override public void onBytesTransferred(DataSource s, DataSpec spec, boolean network, int count) {
                    networkBytes.addAndGet(count);
                }
            });
            return source;
        };
        StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(RuntimeEnvironment.getApplication());
        SimpleCache cache = new SimpleCache(temp.newFolder(), new NoOpCacheEvictor(), database);
        try {
            DataSource.Factory factory = new CacheDataSource.Factory().setCache(cache)
                    .setUpstreamDataSourceFactory(upstream);
            for (int pass = 0; pass < 2; pass++) {
                File directory = temp.newFolder();
                File out = new File(directory, "out.part");
                DataSource source = factory.createDataSource();
                try {
                    long total = source.open(new DataSpec(Uri.parse("https://cdn.example/video.mp4")));
                    ShitTokVideoShare.copy(source::read, out, total, directory, new ShitTokVideoShare.Job(), (a,b) -> {});
                } finally { source.close(); }
                assertArrayEquals(bytes, Files.readAllBytes(out.toPath()));
            }
            assertEquals(bytes.length, networkBytes.get());
        } finally {
            cache.release();
            database.close();
        }
    }

    @Test public void cacheKeysAreStableAndCannotInjectPaths() throws Exception {
        String key = ShitTokVideoShare.cacheKey("https://source.example/../../video?q=1");
        assertTrue(key.matches("[0-9a-f]{64}"));
        assertEquals(key, ShitTokVideoShare.cacheKey("https://source.example/../../video?q=1"));
        assertNotEquals(key, ShitTokVideoShare.cacheKey("https://source.example/other"));
    }

    @Test public void completedDownloadExportsThroughProviderAndRepeatShareReusesPreparedFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        byte[] bytes = Files.readAllBytes(new File("src/test/resources/shittok-orientation/portrait.mp4").toPath());
        File download = temp.newFile("download.mp4");
        Files.write(download.toPath(), bytes);
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://source.invalid/downloaded-share-test", "", "", "", "");
        JSONObject metadata = new JSONObject().put("id", -501).put("page", item.url)
                .put("title", item.title).put("status", DownloadManager.STATUS_SUCCESSFUL)
                .put("local", Uri.fromFile(download)).put("mime", "video/mp4");
        context.getSharedPreferences("native_video_downloads", Context.MODE_PRIVATE).edit()
                .putStringSet("ids", Collections.singleton("-501"))
                .putString("entry_-501", metadata.toString()).commit();
        File partial = new File(new File(context.getCacheDir(), "shittok_share"), ShitTokVideoShare.cacheKey(item.url) + ".part");
        // Robolectric's Android MediaExtractor has no demuxer. Register its video-track boundary;
        // actual downloaded bytes, file export and provider reads below remain real.
        ShadowMediaExtractor.addTrack(org.robolectric.shadows.util.DataSource.toDataSource(partial.getAbsolutePath()),
                MediaFormat.createVideoFormat("video/avc", 24, 40), new byte[]{1,2,3});
        CrazyShitRepository.StreamInfo unavailable = new CrazyShitRepository.StreamInfo("https://source.invalid/live.m3u8", item.url, item.title);
        ShitTokVideoShare.Result first = ShitTokVideoShare.prepareBlocking(context, item, unavailable,
                new ShitTokVideoShare.Job(), (a,b) -> {});
        assertEquals("content", first.uri.getScheme());
        assertEquals("video/mp4", first.mime);
        try (java.io.InputStream input = context.getContentResolver().openInputStream(first.uri)) {
            assertNotNull(input);
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertArrayEquals(bytes, Files.readAllBytes(download.toPath()));
        assertTrue(download.delete());
        context.getSharedPreferences("native_video_downloads", Context.MODE_PRIVATE).edit().clear().commit();
        ShitTokVideoShare.Result second = ShitTokVideoShare.prepareBlocking(context, item, unavailable,
                new ShitTokVideoShare.Job(), (a,b) -> {});
        assertEquals(first.uri, second.uri);
        assertArrayEquals(bytes, Files.readAllBytes(second.file.toPath()));
    }
}
