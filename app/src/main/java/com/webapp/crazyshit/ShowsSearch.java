package com.webapp.crazyshit;

import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent, bounded search for one Shows provider. The caller runs providers concurrently. */
final class ShowsSearch {
    static final int SOURCE_LIMIT = 40;
    static final int MAX_REQUESTS = 5;
    private static final int MAX_SEARCH_PAGES = 4;
    private static final int MAX_PAGES_PER_QUERY = 2;
    private static final int REQUEST_TIMEOUT_MS = 3500;
    private static final int TIME_BUDGET_MS = 8500;
    private static final Pattern SEARCH_PATH = Pattern.compile("^(.*/search/[^/]+)(?:/(?:page/)?[0-9]+)?/?$");
    private static final Pattern NEXT_REL = Pattern.compile("(?i)(?:^|\\s)next(?:$|\\s)");

    interface Pages {
        Page fetch(String url, int page, Budget budget) throws IOException;
    }

    interface Fallback {
        List<NativeContentItem> fetch(Budget budget) throws IOException;
    }

    static final class Page {
        final List<NativeContentItem> items;
        final String next;

        Page(List<NativeContentItem> items, String next) {
            this.items = items;
            this.next = next;
        }

        static Page from(Document document, List<NativeContentItem> items,
                         String requestedUrl, int page) {
            return new Page(items, nextLink(document, requestedUrl, page + 1));
        }
    }

    static final class Budget {
        private final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIME_BUDGET_MS);
        private int requests;

        boolean available() throws InterruptedIOException {
            checkCancelled();
            return requests < MAX_REQUESTS && System.nanoTime() < deadline;
        }

        int beginRequest(int configuredTimeoutMs) throws IOException {
            if (!available()) throw new IOException("Shows search request budget exhausted");
            requests++;
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            return (int) Math.max(1L, Math.min(remainingMs, Math.min(configuredTimeoutMs, REQUEST_TIMEOUT_MS)));
        }
    }

    static List<NativeContentItem> find(ShowsSearchQuery query, Function<String, String> searchUrl,
                                        Pages provider, Fallback fallback,
                                        Consumer<List<NativeContentItem>> publish) throws IOException {
        Budget budget = new Budget();
        List<NativeContentItem> results = Collections.emptyList();
        int pages = 0;
        boolean answered = false;
        IOException failure = null;
        Set<String> attemptedQueries = new HashSet<>();
        for (String attempt : query.attempts()) {
            if (pages >= MAX_SEARCH_PAGES || !budget.available() || query.hasEnoughResults(results)) break;
            if (attemptedQueries.size() >= 3 || (attemptedQueries.size() >= 2 && !results.isEmpty())) break;
            String url = searchUrl.apply(attempt);
            if (url.isEmpty() || !attemptedQueries.add(url)) continue;
            boolean firstAttempt = attemptedQueries.size() == 1;
            boolean continueSearch = true;
            try {
                pages++;
                Page page = provider.fetch(url, 1, budget);
                answered = true;
                for (int depth = 1; ; depth++) {
                    checkCancelled();
                    results = collect(query, results, page.items, publish);
                    if (depth >= MAX_PAGES_PER_QUERY || pages >= MAX_SEARCH_PAGES || page.next.isEmpty()
                            || page.items.isEmpty() || query.hasEnoughResults(results) || !budget.available()) break;
                    pages++;
                    page = provider.fetch(page.next, depth + 1, budget);
                }
            } catch (IOException error) {
                checkCancelled();
                failure = error;
                continueSearch = error instanceof HttpStatusException
                        && ((HttpStatusException) error).getStatusCode() == 404;
            }

            // Keep Kaotic's existing first-feed fallback ahead of optional rewrites.
            if (firstAttempt && fallback != null && results.isEmpty() && budget.available()) {
                try {
                    List<NativeContentItem> items = fallback.fetch(budget);
                    checkCancelled();
                    results = collect(query, results, items, publish);
                    answered = true;
                } catch (IOException error) {
                    checkCancelled();
                    if (failure == null) failure = error;
                }
            }
            if (!continueSearch) break;
        }
        checkCancelled();
        if (!answered && failure != null) throw failure;
        return results;
    }

    private static List<NativeContentItem> collect(ShowsSearchQuery query, List<NativeContentItem> current,
                                                   List<NativeContentItem> items,
                                                   Consumer<List<NativeContentItem>> publish)
            throws InterruptedIOException {
        List<NativeContentItem> ranked = query.rank(SOURCE_LIMIT, current, items);
        checkCancelled();
        if (!ranked.equals(current)) publish.accept(ranked);
        return ranked;
    }

    static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Shows search cancelled");
        }
    }

    static String cardDescription(Element card) {
        if (card == null) return "";
        Element description = card.selectFirst("[itemprop=description], .description, .video-description, .excerpt");
        if (description == null) return "";
        String text = description.hasAttr("content") ? description.attr("content") : description.text();
        return text.length() <= 500 ? text : "";
    }

    /** Only follow an advertised next page for the same search, on the responding origin. */
    static String nextLink(Document document, String requestedUrl, int nextPage) {
        try {
            URI origin = URI.create(document.location());
            URI requested = URI.create(requestedUrl);
            String path = searchPath(requested);
            if (path.isEmpty() || !path.equals(searchPath(origin))
                    || isNextPage(origin, path, nextPage)) return "";
            Set<String> numericLinks = new HashSet<>();
            for (Element link : document.select("a[href], link[rel][href]")) {
                String address = link.absUrl("href");
                if (address.isEmpty()) continue;
                URI next;
                try {
                    next = URI.create(address).normalize();
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!sameOrigin(origin, next) || !path.equals(searchPath(next))
                        || !isNextPage(next, path, nextPage)) continue;
                if (NEXT_REL.matcher(link.attr("rel")).find()) return next.toString();
                String label = link.text().trim();
                if (label.equals(String.valueOf(nextPage)) || label.equalsIgnoreCase("next")
                        || label.equalsIgnoreCase("next page")) numericLinks.add(next.toString());
            }
            return numericLinks.size() == 1 ? numericLinks.iterator().next() : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String searchPath(URI url) {
        String path = url.getRawPath();
        if (path == null) return "";
        Matcher match = SEARCH_PATH.matcher(path);
        return match.matches() ? match.group(1) : "";
    }

    private static boolean isNextPage(URI url, String path, int page) {
        String candidatePath = url.getRawPath().replaceFirst("/$", "");
        if (url.getRawFragment() != null) return false;
        if (candidatePath.equals(path)) return ("page=" + page).equals(url.getRawQuery());
        return url.getRawQuery() == null
                && (candidatePath.equals(path + "/" + page) || candidatePath.equals(path + "/page/" + page));
    }

    private static boolean sameOrigin(URI origin, URI candidate) {
        return "https".equalsIgnoreCase(candidate.getScheme()) && candidate.getUserInfo() == null
                && origin.getHost() != null && origin.getHost().equalsIgnoreCase(candidate.getHost())
                && Objects.equals(origin.getScheme(), candidate.getScheme())
                && origin.getPort() == candidate.getPort();
    }
}
