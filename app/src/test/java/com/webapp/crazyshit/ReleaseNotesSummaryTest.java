package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReleaseNotesSummaryTest {
    @Test
    public void prefersPlainSummaryParagraphBeforeDetailedBullets() {
        String body = "# ZEROCHILL 5.4.0\n\n"
                + "Faster Search, improved Shows, Bunkr playback fixes, XMegaDrive improvements, and more.\n\n"
                + "- Faster first Search results.\n"
                + "- Improved Bunkr playback recovery.\n";

        assertEquals(
                "Faster Search, improved Shows, Bunkr playback fixes, XMegaDrive improvements, and more.",
                ReleaseNotesSummary.fromMarkdown(body, "ZeroChill v5.4.0", "5.4.0")
        );
    }

    @Test
    public void fallsBackToFirstTwoReleaseBullets() {
        String body = "# ZEROCHILL 5.4.0\n\n"
                + "- **Search** now returns results faster.\n"
                + "- Fixed [Bunkr playback](https://example.com).\n"
                + "- Third detail should not appear.\n";

        assertEquals(
                "Search now returns results faster. • Fixed Bunkr playback.",
                ReleaseNotesSummary.fromMarkdown(body, "ZeroChill v5.4.0", "5.4.0")
        );
    }

    @Test
    public void genericReleaseTitleDoesNotBecomeTheSummary() {
        assertEquals(
                "A new ZEROCHILL update is available.",
                ReleaseNotesSummary.fromMarkdown("", "ZeroChill v5.4.0", "5.4.0")
        );
    }

    @Test
    public void longSummaryIsBoundedForCompactUpdateCard() {
        String body = "# ZEROCHILL 5.4.0\n\n"
                + "This is a deliberately very long release summary that keeps adding useful words so the compact update card remains readable on a phone instead of expanding into a huge wall of text while the user is trying to decide whether to download the update right now.";

        String summary = ReleaseNotesSummary.fromMarkdown(body, "ZeroChill v5.4.0", "5.4.0");
        assertTrue(summary.length() <= 181);
        assertTrue(summary.endsWith("…"));
    }
}
