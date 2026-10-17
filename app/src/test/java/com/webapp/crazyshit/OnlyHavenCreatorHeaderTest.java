package com.webapp.crazyshit;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise the real repository HTTP path against a CDN with a successful missing-image response. */
public class OnlyHavenCreatorHeaderTest {
    private ServerSocket server;
    private Thread worker;
    private String base;
    private volatile int missingStatus = 200;
    private volatile String candidate = "creator artwork";
    private volatile String missing = "site placeholder";

    @Before public void start() throws Exception {
        server = new ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"));
        worker = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String request = reader.readLine();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    boolean probe = request.contains("__zerochill_missing_creator_header__");
                    byte[] body = (probe ? missing : candidate).getBytes(StandardCharsets.UTF_8);
                    int status = probe ? missingStatus : 200;
                    String headers = "HTTP/1.1 " + status + " Test\r\nContent-Type: image/webp\r\n"
                            + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                } catch (java.io.IOException error) {
                    if (!server.isClosed()) throw new AssertionError(error);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
        base = "http://127.0.0.1:" + server.getLocalPort() + "/";
    }

    @After public void stop() throws Exception {
        server.close();
        worker.join(3000);
    }

    private String header() throws Exception {
        return new OnlyHavenRepository().creatorHeaderUrl(OnlyHavenTrendingTest.config(base),
                new OnlyHavenRepository.Creator("onlyfans", "creator", "Creator", "", "avatar"));
    }

    @Test public void genuineBannerSurvivesSuccessfulCdnPlaceholder() throws Exception {
        assertEquals(base + "creator/onlyfans/creator/header.webp", header());
    }

    @Test public void genericBannerIsAbsentEvenWithSuccessfulHttpStatus() throws Exception {
        candidate = missing;
        assertEquals("", header());
    }

    @Test public void genuineBannerSurvivesMissingProbe404() throws Exception {
        missingStatus = 404;
        assertEquals(base + "creator/onlyfans/creator/header.webp", header());
    }

    @Test(expected = java.io.IOException.class)
    public void unverifiedHeaderCannotReplaceAvatarDuringCdnFailure() throws Exception {
        missingStatus = 503;
        header();
    }
}
