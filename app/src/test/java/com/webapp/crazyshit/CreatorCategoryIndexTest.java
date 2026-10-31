package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class CreatorCategoryIndexTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final String profile = "https://cum.st/creators/onlyfans/confirmed-1";

    @Before public void clear() {
        context.getSharedPreferences("onlyhaven_confirmed_categories_v1", 0).edit().clear().commit();
    }

    @Test public void onlyProviderConfirmedCanonicalProfileEntersModeSearch() {
        NativeContentItem known = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Verified Creator", profile, "", "", "", "", "OnlyHaven", "Verified Creator");
        NativeContentItem guessed = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Verified Creator", "https://fapello.com/verified-creator/", "", "", "", "", "Fapello", "Verified Creator");
        assertTrue(CreatorCategoryIndex.filter(context, ContentPreference.MEN,
                Arrays.asList(known, guessed)).isEmpty());
        CreatorCategoryIndex.remember(context, ContentPreference.MEN,
                Collections.singletonList(new OnlyHavenRepository.Creator("onlyfans", "confirmed-1",
                        "Verified Creator", profile, "")));
        assertEquals(Collections.singletonList(known), CreatorCategoryIndex.filter(
                context, ContentPreference.MEN, Arrays.asList(known, guessed)));
        assertTrue(CreatorCategoryIndex.filter(context, ContentPreference.WOMEN,
                Arrays.asList(known, guessed)).isEmpty());
        assertEquals(2, CreatorCategoryIndex.filter(context, ContentPreference.ALL,
                Arrays.asList(known, guessed)).size());
    }

    @Test public void independentConfirmedCategoriesRemainAvailableOnBackNavigation() {
        NativeContentItem known = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Verified Creator", profile, "", "", "", "", "OnlyHaven", "Verified Creator");
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator("onlyfans",
                "confirmed-1", "Verified Creator", profile, "");
        CreatorCategoryIndex.remember(context, ContentPreference.MEN, Collections.singletonList(creator));
        CreatorCategoryIndex.remember(context, ContentPreference.GROUPS, Collections.singletonList(creator));
        assertTrue(CreatorCategoryIndex.includes(context, ContentPreference.MEN, known));
        assertTrue(CreatorCategoryIndex.includes(context, ContentPreference.GROUPS, known));
    }
    @Test public void filteredColdSeedRequiresPersistedMembershipForExactOnlyHavenProfile() {
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator("onlyfans", "confirmed-1",
                "Verified Creator", profile, "");
        CreatorCatalog.remember(context, Collections.singletonList(OnlyHavenRepository.creatorItem(creator)));
        CreatorCategoryIndex.remember(context, ContentPreference.WOMEN, Collections.singletonList(creator));
        ShitTokFilteredInventory.Backend backend = ShitTokFilteredInventory.backend(context, new ShitShowTapSource());
        assertTrue(backend.known(ContentPreference.MEN).isEmpty());
        List<OnlyHavenRepository.Creator> warm = backend.known(ContentPreference.WOMEN);
        assertEquals(1, warm.size()); assertEquals(profile, warm.get(0).url);
        assertEquals("Verified Creator", warm.get(0).name);
    }
}
