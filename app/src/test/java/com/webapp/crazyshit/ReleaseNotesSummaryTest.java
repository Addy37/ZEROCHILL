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
    public void whatsNewRetainsAllLongBulletsForScrolling() {
        StringBuilder notes = new StringBuilder("# ZEROCHILL release notes\n\n");
        for (int i = 1; i <= 7; i++) {
            notes.append("- Update ").append(i).append(": ")
                    .append("A full explanation of the update that stays readable "
                            + "across several wrapped lines on a small phone screen. ")
                    .append("No information in this bullet should be truncated.\n");
        }
        java.util.List<String> highlights = ReleaseNotesSummary.highlights(notes.toString());
        assertEquals(7, highlights.size());
        assertTrue(highlights.get(0).startsWith("Update 1:"));
        assertTrue(highlights.get(6).startsWith("Update 7:"));
        assertTrue(highlights.get(6).endsWith("No information in this bullet should be truncated."));
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
