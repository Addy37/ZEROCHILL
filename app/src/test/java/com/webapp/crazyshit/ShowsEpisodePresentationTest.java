package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ShowsEpisodePresentationTest {
    @Test public void numberingContinuesAcrossPages() {
        assertEquals("EPISODE 01", ShowsEpisodePresentation.label(0, 0));
        assertEquals("EPISODE 10", ShowsEpisodePresentation.label(0, 9));
        assertEquals("EPISODE 11", ShowsEpisodePresentation.label(10, 0));
        assertEquals("EPISODE 24", ShowsEpisodePresentation.label(20, 3));
        assertEquals("EPISODE 101", ShowsEpisodePresentation.label(100, 0));
    }
}
