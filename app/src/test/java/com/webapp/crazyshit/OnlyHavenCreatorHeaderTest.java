package com.webapp.crazyshit;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlyHavenCreatorHeaderTest {
    private String header(String metadata) throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        List<OnlyHavenRepository.Creator> creators = repository.parseCreatorSearchJson(
                "{\"creators\":[{\"service\":\"onlyfans\",\"id\":\"creator\",\"name\":\"Creator\"," + metadata + "}]}",
                OnlyHavenTrendingTest.config("https://configured.example/"), "", 5);
        return repository.creatorHeaderUrl(creators.get(0));
    }

    @Test public void genuineMetadataBackedHeaderUsesConfiguredImageBase() throws Exception {
        assertEquals("https://configured.example/creator/onlyfans/creator/header.webp",
                header("\"headerThumbhash\":\"U+gFCYJFOtmEd/mICIgFVig=\""));
    }

    @Test public void unrelatedSearchSuggestionCannotBecomeCreatorBanner() {
        OnlyHavenRepository.Creator unrelated = new OnlyHavenRepository.Creator(
                "onlyfans", "123", "Goth Egg", "", "");
        assertFalse(OnlyHavenRepository.matchesHeaderCreator("Adryelli Rot", unrelated));
        assertFalse(OnlyHavenRepository.matchesHeaderCreator("afknaty", unrelated));
        assertTrue(OnlyHavenRepository.matchesHeaderCreator("Goth Egg", unrelated));
    }

    @Test public void absentHeaderNeverSynthesizesTheGenericCdnEndpoint() throws Exception {
        assertEquals("", header("\"headerThumbhash\":null"));
        assertEquals("", header("\"headerUrl\":\"https://source.example/creator/onlyfans/creator/header.webp\""));
    }

    @Test public void explicitMissingHeaderRejectsEvenAHash() throws Exception {
        assertEquals("", header("\"hasHeader\":false,\"headerThumbhash\":\"U+gFCYJFOtmEd/mICIgFVig=\""));
    }

    @Test public void unknownOrGenericMetadataKeepsCreatorArtworkFallback() throws Exception {
        assertEquals("", header("\"headerThumbhash\":\"default\""));
        assertEquals("", header("\"headerUrl\":\"https://source.example/goth-egg.webp\""));
    }
}
