package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** One bounded sequential category load. Publication still passes the page's UI generation gate. */
final class OnlyFapCategoryLoader {
    static final int PAGE_SIZE = 16;
    static final int SHELF_COUNT = 3;

    interface Source {
        Batch fetch(ContentPreference preference, int limit, int offset) throws Exception;
    }
    interface Listener {
        void shelf(int index, List<NativeContentItem> items, boolean failed);
        void finished(boolean failed);
    }
    static final class Batch {
        final List<NativeContentItem> items;
        final boolean hasMore;
        Batch(List<NativeContentItem> items, boolean hasMore) {
            this.items = items;
            this.hasMore = hasMore;
        }
    }

    static void load(ContentPreference preference, Source source, BooleanSupplier current, Listener listener) {
        if (preference == ContentPreference.ALL) throw new IllegalArgumentException("Category required");
        Set<String> seen = new HashSet<>();
        boolean failed = false;
        for (int index = 0; index < SHELF_COUNT; index++) {
            if (!live(current)) return;
            Batch batch;
            try {
                batch = source.fetch(preference, PAGE_SIZE, index * PAGE_SIZE);
            } catch (Exception error) {
                if (!live(current)) return;
                failed = true;
                listener.shelf(index, Collections.emptyList(), true);
                continue; // A failed page must not erase a successful shelf or prevent the next page.
            }
            if (!live(current)) return;
            List<NativeContentItem> unique = new ArrayList<>();
            for (NativeContentItem item : batch.items) {
                if (item == null) continue;
                // Canonical OnlyHaven URLs encode service + provider ID. Never deduplicate by display name.
                String identity = identity(item);
                if (!identity.isEmpty() && seen.add(identity)) unique.add(item);
            }
            listener.shelf(index, Collections.unmodifiableList(unique), false);
            if (!batch.hasMore) break;
        }
        if (live(current)) listener.finished(failed);
    }

    static String identity(NativeContentItem item) {
        return item.url.split("[?#]", 2)[0].replaceAll("/+$", "").toLowerCase(java.util.Locale.US);
    }

    private static boolean live(BooleanSupplier current) {
        return !Thread.currentThread().isInterrupted() && current.getAsBoolean();
    }
}
