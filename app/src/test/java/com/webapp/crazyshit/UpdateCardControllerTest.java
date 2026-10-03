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
}
