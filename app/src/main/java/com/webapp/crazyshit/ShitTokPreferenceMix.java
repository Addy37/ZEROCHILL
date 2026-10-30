package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Category eligibility is evidence from the provider endpoint, never a ranking hint. */
final class ShitTokPreferenceMix {
    static boolean eligible(NativeContentItem item, ContentPreference mode, Set<String> verified) {
        return item != null && (mode == ContentPreference.ALL || verified.contains(mode.value + "|" + item.url));
    }
    static List<NativeContentItem> order(List<NativeContentItem> items, Predicate<NativeContentItem> matches) {
        List<NativeContentItem> result = new ArrayList<>();
        for (NativeContentItem item : items) if (matches.test(item)) result.add(item);
        return result;
    }
}
