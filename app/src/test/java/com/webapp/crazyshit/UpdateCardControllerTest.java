package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdateCardControllerTest {
    @Test
    public void formatsDownloadProgressForCompactCard() {
        assertEquals("0 KB", UpdateCardController.formatBytes(0));
        assertEquals("512 KB", UpdateCardController.formatBytes(512L * 1024L));
        assertEquals("1.0 MB", UpdateCardController.formatBytes(1024L * 1024L));
        assertEquals(
                "8.7 MB of 14.1 MB",
                UpdateCardController.downloadDetail(9_122_611L, 14_785_126L)
        );
        assertEquals(
                "2.0 MB downloaded",
                UpdateCardController.downloadDetail(2L * 1024L * 1024L, -1L)
        );
    }

    @Test
    public void formatsReleaseHighlightsForWhatsNewStylePanel() {
        assertEquals(
                "•  Better playback\\n\\n•  Faster creator loading".replace("\\n", "\n"),
                UpdateCardController.displaySummary("Better playback • Faster creator loading")
        );
    }

    @Test
    public void preservesPlainReleaseSummaryAndEmptyFallback() {
        assertEquals("Several fixes are available.",
                UpdateCardController.displaySummary(" Several fixes are available. "));
        assertEquals("A new ZEROCHILL update is available.",
                UpdateCardController.displaySummary(null));
    }
}
