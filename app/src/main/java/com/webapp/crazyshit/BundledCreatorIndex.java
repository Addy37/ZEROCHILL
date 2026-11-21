package com.webapp.crazyshit;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Compact, immutable creator names. Load once off the main thread; never store this in preferences. */
final class BundledCreatorIndex {
    private static volatile BundledCreatorIndex cached;
    private final List<Entry> entries;
    private final Map<String, Entry> byName = new HashMap<>();
    private final Map<String, Integer> exactCounts = new HashMap<>();

    private BundledCreatorIndex(List<Entry> entries) {
        this.entries = entries;
        for (Entry entry : entries) {
            byName.put(entry.searchable.get(0), entry);
            for (String term : entry.searchable) exactCounts.merge(term, 1, Integer::sum);
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
            ArrayList<String> reviewedAliases = new ArrayList<>();
            searchable.add(normalized);
            for (String alias : aliases) {
                String key = CreatorNameMatcher.normalized(alias);
                if (key.isEmpty()) continue;
                if (!searchable.contains(key)) searchable.add(key);
                reviewedAliases.add(alias.trim());
            }
            rows.add(new Entry(name, searchable, reviewedAliases));
        }
        return new BundledCreatorIndex(rows);
    }

    int size() { return entries.size(); }

    /** Reviewed aliases only; a collision must never pick whichever row was loaded first. */
    NativeContentItem exact(String query) {
        String wanted = CreatorNameMatcher.normalized(query);
        Entry found = null;
        for (Entry entry : entries) {
            if (!entry.searchable.contains(wanted)) continue;
            if (found != null && found != entry) return null;
            found = entry;
        }
        return found == null ? null : item(found);
    }

    boolean ambiguous(String query) {
        return exactCounts.getOrDefault(CreatorNameMatcher.normalized(query), 0) > 1;
    }

    int rank(NativeContentItem item, String query) {
        Entry entry = byName.get(CreatorNameMatcher.normalized(item.title));
        int score = OnlyFapCreatorResults.rank(item, query);
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
            out.add(item(matches.get(i).entry));
        }
        return out;
    }

    private NativeContentItem item(Entry entry) {
        ArrayList<String> aliases = new ArrayList<>();
        for (String alias : entry.aliases) if (!ambiguous(alias)) aliases.add(alias);
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, entry.name,
                "", "", "", "", "", "", entry.name)
                .withCreatorIdentity(aliases, java.util.Collections.emptyList());
    }

    private static final class Entry {
        final String name;
        final List<String> searchable;
        final List<String> aliases;

        Entry(String name, List<String> searchable, List<String> aliases) {
            this.name = name;
            this.searchable = searchable;
            this.aliases = aliases;
        }
    }
    private static final class Match {
        final Entry entry;
        final int rank;
        Match(Entry entry, int rank) { this.entry = entry; this.rank = rank; }
    }
}
