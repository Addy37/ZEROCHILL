package com.webapp.crazyshit;

import java.text.Normalizer;
import java.util.Locale;

/** Shared matching rules for typed names, cached creators and favorite sorting. */
final class CreatorNameMatcher {
    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final java.util.regex.Pattern SEPARATORS = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    private CreatorNameMatcher() { }

    static String normalized(String value) {
        if (value == null) return "";
        String unmarked = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
        String separated = SEPARATORS.matcher(unmarked.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        return WHITESPACE.matcher(separated).replaceAll(" ");
    }

    static int rank(String name, String query) {
        String candidate = normalized(name);
        String needle = normalized(query);
        if (needle.isEmpty()) return 0;
        if (candidate.equals(needle)) return 0;
        String compactName = candidate.replace(" ", "");
        String compactQuery = needle.replace(" ", "");
        if (compactName.equals(compactQuery)) return 0;
        if (candidate.startsWith(needle) || compactName.startsWith(compactQuery)) return 1;
        if (candidate.contains(" " + needle)) return 2;
        if (candidate.contains(needle) || compactName.contains(compactQuery)) return 3;
        return Integer.MAX_VALUE;
    }
}
