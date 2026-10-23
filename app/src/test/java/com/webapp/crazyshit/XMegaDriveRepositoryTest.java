package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XMegaDriveRepositoryTest {
    @Test public void acceptsOnlyXMegaDriveHosts() {
        assertTrue(XMegaDriveRepository.isXMegaDriveUrl("https://www.xmegadrive.com/videos/example/"));
        assertTrue(XMegaDriveRepository.isXMegaDriveUrl("https://xmegadrive.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl("https://baddiehub.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl("https://example.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl(null));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl(""));
    }
}
