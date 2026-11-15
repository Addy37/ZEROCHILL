package com.webapp.crazyshit;

import android.app.Application;
import android.net.Uri;
import android.webkit.CookieManager;

import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrGalleryHttpHeadersTest {
    @Test public void bunkrPageCookieIsNotForwardedToMediaHost() throws Exception {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        String page = "https://bunkr.cr/f/sample";
        cookies.setCookie(page, "verification=page-only");
        assertNotNull(cookies.getCookie(page));
        try (Fixture media = new Fixture()) {
            assertNull(cookies.getCookie(media.url()));
            Map<String, String> headers = request(media, page);
            assertNull(headers.get("cookie"));
            assertEquals(page, headers.get("referer"));
        }
    }

    @Test public void mediaHostCookieIsStillSentForBunkrPlayback() throws Exception {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        try (Fixture media = new Fixture()) {
            cookies.setCookie(media.url(), "media_session=host-only");
            assertNotNull(cookies.getCookie(media.url()));
            Map<String, String> headers = request(media, "https://bunkr.cr/f/sample");
            assertTrue(headers.get("cookie"), headers.get("cookie").contains("media_session=host-only"));
        }
    }

    @Test public void legacyBunkrDownloadRefererCookieIsNotForwarded() throws Exception {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        String referer = "https://dl.bunkr.cr/file/123";
        assertTrue(BunkrRepository.isBunkrUrl(referer));
        cookies.setCookie(referer, "legacy_verification=page-only");
        assertNotNull(cookies.getCookie(referer));
        try (Fixture media = new Fixture()) {
            assertNull(request(media, referer).get("cookie"));
        }
    }

    @Test public void otherProviderKeepsRefererCookieFallback() throws Exception {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        String referer = "https://example.com/watch/123";
        cookies.setCookie(referer, "provider_session=original-behavior");
        assertNotNull(cookies.getCookie(referer));
        try (Fixture media = new Fixture()) {
            assertNull(cookies.getCookie(media.url()));
            Map<String, String> headers = request(media, referer);
            assertTrue(headers.get("cookie"),
                    headers.get("cookie").contains("provider_session=original-behavior"));
        }
    }

    private static Map<String, String> request(Fixture media, String referer) throws Exception {
        BunkrGalleryActivity activity = new BunkrGalleryActivity();
        DefaultHttpDataSource.Factory factory = ReflectionHelpers.callInstanceMethod(activity,
                "videoHttpFactory",
                ReflectionHelpers.ClassParameter.from(String.class, media.url()),
                ReflectionHelpers.ClassParameter.from(String.class, referer));
        DefaultHttpDataSource source = factory.createDataSource();
        try {
            source.open(new DataSpec(Uri.parse(media.url())));
            source.read(new byte[1], 0, 1);
        } finally {
            source.close();
        }
        return media.headers();
    }

    /** Captures the headers emitted by Media3 over a real bounded loopback connection. */
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile Map<String, String> headers;
        private volatile Throwable failure;

        Fixture() throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(3000);
            worker = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.US_ASCII));
                    assertNotNull(in.readLine());
                    Map<String, String> seen = new LinkedHashMap<>();
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        int colon = line.indexOf(':');
                        if (colon > 0) seen.put(line.substring(0, colon).toLowerCase(Locale.US),
                                line.substring(colon + 1).trim());
                    }
                    headers = seen;
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\n"
                            + "Content-Length: 1\r\nConnection: close\r\n\r\nx")
                            .getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (Throwable error) {
                    if (!server.isClosed()) failure = error;
                }
            }, "gallery-header-fixture");
            worker.setDaemon(true);
            worker.start();
        }

        String url() { return "http://127.0.0.1:" + server.getLocalPort() + "/video"; }

        Map<String, String> headers() {
            assertNotNull("Media3 request should reach the fixture", headers);
            return headers;
        }

        @Override public void close() throws Exception {
            server.close();
            worker.join(4000);
            assertNull(failure);
            assertFalse(worker.isAlive());
        }
    }
}
