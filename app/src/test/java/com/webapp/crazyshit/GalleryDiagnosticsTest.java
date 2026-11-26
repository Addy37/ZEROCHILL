package com.webapp.crazyshit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class GalleryDiagnosticsTest {
    @Test public void mixedGalleryCreatorActionUsesSelectedItemProfile() {
        String parent = "https://example.invalid/search";
        String profile = "https://cum.st/creators/test/example";
        NativeContentItem photo = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Example",
                "https://media.cum.st/example.jpg", "https://img.cum.st/example.jpg", "", profile, "");
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Example",
                "https://media.cum.st/example.mp4", "", "", profile, "");
        assertEquals(profile, BunkrGalleryActivity.creatorPageDestination(photo, parent));
        assertEquals(profile, BunkrGalleryActivity.creatorPageDestination(video, parent));

        NativeContentItem missing = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Example",
                photo.url, photo.imageUrl, "", "", "");
        assertEquals("", BunkrGalleryActivity.creatorPageDestination(missing, parent));
        NativeContentItem wrongProvider = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Example",
                photo.url, photo.imageUrl, "", "https://example.invalid/creator", "");
        assertEquals("", BunkrGalleryActivity.creatorPageDestination(wrongProvider, parent));
        NativeContentItem directInsteadOfProfile = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Example",
                photo.url, photo.imageUrl, "", photo.url, "");
        assertEquals("", BunkrGalleryActivity.creatorPageDestination(directInsteadOfProfile, parent));

        NativeContentItem legacy = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Example",
                "https://example.invalid/item", "", "", "", "");
        assertEquals(parent, BunkrGalleryActivity.creatorPageDestination(legacy, parent));
        assertEquals("", BunkrGalleryActivity.creatorPageDestination(null, parent));
    }

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
