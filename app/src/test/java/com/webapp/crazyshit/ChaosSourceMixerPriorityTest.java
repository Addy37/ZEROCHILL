package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class ChaosSourceMixerPriorityTest {
    private static List<NativeContentItem> clips(String source, int count) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    source + i, "https://example.com/" + source + i, "", "", source, ""));
        }
        return result;
    }

    @Test public void healthyMixLeadsWithKaoticAndKeepsEfuktAroundOneInThirteen() {
        List<NativeContentItem> result = ChaosSourceMixer.mixAvailable(
                clips("Kaotic", 8), clips("Shit Show", 24), clips("Fapello", 4),
                clips("OnlyHaven", 6), clips("CrazyShit", 12), clips("EFukt", 12));
        assertEquals(34, result.size());
        assertEquals(8, count(result, "Kaotic"));
        assertEquals(7, count(result, "Shit Show"));
        assertEquals(6, count(result, "CrazyShit"));
        assertEquals(3, count(result, "EFukt"));
    }

    @Test public void failedPreferredSourcesLetLowerPriorityFillAvailablePool() {
        List<NativeContentItem> result = ChaosSourceMixer.mixAvailable(
                Collections.emptyList(), clips("Shit Show", 2), Collections.emptyList(),
                Collections.emptyList(), clips("CrazyShit", 2), clips("EFukt", 12));
        assertEquals(16, result.size());
        assertEquals(12, count(result, "EFukt"));
        List<NativeContentItem> noEfukt = ChaosSourceMixer.mixAvailable(
                clips("Kaotic", 4), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(4, noEfukt.size());
    }

    @Test public void bunkrIsNotPartOfShitTokMixedPool() {
        List<NativeContentItem> result = ChaosSourceMixer.mixAvailable(
                clips("Kaotic", 2), clips("Shit Show", 2), clips("Fapello", 2),
                clips("OnlyHaven", 2), clips("CrazyShit", 2), clips("EFukt", 2));
        assertEquals(0, count(result, "Bunkr"));
    }

    private static int count(List<NativeContentItem> items, String source) {
        int result = 0;
        for (NativeContentItem item : items) if (source.equals(item.uploader)) result++;
        return result;
    }
}
