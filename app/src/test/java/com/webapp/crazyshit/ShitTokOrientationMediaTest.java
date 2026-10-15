package com.webapp.crazyshit;

import android.app.Application;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Real MP4 track metadata, with a tail moov atom, rotation and non-square pixels. No decoder. */
@UnstableApi
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokOrientationMediaTest {
    private float inspect(String name) throws Exception {
        byte[] bytes;
        try (InputStream input = getClass().getResourceAsStream("/shittok-orientation/" + name + ".mp4")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        DataSource.Factory factory = () -> new ByteArrayDataSource(bytes);
        return ReflectionHelpers.callStaticMethod(ShitTokOrientationProbe.class, "progressive",
                from(DataSource.Factory.class, factory),
                from(String.class, "https://fixture.invalid/" + name + ".mp4"),
                from(ShitTokOrientationProbe.Budget.class, new ShitTokOrientationProbe.Budget(() -> true)));
    }
    @Test public void extractsPortraitBeforeQueueAdmission() throws Exception {
        assertEquals(18f / 32, inspect("portrait"), .00001f);
    }
    @Test public void extractsAndAllowsSquare() throws Exception {
        assertEquals(1f, inspect("square"), 0f);
    }
    @Test public void actualLandscapeContainerFailsEligibility() throws Exception {
        assertFalse(ShitTokOrientation.eligible(inspect("landscape")));
    }
    @Test public void readsContainerDisplayRotation() throws Exception {
        assertEquals(18f / 32, inspect("rotated"), .00001f);
    }
    @Test public void nonSquarePixelsCanMakeEncodedPortraitDisplayAsLandscape() throws Exception {
        assertEquals(1.125f, inspect("wide-pixels"), .00001f);
        assertFalse(ShitTokOrientation.eligible(inspect("wide-pixels")));
    }
}
