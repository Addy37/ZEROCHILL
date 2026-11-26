package com.webapp.crazyshit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class GalleryDiagnosticsTest {
    @Test public void reportSeparatesProviderRolesWithoutExposingUrlsAndBoundsEvents() {
        boolean enabled = ShitTokPoolDiagnostics.isEnabled();
        ShitTokPoolDiagnostics.setEnabled(true);
        GalleryDiagnostics.clearForTests();
        try {
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_IMAGE, "private-title",
                    "https://media.cum.st/private-key?token=secret", "https://img.cum.st/private-preview",
                    "", "https://cum.st/creators/test/private-name", "");
            GalleryDiagnostics.imageFailure("grid_artwork", item, item.imageUrl, item.uploader, null,
                    new com.bumptech.glide.load.engine.GlideException("private exception",
                            new com.bumptech.glide.load.HttpException(403)));
            GalleryDiagnostics.navigation(item, true, "https://balbums.com/private-album");
            String report = GalleryDiagnostics.report();
            assertTrue(report.contains("http=403"));
            assertTrue(report.contains("request=onlyhaven_thumbnail"));
            assertTrue(report.contains("referer=onlyhaven"));
            assertTrue(report.contains("destination=balbums"));
            assertFalse(report.contains("private"));
            assertFalse(report.contains("secret"));
            assertFalse(report.contains("https://"));
            assertTrue(ShitTokPoolDiagnostics.userReport().contains("Gallery diagnostics"));
            for (int i = 0; i < 100; i++) GalleryDiagnostics.navigation(item, true, "https://balbums.com/a");
            assertEquals(80, GalleryDiagnostics.report().split("\n").length);
            ShitTokPoolDiagnostics.setEnabled(false);
            GalleryDiagnostics.clearForTests();
            GalleryDiagnostics.missingImage(item);
            assertTrue(GalleryDiagnostics.report().startsWith("No gallery"));
        } finally {
            GalleryDiagnostics.clearForTests();
            ShitTokPoolDiagnostics.setEnabled(enabled);
        }
    }
}
