package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Application;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = BunkrResolutionTest.Wire.class)
public class BunkrResolutionTest {
    static final String PAGE = "https://bunkr.cr/f/sample";
    static final String CDN = "https://c3mb-b.cdn.cr/storage/media/movie%20name.mp4?quality=original";

    @Implements(value = Jsoup.class, isInAndroidSdk = false)
    public static class Wire {
        static String html;
        static boolean signFails, legacyWorks;
        static String signRequest;
        static Map<String, String> signHeaders;
        @Implementation protected static Connection connect(String url) {
            Map<String, String> headers = new LinkedHashMap<>();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("get".equals(method.getName())) return Jsoup.parse(html, url);
                        if ("header".equals(method.getName()) && args.length == 2)
                            headers.put((String) args[0], (String) args[1]);
                        if ("execute".equals(method.getName())) {
                            final String response;
                            if (url.contains("/sign?")) {
                                signRequest = url;
                                signHeaders = headers;
                                if (signFails) throw new IOException("sign unavailable");
                                response = "{\"token\":\"a+b/=\",\"ex\":\"2000000000\"}";
                            } else if (url.contains("/api/_001_v2") && legacyWorks) {
                                response = "{\"url\":\"https://legacy.cdn.cr/old.mp4\"}";
                            } else throw new IOException("legacy unavailable");
                            return Proxy.newProxyInstance(Connection.Response.class.getClassLoader(),
                                    new Class<?>[]{Connection.Response.class}, (r, m, a) ->
                                            "body".equals(m.getName()) ? response : null);
                        }
                        if (method.getReturnType().isInstance(proxy)) return proxy;
                        return null;
                    });
        }
    }

    @Before public void reset() {
        Wire.signFails = false;
        Wire.legacyWorks = false;
        Wire.signHeaders = new LinkedHashMap<>();
        Wire.html = "<h1>Example.mp4</h1><div data-file-id='123'></div><script>"
                + "var videoCoverUrl='https://static.scdn.st/thumbs/movie.mp4_grid.png';"
                + "var jsCDN='" + CDN + "';var signUrl='https://glb-apisign.cdn.cr/sign';</script>";
    }

    @Test public void resolvesModernPageWithEncodedSigningPathAndRetainedCdnQuery() throws Exception {
        CrazyShitRepository.StreamInfo stream = PlayableSourceRouter.resolve(null, PAGE);
        assertEquals(CDN + "&token=a%2Bb%2F%3D&ex=2000000000", stream.mediaUrl);
        assertEquals("/storage/media/movie name.mp4",
                android.net.Uri.parse(Wire.signRequest).getQueryParameter("path"));
        assertEquals(PAGE, stream.requestReferer);
        assertEquals("https://bunkr.cr", Wire.signHeaders.get("Origin"));
        assertFalse(Wire.signHeaders.containsKey("Cookie"));
    }

    @Test public void legacyDownloadApiSurvivesSignerFailure() throws Exception {
        Wire.signFails = true;
        Wire.legacyWorks = true;
        CrazyShitRepository.StreamInfo stream = new BunkrRepository().resolvePlayable(null, PAGE);
        assertEquals("https://legacy.cdn.cr/old.mp4", stream.mediaUrl);
        assertEquals("https://dl.bunkr.cr/file/123", stream.requestReferer);
    }

    @Test public void signerFailureCannotTurnGridArtworkOrUnsignedCdnIntoPlayable() {
        Wire.signFails = true;
        assertThrows(IOException.class, () -> new BunkrRepository().resolvePlayable(null, PAGE));
    }

    @Test public void legacyEmbeddedVideoStillWorks() throws Exception {
        Wire.html = "<h1>Old movie</h1><video src='https://legacy.cdn.cr/movie.mp4'></video>";
        assertEquals("https://legacy.cdn.cr/movie.mp4",
                new BunkrRepository().resolvePlayable(null, PAGE).mediaUrl);
    }

    @Test public void cachedSignedStreamsExpireBeforeReuseButLegacyAndOtherSourcesRemain() {
        String signed = "https://media.cdn.cr/movie.mp4?token=abc&ex=2000000000";
        assertTrue(BunkrRepository.usableCachedPlayable(PAGE, signed, 1999999900L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed, 1999999990L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed, 2000000001L));
        assertFalse(BunkrRepository.usableCachedPlayable(PAGE, signed.replace("2000000000", "bad"), 1));
        assertTrue(BunkrRepository.usableCachedPlayable(PAGE, "https://legacy.cdn.cr/movie.mp4", 2000000001L));
        assertTrue(BunkrRepository.usableCachedPlayable("https://example.com/file", signed, 2000000001L));
    }
}
