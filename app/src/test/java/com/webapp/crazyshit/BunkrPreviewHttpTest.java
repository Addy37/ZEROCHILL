package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrPreviewHttpTest {
    @Test public void validatesRangeAndRetainsConfiguredHeaders() throws Exception {
        try (Fixture server = new Fixture()) {
            BunkrPreviewDataSource.Block block = new BunkrMediaPreview.Task().range(server.url("/video"),
                    Collections.singletonMap("Referer", "https://bunkr.cr/f/example"), 8, 8);
            assertEquals(12, block.total); assertArrayEquals(new byte[] {8, 9, 10, 11}, block.bytes);
            assertEquals("bytes=8-15", server.range);
            assertEquals("https://bunkr.cr/f/example", server.referer);
        }
    }

    @Test public void refusesFullVideoDownloadHtmlAndIncorrectRanges() throws Exception {
        try (Fixture server = new Fixture()) {
            for (String path : new String[] {"/ignored-range", "/html", "/wrong-range"}) {
                assertThrows(path, IOException.class, () -> new BunkrMediaPreview.Task()
                        .range(server.url(path), Collections.emptyMap(), 0, 8));
            }
            assertThrows(IOException.class, () -> new BunkrMediaPreview.Task()
                    .image(server.url("/html"), Collections.emptyMap()));
        }
    }

    /** Real HTTP fixture using Android/JDK-common APIs, without a new test dependency. */
    private static final class Fixture implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile Throwable failure;
        volatile String range, referer;
        Fixture() throws IOException {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(3000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                        String request = in.readLine();
                        if (request == null) continue;
                        String path = request.split(" ")[1];
                        String line;
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            if (line.toLowerCase(Locale.US).startsWith("range: ")) range = line.substring(7);
                            if (line.toLowerCase(Locale.US).startsWith("referer: ")) referer = line.substring(9);
                        }
                        boolean video = path.equals("/video");
                        boolean wrong = path.equals("/wrong-range");
                        boolean html = path.equals("/html");
                        String headers = "HTTP/1.1 " + (video || wrong ? "206 Partial Content" : "200 OK")
                                + "\r\nContent-Type: " + (html ? "text/html" : "video/mp4")
                                + "\r\nContent-Length: " + (path.equals("/ignored-range") ? 1_000_000 : 4)
                                + "\r\nConnection: close\r\n";
                        if (video) headers += "Content-Range: bytes 8-11/12\r\n";
                        if (wrong) headers += "Content-Range: bytes 0-3/100\r\n";
                        OutputStream out = socket.getOutputStream();
                        out.write((headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(new byte[] {8, 9, 10, 11}); out.flush();
                    } catch (IOException error) {
                        if (!server.isClosed()) failure = error;
                    }
                }
            }, "preview-http-fixture");
            worker.setDaemon(true); worker.start();
        }
        String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }
        @Override public void close() throws Exception {
            server.close(); worker.join(4000); assertNull(failure); assertFalse(worker.isAlive());
        }
    }
}
