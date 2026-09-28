package com.webapp.crazyshit;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small reviewed aliases. No catalog parsing or approximate matching on UI paths. */
final class CreatorIdentity {
    private static final Map<String, String> ALIASES;
    private static final Map<String, List<String>> GROUPS;

    static {
        Map<String, String> aliases = new HashMap<>();
        Map<String, List<String>> groups = new HashMap<>();
        register(aliases, groups, "Belle Delphine", "Belle Del", "Belle Delph",
                "Belle Delphi", "Belle Delphin", "belledelphiine");
        register(aliases, groups, "Taliya & Gustavo", "Taliyaandgustavo");
        register(aliases, groups, "Sasha Foxx", "Sasha Foxxx");
        ALIASES = Collections.unmodifiableMap(aliases);
        GROUPS = Collections.unmodifiableMap(groups);
    }

    private CreatorIdentity() { }

    private static void register(Map<String, String> aliases, Map<String, List<String>> groups,
                                 String canonical, String... variants) {
        String key = CreatorNameMatcher.normalized(canonical);
        List<String> members = new java.util.ArrayList<>();
        members.add(canonical.toLowerCase(Locale.US));
        members.add(key);
        for (String variant : variants) {
            members.add(variant.toLowerCase(Locale.US));
            members.add(CreatorNameMatcher.normalized(variant));
        }
        groups.put(key, Collections.unmodifiableList(members));
        aliases.put(key, key);
        for (String variant : variants) aliases.put(CreatorNameMatcher.normalized(variant), key);
    }

    static String key(String name) {
        String normalized = CreatorNameMatcher.normalized(name == null ? "" :
                name.replaceAll("(?i)&amp;", "&"));
        return ALIASES.getOrDefault(normalized, normalized);
    }

    static boolean reviewed(String name) {
        return GROUPS.containsKey(key(name));
    }

    static String display(String name) {
        switch (key(name)) {
            case "belle delphine": return "Belle Delphine";
            case "taliya gustavo": return "Taliya & Gustavo";
            case "sasha foxx": return "Sasha Foxx";
            default: return name;
        }
    }

    static boolean hasStoredAlias(java.util.Set<String> stored, String name) {
        List<String> members = storedKeys(name);
        if (members == null) return false;
        for (String member : members) if (stored.contains(member)) return true;
        return false;
    }

    static List<String> storedKeys(String name) { return GROUPS.get(key(name)); }

    static boolean sameReviewedIdentity(String a, String b) {
        return reviewed(a) && key(a).equals(key(b));
    }
}
