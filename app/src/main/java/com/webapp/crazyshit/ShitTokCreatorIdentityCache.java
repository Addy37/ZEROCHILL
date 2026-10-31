package com.webapp.crazyshit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Worker-only successful identity memoization. A catalog miss cannot outlive new discovery. */
final class ShitTokCreatorIdentityCache {
    static final int LIMIT = 128;
    private final Map<String, NativeContentItem> successes = new LinkedHashMap<>(32, .75f, true);
    synchronized NativeContentItem resolve(String key, Supplier<NativeContentItem> lookup) {
        NativeContentItem result = successes.get(key);
        long token = ShitTokPoolDiagnostics.modeToken();
        ShitTokPoolDiagnostics.modeEvent(token, result == null ? "creatorIdentityMisses" : "creatorIdentityHits", 1);
        if (result != null) return result;
        result = lookup.get();
        if (result != null && !key.isEmpty()) {
            successes.put(key, result);
            while (successes.size() > LIMIT) successes.remove(successes.keySet().iterator().next());
        }
        return result;
    }
    synchronized int size() { return successes.size(); }
}
