package com.webapp.crazyshit;

/** Display geometry, including non-square pixels and the container's display rotation. */
final class ShitTokOrientation {
    private ShitTokOrientation() {}

    static boolean eligible(float aspect) {
        return Float.isFinite(aspect) && aspect > 0f && aspect <= 1f;
    }

    static float effectiveAspect(int width, int height, float pixelRatio, int rotationDegrees) {
        if (width <= 0 || height <= 0 || !(pixelRatio > 0f) || !Float.isFinite(pixelRatio)) return 0f;
        int rotation = ((rotationDegrees % 360) + 360) % 360;
        if (rotation % 90 != 0) return 0f;
        double ratio = ((double) width * pixelRatio) / height;
        if (rotation == 90 || rotation == 270) ratio = 1d / ratio;
        return ratio > 0d && ratio <= Float.MAX_VALUE && Double.isFinite(ratio) ? (float) ratio : 0f;
    }

    /** Adaptive playback may pick any video variant. Every variant must have valid geometry. */
    static float conservativeAspect(Iterable<Float> aspects) {
        float maximum = 0f;
        for (Float aspect : aspects) {
            if (aspect == null || !(aspect > 0f) || !Float.isFinite(aspect)) return 0f;
            maximum = Math.max(maximum, aspect);
        }
        return maximum;
    }

    static NativeContentItem copy(NativeContentItem item, float aspect) {
        return new NativeContentItem(item.kind, item.title, item.url, item.imageUrl, item.views,
                item.uploader, item.comments, item.description, item.searchQuery,
                item.publishedAtMillis, aspect);
    }
}
