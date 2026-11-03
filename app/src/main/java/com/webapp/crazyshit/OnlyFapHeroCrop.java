package com.webapp.crazyshit;

import android.graphics.Bitmap;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import java.security.MessageDigest;

/** Validate the decoded source aspect before the existing pooled center-crop transform. */
final class OnlyFapHeroCrop extends BitmapTransformation {
    private static final byte[] ID = "ZeroChill.QualifiedHeroCenterCrop.1".getBytes(CHARSET);
    @Override protected Bitmap transform(BitmapPool pool, Bitmap source, int width, int height) {
        if (!OnlyFapHeroPolicy.isGoodPortraitDimensions(source.getWidth(), source.getHeight()))
            throw new IllegalArgumentException("Hero original is not portrait artwork");
        return TransformationUtils.centerCrop(pool, source, width, height);
    }
    @Override public boolean equals(Object other) { return other instanceof OnlyFapHeroCrop; }
    @Override public int hashCode() { return 0x5a434801; }
    @Override public void updateDiskCacheKey(MessageDigest digest) { digest.update(ID); }
}
