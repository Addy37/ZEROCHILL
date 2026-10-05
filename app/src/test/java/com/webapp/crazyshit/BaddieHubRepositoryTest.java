package com.webapp.crazyshit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BaddieHubRepositoryTest {
    @Test public void acceptsOnlyBaddieHubPageHosts() {
        assertTrue(BaddieHubRepository.isBaddieHubUrl("https://baddiehub.com/lala-koi-54/"));
        assertTrue(BaddieHubRepository.isBaddieHubUrl("https://www.baddiehub.com/lala-koi-54/"));
        assertFalse(BaddieHubRepository.isBaddieHubUrl("https://cdn.baddiehub.com/a68436.mp4"));
        assertFalse(BaddieHubRepository.isBaddieHubUrl("https://example.com/lala-koi-54/"));
    }

    @Test public void portraitGateRejectsLandscapeAndSquare() {
        assertTrue(BaddieHubRepository.isPortraitDimensions(1080, 1920, 0));
        assertTrue(BaddieHubRepository.isPortraitDimensions(720, 1280, 0));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1920, 1080, 0));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1080, 1080, 0));
    }

    @Test public void portraitGateHonorsRotationMetadata() {
        assertTrue(BaddieHubRepository.isPortraitDimensions(1920, 1080, 90));
        assertTrue(BaddieHubRepository.isPortraitDimensions(1920, 1080, 270));
        assertFalse(BaddieHubRepository.isPortraitDimensions(1080, 1920, 90));
    }
}
