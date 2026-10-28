package com.webapp.crazyshit;

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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** HTTP-200 unavailable pages must fall through to the configured page origins. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = BunkrArtworkFallbackTest.Wire.class)
public class BunkrArtworkFallbackTest {
    private static final String ART = "https://static.scdn.st/thumbs/example.png";

    @Implements(value = Jsoup.class, isInAndroidSdk = false)
    public static class Wire {
        static final List<String> requests = new ArrayList<>();
        static boolean unavailable;
        @Implementation protected static Connection connect(String url) {
            requests.add(url);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("get".equals(method.getName())) {
                            String html = unavailable && url.startsWith("https://bunkr.cr/")
                                    ? "<html><head><title>Site Unavailable</title></head><body>"
                                        + "<h1>Site Unavailable</h1><p>Unable to access this site.</p></body></html>"
                                    : "<meta property='og:image' content='" + ART + "'>";
                            return Jsoup.parse(html, url);
                        }
                        if (method.getReturnType().isInstance(proxy)) return proxy;
                        return null;
                    });
        }
    }

    @Before public void reset() {
        Wire.requests.clear();
        Wire.unavailable = false;
    }

    @Test public void unavailableCanonicalHostFallsThroughForArtworkRepair() throws Exception {
        Wire.unavailable = true;
        assertEquals(ART, new BunkrRepository().fetchFileArtwork(null,
                "https://bunkr.cr/f/example", ""));
        assertEquals("https://bunkr.cr/f/example", Wire.requests.get(0));
        assertTrue(Wire.requests.size() > 1);
        assertFalse(Wire.requests.get(1).startsWith("https://bunkr.cr/"));
    }

    @Test public void workingCanonicalHostKeepsItsArtworkWithoutFallback() throws Exception {
        assertEquals(ART, new BunkrRepository().fetchFileArtwork(null,
                "https://bunkr.cr/f/example", ""));
        assertEquals(1, Wire.requests.size());
    }
}
