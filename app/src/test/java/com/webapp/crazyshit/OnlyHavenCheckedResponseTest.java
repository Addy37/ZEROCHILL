package com.webapp.crazyshit;

import org.jsoup.Connection;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class OnlyHavenCheckedResponseTest {
    private static Connection.Response response(boolean fail) {
        return (Connection.Response) Proxy.newProxyInstance(
                Connection.Response.class.getClassLoader(),
                new Class<?>[] { Connection.Response.class },
                (proxy, method, args) -> {
                    if ("readBody".equals(method.getName())) {
                        if (fail) throw new IOException("Network response dropped during read");
                        return "{\"results\":[]}";
                    }
                    throw new AssertionError("OnlyHaven must use the checked readBody API, not " + method.getName());
                });
    }

    @Test public void completedResponseReturnsContent() throws Exception {
        assertEquals("{\"results\":[]}", OnlyHavenRepository.readCreatorApiBody(response(false)));
    }

    @Test public void interruptedResponseRemainsCheckedForRetry() {
        try {
            OnlyHavenRepository.readCreatorApiBody(response(true));
            fail("Expected checked IOException to reach the existing retry loop");
        } catch (IOException expected) {
            assertEquals("Network response dropped during read", expected.getMessage());
        }
    }
}
