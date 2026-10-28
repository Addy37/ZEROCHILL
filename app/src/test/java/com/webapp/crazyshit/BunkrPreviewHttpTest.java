package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrPreviewHttpTest {
    @Test public void validatesRangeAndRetainsConfiguredHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> range = new AtomicReference<>();
        AtomicReference<String> referer = new AtomicReference<>();
        server.createContext("/video", exchange -> {
            range.set(exchange.getRequestHeaders().getFirst("Range"));
            referer.set(exchange.getRequestHeaders().getFirst("Referer"));
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.getResponseHeaders().set("Content-Range", "bytes 8-11/12");
            exchange.sendResponseHeaders(206, 4);
            exchange.getResponseBody().write(new byte[] {8, 9, 10, 11});
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/video";
            BunkrPreviewDataSource.Block block = new BunkrMediaPreview.Task().range(url,
                    Collections.singletonMap("Referer", "https://bunkr.cr/f/example"), 8, 8);
            assertEquals(12, block.total); assertArrayEquals(new byte[] {8, 9, 10, 11}, block.bytes);
            assertEquals("bytes=8-15", range.get());
            assertEquals("https://bunkr.cr/f/example", referer.get());
        } finally { server.stop(0); }
    }

    @Test public void refusesFullVideoDownloadHtmlAndIncorrectRanges() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ignored-range", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, 1_000_000);
            exchange.close();
        });
        server.createContext("/html", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 4);
            exchange.getResponseBody().write(new byte[] {1, 2, 3, 4}); exchange.close();
        });
        server.createContext("/wrong-range", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.getResponseHeaders().set("Content-Range", "bytes 0-3/100");
            exchange.sendResponseHeaders(206, 4);
            exchange.getResponseBody().write(new byte[] {1, 2, 3, 4}); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            for (String path : new String[] {"/ignored-range", "/html", "/wrong-range"}) {
                assertThrows(path, IOException.class, () -> new BunkrMediaPreview.Task()
                        .range(base + path, Collections.emptyMap(), 0, 8));
            }
            assertThrows(IOException.class, () -> new BunkrMediaPreview.Task()
                    .image(base + "/html", Collections.emptyMap()));
        } finally { server.stop(0); }
    }
}
