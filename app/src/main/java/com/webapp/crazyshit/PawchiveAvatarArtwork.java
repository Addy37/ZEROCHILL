package com.webapp.crazyshit;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** A missing favorite icon may use one image from that exact creator's first page. */
final class PawchiveAvatarArtwork {
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8));

    static {
        IO.allowCoreThreadTimeOut(true);
    }

    private PawchiveAvatarArtwork() { }

    static RequestListener<Drawable> fallback(ImageView image, NativeContentItem item,
            BooleanSupplier current, int size) {
        return new RequestListener<Drawable>() {
            @Override public boolean onLoadFailed(GlideException error, Object model,
                    Target<Drawable> target, boolean first) {
                PawchiveRepository.Creator creator = PawchiveRepository.profile(item.url, item.title);
                if (creator == null) return false;
                image.post(() -> {
                    if (!image.isShown() || !current.getAsBoolean()) return;
                    try {
                        IO.execute(() -> {
                            try {
                                String artwork = new PawchiveRepository().creatorArtwork(creator);
                                if (artwork.isEmpty()) return;
                                image.post(() -> {
                                    if (!image.isShown() || !current.getAsBoolean()) return;
                                    Glide.with(image).load(new GlideUrl(artwork, new LazyHeaders.Builder()
                                                    .setHeader("Referer", PawchiveRepository.ORIGIN).build()))
                                            .override(size, size).centerCrop()
                                            .error(R.drawable.ic_more_account)
                                            .transition(SharedArtworkReveal.artwork("creator-avatar-fallback", creator.url, true)).into(image);
                                });
                            } catch (IOException ignored) { }
                        });
                    } catch (RejectedExecutionException ignored) { }
                });
                return false;
            }

            @Override public boolean onResourceReady(Drawable resource, Object model,
                    Target<Drawable> target, DataSource source, boolean first) {
                return false;
            }
        };
    }
}
