package com.webapp.crazyshit;

/**
 * Manifest-translucent host for the live OnlyFap circular reveal. Android knows
 * before launch that this Activity must leave its source surface visible.
 * Browsing, saved state, media navigation and pagination remain in the native host.
 */
public final class OnlyFapGalleryActivity extends NativeFeedBrowserActivity {
    @Override protected void onCreate(android.os.Bundle state) {
        // Colors only: preserve manifest translucency before and after recreation,
        // including Android 8.1/9 where onActivityPreCreated is unavailable.
        AppearanceTheme.applyAccentOverlay(this);
        super.onCreate(state);
    }
}
