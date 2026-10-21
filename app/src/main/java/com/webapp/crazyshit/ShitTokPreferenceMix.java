package com.webapp.crazyshit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** One reliably matched clip, then two ordinary clips. Nothing is excluded. */
final class ShitTokPreferenceMix {
    static List<NativeContentItem> order(List<NativeContentItem> items,
            Predicate<NativeContentItem> matches) {
        ArrayDeque<NativeContentItem> preferred = new ArrayDeque<>(), normal = new ArrayDeque<>();
        for (NativeContentItem item : items) (matches.test(item) ? preferred : normal).add(item);
        List<NativeContentItem> result = new ArrayList<>(items.size());
        while (!preferred.isEmpty() || !normal.isEmpty()) {
            if (!preferred.isEmpty()) result.add(preferred.removeFirst());
            for (int i = 0; i < 2 && !normal.isEmpty(); i++) result.add(normal.removeFirst());
            if (preferred.isEmpty()) { result.addAll(normal); break; }
        }
        return result;
    }
}
