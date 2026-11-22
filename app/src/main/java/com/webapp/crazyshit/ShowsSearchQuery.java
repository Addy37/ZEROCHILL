package com.webapp.crazyshit;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scores every result against the user's original query, including results from rewritten queries. */
final class ShowsSearchQuery {
    private static final int EXACT = 500;
    private static final int ALL_TITLE = 400;
    private static final int PARTIAL = 300;
    private static final int METADATA = 200;
    private static final int RELATED = 100;
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "of", "in", "on", "at", "for", "to", "and", "with", "by"));
    private static final Set<String> BROAD_ANCHORS = new HashSet<>(Arrays.asList(
            "video", "footage", "clip", "accident", "crash", "shooting"));
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern EFUKT_ID = Pattern.compile("/(\\d+)_[^/]+\\.html$");
    private static final Pattern CRAZYSHIT_ID = Pattern.compile("^/cnt/medias/(\\d+)(?=[/._-]|$)");

    private final String original;
    private final String phrase;
    private final List<String> words;
    private final Set<String> terms;
    private final Map<String, List<String>> related = new LinkedHashMap<>();
    private final Comparator<NativeContentItem> order =
            Comparator.<NativeContentItem>comparingInt(item -> match(item).score).reversed()
                    .thenComparing(item -> item.imageUrl.isEmpty())
                    .thenComparing(item -> match(item).key)
                    .thenComparing(item -> item.url);
    private final Map<NativeContentItem, Match> matches = new ConcurrentHashMap<>();

    ShowsSearchQuery(String query) {
        original = query.trim();
        phrase = normalize(original);
        words = contentWords(phrase);
        terms = new LinkedHashSet<>(words);
        if (terms.contains("cartel") && terms.contains("funeral")) {
            related.put("cartel", Arrays.asList("narco", "drug trafficker"));
        }
        if (terms.size() > 1 && terms.contains("funeral")) {
            related.put("funeral", Arrays.asList("burial", "memorial service"));
        }
        if (terms.size() > 1 && terms.contains("accident")) {
            related.put("accident", Arrays.asList("crash", "collision"));
        }
        if (terms.contains("car") && (terms.contains("accident") || terms.contains("crash"))) {
            related.put("car", Arrays.asList("auto", "automobile"));
        }
        if (terms.contains("police") && terms.contains("shooting")) {
            related.put("police", Arrays.asList("officer involved", "cop"));
        }
    }

    List<String> attempts() {
        if (terms.isEmpty()) return Collections.emptyList();
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put(original.toLowerCase(Locale.ROOT), original);
        if (phrase.length() >= 2) queries.putIfAbsent(phrase, phrase);
        if (!related.isEmpty()) {
            Map.Entry<String, List<String>> rewrite = related.entrySet().iterator().next();
            List<String> rewritten = new ArrayList<>(words);
            Collections.replaceAll(rewritten, rewrite.getKey(), rewrite.getValue().get(0));
            String alternate = String.join(" ", rewritten);
            queries.putIfAbsent(alternate, alternate);
        }
        String simplified = String.join(" ", words);
        if (simplified.length() >= 2) queries.putIfAbsent(simplified, simplified);
        if (terms.size() > 1 && !related.isEmpty()) addAnchor(queries);
        if (words.size() > 1) {
            List<String> reversed = new ArrayList<>(words);
            Collections.reverse(reversed);
            String reordered = String.join(" ", reversed);
            queries.putIfAbsent(reordered, reordered);
        }
        if (terms.size() > 1 && related.isEmpty()) addAnchor(queries);
        return new ArrayList<>(queries.values());
    }

    private void addAnchor(Map<String, String> queries) {
        String anchor = "";
        for (String term : terms) {
            if (term.length() > anchor.length() && !BROAD_ANCHORS.contains(term)) anchor = term;
        }
        if (anchor.length() >= 2) queries.putIfAbsent(anchor, anchor);
    }

    boolean hasEnoughResults(List<NativeContentItem> items) {
        int useful = 0;
        for (NativeContentItem item : items) {
            if (match(item).score >= ALL_TITLE && ++useful >= 4) return true;
        }
        return false;
    }

    @SafeVarargs
    final List<NativeContentItem> rank(int limit, List<NativeContentItem>... batches) {
        Map<String, NativeContentItem> unique = new LinkedHashMap<>();
        for (List<NativeContentItem> batch : batches) {
            for (NativeContentItem item : batch) {
                if (item == null || !item.isVideo() || item.url.isEmpty()) continue;
                Match candidate = match(item);
                if (candidate.score <= 0) continue;
                NativeContentItem existing = unique.get(candidate.key);
                if (existing == null || order.compare(item, existing) < 0) unique.put(candidate.key, item);
            }
        }
        ArrayList<NativeContentItem> ranked = new ArrayList<>(unique.values());
        ranked.sort(order);
        int end = Math.min(limit, ranked.size());
        return end == ranked.size() ? ranked : new ArrayList<>(ranked.subList(0, end));
    }

    private Match match(NativeContentItem item) {
        return matches.computeIfAbsent(item, video -> new Match(score(video), videoKey(video.url)));
    }

    private int score(NativeContentItem video) {
        if (terms.isEmpty()) return 0;
        String title = normalize(video.title);
        if (hasPhrase(title, phrase)) return EXACT + (title.equals(phrase) ? 1 : 0);
        List<String> titleWords = contentWords(title);
        Set<String> hits = new HashSet<>(titleWords);
        hits.retainAll(terms);
        if (hits.size() == terms.size()) return ALL_TITLE + (titleWords.equals(words) ? 1 : 0);

        // Two-word searches require evidence for both concepts. Longer queries can
        // match on a majority when the matching words include an adjacent query pair.
        if (hits.size() >= 2 && hits.size() * 3 >= terms.size() * 2
                && sharesPair(titleWords)) return PARTIAL + Math.min(99, hits.size());

        List<String> description = contentWords(normalize(video.description));
        Set<String> context = new HashSet<>(description);
        context.addAll(hits);
        if (context.containsAll(terms)) return METADATA + Math.min(99, hits.size());

        if (related.isEmpty()) return 0;
        if (containsRelatedTerms(titleWords)) return RELATED + 1;
        return containsRelatedTerms(description) ? RELATED : 0;
    }

    private boolean containsRelatedTerms(List<String> candidate) {
        String text = String.join(" ", candidate);
        for (String term : terms) {
            if (candidate.contains(term)) continue;
            List<String> alternatives = related.get(term);
            if (alternatives == null) return false;
            boolean matched = false;
            for (String alternate : alternatives) {
                if (hasPhrase(text, String.join(" ", contentWords(alternate)))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private boolean sharesPair(List<String> titleWords) {
        for (int i = 1; i < words.size(); i++) {
            List<String> pair = words.subList(i - 1, i + 1);
            if (Collections.indexOfSubList(titleWords, pair) >= 0) return true;
        }
        return false;
    }

    private static boolean hasPhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static String normalize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        return PUNCTUATION.matcher(MARKS.matcher(lower).replaceAll("")
                .replace("’", "").replace("'", "")).replaceAll(" ").trim();
    }

    private static List<String> contentWords(String text) {
        List<String> result = new ArrayList<>();
        for (String word : text.split(" ")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) result.add(singular(word));
        }
        return result;
    }

    private static String singular(String word) {
        if (word.equals("news") || word.equals("series") || word.equals("species")) return word;
        if (word.endsWith("ovies") || word.endsWith("ookies")) return word.substring(0, word.length() - 1);
        if (word.length() > 4 && word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("shes") || word.endsWith("ches") || word.endsWith("xes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")
                && !word.endsWith("us") && !word.endsWith("is")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static String videoKey(String url) {
        try {
            URI uri = URI.create(url).normalize();
            if (uri.getHost() == null || uri.getRawPath() == null) return url;
            String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getRawPath().replaceFirst("/+$", "");
            if (host.equals("efukt.com") || host.equals("crazyshit.com")) {
                Matcher id = (host.equals("efukt.com") ? EFUKT_ID : CRAZYSHIT_ID).matcher(path);
                if (id.find()) return host + ":" + id.group(1);
            }
            List<String> parameters = new ArrayList<>();
            if (uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    if (!name.startsWith("utm_") && !name.equals("fbclid") && !name.equals("gclid")) {
                        parameters.add(parameter);
                    }
                }
            }
            int port = uri.getPort();
            return host + (port == -1 || port == 80 || port == 443 ? "" : ":" + port) + path
                    + (parameters.isEmpty() ? "" : "?" + String.join("&", parameters));
        } catch (IllegalArgumentException ignored) {
            return url;
        }
    }

    private static final class Match {
        final int score;
        final String key;

        Match(int score, String key) {
            this.score = score;
            this.key = key;
        }
    }
}
