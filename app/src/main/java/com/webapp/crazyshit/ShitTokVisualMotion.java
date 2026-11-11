package com.webapp.crazyshit;

/** View-only paging opacity. A canceled or reversed drag follows the same position. */
final class ShitTokVisualMotion {
    private ShitTokVisualMotion() {}

    static float pageAlpha(int page, int firstPage, float offset, boolean enabled) {
        if (!enabled || page < firstPage || page > firstPage + 1) return 1f;
        float progress = Math.max(0f, Math.min(1f, offset));
        float distance = page == firstPage ? progress : 1f - progress;
        // The TextureView receives the same alpha explicitly; two layers compound.
        return 1f - .04f * distance;
    }
}
