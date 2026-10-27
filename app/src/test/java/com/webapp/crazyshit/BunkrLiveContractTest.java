package com.webapp.crazyshit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Opt-in read-only contract for the exact device reproduction. Never downloads a video body. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrLiveContractTest {
    @Test public void recordedFileResolvesToRealMp4BytesAndItsAlbumHasRasterArtwork() throws Exception {
        assumeTrue("true".equals(System.getenv("BUNKR_LIVE_CONTRACT")));
        BunkrRepository repository = new BunkrRepository();
        CrazyShitRepository.StreamInfo stream = PlayableSourceRouter.resolve(
                RuntimeEnvironment.getApplication(), "https://bunkr.cr/f/Fuyd9dOX3ZbhP");
        HttpURLConnection connection = (HttpURLConnection) new URL(stream.mediaUrl).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Range", "bytes=0-31");
        connection.setRequestProperty("Referer", stream.requestReferer);
        connection.setRequestProperty("Origin", "https://bunkr.cr");
        try {
            assertEquals(206, connection.getResponseCode());
            byte[] head = connection.getInputStream().readNBytes(32);
            assertEquals(32, head.length);
            assertEquals("ftyp", new String(head, 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
        } finally { connection.disconnect(); }
        List<NativeContentItem> album = repository.fetchAlbum(RuntimeEnvironment.getApplication(),
                "https://bunkr.cr/a/WlA5Bbyp", 1);
        assertFalse(album.isEmpty());
        for (NativeContentItem file : album) assertTrue(BunkrRepository.isUsableArtwork(file.imageUrl));
        System.out.println("Bunkr recorded file: signed resolution and MP4 range passed; album raster artwork passed.");
    }
}
