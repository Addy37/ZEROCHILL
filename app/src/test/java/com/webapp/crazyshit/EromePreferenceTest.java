package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.webkit.CookieManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class EromePreferenceTest {
    @Test public void onlyTheSitesFivePreferencesAreAddressableAndTheyStayIndependent() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        assertEquals(EromePreference.Choice.ALL, EromePreference.get(host.get()));
        assertEquals("https://www.erome.com/version/straight",
                EromePreference.versionUrl(EromePreference.Choice.STRAIGHT));
        assertEquals("https://www.erome.com/version/hentai",
                EromePreference.versionUrl(EromePreference.Choice.HENTAI));
        assertNull(EromePreference.Choice.fromSlug("lesbian"));
        assertEquals(5, SearchProviderControls.supported(SearchPanel.Scope.EROME).size());
        assertTrue(SearchProviderControls.supported(SearchPanel.Scope.SHOWS).isEmpty());
        assertTrue(SearchProviderControls.supported(SearchPanel.Scope.ONLYFAP).isEmpty());
        EromePreference.set(host.get(), EromePreference.Choice.GAY);
        assertEquals(EromePreference.Choice.GAY, EromePreference.get(host.get()));
        assertEquals("", EromePreference.applied(host.get()));
        assertFalse(EromePreference.hasVersionCookie(Collections.singletonList("erome_session=abc")));
        assertFalse(EromePreference.hasVersionCookie(Collections.singletonList("erome_version=; Max-Age=0")));
        assertTrue(EromePreference.hasVersionCookie(Arrays.asList(
                "XSRF-TOKEN=abc",
                "erome_version=opaque; Max-Age=31536000; path=/; domain=.erome.com; secure; httponly")));
    }

    @Test public void aVersionRedirectStoresTheCookieWithoutFilteringResultsLocally() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        EromePreference.set(host.get(), EromePreference.Choice.TRANS);
        assertTrue(new EromeRepository().acceptPreference(host.get(), EromePreference.Choice.TRANS, 302,
                Collections.singletonList("erome_version=opaque-trans; Max-Age=31536000; path=/; secure; httponly")));
        assertEquals("trans", EromePreference.applied(host.get()));
        String jar = CookieManager.getInstance().getCookie("https://www.erome.com/");
        assertTrue(jar.contains("erome_version=opaque-trans"));
        EromePreference.set(host.get(), EromePreference.Choice.ALL);
        assertEquals("", EromePreference.applied(host.get()));
        assertFalse(new EromeRepository().acceptPreference(host.get(), EromePreference.Choice.ALL, 403,
                Collections.singletonList("erome_version=nope")));
        assertEquals("", EromePreference.applied(host.get()));
    }

    @Test public void spotlightKeepsTheChoiceOnEromeOnly() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        SearchPanel panel = new SearchPanel(host.get(), new Intent().putExtra("scope", "erome"), true, () -> {});
        panel.create(null);
        try {
            View straight = find(panel.view, "Erome preference Straight");
            assertTrue(straight != null);
            straight.performClick();
            assertEquals(EromePreference.Choice.STRAIGHT, EromePreference.get(host.get()));
            find(panel.view, "Shows search").performClick();
            assertNull(find(panel.view, "Erome preference Gay"));
            assertEquals(EromePreference.Choice.STRAIGHT, EromePreference.get(host.get()));
        } finally {
            panel.close();
        }
    }

    private static View find(View root, String description) {
        if (root.getContentDescription() != null && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
}
