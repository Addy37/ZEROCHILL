package com.webapp.crazyshit;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Compact, immutable creator names. Load once off the main thread; never store this in preferences. */
final class BundledCreatorIndex {
    private static volatile BundledCreatorIndex cached;
    private final List<Entry> entries;
    private final Map<String, Entry> byName = new HashMap<>();
    private final Map<String, String> canonicalByAlias = new HashMap<>();
    private final Map<String, String> canonicalBySafeVariant = new HashMap<>();
    private final Set<String> ambiguousSafeVariants = new HashSet<>();

    private BundledCreatorIndex(List<Entry> entries) {
        this.entries = entries;
        for (Entry entry : entries) {
            String canonical = entry.searchable.get(0);
            byName.put(canonical, entry);
            canonicalByAlias.put(canonical, canonical);
        }
        // Explicit aliases are reviewed identity links and take priority over a separately
        // discovered row with the same spelling.
        for (Entry entry : entries) {
            String canonical = entry.searchable.get(0);
            for (int i = 1; i < entry.searchable.size(); i++) {
                canonicalByAlias.put(entry.searchable.get(i), canonical);
            }
        }

        // Precompute conservative typo/truncation variants once. Favorite screens can contain
        // thousands of learned creator rows, so canonicalKey() must stay O(1).
        for (Entry entry : entries) {
            String canonical = entry.searchable.get(0);
            String compact = compact(canonical);
            for (int missing = 1; missing <= 5 && compact.length() - missing >= 8; missing++) {
                addSafeVariant(compact.substring(0, compact.length() - missing), canonical);
            }
            for (int i = 0; i < compact.length(); i++) {
                char value = compact.charAt(i);
                String doubled = compact.substring(0, i) + value + compact.substring(i);
                addSafeVariant(doubled, canonical);
                boolean repeated = (i > 0 && compact.charAt(i - 1) == value)
                        || (i + 1 < compact.length() && compact.charAt(i + 1) == value);
                if (repeated && compact.length() - 1 >= 8) {
                    addSafeVariant(compact.substring(0, i) + compact.substring(i + 1), canonical);
                }
            }
        }
    }

    private void addSafeVariant(String variant, String canonical) {
        if (variant == null || variant.length() < 8 || variant.equals(compact(canonical))) return;
        if (ambiguousSafeVariants.contains(variant)) return;
        String previous = canonicalBySafeVariant.get(variant);
        if (previous == null) {
            canonicalBySafeVariant.put(variant, canonical);
        } else if (!previous.equals(canonical)) {
            canonicalBySafeVariant.remove(variant);
            ambiguousSafeVariants.add(variant);
        }
    }

    static BundledCreatorIndex get(Context context) {
        BundledCreatorIndex result = cached;
        if (result != null) return result;
        synchronized (BundledCreatorIndex.class) {
            if (cached == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        context.getApplicationContext().getAssets().open("onlyfap_creators.tsv"),
                        StandardCharsets.UTF_8))) {
                    cached = parse(reader);
                } catch (Exception failure) {
                    android.util.Log.w("OnlyFapIndex", "Bundled index unavailable", failure);
                    cached = new BundledCreatorIndex(new ArrayList<>());
                }
            }
            return cached;
        }
    }

    static BundledCreatorIndex parse(BufferedReader reader) throws java.io.IOException {
        List<Entry> rows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] columns = line.split("\t", -1);
            String name = columns[0].trim();
            if (name.isEmpty()) continue;
            String normalized = CreatorNameMatcher.normalized(name);
            if (normalized.isEmpty()) continue;
            String[] aliases = columns.length > 1 ? columns[1].split("\\|", -1) : new String[0];
            ArrayList<String> searchable = new ArrayList<>();
            searchable.add(normalized);
            for (String alias : aliases) {
                String key = CreatorNameMatcher.normalized(alias);
                if (!key.isEmpty() && !searchable.contains(key)) searchable.add(key);
            }
            rows.add(new Entry(name, searchable));
        }
        return new BundledCreatorIndex(rows);
    }

    int size() { return entries.size(); }

    String canonicalKey(String name) {
        String normalized = CreatorNameMatcher.normalized(name);
        if (normalized.isEmpty()) return "";
        String exact = canonicalByAlias.get(normalized);
        if (exact != null) return exact;

        String compact = compact(normalized);
        if (compact.length() < 8 || ambiguousSafeVariants.contains(compact)) return normalized;
        String resolved = canonicalBySafeVariant.get(compact);
        return resolved == null ? normalized : resolved;
    }

    String canonicalName(String name) {
        String key = canonicalKey(name);
        Entry entry = byName.get(key);
        return entry == null ? (name == null ? "" : name.trim()) : entry.name;
    }

    boolean isCanonicalSpelling(String name) {
        String normalized = CreatorNameMatcher.normalized(name);
        return !normalized.isEmpty() && normalized.equals(canonicalKey(name));
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace(" ", "");
    }

    int rank(NativeContentItem item, String query) {
        Entry entry = byName.get(CreatorNameMatcher.normalized(item.title));
        int score = Math.min(CreatorNameMatcher.rank(item.title, query),
                CreatorNameMatcher.rank(item.searchQuery, query));
        if (entry != null) for (String alias : entry.searchable) {
            score = Math.min(score, CreatorNameMatcher.rank(alias, query));
        }
        return score;
    }

    List<NativeContentItem> matching(String query, int limit) {
        ArrayList<Match> matches = new ArrayList<>();
        for (Entry entry : entries) {
            int rank = Integer.MAX_VALUE;
            for (String alias : entry.searchable) {
                rank = Math.min(rank, CreatorNameMatcher.rank(alias, query));
            }
            if (rank != Integer.MAX_VALUE) matches.add(new Match(entry, rank));
        }
        matches.sort(Comparator.comparingInt((Match match) -> match.rank)
                .thenComparing(match -> match.entry.searchable.get(0)));
        ArrayList<NativeContentItem> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, matches.size()); i++) {
            String name = matches.get(i).entry.name;
            out.add(new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                    "", "", "", "", "", "", name));
        }
        return out;
    }

    private static final class Entry {
        final String name;
        final List<String> searchable;
        Entry(String name, List<String> searchable) {
            this.name = name;
            this.searchable = searchable;
        }
    }
    private static final class Match {
        final Entry entry;
        final int rank;
        Match(Entry entry, int rank) { this.entry = entry; this.rank = rank; }
    }
}
