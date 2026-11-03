package com.webapp.crazyshit;

import android.graphics.Bitmap;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import java.security.MessageDigest;

/** Validate the decoded source aspect before the existing pooled center-crop transform. */
final class OnlyFapHeroCrop extends BitmapTransformation {
    private static final byte[] ID = "ZeroChill.QualifiedHeroCenterCrop.3".getBytes(CHARSET);
    // Glide's normal decoder may upscale a thumbnail before transform(). Keep the
    // decoded source at/below native resolution so quality validation sees real pixels.
    static final com.bumptech.glide.load.resource.bitmap.DownsampleStrategy NO_UPSCALE =
            new com.bumptech.glide.load.resource.bitmap.DownsampleStrategy() {
        @Override public float getScaleFactor(int sourceW, int sourceH, int targetW, int targetH) {
            return Math.min(1f, Math.max((float) targetW / sourceW, (float) targetH / sourceH));
        }
        @Override public SampleSizeRounding getSampleSizeRounding(int w, int h, int targetW, int targetH) {
            return SampleSizeRounding.QUALITY;
        }
    };
    @Override protected Bitmap transform(BitmapPool pool, Bitmap source, int width, int height) {
        if (!OnlyFapHeroPolicy.isGoodPortraitDimensions(source.getWidth(), source.getHeight())
                || source.getWidth() < Math.min(width, 720)
                || source.getHeight() < Math.min(height, 1080))
            throw new IllegalArgumentException("Hero original does not meet portrait/resolution requirements");
        return TransformationUtils.centerCrop(pool, source, width, height);
    }
    @Override public boolean equals(Object other) { return other instanceof OnlyFapHeroCrop; }
    @Override public int hashCode() { return 0x5a434801; }
    @Override public void updateDiskCacheKey(MessageDigest digest) { digest.update(ID); }
}
