package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapCombinedPreferenceTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Before public void clean() {
        ContentPreference.prefs(context).edit().clear().commit();
        context.getSharedPreferences("onlyhaven_confirmed_categories_v1", 0).edit().clear().commit();
    }

    @Test public void legacyChoicesAppearCombinedWithoutRewritingSavedValues() {
        for (ContentPreference legacy : Arrays.asList(ContentPreference.TRANS_WOMEN, ContentPreference.TRANS_MEN)) {
            ContentPreference.set(context, legacy);
            assertEquals(ContentPreference.TRANS, ContentPreference.getOnlyFap(context));
            assertEquals(legacy, ContentPreference.get(context));
            assertFalse(ContentPreference.setOnlyFap(context, ContentPreference.TRANS));
            assertEquals(legacy.value, ContentPreference.prefs(context).getString(ContentPreference.KEY, ""));
        }
    }

    @Test public void combinedSelectionPreservesIndependentShitTokAndPawchive() {
        ContentPreference.set(context, ContentPreference.MEN);
        assertTrue(ContentPreference.setOnlyFap(context, ContentPreference.TRANS));
        assertEquals(ContentPreference.MEN, ShitTokContentPreference.get(context));
        assertFalse(ShitTokContentPreference.set(context, ContentPreference.TRANS));
        assertTrue(ContentPreference.setOnlyFap(context, ContentPreference.PAWCHIVE));
        assertEquals(ContentPreference.PAWCHIVE, ContentPreference.getOnlyFap(context));
        assertTrue(ContentPreference.setOnlyFap(context, ContentPreference.TRANS));
        assertEquals(ContentPreference.TRANS, ContentPreference.getOnlyFap(context));
        assertEquals(ContentPreference.MEN, ShitTokContentPreference.get(context));
    }

    @Test public void confirmedMembershipIsUnionNotUnfilteredFallback() {
        OnlyHavenRepository.Creator a = creator("a");
        OnlyHavenRepository.Creator b = creator("b");
        OnlyHavenRepository.Creator c = creator("c");
        CreatorCategoryIndex.remember(context, ContentPreference.TRANS_WOMEN, Collections.singletonList(a));
        CreatorCategoryIndex.remember(context, ContentPreference.TRANS_MEN, Collections.singletonList(b));
        CreatorCategoryIndex.remember(context, ContentPreference.WOMEN, Collections.singletonList(c));
        assertEquals(Arrays.asList(OnlyHavenRepository.creatorItem(a).url, OnlyHavenRepository.creatorItem(b).url),
            urls(CreatorCategoryIndex.filter(context, ContentPreference.TRANS,
                Arrays.asList(OnlyHavenRepository.creatorItem(a), OnlyHavenRepository.creatorItem(b), OnlyHavenRepository.creatorItem(c)))));
        assertFalse(CreatorCategoryIndex.includes(context, ContentPreference.TRANS_WOMEN, OnlyHavenRepository.creatorItem(b)));
    }

    @Test public void pagesCoverBothLegacyStreamsWithoutOverlappingOffsets() throws Exception {
        List<String> calls = new ArrayList<>();
        OnlyHavenRepository.CategoryPageSource source = (mode, count, offset) -> {
            calls.add(mode.value + ":" + count + ":" + offset);
            List<OnlyHavenRepository.Creator> items = new ArrayList<>();
            for (int i = 0; i < count; i++) items.add(creator(mode.value + "-" + (offset + i)));
            return new OnlyHavenRepository.CategoryPage(items, true);
        };
        OnlyHavenRepository.CategoryPage first = OnlyHavenRepository.combinedCategoryPage(16, 0, source);
        OnlyHavenRepository.CategoryPage next = OnlyHavenRepository.combinedCategoryPage(16, 16, source);
        assertEquals(Arrays.asList("trans_women:8:0", "trans_men:8:0", "trans_women:8:8", "trans_men:8:8"), calls);
        assertEquals(16, first.creators.size());
        assertEquals(16, next.creators.size());
        assertEquals("trans_women-0", first.creators.get(0).id);
        assertEquals("trans_men-0", first.creators.get(1).id);
        assertEquals("trans_women-8", next.creators.get(0).id);
    }

    @Test public void oneUnavailableStreamDoesNotEraseOtherAndDuplicateProfilesCollapse() throws Exception {
        OnlyHavenRepository.CategoryPage partial = OnlyHavenRepository.combinedCategoryPage(16, 0, (mode, count, offset) -> {
            if (mode == ContentPreference.TRANS_WOMEN) throw new IOException("fixture unavailable");
            return new OnlyHavenRepository.CategoryPage(Collections.singletonList(creator("b")), false);
        });
        assertEquals(1, partial.creators.size());
        assertTrue(partial.hasMore);
        OnlyHavenRepository.CategoryPage exhausted = OnlyHavenRepository.combinedCategoryPage(16, 0, (mode, count, offset) ->
            new OnlyHavenRepository.CategoryPage(Collections.singletonList(creator("same")), false));
        assertEquals(1, exhausted.creators.size());
        assertFalse(exhausted.hasMore);
    }

    private static OnlyHavenRepository.Creator creator(String id) {
        return new OnlyHavenRepository.Creator("onlyfans", id, id, "https://cum.st/creators/onlyfans/" + id, "");
    }

    private static List<String> urls(List<NativeContentItem> items) {
        List<String> result = new ArrayList<>();
        for (NativeContentItem item : items) result.add(item.url);
        return result;
    }
}
