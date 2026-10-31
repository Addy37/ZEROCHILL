package com.webapp.crazyshit;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokCreatorIdentityCacheTest {
    private NativeContentItem creator(String name) {
        return new NativeContentItem("creator", name, "https://cum.st/creators/onlyfans/" + name, "", "", "", "");
    }
    @Test public void missBeforeCatalogDiscoveryCannotHideLaterVerifiedIdentity() {
        ShitTokCreatorIdentityCache cache = new ShitTokCreatorIdentityCache();
        AtomicInteger lookups = new AtomicInteger();
        assertNull(cache.resolve("jane", () -> { lookups.incrementAndGet(); return null; }));
        NativeContentItem known = creator("Jane");
        assertSame(known, cache.resolve("jane", () -> { lookups.incrementAndGet(); return known; }));
        for (int i = 0; i < 30; i++) assertSame(known, cache.resolve("jane", () -> { fail("Successful identity must be reused"); return null; }));
        assertEquals(2, lookups.get()); assertEquals(1, cache.size());
    }
    @Test public void successesStayBoundedAndEvictedIdentityCanBeResolvedAgain() {
        ShitTokCreatorIdentityCache cache = new ShitTokCreatorIdentityCache();
        for (int i = 0; i < 400; i++) { String name = "creator" + i; cache.resolve(name, () -> creator(name)); }
        assertEquals(ShitTokCreatorIdentityCache.LIMIT, cache.size());
        AtomicInteger lookup = new AtomicInteger();
        cache.resolve("creator0", () -> { lookup.incrementAndGet(); return creator("creator0"); });
        assertEquals(1, lookup.get()); assertEquals(ShitTokCreatorIdentityCache.LIMIT, cache.size());
    }
}
