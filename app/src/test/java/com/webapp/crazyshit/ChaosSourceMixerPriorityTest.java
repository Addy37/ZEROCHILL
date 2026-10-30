package com.webapp.crazyshit;

import static org.junit.Assert.*;
import java.nio.file.*;
import org.junit.Test;

public final class ChaosSourceMixerPriorityTest {
    @Test public void everyExistingProviderUsesSessionAcquisitionWithoutPermanentMixQuotas() throws Exception {
        String mixer = new String(Files.readAllBytes(Paths.get("src/main/java/com/webapp/crazyshit/ChaosSourceMixer.java")), java.nio.charset.StandardCharsets.UTF_8);
        for (String source : new String[] {"KAOTIC", "FAPELLO", "REGULAR", "BADDIEHUB", "XMEGADRIVE", "EFUKT", "ONLY_HAVEN", "SHIT_SHOW"})
            assertTrue(source, mixer.contains("sourcePool.request(BATCH_" + source));
        assertFalse(mixer.contains("mixAvailable("));
        assertFalse(mixer.contains("new BunkrRepository"));
    }
}
