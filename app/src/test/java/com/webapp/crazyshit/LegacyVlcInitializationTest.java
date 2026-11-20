package com.webapp.crazyshit;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.videolan.libvlc.LibVLC;

import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = LegacyVlcInitializationTest.NativeLibVlc.class)
public class LegacyVlcInitializationTest {
    @Implements(value = LibVLC.class, isInAndroidSdk = false)
    public static class NativeLibVlc {
        static String[] options;

        @Implementation protected static void loadLibraries() { }

        @Implementation protected void nativeNew(String[] args, String path) {
            options = args;
        }
    }

    @Test public void decoderConstructorCanAppendItsPlatformDefaults() {
        Application app = RuntimeEnvironment.getApplication();
        assertThrows(UnsupportedOperationException.class,
                () -> new LibVLC(app, java.util.Collections.emptyList()));
        LibVLC decoder = LegacyVideoPlayerActivity.createDecoder(app);
        assertNotNull(decoder);
        assertNotNull(NativeLibVlc.options);
        assertTrue(Arrays.asList(NativeLibVlc.options).contains("--android-display-chroma"));
        assertTrue(Arrays.stream(NativeLibVlc.options).anyMatch(option -> option.startsWith("--aout=")));
    }
}
