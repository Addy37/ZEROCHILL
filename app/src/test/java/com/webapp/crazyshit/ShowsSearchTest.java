package com.webapp.crazyshit;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ShowsSearchTest {
    @Test public void ranksExactAllTermsPartialMetadataAndRelatedInOrder() {
        ShowsSearchQuery query = new ShowsSearchQuery("night car accident");
        NativeContentItem exact = video("Night car accident", "1", "");
        NativeContentItem all = video("At night, multiple cars in accidents", "2", "");
        NativeContentItem partial = video("Car accident caught on camera", "3", "");
        NativeContentItem metadata = video("Intersection at night", "4", "A car accident at the intersection.");
        NativeContentItem related = video("Nighttime footage of a car crash at night", "5", "");
        NativeContentItem weak = video("Car wash at noon", "6", "");
        assertEquals(Arrays.asList(exact, all, partial, metadata, related),
                query.rank(20, Arrays.asList(related, weak, all, partial, metadata, exact)));
    }

    @Test public void rejectsUnanchoredSynonymsAndSubstringMatches() {
        ShowsSearchQuery query = new ShowsSearchQuery("car accident");
        NativeContentItem related = video("Automobile collision on bridge", "1", "");
        assertEquals(Collections.singletonList(related), query.rank(20, Arrays.asList(
                video("Cargo accident at the dock", "2", ""),
                video("A car show", "3", "See the weekend listings"),
                video("Truck crash", "4", ""),
                related)));
        assertEquals(Arrays.asList("car accident", "car crash", "accident car"), query.attempts());
        assertEquals(Arrays.asList("The cars", "car"), new ShowsSearchQuery("The cars").attempts());
    }

    @Test public void deduplicatesProviderAliasesAndTrackingWithoutCollapsingDistinctClips() {
        NativeContentItem wrongPageTitle = item("Accident involving a car",
                "http://www.efukt.com/folder/13_old_title.html?utm_source=share", "");
        NativeContentItem better = item("Car accident",
                "https://efukt.com/13_new_title.html", "");
        NativeContentItem otherWithSameTitle = item("Car accident",
                "https://efukt.com/25_new_title.html", "");
        NativeContentItem otherProvider = item("Car accident",
                "https://kaotic.com/video/13", "");
        NativeContentItem otherProviderAlias = item("Car accident",
                "https://www.kaotic.com/video/13/?utm_medium=copy#player", "");
        NativeContentItem meaningfulQuery = item("Car accident",
                "https://kaotic.com/video/13?clip=2", "");
        List<NativeContentItem> ranked = new ShowsSearchQuery("car accident").rank(20,
                Arrays.asList(wrongPageTitle, otherProviderAlias, meaningfulQuery),
                Arrays.asList(better, otherWithSameTitle, otherProvider));
        assertEquals(Arrays.asList(better, otherWithSameTitle, otherProvider, meaningfulQuery), ranked);
    }

    @Test public void followsOnlyAdvertisedSameSearchPagination() {
        String base = "https://efukt.com/search/car+accident/";
        String links = "<a rel=next href='https://example.com/search/car+accident/2/'>Next</a>"
                + "<a rel=next href='/search/other/2/'>Next</a>"
                + "<a rel=next href='/search/car+accident/8/'>Next</a>"
                + "<a href='/2/'>2</a><a href='?page=2'>2</a>";
        assertEquals(base + "?page=2", ShowsSearch.nextLink(Jsoup.parse(links, base), base, 2));
        String rel = "<link rel='nofollow next' href='/search/car+accident/page/2/'>";
        assertEquals(base + "page/2/", ShowsSearch.nextLink(Jsoup.parse(rel, base), base, 2));
        assertEquals("", ShowsSearch.nextLink(Jsoup.parse(links, "https://efukt.com/"), base, 2));
        assertEquals("", ShowsSearch.nextLink(Jsoup.parse("<a href='/2/'>Next</a>", base), base, 2));
        assertEquals("", ShowsSearch.nextLink(
                Jsoup.parse("<a href='?page=2'>2</a>", base + "?page=2"), base, 2));
    }

    @Test public void capsPagesAndRequestsAndPublishesUsefulHitsBeforeFinishing() throws Exception {
        List<String> requests = new ArrayList<>();
        List<String> firstHitAfter = new ArrayList<>();
        List<NativeContentItem> result = ShowsSearch.find(new ShowsSearchQuery("car accident"),
                text -> text, (address, page, budget) -> {
                    assertTrue(budget.beginRequest(18000) <= 3500);
                    requests.add(address);
                    return new ShowsSearch.Page(Collections.singletonList(
                            video("Car accident " + requests.size(), String.valueOf(requests.size()), "")),
                            address + "-next");
                }, null, items -> firstHitAfter.add(requests.get(requests.size() - 1)));
        assertEquals(Arrays.asList("car accident", "car accident-next", "car crash", "car crash-next"), requests);
        assertEquals(requests, firstHitAfter);
        assertEquals(4, result.size());

        ShowsSearch.Budget budget = new ShowsSearch.Budget();
        for (int i = 0; i < ShowsSearch.MAX_REQUESTS; i++) budget.beginRequest(1000);
        assertFalse(budget.available());
        assertThrows(IOException.class, () -> budget.beginRequest(1000));
    }

    @Test public void skipsAlternativesAndDeeperPagesAfterStrongFirstPage() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        List<NativeContentItem> hits = Arrays.asList(video("Car accident 1", "1", ""),
                video("Car accident 2", "2", ""), video("Car accident 3", "3", ""),
                video("Car accident 4", "4", ""));
        assertEquals(hits, ShowsSearch.find(new ShowsSearchQuery("car accident"), text -> text,
                (address, page, budget) -> {
                    requests.incrementAndGet();
                    return new ShowsSearch.Page(hits, "next");
                }, null, ignored -> {}));
        assertEquals(1, requests.get());
    }

    @Test public void evaluatesAlternativeResultsAgainstOriginalQuery() throws Exception {
        NativeContentItem relevant = video("Police shooting downtown", "1", "");
        NativeContentItem irrelevant = video("Officers involved in a road closure", "2", "");
        List<String> attempts = new ArrayList<>();
        assertEquals(Collections.singletonList(relevant), ShowsSearch.find(
                new ShowsSearchQuery("police shooting"), text -> text, (address, page, budget) -> {
                    attempts.add(address);
                    return new ShowsSearch.Page(address.equals("officer involved shooting")
                            ? Arrays.asList(irrelevant, relevant) : Collections.singletonList(irrelevant), "");
                }, null, ignored -> {}));
        assertEquals(Arrays.asList("police shooting", "officer involved shooting"), attempts);
    }

    @Test public void cancellationStopsFallbackFollowupAndPublication() {
        AtomicInteger calls = new AtomicInteger();
        try {
            assertThrows(InterruptedIOException.class, () -> ShowsSearch.find(
                    new ShowsSearchQuery("police shooting"), text -> text, (address, page, budget) -> {
                        calls.incrementAndGet();
                        Thread.currentThread().interrupt();
                        return new ShowsSearch.Page(Collections.singletonList(
                                video("Police shooting", "1", "")), "next");
                    }, budget -> { throw new AssertionError("Cancelled search used the feed fallback"); },
                    ignored -> { throw new AssertionError("Cancelled search published"); }));
            assertEquals(1, calls.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void preservesHitsWhenPaginationFailsAndSurfacesUnavailableFirstPage() throws Exception {
        NativeContentItem hit = video("Warehouse accident", "1", "");
        ShowsSearchQuery query = new ShowsSearchQuery("warehouse accident");
        assertEquals(Collections.singletonList(hit), ShowsSearch.find(query, text -> text,
                (address, page, budget) -> {
                    if (page == 2) throw new IOException("broken next page");
                    return new ShowsSearch.Page(Collections.singletonList(hit), "next");
                }, null, ignored -> {}));
        assertThrows(IOException.class, () -> ShowsSearch.find(query, text -> text,
                (address, page, budget) -> { throw new IOException("offline"); },
                null, ignored -> {}));
    }

    @Test public void limitsUnavailableSearchToExistingFirstFeedFallback() throws Exception {
        AtomicInteger fallbacks = new AtomicInteger();
        NativeContentItem hit = video("Warehouse accident", "1", "");
        assertEquals(Collections.singletonList(hit), ShowsSearch.find(
                new ShowsSearchQuery("warehouse accident"), text -> text, (address, page, budget) -> {
                    throw new HttpStatusException("missing search results", 404, address);
                }, budget -> {
                    fallbacks.incrementAndGet();
                    return Arrays.asList(video("Building opening", "2", ""), hit);
                }, ignored -> {}));
        assertEquals(1, fallbacks.get());
    }

    @Test public void comparesBaselineAndRankedSearchOnFourControlledQueries() throws Exception {
        compare("cartel funeral", "Cartel burial procession", "2", 2, 0, 1, 4, 10, 0, 220);
        compare("car accident", "Car accident on highway", "1", 4, 1, 1, 3, 9, 110, 110);
        compare("police shooting", "Officer-involved shooting downtown", "3", 2, 0, 1, 4, 9, 0, 320);
        compare("warehouse accident", "Warehouse accident on loading dock", "4", 3, 1, 1, 4, 9, 170, 170);
    }

    private static void compare(String text, String relevantTitle, String relevantId,
                                int baselineReturned, int baselineRelevant, int improvedRelevant, int baselineRequests,
                                int improvedRequests, int baselineFirstMs, int improvedFirstMs)
            throws IOException {
        NativeContentItem relevant = video(relevantTitle, relevantId, "");
        Predicate<NativeContentItem> judgedRelevant = item -> item.url.equals(relevant.url);
        FixtureSource[] providers = {
                new FixtureSource("CrazyShit", 110, video("Local sports update", "11", "")),
                new FixtureSource("EFukt", 160, video("An unrelated upload", "12", "")),
                new FixtureSource("Kaotic", 90, video("Weekend road report", "13", ""))
        };
        switch (relevantId) {
            case "1":
                providers[0].initial.add(relevant);
                // The baseline Kaotic substring filter treated "cargo" as "car".
                providers[2].initial.add(video("Cargo accident at the port", "14", ""));
                break;
            case "2":
                providers[0].pageTwo = Collections.singletonList(relevant);
                break;
            case "3":
                providers[1].alternate = Collections.singletonList(relevant);
                break;
            case "4":
                providers[2].feed = Collections.singletonList(relevant);
                break;
            default: throw new AssertionError(relevantId);
        }
        Comparison baseline = new Comparison();
        Comparison improved = new Comparison();
        ShowsSearchQuery query = new ShowsSearchQuery(text);
        List<NativeContentItem> rankedBatches = new ArrayList<>();
        for (FixtureSource provider : providers) {
            List<NativeContentItem> before = provider.baseline(text);
            baseline.record(before, judgedRelevant, provider.requests,
                    before.stream().anyMatch(judgedRelevant) ? provider.elapsed : 0);
            provider.requests = 0;
            provider.elapsed = 0;
            AtomicInteger usefulAt = new AtomicInteger();
            List<NativeContentItem> after = ShowsSearch.find(query, value -> value,
                    (address, page, budget) -> provider.search(address, text, page, budget),
                    provider.name.equals("Kaotic") ? provider::feed : null, hits -> {
                        if (hits.stream().anyMatch(judgedRelevant)) usefulAt.compareAndSet(0, provider.elapsed);
                    });
            rankedBatches.addAll(after);
            improved.record(after, judgedRelevant, provider.requests, usefulAt.get());
        }
        assertEquals(baselineRelevant, baseline.relevant);
        assertEquals(improvedRelevant, improved.relevant);
        assertEquals(baselineReturned, baseline.returned);
        assertEquals(improvedRelevant, query.rank(120, rankedBatches).size());
        assertEquals(baselineRequests, baseline.requests);
        assertEquals(improvedRequests, improved.requests);
        assertEquals(baselineFirstMs, baseline.firstUsefulMs);
        assertEquals(improvedFirstMs, improved.firstUsefulMs);
        System.out.printf(Locale.ROOT, "%s: %s -> %s%n", text, baseline, improved);
    }

    /** Request durations are assigned fixture costs, with the three providers starting together. */
    private static final class FixtureSource {
        final String name;
        final int searchMs;
        final List<NativeContentItem> initial = new ArrayList<>();
        List<NativeContentItem> pageTwo = Collections.emptyList();
        List<NativeContentItem> alternate = Collections.emptyList();
        List<NativeContentItem> feed = Collections.emptyList();
        int elapsed;
        int requests;

        FixtureSource(String name, int searchMs, NativeContentItem noise) {
            this.name = name;
            this.searchMs = searchMs;
            initial.add(noise);
        }

        List<NativeContentItem> baseline(String query) {
            recordRequest(searchMs);
            if (!name.equals("Kaotic")) return initial;
            List<NativeContentItem> matches = kaoticSubstringFilter(initial, query);
            if (!matches.isEmpty()) return matches;
            recordRequest(80);
            return kaoticSubstringFilter(feed, query);
        }

        ShowsSearch.Page search(String address, String query, int page, ShowsSearch.Budget budget)
                throws IOException {
            budget.beginRequest(3500);
            recordRequest(searchMs);
            if (page == 2) return new ShowsSearch.Page(pageTwo, "");
            if (!address.equals(query)) return new ShowsSearch.Page(alternate, "");
            return new ShowsSearch.Page(initial, pageTwo.isEmpty() ? "" : address + "#next");
        }

        List<NativeContentItem> feed(ShowsSearch.Budget budget) throws IOException {
            budget.beginRequest(3500);
            recordRequest(80);
            return feed;
        }

        void recordRequest(int duration) {
            requests++;
            elapsed += duration;
        }

        private static List<NativeContentItem> kaoticSubstringFilter(List<NativeContentItem> items, String query) {
            List<NativeContentItem> result = new ArrayList<>();
            for (NativeContentItem item : items) {
                if (LibrarySearch.matches(query, item.title, item.url, item.description, item.uploader)) {
                    result.add(item);
                }
            }
            return result;
        }
    }

    private static final class Comparison {
        int returned;
        int relevant;
        int requests;
        int firstUsefulMs;

        void record(List<NativeContentItem> items, Predicate<NativeContentItem> judgement,
                    int requestCount, int usefulAt) {
            returned += items.size();
            relevant += (int) items.stream().filter(judgement).count();
            requests += requestCount;
            if (usefulAt > 0 && (firstUsefulMs == 0 || usefulAt < firstUsefulMs)) firstUsefulMs = usefulAt;
        }

        @Override public String toString() {
            return String.format(Locale.ROOT, "%d/%d relevant, %d requests, first useful %s",
                    relevant, returned, requests, firstUsefulMs == 0 ? "none" : firstUsefulMs + " ms");
        }
    }

    private static NativeContentItem video(String title, String id, String description) {
        return item(title, "https://kaotic.com/video/" + id, description);
    }

    private static NativeContentItem item(String title, String url, String description) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, title, url, "", "", "", "", description);
    }
}
