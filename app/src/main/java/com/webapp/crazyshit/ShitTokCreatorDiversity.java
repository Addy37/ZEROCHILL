package com.webapp.crazyshit;

import java.util.*;

/** Bounded sequencing, after SmartRanker ordering. Never drops an eligible candidate. */
final class ShitTokCreatorDiversity {
    static final int WINDOW = 5;
    private final ArrayDeque<String> recent = new ArrayDeque<>();

    void remember(NativeContentItem item) {
        String key = key(item);
        if (key.isEmpty()) return;
        recent.addLast(key);
        while (recent.size() > WINDOW) recent.removeFirst();
    }
    static String key(NativeContentItem item) {
        return CreatorIdentity.key(ShitTokCreatorMetadata.creatorName(item));
    }
    List<NativeContentItem> order(List<NativeContentItem> ranked) {
        ArrayList<NativeContentItem> remaining = new ArrayList<>(ranked), result = new ArrayList<>();
        ArrayDeque<String> history = new ArrayDeque<>(recent);
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestValue = -Double.MAX_VALUE;
            boolean alternative = false;
            String last = history.peekLast();
            for (NativeContentItem item : remaining)
                if (!key(item).isEmpty() && !key(item).equals(last)) alternative = true;
            for (int i = 0; i < remaining.size(); i++) {
                NativeContentItem item = remaining.get(i);
                String key = key(item);
                if (alternative && !key.isEmpty() && key.equals(last)) continue;
                // Personalization survives; short-term repeats lose a bounded four points.
                double value = ShitTokSmartRanker.score(item) - (history.contains(key) ? 4 : 0);
                if (value > bestValue) { best = i; bestValue = value; }
            }
            NativeContentItem next = remaining.remove(best);
            result.add(next);
            String key = key(next);
            if (!key.isEmpty()) { history.addLast(key); while (history.size() > WINDOW) history.removeFirst(); }
        }
        return result;
    }
}
