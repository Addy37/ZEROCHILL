package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SpotlightSearchTest {
    @Test public void eachProviderRemembersItsOwnLayoutAndStartsFromTheRecommendedDefault() {
        var host = Robolectric.buildActivity(android.app.Activity.class).setup();
        assertEquals(SearchLayoutPreference.Mode.GRID, SearchLayoutPreference.fallback(SearchPanel.Scope.SHOWS));
        assertEquals(SearchLayoutPreference.Mode.GRID, SearchLayoutPreference.fallback(SearchPanel.Scope.ONLYFAP));
        assertEquals(SearchLayoutPreference.Mode.GRID, SearchLayoutPreference.fallback(SearchPanel.Scope.BUNKR_ALBUMS));
        assertEquals(SearchLayoutPreference.Mode.GRID, SearchLayoutPreference.fallback(SearchPanel.Scope.EROME));
        assertEquals(SearchLayoutPreference.Mode.LIST, SearchLayoutPreference.fallback(SearchPanel.Scope.XMEGADRIVE));
        assertEquals(SearchCardStyle.LANDSCAPE,
                SearchCardStyle.forScope(SearchPanel.Scope.SHOWS, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.NATURAL,
                SearchCardStyle.forScope(SearchPanel.Scope.ONLYFAP, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.NATURAL,
                SearchCardStyle.forScope(SearchPanel.Scope.BUNKR_ALBUMS, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.NATURAL,
                SearchCardStyle.forScope(SearchPanel.Scope.EROME, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.NATURAL,
                SearchCardStyle.forScope(SearchPanel.Scope.XMEGADRIVE, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.NATURAL,
                SearchCardStyle.forScope(SearchPanel.Scope.BADDIEHUB, SearchLayoutPreference.Mode.GRID));
        assertEquals(SearchCardStyle.LIST,
                SearchCardStyle.forScope(SearchPanel.Scope.EROME, SearchLayoutPreference.Mode.LIST));
        assertEquals(SearchCardStyle.LIST,
                SearchCardStyle.forScope(SearchPanel.Scope.XMEGADRIVE, SearchLayoutPreference.Mode.LIST));
        assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP,
                SearchCardStyle.NATURAL.thumbnailScale());
        assertTrue(SearchCardStyle.NATURAL.naturalAspect());
        assertFalse(SearchCardStyle.LANDSCAPE.naturalAspect());
        assertTrue(SearchProviderControls.supported(SearchPanel.Scope.BUNKR_ALBUMS).isEmpty());
        SearchLayoutPreference.set(host.get(), SearchPanel.Scope.EROME, SearchLayoutPreference.Mode.LIST);
        SearchLayoutPreference.set(host.get(), SearchPanel.Scope.SHOWS, SearchLayoutPreference.Mode.GRID);
        assertEquals(SearchLayoutPreference.Mode.LIST,
                SearchLayoutPreference.get(host.get(), SearchPanel.Scope.EROME));
        assertEquals(SearchLayoutPreference.Mode.GRID,
                SearchLayoutPreference.get(host.get(), SearchPanel.Scope.SHOWS));
        assertEquals(SearchLayoutPreference.Mode.LIST,
                SearchLayoutPreference.get(host.get(), SearchPanel.Scope.XMEGADRIVE));
    }

    @Test public void relevanceKeepsCloserTitlesFirstWithoutDroppingResults() {
        NativeContentItem later = item("Storm watch");
        NativeContentItem exact = item("coast");
        NativeContentItem prefix = item("Coast light");
        List<NativeContentItem> ordered = SearchRelevance.prefer("coast", Arrays.asList(later, exact, prefix));
        assertEquals(3, ordered.size());
        assertEquals("coast", ordered.get(0).title);
        assertEquals("Coast light", ordered.get(1).title);
        assertEquals("Storm watch", ordered.get(2).title);
    }

    @Test public void bunkrIndexReportsAnotherPageOnlyWhenTheProviderSaysSo() throws Exception {
        BunkrIndexSearch.Page first = parse("{\"page\":1,\"pages\":3,\"results\":["
                + "{\"album_slug\":\"album_123\",\"album_name\":\"Coast\",\"file_count\":2}]}");
        assertTrue(first.hasNext());
        assertEquals(1, first.albums.size());
        assertFalse(parse("{\"page\":3,\"pages\":3,\"results\":[]}").hasNext());
        assertFalse(parse("{\"page\":1,\"results\":[]}").hasNext());
    }

    private static BunkrIndexSearch.Page parse(String body) throws Exception {
        List<NativeContentItem> albums = BunkrIndexSearch.parseResults(body);
        org.json.JSONObject root = new org.json.JSONObject(body);
        int page = root.optInt("page", 1);
        int pages = root.has("pages") ? root.optInt("pages") : -1;
        return new BunkrIndexSearch.Page(albums, page, pages);
    }

    private static NativeContentItem item(String title) {
        return new NativeContentItem(NativeContentItem.KIND_SERIES, title,
                "https://www.erome.com/a/" + title.hashCode(), "", "", "", "");
    }
}
