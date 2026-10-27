package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class PlayableSourceRouterBunkrTest {
    @Test
    public void extensionShapedBunkrFilePagesAreForcedThroughThePageResolver() {
        assertPageMarked("https://bunkr.cr/f/Nina-Crowne---POV-Gyno-Exam-diDQPTX3.mp4");
        assertPageMarked("https://bunkr.cr/f/Nina-Crowne---41-Panties-JOI-Game-MvLbZwxh.mp4");
        assertPageMarked("https://bunkr.cr/f/Nina-Crowne---Hairy-Pits-&-Beautiful-Tits-TcDuJMBk.mp4");
        assertPageMarked("https://bunkr.fi/v/example.webm?source=gallery");
        assertPageMarked("https://bunkr.site/i/example.jpg");
        assertPageMarked("https://bunkr.ph/d/example.mp4");
    }

    @Test
    public void ordinaryBunkrPathsAndOtherHostsRemainUntouched() {
        assertEquals(
                "https://bunkr.cr/assets/example.mp4",
                PlayableSourceRouter.bunkrResolverInput("https://bunkr.cr/assets/example.mp4")
        );
        assertEquals(
                "https://cdn.example.com/example.mp4",
                PlayableSourceRouter.bunkrResolverInput("https://cdn.example.com/example.mp4")
        );
    }

    @Test
    public void existingFragmentAlreadyPreventsTheExtensionShortcut() {
        String url = "https://bunkr.cr/f/example.mp4#existing";
        assertEquals(url, PlayableSourceRouter.bunkrResolverInput(url));
    }

    private static void assertPageMarked(String url) {
        assertEquals(url + "#zerochill-bunkr-page", PlayableSourceRouter.bunkrResolverInput(url));
    }
}
