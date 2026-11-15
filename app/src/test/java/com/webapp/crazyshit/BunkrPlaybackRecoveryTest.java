package com.webapp.crazyshit;

import android.app.Application;
import android.net.Uri;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrPlaybackRecoveryTest {
    private static final String PAGE = "https://bunkr.cr/f/sample";
    private static final long NOW = 1_700_000_000L;

    @Test public void bunkrAuthenticationAndRateLimitDenialsDoNotRefresh() {
        String expired = signed(NOW - 1);
        for (int status : new int[]{401, 429}) {
            assertFalse(PlaybackRecovery.permitsRefresh(PAGE, expired, httpError(status), NOW, 0));
        }
    }

    @Test public void expiredBunkrSignatureAllowsExactlyOneRefreshAfterForbidden() {
        String expired = signed(NOW - 1);
        assertTrue(PlaybackRecovery.permitsRefresh(PAGE, expired, httpError(403), NOW, 0));
        assertFalse(PlaybackRecovery.permitsRefresh(PAGE, expired, httpError(403), NOW, 1));
    }

    @Test public void freshOrUnverifiableBunkrSignatureDoesNotRefreshAfterForbidden() {
        for (String url : new String[]{signed(NOW + 1), signed(-1), signed(0),
                "https://cdn.example/video.mp4?token=abc&ex=oops",
                "https://cdn.example/video.mp4?token=abc",
                "https://cdn.example/video.mp4?ex=" + (NOW - 1),
                "https://cdn.example/video.mp4"}) {
            assertFalse(url, PlaybackRecovery.permitsRefresh(PAGE, url, httpError(403), NOW, 0));
        }
    }

    @Test public void otherProvidersAndTransientBunkrErrorsKeepExistingRecovery() {
        for (int status : new int[]{401, 403, 429}) {
            assertTrue(PlaybackRecovery.permitsRefresh("https://example.com/video",
                    signed(NOW + 60), httpError(status), NOW, 1));
        }
        assertTrue(PlaybackRecovery.permitsRefresh(PAGE, signed(NOW + 60),
                httpError(503), NOW, 1));
        assertTrue(PlaybackRecovery.permitsRefresh(PAGE, signed(NOW + 60),
                new PlaybackException("network", new java.io.IOException("timeout"),
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), NOW, 1));
    }

    private static String signed(long expiry) {
        return "https://cdn.example/video.mp4?token=abc&ex=" + expiry;
    }

    private static PlaybackException httpError(int status) {
        HttpDataSource.InvalidResponseCodeException response =
                new HttpDataSource.InvalidResponseCodeException(status, "Denied", null,
                        Collections.emptyMap(),
                        new DataSpec(Uri.parse("https://cdn.example/video.mp4")), new byte[0]);
        return new PlaybackException("HTTP " + status, new java.io.IOException("outer", response),
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS);
    }
}
