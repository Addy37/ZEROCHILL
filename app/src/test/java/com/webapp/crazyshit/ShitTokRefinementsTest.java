package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.content.Context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokRefinementsTest {
    private Activity activity;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.prefs(activity).edit().clear().commit();
    }

    @Test public void preferenceLabelsChangeWithoutTouchingSavedFeeds() {
        ContentPreference.set(activity, ContentPreference.MEN);
        ShitTokContentPreference.set(activity, ContentPreference.WOMEN);
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);

        assertEquals("Chaos", label(strip, "chaos"));
        assertEquals("Straight", label(strip, "ladies"));
        assertEquals("Gay", label(strip, "guys"));
        assertEquals("Trans", label(strip, "trans"));
        assertEquals("Groups", label(strip, "groups"));
        assertTrue(((TextView) strip.findViewWithTag("shittok_category_ladies")).isSelected());
        assertEquals(ContentPreference.MEN, ContentPreference.get(activity));

        strip.findViewWithTag("shittok_category_cell_groups").performClick();
        assertEquals(ContentPreference.GROUPS, ShitTokContentPreference.get(activity));
        assertEquals(ShitTokCategoryStrip.Tab.GROUPS, ShitTokCategoryStrip.Tab.selected(activity));
        assertEquals("groups", ShitTokCategoryStrip.Tab.GROUPS.value);
        assertEquals(ContentPreference.GROUPS, ShitTokCategoryStrip.Tab.GROUPS.preference);
        assertEquals(ContentPreference.MEN, ContentPreference.get(activity));
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "fapello"));
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "baddiehub"));
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "erome"));
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "kaotic"));
    }

    @Test public void sourceMarkUsesPlaybackProviderNotCreatorLabel() {
        NativeContentItem erome = clip(
                "https://www.erome.com/a/Ab12Cd#clip",
                "https://v1.erome.com/video.mp4",
                "Creator Name",
                "OnlyFap").withSourceMedia(
                "https://v1.erome.com/video.mp4", "https://www.erome.com/a/Ab12Cd");
        assertEquals("Erome", ShitTokSourceMark.of(erome).name);
        assertEquals(R.drawable.ic_source_erome, ShitTokSourceMark.of(erome).iconRes);

        NativeContentItem bunkr = clip(
                "https://bunkr.fi/v/clip", "", "Creator Name", "OnlyFap");
        assertEquals("Bunkr", ShitTokSourceMark.of(bunkr).name);
        assertEquals(R.drawable.ic_source_bunkr, ShitTokSourceMark.of(bunkr).iconRes);

        NativeContentItem baddie = clip(
                "https://baddiehub.com/video/1", "", "Creator Name", "OnlyFap");
        assertEquals("BaddieHub", ShitTokSourceMark.of(baddie).name);
        assertEquals(R.drawable.ic_source_baddiehub, ShitTokSourceMark.of(baddie).iconRes);

        assertEquals("Fapello", ShitTokSourceMark.of(clip(
                "https://fapello.com/model/1/", "", "Creator Name", "OnlyFap")).name);
        assertEquals("OnlyHaven", ShitTokSourceMark.of(clip(
                "https://e1.cum.st/media/clip.mp4", "", "onlyhaven", "")).name);
        assertEquals(R.drawable.ic_source_kaotic, ShitTokSourceMark.of(clip(
                "https://kaotic.com/video/1", "", "Kaotic", "")).iconRes);
        assertEquals(R.drawable.ic_source_efukt, ShitTokSourceMark.of(clip(
                "https://efukt.com/1", "", "efukt", "")).iconRes);
        assertEquals(R.drawable.ic_source_shitshow, ShitTokSourceMark.of(clip(
                "https://crazyshit.com/shitshow/1", "", "Shit Show", "")).iconRes);
        assertEquals(R.drawable.ic_source_crazyshit, ShitTokSourceMark.of(clip(
                "https://crazyshit.com/video/1", "", "CrazyShit", "")).iconRes);
        assertNull(ShitTokSourceMark.of(null));
        assertNull(ShitTokSourceMark.of(clip("https://example.com/video", "", "Someone", "")));
    }

    @Test public void eromeSwipeUsesAlbumIdAndExistingReturnGesture() {
        NativeContentItem clip = clip(
                "https://www.erome.com/a/Ab12Cd#tok",
                "",
                "Not A Creator Match",
                "OnlyFap").withSourceMedia("", "https://www.erome.com/a/Ab12Cd");
        assertEquals("https://www.erome.com/a/Ab12Cd", ShitTokEromeGallery.albumUrl(clip));
        assertEquals("", ShitTokEromeGallery.albumUrl(clip(
                "https://fapello.com/model/1/", "", "Not A Creator Match", "OnlyFap")));

        assertTrue(ChaosFeedView.shouldOpenCreatorGallerySwipe(-80f, 10f, 72f));
        assertFalse(ChaosFeedView.shouldOpenCreatorGallerySwipe(-40f, 10f, 72f));
        assertFalse(ChaosFeedView.shouldOpenCreatorGallerySwipe(-80f, 90f, 72f));
        assertTrue(NativeFeedBrowserActivity.allowsShitTokReturnGesture(
                false, true, true, false, false));
        assertFalse(NativeFeedBrowserActivity.allowsShitTokReturnGesture(
                true, false, true, false, false));
        assertTrue(NativeFeedBrowserActivity.allowsShitTokReturnGesture(
                true, false, true, true, false));
        assertFalse(NativeFeedBrowserActivity.allowsShitTokReturnGesture(
                false, true, true, true, true));
        assertFalse(NativeFeedBrowserActivity.allowsShitTokReturnGesture(
                false, false, true, true, false));
    }

    @Test public void galleryPillUsesKnownTotalsAndFallsBackWithoutGuessing() {
        NativeContentItem erome = clip(
                "https://www.erome.com/a/Ab12Cd#tok", "", "Uploader", "")
                .withSourceMedia("https://v1.erome.com/video.mp4", "https://www.erome.com/a/Ab12Cd");
        ShitTokGalleryAccess.Presentation missing = ShitTokGalleryAccess.of(erome, null);
        assertEquals(ShitTokGalleryAccess.Kind.ALBUM, missing.kind);
        assertEquals("View album", missing.label);
        assertTrue(missing.contentDescription.contains("Swipe right"));

        NativeContentItem counted = erome.withGalleryDetail(
                ShitTokGalleryAccess.countsLabel("12 videos · 24 pictures"));
        NativeContentItem creator = new NativeContentItem(
                NativeContentItem.KIND_CREATOR, "Uploader", "https://example.com/uploader",
                "", "", "", "", "9 posts");
        ShitTokGalleryAccess.Presentation album = ShitTokGalleryAccess.of(counted, creator);
        assertEquals("12 videos · 24 photos", album.label);
        assertEquals(ShitTokGalleryAccess.Kind.ALBUM, album.kind);
        assertEquals("", ShitTokGalleryAccess.countsLabel("one loaded page"));
        assertEquals("", ShitTokGalleryAccess.countsLabel("0 videos · 0 photos"));

        NativeContentItem fapello = clip(
                "https://fapello.com/model/1/", "", "Name", "OnlyFap");
        assertFalse(ShitTokGalleryAccess.of(fapello, null).visible());
        assertEquals("View creator", ShitTokGalleryAccess.of(fapello, new NativeContentItem(
                NativeContentItem.KIND_CREATOR, "Name", "https://fapello.com/name/",
                "", "", "", "", "Fapello")).label);
        assertEquals("1,482 posts", ShitTokGalleryAccess.of(fapello, new NativeContentItem(
                NativeContentItem.KIND_CREATOR, "Name", "https://e1.cum.st/creators/fansly/name",
                "", "", "", "", "OnlyHaven · Fansly · 1,482 posts")).label);
        assertEquals("36 photos", ShitTokGalleryAccess.countsLabel("WikiFeet X · 36 photos"));
        assertEquals("8 videos", ShitTokGalleryAccess.countsLabel("8 videos"));
        assertEquals("1 video", ShitTokGalleryAccess.countsLabel("1 video"));

        NativeContentItem copied = ShitTokOrientation.copy(counted, 0.56f);
        assertEquals("12 videos · 24 photos", copied.galleryDetail);
        assertEquals(counted.sourceMediaUrl, copied.sourceMediaUrl);
        assertEquals("https://www.erome.com/a/Ab12Cd", copied.sourcePageUrl);
    }

    @Test public void accessHintStopsAfterAFewAppearancesOrASuccessfulSwipe() {
        activity.getSharedPreferences("shittok_access_hint", Context.MODE_PRIVATE).edit().clear().commit();
        assertTrue(ShitTokAccessHint.noteAppearance(activity, "clip-a"));
        assertFalse(ShitTokAccessHint.noteAppearance(activity, "clip-a"));
        assertTrue(ShitTokAccessHint.noteAppearance(activity, "clip-b"));
        assertTrue(ShitTokAccessHint.noteAppearance(activity, "clip-c"));
        assertFalse(ShitTokAccessHint.noteAppearance(activity, "clip-d"));
        activity.getSharedPreferences("shittok_access_hint", Context.MODE_PRIVATE).edit().clear().commit();
        assertTrue(ShitTokAccessHint.noteAppearance(activity, "clip-a"));
        ShitTokAccessHint.learned(activity);
        assertFalse(ShitTokAccessHint.noteAppearance(activity, "clip-b"));
    }

    private static String label(ShitTokCategoryStrip strip, String value) {
        return ((TextView) strip.findViewWithTag("shittok_category_" + value)).getText().toString();
    }

    private static NativeContentItem clip(String url, String image, String uploader, String description) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Clip", url, image, "", uploader, "", description);
    }
}
