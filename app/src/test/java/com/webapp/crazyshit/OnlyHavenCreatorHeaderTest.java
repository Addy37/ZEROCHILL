package com.webapp.crazyshit;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise the real repository HTTP path against a CDN with a successful missing-image response. */
public class OnlyHavenCreatorHeaderTest {
    private HttpServer server;
    private String base;
    private int missingStatus = 200;
    private String candidate = "creator artwork";
    private String missing = "site placeholder";

    @Before public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/creator/", exchange -> {
            boolean probe = exchange.getRequestURI().getPath().contains("__zerochill_missing_creator_header__");
            byte[] body = (probe ? missing : candidate).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "image/webp");
            exchange.sendResponseHeaders(probe ? missingStatus : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @After public void stop() { server.stop(0); }

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
