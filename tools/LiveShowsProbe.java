package com.webapp.crazyshit;

import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Measures metadata-only search with both sets of Android repository classes. */
final class LiveShowsProbe {
    private static final ThreadLocal<Integer> REQUESTS = ThreadLocal.withInitial(() -> 0);

    static void request() {
        REQUESTS.set(REQUESTS.get() + 1);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        var active = RemoteSourceConfigManager.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        ((AtomicReference<SourceConfig>) active.get(null)).set(SourceConfig.parseAndValidate(
                Files.readString(Path.of("app/src/main/assets/source_config_defaults.json"))));
        var executor = Executors.newFixedThreadPool(3);
        try {
            for (String text : List.of("cartel funeral", "car accident", "police shooting", "warehouse accident")) {
                var query = new ShowsSearchQuery(text);
                long started = System.nanoTime();
                var result = executor.invokeAll(List.of(
                        source(mode, "CrazyShit", text, query, started),
                        source(mode, "EFukt", text, query, started),
                        source(mode, "Kaotic", text, query, started)));
                for (var provider : result) System.out.print(provider.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<String> source(String mode, String site, String text,
                                             ShowsSearchQuery query, long started) {
        return () -> {
            var report = new StringBuilder();
            REQUESTS.set(0);
            long[] first = {-1};
            Consumer<List<NativeContentItem>> publish = items -> {
                if (first[0] < 0 && !items.isEmpty()) first[0] = elapsed(started);
            };
            try {
                var crazy = new CrazyShitRepository();
                var efukt = new EfuktRepository();
                var kaotic = new WebVideoSourceRepository();
                List<NativeContentItem> items;
                if ("baseline".equals(mode)) {
                    if (site.equals("CrazyShit")) items = crazy.fetchFeed(null, crazy.searchUrl(text), 1);
                    else items = callSearch(site.equals("EFukt") ? efukt : kaotic, text);
                } else if (site.equals("CrazyShit")) {
                    items = ShowsSearch.find(query, crazy::searchUrl,
                            (url, page, budget) -> crazy.searchPage(null, url, page, budget), null, publish);
                } else if (site.equals("EFukt")) {
                    items = ShowsSearch.find(query, EfuktRepository::searchUrl,
                            (url, page, budget) -> efukt.searchPage(null, url, page, budget), null, publish);
                } else {
                    items = kaotic.search(null, query, publish);
                }
                long completed = elapsed(started);
                var judged = query.rank(120, items);
                long firstComparable = "baseline".equals(mode) ? (judged.isEmpty() ? -1 : completed) : first[0];
                report.append(String.format(Locale.US, "%s %s %s | returned %d | accepted %d | requests %d | first accepted ms %d | completed ms %d%n",
                        mode, text, site, items.size(), judged.size(), REQUESTS.get(), firstComparable, completed));
                for (int i = 0; i < Math.min(10, items.size()); i++) {
                    String title = items.get(i).title.replaceAll("[\\r\\n]", " ");
                    report.append("  ").append(title).append('\n');
                }
            } catch (Exception failure) {
                Throwable cause = failure instanceof InvocationTargetException
                        ? ((InvocationTargetException) failure).getTargetException() : failure;
                report.append(String.format(Locale.US, "%s %s %s | requests %d | %s: %s%n",
                        mode, text, site, REQUESTS.get(), cause.getClass().getSimpleName(), cause.getMessage()));
            }
            return report.toString();
        };
    }

    @SuppressWarnings("unchecked")
    private static List<NativeContentItem> callSearch(Object repository, String query) throws Exception {
        Method method = repository.getClass().getDeclaredMethod("search", Context.class, String.class);
        return (List<NativeContentItem>) method.invoke(repository, null, query);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
